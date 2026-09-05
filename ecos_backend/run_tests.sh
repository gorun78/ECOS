#!/usr/bin/env bash
# Wave-5.1 三模块单测 — 临时脚本
cd /home/guorongxiao/ECOS/ecos_backend

env -i HOME=/home/guorongxiao \
    PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
    JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
    bash -c 'mvn test -pl engine/kb-engine/kb-engine-impl,engine/cognitive-engine/cognitive-engine-impl,engine/ai-engine/ai-engine-impl 2>&1'
