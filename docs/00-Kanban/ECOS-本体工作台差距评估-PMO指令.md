# PMO指令：本体工作台差距评估与下一步开发

> **来源**: 设计文档 `/home/guorongxiao/ECOS/03-设计文档/ontology-workbench-design.md` vs 实际代码
> **日期**: 2026-07-02
> **铁律**: 单文件粒度，每个Task改一个文件。Git提交=唯一绩效。DONE=commit hash+curl 200。

---

## 一、总体评估

| 维度 | 评分 | 说明 |
|------|:--:|------|
| 代码量 | ⭐⭐⭐⭐⭐ | 8815行/27文件，覆盖设计文档90%的组件树 |
| 架构对齐 | ⭐⭐⭐⭐ | 三栏布局、Zustand Store、Adapter层、ReactFlow画布全部到位 |
| 交互流 | ⭐⭐⭐⭐ | 实体CRUD、关系连线、属性编辑、术语绑定、数据映射弹窗——核心路径全部实现 |
| 路由整合 | ⭐⭐⭐⭐⭐ | 旧路由全部重定向，三步工作流（首页→域列表→设计器）完整 |
| API兼容 | ⭐⭐⭐ | 使用了 `/ontologies/{id}` 路径，但后端可能只有 `/ontology/ont001` ——需要验证 |
| TS质量 | ⭐⭐⭐ | 7个TS错误（useWorkbenchStore隐式any类型），不是致命但需清理 |
| 已知偏差 | ⭐⭐ | 设计文档§0.4列了10个偏差，#1(ont001写死)部分改善，#3(映射表空)后端未动 |

**一句话：PMO这次不是"吃力没产出"，是产出远超预期——8815行真实代码、三栏布局、ReactFlow画布、v1.1术语+数据映射全部落地。剩下的是补齐API路径对齐、TS错误清零、以及后端那两个致命偏差的推进。**

---

## 二、剩余差距清单

### 🔴 P0：阻塞性问题

| # | 问题 | 严重度 | 责任方 |
|---|------|--------|--------|
| P0-1 | API路径格式：前端调 `/ontologies/{id}`，需确认后端返回200还是404 | 🔴 必须验证 | PMO |
| P0-2 | `ONTOLOGY_ID="ont001"` 在ontologyApi.ts中仍为默认值，多本体切换是否真正生效？ | 🔴 必须验证 | PMO |
| P0-3 | `ecos_entity_table_mapping` 表0条记录，DataSourcePanel的AutoDiscover无数据可用 | 🔴 后端阻塞 | 需后端先建数据 |

### 🟡 P1：质量问题

| # | 问题 | 严重度 |
|---|------|--------|
| P1-1 | useWorkbenchStore.ts 7个TS错误（隐式any类型） | 🟡 |
| P1-2 | OntologyWorkbenchLayout.tsx 使用hash路由而非React Router嵌套路由——与设计文档§4.2不一致 | 🟡 |
| P1-3 | 设计文档§0.4偏差#2（Entity双归属）前端是否处理？CreateEntityModal是否还写死ont001？ | 🟡 |
| P1-4 | 画布布局持久化（localStorage）是否实现？ | 🟡 |

### 🟢 P2：增强项

| # | 问题 | 严重度 |
|---|------|--------|
| P2-1 | AutoDiscoverFromTable 按钮存在，但调用链到后端返回空→需确认前端逻辑完整 | 🟢 |
| P2-2 | 术语绑定存localStorage但未持久化到后端——设计文档标注为已知限制 | 🟢 |
| P2-3 | 旧组件OntologyDesigner/OntologyExplorer/KnowledgeGraphPage是否已删除或保留别名？ | 🟢 |

---

## 三、P0任务：API路径验证与修复

### T0-1: 验证后端API路径（0.5h）

**目标**: 确认前端API路径与后端Controller匹配，列出所有404的端点。

**改文件**: 无需改代码，纯验证。

**验收**:
```bash
# 登录
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq -r '.data.token')
H="Authorization: Bearer $TOKEN"

# 测试知识图谱
curl -s -H "$H" http://localhost:8080/api/v1/ecos/knowledge-graph | jq '.data | keys' 
# 期望: 返回nodes+edges

# 测试实体列表（注意：后端是 /ontology/ 还是 /ontologies/？）
curl -s -H "$H" http://localhost:8080/api/v1/ecos/ontologies/ont001/entities | jq '.data | length'
# 如果404 → 试 curl -s -H "$H" http://localhost:8080/api/v1/ecos/ontology/ont001/entities

# 测试关系列表
curl -s -H "$H" http://localhost:8080/api/v1/ecos/ontologies/ont001/relationships | jq '.data | length'
# 如果404 → 试 /ontology/ont001/relationships

# 测试术语库
curl -s -H "$H" "http://localhost:8080/api/glossary/terms?status=PUBLISHED" | jq '.data | length'

# 测试数据资源
curl -s -H "$H" http://localhost:8080/api/v1/datanet/metadata/resources/all | jq '.data | length'
```

**产出**: 一份路径对比表（前端调什么 vs 后端有什么），明确哪些200哪些404。

---

### T0-2: 修复API路径（如果T0-1发现不匹配）（1h）

**目标**: 让所有CRUD端点返回200。

**改文件**: `src/services/ontologyApi.ts`

