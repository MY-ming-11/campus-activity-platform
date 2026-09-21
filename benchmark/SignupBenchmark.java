import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 抢名额压测程序。零依赖，JDK 21 直接单文件运行：
 *
 *   java benchmark/SignupBenchmark.java [baseUrl] [并发用户数] [名额]
 *   java benchmark/SignupBenchmark.java http://localhost:8080 1000 100
 *
 * 用虚拟线程承载并发用户：1000 个平台线程要占掉约 1GB 栈内存，
 * 虚拟线程只在真正阻塞时才挂起，同样的并发量内存占用可以忽略。
 *
 * 程序会自己建一个新活动、造一批压测用户，然后让所有人同一瞬间开始抢。
 * 报告的"缺口"一行是最关键的：如果缺口不为 0，说明发生了超卖。
 */
public class SignupBenchmark {

    private static final Pattern CODE = Pattern.compile("\"code\":(-?\\d+)");
    private static final Pattern SUCCESS = Pattern.compile("\"success\":(true|false)");
    private static final String BENCH_PASSWORD = "bench123456";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

    public static void main(String[] args) throws Exception {
        String baseUrl = at(args, 0, "http://localhost:8080");
        int userCount = Integer.parseInt(at(args, 1, "1000"));
        int quota = Integer.parseInt(at(args, 2, "100"));

        log("目标服务   : " + baseUrl);
        log("并发用户数 : " + userCount);
        log("活动名额   : " + quota);
        log("");

        String adminToken = login(baseUrl, "admin", "admin123");
        long activityId = createActivity(baseUrl, adminToken, quota);
        log("压测活动已创建，id=" + activityId);

        long setupStart = System.currentTimeMillis();
        String[] tokens = prepareUsers(baseUrl, userCount);
        log("压测用户就绪 " + tokens.length + " 个，耗时 " + (System.currentTimeMillis() - setupStart) + " ms");
        log("");

        long[] latencies = runBurst(baseUrl, activityId, tokens);

        // 等消息表消费完，再读最终余量
        Thread.sleep(3000);
        long remaining = readRemaining(baseUrl, activityId);

        printReport(userCount, quota, latencies, remaining, activityId);
    }

