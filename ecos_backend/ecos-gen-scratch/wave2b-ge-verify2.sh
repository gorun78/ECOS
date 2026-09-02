#!/usr/bin/env bash
# Wave-2B ge D→I — V3 验证（env -i 绕 Hermes UNC 双写 bug）
set -uo pipefail
exec env -i \
  HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -u /home/guorongxiao/ECOS/ecos_backend/ecos-gen-scratch/_wave2b-inner.sh
