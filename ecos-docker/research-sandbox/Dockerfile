# ECOS L3 研究沙盒镜像（Phase 1 + 受控代码执行，2026-11-25 用户授权）
#
# 基础镜像选用 maven:3.9-eclipse-temurin-17 而非 jre：
#   代码执行需要 javac，JRE 镜像没有编译器（实测 "Module jdk.compiler not in boot Layer"）。
#   本机 Docker Hub 不可达，只能用本地已有镜像。
#
# 镜像偏大（JDK 269M）是必要代价：没有编译器就没有「代码进化」能力。
# 注意：镜像保留 maven 自带的 /root/.m2 知识无害——容器以 10001 非 root 运行，
#   且 /root 不可写（只读根文件系统）。
#
# 隔离参数在 docker-compose.yml 的 research-sandbox 段下发：
#   非 root(10001) / 只读根 + tmpfs / cap-drop ALL / no-new-privileges /
#   无出网(仅回环发布) / 内存与 PID 配额。
FROM maven:3.9-eclipse-temurin-17

COPY out /app/classes
WORKDIR /app

USER 10001:10001
ENV SANDBOX_PORT=8000 SANDBOX_BIND=0.0.0.0 \
    SANDBOX_HEARTBEAT_FILE=/tmp/sandbox.heartbeat \
    JAVA_HOME=/opt/java/openjdk

EXPOSE 8000

# -XX:+UseSerialGC：容器内存与 PID 配额很小，避免 GC 线程抢占
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-XX:MaxRAMPercentage=70", "-cp", "/app/classes", "sandbox.SandboxServer"]
