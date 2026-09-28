# ECOS 工程规范知识库索引（.trae/rules 要点摘录）

> 来源: 用户指令「将 .trae/rules 写入项目知识库」（承接 `docs/40-实现/ecos-工程现状分析-2026-09-28.md`）
> 日期: 2026-09-28
> 责任人: Qoder Agent（摘录）/ PMO（规则维护）
> 上游依据: `.trae/rules/` 全部 9 份规则文件
> **单一事实源声明**: 本文件仅是检索索引与要点摘录，**规范正文唯一权威出口为 `.trae/rules/` 各源文件**；两处表述不一致时以源文件为准。凭据类内容按文档编写规范 R7 不复制到 docs/，仅指向源文件。

---

## 一、规则文件总览

| 文件 | 版本 | 日期 | 定位 | 强制级别 |
|:--|:--:|:--:|:--|:--|
| `架构铁律.md` | v1.8 | 2026-09-28 | 宪法，所有 PMO 指令开头必引；与其他规范冲突时以铁律为准 | 🔴 违反任一 = 验收不通过 |
| `数据库访问规范.md` | v1.1 | 2026-09-28 | IR/DR/EN/ST/MC 共 30 条数据层红线 + 数据域二分 + Schema 归属（5+1）+ 多库兼容 | 🔴 违反 = Reviewer FAIL |
| `后端开发规范.md` | v1.0 | 2026-09-22 | Java/Spring/MyBatis 通用编码规范 | 🔴 强制 |
| `前端开发规范.md` | v1.0 | 2026-09-22 | TS/React/Tailwind 通用编码规范 | 🔴 强制 |
| `Git提交规范.md` | v1.0 | 2026-09-22 | 分支管理 + commit message + DONE 凭证 | 🔴 强制 |
| `文档编写规范.md` | v1.0 | 2026-09-22 | 文档红线 R1~R13 + 15 类模板 + Agent 自检 8 项 | 🔴 违反 = FAIL |
| `文档目录规范.md` | v2.0 | 2026-09-22 | 14 一级目录定义 + 命名 + 旧目录映射（与编写规范互补：目录定义 vs 行为规范） | 🔴 强制 |
| `数据湖存储分层规范.md` | v2.0 | 2026-09-28 | 存储分层口径唯一规则出口（数据域二分 + 五层 + 知识双形态 + zone/对象 key + 版本×分层矩阵） | 🔴 强制 |
| `开发环境登录凭据.md` | v1.0 | 2026-09-20 | dev/test 登录凭据唯一权威出口（红线文件，凭据内容不在本文复制） | 🔴 Agent 出手前必读 |

---

## 二、架构铁律 v1.8 要点（宪法）

### 2.1 版本演进脉络
- **v1.1**（09-11）：单体 fat-JAR → 微服务 v2（7 独立 JAR）；P8-A/B/C 目录迁移完成（保 artifact 稳定、仅动物理目录、零 import 改动）。
- **v1.2**（09-16）：§1.2 三滤波器豁免写法判据修正——实证**路径重写先于鉴权**（`VersionPrefixRewriteFilter @Order(MIN+10)` 早于 Security `-100`），废止「双路径各写一遍」机械表述；触发事故：误补 `/api/integration/**` permitAll 致未认证可读数据源连接信息。
- **v1.3**（09-19）：新增 §0.5 三工作台职责边界。
- **v1.4**（09-24）：DIKCW 升级为 **Enterprise Cognitive Loop**——主链 `D→I→K→C→W` + 现实反馈链 `W→D`；「明」由 K→W 重定义为 **C→W**（K→W 仅作确定性知识驱动自动化特殊通道）；`C = f(D,I,K,Context,Evidence,Hypothesis,Belief,Cognitive Model)`，禁「C=f(K)」理解；W = Decision/Strategy/Policy/Action/Execution，**禁 AI=W**。
- **v1.5**（09-24）：认知（木 C）**前端**归属从知识工作台移入 AI 工作台（系统1直觉/系统2审慎一体两面）；引擎层、数据边界、C↔AI 职责边界三样不变。
- **v1.6**（09-26）：新增 §0.6 场景工作台职责边界（见 2.5）。
- **v1.7**（09-28）：开发环境事实对齐——§1.5 构建命令 Windows 原生化；§六 WSL 铁律废止（历史存档）；§5.1 #13/#14 启动脚本引用改为 _win_tasks/ 4 入口脚本。零架构约束增删。
- **v1.8**（09-28）：数据域二分——§3.1 补 Schema 归属「5+1」与多库兼容目标；新增 §3.5 控制域 vs 业务域铁律（配套 DB 规范 v1.1 / 湖规 v2.0）。

