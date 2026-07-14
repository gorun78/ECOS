# ECOS Maven 模块地图

> 根: `databridge-v2` v1.0.0-SNAPSHOT

## 模块清单（40 个）

| 模块 | 路径 | 打包 | Java文件 | SQL文件 | 关键依赖 |
|------|------|:----:|:--------:|:-------:|---------|
| `aimod-impl` | `databridge-aimod/aimod-impl` | jar | 12 | 0 | com.chinacreator.gzcm.common:databridge-common-api |
| `databridge-aimod` | `databridge-aimod` | pom | 12 | 0 |  |
| `buszhi-impl` | `databridge-buszhi/buszhi-impl` | jar | 35 | 0 | com.chinacreator.gzcm.common:databridge-common-api |
| `databridge-buszhi` | `databridge-buszhi` | pom | 35 | 0 |  |
| `cognitive-api` | `databridge-cognitive/cognitive-api` | jar | 27 | 0 | com.chinacreator.gzcm.common:databridge-common-api |
| `cognitive-impl` | `databridge-cognitive/cognitive-impl` | jar | 4 | 0 |  |
| `databridge-cognitive` | `databridge-cognitive` | pom | 31 | 0 |  |
| `databridge-common-api` | `databridge-common/databridge-common-api` | jar | 13 | 0 |  |
| `databridge-common` | `databridge-common` | pom | 13 | 0 |  |
| `datanet-api` | `databridge-datanet/datanet-api` | jar | 14 | 0 | com.chinacreator.gzcm.common:databridge-common-api |
| `datanet-boot` | `databridge-datanet/datanet-boot` | jar | 1 | 2 |  |
| `datanet-impl` | `databridge-datanet/datanet-impl` | jar | 21 | 0 |  |
| `databridge-datanet` | `databridge-datanet` | pom | 36 | 2 |  |
| `dccheng-api` | `databridge-dccheng/dccheng-api` | jar | 1 | 0 | com.chinacreator.gzcm.common:databridge-common-api |
| `dccheng-impl` | `databridge-dccheng/dccheng-impl` | jar | 30 | 0 |  |
| `databridge-dccheng` | `databridge-dccheng` | pom | 31 | 0 |  |
| `databridge-gateway` | `databridge-gateway` | jar | 31 | 67 |  |
| `market-impl` | `databridge-market/market-impl` | jar | 6 | 0 | com.chinacreator.gzcm.common:databridge-common-api |
| `databridge-market` | `databridge-market` | pom | 6 | 0 |  |
| `databridge-portal` | `databridge-portal` | pom | 3 | 0 |  |
| `portal-impl` | `databridge-portal/portal-impl` | jar | 3 | 0 | com.chinacreator.gzcm.common:databridge-common-api |
| `hermes-engine` | `databridge-runtime/hermes-engine` | jar | 26 | 0 | com.chinacreator.gzcm:databridge-common-api |
| `databridge-runtime` | `databridge-runtime` | pom | 590 | 0 |  |
| `runtime-core` | `databridge-runtime/runtime-core` | jar | 425 | 0 | com.chinacreator.gzcm:databridge-common-api |
| `runtime-crypto` | `databridge-runtime/runtime-crypto` | jar | 19 | 0 |  |
| `runtime-monitor` | `databridge-runtime/runtime-monitor` | jar | 57 | 0 |  |
| `runtime-security` | `databridge-runtime/runtime-security` | jar | 37 | 0 |  |
| `runtime-task` | `databridge-runtime/runtime-task` | jar | 26 | 0 |  |
| `databridge-sysman` | `databridge-sysman` | pom | 214 | 31 |  |
| `sysman-api` | `databridge-sysman/sysman-api` | jar | 118 | 0 | com.chinacreator.gzcm.common:databridge-common-api |
| `sysman-boot` | `databridge-sysman/sysman-boot` | jar | 5 | 31 |  |
| `sysman-impl` | `databridge-sysman/sysman-impl` | jar | 91 | 0 |  |
| `databridge-workspace` | `databridge-workspace` | pom | 16 | 0 |  |
| `workspace-impl` | `databridge-workspace/workspace-impl` | jar | 16 | 0 | com.chinacreator.gzcm.common:databridge-common-api |
| `databridge-worldmodel` | `databridge-worldmodel` | pom | 13 | 0 |  |
| `worldmodel-impl` | `databridge-worldmodel/worldmodel-impl` | jar | 13 | 0 | com.chinacreator.gzcm.common:databridge-common-api |
| `kanban-api` | `ecos-kanban/kanban-api` | jar | 0 | 0 | com.chinacreator.gzcm:databridge-common-api |
| `kanban-boot` | `ecos-kanban/kanban-boot` | jar | 0 | 0 |  |
| `kanban-impl` | `ecos-kanban/kanban-impl` | jar | 0 | 0 |  |
| `ecos-kanban` | `ecos-kanban` | pom | 0 | 0 |  |

## 依赖关系图

```
  [aimod-impl] ──→ [databridge-common-api]
  [databridge-aimod] (无内部依赖)
  [buszhi-impl] ──→ [databridge-common-api]
  [databridge-buszhi] (无内部依赖)
  [cognitive-api] ──→ [databridge-common-api]
  [cognitive-impl] (无内部依赖)
  [databridge-cognitive] (无内部依赖)
  [databridge-common-api] (无内部依赖)
  [databridge-common] (无内部依赖)
  [datanet-api] ──→ [databridge-common-api]
  [datanet-boot] (无内部依赖)
  [datanet-impl] (无内部依赖)
  [databridge-datanet] (无内部依赖)
  [dccheng-api] ──→ [databridge-common-api]
  [dccheng-impl] (无内部依赖)
  [databridge-dccheng] (无内部依赖)
  [databridge-gateway] (无内部依赖)
  [market-impl] ──→ [databridge-common-api]
  [databridge-market] (无内部依赖)
  [databridge-portal] (无内部依赖)
  [portal-impl] ──→ [databridge-common-api]
  [hermes-engine] ──→ [databridge-common-api]
  [databridge-runtime] (无内部依赖)
  [runtime-core] ──→ [databridge-common-api]
  [runtime-crypto] (无内部依赖)
  [runtime-monitor] (无内部依赖)
  [runtime-security] (无内部依赖)
  [runtime-task] (无内部依赖)
  [databridge-sysman] (无内部依赖)
  [sysman-api] ──→ [databridge-common-api]
  [sysman-boot] (无内部依赖)
  [sysman-impl] (无内部依赖)
  [databridge-workspace] (无内部依赖)
  [workspace-impl] ──→ [databridge-common-api]
  [databridge-worldmodel] (无内部依赖)
  [worldmodel-impl] ──→ [databridge-common-api]
  [kanban-api] ──→ [databridge-common-api]
  [kanban-boot] (无内部依赖)
  [kanban-impl] (无内部依赖)
  [ecos-kanban] (无内部依赖)
```