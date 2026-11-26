# ECOS L3 研究沙盒镜像（Phase 1 + Java/Python 受控代码执行，2026-11-25）
#
# 基础镜像选用 maven:3.9-eclipse-temurin-17：
#   代码执行需要 javac，JRE 镜像没有编译器（实测 "Module jdk.compiler not in boot Layer"）。
#   本机 Docker Hub 不可达，只能用本地已有镜像。
#
# Python 的获取方式（2026-11-25 用户要求「沙盒应同时支持 python」）：
#   Docker Hub 拉不到 python 官方镜像，但**容器内 apt 源可达**（实测）。
#   Ubuntu 官方源 archive.ubuntu.com 返回 502，阿里云镜像返回 200，
#   故构建时切到阿里云源安装 python3。实测可得 Python 3.12.3。
#
# 镜像偏大（JDK 269M + Python ~50M）是必要代价：没有运行时就没有「代码进化」能力。
FROM maven:3.9-eclipse-temurin-17

# ── Python 运行时（仅标准库，不装 pip 包） ──────────────────────────────
# 为什么只装 python3 而不装 pip：受控执行只允许标准库计算，
# 装 pip 等于给沙盒开一条「运行时可装任意代码」的通道，与隔离目标冲突。
RUN set -eux; \
    printf 'Types: deb\nURIs: https://mirrors.aliyun.com/ubuntu/\nSuites: noble noble-updates\nComponents: main universe\nSigned-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg\n' \
        > /etc/apt/sources.list.d/ubuntu.sources; \
    apt-get update -qq; \
    DEBIAN_FRONTEND=noninteractive apt-get install -y -qq --no-install-recommends python3; \
    apt-get clean; \
    rm -rf /var/lib/apt/lists/*; \
    python3 --version

COPY out /app/classes
WORKDIR /app

USER 10001:10001
ENV SANDBOX_PORT=8000 SANDBOX_BIND=0.0.0.0 \
    SANDBOX_HEARTBEAT_FILE=/tmp/sandbox.heartbeat \
    JAVA_HOME=/opt/java/openjdk

EXPOSE 8000

# -XX:+UseSerialGC：容器内存与 PID 配额很小，避免 GC 线程抢占
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-XX:MaxRAMPercentage=70", "-cp", "/app/classes", "sandbox.SandboxServer"]
