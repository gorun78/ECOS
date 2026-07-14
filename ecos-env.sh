#!/bin/bash
# ECOS 开发环境标准化
# 用法: source ~/ecos-env.sh

export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
export M2_HOME=/home/guorongxiao/.local/apache-maven-3.9.11
export MVND_HOME=/home/guorongxiao/.local/mvnd
export PATH="$MVND_HOME/bin:$JAVA_HOME/bin:$M2_HOME/bin:$PATH"
alias mvn='mvnd --no-transfer-progress'

export ECOS_BACKEND=/home/guorongxiao/databridge-v2
export ECOS_FRONTEND=/home/guorongxiao/c2eos

alias ecos-be='cd $ECOS_BACKEND'
alias ecos-fe='cd $ECOS_FRONTEND'
alias ecos-build='cd $ECOS_BACKEND && mvn compile -DskipTests -q && echo "✓ Build OK"'
alias ecos-test='cd $ECOS_BACKEND && mvn test -q'
alias ecos-up='cd $ECOS_BACKEND && mvn spring-boot:run -pl databridge-sysman/sysman-boot &'
alias ecos-check='bash /home/guorongxiao/pre-check.sh'

echo "[ecos-env] mvnd $(mvnd --version 2>/dev/null | head -1 | grep -oP '[0-9.]+' | head -1)"
echo "[ecos-env] JAVA_HOME=$JAVA_HOME"
echo "[ecos-env] BACKEND=$ECOS_BACKEND  FRONTEND=$ECOS_FRONTEND"
