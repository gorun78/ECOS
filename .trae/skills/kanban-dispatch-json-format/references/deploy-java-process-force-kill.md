# Java 进程部署强杀模式

## 问题

`pkill -f 'ainative-factory-1.0.0.jar'` 在很多情况下杀不掉所有旧 Java 进程：

- 同一 jar 有多个实例在跑（不同端口）
- 子进程未响应 SIGTERM
- `pkill` 仅匹配进程名但子进程 escaped

后果：旧代码继续处理请求，新部署的代码不生效。排查了多次"部署了但没生效"的 bug 才发现根因。

## 正确部署脚本

```bash
# 1. 强杀所有旧进程
pkill -9 -f 'ainative-factory-1.0.0.jar' 2>/dev/null
sleep 2  # 等待端口释放

# 2. 编译
cd /path/to/project/backend
mvn clean package -DskipTests

# 3. 启动
java -jar target/ainative-factory-1.0.0.jar --server.port=28081 &

# 4. 验证
lsof -i :28081  # 确认只有一个 PID
curl -s -o /dev/null -w '%{http_code}' http://localhost:28081/api/auth/info
# 期望 401（鉴权正常，非 000 即不可达）
```

## 验证步骤

编译部署后必须确认：

```bash
# 确认正在运行的进程 PID 和启动时间
ps -eo pid,lstart,args --no-headers | grep ainative-factory

# 确认只有一个进程在目标端口监听
lsof -i :28081
```

如果 lsof 显示多个进程或启动时间不对——**重新执行强杀部署**。
