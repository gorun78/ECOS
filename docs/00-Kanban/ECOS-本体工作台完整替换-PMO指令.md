# PMO指令：ceos_new 本体工作台完整替换 + 后端对接

> **来源**: 肖总 | **日期**: 2026-07-05 | **工期**: 预计5天
> **设计源**: ceos_new `/home/guorongxiao/ceos_new/` — server.ts 本体相关API + 前端本体组件
> **铁律**: PMO唯一产出= commit hash + curl PASS/FAIL。不产出分析文档。每个Task一个commit。

---

## 0. 前置：理解ceos_new本体架构

### 前端组件清单

| ceos_new源文件 | 行数 | 用途 |
|------|:--:|------|
| `src/App.tsx` | ~725 | 统一状态管理：ObjectType/LinkType/ActionType/FunctionType/Interface/SharedProperty/Dataset/Domain |
| `src/types.ts` | 269 | Palantir对齐类型系统 |
| `src/components/OntologyGraph.tsx` | 277 | 可拖拽SVG关系图画布 |
| `src/components/ObjectTypeView.tsx` | ~800 | 对象类型详情编辑器（含属性CRUD+数据集映射） |
| `src/components/LinkTypeView.tsx` | ~515 | 链接类型编辑器（外键/关联表两种模式） |
| `src/components/ActionTypeView.tsx` | ~992 | 操作类型编辑器（参数+规则+校验规则+表单布局） |
| `src/components/FunctionTypeView.tsx` | ~873 | 函数类型编辑器（TypeScript代码编辑器） |
| `src/components/OverviewView.tsx` | 815 | 总览页（OntologyGraph+统计卡片+审计日志） |
| `src/components/Sidebar.tsx` | ~300 | 左栏：域筛选+实体树+搜索+新建按钮 |
| `src/components/ObjectExplorerView.tsx` | ~700 | 对象浏览器：数据预览+关系浏览+Action执行 |

### 后端API设计（ceos_new server.ts参考）

```
本体映射:
  GET    /api/ontology/mappings        → 对象↔数据集映射列表
  POST   /api/ontology/mappings        → 创建/更新映射

本体导出:
  GET    /api/ontology/export          → 导出本体为JSON

本体数据:
  GET    /api/ontology/data            → 本体实例数据

AI辅助提案:
  GET    /api/ontology/proposals       → AI生成的建模提案列表
  POST   /api/ontology/proposals       → 提交新提案
  POST   /api/ontology/proposals/:id/verify  → 验证提案
  POST   /api/ontology/proposals/:id/execute → 执行已审批提案

血缘:
  POST   /api/lineage/parse            → 解析血缘数据（OpenLineage/Atlas）
  GET    /api/lineage/impact           → 下游影响分析
```

---

## 1. 禁止清单

1. **禁止** 改动c2eos现有路由 `/ontology_workbench` 结构
2. **禁止** 删除c2eos现有后端API——扩展不替换
3. **禁止** 引入新npm依赖
4. **禁止** 产出分析文档
5. **禁止** 前端组件直接使用ceos_new的mockData——必须对接后端API

---

## 2. Phase 1: 前端本体工作台替换（FE，2天）

> **注**: T1.1-T1.4 上次PMO已完成（commit dc48f70），如已就绪则跳过，直接验证。

### T1.1: 类型系统对齐（0.5h，FE）

**目标**: c2eos类型系统对齐ceos_new。

**改文件**: `/home/guorongxiao/c2eos/src/types/ontology.ts`（新建或更新）

**源**: ceos_new `src/types.ts` — 复制ObjectType/LinkType/ActionType/FunctionType/InterfaceType/SharedProperty/OntologyDomain/Dataset全部接口。mapping改为可选。

**验收**: `npx tsc --noEmit | grep "ontology.ts"` → 0 errors

### T1.2: 移植OntologyGraph + Sidebar（2h，FE）

**改文件**:
- `/home/guorongxiao/c2eos/src/components/OntologyGraph.tsx`（新建）
- `/home/guorongxiao/c2eos/src/components/ontology/Sidebar.tsx`（新建）

**源**: ceos_new `OntologyGraph.tsx` + `Sidebar.tsx`

**适配**: 图标组件统一用c2eos的lucide-react；域列表+实体树数据从后端API加载。

