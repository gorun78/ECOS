# PMO指令: 后端架构重整 — 七域四引擎

> **来源**: 肖国荣 | **日期**: 2026-07-08
> **协同**: ECOS-ARCH (架构评审) + ECOS-BE (编码执行) + ECOS-FE (引擎管控页面)
> **铁律**: 不动前端API路径、不改DIKW分层、阶段验证门禁不过不进下一阶段

---

## 零、现状摸底

### 当前模块 (14个)

| 当前模块 | 子模块 | Controller数 | 职责 |
|---------|--------|:--:|------|
| `runtime/` | core/crypto/security/monitor/task/hermes-engine | 0 | 运行时基础设施 |
| `sysman/` | api/impl/boot | 18 | IAM/权限/审计/配置/租户 |
| `datanet/` | api/impl/boot | 6 | 数据源/目录/元数据/管道 |
| `buszhi/` | impl | 4 | 工作流/DQ规则 |
| `dccheng/` | api/impl | 16 | 本体/知识图/分类/术语/血缘 |
| `aimod/` | impl | 9 | Agent/Mesh/模型/NLQ |
| `gateway/` | — | 14 | 统一入口+聚合Controller |
| `workspace/` | impl | 8 | Object/QL/关系/工作簿 |
| `common/` | api | 0 | 公共API(IObjectStorage/IAnalytics/IGraph) |
| `portal/` | impl | 5 | BizDashboard/合同/项目统计 |
| `worldmodel/` | impl | 4 | 因果链/帕累托/世界模型 |
| `ecos-kanban/` | api/impl/boot | 0 | 看板管理 |
| `cognitive/` | api/impl | 1 | 认知引擎(已有雏形) |
| `market/` | impl | 1 | Agent市场 |

### 当前问题
- `gateway/` 聚合了本该属于各子系统的Controller(DataLake/DqDashboard/EcosKnowledgeGraph/Security等)
- `portal/` 和 `workspace/` 职责重叠
- `dccheng/` 承担了本体+知识两项职责，粒度过粗
- `cognitive/` 有引擎名但无引擎实
- 无统一的引擎管理/监控机制

---

## 一、目标结构

```
databridge-v2/
├── runtime/          ← 运行时(不变)
├── sysman/           ← 系统管理与安全(精简后)
├── datanet/          ← 数据工作台
├── buszhi/           ← 本体工作台(收编dccheng本体+worldmodel)
├── dccheng/          ← 知识工作台(保留知识图谱/分类/术语)
├── aimod/            ← AI工作台
├── workspace/        ← 项目工作台(收编portal/ecos-kanban/market)
├── gateway/          ← 服务网关(精简后,只做路由)
├── common/           ← 公共API(不变)
└── engine/           ← ★ 新增: 四引擎
    ├── security-engine/    ← 安全引擎
    ├── data-engine/        ← 数据引擎
    ├── ontology-engine/    ← 本体引擎
    └── cognitive-engine/   ← 认知引擎
```

### 模块吸收关系

| 被吸收模块 | → 目标模块 | 理由 |
|-----------|-----------|------|
| `portal/` | `workspace/` | 项目仪表板=项目工作台功能 |
| `worldmodel/` | `buszhi/` | 因果链/帕累托=本体推理能力 |
| `ecos-kanban/` | `runtime/` | 看板=任务管理=运行时基础设施 |
| `cognitive/` | `dccheng/` + `aimod/` | 认知引擎实体提升到engine层 |
| `market/` | `workspace/` | Agent市场=项目工作台功能 |

---

## 二、四引擎定义

### 引擎架构标准

每个引擎满足以下契约:

```java
// 引擎必须实现的接口 (放在 common/common-api)
public interface IEngine {
    String getName();              // 引擎唯一标识
    EngineStatus getStatus();      // RUNNING/DEGRADED/STOPPED
    Map<String, Object> getConfig();   // 当前配置快照
    HealthCheck healthCheck();     // 健康检查
    void start();                  // 启动
    void stop();                   // 停止
}

// 引擎自动注册到任务中心
public interface ITaskAwareEngine extends IEngine {
    List<Task> getActiveTasks();    // 当前执行中的任务
    Task submitTask(TaskRequest req); // 提交任务
    TaskStatus queryTask(String taskId); // 查询任务状态
}
```

### 引擎API端点(统一前缀 `/api/v1/engine/{type}`)

| 端点 | 用途 |
|------|------|
| `GET /health` | 健康状态 |
| `GET /config` | 当前配置 |
| `GET /status` | 运行状态+指标 |
| `GET /tasks` | 活跃任务列表 |
| `POST /tasks` | 提交任务 |

