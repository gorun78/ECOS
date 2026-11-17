# PMO-45 前端 P0 收尾（window.confirm 14 处 + 6 文件去自建 fetch）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../.trae/rules/架构铁律.md) §4 前端铁律
> 来源: 肖国荣 | 日期: 2026-09-05 | 责任人: fullstack-implementer (FE)
> 铁律:
> 1. 危险操作（删除/回滚/批量）确认必须用 `common/ConfirmDialog`，禁止 `window.confirm`（§4 前端铁律 + 报告 §4.6）
> 2. 接口调统一封装（前端规范五），禁止 6 文件各写 `request` 函数
> 3. 公共组件冲突：`ConfirmDialog` 已存在，**不新建** `ConfirmDialog` 副本（扩展性铁律）

报告来源：`docs/9-checks/2026-09-05-前端界面操作实现情况检查报告.md` §3.6（14 处 window.confirm）+ §2.2（6 文件自建 fetch）+ §5.1 P0 项 5/6 中的 Toast 副本。

## §禁止清单

1. ❌ 不新建 `src/components/ConfirmDialog.tsx`（已存在于 `src/components/common/ConfirmDialog.tsx`）
2. ❌ 不改 ConfirmDialog 已有 props（行为不变；只新增 `title` 默认主题色引用）
3. ❌ 不修改 `request` 函数的 HTTP 语义（仅改为走统一封装）
4. ❌ 不允许"代码已改但没 grep 验证"

## §Task（原子）

| Task | 文件/路径 | 操作 | 验收 |
|:--|------|------|------|
| T1 | 10 个文件 14 处 `window.confirm` → `ConfirmDialog`（报告 §4.6） | 逐文件改（**单 Task 但 10 个文件**，文件路径全列）：① `src/pages/aiworkbench/LogicView.tsx:418`；② `src/pages/aiworkbench/AgentStudioView.tsx:241`（注销 AIP 智能体）；③ `src/pages/aiworkbench/ChatbotStudioView.tsx:121`；④ `src/pages/OntologyWorkbenchLayout.tsx:253`（删除+回滚）；⑤ `src/components/ontology/Sidebar.tsx:141`；⑥ `src/pages/ontology/OverviewView.tsx:46`；⑦ `src/components/TaskPanel.tsx:317`；⑧ `src/pages/datasource/DataSourceList.tsx:93`；⑨ `src/aiworkbench/agent-studio/AgentManager.tsx:123`；⑩ `src/pages/PolicyEngine.tsx:107`。统一模式：`const [confirmState, setConfirmState] = useState<null \| {title,message,doWhen()}>(null); ...setConfirmState({title:t('common.delete'),message,doWhen}); {confirmState && <ConfirmDialog open onConfirm={confirmState.doWhen} title={confirmState.title} message={confirmState.message}/>}` | `npm run lint` 0 错误；`grep -Rn "window.confirm" src/` = 0；浏览器删数据源 / 删 Agent / 回滚 操作的弹框是 ConfirmDialog 不是原生确认 |
| T2 | 5 个文件去自建 fetch 收敛到统一 request 封装（报告 §2.2 的 6 文件 - agentConfig 已在 PMO-41 T2 = 5 文件）| ① `src/services/glossary.ts` 自建 `request<T>` → 改为走 `src/api.ts` 的统一 request；② `src/services/dict.ts` 同上 + 修正 `code !== 0` 反逻辑（应为 `code === 0 ? data : reject`）；③ `src/services/glossaryClient.ts` 直接 fetch → 统一封装；④ `src/pages/data-workbench/api.ts` 自建 `get/post/put/del` 4 helper → 改为统一封装；⑤ `src/pages/sql-query-console/api.ts` 自建 `get/post/del` 3 helper → 统一封装。**保留**原有函数签名与返回值形状（向后兼容），只改底层调用 | `npm run lint` 0 错误；`grep -Rn "^\s*const request \|async function request \|function requestT\|fetch(" src/services/glossary.ts src/services/dict.ts src/services/glossaryClient.ts src/pages/data-workbench/api.ts src/pages/sql-query-console/api.ts` 命中 0（除了 request 封装本身）；浏览器开 DevTools Network，5 个服务的 API 调用走 `/api/...` 正常 200 |
| T3 | 30+ 文件删除重复 `<Toast>` 实现（**报告 §4.5/§3.6**） | PMO-43 T2 已挂 `<ToastProvider>`；本任务把 30+ 页自写 `<Toast/>` 实例 + 各种 `showToast` / `useToast` 自定义 hook 全删；改为消费 `useToast()` 自上下文（注意：**保留 useToast 函数 API**，只更新时间路径） | `npm run lint` 0 错误；`grep -Rn "const \[toasts\|const \[toast\|import.*Toast.*from.*\./Toast" src/pages/ src/components/` = 0（确认无剩余 Toast 副本）；任意弹 toast 的页面（TokenManager/UserList/Glossary）任一 toast 显示 3s 后自消失 |
| T4 | 6 处 console.log toast 残缺收口（PMO-43 T2 已删 5 处，本 Task 处理剩余）| 检查 `src/main.tsx` 仍有 `showToast={(type,msg) => console.log(...)}` 形式残留；`grep -rn "console\.log" src/` 仍命中 → 逐一替换 `useToast()` + 主题感知；若 >14 处残留，超出本 Task 范围（视为 P1 后续） | `npm run lint` 0 错误；`grep -rn "console\.log" src/` 仅允许测试文件 / 错误回退 console.error；浏览器各 toast 页均可显示 toast（非 console） |
| T5 | Grep 总闸 + 验收脚本 | `cd ecos_frontend && grep -RnE "window\.confirm|createContext\(.*Toast|const Toast: React\"" src/` 命中应 = 0；输出到 `docs/PMO/PMO-45-grep-audit.md`（仅 grep 报告，不改代码） | 文档存在；grep 命中 0；`npm run build` 通过 |

## §目标工时

5 小时（T1 1.5h + T2 1.2h + T3 1.5h + T4 0.3h + T5 0.5h）

## §依赖关系

- **前置**：PMO-43 T2（ToastProvider 挂载）必须 done（**硬依赖**）
- **输入**：检查报告 §3.6 / §2.2 / §4.5 / §4.6
- **后置**：PMO-46 QA 最终验收
- **Worker**: `fullstack-*`，**Verifier**: `reviewer-*`

## §分支

`feature/pmo-45-fe-p0-p1-收尾`

## §验收

- [ ] `npm run lint` 0 错误；`npm run build` 通过
- [ ] `grep -Rn "window.confirm" src/` = 0（14 处全替换）
- [ ] `grep -Rn "fetch(" src/services/glossary.ts src/services/dict.ts src/services/glossaryClient.ts src/pages/data-workbench/api.ts src/pages/sql-query-console/api.ts` = 0（5 文件全改）
- [ ] `grep -Rn "console\.log" src/` 命中仅测试文件
- [ ] 浏览器手动：威胁操作（删数据源 / 回滚 Agent / 批量删任务 / 删本体）均 ConfirmDialog 而非 confirm()
- [ ] 浏览器手动：任意 toast 不再 console，而是可见 Toast 卡 3s 自消失
- [ ] Reviewer 代码审查 deliverable_allowed=true