### 2.2 分层与依赖（§0.1~§0.3.1）
- 系统分层：前端 → gateway(:8080 唯一对外) → 服务层四转化(格致诚明) → 引擎层五对象(五行)；横切 = 护(security-engine) + 器(runtime)。
- **依赖方向铁律**：`workspace →(REST)→ services/* →(Maven)→ engine-impl → engine-api → common-api`；横向/反向依赖一律拒绝；下层禁 import 上层；跨模块走 `PipelineEvent`(common-api) 或 REST。
- 命名铁律：ge/zhi/cheng/ming 只可用于服务层转化，禁用于引擎层对象管理模块；对象代码与转化逻辑不得混在同一模块。
- 引擎 api/impl/boot 三模块；boot 仅独立调试（非生产入口），与 service 同端口互斥需 `--server.port` 错开。
- 服务端口仅内网可达（ADR-7），直连 18081~18090 默认 DENY。
- 三档发布（§0.4）：standard=PG / enterprise=+Neo4j（因果链>3层）/ ultimate=+Doris（单表>100万行）；不加新 Maven 模块（基线 13）、不加新 Docker 容器。

### 2.3 横切收敛铁律（§2.4 安全 / §2.5 runtime）
- 安全八条：RLS 行过滤、CLS 列过滤、脱敏、OPA ABAC 裁决、写操作发 Kafka `ecos.audit`、security 不可用**默认 DENY**、禁引擎内重复实现安全、task card 涉密必列加密/解密/脱敏集成项（grep 命中 0 = FAIL）。
- runtime 六条：基础设施 Driver 统一 `runtime-access`、LLM 调用统一 `llm-gateway`、调度统一 `runtime-task`、监控统一 `runtime-monitor`、基础工具统一 runtime 工具类、**发现公共能力缺失先补 runtime 不自建**。
- 任务统一入口（§1.6，4 条）：即时任务走 `ITaskManagementService.submitTask+executeTask` 同步等真实 `taskId`；定时任务禁自建 `*_scheduled_*` 表、统一 `td_runtime_task_plan`；监控走异步任务中心整体视图；审计表与 runtime 5 表职责正交。

### 2.4 三工作台边界（§0.5，五条铁律）
数据工作台(D)=存储与管理、本体工作台(I)=模型与映射契约、知识工作台(K)=语义实例化，单向链路。五条：① DW 层写入权只属数据工作台（其余只读）② 契约先行（知识必消费 `ecos_entity_table_mapping`，不得自行推断表↔实体）③ 语义不上移 ④ 图谱不回写 DW ⑤ 映射保存必校验表/列存在性与类型。

### 2.5 场景工作台边界（§0.6，v1.6 新增）
- 定位：顶层场景应用层（workspace :18090），DIKCW 闭环 **W 侧执行与收口**；不生产能力，只编排能力。
- 场景定义：可执行/可编排/可模拟/可回放的**业务情形容器**；主实体「场景」**非「项目」**，禁 `ecos_project`；四态 `DRAFT→ACTIVE→COMPLETED`（可 SUSPENDED）；场景≠资源；同场景可多次 run。
- 组成模型（🔴 子图+横切+出口，非平铺清单）：六类绑定分三层——链路节点 `DATASET/OBJECT_TYPE/KNOWLEDGE_BASE/AI_AGENT`（有序有向主链）/ 横切约束 `SECURITY_POLICY` / 出口 `INTERFACE`。关系矩阵：映射(格,引用 `ecos_entity_table_mapping`)、抽取(致)、认知(诚)、行动(明/W→D)、约束、暴露。
- 三条铁律：**禁孤岛绑定**（必须保连边连通）；**契约先行**（继承 §0.5，场景层不自造映射语义）；**完整度按真实连边覆盖率计，禁按绑定数量算分**。DDL 方向：新增边表 `ecos_scenario_binding_link`。
- 排他：使用对象由 `department`+`SECURITY_POLICY` 绑定界定；「场景」≠ AI 域「项目跟踪」（ProjectTracker 名词不得混用）。
- 沙盘三铁律：画布不产生资源；乐观锁 `layout_version`（冲突 409）；演练必经 services REST 真调认知引擎，**禁 stub/mock 落 UI**。