### 2.1 安全引擎 (security-engine)

**职责**: 认证/授权/审计/加密/脱敏/合规的完整生命周期

**代码来源**:
- `runtime/runtime-security/` → ABAC策略引擎缓存
- `runtime/runtime-crypto/` → 加密/密钥管理
- `sysman/sysman-impl/` → AbacController, AuditController, CryptoAuditController, DataMaskingController, DataPermissionController, PolicyEngineController, SecurityConfigController
- `sysman/sysman-api/` → 安全相关Service接口

**模块结构**:
```
engine/security-engine/
├── security-engine-api/        ← 安全引擎接口定义
├── security-engine-impl/       ← 引擎实现+控制器
└── security-engine-boot/       ← 可独立启动
```

### 2.2 数据引擎 (data-engine)

**职责**: 数据源/管道/目录/质量/血缘的数据全生命周期

**代码来源**:
- `datanet/datanet-impl/` → CatalogController, DataSourceController, MetadataController, PipelineController, CategoryController
- `datanet/datanet-api/` → 数据相关Service接口
- `gateway/` → DataLakeController(移回数据引擎)

**模块结构**:
```
engine/data-engine/
├── data-engine-api/
├── data-engine-impl/
└── data-engine-boot/
```

### 2.3 本体引擎 (ontology-engine)

**职责**: 本体建模/实体/关系/属性/版本/提案的管理

**代码来源**:
- `dccheng/dccheng-impl/ontology/` → 全部Ontology*Controller + LineageController + AutoDiscoverController
- `buszhi/buszhi-impl/workflow/` → WorkflowController(工作流=本体动作编排)
- `worldmodel/` → 因果链/帕累托(吸收后纳入)

**模块结构**:
```
engine/ontology-engine/
├── ontology-engine-api/
├── ontology-engine-impl/
└── ontology-engine-boot/
```

### 2.4 认知引擎 (cognitive-engine)

**职责**: AI Agent/知识图谱/分类/术语/模型/NLQ的智能全生命周期

**代码来源**:
- `aimod/aimod-impl/` → 全部AIP*Controller + Agent*Controller + NLQController
- `dccheng/dccheng-impl/` → ClassificationController, GlossaryController, KnowledgeGraphController, KnowledgeApiController, GuardrailsApiController
- `cognitive/cognitive-impl/` → CognitiveController(升级为正式引擎实现)
- `gateway/` → DiagnosticAgentController(移回认知引擎)

**模块结构**:
```
engine/cognitive-engine/
├── cognitive-engine-api/
├── cognitive-engine-impl/
└── cognitive-engine-boot/
```

---

## 三、分阶段执行

### 阶段0: 预备 — 创建engine父POM + IEngine接口

**Owner**: ECOS-ARCH → ECOS-BE

```bash
mkdir -p engine/
# 创建 engine/pom.xml (父POM, packaging=pom)
# 添加 <module>security-engine</module> 等4个模块占位
# common/common-api 添加 IEngine.java / EngineStatus.java / HealthCheck.java
```

**验证**: `mvn install -pl common -DskipTests` 通过

### 阶段1: 安全引擎 — 第一个引擎落地，建立模式

**Owner**: ECOS-BE

1. 创建 `engine/security-engine/` 完整骨架(api+impl+boot)
2. 从 `sysman/sysman-impl/` 移动安全相关Controller:
   - AbacController → security-engine-impl
   - AuditController → security-engine-impl
   - CryptoAuditController → security-engine-impl
   - DataMaskingController → security-engine-impl
   - DataPermissionController → security-engine-impl
   - PolicyEngineController → security-engine-impl
   - SecurityConfigController → security-engine-impl
3. 从 `runtime/runtime-security/` 和 `runtime/runtime-crypto/` 移动底层Service
4. 迁移 `sysman/sysman-api/` 安全相关Service接口到 `security-engine-api`
5. 保留原Controller的 `@RequestMapping` 路径不变
6. Gateway添加路由: `/api/v1/security/**` → security-engine-boot
7. `sysman/sysman-boot` 不再扫描安全相关包
8. 实现 `/api/v1/engine/security/health|config|status`

**验证**:
```bash
# 编译
mvn install -pl engine/security-engine -am -DskipTests
# 安全API仍可访问
curl http://localhost:8080/api/v1/security/audit-logs | jq .
# 引擎健康检查
curl http://localhost:8080/api/v1/engine/security/health | jq .
```

### 阶段2: 数据引擎

