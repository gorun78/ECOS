# PMO指令：Phase2-1-国际化 — i18n迁移（~12K硬编码中文→语言包）

> 来源: 完善计划 Phase 2-1 T1 | 工期: 1周 | 范围: 前端 aiworkbench/ + AgentStudio/ + AgentTestConsole | 依赖: PMO-09文件拆分 | 并行: 可与PMO-11(UX补齐)并行

---

## §背景

AI工作台15个文件审计结果：67%文件未使用i18n，73%文件含硬编码中文，总量~12,096字符。仅2个小文件（CreateAgentModal、MetricsPanel）有正确的i18n设置。两个文件import了useLanguage但从未调用t()（死代码）。

---

## §禁止清单

1. ❌ 不修改英文key对应的中文含义（只迁移，不改文案）
2. ❌ 不修改组件结构（PMO-09已完成拆分，此时文件结构已确定）
3. ❌ 语言包key命名规范：`aiworkbench.{模块名}.{字段名}`，如 `aiworkbench.chatbot.send`
4. ❌ 不遗漏任何硬编码中文（验收标准：grep中文=0）

---

## §Task

### T1-1: 创建i18n语言包（0.5天）

**文件**：
- `src/i18n/locales/aiworkbench/zh-CN.json` — 中文语言包
- `src/i18n/locales/aiworkbench/en.json` — 英文语言包

**命名空间**: `aiworkbench`

**key命名规范示例**：
```json
{
  "chatbot": {
    "title": "Chatbot Studio",
    "send": "发送",
    "agentList": "Agent列表",
    "config": "配置",
    "emptyState": "选择一个Agent开始对话",
    "loading": "加载中..."
  },
  "agent": {
    "title": "Agent工作台",
    "create": "创建Agent",
    "delete": "删除",
    "confirmDelete": "确认删除该Agent？",
    "market": "Agent市场",
    "builtin": "内置模板"
  },
  "dashboard": { "title": "总览仪表盘", "metrics": "指标" },
  "knowledge": { "title": "知识工作台" },
  "guardrails": { "title": "安全护栏", "policy": "安全策略" },
  "logic": { "title": "逻辑编排" },
  "model": { "title": "模型目录" },
  "common": { "save": "保存", "cancel": "取消", "search": "搜索", "delete": "删除", "edit": "编辑", "create": "新建" }
}
```

**英文key必须覆盖所有中文key**（一一对应，不允许缺key）

---

### T1-2: 迁移文件（4天，按优先级排列）

**每个文件的迁移步骤**：
1. 在文件顶部添加 `const { t } = useLanguage();`
2. 将文件中所有硬编码中文替换为 `t("aiworkbench.xxx.yyy")`
3. 将新增的key同步添加到 zh-CN.json 和 en.json
4. 确认 `npx tsc --noEmit` 无新增错误

**迁移顺序**：

| 顺序 | 文件 | 中文字符 | 工期 |
|:--:|------|:--:|:--:|
| 1 | KnowledgeView.tsx（已拆分组件） | 3271 | 1天 |
| 2 | ChatbotStudioView.tsx（已拆分组件） | 3131 | 1天 |
| 3 | AgentStudioView.tsx（已拆分组件） | 2012 | 0.5天 |
| 4 | GuardrailsView.tsx（已拆分组件） | 1466 | 0.5天 |
| 5 | mockData.ts | 1105 | 0.5天 |
| 6 | LogicView.tsx + DashboardView.tsx + ModelCatalogView.tsx 等5小文件 | ~1200 | 0.5天 |

---

### T1-3: 清理死代码（0.5天）

**文件**：
- `CognitiveOperatingSystem.tsx`：删除未使用的 `useLanguage` import
- `AgentTestConsole.tsx`：删除未使用的 `useLanguage` import

**验收**：
```bash
grep -n "useLanguage" src/pages/aiworkbench/CognitiveOperatingSystem.tsx
grep -n "t(" src/pages/aiworkbench/CognitiveOperatingSystem.tsx
# 期望: 要么useLanguage+t()同时存在，要么都不存在
```

---

## §最终验收

```bash
# 1. 零硬编码中文
cd ecos_frontend/src
grep -rP '[\x{4e00}-\x{9fff}]' pages/aiworkbench/ pages/AgentStudio/ pages/AgentTestConsole.tsx | wc -l
# 期望: 0

# 2. 编译无新增错误
npx tsc --noEmit 2>&1 | wc -l  # ≤ 289

# 3. 语言包完整性
python3 -c "
import json
zh=json.load(open('i18n/locales/aiworkbench/zh-CN.json'))
en=json.load(open('i18n/locales/aiworkbench/en.json'))
# 递归比较key
def keys(obj, prefix=''):
    ks=[]
    if isinstance(obj,dict):
        for k,v in obj.items():
            p=f'{prefix}.{k}' if prefix else k
            ks.append(p)
            ks.extend(keys(v,p))
    return ks
zh_keys=set(keys(zh))
en_keys=set(keys(en))
missing_en=zh_keys-en_keys
missing_zh=en_keys-zh_keys
if missing_en: print('MISSING en:', missing_en)
if missing_zh: print('MISSING zh:', missing_zh)
if not missing_en and not missing_zh: print('语言包完整 ✅')
"

# 4. 浏览器验证: 切换语言→所有Tab→确认文案正确切换
```
