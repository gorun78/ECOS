# PMO指令：Phase2-补工 — ChatbotStudioView 拆分+国际化

> 来源: PMO-09/10 验证 | 工期: 1周 | 范围: 前端 aiworkbench/ | 优先级: 高（阻塞企业级代码规范）

---

## §背景

ChatbotStudioView.tsx 是Phase 2唯一未完成的文件：
- **1586行**（目标<200行），是第二大文件
- **2956个硬编码中文字符**，占整个aiworkbench残留的95%
- 已拆分出 `ConfigPanel.tsx`（703行）和侧边栏等子组件，但主视图文件未重构

## §禁止清单

1. 不改后端OAG Pipeline API路径
2. 不删除ChatbotStudioView现有功能（对话/SSE流/消息历史）
3. 不新增npm依赖
4. 拆分后JSX结构必须一致，确保UI零退化

## §Task

### T1: ChatbotStudioView 拆分（3天）

**目标**: 1586行 → <200行主视图 + 3个独立子组件

| 子任务 | 内容 | 工期 |
|--------|------|:--:|
| 1-1 | 拆出 `ChatHistory.tsx` — 消息列表+滚动加载+时间戳 | 1天 |
| 1-2 | 拆出 `ChatInput.tsx` — 输入框+发送按钮+附件+快捷键 | 0.5天 |
| 1-3 | 拆出 `ChatHeader.tsx` — Agent选择器+会话标题+操作按钮 | 0.5天 |
| 1-4 | 重写 `ChatbotStudioView.tsx` — 仅组合子组件+状态管理+OAG API调用 | 1天 |

### T2: 国际化迁移（2天）

| 子任务 | 内容 | 工期 |
|--------|------|:--:|
| 2-1 | 在 `zh-CN.json`/`en.json` 新增 `chatbot.*` namespace（~60 keys） | 0.5天 |
| 2-2 | 替换ChatbotStudioView + 3个子组件中2956个硬编码中文为 `t("chatbot.xxx")` | 1天 |
| 2-3 | TS编译零错误、浏览器验证 | 0.5天 |

## §curl验收

```bash
# 编译
cd /home/guorongxiao/ECOS/ecos_frontend && npx tsc --noEmit 2>&1 | grep -c "ChatbotStudio\|ChatHistory\|ChatInput\|ChatHeader" 
# 期望: 0

# 行数
wc -l src/pages/aiworkbench/ChatbotStudioView.tsx
# 期望: <200

# 硬编码中文
grep -rP '[\x{4e00}-\x{9fff}]' src/pages/aiworkbench/ChatbotStudioView.tsx src/components/aiworkbench/chatbot/ --include="*.tsx" | wc -l
# 期望: 0
```

## §交付检查清单

- [ ] 4个文件全部≤500行
- [ ] ChatbotStudioView ≤ 200行（纯组合）
- [ ] 0硬编码中文字符
- [ ] TS编译零错误（ChatbotStudio相关）
- [ ] 浏览器操作：对话/SSE流/消息历史/Agent切换 均正常
- [ ] 中英文切换后UI文案变化正确

## §文件清单

| 文件 | 操作 | 目标行数 |
|------|------|:--:|
| `ChatbotStudioView.tsx` | 重写 | <200 |
| `chatbot/ChatHistory.tsx` | 新建 | ~400 |
| `chatbot/ChatInput.tsx` | 新建 | ~250 |
| `chatbot/ChatHeader.tsx` | 新建 | ~200 |
| `chatbot/ConfigPanel.tsx` | 已有 | 703→补i18n |
