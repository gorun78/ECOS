#!/usr/bin/env bash
cd /home/guorongxiao/ECOS/ecos_backend
env -i HOME=/home/guorongxiao PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
mvn test -pl engine/kb-engine/kb-engine-impl -Dtest=com.chinacreator.gzcm.engine.kb.service.KnowledgeExtractionServiceTest 2>&1 | tail -n 180
