# ECOS CLI 镜像（独立构建，不进 ecos-docker/docker-compose.yml 基线）
#
# 构建（构建上下文必须是仓根，因为要取 tools/ecos-cli 下的源码）：
#   docker build -f tools/ecos-cli/Dockerfile \
#     --build-arg BUILD_ID=$(git rev-parse --short=8 HEAD) -t ecos-cli:local .
# 运行（入口地址靠环境变量，不写死端口）：
#   docker run --rm -e ECOS_CLI_BASE_URL=http://host.docker.internal:8080 \
#              -e ECOS_TOKEN=... ecos-cli:local doctor
#
# 说明：
#   · 多阶段构建，运行镜像只含 JRE 17，非 root 用户
#   · 镜像不含任何凭据；token 一律经环境变量 / 挂载文件注入
#   · CLI 只经 gateway 通信，容器内不解析任何 service 端口
#   · 构建期 -DskipTests：契约一致性门禁在 CI 的 cli-build job 里跑（需要仓根 + ecos_backend 才能定位仓库根）

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
# 版本可追溯：--build-arg BUILD_ID=$(git rev-parse --short=8 HEAD) 让 `--version` 带 sha（缺省 local）
ARG BUILD_ID=local
COPY tools/ecos-cli/pom.xml ./pom.xml
RUN mvn -q -B dependency:go-offline
COPY tools/ecos-cli/src ./src
RUN mvn -q -B -DskipTests -Dbuild.id="${BUILD_ID}" package

FROM eclipse-temurin:17-jre
LABEL org.opencontainers.image.title="ecos-cli" \
      org.opencontainers.image.description="ECOS unified CLI (gateway-only HTTP client)"

RUN useradd --create-home --uid 10001 ecos
WORKDIR /app
COPY --from=build /src/target/ecos-cli.jar /app/ecos-cli.jar
RUN chown -R ecos:ecos /app

USER ecos
ENV ECOS_CLI_BASE_URL=http://host.docker.internal:8080 \
    ECOS_CLI_LOG_LEVEL=INFO \
    JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"

ENTRYPOINT ["java", "-jar", "/app/ecos-cli.jar"]
CMD ["--help"]
