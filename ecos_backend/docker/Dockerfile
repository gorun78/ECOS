# =============================================================================
# ECOS Gateway Dockerfile — Multi-stage Build
#   Build stage: eclipse-temurin:17-jdk + Maven wrapper
#   Runtime:      eclipse-temurin:17-jre
#
# Build args:
#   EDITION — Maven profile to activate (standard | enterprise | ultimate)
#             Default: standard
#
# Usage:
#   docker build --build-arg EDITION=enterprise -t ecos-gateway:enterprise -f docker/Dockerfile .
# =============================================================================

# =============================================================================
# Stage 1: Build — compile with Maven, activate -P${EDITION}
# =============================================================================
FROM eclipse-temurin:17-jdk AS builder

ARG EDITION=standard

WORKDIR /build

# Copy Maven wrapper and pom files first for dependency caching
COPY .mvn/ .mvn/
COPY mvnw ./
COPY pom.xml ./
COPY common/pom.xml common/
COPY runtime/pom.xml runtime/
COPY sysman/pom.xml sysman/
COPY datanet/pom.xml datanet/
COPY dccheng/pom.xml dccheng/
COPY buszhi/pom.xml buszhi/
COPY aimod/pom.xml aimod/
COPY workspace/pom.xml workspace/
COPY market/pom.xml market/
COPY portal/pom.xml portal/
COPY gateway/pom.xml gateway/
COPY worldmodel/pom.xml worldmodel/
COPY cognitive/pom.xml cognitive/

# Download dependencies (cached unless pom.xml changes)
RUN ./mvnw -P${EDITION} dependency:go-offline -B -q || true

# Copy full source
COPY . .

# Build the gateway JAR with the specified edition profile
RUN ./mvnw -P${EDITION} install -DskipTests -q -pl gateway -am

# =============================================================================
# Stage 2: Runtime — minimal JRE image
# =============================================================================
FROM eclipse-temurin:17-jre AS runtime

# Create non-root user for security
RUN groupadd --system --gid 1000 appuser \
    && useradd --system --gid appuser --uid 1000 --no-log-init appuser

WORKDIR /app

# Copy the fat JAR from builder stage
ARG EDITION=standard
COPY --from=builder /build/gateway/target/gateway-*.jar app.jar

# Create directories for logs, temp, and uploads
RUN mkdir -p /app/logs /app/tmp /app/data \
    && chown -R appuser:appuser /app

# Switch to non-root user
USER appuser:appuser

# Expose gateway port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -sf http://localhost:8080/api/health || exit 1

# Set edition info as env (available at runtime)
ENV ECOS_EDITION=${EDITION}

# JVM options optimized for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom -Djava.io.tmpdir=/app/tmp"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
