# PMO指令：知识工作台系统重构 — 双轨知识体系

> **来源**: 肖国荣 | **日期**: 2026-07-10
> **铁律**: 单文件粒度，每个Task改一个文件。Git提交=唯一绩效。DONE=commit hash+curl 200。禁止产出解释性文档。

---

## 零、架构决策：双轨知识模型

知识工作台服务于两类知识，共用基础设施但生命周期不同：

```
┌──────────────────────────────────────────────────────────┐
│                    知识工作台                             │
├────────────────────────┬─────────────────────────────────┤
│  轨道A：平台自用知识     │  轨道B：智能体知识               │
│  (AIP Copilot知识底座)  │  (AI工作台知识底座)              │
├────────────────────────┼─────────────────────────────────┤
│ 数据来源：              │ 数据来源：                       │
│ ① 数据集元数据          │ ① 业务对象本体(ObjectType)        │
│ ② 数据血缘/级联关系     │ ② 对象知识图谱(KG抽取)           │
│ ③ 语义本体对齐映射      │ ③ 术语库/分类体系                │
├────────────────────────┼─────────────────────────────────┤
│ 构建方式：              │ 构建方式：                       │
│ 元数据向量化→切片→索引  │ 本体→实体关系抽取→图谱→RAG      │
├────────────────────────┼─────────────────────────────────┤
│ 消费者：                │ 消费者：                         │
│ ECOS AIP Copilot       │ AI工作台→智能体创建→知识检索      │
└────────────────────────┴─────────────────────────────────┘
```

**关键判断**：轨道B需要使用轨道A的数据。原因——智能体需要理解的不只是业务对象本身，还需要知道这些对象的元数据来源、血缘链路、安全分类。所以轨道A是轨道B的前置条件。

---

## 一、当前状态

### 前端（3370行/10文件）

| 文件 | 行数 | 问题 |
|------|:--:|------|
| `KnowledgeView.tsx` | 554 | 控制器+状态混在一起 |
| `tabs/ArchitectureTab.tsx` | 36 | 静态设计文档，非功能页 |
| `tabs/SyncTab.tsx` | 265 | 元数据同步 |
| `tabs/LineageTab.tsx` | 389 | 血缘解析 |
| `tabs/OntologyTab.tsx` | 499 | 本体对齐映射 |
| `tabs/IndexTab.tsx` | 310 | 向量索引 |
| `tabs/RagTab.tsx` | 170 | RAG模拟 |
| `tabs/GraphSyncTab.tsx` | 325 | 图谱同步 |
| `tabs/GraphExplorerTab.tsx` | 783 | ⚠️ 超标，图谱可视化 |
| `types.ts` + `helpers.ts` | 39 | 类型定义和辅助函数太少 |

### 后端

> **注意**: 知识工作台的后端Controller全部位于认知引擎模块 `engine/cognitive-engine/` 下。认知引擎V1独立重构指令见 `ECOS-认知引擎V1-PMO指令-v2.md`，本指令只涉及知识工作台自身需要的后端补全。

| Controller | 端点 | 所在模块 | 状态 |
|------|:--:|------|:--:|
| `KnowledgeGraphController` | 8 | cognitive-engine | 真实（Neo4j+PG双源） |
| `KnowledgeApiController` | 2 | cognitive-engine | 知识库CRUD |
| `GraphSyncController` | 5 | cognitive-engine | 图谱同步 |
| `GlossaryController` | 4 | cognitive-engine | 术语库 |
| `ClassificationController` | 4 | cognitive-engine | 分类体系 |
| `CognitiveEngineImpl` | — | cognitive-engine | ❌ 骨架（认知引擎V1指令修复）

### 核心问题

1. **8个Tab平铺，无分组**——用户分不清"平台自用知识"和"智能体知识"
2. **ArchitectureTab是静态说明页**——不是功能入口，应改为可操作的"闭环设计器"
3. **GraphExplorerTab 783行**——超标，需拆分
4. **缺少知识准备阶段**——从元数据→知识库的构建流程不完整
5. **类型定义太少**——仅1个`MetadataAsset`，知识域的类型体系缺失

---

## 二、目标结构

### 知识工作台新Tab布局

