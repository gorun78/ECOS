#!/usr/bin/env bash
# v7 build verify — force multiModuleProjectDirectory to absolute root to
# override any relativePath parent-resolution bug on WSL.
set -o pipefail
export HOME=/home/guorongxiao
export PATH="/mnt/d/JavaProjects/env/apache-maven-3.9.11/bin:/home/guorongxiao/.local/bin:/usr/local/bin:/usr/bin:/bin"
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
export MAVEN_OPTS="-Djava.awt.headless=true"
ROOT=/home/guorongxiao/ECOS/ecos_backend
cd "$ROOT"

echo "=== PWD=$(pwd) ==="
echo "=== -Dmaven.multiModuleProjectDirectory workaround attempt ==="
date
mvn -f "$ROOT/pom.xml" -pl engine/data-engine/data-engine-impl -am install \
  -DskipTests -B \
  -Dmaven.multiModuleProjectDirectory="$ROOT" 2>&1 | grep -iE "contact|error|forge|build|SUCCESS|FAIL|total time" | tail -60
echo "=== BUILD_EXIT=${PIPESTATUS[0]} ==="
date
