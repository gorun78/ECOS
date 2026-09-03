#!/bin/bash
# 三版本构建脚本 — ECOS Delivery Verification
set -o pipefail

export HOME=/home/guorongxiao
export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
export PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin:$PATH

cd /home/guorongxiao/ECOS/ecos_backend

echo "=============================================================================="
echo "  BUILD START: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  JAVA: $(java -version 2>&1 | head -1)"
echo "  MAVEN: $(mvn -version 2>&1 | head -1)"
echo "  PROJECT: $(pwd)"
echo "=============================================================================="
echo ""

RESULT_FILE=/home/guorongxiao/ECOS/ecos_backend/build-3edition-result.txt
: > "$RESULT_FILE"

for EDITION in standard enterprise ultimate; do
    echo "------------------------------------------------------------------------------"
    echo "  BUILD: ${EDITION} Edition  —  $(date '+%H:%M:%S')"
    echo "------------------------------------------------------------------------------"
    START=$(date +%s)
    mvn -P"${EDITION}" install -Dmaven.test.skip=true -DskipTests -q > /tmp/mvn-${EDITION}.log 2>&1
    EXIT_CODE=$?
    END=$(date +%s)
    DURATION=$((END - START))

    # 检查 JAR
    JAR="gateway/target/gateway-1.0.0-SNAPSHOT.jar"
    if [ -f "$JAR" ]; then
        SIZE=$(du -h "$JAR" | cut -f1)
        JAR_STATUS="OK (${SIZE})"
    else
        JAR_STATUS="MISSING"
    fi

    if [ $EXIT_CODE -eq 0 ]; then
        STATUS="SUCCESS"
    else
        STATUS="FAILURE (exit=$EXIT_CODE)"
    fi

    echo "  Result: ${STATUS} | Duration: ${DURATION}s | JAR: ${JAR_STATUS}"
    echo "${EDITION}|${STATUS}|${DURATION}s|${JAR_STATUS}" >> "$RESULT_FILE"

    if [ $EXIT_CODE -ne 0 ]; then
        echo "  Last 15 lines of error log:"
        tail -15 /tmp/mvn-${EDITION}.log
    fi
    echo ""
done

echo "=============================================================================="
echo "  BUILD SUMMARY: $(date '+%Y-%m-%d %H:%M:%S')"
echo "=============================================================================="
cat "$RESULT_FILE"
echo ""
echo "Done."