### 2.6 数据域二分与 Schema 归属（§3.1/§3.5，v1.8 新增）
- **控制域**（平台运行数据）：PG 承载，Schema「5+1」——引擎独立 schema 仅 `ecos_data/ecos_ontology/ecos_knowledge/ecos_ai/ecos_cognitive` 五枚举，其余控制数据统一主控制 schema（现 `public`→目标 `ecos_control`）；横切（security/runtime）与 sysman/workspace/service 层**不独立成 schema**。
- **业务域**（外部接入+加工数据）：走五层模型（近源/DW/知识[图谱+向量]/应用），禁入控制 schema；控制域禁写湖/OLAP/向量载体；**跨域只存定位符**（对象 key / `schema.table` / datasourceId / 节点 ID）。
- 控制域多库兼容目标：PG 基线 + 兼容 MySQL/Oracle/MSSQL/达梦/人大金仓（MC 红线）；Doris∨ClickHouse、pgvector/向量引擎为业务域专用**受控例外**。
- 细则出口：DB 规范 v1.1 §〇/§二附(ST07~09)/§十附(MC01~06) + 湖规 v2.0 §〇/§七A；工程调整 13+1 项见两文件附录。

### 2.7 后端服务层高频坑（§1）
- 三滤波器四步（§1.2）：①`VersionPrefixRewriteFilter` V1_REWRITE_MAP ②`SecurityConfig`（先判是否应匿名，业务数据端点**不写 permitAll**）③`ClearanceInterceptor`（只见最终路径）④各 service `HeaderAuthInterceptor`（`X-ECOS-*` 头还原上下文）。豁免三步判据：应匿名否→前缀在重写表内否（在→只写裸路径；不在→两形式各写）→收尾匿名回归（无 token 403 / 带 token 200）。
- Ant 陷阱：含连字符路径不保证被父级 `/**` 匹配，须显式写完整前缀；近似前缀兄弟（`/object` vs `/objects`）按精确/最长前缀优先消歧。
- DI：构造器注入；禁 implements 已有 Service 接口（多 Bean 冲突）；新 Bean 加 `ecos` 前缀。
- Maven：编译=`mvn install`（非 compile，Gateway 从 .m2 加载）；重命名模块必删 .m2 旧 artifact。
- 引擎间只调 API 不调 Impl；新增引擎 Controller 必在 GatewayApplication excludeFilters 排旧副本；引擎端点统一 `/api/v1/engine/{type}/...` 并追加引擎 AGENTS.md 清单。

### 2.8 前端铁律（§4）
- 禁硬编码 Tailwind 颜色（用 `useTheme().styles`，4 主题）；禁硬编码中文（`t("ns.key")`）；图标仅 lucide-react；组件文件 ≤800 行、每 Tab 独立文件、主 Layout <300 行；HashRouter。
- 前后端契约四条（§4.8）：列表摘要不得渲染编辑态（必调详情拉全量）；枚举前后端同源；写后必重拉列表；DELETE = 逻辑删除语义（status→ARCHIVED）。

### 2.9 PMO 执行铁律（§5）
- 禁止清单 14 条（跨 Phase 预创建、implements 冲突、.m2 旧 JAR、三滤波器漏更、compile 代 install、硬编码颜色/中文、自定义 SVG、新建模块/容器、未过 lint 门交付、列表渲染编辑态、be_win.bat 启动、**运行中创建临时脚本**——探测用内联命令/调试用 logger/构建用 `_win_tasks/` 现有 4 脚本）。
- 原子任务 = 单文件 + curl 验收 + 工期；单指令 ≤5 Task；验证四步法 V1 生存→V2 集成 grep→V3 编译门→V4 启动+curl（涉前端加浏览器 E2E 三项：渲染/console/network）。
- curl 陷阱：PowerShell 内联 JSON 转义→用 `--data-binary @file`；中文 body 必 UTF-8；断言看 `ApiResponse.code`+`success`（HTTP 可能仍 200）。

