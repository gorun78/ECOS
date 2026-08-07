# PMO指令：Phase2-3-OAG面板 — OAG对话面板完善

> 来源: 完善计划 Phase 2-3 T7 | 工期: 1周 | 范围: 前端 aiworkbench/chatbot/ | 依赖: PMO-14

---

## §背景

OAG Pipeline引擎已完成(PMO-14)，前端需要配套的完整对话体验：多线程、导出、本体上下文面板、Agent切换。

---

## §Task

### T7-1: 多线程对话切换（1天）

**文件**: 修改 `ChatPanel.tsx`

**功能**:
- 左侧线程列表（新建/切换/删除/重命名）
- 每个线程独立保存对话历史（调 `/api/v1/agent-loop/sessions`）
- 线程标题自动生成（取第一条消息前20字）

### T7-2: 对话导出（1天）

**文件**: 修改 `ChatPanel.tsx`

**功能**:
- Markdown导出：对话历史→纯文本Markdown（含用户/AI消息+时间戳+来源标注）
- 导出按钮在对话区顶部工具栏
- 使用 `Blob` + `URL.createObjectURL` 触发下载

### T7-3: 本体上下文面板（2天）

**文件**: 新建 `components/aiworkbench/chatbot/OntologyContextPanel.tsx`

**功能**:
- 对话过程中实时展示当前涉及的本体对象（从SSE step3事件获取）
- 对象以卡片形式展示：ObjectType名称/属性列表/关联关系图谱缩略图
- 使用Neo4j驱动的force-graph或自绘简单SVG
- 点击对象卡片→跳转到本体工作台对应对象详情

### T7-4: Agent切换（1天）

**文件**: 修改 `AgentSelector.tsx`

**功能**:
- 对话中可切换Agent（列表中选择新Agent → 当前线程保持，后续消息使用新Agent）
- 切换时显示"已切换到 XXX Agent"系统消息

**验收**:
- 多线程：创建3个线程 → 各发5条消息 → 切换线程 → 消息独立
- 导出：点导出→下载.md文件→内容完整
- 本体面板：对话中涉及采购订单 → 右侧显示PurchaseOrder对象卡片+关联关系
- Agent切换：data-agent→cognitive-agent → 系统消息提示切换