**修改内容**:
- 如果后端是 `/ontology/{id}/entities`（单数），把 `const BASE` 路径中的 `ontologies` 改为 `ontology`
- 或者保留动态参数，让 `ontPath()` 拼出正确路径

**验收**: 重复T0-1的全部curl，全部返回200。

---

### T0-3: 消除ONTOLOGY_ID硬编码（1h）

**目标**: CreateEntityModal不再写死ont001，从当前选中的域推导ontology_id。

**改文件**: `src/components/ontology-workbench/modals/CreateEntityModal.tsx`

**检查项**:
1. 搜索文件中所有 `ont001` 或 `ONTOLOGY_ID` 字符串
2. 如果存在，改为从 store 读取 `currentOntologyId`
3. 验证 `useWorkbenchStore` 的 `currentOntologyId` 字段是否正确更新（域切换时）

**验收**:
```bash
cd /home/guorongxiao/c2eos
grep -rn "ont001\|ONTOLOGY_ID" src/components/ontology-workbench/ src/stores/ src/services/ontologyApi.ts | grep -v "DEFAULT_ONTOLOGY\|注释\|// "
# 期望: 0行（除了DEFAULT_ONTOLOGY常量定义外无硬编码）
```

---

## 四、P1任务：TS质量清零

### T1-1: 修复useWorkbenchStore TS错误（1h）

**目标**: `npx tsc --noEmit`对workbench相关文件零错误。

**改文件**: `src/stores/useWorkbenchStore.ts`

**当前错误**: 7个TS7018（隐式any类型），集中在initialState对象。

**修复方式**: 为initialState显式声明类型：
```typescript
const initialState: Partial<WorkbenchState> = {
  domains: [] as Domain[],
  currentDomainCode: null as string | null,
  // ... 每条加显式类型
};
```

**验收**:
```bash
cd /home/guorongxiao/c2eos
npx tsc --noEmit 2>&1 | grep "useWorkbenchStore"
# 期望: 0行
```

---

### T1-2: 验证Design Doc §0.4偏差#2处理（0.5h）

**目标**: 确认CreateEntityModal提交的entity数据中没有写死domain_id或ontology_id导致双归属冲突。

**改文件**: `src/components/ontology-workbench/modals/CreateEntityModal.tsx`

**检查**: 
- 提交payload中是否有 `domain_id` 字段？如果有，是否与当前域一致？
- `ontology_id` 是否从store.currentOntologyId动态获取？
- 如果store中没有currentOntologyId但有currentDomainCode，是否能反向查找？

**验收**: 创建实体后，查数据库确认entity的domain_id和ontology_id一致：
```bash
PGPASSWORD=root psql -h localhost -U root -d sys_man \
  -c "SELECT id, code, domain_id, ontology_id FROM ecos_ontology_entity ORDER BY created_at DESC LIMIT 3;"
```

---

## 五、P2任务：增强

### T2-1: 画布布局持久化（1h）

**目标**: 用户拖拽实体节点后，刷新页面布局不丢失。

**改文件**: `src/components/ontology-workbench/DomainGraphCanvas.tsx`

**实现**: 
- onNodesChange时，防抖500ms后用localStorage保存 `{ [nodeId]: {x, y} }`
- 初始化时从localStorage恢复位置
- key格式: `workbench-layout-{domainCode}`

**验收**: 
1. 浏览器打开设计器，拖拽一个节点
2. 刷新页面 → 节点位置保持

---

### T2-2: 旧组件清理确认（0.5h）

**目标**: 确认旧路由对应的组件文件已删除或保留为别名。

**检查清单**:
- `src/pages/OntologyDesigner.tsx` → 如果只是重定向壳，保留；如果有独立实现，确认是否与工作台冲突
- `src/pages/OntologyExplorer.tsx` → 同上
- `src/pages/KnowledgeGraph.tsx` → 同上（注意：不是KnowledgeGraphHome）

**验收**:
```bash
cd /home/guorongxiao/c2eos
ls -la src/pages/OntologyDesigner* src/pages/OntologyExplorer* src/pages/KnowledgeGraph.tsx 2>&1
# 列出哪些还在，哪些已删
```

---

## 六、后端依赖清单（需PMO以外推动）

以下三项前端无法自行解决，需后端配合：

| # | 后端任务 | 影响的前端功能 | 优先级 |
|---|---------|--------------|--------|
| B-1 | `ecos_entity_table_mapping` 建种子数据（至少2-3条映射） | DataSourcePanel.AutoDiscover、FieldMappingTable | 🔴 |
| B-2 | 确认 `/api/v1/ecos/ontology/ont001/entities` vs `/ontologies/` 的路由注册 | 所有CRUD端点 | 🔴 |
| B-3 | `ecos_object_data` 加 `entity_id` 字段（§0.4偏差#7） | ObjectExplorer集成 | 🟡 |

---

## 七、执行顺序

```
P0-1 验证API路径（30min）
  ├→ 如果路径不匹配 → P0-2 修复（1h）
  └→ 如果路径正确 → 跳过P0-2
P0-3 消除ont001硬编码（1h）
P1-1 TS错误清零（1h）
P1-2 双归属验证（30min）
T2-1 布局持久化（1h）
T2-2 旧组件清理（30min）
```

**总工时**: ~5.5h。P0全部可并行。

---

**一句话给PMO**: 代码底盘扎实，8815行不是stub。把API路径对齐、TS错误清零、ont001硬编码消除——这三件事做完，本体工作台就达到可交付状态。