---

## 三、数据库访问规范要点（30 条红线，v1.1）

### 3.1 IR01~IR06（P0 级）
IR01 gateway/顶层 service 禁直持 PG 连接（必经 engine-*-impl 三层）；IR02 Flyway 永久禁用（7 处 yml 锁定），变更走手动 `psql -f` + seed.sql；IR03 禁 DROP/ALTER/DROP COLUMN（只加不删）；IR04 禁 `SELECT *`；IR05 禁裸 SQL 拼接（必 `#{}`/PreparedStatement）；IR06 禁自建 Driver（统一 runtime-access）。

### 3.2 DR01~DR08（命名 DDL）
表名小写下划线 + 新表强制 `ecos_` 前缀 + 单数；JSONB 加 `_json`；布尔 `is_` 前缀；审计 5 字段固定 `create_time/update_time/create_by/update_by/is_deleted`；`version_no VARCHAR(20) NOT NULL` 乐观锁；新表必带 `domain VARCHAR(50) DEFAULT 'default'`。主键推荐 UUIDv4（VARCHAR(36) + `gen_random_uuid()`）。索引 `idx_` / 唯一 `uniq_`；过期索引不删只注 `-- DEPRECATED`。

### 3.3 EN01~EN03 + ST01~ST06
EN：HikariCP 连接池；事务边界在 Service、单事务 1 个 commit 点；批量 `REQUIRES_NEW` + 单批 ≤100。数据流单向：data-engine 主写近源层，Ontology/Knowledge 跨引擎读只走 runtime-access。ST：表名禁 `std_/ent_/ult_` 前缀（用 schema+@Profile）；跨档差化必 `@Profile` 守卫；敏感列 AES-256-GCM+KMS 加密；列过滤/行过滤调 security-engine REST；写操作发 Kafka `ecos.audit`。

### 3.4 流程与存量
迁移：`V{n}__{描述}.sql` 入 gateway db/migration（单调递增）+ 手写反向 rollback + psql 应用 + V{n}/seed 同 commit。DDL 审查清单 13 项必跑；lint 脚本 `_win_tasks/db-migration-lint.ps1` 实跑 15 项（0 FAIL / 8 WARN，详见 `docs/40-实现/数据库现状盘点与schema归属映射-2026-09-28.md` §六）。存量裁定（V150 落地）：`workflow_*` 不 RENAME（knownLegacy）；`created_at` 57 文件 218 字段走 RENAME+`v_*` 兼容视图双轨；JSONB 无后缀历史不动；96 张表缺 domain/version_no 存量不补；gateway 上帝模块不加新文件，新表 DDL 入 runtime-access migration 目录。

### 3.5 v1.1 新增（2026-09-28）
- **ST07~ST09 Schema 归属（5+1）**：引擎 schema 仅五枚举（data/ontology/knowledge/ai/cognitive），其余控制数据统一主控制 schema（现 `public`→目标 `ecos_control`）；业务数据禁入控制域（ST08）；跨引擎自家 schema + REST/runtime-access（ST09）。非五引擎存量 schema（`ecos_sysman/ecos_security/ecos_infra/ecos_dq` 等）标 knownLegacy，只禁新增。
- **MC01~MC06 多库兼容（目标态）**：新代码可移植 PG/MySQL/Oracle/MSSQL/达梦/金仓——UUID 应用侧生成（禁 `gen_random_uuid()`）、类型白名单（禁 `timestamptz`/PG 数组）、PG 专有语法走 `databaseId` 方言分支、Doris∨ClickHouse 单部署二选一且仅业务域、向量降级存须标记 `_json`、schema 名配置化（禁硬编码前缀）。
- **§三 三档表重写**：控制域三档同构；差异全落业务域载体（详见湖规 v2.0 §七A）。
- 红线总数 23→**30**；工程调整清单 #1~#13（lint 白名单、数据源按域治理、public 清点、pgvector ADR、CH sink 等）。