同阶段1模式，迁移 `datanet/` Controller到 `engine/data-engine/`，Gateway改路由。

### 阶段3: 本体引擎

迁移 `dccheng/ontology/` + `buszhi/workflow/` + `worldmodel/` → `engine/ontology-engine/`。

### 阶段4: 认知引擎

迁移 `aimod/` + `dccheng/` (非本体部分) + `cognitive/` → `engine/cognitive-engine/`。

### 阶段4.5: 引擎闭环验证 ★

> 四引擎全部创建完毕后，逐一对标引擎定义进行闭环验证。
> 不通过则退回对应阶段修改，直至全部PASS才进入阶段5。

#### 验证标准（对标第二章引擎定义）

| # | 检查项 | 契约要求 | 验证方法 |
|:--:|------|------|------|
| V1 | **引擎标识** | `getName()` 返回唯一标识 | curl `/api/v1/engine/{type}/status` → 确认name字段 |
| V2 | **独立运行** | `engine-*-boot` 可脱离Gateway独立启动 | `mvn spring-boot:run -pl engine/{type}-engine/{type}-engine-boot` → 进程正常启动 |
| V3 | **健康检查** | `healthCheck()` 返回各子系统状态 | curl `/api/v1/engine/{type}/health` → 返回各组件UP/DOWN |
| V4 | **配置快照** | `getConfig()` 返回当前运行参数 | curl `/api/v1/engine/{type}/config` → 返回非空配置Map |
| V5 | **状态上报** | `getStatus()` 返回RUNNING/DEGRADED/STOPPED | curl `/api/v1/engine/{type}/status` → status字段三值之一 |
| V6 | **任务提交** | `submitTask()` + `queryTask()` 可追踪 | curl POST `/api/v1/engine/{type}/tasks` + GET 查询 → 任务状态流转 |
| V7 | **任务汇聚** | 所有引擎任务出现在 `/api/v1/engine/tasks` | curl → 确认含多引擎来源的任务 |
| V8 | **全生命周期** | 引擎管理对应对象的创建→运行→销毁 | 创建实体→状态变更→删除，全链路可追踪 |
| V9 | **API路径不变** | 迁移前后端API响应一致 | diff 迁移前后各Controller的curl响应快照 |
| V10 | **编译隔离** | 四引擎间无循环依赖 | `mvn dependency:tree` + ArchUnit规则检查 |

#### 验证流程

```
安全引擎 V1-V10 → 数据引擎 V1-V10 → 本体引擎 V1-V10 → 认知引擎 V1-V10
                    ↓ 任一FAIL                              ↓ 全部PASS
              退回对应阶段修改                           进入阶段5
```

#### 每引擎验证脚本模板

```bash
#!/bin/bash
ENGINE=$1  # security|data|ontology|cognitive
BASE="http://localhost:8080/api/v1/engine/${ENGINE}"

echo "=== V1: 引擎标识 ==="
curl -s $BASE/status | jq '.data.name'

echo "=== V3: 健康检查 ==="
curl -s $BASE/health | jq '.data.components'

echo "=== V4: 配置快照 ==="
curl -s $BASE/config | jq '.data | keys | length'

echo "=== V5: 状态上报 ==="
curl -s $BASE/status | jq '.data.status'

echo "=== V6: 任务提交 ==="
TASK_ID=$(curl -s -X POST $BASE/tasks -H "Content-Type: application/json" \
  -d '{"type":"test","params":{}}' | jq -r '.data.taskId')
echo "Task created: $TASK_ID"
sleep 2
curl -s "$BASE/tasks/$TASK_ID" | jq '.data.status'

echo "=== V7: 任务汇聚 ==="
curl -s "http://localhost:8080/api/v1/engine/tasks" | jq '.data | length'
```

#### FAIL处理规则

| 失败项 | 根因 | 修复动作 |
|------|------|------|
| V1 FAIL | 引擎未注册 | 实现 `IEngine.getName()` |
| V2 FAIL | boot模块依赖缺失 | 补齐pom.xml依赖 |
| V3 FAIL | 健康检查未实现 | 实现 `healthCheck()` |
| V4 FAIL | 配置未注入 | 补齐 `getConfig()` |
| V5 FAIL | 状态枚举缺失 | 实现 `EngineStatus` |
| V6 FAIL | TaskService未迁移 | 迁移对应Controller+Service |
| V7 FAIL | 任务未注册到中心 | 实现 `ITaskAwareEngine` |
| V8 FAIL | 生命周期不完整 | 检查Service的CRUD完整性 |
| V9 FAIL | API路径变动 | 恢复原 `@RequestMapping` |
| V10 FAIL | 循环依赖 | 调整模块依赖方向 |