**验收**: `npx tsc --noEmit | grep -i "ontolog\|sidebar"` → 0 errors

### T1.3: 移植4个详情编辑器（3h，FE）

**改文件**（新建4个）:
- `/home/guorongxiao/c2eos/src/pages/ontology/ObjectTypeDetail.tsx`
- `/home/guorongxiao/c2eos/src/pages/ontology/LinkTypeDetail.tsx`
- `/home/guorongxiao/c2eos/src/pages/ontology/ActionTypeDetail.tsx`
- `/home/guorongxiao/c2eos/src/pages/ontology/FunctionTypeDetail.tsx`

**源**: ceos_new的对应View组件

**关键适配**: 
- ObjectTypeDetail → mapping区域对接T4.1的映射API
- ActionTypeDetail → validationRules对接后端
- 所有CRUD操作对接现有 `/api/v1/ecos/objects` 等端点

**验收**: `npx tsc --noEmit | grep "ontology/"` → 0 errors

### T1.4: ObjectExplorerView移植（2h，FE）

**目标**: 对象浏览器——本体数据预览+关系浏览+Action执行。

**改文件**: `/home/guorongxiao/c2eos/src/pages/ObjectExplorerView.tsx`（新建或替换）

**源**: ceos_new `ObjectExplorerView.tsx`

**适配**: 数据预览从后端API加载实例数据；关系浏览用Link API；Action执行按钮对接T4.4的execute端点。

**验收**: `npx tsc --noEmit | grep -i "explorer"` → 0 errors

### T1.5: OntologyWorkbenchLayout整合（1.5h，FE）

**目标**: 把所有新组件整合到本体工作台布局。

**改文件**: `/home/guorongxiao/c2eos/src/pages/OntologyWorkbenchLayout.tsx`

**布局**:
```
┌──────────┬──────────────────────┬──────────┐
│ Sidebar  │  中央编辑区          │ 右侧面板  │
│ 域筛选   │  Overview(图+统计)   │ 属性编辑器 │
│ 实体树   │  ObjectTypeDetail    │           │
│ 搜索     │  LinkTypeDetail      │           │
│ +新建    │  ActionTypeDetail    │           │
│          │  FunctionTypeDetail  │           │
│          │  ObjectExplorerView  │           │
└──────────┴──────────────────────┴──────────┘
```

**验收**:
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/ontology_workbench  # 200
```

---

## 3. Phase 2: 后端API对接（BE，2天）

### T2.1: Ontology Mapping CRUD（2h，BE）

**目标**: 对象类型↔数据集的映射管理。

**改文件**: `/home/guorongxiao/databridge-v2/dccheng/dccheng-impl/src/main/java/com/chinacreator/gzcm/dccheng/ontology/controller/OntologyMappingController.java`（新建）

**端点**（参照ceos_new server.ts L676-720）:
```java
GET  /api/v1/ontology/mappings              → 全部映射列表
POST /api/v1/ontology/mappings              → 创建/更新映射
     body: {objectTypeId, datasetId, propertyMappings: {propId: colName}}
GET  /api/v1/ontology/mappings/{objectId}    → 查特定对象的映射
DELETE /api/v1/ontology/mappings/{objectId}  → 删除映射
```

**数据库**: 新建表 `ecos_ontology_mapping`(id, object_type_id, dataset_id, property_mappings_json, created_at, updated_at)

**验收**:
```bash
# 创建映射
curl -s -X POST -H @/tmp/auth_header.txt http://localhost:8080/api/v1/ontology/mappings \
  -H "Content-Type: application/json" \
  -d '{"objectTypeId":"Project","datasetId":"ds_projects","propertyMappings":{"name":"project_name","budget":"total_budget"}}' \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('PASS' if d.get('code')==0 else 'FAIL')"

# 查询映射
curl -s -H @/tmp/auth_header.txt http://localhost:8080/api/v1/ontology/mappings/Project \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('PASS' if d.get('data') and d['data'].get('propertyMappings') else 'FAIL')"
```

### T2.2: Ontology Export端点（1h，BE）

**目标**: 导出本体完整定义为JSON（含对象/链接/动作/函数/映射）。

**改文件**: 扩展现有OntologyDomainApiController或新建OntologyExportController

**端点**: `GET /api/v1/ontology/export`

**验收**:
```bash
curl -s -H @/tmp/auth_header.txt http://localhost:8080/api/v1/ontology/export \
  | python3 -c "import sys,json;d=json.load(sys.stdin);data=d.get('data',{});print('PASS' if 'objectTypes' in data and 'linkTypes' in data else 'FAIL')"
