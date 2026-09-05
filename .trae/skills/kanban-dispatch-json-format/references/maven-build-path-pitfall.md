# Maven 构建路径陷阱

## 问题

`mvn clean package` 在项目根目录执行时报 `MissingProjectException: The goal you specified requires a project to execute`。

## 原因

`pom.xml` 在 `backend/` 目录下，不在项目根目录。

## 正确做法

```bash
cd /home/hermes/prj/AI-Native-Factory/3/backend && mvn clean package -DskipTests
```

jar 文件路径：`backend/target/ainative-factory-1.0.0.jar`

## 构建 + 部署脚本

```bash
# 编译
cd /home/hermes/prj/AI-Native-Factory/3/backend
mvn clean package -DskipTests

# 停旧进程
pkill -9 -f 'ainative-factory-1.0.0.jar'

# 启动
java -jar target/ainative-factory-1.0.0.jar --server.port=28081 &

# 等待启动
until curl -s -o /dev/null -w '%{http_code}' http://localhost:28081/api/auth/info | grep -q 401; do
  sleep 2
done
echo "Backend ready"
```