### 阶段5: 模块吸收 + Runtime清理

#### 5.1 模块吸收

| 被吸收模块 | → 目标模块 | 理由 |
|-----------|-----------|------|
| `portal/` | `workspace/` | 项目仪表板=项目工作台功能 |
| `worldmodel/` | `buszhi/` | 因果链/帕累托=本体推理能力 |
| `ecos-kanban/` | `runtime/` | 看板=任务管理=运行时基础设施 |
| `market/` | `workspace/` | Agent市场=项目工作台功能 |

#### 5.2 Runtime清理（引擎剥离后）

**背景**: `runtime-core/` 当前425个Java文件，混杂了数据/安全/Agent/转换/遗留框架。四引擎创建后，runtime必须瘦身。

**保留在 runtime 的**（运行时基础设施，约100文件）:
- `runtime-core/agent/` — Agent运行时模型+LLMClient+ToolRegistry（共享，各引擎都可能调用LLM）
- `runtime-core/agent/mesh/` — 消息总线+Mission执行引擎
- `runtime-core/bigdataengine/` — Spark/Flink大数据执行环境
- `runtime-core/core/` — TaskLockService + LicenseService
- `runtime-core/alert/` — 系统告警服务
- `runtime-core/config/` — 配置接口
- `runtime-core/logging/` — 日志服务
- `runtime-core/git/` — Git仓库服务
- `runtime-core/mybatis/` — MyBatis配置
- `hermes-engine/` — 不变
- `runtime-monitor/` — 基础设施监控（不变）
- `runtime-task/` — 任务中心（增强：对接所有引擎任务）
- `ecos-kanban/` — 吸收后纳入

**从 runtime-core 移出**（约300文件）:

| 包路径 | 移入引擎 |
|------|------|
| `common/dataobjectmgr/` (数据对象DAO/Service/Bean) | data-engine |
| `common/datasourcemgr/` (数据源DAO/Service/Bean) | data-engine |
| `common/datamusterdefine/` (数据模板定义) | data-engine |
| `common/dataclean/` (数据清洗规则) | data-engine |
| `datadescription/` (数据描述+SchemaRegistry) | data-engine |
| `datasource/` (数据源服务接口) | data-engine |
| `dataaccess/` (数据产品服务) | data-engine |
| `metadata/` (元数据服务+远程元数据) | data-engine |
| `quality/` (QualityEvaluator+规则SPI) | data-engine |
| `transform/` (TransformChain+6种Step) | data-engine |
| `format/` (格式转换) | data-engine |
| `modelaccess/` (模型访问+推理) | cognitive-engine |
| `security/` (PEP/ColumnPermission/RowFilter) | security-engine |

**从 runtime 删除**:
- `frameworkset/` — 老poolman数据库工具(已被JdbcTemplate替代)
- `org.jdom/` — 老XML解析(JDK自带)
- `com.chinacreator.security/` — 老密码加密(已被crypto模块替代)

#### 5.3 验证

```bash
# 父POM modules减少到: runtime/sysman/datanet/buszhi/dccheng/aimod/workspace/gateway/common/engine
mvn install -DskipTests 2>&1 | tail -20
# runtime-core编译通过(Agent/Tool/Mesh/Alert等保留项不受影响)
# 移出的包在目标引擎模块编译通过
```

### 阶段6: Gateway瘦身

Gateway不应聚合业务Controller。将以下Controller移回对应引擎:
- `DataLakeController` → data-engine
- `DqDashboardController` → data-engine
- `EcosKnowledgeGraphController` → cognitive-engine
- `SecurityController` → security-engine
- `DiagnosticAgentController` → cognitive-engine

Gateway只保留: 路由转发 + AuthController + HealthController + MonitorController + TelemetryController。

### 阶段7: 前端引擎管控页

**Owner**: ECOS-FE

#### 7.1 左侧栏新增「功能监控中心」组

在 `Sidebar.tsx` 中 `总览` 和 `资源概览` 之间插入新组:

