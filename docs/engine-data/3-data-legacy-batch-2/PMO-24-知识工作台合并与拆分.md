# PMO-24: 知识工作台合并+拆分

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> 来源: 肖国荣 | 日期: 2026-08-07 | 优先级: 🔴 P0
> **范围**: 前端 `src/pages/knowledge/` + `src/pages/aiworkbench/KnowledgeView.tsx` + `ScenarioManagementView.tsx`
> **工期**: 3.5天 | **协同**: ECOS-FE

---

## §背景

知识工作台存在**双版本**：
- `pages/KnowledgeView.tsx` (134行，Shell) — 当前知识工作台入口
- `aiworkbench/KnowledgeView.tsx` (2073行 🔴>800) — AI工作台版本，含Copilot

需要合并为统一入口：`pages/KnowledgeView.tsx`作Shell，aiworkbench版本的Tab迁入Shell。

同时拆分`ScenarioManagementView.tsx` (1536行 🔴>800)。

---

## §禁止清单

1. ❌ 合并后知识工作台所有功能必须完整保留
2. ❌ 不新增npm依赖
3. ❌ 不改后端API路径
4. ❌ **不在此指令中做i18n**——i18n见PMO-26
5. ❌ 不硬编码Tailwind颜色（铁律4.1）
6. ❌ 文件不超800行（铁律4.6）

---

## §Task

### T1: KnowledgeView双版本合并（1.5天）

**策略**: `pages/KnowledgeView.tsx` 扩为统一Shell（≤300行），`aiworkbench/KnowledgeView.tsx` 的Tab逐个迁移

**步骤**：

1. **审计aiworkbench/KnowledgeView.tsx**：列出当前12个Tab + 特有功能(Copilot集成/知识图谱检索)

2. **pages/KnowledgeView.tsx扩充**：
   - 接收`showCopilot` prop（可选，AI工作台调用时传true）
   - Tab注册表从`typesAndConstants.ts`加载（现有12 Tab）
   - 如有aiworkbench版本独有Tab→`typesAndConstants.ts`中追加

3. **aiworkbench/KnowledgeView.tsx删除**：
   - 拆分为独立Tab文件后删除此文件
   - `aiworkbench/index.tsx`中的路由指向改为`pages/KnowledgeView`

4. **Copilot兼容**：AI工作台侧的Copilot Panel通过`showCopilot`prop控制，不影响独立知识工作台

**验收**: 
```bash
# 仅存在一个KnowledgeView入口
find src/pages -name "KnowledgeView.tsx" | wc -l
# 期望: 1 (pages/KnowledgeView.tsx)

wc -l src/pages/KnowledgeView.tsx
# 期望: ≤300
```

---

### T2: aiworkbench/KnowledgeView.tsx拆分（2天）

**文件**: `src/pages/aiworkbench/KnowledgeView.tsx` (2073行) → 拆为独立Tab文件

**拆分方案**：保留已有的`pages/knowledge/tabs/*.tsx`，将aiworkbench版本独有逻辑迁入：

| Tab | 已有文件 | 操作 | 目标行数 |
|-----|---------|------|:--:|
| closed_loop | `tabs/ClosedLoopTab.tsx` | 补全aiworkbench版逻辑 | ≤400 |
| sync | `tabs/SyncTab.tsx` | 补全 | ≤400 |
| lineage | `tabs/LineageTab.tsx` | 补全 | ≤400 |
| ontology | `tabs/OntologyTab.tsx` | 补全 | ≤400 |
| graph_sync | `tabs/GraphSyncTab.tsx` | 补全 | ≤400 |
| classification | `tabs/ClassificationTab.tsx` | 补全 | ≤400 |
| index | `tabs/IndexTab.tsx` | 补全 | ≤400 |
| rag | `tabs/RagTab.tsx` | 补全 | ≤400 |
| cognitive_config | `tabs/CognitiveConfigTab.tsx` | 补全 | ≤400 |
| knowledge_extraction | `tabs/KnowledgeExtractionTab.tsx` | 需解码(二进制文件) | ≤500 |
| rules | `tabs/KnowledgeRuleRepositoryTab.tsx` | 补全 | ≤400 |
| compliance_check | `tabs/KnowledgeComplianceCheckTab.tsx` | 补全 | ≤400 |

**实现要求**：
1. 每个Tab是独立React组件（无外部状态依赖，通过props通信）
2. 共享类型从`typesAndConstants.ts`导入
3. API调用统一走`services/knowledgeApi.ts`
4. [ ] `KnowledgeExtractionTab.tsx`当前为二进制编码文件——需用`cat`读取验证内容，如损坏则根据后端API重写

**TS编译验收**:
```bash
cd /home/guorongxiao/ECOS/ecos_frontend && npx tsc --noEmit 2>&1 | grep -c "KnowledgeView\|KnowledgeExtraction\|KnowledgeRule\|KnowledgeCompliance"
# 期望: 0
```

---

### T3: ScenarioManagementView拆分（1天）

**文件**: `src/pages/ScenarioManagementView.tsx` (1536行) → 拆为3文件

| 新文件 | 路径 | 目标行数 | 职责 |
|--------|------|:--:|------|
| `ScenarioList.tsx` | `src/pages/scenario/ScenarioList.tsx` | ≤300 | 情景列表+搜索+新建按钮 |
| `ScenarioEditor.tsx` | `src/pages/scenario/ScenarioEditor.tsx` | ≤300 | 情景编辑表单(名称/变量/参数) |
| `SimulationResultPanel.tsx` | `src/pages/scenario/SimulationResultPanel.tsx` | ≤300 | 推演结果展示(基线vs预测+Δ值+趋势) |

**原始文件**: `ScenarioManagementView.tsx`重写为Shell（≤200行），仅组合上述3子组件+状态管理

**验收**:
```bash
wc -l src/pages/ScenarioManagementView.tsx \
     src/pages/scenario/ScenarioList.tsx \
     src/pages/scenario/ScenarioEditor.tsx \
     src/pages/scenario/SimulationResultPanel.tsx
# 期望: ScenarioManagementView<200, 其余<300
```

---

## §验证门禁

```bash
# V1: 行数检查 (所有新/改文件)
wc -l src/pages/KnowledgeView.tsx \
     src/pages/ScenarioManagementView.tsx \
     src/pages/knowledge/tabs/*.tsx

# V2: KnowledgeView双版本确认
find src -name "KnowledgeView.tsx" | wc -l
# 期望: 1

# V3: aiworkbench/KnowledgeView.tsx删除确认
test ! -f src/pages/aiworkbench/KnowledgeView.tsx && echo "PASS: 已删除" || echo "FAIL: 仍存在"

# V4: TS编译
cd /home/guorongxiao/ECOS/ecos_frontend && npx tsc --noEmit 2>&1 | grep -c "error TS"
# 期望: 0新增

# V5: Vite构建
cd /home/guorongxiao/ECOS/ecos_frontend && npm run build 2>&1 | tail -5
```

## §工时

| Task | 工期 | 依赖 |
|:--|:--:|------|
| T1 KnowledgeView合并 | 1.5天 | — |
| T2 aiworkbench版拆分 | 2天 | T1 |
| T3 ScenarioManagement拆分 | 1天 | — |
