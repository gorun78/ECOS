# PMO-74 H3-T-KMS-PERSIST 密钥持久化与存量密文处置方案

> 来源: PMO（Qoder Agent 起草） | 日期: 2026-09-29 | 责任人: PMO + 安全引擎 Owner
> 版本: v1.0（待批）
> 上游依据:
> - `docs/40-实现/pmo/PMO-74-规则符合性全量审计与整改任务清单-2026-09-28.md` **§9.52**（本方案的缺陷实证，全部带 `file:line`）
> - `.trae/rules/架构铁律.md` **v2.0** §2.4#6（security 不可用默认 DENY）/ §2.5（基础设施收敛 runtime）/ §3.1（Schema 只加不删）
> - `.trae/rules/数据库访问规范.md` v1.2（EN/ST 红线）、`.trae/rules/后端开发规范.md` v1.1 §九 runtime 公共底座
> - 裁决（2026-09-29 09:02，PMO 口头裁定）：**存量密文判废 + 强制重填预案**

---

## 一、缺陷复述（一句话 + 证据锚点）

**KMS 主密钥只存进程内 `HashMap`、全仓唯一实现、零持久化**，导致所有以它加密的入库密文跨重启不可解；同时解密路径在密钥缺失时静默造新密钥、加密失败降级明文落库。

| # | 事实 | 证据 |
|:--|:--|:--|
| E1 | 唯一实现、纯内存 | `grep -rn "implements IKeyManagementService" --exclude-dir=target` → 1 命中；`KeyManagementServiceImpl.java:17` `private final Map<String, Key> keyCache = new HashMap<>();`；类注释自述 "in memory" |
| E2 | 密钥缺失时解密路径静默 `createKey` | `DataEncryptionServiceImpl.java:22-27`（`cipher(mode,keyId)` 加解密共用），`:29 Cipher.getInstance("AES")` = ECB、全文件无 IV |
| E3 | 冷启动必造新密钥、只 INFO 不告警 | `DataSourceServiceImpl.java:113-124` `ensureKey()`，`:108-109` 又 `new` 了一套独立实例 |
| E4 | 加密失败 → **明文落库**（~~`:139-141`~~ **→ 行号与文件已于 2026-09-29 实测更正，见下**） | 唯一正确 FQN = `engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/service/DataSourceServiceImpl.java`（简称 **A**）。实测锚点：`:94` 注释「降级策略：加密/解密异常 → **明文落库**/跳过回填 + WARN（降密不降级服务）」、`:132`「加密失败时**降级返回原明文**并告警」、`:139` 未注入即明文、`:146` 加密失败即明文。对岸自证 = `runtime/runtime-core/.../crypto/SecurityCryptoEgress.java:29`「数据源写路径降级明文 + WARN，**读路径返回 null**」。<br>**同名类陷阱**：`find . -name DataSourceServiceImpl.java -not -path '*/target/*'` = **2**；另一枚 B=`.../engine/data/datasource/service/impl/DataSourceServiceImpl.java`（`@Service("ecoDsDataSourceService")`，实现 `IDataSourceService`）**不承载 E4**，其 `password` 仅出现在 `:324/:337` |
| E5 | ~~密钥库本体同受累：secret 值加密后**落库**~~ **→ 已证伪，见 E5' ** | `SecretServiceImpl.java:72-78` 确实用 `default-master-key` 调 `encryptionService.encrypt(...)` 后写入 `secret.setSecretValueEncrypted(...)` |
| **E5'** | **密钥库根本不持久化**（本行取代 E5 的「落库」表述，2026-09-29 实测） | 三源取证：① `SecuritySecretDaoImpl.java:24-26` = `secrets`/`shares`/`accessLogs` 三个 **`ConcurrentHashMap`**，文件内无 JDBC/SQL；② `SELECT ... FROM information_schema.tables WHERE lower(table_name) LIKE '%secret%'` → **0 行**（全 schema）；③ `grep -rni --include=*.sql "create table.*secret" ecos_backend ecos-sql` → **0 命中**。装配点 `CryptoBeanConfig.java:41 return new SecretServiceImpl(...)`。**后果比 E1 更严重**：密钥库条目进程重启即全量消失（不是「不可解」而是「不存在」），但**不产生存量密文** |

