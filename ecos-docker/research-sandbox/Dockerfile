# ECOS L3 研究沙盒镜像（Phase 1，2026-11-25）
#
# 构建方式见同目录 build.ps1：分两阶段不适用（本机无法联网拉 maven），
# 改为「宿主机 javac 预编译 → 镜像只 COPY class」——镜像层无编译器、无源码包管理。
#
# 基础镜像选用 eclipse-temurin:17-jre（本地已有）：
#   本机 Docker Hub 不可达，python 镜像拉不下来；改用 JDK 自带 HttpServer，
#   零第三方依赖，与项目 Java 工具链一致。
#
# 隔离参数在 docker-compose.yml 的 research-sandbox 段下发：
#   非 root(10001) / 只读根 / cap-drop ALL / no-new-privileges /
#   internal 网络(无出网) / 内存与 PID 配额 / 超时。
FROM eclipse-temurin:17-jre

COPY out /app/classes
WORKDIR /app

USER 10001:10001
ENV SANDBOX_PORT=8000 SANDBOX_BIND=0.0.0.0
EXPOSE 8000

# -XX:+UseSerialGC / MaxRAMPercentage：容器内存上限很小，避免 GC 线程抢 PID 配额
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-XX:MaxRAMPercentage=70", "-cp", "/app/classes", "sandbox.SandboxServer"]