```
知识工作台
├── 📋 闭环设计 (原Architecture → 改造为功能页)
│   └── 定义知识闭环策略：数据源→元数据提取→向量化→检索→反馈
│
├── 📥 知识准备 (轨道A：平台自用知识)
│   ├── 元数据同步 (原SyncTab)
│   ├── 血缘解析 (原LineageTab)
│   └── 本体对齐 (原OntologyTab)
│
├── 🔧 知识构建 (轨道B：智能体知识)
│   ├── 图谱同步 (原GraphSyncTab)
│   ├── 图谱探索 (原GraphExplorerTab → 拆分)
│   ├── 术语库 (新增)
│   └── 分类体系 (新增)
│
├── 📊 知识检索
│   ├── 向量索引 (原IndexTab)
│   └── RAG模拟 (原RagTab)
│
├── ⚙️ 工作台配置 (新增)
│   └── 向量模型选择/切片参数/检索策略/知识库绑定
│
└── 🧠 认知引擎配置 (新增，靠底对齐)
    └── 四个子引擎参数配置（对接认知引擎V1）
```

### 前端文件目标结构

```
src/pages/knowledge/
├── KnowledgeView.tsx          → ~300行（纯控制器，只做Tab路由+全局状态）
├── typesAndConstants.ts       → 新增，所有类型+静态配置+图标映射
├── services/
│   ├── knowledgeApi.ts        → 新增，API调用封装
│   └── knowledgeCompiler.ts   → 新增，知识构建纯函数
├── tabs/
│   ├── ClosedLoopTab.tsx      → 改造自ArchitectureTab
│   ├── SyncTab.tsx            → 保留，≤265行
│   ├── LineageTab.tsx         → 保留，≤389行
│   ├── OntologyTab.tsx        → 保留，≤499行
│   ├── GraphSyncTab.tsx       → 保留，≤325行
│   ├── GraphExplorerTab.tsx   → 拆分为≤400行（抽Canvas渲染器）
│   ├── GlossaryTab.tsx        → 新增
│   ├── ClassificationTab.tsx  → 新增
│   ├── IndexTab.tsx           → 保留，≤310行
│   ├── RagTab.tsx             → 保留，≤170行
│   ├── SettingsTab.tsx        → 新增，工作台配置
│   └── CognitiveConfigTab.tsx → 新增，认知引擎配置（排在最后，靠底对齐）
└── components/
    ├── GraphCanvas.tsx         → 从GraphExplorer抽离
    ├── KnowledgeLifecycle.tsx  → 新增，知识生命周期仪表板
    └── CopilotPreview.tsx      → 新增，Copilot效果预览
```

---

## 三、禁止清单

1. 禁止新增npm依赖（lucide-react已够用）
2. 禁止在tsx中直接写fetch——必须走`knowledgeApi.ts`
3. 禁止硬编码配置值——统一从typesAndConstants读取
4. 禁止产出解释性文档
5. 禁止改动已有API路径签名
6. 禁止删除现有Tab中已工作的API调用逻辑（Read-Modify-Write）

---

## 四、Phase 1：类型体系+服务层提取（2天，FE+BE）

### P1-1: typesAndConstants.ts（FE，0.5天）

**目标**: 把散落在各Tab的类型、配置、图标映射集中到一个文件。

**新建文件**: `/home/guorongxiao/c2eos/src/pages/knowledge/typesAndConstants.ts`

**必须包含**：

```typescript
// ─── 知识域核心类型 ───
interface KnowledgeAsset {
  id: string;
  name: string;
  track: 'platform' | 'agent';  // 轨道A或B
  sourceType: 'metadata' | 'lineage' | 'ontology' | 'business_object';
  status: 'draft' | 'syncing' | 'indexed' | 'ready' | 'error';
  chunkCount: number;
  vectorDim?: number;
  lastUpdated: string;
}

interface KnowledgeGraphNode {
  id: string; label: string; type: string;
  domain: string; properties: Record<string, any>;
}

interface KnowledgeGraphEdge {
  id: string; source: string; target: string;
  type: string; weight?: number;
}

// ─── 闭环设计配置 ───
interface ClosedLoopConfig {
  id: string; name: string;
  sources: { track: 'platform'|'agent'; sourceType: string; enabled: boolean }[];
  vectorModel: string; chunkSize: number; overlap: number;
  targetIndex: string; refreshCron?: string;
}

// ─── 工作台配置 ───
interface KnowledgeSettings {
  defaultVectorModel: string;
  defaultChunkSize: number; defaultOverlap: number;
  neo4jEnabled: boolean;
  autoSyncEnabled: boolean;
  maxRetrievalResults: number;
}

// ─── 图标映射（Lucide → 统一管理） ───
export const KNOWLEDGE_ICONS = {
  platform: Database, agent: Bot, metadata: FileText,
  lineage: Network, ontology: Workflow, graph: Share2,
  index: Layers, rag: Sparkles, sync: RefreshCw,
  // ... 所有图标集中
} as const;

// ─── 静态配置 ───
export const CHUNK_SIZE_OPTIONS = [256, 512, 1024, 2048];
export const VECTOR_MODELS = [
  { id: 'text-embedding-3-small', dim: 1536, label: 'OpenAI Small' },
  { id: 'bge-large-zh-v1.5', dim: 1024, label: 'BGE 中文大模型' },
];
```

