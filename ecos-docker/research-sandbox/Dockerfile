# ECOS L3 研究沙盒镜像（仅 Python 受控执行，2026-11-26 裁决）
#
# 2026-11-26 用户裁决：沙盒执行器只保留 Python，Java 编译/执行路径已移除。
# 基础镜像仍用 maven:3.9-eclipse-temurin-17 —— 沙盒服务端自身跑在 JVM 上（非用户代码）。
#
# Python 的获取方式：Docker Hub 拉不到 python 官方镜像，但容器内 apt 源可达（实测），
# 构建时切到阿里云源安装 python3 + numpy + scipy（APT 包，不装 pip）。
# 不装 pip 的理由：pip 等于给沙盒开一条「运行时可装任意代码」的通道，与隔离目标冲突。
FROM maven:3.9-eclipse-temurin-17

# ── Python 运行时（stdlib + 科学计算 APT 包，不装 pip） ─────────────────
RUN set -eux; \
    printf 'Types: deb\nURIs: https://mirrors.aliyun.com/ubuntu/\nSuites: noble noble-updates\nComponents: main universe\nSigned-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg\n' \
        > /etc/apt/sources.list.d/ubuntu.sources; \
    apt-get update -qq; \
    DEBIAN_FRONTEND=noninteractive apt-get install -y -qq --no-install-recommends \
        python3 python3-numpy python3-scipy; \
    apt-get clean; \
    rm -rf /var/lib/apt/lists/*; \
    python3 --version; \
    python3 -c "import numpy, scipy; print('numpy', numpy.__version__, 'scipy', scipy.__version__)"

COPY out /app/classes
WORKDIR /app

USER 10001:10001
ENV SANDBOX_PORT=8000 SANDBOX_BIND=0.0.0.0 \
    SANDBOX_HEARTBEAT_FILE=/tmp/sandbox.heartbeat \
    JAVA_HOME=/opt/java/openjdk

EXPOSE 8000

# -XX:+UseSerialGC：容器内存与 PID 配额很小，避免 GC 线程抢占
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-XX:MaxRAMPercentage=70", "-cp", "/app/classes", "sandbox.SandboxServer"]