**影响面对象（已实证）**：~~`td_datasource.password_enc`（`DataSourceEntity.java:34` 注释确认列名）~~ → **2026-09-29 实测扩围，本行由 R1' 取代**：泄漏面不止 `password_enc` 一列，见下方 R1' 块。
**~~待核：`SecretServiceImpl` 落库表名~~** → **已核并证伪**（见 E5'）：密钥库**无表、无落库**。~~故本方案的存量影响面 = **仅 `td_datasource.password_enc` 单一对象**~~ → **该「单一对象」结论已作废**：`td_datasource.connection_config` 才是当前唯一的**明文**泄漏通道（`password_enc` 只是「密文不可解」问题）。二者性质不同，必须分开整改（见 R1' 与本文件 §七）。

---

## 二、目标与非目标

**目标**
1. 主密钥跨重启、跨实例稳定（同一 keyId 恒得同一密钥材料）。
2. 消除 fail-open：加密/解密失败一律显式失败，禁止明文入库。
3. 消除静默密钥替换：密钥缺失 → `KEY_NOT_FOUND`，不与"密文损坏"混淆。
4. 密文改造为带模式参数的认证加密（AES/GCM + 随机 IV）。
5. 存量密文按裁决**判废**，并提供可执行的强制重填运维预案。

**非目标（本方案不做）**
- 不接外部 Vault/KMS 基础设施（AGENTS.md「Don't add new Docker containers」基线约束）→ 登记为 ultimate 档后续可选演进。
- 不动 `password_enc` 列、不 DROP 任何表（铁律 §3.1 只加不删）。
- 不改任何 API 路径与签名（铁律「API 只增不改」）。

---

## 三、密钥持久化设计（三选一，含推荐）

| 方案 | 做法 | 优点 | 缺点 | 结论 |
|:--|:--|:--|:--|:--|
| **S1 纯 DB 存 DEK（明文）** | 新表存 AES key 字节 | 改动最小 | 密钥与密文同库同权限，DB 泄露即全量可解；形同加密失效 | **拒**（安全上无意义） |
| **S2 信封加密（KEK 包 DEK）** | 环境变量/注入链提供 KEK master secret；新表 `ecos_security_kms_key(key_id, wrapped_key, iv, tag, algo, key_version, created_by, created_at)` 存被 KEK 加密的 DEK；解密 = KEK 解 wrap → 得 DEK | 支持轮换（`key_version` 多行并存）、DB 单独泄露不致明文、KEK 与数据分离 | 需新表 + wrap/unwrap 逻辑 + 轮换流程 | **终态** |
| **S3 确定性派生（HKDF）** | 由 master secret + keyId 经 HKDF-SHA256 派生 DEK，**不落库任何密钥** | 最简、无新表、天然跨实例一致（同 master 同 keyId 必同结果）；立刻消除 E1/E3 | 无轮换（换 master = 全量作废，但本场景旧密文**已裁决作废**，代价可接受）；master 需外部保管 | **MVP（推荐先落）** |

**推荐路线 = S3 先落地止血 → S2 补轮换**，两者共用同一实现类，S3 期不建表，S2 期加表与 `key_version`。

**master secret 注入链复用既有范式**（与 `JWT_PRIVATE_KEY`、`DEEPSEEK_API_KEY` 同路，见 AGENTS.md 与 `_win_tasks/start-backend.ps1`）：
进程环境变量 `ECOS_KMS_MASTER` → `~\.config\ecos\.env` → 仓库根 `.env`；**缺失时服务启动即失败（fail-closed），不得回退随机密钥**（这是与现状最关键的行为反转，见 K-T3）。

> 注意：master secret **禁**写入 `sys_config` 明名列（《数据库访问规范》EN 红线 + 铁律 §3.6 配置字典单源），只走注入链。

---

## 四、配套必改项（与持久化同等必要）

