# PMO-40 数据确认 — Query Template 路径核实

> 来源: `docs/9-checks/2026-09-05-前端界面操作实现情况检查报告.md` §2.1 #24/#25 + §6 #2
> 责任人: backend-fullstack (ecos-be) | 日期: 2026-09-06 | 状态: 已确认

## §核实结论

经核实，后端 `QueryController`
（`ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/controller/QueryController.java`）
路径**已固定为复数** `/templates`（唯一路径，无单数兄弟路径）：

| 端点 | 方法 | 路径 |
|------|------|------|
| 模板列表 | GET | `/api/v1/engine/data/query/templates`（**复数，唯一**） |
| 查询模板 | GET | `/api/v1/engine/data/query/templates/{id}`（**复数**） |
| 删除模板 | DELETE | `/api/v1/engine/data/query/templates/{id}`（**复数**） |
| 保存模板 | POST | `/api/v1/engine/data/query/template`（单数，后端实际实现） |

说明：09-05 检查报告 §2.1 #24/#25 中 POST 使用 `/templates`（复数）的假设与后端
实际不符 — 后端保存端点实际为单数 `POST /template`。本指令**不新增**
Controller，**不改**任何既有路径（§0.2 API 只增不改）。

## §确认结论

1. **后端路径已存在且唯一**：`GET/DELETE /api/v1/engine/data/query/templates`
   及 `/templates/{id}`（复数）已存在且功能正常；本 Task（T3）仅做确认记录，
   不新增代码。
2. **前端必须把 `/template` 改为 `/templates`**：前端列表 / 删除类调用必须走
   复数 `/templates`；保存（新增/更新）走单数 `POST /template`。该结论已作为
   PMO-41 前端路径修正任务的依据落地（`sql-query-console/api.ts:177,192`
   列表/删除已用 `TEMPLATES_URL`（复数），`data-workbench/api.ts` 同步修正，
   保存统一用单数 `/template`）。
3. **任务下发确认**：本确认记录由 PMO-40 批次 3 下发、ecos-be 执行核实；
   前端修正结论已同步 PMO-41（前端路径修正）与后续 PMO-44 批次。

## §遗留项

- 无。前端修正已由 PMO-41 落地（本批完成时验证 `sql-query-console/api.ts`
  列表/删除均指向复数路径）。

## §PM 签名

PMO: ecos-pmo | 2026-09-06 | 已阅（本确认记录随 PMO-40 批次 3 交付，
由 backend-fullstack / ecos-be 出具，供 PMO-41/44 前端修正引用。）