```

### T2.3: Ontology Data查询端点（1h，BE）

**目标**: 按对象类型查询本体实例数据（支持分页/过滤）。

**改文件**: 扩展现有ObjectController

**端点**: `GET /api/v1/ontology/data?type={objectType}&page=1&size=20`

**验收**:
```bash
curl -s -H @/tmp/auth_header.txt "http://localhost:8080/api/v1/ontology/data?type=Project&page=1&size=10" \
  | python3 -c "import sys,json;d=json.load(sys.stdin);records=d.get('data',{}).get('data',[]);print('PASS' if len(records)>0 else 'FAIL')"
```

### T2.4: AI建模提案端点（2h，BE）

**目标**: AI辅助本体建模——接收AI生成的建模提案，支持人工审批后执行。

**改文件**: `/home/guorongxiao/databridge-v2/dccheng/dccheng-impl/src/main/java/com/chinacreator/gzcm/dccheng/ontology/controller/OntologyProposalController.java`（新建）

**端点**（参照ceos_new server.ts L2582-2766）:
```java
GET   /api/v1/ontology/proposals              → 提案列表
POST  /api/v1/ontology/proposals              → 提交新提案
      body: {type:"NEW_OBJECT"|"NEW_LINK"|"MODIFY", payload:{...}, source:"AI_Copilot"}
POST  /api/v1/ontology/proposals/{id}/verify   → 验证提案（检查冲突/完整性）
POST  /api/v1/ontology/proposals/{id}/execute  → 执行已审批提案（实际创建/修改本体）
```

**数据库**: 新建表 `ecos_ontology_proposal`(id, type, payload_json, status, source, verified_at, executed_at, created_at)

**验收**:
```bash
# 创建提案
curl -s -X POST -H @/tmp/auth_header.txt http://localhost:8080/api/v1/ontology/proposals \
  -H "Content-Type: application/json" \
  -d '{"type":"NEW_OBJECT","payload":{"displayName":"供应商","apiName":"Supplier","icon":"Truck"},"source":"AI_Copilot"}' \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('PASS' if d.get('code')==0 else 'FAIL')"

# 验证提案
PROPOSAL_ID=...  # 上一步返回的ID
curl -s -X POST -H @/tmp/auth_header.txt "http://localhost:8080/api/v1/ontology/proposals/$PROPOSAL_ID/verify" \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('PASS' if d.get('data',{}).get('valid') else 'FAIL')"

# 执行提案
curl -s -X POST -H @/tmp/auth_header.txt "http://localhost:8080/api/v1/ontology/proposals/$PROPOSAL_ID/execute" \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('PASS' if d.get('code')==0 else 'FAIL')"
```

### T2.5: Lineage血缘端点（1.5h，BE）

**目标**: 血缘数据解析 + 下游影响分析。

**改文件**: `/home/guorongxiao/databridge-v2/dccheng/dccheng-impl/src/main/java/com/chinacreator/gzcm/dccheng/lineage/LineageController.java`（新建）

**端点**（参照ceos_new server.ts L762-835）:
```java
POST /api/v1/lineage/parse    → 解析OpenLineage/Atlas格式血缘数据
GET  /api/v1/lineage/impact   → 下游影响分析（改某个对象会影响哪些对象）
     ?objectId={id}&depth=3
```

**验收**:
```bash
# 影响分析
curl -s -H @/tmp/auth_header.txt "http://localhost:8080/api/v1/lineage/impact?objectId=Project&depth=2" \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('PASS' if d.get('code')==0 else 'FAIL')"
```

---

## 4. Phase 3: 精修复 + QA（FE+QA，1天）

### T3.1: 编译零新增错误（FE，1h）

```bash
cd /home/guorongxiao/c2eos && npx tsc --noEmit 2>&1 | grep "error TS" | \
  grep -v "api.ts\|DictManager\|KnowledgeGraph\|CommandPalette\|DatasetExplorer\|ScenarioSandbox\|AgentStudio" | wc -l
