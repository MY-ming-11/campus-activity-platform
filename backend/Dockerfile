# ---- 构建阶段：编译出可执行 jar ----
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
# 先只复制 pom，利用 Docker 层缓存，改代码时不必重新拉依赖
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- 运行阶段：只带 JRE，镜像更小 ----
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV TZ=Asia/Shanghai
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