**验收**:
```bash
cd /home/guorongxiao/c2eos
# 零TS错误
npx tsc --noEmit 2>&1 | grep -i "typesAndConstants"
# 期望: 0行
```

---

### P1-2: knowledgeApi.ts — Service层（FE，0.5天）

**目标**: 所有API调用统一封装，KnowledgeView和各Tab不再直接写fetch。

**新建文件**: `/home/guorongxiao/c2eos/src/pages/knowledge/services/knowledgeApi.ts`

**必须封装的端点**:

| 方法 | 端点 | 用途 |
|------|------|------|
| `fetchGraph(domain?)` | `GET /api/knowledge/graph` | 全图节点+边 |
| `fetchNode(id)` | `GET /api/knowledge/nodes/{id}` | 节点详情 |
| `searchKnowledge(q)` | `GET /api/knowledge/search?q=` | 语义搜索 |
| `findPath(s, t)` | `GET /api/knowledge/path?s=&t=` | 最短路径 |
| `fetchNeighbors(id, depth)` | `GET /api/knowledge/neighbors/{id}?d=` | N度邻居 |
| `createNode(data)` | `POST /api/knowledge/nodes` | 创建节点 |
| `createEdge(data)` | `POST /api/knowledge/edges` | 创建边 |
| `getDataSource()` | `GET /api/knowledge/source` | 数据源类型 |
| `syncMetadata(assets)` | `POST /api/ontology/mappings` | 元数据同步 |
| `fetchOntologyMappings()` | `GET /api/ontology/mappings` | 本体映射列表 |
| `runRAGQuery(query)` | `POST /api/knowledge/rag` | RAG检索 |
| `getSettings()` | `GET /api/knowledge/settings` | 工作台配置 |
| `updateSettings(data)` | `PUT /api/knowledge/settings` | 更新配置 |

**验收**: 同上，零TS错误

---

### P1-3: KnowledgeView.tsx 瘦身（FE，0.5天）

**目标**: 把554行的KnowledgeView拆成纯Tab路由器，状态管理下放各Tab。

**修改文件**: `/home/guorongxiao/c2eos/src/pages/KnowledgeView.tsx`

**改动**:
- 删除所有ontologyMappings/fetchOntologyData状态→移到OntologyTab内部
- 删除RAG相关状态(queryInput/retrievedDocs等)→移到RagTab内部
- 删除直接fetch调用→改为调用knowledgeApi
- 保留：activeSubTab状态、Tab切换UI骨架
- 目标≤300行

**验收**: 零TS错误 + 浏览器打开知识工作台，8个Tab均正常显示

---

## 五、Phase 2：双轨Tab重组（3.5天，FE为主）

### P2-1: ClosedLoopTab — 闭环设计器（FE，1天）

**目标**: 把36行的静态ArchitectureTab改造成可操作的闭环设计页面。

**改造文件**: `/home/guorongxiao/c2eos/src/pages/knowledge/tabs/ArchitectureTab.tsx` → 重命名为 `ClosedLoopTab.tsx`

**核心功能**:
1. 展示知识闭环五步流程（数据接入→元数据提取→向量化→索引→检索反馈）
2. 每一步展示当前状态（已接入数据源数、已索引文档数等），从`knowledgeApi`实时拉取
3. 可创建/编辑"闭环方案"（ClosedLoopConfig），选择数据源组合
4. 预览Copilot效果（使用CopilotPreview组件）
5. 保留原ArchitectureTab中的Q&A和闭环拓扑图（它们有价值）