---

## 四、后端开发规范要点

- AI 编程七准则：低冗余高整洁 / 确定性（拒万能兜底）/ 项目一致性 / 分层单一职责 / 安全健壮优先 / 禁魔改全局配置 / 同类逻辑抽象公共方法。
- 分层：Controller 禁业务逻辑与 SQL；Service 禁持 Http 对象；Dao 禁业务逻辑；Entity 仅字段注解。
- 接口：RESTful、统一返回体、分页 `pageNum/pageSize`；**入出参必强类型 DTO/VO，禁 Map**；返回 `ApiResult<T>`；`XxxQuery`/`XxxSaveDTO`/`XxxVO` 命名。
- Lombok：Entity 用 `@Getter+@Setter+@NoArgsConstructor`（**禁 @Data**）；DTO 用 `@Data`。
- 控制流必带花括号；异常全走日志框架 + `DataBridgeException` 体系；禁 `select *`、无条件更新删除、循环查库；禁硬编码密钥、日志打印敏感数据。
- 编码后自检 7 项（格式命名/分层/参数安全/SQL/异常日志/性能整洁/上线就绪）。

## 五、前端开发规范要点

2 空格缩进、必分号、全 ES6+；Hooks `use` 前缀、组件大驼峰、CSS 短横线；TS 禁滥用 any；React 仅函数组件+Hooks、Props 必 TS 约束；禁硬编码接口地址/密钥、统一封装 request；销毁清定时器、高频防抖节流、路由懒加载；注释仅注复杂逻辑与公共函数；输出无 console/debugger 残留。

## 六、Git 提交规范要点

- 单一提交（一次一件事）；禁提交调试代码/密钥/本地产物；main 禁直提（MR/PR 合入）、dev 开发主干、test 测试专用；分支命名 `类型/模块-描述`（英文）。
- Message：`type(模块): 动宾描述`，type 8 枚举（feat/fix/refactor/perf/style/docs/test/chore）；功能与格式优化分两次提交。
- **commit hash = DONE 凭证**：working tree 未提交不算交付；验收用 `git log --grep` 溯源；禁主干强推。

## 七、文档双规范要点

- **目录宪法（生命周期五类，2026-09-28 v3.1）**：10-架构/20-需求/30-设计/40-实现/50-测试；模块信息进文件名，不为模块开一级目录；旧 14 镜像目录与 `*-legacy/` 归档已按批准清除，溯源走 `docs/迁移映射-2026-09-28.md`。
- **红线 R1~R13**：头部必带来源/日期/责任人 + 版本；文件名小写+短横线+日期（数字前缀 2 位）；类型白名单 .md/.json/.mjs/.html/.sh/.txt；docs/ 禁图片；深度 ≤3；**禁明文密钥**；删除需用户/PMO 批准+hash 留痕+映射表登记；现行有效文档只加不删（v1.2：过期归档/目录壳批准后可删）；相对路径引用禁 `<TODO>`；新文档必入五类目标态目录；禁零散迁移（批量+映射表）。
- 15 类文档模板（宪法/ADR/PMO/验收/审查/手册/指南/PRD/差距/联调/测试/运维/契约/参考/归档），Agent 写文档自检 8 项；文档-代码联动：模块增改同步建目录+README、PMO 完成写交付报告。

## 八、数据湖存储分层规范要点（v2.0）