# 期望: 0
```

### T3.2: 精修复八步法（FE，3h）

**范围**: `/ontology_workbench` 全部页面

a. 页面加载无白屏无控制台报错
b. Object/Link/Action/Function CRUD全部可用
c. 样式一致、国际化无裸key
d. 域下拉/属性类型/数据集下拉从后端加载
e. 数据字典正确
f. 域筛选→实体树→详情编辑器联动正常
g. OntologyGraph拖拽流畅、点击跳转正常
h. 种子数据可见（≥1域+≥3对象+≥2链接+≥1映射）

### T3.3: QA 15项验证（QA，4h）

| # | 测试场景 | 验证方式 | 期望 |
|---|---------|----------|------|
| 1 | 页面加载 | `curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/ontology_workbench` | 200 |
| 2 | 域列表 | `curl -s -H @/tmp/auth_header.txt http://localhost:8080/api/v1/ontology/domains` | code=0, data≥3 |
| 3 | 对象列表 | `curl -s -H @/tmp/auth_header.txt http://localhost:8080/api/v1/ontology/objects` | code=0, data含mapping+domainId |
| 4 | 创建对象 | POST 新对象 → 列表+1 | 201或code=0 |
| 5 | 编辑对象属性 | PUT 更新 → 详情刷新可见 | 200或code=0 |
| 6 | 删除对象 | DELETE → 列表-1，关联清理 | 204或code=0 |
| 7 | 创建外键链接 | POST foreign_key链接 → 图上线出现 | code=0 |
| 8 | 创建关联表链接 | POST M:N链接 → 成功 | code=0 |
| 9 | 创建映射 | POST /api/v1/ontology/mappings | code=0 |
| 10 | 查询映射 | GET /api/v1/ontology/mappings/{id} | 返回propertyMappings |
| 11 | 导出本体 | GET /api/v1/ontology/export | 含objectTypes+linkTypes |
| 12 | 创建提案→验证→执行 | 三步连调 | 全部code=0 |
| 13 | 血缘影响分析 | GET /api/v1/lineage/impact | code=0 |
| 14 | OntologyGraph拖拽 | 浏览器手动拖节点 | 位置更新 |
| 15 | 编译零新增错误 | `npx tsc --noEmit` | PMO新文件0 error |

**QA判定**: 全部15项 PASS → 汇报肖总。任何 FAIL → 打回。

---

## 5. 执行顺序

```
T1.1 (类型系统)
  ↓
T1.2 (Graph+Sidebar) + T1.3 (4个编辑器，可并行) + T1.4 (Explorer)
  ↓
T1.5 (WorkbenchLayout整合)
  ↓
T2.1 (Mapping API) + T2.2 (Export API) + T2.5 (Lineage API) — 可并行
  ↓
T2.3 (Data API) + T2.4 (Proposal API) — 可并行
  ↓
T3.1 (编译) → T3.2 (精修复) → T3.3 (QA)
```

**可并行**: T1.2/T1.3/T1.4三者 | T2.1/T2.2/T2.5三者 | T2.3/T2.4

---

## 6. 交付检查清单

| Task | 负责人 | 文件 | 验收 |
|------|--------|------|------|
| T1.1 | FE | types/ontology.ts | tsc |
| T1.2 | FE | OntologyGraph.tsx + Sidebar.tsx | tsc |
| T1.3 | FE | 4个Detail组件 | tsc |
| T1.4 | FE | ObjectExplorerView.tsx | tsc |
| T1.5 | FE | OntologyWorkbenchLayout.tsx | curl 200 |
| T2.1 | BE | OntologyMappingController | curl PASS |
| T2.2 | BE | OntologyExportController | curl PASS |
| T2.3 | BE | ObjectController扩展 | curl PASS |
| T2.4 | BE | OntologyProposalController | curl PASS (3步) |
| T2.5 | BE | LineageController | curl PASS |
| T3.1 | FE | 编译验证 | tsc 0新增错误 |
| T3.2 | FE | 精修复 | 8/8步通过 |
| T3.3 | QA | 15项验证 | 全部PASS |

---

## 一句话给PMO

**按ceos_new的server.ts API设计，把本体工作台完整替换：前端搬OntologyGraph+4编辑器+Explorer，后端新建Mapping/Proposal/Lineage三组API。15项QA，一个FAIL打回。**