**验收**:
```bash
# 从侧边栏点击"闭环设计"Tab，页面加载
# 能创建/查看闭环方案
# CopilotPreview显示当前知识库可响应的示例问题
```

---

### P2-2: 知识准备组重组（FE，0.5天）

**目标**: SyncTab + LineageTab + OntologyTab 归入"知识准备"分组。

**修改文件**: `/home/guorongxiao/c2eos/src/pages/KnowledgeView.tsx`

**改动**: Tab分组UI（分组标签+折叠/展开）：
```
📥 知识准备（轨道A：平台自用）
  ├── 元数据同步
  ├── 血缘解析
  └── 本体对齐
```
各Tab代码不改，仅增加分组标签。

---

### P2-3: 知识构建组重组 + GlossaryTab（FE，1天）

**目标**: GraphSync + GraphExplorer(拆分) + 术语库 + 分类体系归入"知识构建"分组。

**新建文件1**: `/home/guorongxiao/c2eos/src/pages/knowledge/tabs/GlossaryTab.tsx`

**功能**: 术语库CRUD——展示/新增/编辑/删除业务术语，关联到本体实体。
- 表结构已存在（`td_glossary`，由GlossaryController管理）
- API: `GET/POST/PUT/DELETE /api/knowledge/glossary`

**新建文件2**: `/home/guorongxiao/c2eos/src/pages/knowledge/tabs/ClassificationTab.tsx`

**功能**: 分类体系管理——树形分类结构，拖拽排序，关联到术语。
- API: `GET/POST /api/knowledge/classification`

**拆分**: `GraphExplorerTab.tsx` 783行→抽离`GraphCanvas.tsx`(纯Canvas渲染)+保留交互逻辑
- 新建 `/home/guorongxiao/c2eos/src/pages/knowledge/components/GraphCanvas.tsx`
- 原GraphExplorerTab ≤400行

**验收**: TS编译通过 + 分组UI正常 + 术语/分类CRUD可用

---

### P2-4: SettingsTab — 工作台配置（FE，0.5天）

**目标**: 知识工作台可配置。

**新建文件**: `/home/guorongxiao/c2eos/src/pages/knowledge/tabs/SettingsTab.tsx`

**功能**:
- 向量模型选择（下拉，从VECTOR_MODELS读取）
- 切片参数（chunkSize + overlap，从CHUNK_SIZE_OPTIONS读取）
- 知识库绑定（勾选哪些分类/术语库启用）
- 自动同步开关

**验收**:
```bash
curl -s -H "$H" "http://localhost:8080/api/knowledge/settings" | jq '.data.defaultVectorModel'
# 期望: 返回模型名称字符串
```

---

### P2-5: CognitiveConfigTab — 认知引擎配置（FE，0.5天）

**目标**: 知识工作台底部新增认知引擎配置Tab，对接 `ECOS-认知引擎V1-PMO指令-v2.md` 中定义的四个子引擎参数。

**新建文件**: `/home/guorongxiao/c2eos/src/pages/knowledge/tabs/CognitiveConfigTab.tsx`

**样式要求**: Tab排在最后，**靠底对齐**（`margin-top: auto`），与上方内容区形成视觉分隔。

**功能**:
- 从 `/api/v1/cognitive/config` 读取四个子引擎配置
- 可编辑的配置表单：
  - PromptCompiler: topK、向量模型选择
  - AgentMesh: 路由策略（keyword_match/semantic）
  - Guardrails: PII检测开关、幻觉检测开关
  - ActionBridge: 自动执行开关、匹配置信度阈值
- 保存时调 `PUT /api/v1/cognitive/config`

**验收**:
```bash
curl -s -H "$H" "http://localhost:8080/api/v1/cognitive/config" | jq '.data'
# 期望: 返回四个子引擎的配置json
```

---

## 六、Phase 3：后端补全 + 端到端联调（2天，BE为主）

### P3-1: 知识设置API端点（BE，0.5天）

**目标**: 知识工作台配置持久化。

**新建文件**: `/home/guorongxiao/databridge-v2/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive/controller/KnowledgeSettingsController.java`

**端点**:
```java
@RestController
@RequestMapping("/api/knowledge/settings")
public class KnowledgeSettingsController {
    @GetMapping  → 返回当前配置(向量模型/chunkSize/overlap/autoSync)
    @PutMapping  → 更新配置(写入sys_config)
}
```