- **五层模型**（`DataLayer` 枚举 Java 侧唯一权威，禁用 ODS/DWD/DWS 名）：SOURCE 源系统 → RAW 近源层（MinIO）→ CURATED DW 层（PG/Doris）→ SEMANTIC 语义层 → APPLICATION 应用层；`CURATED→SEMANTIC`=格、`SEMANTIC→K`=致。
- RAW 两 zone：`STRUCTURED`（dt 日期分区、90 天滚动）/`UNSTRUCTURED`（docId 寻址、长期保留）；同 bucket 前缀分区，不加 bucket/容器。
- 对象 key：`raw/structured/{source}/{table}/dt=YYYY-MM-DD/...csv`、`raw/unstructured/{source}/{docId}/{fileName}`；旧 `datalake/` 前缀只读不新写；禁新增 lake/ods 等前缀。
- 读写边界：数据工作台近源读写+DW 读写；本体/知识**禁直读近源**、DW 只读；场景工作台只经服务层 REST 读；分层标记失败仅 warn 不拖垮管道。
- `td_data_resource`：`layer/zone/resource_type/source_path` 口径 + 合法性矩阵（仅 RAW 允许 zone 非空）；LAKE_OBJECT 需显式登记。
- 缺口（P3）：parquet 写入器、DQ `buildContext()` 空转、`AutoDiscoverPreviewController` 遗留 `td_datasource` 直查、kb `kb_doc_chunk` A3 过渡态待退出。已闭合：SOURCE_MINIO/TRANSFORM_DOC_PARSE 链路、文档解析上移 runtime-access、本体越界直查归零、前端分层视图接入。

**v2.0 新增（2026-09-28）**：
- **§〇 数据域二分**：五层模型只描述业务域；控制域（平台运行数据，PG 5+1 schema）禁登记 `layer`、禁落湖/OLAP/向量；跨域只存定位符。知识层定义态元数据属控制域（`ecos_knowledge`），内容形态（图谱/chunk/向量）属业务域。
- **知识层双形态**：语义图谱（结构数据 I→K 抽取 → Neo4j / standard PG 表）+ 向量库（非结构化 chunk embedding → pgvector/专用引擎）；两形态分库分承载，禁混存；embedding 模型调用经 `llm-gateway`。
- **§七A 版本×分层矩阵**：近源=MinIO 三档同构；DW/应用 standard·enterprise=PG `ecos_dw`，ultimate=**Doris ∨ ClickHouse**（`dw.olap.engine` 单部署二选一，runtime-access 收口）；图谱 standard=PG / enterprise+=Neo4j；向量=pgvector 优先（未启用按 MC03 降级并标记）。
- 新缺口记录（2026-09-28 实查更正）：pgvector `0.6.2` **已装**于 dev 库，kb 侧真实向量链路已具备（`PgVectorSupport` 探测 + 嵌入经 `llm-gateway` + `embedding_vec`/HNSW/`<=>`），但 `knowledge_embedding` 0 行→ 缺端到端实测；仍降级的是 DQ（MD5 伪向量存 text）与 cognitive（关键词回退）。CH 内部物化 sink 缺失（"二选一"暂不可兑现）。湖规侧工程项 #5/#7/#10~#14。

## 九、开发环境登录凭据（红线文件）

- **定位**：dev/test 登录凭据唯一权威出口；任何 Agent 出手开发任务前必读；凭据失效/更换须用户明确批准。
- **本文不复制账号密码等凭据值**（文档编写规范 R7），请直接读源文件：`.trae/rules/开发环境登录凭据.md`（含登录端点、请求体、Token 类型与 getUserInfo 用法、seed.sql 关联）。
- 硬红线：Agent 不得自行修改；调整须用户对话中写出新值并同步 seed SQL 与固化凭据的 smoke 脚本；禁把 dev 凭据带入生产配置或镜像。
- 历史记录：登录后被踢 bug 的 10s `auth_grace_period` 是过渡兜底，根治项（`/api/v1/security-profiles/**` 白名单或鉴权穿透加固）落 P3 批次。

---

## 十、规范间关系图

```
架构铁律 v1.8（宪法，冲突时最高优先）
 ├─ §1/§2/§5 ──细化──► 后端开发规范（Java 语法层）
 ├─ §2.4/§3 ──细化──► 数据库访问规范（30 条红线）──对齐──► 数据湖存储分层规范（物理分层）
 ├─ §4 ──细化──► 前端开发规范
 ├─ §7 PMO 模板 ──引用──► Git 提交规范（DONE 凭证）
 └─ 文档侧 ──► 文档编写规范（R1~R13）× 文档目录规范（目录定义 v2.0，两者互补）
凭据红线文件（开发环境登录凭据）= 独立红线，Agent 出手前必读
```
