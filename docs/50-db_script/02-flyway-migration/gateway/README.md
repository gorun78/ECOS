# 02-flyway-migration/gateway — DDL 迁移脚本「活单源」

> 宪法依据: `.trae/rules/架构铁律.md` **v2.4 §3.1** | 状态单源: `../../README.md` §阶段5
> 建立: 2026-11-18 数据库脚本整理计划 阶段5（用户裁决「分两步：先建新，后归档」）

## 这个目录为什么是空的

**空是预期状态，不是脚本丢失。**

V1~V349 共 **298 枚** `V*.sql` 已整体 `git mv` 归档至：

```
docs/90-归档/50-db_script/02-flyway-migration-legacy/gateway/
```

本目录自 **V350** 起只承载**新脚本**。下一枚 = **V350**。

## 写入规则

| 项 | 规则 |
|:--|:--|
| 落点 | 新迁移脚本**只写这里**，禁散落到 engine/services/runtime/workspace 任何 `src/main/resources/db/migration/` |
| 命名 | `V{n}__{描述}.sql`，n 单调递增，**自 V350 起** |
| 禁回填 | **不得**把归档里的历史脚本复制/搬回本目录（会撞版本号，`MigrationVersionUniquenessTest` 判红） |
| 钉值同批改 | 新增脚本须同批更新 gateway `MigrationVersionUniquenessTest` 的 `MAX_BASELINE_VERSION` / `NEXT_FREE_VERSION` 与 `docs/40-实现/DDL迁移脚本落地登记-2026-09-30.md` |
| 实跑 | Flyway 禁用（`spring.flyway.enabled: false`），脚本落文件不实跑；对 dev 库实跑属授权动作，须先 `BEGIN`/`ROLLBACK` 事务空跑 |
| 红线 | `数据库访问规范.md` v1.6（IR/DR/EN/ST/MC 30 条）；种子幂等用 `WHERE NOT EXISTS` / DELETE+INSERT，**禁** `ON CONFLICT`（MC03） |

## 🔴 门禁扫描面义务

**任何按目录枚举迁移脚本的门禁/lint，扫描面必须是「活单源 ∪ 归档」。**

- 只扫本目录 ⇒ 298 枚历史脚本脱离判定面 = **空集假绿**（根因同 2026-10-11 卷09 H-7）。
- 只扫归档 ⇒ 未来 V350+ 新脚本脱离判定面。
- 点名取单个脚本者 ⇒ 按「**活单源优先、归档兜底**」解析（参考实现：gateway `DdlShapeScanner.scriptPath()`）。

本目录同时是 `SourceNotIgnoredGateTest` 探针写盘门（`V99999__probe_target.sql`）与多个门禁 `repoRoot()` 锚点的判定依据，**不得删除本目录**。

## 相关

- 从零建库（不是增量重放）：`../../00_init/`（00 → 01~08 → 09 → 10）
- 演示数据四件套：`../../04-demo-data/`
- 清理前基线/回滚依据：`../../03-baseline/`