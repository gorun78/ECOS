# PMO指令：四大工作台全面替换 + 后端对接 + 精修

> **来源**: 肖总 | **日期**: 2026-07-07 | **工期**: 5天 | **角色**: FE + BE
> **设计源**: ceos_new `/home/guorongxiao/ceos_new/` (最新 b92e310)
> **铁律**: 一个Task一个commit。TS编译0新增错误。读源码再改。禁用mock数据——必须对接后端API。

---

## 0. 范围总览

| # | 工作台 | ceos_new源 | 行数 | c2eos现状 | 操作 |
|---|--------|-----------|------|-----------|------|
| 0 | 安全中心 | SecurityCenterView.tsx | 3,290 | ✅ 已完成 | 跳过 |
| 1 | 数据工作台 | DataIntegrationView + 4子组件 | ~8,800 | 638行薄壳 | **完整替换** |
| 2 | 本体工作台 | 已移植各组件 | ~3,500 | 各组件已就绪 | **对接精修** |
| 3 | 知识库工作台 | AIPWorkbench/KnowledgeView | 2,066 | 482行旧版 | **替换升级** |
| 4 | AI工作台 | AIPWorkbench (8文件) | 6,420 | 698行薄AgentStudio | **完整替换** |

**后端审计**: Pipeline/DataSource/Catalog/Metadata/Ontology/Knowledge/AgentMesh 各Controller已存在，大部分API可用。

---

## 1. 禁止清单

1. **禁止** 在前端组件中使用mock数据——全部对接后端API
2. **禁止** 引入新npm依赖（recharts/lucide-react已安装）
3. **禁止** 删除c2eos现有路由路径
4. **禁止** 改动后端已有Controller签名（可新增端点，不改旧签名）
5. **禁止** 产出分析文档
6. **禁止** 改Topbar/App.tsx主布局
7. **禁止** 图标用LucideIcon wrapper——直接用lucide-react原生import

---

## 2. Phase 1: 数据工作台替换（FE 1天 + BE 0.5天）

### T1.1: 移植DataIntegrationView主框架（3h，FE）

**源**: `/home/guorongxiao/ceos_new/src/components/DataIntegrationView.tsx` (4333行)
**目标**: `/home/guorongxiao/c2eos/src/pages/DataWorkbenchLayout.tsx`（重写）

**DataIntegrationView 9个Tab**:
1. Connections — 数据连接管理
2. Syncs — 同步任务
3. Pipelines — 管道列表
4. Health — 健康检查
5. Lineage — 血缘
6. Pipeline Builder — 可视化管道编辑器
7. Code Repositories — 代码仓库
8. Code Workbooks — 代码工作簿
9. Contour — 数据轮廓分析

**适配要点**:
- `showToast` prop → console.log静默处理（c2eos无全局toast机制）
- `objectTypes/datasets` props → 从后端API加载（用api.ts已有或新建API调用）
- `LucideIcon` → 直接用lucide-react命名导入
- 保留Pipeline Builder / Repositories / Workbooks / Contour等子组件引用

**验收**: TS编译0错误，`http://localhost:3000/#/data-workbench` 返回200

---

### T1.2: 移植4个子组件（2h，FE）

| 源文件 | 目标 | 行数 |
|--------|------|------|
| `PipelineBuilderPrototype.tsx` | `src/pages/data-workbench/PipelineBuilderPrototype.tsx` | 2,612 |
| `ContourPrototype.tsx` | `src/pages/data-workbench/ContourPrototype.tsx` | 457 |
| `CodeRepositoriesPrototype.tsx` | `src/pages/data-workbench/CodeRepositoriesPrototype.tsx` | 776 |
| `CodeWorkbooksPrototype.tsx` | `src/pages/data-workbench/CodeWorkbooksPrototype.tsx` | 615 |

**适配**: 同T1.1规则。LucideIcon→lucide-react，showToast→静默处理。

**验收**: TS编译0错误

---

### T1.3: 后端API对接 — 数据工作台（2h，BE）

**目标**: DataIntegrationView各Tab对接真实后端API。

**已有后端**（直接可用）:
- `DataSourceController` — 数据源CRUD
- `CatalogController` — 数据资产目录
- `MetadataController` — 元数据
- `PipelineController` — 管道CRUD
- `AutoDiscoverController` — 自动发现

**需新增**（如前端需要，按需创建）:
- DataSync任务CRUD端点（如ceos_new mock中有sync任务列表）
- DataHealth健康检查聚合端点