| 编号 | 改动 | 现状证据 | 判据 |
|:--|:--|:--|:--|
| K-T1 | `KeyManagementServiceImpl` → 无状态派生实现（S3），`getKey/getKeyBytes` 不再依赖 `HashMap` 缓存；`createKey` 对派生模式改为**幂等登记**而非生成随机 key | `:17/:20/:32` | 同 keyId 两次实例化得同一 `getEncoded()` |
| K-T2 | `DataEncryptionServiceImpl` 拆 `cipher(...)` 为 `encryptCipher`/`decryptCipher`；**解密路径禁止 createKey**，密钥缺失抛 `KeyManagementException("KEY_NOT_FOUND:"+keyId)` | `:22-27` | 空 cache 下 decrypt → `KEY_NOT_FOUND`，而非 `DEC decrypt failed` |
| K-T3 | `Cipher.getInstance("AES")` → `"AES/GCM/NoPadding"` + 随机 12B IV 前置；密文加版本前缀 `KMSSIV2:`；`ENC:`（旧 ECB）识别为**已作废**并返回专用错误码 | `:29-30` | 同明文两次加密得不同密文（GCM 随机 IV 生效）；旧前缀不再被接受 |
| K-T4 | **删除 E4 的明文降级**：`encryptPassword` 异常必须上抛（→ 保存数据源失败），不得 `return password` | ~~`DataSourceServiceImpl.java:139-141`、注释 :97~~ → **FQN 已钉死（见 E4 更正）**：`engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/service/DataSourceServiceImpl.java`（A）`:132/:139/:146` + 注释 `:94`；**禁动 B**（`.../datasource/service/impl/`） | ~~`grep -n "降级明文" DataSourceServiceImpl.java` 命中 0~~ **该判据作废（裸文件名 + 同名 2 枚 = 假绿源）**，改为三条：① `grep -n "降级明文" <A 的完整路径>` 命中 0，且 `grep -rn "return password" <A>` 命中 0；② 单测 mock `SecurityCryptoEgress` 缺失 → 断言异常上抛且**无 DB 写**；③ 只读 `SELECT` 复跑：`td_datasource` 中 `connection_config` 含非空 `"password"` 行数 **2→0**（基线 2 见 §七 R1'） |
| K-T5 | 消除 `new`：`DataSourceServiceImpl:108-109` 改构造器注入；`SecretServiceImpl:37` 改注入；`EncryptedFieldProcessor:24-25` 保留双构造器但由 Spring 走带参构造 | 四处 `file:line` | AGENTS.md「Don't bypass `@Autowired` with `new`」grep 门 0 命中（`@Bean` 工厂方法除外） |
| K-T6 | `resolvePassword` 解密失败语义：从"静默保持原 config"改为**返回明确的 `KEY_INVALIDATED` 提示 + 审计事件**，前端数据源编辑页强制要求重填 | `:154-162` 注释 | UI 侧可观测：状态位 + 文案，不再"看起来正常但连不上" |

---

## 五、存量处置预案（按裁决：判废 + 强制重填）

**R1 盘点（只读，零风险，先做）**
```sql
-- 只读 SELECT，禁任何写/DDL
SELECT count(*) AS ds_total,
       count(password_enc) AS ds_with_encrypted_pwd
FROM   td_datasource;
```
`SecretServiceImpl` 落库表名核实后补第二条同等只读盘点。产出：受影响数据源条数 + 清单（脱敏：只出 id/name/owner，不出密文与明文）。

**R2 判废动作（不改数据，改语义）**
- 旧密文（无 `KMSSIV2:` 前缀）一律视为不可信：`decrypt` 直接返回 `KEY_INVALIDATED`，**不做任何解密尝试**（避免用新密钥解旧密文的无意义失败与误读）。
- **不清空 `password_enc`**：保留原值供审计追溯；重填成功后覆盖为新格式密文。

**R3 强制重填通路**
1. 数据源列表/详情页：`KEY_INVALIDATED` → 行内标红「凭据已失效，请重新输入」+ 阻断"测试连接"按钮。
2. 批量运维：提供**受控**的一次性重填入口（管理员逐条输入，逐条写审计），**禁**提供"批量导入明文密码"接口（防凭据旁路）。
3. 密钥库 secret 条目：同上，重录而非解密恢复。