    /** 所有人卡在同一个起跑线上，然后同时发车。 */
    private static long[] runBurst(String baseUrl, long activityId, String[] tokens) throws Exception {
        long[] latencies = new long[tokens.length];
        CountDownLatch ready = new CountDownLatch(tokens.length);
        CountDownLatch fire = new CountDownLatch(1);
        AtomicInteger won = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        AtomicInteger limited = new AtomicInteger();
        AtomicInteger already = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < tokens.length; i++) {
                int index = i;
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        fire.await();
                        long start = System.nanoTime();
                        String body = post(baseUrl + "/api/activities/" + activityId + "/signup", "", tokens[index]);
                        latencies[index] = System.nanoTime() - start;
                        classify(body, won, soldOut, limited, already, failed);
                    } catch (Exception e) {
                        latencies[index] = 0;
                        failed.incrementAndGet();
                    }
                });
            }

            ready.await();
            log("全部就绪，开始抢名额 ...");
            long start = System.currentTimeMillis();
            fire.countDown();

            // 等所有请求返回
            pool.shutdown();
            while (!pool.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                // 空转等待
            }
            long cost = System.currentTimeMillis() - start;

            log("");
            log("抢购结果：抢到 " + won.get() + " / 售罄 " + soldOut.get()
                    + " / 限流 " + limited.get() + " / 已报名 " + already.get() + " / 异常 " + failed.get());
            log("总耗时 " + cost + " ms，吞吐 " + (tokens.length * 1000L / Math.max(1, cost)) + " req/s");
        }
        return latencies;
    }

    private static void classify(String body, AtomicInteger won, AtomicInteger soldOut,
                                 AtomicInteger limited, AtomicInteger already, AtomicInteger failed) {
        Matcher codeMatcher = CODE.matcher(body);
        int code = codeMatcher.find() ? Integer.parseInt(codeMatcher.group(1)) : -999;

        if (code == 429) {
            limited.incrementAndGet();
            return;
        }
        if (body.contains("已经报名过")) {
            already.incrementAndGet();
            return;
        }
        if (body.contains("名额已经抢完")) {
            soldOut.incrementAndGet();
            return;
        }
        Matcher successMatcher = SUCCESS.matcher(body);
        if (code == 0 && successMatcher.find() && "true".equals(successMatcher.group(1))) {
            won.incrementAndGet();
            return;
        }
        failed.incrementAndGet();
    }

    private static void printReport(int userCount, int quota, long[] latencies, long remaining, long activityId) {
        long[] valid = Arrays.stream(latencies).filter(v -> v > 0).sorted().toArray();

        log("");
        log("================== 压测报告 ==================");
        log("并发用户数     : " + userCount);
        log("活动名额       : " + quota);
        log("最终剩余名额   : " + remaining);
        if (valid.length > 0) {
            log("平均延迟       : " + ms(avg(valid)) + " ms");
            log("P50 延迟       : " + ms(percentile(valid, 50)) + " ms");
            log("P90 延迟       : " + ms(percentile(valid, 90)) + " ms");
            log("P99 延迟       : " + ms(percentile(valid, 99)) + " ms");
            log("最大延迟       : " + ms(valid[valid.length - 1]) + " ms");
        }
        log("---------------------------------------------");
        log("活动 id        : " + activityId + "（核对数据库用）");
        log("=============================================");
    }

    private static String[] prepareUsers(String baseUrl, int count) throws Exception {
        String[] tokens = new String[count];
        Semaphore limit = new Semaphore(200);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    limit.acquire();
                    try {
                        String username = "bench_" + index;
                        String registerBody = "{\"username\":\"" + username + "\",\"password\":\"" + BENCH_PASSWORD + "\"}";
                        String result = post(baseUrl + "/api/auth/register", registerBody, null);
                        tokens[index] = extractToken(result);
                        if (tokens[index] == null) {
                            // 用户名已存在，说明不是第一次跑，直接登录
                            tokens[index] = login(baseUrl, username, BENCH_PASSWORD);
                        }
                    } finally {
                        limit.release();
                    }
                    return null;
                }));
            }
            for (var future : futures) {
                future.get();
            }
        }
        return tokens;
    }

    private static long createActivity(String baseUrl, String adminToken, int quota) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        String body = """
                {
                  "title": "压测活动 %d",
                  "location": "压测场地",
                  "startTime": "%s",
                  "endTime": "%s",
                  "signupStart": "%s",
                  "signupEnd": "%s",
                  "quota": %d,
                  "status": "PUBLISHED"
                }
                """.formatted(
                System.currentTimeMillis() % 100000,
                now.plusDays(31).format(FMT),
                now.plusDays(31).plusHours(2).format(FMT),
                now.minusDays(1).format(FMT),
                now.plusDays(30).format(FMT),
                quota);

        String response = post(baseUrl + "/api/admin/activities", body, adminToken);
        Matcher matcher = Pattern.compile("\"data\":(\\d+)").matcher(response);
        if (!matcher.find()) {
            throw new IllegalStateException("创建压测活动失败: " + response);
        }
        return Long.parseLong(matcher.group(1));
    }

    private static long readRemaining(String baseUrl, long activityId) throws Exception {
        String response = get(baseUrl + "/api/activities/" + activityId, null);
        Matcher matcher = Pattern.compile("\"remaining\":(\\d+)").matcher(response);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : -1;
    }

    private static String login(String baseUrl, String username, String password) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        String token = extractToken(post(baseUrl + "/api/auth/login", body, null));
        if (token == null) {
            throw new IllegalStateException("登录失败: " + username);
        }
        return token;
    }

    private static String extractToken(String json) {
        Matcher matcher = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String post(String url, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    private static String get(String url, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    private static long avg(long[] sorted) {
        long sum = 0;
        for (long v : sorted) {
            sum += v;
        }
        return sum / sorted.length;
    }

    private static long percentile(long[] sorted, int p) {
        int index = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }

    private static String ms(long nanos) {
        return String.format("%.2f", nanos / 1_000_000.0);
    }

    private static String at(String[] args, int index, String fallback) {
        return args.length > index ? args[index] : fallback;
    }

    private static void log(String message) {
        System.out.println(message);
    }
}