**对接策略**: 前端组件在`useEffect`中调用后端API获取数据，替换mockData初始值。

**验收**:
```bash
# 数据源列表
curl -s -H @/tmp/auth_header.txt http://localhost:8080/api/v1/datasources | python3 -c "import sys,json;d=json.load(sys.stdin);print('PASS' if d.get('code')==0 else 'FAIL')"
```

---

### T1.4: 数据工作台精修复（3h，FE）

按八步法对数据工作台页面执行精修：
a. 页面加载无白屏无控制台报错
b. 9个Tab切换正常
c. 各Tab数据从后端加载
d. 样式一致/i18n无裸key
e. Pipeline Builder画布可用
f. 交互体验流畅
g. 种子数据（如后端返回空则显示空状态，不白屏）
h. 配置/字典项正常

---

## 3. Phase 2: 知识库工作台替换（FE 1天 + BE 0.5天）

### T2.1: 知识库工作台替换（3h，FE）

**源**: `/home/guorongxiao/ceos_new/src/components/AIPWorkbench/KnowledgeView.tsx` (2,066行 / 118KB)
**目标**: `/home/guorongxiao/c2eos/src/pages/KnowledgeView.tsx`（重写）

**ceos_new KnowledgeView 包含**:
- 向量库管理（创建/删除/搜索）
- 文本切片（Chunk策略配置）
- RAG检索测试
- 知识图谱同步状态
- 元数据向量化

**适配**: 
- LucideIcon → lucide-react
- 去掉AIPWorkbench内部mock data → 对接后端KnowledgeApiController + KnowledgeGraphController
- Toast → 静默

**依赖**: ceos_new AIPWorkbench/types.ts + mockData.ts → 复制到 `src/pages/knowledge/`

**验收**: TS编译0错误，`http://localhost:3000/#/knowledge_view` 返回200

---

### T2.2: 知识库后端API对接（1h，BE）

**已有后端**:
- `KnowledgeApiController` — 向量库CRUD
- `KnowledgeGraphController` — 知识图谱同步

**需检查**: 端点签名是否匹配ceos_new KnowledgeView的API调用模式。

**验收**:
```bash
curl -s -H @/tmp/auth_header.txt http://localhost:8080/api/v1/knowledge/collections | python3 -c "import sys,json;d=json.load(sys.stdin);print('PASS' if d.get('code')==0 else 'FAIL')"
```

---

### T2.3: 知识库工作台精修复（2h，FE）

八步法精修。

---

## 4. Phase 3: AI工作台替换（FE 1.5天 + BE 0.5天）

### T3.1: 移植AIPWorkbench完整套件（4h，FE）

