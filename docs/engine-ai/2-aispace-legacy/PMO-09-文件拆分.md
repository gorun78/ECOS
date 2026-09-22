# PMO指令：Phase2-1-文件拆分 — 大文件拆分（4个>800行文件→15+组件）

> 来源: 完善计划 Phase 2-1 T2 | 工期: 1周 | 范围: 前端 aiworkbench/ | 依赖: 无 | 后继: PMO-10(国际化) / PMO-11(UX补齐)

---

## §背景

AI工作台4个核心文件超过800行，合计6454行，不可维护：

| 文件 | 行数 | 拆为 |
|------|:--:|------|
| KnowledgeView.tsx | 2074 | 6子Tab组件 |
| ChatbotStudioView.tsx | 1613 | 三栏+消息组件 |
| GuardrailsView.tsx | 1465 | 策略列表/编辑器/审计 |
| AgentStudioView.tsx | 1302 | Agent列表/详情/工具面板 |

**铁律**：功能等价拆分——不改业务逻辑，不增删功能，只重构文件结构。

---

## §禁止清单

1. ❌ 不改任何业务逻辑（函数签名/状态管理/数据流必须等价）
2. ❌ 不修改API调用或接口
3. ❌ 组件props传递保持原有类型，不引入新的props
4. ❌ 不拆分类型定义（保留在 `types/aiworkbench.ts`）
5. ❌ 每个子组件 ≤ 500行
6. ❌ 不引入新的npm依赖

---

## §Task

### T2-1: KnowledgeView拆分（2天）

**源文件**: `pages/aiworkbench/KnowledgeView.tsx` (2074行)  
**目标目录**: `components/aiworkbench/knowledge/`

**拆分为**：

| 组件 | 内容 | 预估行数 |
|------|------|:--:|
| `KnowledgeTabs.tsx` | 6子Tab导航+主layout | ~150 |
| `ClosedLoopTab.tsx` | 闭环设计Tab | ~350 |
| `MetadataSyncTab.tsx` | 元数据同步Tab | ~350 |
| `LineageTab.tsx` | 血缘解析Tab | ~350 |
| `OntologyAlignTab.tsx` | 本体对齐Tab | ~350 |
| `VectorIndexTab.tsx` | 向量索引Tab | ~350 |
| `RagSimTab.tsx` | RAG模拟Tab | ~350 |

**主文件保留**: `KnowledgeView.tsx` → 只保留 imports + 组合子组件 (~50行)

**验收**：
```bash
# 编译无新错误
cd ecos_frontend && npx tsc --noEmit 2>&1 | grep "KnowledgeView\|knowledge/" | grep -v "baseline"
# 期望: 无输出（无新TS错误）

# 浏览器: 知识工作台6个Tab可切换，每个Tab内容与原版一致
```

---

### T2-2: ChatbotStudioView拆分（1.5天）

**源文件**: `pages/aiworkbench/ChatbotStudioView.tsx` (1613行)  
**目标目录**: `components/aiworkbench/chatbot/`

**拆分为**：

| 组件 | 内容 | 预估行数 |
|------|------|:--:|
| `AgentSelector.tsx` | 左侧Agent列表 | ~200 |
| `ChatPanel.tsx` | 中间对话区+消息列表 | ~400 |
| `MessageBubble.tsx` | 单条消息组件 | ~150 |
| `ConfigPanel.tsx` | 右侧配置面板 | ~350 |
| `ChatInput.tsx` | 底部输入框+发送按钮 | ~150 |

**主文件保留**: `ChatbotStudioView.tsx` → ~80行（状态管理+组合）

**验收**：
```bash
npx tsc --noEmit 2>&1 | grep "ChatbotStudio\|chatbot/" | grep -v "baseline"
# 期望: 0 errors
```

---

### T2-3: GuardrailsView拆分（1天）

**源文件**: `pages/aiworkbench/GuardrailsView.tsx` (1465行)  
**目标目录**: `components/aiworkbench/guardrails/`

**拆分为**：

| 组件 | 内容 | 预估行数 |
|------|------|:--:|
| `GuardrailPolicyList.tsx` | 策略列表+CRUD操作 | ~400 |
| `GuardrailPolicyEditor.tsx` | 策略编辑表单 | ~350 |
| `GuardrailAuditLog.tsx` | 审计日志查看 | ~300 |

**主文件保留**: `GuardrailsView.tsx` → ~100行

**验收**：
```bash
npx tsc --noEmit 2>&1 | grep "Guardrail\|guardrails/" | grep -v "baseline"
# 期望: 0 errors
```

---

### T2-4: AgentStudioView拆分（1天）

**源文件**: `pages/aiworkbench/AgentStudioView.tsx` (1302行)  
**目标目录**: `components/aiworkbench/agent-studio/`

**拆分为**：

| 组件 | 内容 | 预估行数 |
|------|------|:--:|
| `AgentList.tsx` | Agent列表+搜索+筛选 | ~350 |
| `AgentDetail.tsx` | Agent详情+配置 | ~400 |
| `AgentToolPanel.tsx` | 工具绑定+函数选择 | ~300 |

**主文件保留**: `AgentStudioView.tsx` → ~100行

**验收**：
```bash
npx tsc --noEmit 2>&1 | grep "AgentStudio\|agent-studio/" | grep -v "baseline"
# 期望: 0 errors
```

---

### T2-5: 最终验证

```bash
# 1. 编译基线比较
cd ecos_frontend && npx tsc --noEmit 2>&1 | wc -l  # 应 ≤ 289（基线）

# 2. 无超800行文件
find src/components/aiworkbench/ -name "*.tsx" -exec wc -l {} \; | awk '$1>500{print "VIOLATION:",$0}'
# 期望: 无输出

# 3. 主文件精简
wc -l src/pages/aiworkbench/KnowledgeView.tsx     # ~50
wc -l src/pages/aiworkbench/ChatbotStudioView.tsx  # ~80
wc -l src/pages/aiworkbench/GuardrailsView.tsx    # ~100
wc -l src/pages/aiworkbench/AgentStudioView.tsx   # ~100

# 4. 组件数量
find src/components/aiworkbench/ -name "*.tsx" | wc -l  # ≥ 15

# 5. 浏览器全Tab功能等价验证（登录→每个Tab→确认无白屏/功能缺失）
```