```typescript
// ── 功能监控中心 ──────────────────────────────────────
const engineGroup: NavGroup = {
  group: "引擎监控",
  groupZh: "功能监控中心",
  items: [
    { id: "engine-security", label: "Security Engine", labelZh: "安全引擎", icon: Shield, desc: "Auth, audit, encryption, compliance", descZh: "认证·审计·加密·合规" },
    { id: "engine-data", label: "Data Engine", labelZh: "数据引擎", icon: Database, desc: "Ingestion, pipeline, catalog, quality", descZh: "采集·管道·目录·质量" },
    { id: "engine-ontology", label: "Ontology Engine", labelZh: "本体引擎", icon: Network, desc: "Modeling, entities, relationships, versions", descZh: "建模·实体·关系·版本" },
    { id: "engine-cognitive", label: "Cognitive Engine", labelZh: "认知引擎", icon: Brain, desc: "Agent, knowledge, classification, NLQ", descZh: "智能体·知识·分类·NLQ" },
    { id: "engine-tasks", label: "Task Center", labelZh: "异步任务中心", icon: Clock, desc: "Unified task queue & monitoring", descZh: "统一任务队列与监控" },
  ],
};
```

#### 7.2 路由注册

在 `main.tsx` 中增加5个路由:
```tsx
<Route path="engine-security" element={<EngineMonitor engine="security" />} />
<Route path="engine-data" element={<EngineMonitor engine="data" />} />
<Route path="engine-ontology" element={<EngineMonitor engine="ontology" />} />
<Route path="engine-cognitive" element={<EngineMonitor engine="cognitive" />} />
<Route path="engine-tasks" element={<AsyncTaskCenter />} />
```

#### 7.3 EngineMonitor 组件

创建 `/home/guorongxiao/c2eos/src/pages/EngineMonitor.tsx`，单一组件适配四引擎(通过 `engine` prop 区分)。

每个引擎监控面板统一包含:
- 引擎状态指示灯(RUNNING/DEGRADED/STOPPED)
- 健康指标(CPU/内存/请求延迟/错误率)
- 活跃任务列表+进度
- 配置参数面板(只读展示当前配置)

#### 7.4 异步任务中心

复用现有 `AsyncTaskCenterView`（如不存在则创建），对接 `/api/v1/engine/{type}/tasks` 统一展示所有引擎的任务队列。

**关键**：所有引擎任务通过 `/api/v1/engine/tasks` 汇聚到一个视图，不做分散管控。

---

## 四、约束与风险

### 铁律(不可违反)
1. **API路径不变** — 所有 `@RequestMapping` 保持不变，前端零感知
2. **DIKW分层不变** — 引擎内部仍遵循 D→I→K→W 单向依赖
3. **Bean名称不变** — 移动Controller时不改 `@Service` Bean名
4. **扫描路径渐进调整** — 每阶段只改一个模块的 `@ComponentScan`

### 风险点
| 风险 | 缓解 |
|------|------|
| Bean冲突(移动后重复扫描) | 原模块排除旧扫描路径+新引擎精确扫描 |
| Gateway路由断裂 | 每阶段按标准操作: 加新路由→验证→删旧路由 |
| 循环依赖 | ARCH逐阶段审查依赖图 |
| 前端页面找不到引擎 | 阶段7统一增加，前面的阶段前端不感知 |

---

## 五、技能化

本指令固化为技能 `ecos-backend-restructure`，供后续分阶段执行时复用:

### 单阶段执行模板

```
阶段N执行: 调用 ARCH 评审迁移清单 → BE 执行移动 → QA 验证API → PMO 确认
```

### 标准操作: 移动Controller

```bash
# 1. 复制Controller到目标模块
cp {src}/Controller.java {target}/controller/

# 2. 修改目标模块的 @ComponentScan 包扫描范围

# 3. 在原模块排除已迁移的包
# @SpringBootApplication(exclude = {OldController.class})

# 4. 原模块pom.xml添加对引擎模块的依赖
# <dependency>engine:xxx-engine-api</dependency>

# 5. 编译验证
mvn install -pl engine/{xxx}-engine -am -DskipTests

# 6. Gateway添加路由(阶段6前暂时不改Gateway)
```

### 标准操作: 模块吸收

```bash
# 1. 复制所有Java源文件到目标模块
# 2. 目标模块pom.xml合并被吸收模块的依赖
# 3. 父POM删除被吸收模块的<module>
# 4. 被吸收模块目录保留但git rm
# 5. 全量编译验证
```

---

## 六、执行优先级

```
阶段0(预备) → 阶段1(安全引擎) → 阶段2(数据引擎) → 阶段3(本体引擎) → 阶段4(认知引擎) → 阶段4.5(闭环验证★) → 阶段5(吸收+Runtime清理) → 阶段6(Gateway瘦身) → 阶段7(前端管控)
```

**阶段0+1 P0优先**，建立模式后阶段2-4可并行，**阶段4.5为硬门禁**——四引擎全部通过V1-V10验证方可进入阶段5。