**R4 沟通与窗口**
- 升级公告必须写明「本次升级会使既有数据源密码与密钥库条目失效，需重填后才可连接」，并给出受影响条数（来自 R1）。
- 建议在低峰窗口发布；发布前先跑 R1 盘点确认影响面可接受。

**R5 回滚**
- 代码侧可回滚（K-T1~T6 均为向后不兼容改动，回滚即回到旧 ECB + 内存密钥；旧密文在回滚后仍不可解，因密钥早已丢失 → 回滚不恢复数据，仅恢复行为）。
- **数据侧不可逆**：判废是语义判废、未删数据，因此不需要 DB 回滚；但一旦运维开始重填，旧密文即被覆盖 → 重填前 R1 盘点结果须留档。

---

## 六、实施分批（铁律 §5.2：每批 ≤5 Task，Task = 单文件 + grep/curl 验收）

| 批 | Task | 单文件 | 验收 |
|:--|:--|:--|:--|
| **K-B1 止血（无新表）** | K-T1 | `KeyManagementServiceImpl.java` | 新单测：两实例同 keyId → `getEncoded()` 逐字节相等 |
| | K-T2 | `DataEncryptionServiceImpl.java` | 单测：未知 keyId decrypt → 错误码含 `KEY_NOT_FOUND` |
| | K-T3 | `DataEncryptionServiceImpl.java`（同文件顺序改） | 单测：同明文两次密文不等；旧 `ENC:` 走 `KEY_INVALIDATED` |
| | K-T4 | `DataSourceServiceImpl.java` | `grep -c "降级明文" = 0`；单测断言异常上抛 |
| | K-T5 | `DataSourceServiceImpl.java`（注入改造） | `grep "new KeyManagementServiceImpl" = 0`（该类内部与 `@Bean` 工厂除外） |
| **K-B2 存量面** | R1 盘点 | 只读 SQL 记录进本文件 §七 | SELECT 结果原文留档（脱敏） |
| | K-T6 | 前端数据源编辑页单文件 | UI 可观测失效态 + 阻断测连 |
| **K-B3 轮换（S2）** | 建 `ecos_security_kms_key` | DDL 落 `gateway/src/main/resources/db/migration/` 单源 | `db-migration-lint.ps1` 0 FAIL；只加不删 |
| | wrap/unwrap + `key_version` | `KeyManagementServiceImpl.java` | 轮换后旧版本密文仍可解、新版本可读 |

> **H3-T-ARCH 与 K-B1 的关系**：K-B1 做完后，`data-engine-impl → security-engine-impl` 的编译依赖（`pom.xml:39`）**仍在**，ArchUnit 生产违例 11 条**不会因此消红**。二者是正交的两件事，禁把"KMS 修好了"当成架构项已过。H3-T-ARCH 推荐方案 = **C（仿 `SecurityEngineBridge`：类名反射 + 可选 bean + fail-closed，密钥出口收敛 runtime）**，仍待批。

---

## 七、盘点留档区（执行 R1 后回填）—— **已回填（2026-09-29 11:24，实测）**

```
容器: ecos-postgres  Up 3 hours（Windows Docker Desktop）；库: sys_man；全程只读 SELECT，零写入零 DDL
命令: docker exec ecos-postgres psql -U postgres -d sys_man -tA -c "SELECT count(*), count(password_enc) FROM td_datasource;"
结果: 5 | 1
      → td_datasource 总 5 条，其中 password_enc 非空 1 条 = 存量判废影响面（唯一）
第二条盘点: 【取消】—— E5' 已证密钥库无表（information_schema LIKE '%secret%' 0 行 + DDL grep 0 命中），无对象可盘
脱敏说明: 本轮只出计数，未取 id/name/owner，也未触碰任何密文/明文值
```

**R1 结论对本方案的影响**：影响面从「数据源 + 密钥库条目」两类收缩为 **1 条数据源凭据**。R3 的第 3 点（密钥库 secret 条目重录）**失去对象**，改为一条独立缺陷登记：「密钥库全量不持久化」属**功能缺失**（不是密码学缺陷），应单列 Task 而非混入 KMS 判废预案 —— 已登记至 PMO-74 主台账 §9.61 四。

### R1'（2026-09-29 12:0x 复跑，扩围盘点）—— **取代上面「影响面 = 1 条」的口径**