**ceos_new AIPWorkbench/** (8文件, 6,420行):

| 源文件 | 目标 | 行数 |
|--------|------|------|
| `index.tsx` | `src/pages/ai-workbench/index.tsx` | 199 |
| `types.ts` | `src/pages/ai-workbench/types.ts` | 95 |
| `mockData.ts` | `src/pages/ai-workbench/mockData.ts` (后端替换前临时用) | 312 |
| `DashboardView.tsx` | `src/pages/ai-workbench/DashboardView.tsx` | 287 |
| `LogicView.tsx` | `src/pages/ai-workbench/LogicView.tsx` | 630 |
| `AgentStudioView.tsx` | `src/pages/ai-workbench/AgentStudioView.tsx` | 1,219 |
| `KnowledgeView.tsx` | `src/pages/ai-workbench/KnowledgeView.tsx` | 2,066 |
| `ModelCatalogView.tsx` | `src/pages/ai-workbench/ModelCatalogView.tsx` | 263 |
| `GuardrailsView.tsx` | `src/pages/ai-workbench/GuardrailsView.tsx` | 1,449 |

**6个模块**: 总览仪表盘 / Logic逻辑编排 / Agent智能体 / Knowledge知识库 / Model Catalog模型目录 / Guardrails安全审计

**关键**: AI工作台内部的KnowledgeView与Phase 2的知识库工作台是**同一组件不同用途**——AI工作台是集成视图，知识库工作台是独立全屏视图。两个都保留。

**路由**: 新增 `ai-workbench` route → 指向AIPWorkbench index.tsx
**路由**: 保留 `agent_studio` → 重定向到 `ai-workbench`

**验收**: TS编译0错误，`http://localhost:3000/#/ai-workbench` 返回200

---

### T3.2: AI工作台后端API对接（2h，FE+BE）

**已有后端**:
- `AgentMeshController` — Agent Mesh管理
- `AgentConfigController` — Agent配置CRUD
- `AgentProfileController` — Agent Profile
- `AgentCallController` — Agent调用

**对接策略**: 
- LogicView → 对接PipelineController（管道编排即Logic管道）
- AgentStudioView → 对接AgentConfig+AgentMesh控制器
- ModelCatalogView → 新增或对接模型注册API
- GuardrailsView → 对接GuardrailsApiController

**验收**: Agent列表从后端加载（非mock），创建Agent成功。

---

### T3.3: AI工作台精修复（2h，FE）

八步法精修。

---

## 5. Phase 4: 本体工作台精修（FE 1天）

### T4.1: 本体工作台后端对接验证（2h，FE+BE）

本体工作台各组件已移植完成（commit b9138f5）。验证：
- ObjectTypeDetail CRUD → OntologyController端点
- LinkTypeDetail → OntologyRelationshipController
- ActionTypeDetail → OntologyActionController
- FunctionTypeDetail → 函数管理端点
- OntologyGraph → 关系图谱数据从后端加载
- ObjectExplorer → 实例数据从OntologyDataController加载
- Sidebar域树 → OntologyDomainApiController

**重点**: 逐一确认每个组件的CRUD操作走的是后端API而非mock数据。

---

### T4.2: 本体工作台八步精修（4h，FE）

对 `/ontology_workbench` 执行完整八步法：
a. 页面加载 + 控制台
b. Object/Link/Action/Function CRUD
c. 样式/i18n
d. 配置项（域下拉/属性类型下拉从后端加载）
e. 数据字典正确
f. 域筛选→实体树→详情编辑器联动
g. Graph拖拽/Explorer交互
h. 种子数据（≥1域+≥3对象+≥2链接）

---

## 6. Phase 5: 全局QA（QA 2h）

### T5.1: 集成验证

| # | 页面 | 验证 | 期望 |
|---|------|------|------|
| 1 | 数据工作台 | 9个Tab全部可切换 | 数据从后端加载 |
| 2 | 知识库工作台 | 向量库CRUD | 创建/搜索/切片可用 |
| 3 | AI工作台 | 6个模块切换 | Agent/MCP/Logic可用 |
| 4 | 本体工作台 | Graph+编辑器+Explorer | 全部CRUD正常 |
| 5 | 全部路由 | curl 15条关键路由 | 全部200 |
| 6 | TS编译 | `npx tsc --noEmit` | PMO改动0新增错误 |
| 7 | 无mock残留 | grep mockData 新文件 | 仅AI工作台临时保留 |

---

## 7. 执行顺序

```
Phase 1 (数据工作台) ──→ Phase 4 (本体精修)
Phase 2 (知识库)    ──→ Phase 4 (本体精修)    } 三线并行
Phase 3 (AI工作台)  ──→ Phase 4 (本体精修)
                              ↓
                        Phase 5 (全局QA)
```

T1.1/T2.1/T3.1 可并行开跑（三个FE）。
后端T1.3/T2.2/T3.2可与前端并行执行。

---

## 8. 交付检查清单

| Task | 角色 | 核心文件 | 验收 |
|------|------|---------|------|
| T1.1 | FE | DataWorkbenchLayout.tsx | TS + curl |
| T1.2 | FE | 4个子组件 | TS |
| T1.3 | BE | DataSync/Health端点 | curl |
| T1.4 | FE | 数据工作台精修 | 八步 |
| T2.1 | FE | KnowledgeView.tsx | TS + curl |
| T2.2 | BE | Knowledge API对齐 | curl |
| T2.3 | FE | 知识库精修 | 八步 |
| T3.1 | FE | ai-workbench/ (9文件) | TS + curl |
| T3.2 | FE+BE | AI API对接 | curl |
| T3.3 | FE | AI工作台精修 | 八步 |
| T4.1 | FE+BE | 本体API验证 | curl |
| T4.2 | FE | 本体精修 | 八步 |
| T5.1 | QA | 6项集成验证 | 全部PASS |

---

## 9. 一句话给PMO

**四个工作台：数据(DataIntegrationView 9Tab)→知识库(AIP KnowledgeView 2066行)→AI(AIPWorkbench 8文件)→本体(精修)。全部对接后端API，不做mock。总计≈17,300行前端代码移植+后端API补齐。四线并行，5天，一个FAIL打回。**
