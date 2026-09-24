# syntax=docker/dockerfile:1
# ==================== Stage 1: build ====================
# pom.xml 编译目标为 Java 17，使用 JDK 21 镜像向后兼容编译与运行
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 依赖层缓存：先只拷 pom，离线拉全量依赖，src 变化不会击穿该层
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
# 测试在 CI 中已跑，镜像构建跳过；产物为 target/douluo-game-<version>.jar
RUN mvn -B package -DskipTests

# ==================== Stage 2: runtime ====================
FROM eclipse-temurin:21-jre
# HEALTHCHECK 需要 curl（基础镜像不自带）
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 非 root 运行
RUN useradd --system --no-create-home appuser
WORKDIR /app
COPY --from=build /build/target/douluo-game-*.jar /app/app.jar
RUN chown appuser /app/app.jar
USER appuser

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/api/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
