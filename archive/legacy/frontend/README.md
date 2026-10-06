# archive/legacy/frontend — 死前端文件归档

来源：《前端检视报告-2026-10-06》§E5.1 (P1 "Legacy 死文件") 一次性搬迁。
归档日期：2026-10-07 · 分支 `release/v2.1-alpha` · 未推送。

## 判据（每个文件均已反向 grep 验证）
1. `import <file-base>` 通过 `grep -rn "from ['\"]\./<base>['\"]" ecos_frontend/src --include="*.ts*"` 全仓 0 命中；
2. `main.tsx` 路由要么 `<Navigate to="…">` 另投，要么根本未挂；
3. 现役页面已由 `<domain>View.tsx` / `<domain>/index.tsx` 承接（详见下）。

## 归档清单（13 文件 + 1 目录，均 0 真引用）
| 归档路径 | 来源 | 死因 | 现役替代 |
|---|---|---|---|
| `ecos_frontend/src/pages/AgentStudio.tsx` (698) | main.tsx:171 | `agent_studio` → `/ai-workbench` | `pages/aiworkbench/AgentStudioView.tsx` |
| `ecos_frontend/src/pages/OntologyDesigner.tsx` (434) | main.tsx:148-155 | 注释自证死页；路由 `ontology_designer` 重定向 | 本体工作台现役文件 |
| `ecos_frontend/src/pages/OntologyDesigner/` (3 文件) | 同上 | 主壳死，子目录孤儿 | — |
| `ecos_frontend/src/pages/OntologyExplorer.tsx` (426) | 无路由 | 路由 `ontology` 重定向 | — |
| `ecos_frontend/src/pages/DomainDesignerView.tsx` (177) | main.tsx | 路由 `domain_designer` 重定向 | — |
| `ecos_frontend/src/pages/DomainListView.tsx` (572) | 无路由 | 0 引用 | — |
| `ecos_frontend/src/pages/PolicyEngine.tsx` (463) | 无路由 | 0 引用 | — |
| `ecos_frontend/src/pages/Marketplace.tsx` (345) | main.tsx:138 | 路由 `marketplace` 挂 `MarketplaceBrowser` | `pages/marketplace/MarketplaceBrowser.tsx` |
| `ecos_frontend/src/pages/AIPKnowledgeView.tsx` (236) | 无路由 | 0 引用 (RAG 4 步页整体孤儿) | — |
| `ecos_frontend/src/pages/TaskCenter.tsx` (508) | main.tsx | 路由 `engine-tasks` 挂 `TasksCenterRoute` | `pages/AsyncTaskCenterView.tsx` |
| `ecos_frontend/src/pages/AbacPolicyManager.tsx` (472) | 无路由 | 双入口中顶层孤儿 | `pages/security-center/detect/AbacPolicyManager.tsx` |
| `ecos_frontend/src/pages/SecurityConfigPanel.tsx` (533) | 无路由 | 0 引用 | — |
| `ecos_frontend/src/pages/DataMaskingDemo.tsx` (318) | 无路由 | 0 引用 | — |
| `ecos_frontend/src/pages/CryptoAuditPanel.tsx` (458) | 无路由 | 0 引用 | — |

## 撤销 / 恢复
`git log --diff-filter=A -- archive/legacy/frontend/ecos_frontend/src/pages/*` 可查这批搬迁 commit；`git show HEAD` 追 rename 记录。若日后需恢复，`git mv` 回原路径即可。

## 主要治理原则
- **不删除 blob** — 保 git history 一次性回滚成本最低；
- **不同步从 api.ts 删 BFF 函数**（该裁决留给后续批次，等 api.ts 按域拆分后一并清理更清晰）；
- 与后端 `archive/legacy/` 同骨架，遵守架构铁律「不加新 Maven 模块/新容器」精神，前端侧死代码不进活动代码树。
