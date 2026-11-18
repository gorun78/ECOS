# 02-flyway-migration-legacy/gateway — DDL 迁移脚本历史归档（V1~V349）

> 宪法依据: `.trae/rules/架构铁律.md` **v2.4 §3.1** | 状态单源: `docs/50-db_script/README.md` §阶段5
> 归档: 2026-11-18 数据库脚本整理计划 阶段5（用户裁决「分两步：先建新，后归档」+「现在做，按你的方案直接执行」）

## 这是什么

原活单源 `docs/50-db_script/02-flyway-migration/gateway/` 的**全部历史脚本**，整体 `git mv` 至此。

| 项 | 实测值 |
|:--|--:|
| `.sql` 文件数 | **298** |
| 版本范围 | V1 ~ **V349** |
| 改名纯度 | `git diff --cached -M --name-status` = **293 枚 R100**（内容逐字节零改动） |
| 另 5 枚 | V345~V349 归档时未入库（untracked），随目录物理搬迁，内容未改 |

## 🔴 只读存证，不是第二 DDL 源

- **禁写入新脚本**。新脚本一律落活单源 `docs/50-db_script/02-flyway-migration/gateway/`，自 **V350** 起。
- **禁把历史脚本搬回/复制回活单源**（会撞版本号）。
- 「禁分域另立目录」的铁律语义**不放宽**：本目录是归档面，不是平行的第二权威。
- 任何表结构争议：**只认活单源 + 活库实查**。本目录里的建表语句大量已被后续脚本改写，**按号重放不等于当前库结构**。从零建库请用 `docs/50-db_script/00_init/`。

## 为什么归档后仍须被门禁扫描

**门禁/lint 扫描面 = 活单源 ∪ 本目录。**

只扫活单源（现为空）会让这 298 枚脚本**全部脱离判定面** = 空集假绿，根因与 2026-10-11 卷09 H-7 完全相同（当时 lint 扫描根只覆盖 `ecos_backend`，迁移脚本从未被扫过，"0 FAIL" 是空集）。

本批已同批改 **51 个 Java 门禁文件** + `_win_tasks/db-migration-lint.ps1` 增本目录为扫描根。点名取单脚本的门禁按「活单源优先、归档兜底」解析。

**并集文件集 = 298，与搬迁前相同** ⇒ 各门禁计数钉值一律未动（`SCRIPT_COUNT_FLOOR=278` / `MAX_BASELINE_VERSION=349` / `NEXT_FREE_VERSION=350`）。

## 已知违例（存量，归档不改变其判定）

| 项 | 实测 | 归因 |
|:--|:--|:--|
| lint IR03「禁 DROP/ALTER」 | **42 处**（总命中 50 − V126 豁免 8） | `V315`×3 + `V323/V324/V325`×各1（设计资产历史版本表 DROP，铁律 §3.1 授权例外）+ `V349`×36（阶段3 已授权清退）。豁免按**文件名**匹配（`-match 'V126'`），与路径无关 |
| `dq_*` 多 schema 同名 | **11 族** | `V111__ecos_dq_governance_v1.sql` 建 `ecos_dq.dq_*` 与 `V342__ecos_data_dq_tables.sql` 建 `ecos_data.dq_*` 并存 = 真实读写分叉，待「逐族按 DDL 意图裁定」处置，**未扩门禁豁免名录换绿** |
| MC03 `::` cast / MC01 `gen_random_uuid()` | 见 `docs/50-db_script/README.md` §登记在案的偏差 | 活库既有形态，非本批新增 |

## 相关

- 活单源（V350+）：`docs/50-db_script/02-flyway-migration/gateway/`
- 状态单源与实跑复验证据：`docs/50-db_script/README.md` §阶段5
- 其余历史 DDL 子目录（01-ddl-多方言 / 03-seed / 04-engine / 05-service 等）：`docs/90-归档/50-db_script/`