R1 只数了 `password_enc` 一列，据此得出「存量影响面 1 条」。**这个口径漏掉了真正的明文通道**，本轮按列分别盘点（全程只读 `SELECT`，敏感值只出长度与形状）：

```
容器 ecos-postgres / 库 sys_man / 只读 SELECT，零写入零 DDL
① password_enc（密文列）：total=5，非空=1（len 24，id 前缀 79b0aba0）      ← R1 原口径，数字仍成立
② connection_config JSON 内含 "password" 键 = 2；其中值非空 = 2            ← R1 完全未计
   id 前缀: bfbd7dd2…, b6431abd…  （两行 password_enc 均为 0 → 走的就是 E4 明文降级路径）
③ connection_config 内含 "passwordEncrypted" 键 = 0（密文只可能落在 ① 列）
④ 运行期出口：GET /api/v1/datasource（admin token）5 行全回显
   connectionConfig(字符串形态) + username(5/5) + host/jdbcUrl(5/5，含容器内网 IP 172.18.0.44)
   其中 2/5 行体内直接携带明文 password(len 8)
   链路取证见主台账 §9.65：PMO45DataSourceController:56 → DataSourceRegistryService:41
   →（Caffeine dsListCache，:23）→ engine.data.service.DataSourceServiceImpl.listAll()
```

**判**：
- 「密文列填充率低」（1/5）与「明文列被回显」（2/5）是**两件事**；R1 的口径让前者掩盖了后者。**若只看 `password_enc` 填充率上升，验收会假绿。**
- KMS 持久化（K-B1）修的是 ①：**已加密的解不开**；②③④ 属**根本没加密 + 出口不掩码**，K-B1 完工**不会**自动消掉。→ KMS 判废预案与明文泄漏整改必须分为两个 Task，禁合并报告为「KMS 已处置」。
- E4 的降级路径已被实证走过 ≥2 次，不是理论分支。K-T4 判据的基线数即此处的 **2**。
- 环境缓解（不据此降级优先级）：两处明文 len 8 与本仓 `AGENTS.md` 已公开的本地 PG 口令同长度 → 当前 dev 库无额外暴露；同一代码路径在生产库上会照样明文落、照样回显。严重度按代码路径判。

## 八、状态

- **R1 盘点 = 已执行（DONE，只读）**；~~影响面 = 1 条~~ → **已被 R1' 取代**（`connection_config` 明文 2 条 + 出口不掩码，属独立缺陷）。**K-B1 止血（K-T1~K-T5）= 已分析、未执行**，本文件仍未改任何 Java/SQL/前端源码。
- **★拆单结论（新增）**：本方案（KMS 持久化 = 密文跨重启可解）与 **H8-T-DS-PLAIN**（写侧 fail-closed + 读侧掩码）是两个 Task，**不可互相顶替、不可合并报 DONE**。判据各自独立：前者看 `ensureKey`/密钥落盘，后者看 §七 R1' 的「明文行数 2→0」与响应体键命中 0。
- **E5 → E5' 反向更正已完成**（原文保留于 §一 表内，划线标注）。**E4/K-T4 的 FQN 与行号更正已完成**（2026-09-29，见 §一 E4 行与 §七 R1'；主台账对应反向更正 #16/#17）。
- 待你批：① S3→S2 路线（已裁「S3 MVP 再上 S2」，此项已闭）② `ECOS_KMS_MASTER` 注入落点 —— 建议沿用 `~\.config\ecos\.env`（与 `JWT_PRIVATE_KEY`/`DEEPSEEK_API_KEY` 同范式；该文件实存 597 B）。**注意 K-T3/§三 的「缺失即 fail-closed 启动失败」是对现状的行为反转，会让本机 7 个服务在缺 master 时全部启不来，风险面大于既往任何一项，须单独确认后再开工** ③ K-B1 是否即刻开工 ④ 新增：「密钥库不持久化」是否并入 H3 还是单列新 Task。
- 原 ④「H3-T-ARCH 方案 A/B/C 取哪个」= **已闭**（裁决 C 已实施并验收，见主台账 §9.59 / §9.61 一~三）。