**复用**: 使用`sys_config`表，config_group=`knowledge`

---

### P3-2: RAG检索端点（BE，0.5天）

**目标**: 前端RagTab调用的RAG模拟端点。

**新建/扩展文件**: `KnowledgeApiController.java`（已存在则扩展）

**新增端点**:
```java
@PostMapping("/rag")
ApiResponse<RagResult> runRag(@RequestBody RagRequest req);
// RagRequest: { query, topK, enableHyde?, rerankModel? }
// RagResult: { answer, sources[{title, snippet, score}], tokensUsed }
```

---

### P3-3: 闭环验证（FE+BE联调，1天）

**验证链路**:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.data.accessToken')
H="Authorization: Bearer $TOKEN"

# V1: 元数据同步
curl -s -H "$H" "http://localhost:8080/api/ontology/mappings" | jq '.data.mappings | length'

# V2: 知识图谱
curl -s -H "$H" "http://localhost:8080/api/knowledge/graph" | jq '.data.nodes | length'

# V3: 术语库
curl -s -H "$H" "http://localhost:8080/api/knowledge/glossary" | jq '.data | length'

# V4: 工作台配置
curl -s -H "$H" "http://localhost:8080/api/knowledge/settings" | jq '.data.defaultVectorModel'

# V5: RAG检索
curl -s -X POST -H "$H" "http://localhost:8080/api/knowledge/rag" \
  -H "Content-Type: application/json" \
  -d '{"query":"如何配置数据源","topK":3}' | jq '.data.answer'

# V6: 分类体系
curl -s -H "$H" "http://localhost:8080/api/knowledge/classification" | jq '.data | length'
```

---

## 七、执行顺序

```
Phase 1: 基础（并行）
  P1-1 typesAndConstants.ts (FE, 0.5天)
  P1-2 knowledgeApi.ts (FE, 0.5天)
  └→ P1-3 KnowledgeView.tsx 瘦身 (FE, 0.5天) — 依赖P1-1+P1-2

Phase 2: Tab重组（可并行）
  P2-1 ClosedLoopTab (FE, 1天)
  P2-2 知识准备组 (FE, 0.5天)
  P2-3 知识构建组+GlossaryTab (FE, 1天)
  P2-4 SettingsTab (FE, 0.5天)
  P2-5 CognitiveConfigTab (FE, 0.5天)

Phase 3: 后端+联调
  P3-1 知识设置API (BE, 0.5天)
  P3-2 RAG检索端点 (BE, 0.5天)
  P3-3 闭环验证 (FE+BE, 1天)
```

**总工时**: ~7天

---

## 八、交付检查清单

| # | 检查项 | 验证方式 |
|---|--------|---------|
| 1 | typesAndConstants.ts 包含所有核心类型 | TS编译通过 |
| 2 | knowledgeApi.ts 封装所有13个端点 | TS编译通过 |
| 3 | KnowledgeView.tsx ≤300行 | wc -l |
| 4 | ClosedLoopTab可操作（非静态文档） | 浏览器 |
| 5 | GraphExplorerTab ≤400行 | wc -l |
| 6 | 术语库CRUD可用 | curl |
| 7 | 工作台配置可读写 | curl GET+PUT |
| 8 | RAG检索返回结果 | curl POST /api/knowledge/rag |
| 9 | 零TS错误 | npx tsc --noEmit |
| 10 | 所有Tab分组UI正常 | 浏览器截图 |
| 11 | CognitiveConfigTab排在最后靠底对齐 | 浏览器截图 |
| 12 | 认知引擎配置可读写 | curl GET+PUT /api/v1/cognitive/config |

---

## 九、一句话给PMO

**知识工作台有两条轨道：平台自用知识（元数据→向量化→Copilot）和智能体知识（本体→图谱抽取→Agent检索），后者依赖前者。先把类型体系和API封装层建好，再把8个平铺Tab按"闭环设计→知识准备→知识构建→检索→配置→认知引擎配置"六个分组重组。ArchitectureTab改成可操作的闭环设计器。GraphExplorer拆分Canvas渲染器。新增术语库、分类体系、工作台配置、认知引擎配置四个Tab，认知引擎配置排在最后靠底对齐。最后补两个后端端点（Settings+RAG），端到端验证闭环。**
