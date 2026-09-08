import subprocess

board = "ecos"
goal = """# PMO-49: 数据源密码加密集成 (Wave 4 — 纯修 P1-02)

> 架构铁律: §2.5 runtime 公共基础 + §2.4 security-engine 安全接入
> task_id: ecos-password-encrypt-w4
> 前置: Wave 2/3 已交付 DataSourceServiceImpl + PMO45DataSourceController，唯一剩余 P1: 密码明文存储

## 范围极小，只做这一个事

在 DataSourceServiceImpl 读/写 config 时调 security-engine 的 IDataEncryptionService:

### 1. DataSourceServiceImpl — 注入加密
- 在构造器接收 IDataEncryptionService (若不可 @Autowired(required = false) 注入) — 不行就 new DataEncryptionServiceImpl(new KeyManagementServiceImpl()) 直接 new (同 Authentication 模式已经这样了)
- KEY_ID 常量: "dataSourcePwd" (IKeyManagementService 会为 keyId 自建 key)

### 2. createDataSource / updateDataSource — 加密写入
- 在任何
  - dao.save(datasource) 前
  - 将 datasource.getPassword() (或 datasource.getConnConfig().get("password")) 调 dataEncryptionService.encrypt(plaintext, KEY_ID)
  - 加密后的密文写回 datasource.setConfigJson(...) 或 datasource.setPasswordEncrypted(ciphertext) — 具体存哪个字段看 DataSourceEntity
  - 原 plaintext 字段置空 (清空) 避免明文落库

### 3. resolveConfig / testConnection — 解密读出
- 在调用 JdbcConnector.testConnectionDetailed 之前:
  - 若 datasource 含 passwordEncrypted (密文字段) 且 password 为 null:
    - String decrypted = dataEncryptionService.decrypt(encrypted, KEY_ID)
    - config.setPassword(decrypted)
  - 若只有明文 password (兼容老数据): 直接用明文
  - 响应 ID
- 路由: PreviewSchema 也同理

### 4. Controller 层 — 返回密码掩码
- PMO45DataSourceController 在返回 list 时: 每个 datasource.password/passwordEncrypted 均在 VO 里置 null (不返回), 或设 "***"
- 详情接口 (getById/GET) 不返回 password (固定 null)

### 5. IKeyManagementService.createKey 拼接
- 在第一次 encrypt 前报 key 不存在异常时, 自动创建
- 保持 KeyId 全局唯一可绑数据源场景

## 绝对禁止
1. 不动其它 4 个 File 误改 (MetadataController/OntologyWorkflowController/AgentMetricsService/UserController)
2. 不动 Flyway/spring-security 其它
3. 不改 IStorageAdapter 接口 (不破坏仅增不改红线)
4. 不新建 DB 表 (复用 td_datasource 现有 password_enc 若有; 没则先 ALTER TABLE td_datasource ADD COLUMN password_enc VARCHAR(1024))

## 验收 (WSL 里)
1. mvn -pl data-engine/data-engine-impl -am install -DskipTests -q 通过
2. curl (登录取 token 后):
   - POST /api/v1/datasource (type=ORACLE, password=testpwd123) -> 201, response 内 password 字段为空或 "***"
   - 看 PG td_datasource 表 -> password 列空, password_enc 列是 ciphertext (base64)
   - GET /api/v1/datasource/{id} -> password null
   - POST /api/v1/datasource/test-connection/{id} -> 能 save 到正确 jdbc 连接
   - 改变 admin 密码 typo -> 连接失败 (报"认证失败") — 验证解密链路
3. 编译通过 (mvn install -DskipTests -q 全部)

## 如果 DAO 层无对应字段
- 先看 DataSourceEntity.java 是否已有 password_enc / passwordEncrypted
- 若无, 先加字段 (passwordEncrypted) — 只加不删
- DAO Mapper XML 的 INSERT/UPDATE 扩展 password_enc (不删其它字段)

"""

cmd = [
    "hermes", "kanban", "--board", board, "swarm",
    "--worker", "ecos-be:密码加密集成W4",
    "--verifier", "ecos-fe",
    "--synthesizer", "ecos-pm",
    "--created-by", "ecos-pm",
    "--json",
    goal,
]
r = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
print("RC:", r.returncode)
print(r.stdout)
if r.returncode != 0:
    print("STDERR:", r.stderr[:500])
