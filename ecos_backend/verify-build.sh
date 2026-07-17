#!/bin/bash
export JAVA_HOME=/home/guorongxiao/jdk17
export M2_HOME=/home/guorongxiao/.local/apache-maven-3.9.11
export PATH=$JAVA_HOME/bin:$M2_HOME/bin:/usr/bin:/bin
cd /home/guorongxiao/ECOS/ecos_backend
mvn clean install -DskipTests 2>&1
