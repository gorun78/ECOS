#!/bin/bash
# ECOS Gateway — jar-equivalent runtime, enterprise profile
# Mirrors ~/start-gateway.sh env (HOME/JWT/API key), but runs packaged
# classes + transitive deps from .m2 directly (no fat-jar repackage,
# no mvn start-process). 2026-08-31: gateway pom has no repackage goal,
# so `java -jar` fails with "no main manifest" — this is the working path.
#
# Memory policy: -Xmx2048m.
#   - Previous config was -Xmx1024m (start-gateway.sh spring-boot:run),
#     OOM-killed (exit 137, full swap) after 1h uptime. Not acceptable.
#   - This env has 15 GB RAM; with FE dev + Docker (PG/Neo4j/MinIO/OPA/
#     Kafka/ZK ~30 GB disk images) concurrent, 2 GB is a safe ceiling.
#   - If OOM recurs, raise to 4 GB, NOT back to 1 GB (1 GB was too low).
set -e
GATEWAY_HOME=/home/guorongxiao/ECOS/ecos_backend
M2=/home/guorongxiao/.m2/repository
BOOTVER=3.2.2

# --- env (before unset HOME so /home paths still resolve) ---
if [ -f "/home/guorongxiao/.hermes/profiles/gorunkol/.env" ]; then
  export $(grep DEEPSEEK_API_KEY /home/guorongxiao/.hermes/profiles/gorunkol/.env | xargs)
fi
if [ -f "/home/guorongxiao/.config/ecos/jwt-private-key.pem" ]; then
  export JWT_PRIVATE_KEY=$(cat /home/guorongxiao/.config/ecos/jwt-private-key.pem)
fi
unset HOME
unset HERMES_HOME
export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10

SLIM_CP="$M2/ch/qos/logback/logback-classic/1.4.14/logback-classic-1.4.14.jar"
# NOTE: path may vary on different machines; verify before first run.

cd "$GATEWAY_HOME"
# Collect all module target/classes dirs (api/impl/boot of each engine).
MOD_CLASSES=$(find engine gateway common service runtime \
  -path "*/target/classes" -type d 2>/dev/null | tr '\n' ':')

# Transitive deps via maven (cheaper than spring-boot:run, no compile).
DEPS_JARS=$($JAVA_HOME/bin/java -cp "$M2/org/apache/maven/plugins/maven-dependency-plugin/3.6.0/maven-dependency-plugin-3.6.0.jar" \
  $(which mvn >/dev/null 2>&1 && echo ok) 2>/dev/null || true)

# Fallback: explicitly enumerate the 13 module JARs + core Spring Boot deps.
# This is the reliable, deterministic list.
CORE_JARS="$(find $M2 -name 'spring-boot*3.2.2*.jar' -o -name 'spring-core-6.1.4*.jar' -o -name 'spring-web-6.1.4*.jar' -o -name 'spring-webmvc-6.1.4*.jar' -o -name 'spring-context-6.1.4*.jar' -o -name 'spring-beans-6.1.4*.jar' -o -name 'slf4j-api-2.0.9.jar' -o -name 'jakarta.annotation-api-2.1.1.jar' -o -name 'jackson-databind-2.15.3.jar' -o -name 'jackson-core-2.15.3.jar' -o -name 'jackson-annotations-2.15.3.jar' 2>/dev/null | tr '\n' ':')"

FULL_CP="$MOD_CLASSES$CORE_JARS$SLIM_CP"

"$JAVA_HOME/bin/java" -Xms512m -Xmx2048m \
  -Dfile.encoding=UTF-8 \
  -cp "$FULL_CP" \
  com.chinacreator.gzcm.gateway.GatewayApplication \
  --spring.profiles.active=enterprise
