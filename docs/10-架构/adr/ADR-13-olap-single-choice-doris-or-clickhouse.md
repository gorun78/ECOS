# ADR-13 — 列存引擎单选择：Doris ∨ ClickHouse（`dw.olap.engine`）

> 来源: 用户指令（2026-09-28 "doris 还有一个平替方案是 clickhouse"）+ 湖规 v2.0 §七A + 《数据库访问规范》v1.2 MC04 | 日期: 2026-09-28 | 责任人: AI Agent
> 版本: v1.0 | 裁决: **Accepted（能力缺口未闭合，见 §3）**
> 上游: 铁律 v2.0 §2.5-1/§3.1/§3.5 + 湖规 v2.0 §七A + MC04/MC06 + ADR-3
> 状态: 生效

---

## 1. 背景

ultimate 档原以 Doris 承载业务域 `CURATED`/`APPLICATION` 物化。用户指令要求承认 ClickHouse 为平替。实查现状：

- CH 在工程中**只作为外部数据源适配存在**（`ClickHouseAdapter`），**没有** DW/应用层物化 sink；
- Doris 侧有物化路径但受 `@Profile` 守卫现状待核（工程清单 #7）；
- `dw.lake.storage_format` 默认 `parquet`，而 `SINK_MINIO` **无 parquet 写入器**（显式要求非 csv 即拒绝，未显式则 csv 兼容 + 告警）。

## 2. 决策

1. **单选择**：同一部署只允许一个列存引擎，由 `dw.olap.engine=doris|clickhouse` 决定；**禁双 OLAP 并存、禁双写**（MC04）。
2. **只承载业务域**：列存只做 `CURATED` / `APPLICATION` 物化（大表阈值 = 单表 > 100 万行）；**控制域禁入**（ST08 + MC04），控制域恒为 PG。
3. **统一经 `runtime-access` 收口**（IR06）：Doris 与 CH 的 Driver/写入/物化都在 runtime-access 内实现同一接口（`OlapSink`），引擎不得各自封装连接与方言。
4. **方言差异以适配器吸收，不外溢到 SQL**：Doris=MySQL 协议、CH=私有方言，两者都**不在**多库兼容目标（MySQL/Oracle/MSSQL/达梦/金仓）之内，因此列存 SQL 允许方言化，但**必须留在 runtime-access 适配层**；控制域 Mapper 仍受 MC01~MC03 约束。
5. **切换验收**：改 `dw.olap.engine` 后，DW 物化 + 应用层聚合 + 前端读取三处不需改代码即跑通（工程清单 #12 验收）。
6. **载体缺口的时序约束**：在 `SINK_MINIO` 无 parquet writer（工程清单 #7/湖规缺口）之前，RAW 层仍 csv；列存选型不影响该结论，但应用层导出若声明 parquet 必须显式失败而非静默 csv（现状已如此）。

## 3. 现状缺口（设计必须写"未实现"）

| 缺口 | 影响 | 归属 |
|:--|:--|:--:|
| CH 无内部物化 sink | MC04 的"二选一"目前**只有 Doris 一侧真实可用**，选 CH 即无物化路径 | 00 册（runtime-access）+ 02 册 |
| Doris 物化 `@Profile` 守卫未核 | standard/enterprise 档可能误触发列存写 | 02 册（工程清单 #7） |
| parquet 写入器缺失 | 近源层与 `storage_format` 配置不一致 | 02 册（工程清单，P3） |

## 4. 影响

| 维度 | 影响 |
|:--|:--|
| 架构 | ✅ 列存从"Doris 专属"变为可替换能力，避免甲方环境只有 CH 时无法交付 |
| 三档矩阵 | 湖规 v2.0 §七A 的 ultimate 列已改为"Doris ∨ ClickHouse"，本 ADR 是其决策依据 |
| 基线 | ⚠️ 不新增 Docker 容器（容器基线已定）；CH 若需容器属基线变更，须用户批准 |

## 5. 关联

湖规 v2.0 §七A / §七 缺口表、《数据库访问规范》v1.2 MC04、铁律 v2.0 §2.5-1、ADR-11（同为业务域专用承载）、工程清单 #7/#12、详细设计 00/02 册。

## 6. 变更日志

| 日期 | 版本 | 变更 | 责任人 |
|:--|:--|:--|:--|
| 2026-09-28 | v1.0 | 首次成文：单选择 + runtime-access 收口 + 记录 CH 侧能力缺口 | AI Agent |
