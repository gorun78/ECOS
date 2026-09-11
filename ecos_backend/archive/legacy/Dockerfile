# =============================================================================
# Dockerfile — sysman-boot (DataBridge v2 系统管理服务)
# Base: eclipse-temurin:17-jre
# JAR: sysman/sysman-boot/target/sysman-boot-*.jar
# Port: 8081
# =============================================================================

FROM eclipse-temurin:17-jre AS base

WORKDIR /app

# =============================================================================
# Stage 1: Extract layered JAR (Spring Boot fat JAR approach)
# =============================================================================
FROM base AS extract
ARG JAR_FILE=sysman/sysman-boot/target/sysman-boot-1.0.0-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar
RUN java -Djarmode=layertools -jar app.jar extract --destination /app

# =============================================================================
# Stage 2: Runtime image
# =============================================================================
FROM base AS runtime

# Create non-root user for security
RUN groupadd --system --gid 1000 appuser \
    && useradd --system --gid appuser --uid 1000 --no-log-init appuser

WORKDIR /app

# Copy extracted layers (Spring Boot layered JAR optimization)
# Order matters: least frequently changed layers first for better caching
COPY --from=extract /app/dependencies/ ./
COPY --from=extract /app/spring-boot-loader/ ./
COPY --from=extract /app/snapshot-dependencies/ ./
COPY --from=extract /app/application/ ./

# Create directories for logs and temp data
RUN mkdir -p /app/logs /app/tmp \
    && chown -R appuser:appuser /app

# Switch to non-root user
USER appuser:appuser

# Expose application port
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/sys-man/api/health || exit 1

# JVM options and entry point
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.io.tmpdir=/app/tmp"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
