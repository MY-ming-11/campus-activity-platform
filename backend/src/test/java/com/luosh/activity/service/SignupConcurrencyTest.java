package com.luosh.activity.service;

import com.luosh.activity.common.BizException;
import com.luosh.activity.entity.Activity;
import com.luosh.activity.entity.User;
import com.luosh.activity.mapper.ActivityMapper;
import com.luosh.activity.mapper.UserMapper;
import com.luosh.activity.vo.SignupResultVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 报名的并发正确性测试：三种策略都要满足「100 人抢 10 个名额，恰好 10 人成功」。
 *
 * 这是整个项目最核心的一条断言——把人工压测时「自己去数据库里看一眼」的动作，
 * 变成了可重复执行、可进 CI 的自动化验证。
 *
 * <h3>为什么默认不跑</h3>
 * 并发正确性依赖真实的 MySQL 行锁、唯一索引和 Redis Lua 原子性，用 H2 或 mock 测不出来，
 * 必须连真实基础设施。而默认的 {@code mvn test} 应该是「克隆下来就能跑」的，
 * 不该要求开发者先起一套数据库。所以这个类用 {@code -Dintegration=true} 显式开启。
 *
 * <h3>怎么跑</h3>
 * <pre>
 * docker compose -p activity-test -f docker-compose.yml -f docker-compose.test.yml up -d mysql redis
 * cd backend &amp;&amp; mvn test -Dintegration=true
 * </pre>
 * 用独立的项目名 {@code activity-test}，容器和数据卷跟开发环境分开，不会污染演示数据。
 * 连接参数可以用 {@code -Dtest.mysql.host} 等系统属性覆盖，CI 里指向服务容器即可。
 */
@SpringBootTest(properties = {
        "signup.allow-override=true",
        // 建表必须早于 ApplicationRunner（DataInitializer 一启动就要查 sys_user）
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema.sql"
})
@EnabledIfSystemProperty(named = "integration", matches = "true")
class SignupConcurrencyTest {

    private static final int QUOTA = 10;
    private static final int CONTENDERS = 100;

    @DynamicPropertySource
    static void testInfrastructure(DynamicPropertyRegistry registry) {
        String mysqlHost = System.getProperty("test.mysql.host", "localhost");
        String mysqlPort = System.getProperty("test.mysql.port", "3307");
        registry.add("spring.datasource.url", () -> String.format(
                "jdbc:mysql://%s:%s/activity_platform"
                        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                mysqlHost, mysqlPort));
        registry.add("spring.datasource.username",
                () -> System.getProperty("test.mysql.user", "root"));
        registry.add("spring.datasource.password",
                () -> System.getProperty("test.mysql.password", "root123456"));
        registry.add("spring.data.redis.host",
                () -> System.getProperty("test.redis.host", "localhost"));
        registry.add("spring.data.redis.port",
                () -> System.getProperty("test.redis.port", "6380"));
    }

    @Autowired
    private SignupService signupService;

    @Autowired
    private ActivityMapper activityMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @ParameterizedTest(name = "策略 {0}")
    @ValueSource(strings = {"redis", "pessimistic", "optimistic"})
    @DisplayName("100 人同时抢 10 个名额：恰好 10 人成功，数据库零超卖")
    void concurrentSignupDoesNotOversell(String strategy) throws Exception {
        Long activityId = createActivity();
        List<Long> userIds = createUsers(CONTENDERS);

        int successCount = runConcurrently(activityId, userIds, strategy);

        // Redis 策略是异步落库的，等消息表消费完再断言
        awaitValidSignups(activityId, QUOTA);

        Integer validSignups = jdbc.queryForObject(
                "SELECT COUNT(*) FROM activity_signup WHERE activity_id = ? AND status <> 'CANCELED'",
                Integer.class, activityId);
        Integer distinctUsers = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT user_id) FROM activity_signup WHERE activity_id = ? AND status <> 'CANCELED'",
                Integer.class, activityId);

        assertThat(validSignups)
                .as("有效报名数必须恰好等于名额数：多了是超卖，少了是漏单")
                .isEqualTo(QUOTA);
        assertThat(distinctUsers)
                .as("不能出现同一个人占两个名额")
                .isEqualTo(QUOTA);
        assertThat(successCount)
                .as("返回成功的人数必须和落库人数一致")
                .isEqualTo(QUOTA);
    }

    /**
     * 所有线程卡在同一个起跑线上再一起发车。
     * 否则先启动的线程早就抢完了，根本测不到并发。
     */
    private int runConcurrently(Long activityId, List<Long> userIds, String strategy) throws Exception {
        AtomicInteger success = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(userIds.size());
        CountDownLatch fire = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (Long userId : userIds) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    fire.await();
                    try {
                        SignupResultVO result = signupService.signup(activityId, userId, strategy);
                        if (result.success()) {
                            success.incrementAndGet();
                        }
                    } catch (BizException soldOut) {
                        // 名额已经抢完了，属于预期结果
                    }
                    return null;
                }));
            }

            ready.await();
            fire.countDown();

            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        }
        return success.get();
    }

    private void awaitValidSignups(Long activityId, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM activity_signup WHERE activity_id = ? AND status <> 'CANCELED'",
                    Integer.class, activityId);
            if (count != null && count >= expected) {
                return;
            }
            Thread.sleep(100);
        }
    }

    private Long createActivity() {
        LocalDateTime now = LocalDateTime.now();
        Activity activity = new Activity();
        activity.setTitle("并发测试-" + UUID.randomUUID());
        activity.setLocation("测试场地");
        activity.setDescription("自动化并发测试创建的活动");
        activity.setStartTime(now.plusDays(2));
        activity.setEndTime(now.plusDays(2).plusHours(2));
        activity.setSignupStart(now.minusDays(1));
        activity.setSignupEnd(now.plusDays(1));
        activity.setQuota(QUOTA);
        activity.setOccupied(0);
        activity.setCheckinSecret(UUID.randomUUID().toString().replace("-", ""));
        activity.setStatus("PUBLISHED");
        activity.setCreatorId(1L);
        activityMapper.insert(activity);
        return activity.getId();
    }

    private List<Long> createUsers(int count) {
        List<Long> ids = new ArrayList<>(count);
        String runTag = UUID.randomUUID().toString().substring(0, 6);
        for (int i = 0; i < count; i++) {
            User user = new User();
            user.setUsername("ct_" + runTag + "_" + i);
            // 测试直接调 Service，不走登录，密码用一个占位值即可
            user.setPassword("$2a$10$placeholderplaceholderplaceholder");
            user.setNickname("并发用户" + i);
            user.setPhone("");
            user.setRole("USER");
            userMapper.insert(user);
            ids.add(user.getId());
        }
        return ids;
    }
}
