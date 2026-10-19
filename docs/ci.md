# 自动化测试与持续集成

## 一、测试分了两层

| 层 | 文件 | 依赖 | 默认是否执行 | 耗时 |
|---|---|---|---|---|
| 单元测试 | `CheckinCodeGeneratorTest` | 无（纯逻辑） | 是 | 0.3 秒 |
| 并发集成测试 | `SignupConcurrencyTest` | 真实 MySQL + Redis | **否**，需 `-Dintegration=true` | 17 秒 |

分层的理由：默认的 `mvn test` 应该「克隆下来就能跑」，不该要求开发者先起一套数据库。而并发正确性又必须连真实基础设施——行锁、唯一索引、Redis Lua 原子性，用 H2 或者 mock 都测不出来。

所以默认只跑单元测试，并发测试显式开启。

## 二、默认跑什么

```bash
cd backend
mvn test
```

12 个用例，6 秒左右，覆盖签到码算法：时间片轮换、上一轮容错、过期失效、跨活动隔离、空值处理。

## 三、并发测试怎么跑

### 1. 起一套独立的测试基础设施

```bash
# 用独立项目名，容器和数据卷跟开发环境完全分开，不会污染演示数据
docker compose -p activity-test -f docker-compose.yml -f docker-compose.test.yml up -d mysql redis
```

端口故意选 **3307 / 6380**，避开本机可能被占用的 3306（MySQL 服务）和 6379（Memurai）。

### 2. 执行

```bash
cd backend
mvn test -Dintegration=true
```

三个策略各跑一遍「100 人同时抢 10 个名额」，然后直接查库断言：

- 有效报名数**恰好** 10 条（多了是超卖，少了是漏单）
- 去重用户数**恰好** 10 个（不能一人占两个名额）
- 返回成功的人数与落库人数一致

### 3. 收尾

```bash
docker compose -p activity-test -f docker-compose.yml -f docker-compose.test.yml down -v
```

### 连接参数覆盖

CI 里基础设施地址不一样时，用系统属性覆盖：

```bash
mvn test -Dintegration=true \
  -Dtest.mysql.host=mysql -Dtest.mysql.port=3306 \
  -Dtest.mysql.user=root -Dtest.mysql.password=你的密码 \
  -Dtest.redis.host=redis -Dtest.redis.port=6379
```

## 四、Gitee Go 配置

Gitee 流水线是**界面配置**的，没有 YAML 文件可以提交进仓库，所以下面这些要在网页上点。

### 步骤

1. 打开仓库页面，找到「流水线」入口（有些账号在「服务」标签里）
2. 新建流水线，代码源选本仓库的 `main` 分支
3. 添加一个构建任务，执行命令：

   ```bash
   cd backend && mvn -B clean test
   ```

4. 触发条件选「推送代码时」

### 构建镜像要带 JDK 21

项目用了 Java 21 和虚拟线程，JDK 8/11/17 都编译不过。如果默认构建镜像的 JDK 版本不够，把镜像换成：

```
maven:3.9-eclipse-temurin-21
```

这个镜像自带 Maven 3.9 和 JDK 21，不用额外装。

### 首次构建会慢

Maven 要下载全部依赖，第一次大概 2-5 分钟。之后的构建会命中缓存。

### 想在流水线里也跑并发测试

需要流水线能提供 MySQL 和 Redis。如果 Gitee 流水线支持服务容器，把它们挂上，然后用上面那组 `-Dtest.*` 参数指过去，再追加 `-Dintegration=true`。

如果配置不了，就保持现在这样：CI 跑单元测试，并发测试作为本地/部署前的验证手段写进 README。这不算缺失——单元测试保证核心算法不被改坏，并发测试保证端到端行为，两者场景不同。

## 五、踩过的坑

### Docker Desktop 29 + Testcontainers 连不上

最初并发测试用 Testcontainers 自动起容器，在 Docker Engine 29.x 上一直失败：

```
NpipeSocketClientProviderStrategy: BadRequestException (Status 400)
```

原因是 **API 版本不兼容**：引擎最低支持 API 1.40，而 Spring Boot 3.2.5 管理的 Testcontainers 1.19.7 默认用 1.32。手动设 `DOCKER_API_VERSION=1.56` 能连上，但连接时通时断，不稳定。

最后放弃 Testcontainers，改成「连一套独立起的 compose 环境」。好处是不依赖宿主机 Docker 的 API 细节，本地和 CI 的跑法一致。

### 建表必须在 Spring 上下文启动之前

`DataInitializer` 一启动就要查 `sys_user`，如果用 `@Sql`（在测试方法前执行），表还没建好，上下文就挂了。要用 `spring.sql.init.schema-locations`，让建表发生在上下文刷新阶段。

### 两个 compose 项目的容器名冲突

`container_name` 是全局唯一的。测试栈如果不改名字，会和开发栈撞车，所以要加 `docker-compose.test.yml` 覆盖。
