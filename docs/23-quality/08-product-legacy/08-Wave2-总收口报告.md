# Wave-2 总收口报告 (按 docs/ 目录顺序)

> 版本: 1.0 | 2026-09-02 | 基于 Wave-1 收口交付的 Wave-2 三路并行

## 1. 三波并列交付

| 波 | 目录 | 主线 PMO | 5 件套 | 状态 |
|:--:|:--|:--|:--:|:--:|
| **Wave-2A** | [2-aispace] | aispace 拆 + i18n 收口 | 5 | ✅ |
| **Wave-2B** | [3-data] | ge (D→I) 收口 | 5 | ✅ |
| **Wave-2C** | [3-data/knowledge] | cheng (K→C) 收口 | 5 | ✅ |

## 2. 数据 pane

### Wave-2A [2-aispace]
- AIPCopilotDrawer 1044→336 行 (拆 4 子组件 + AgentScenarioData + AgentQuickActions + CopilotInputBar + CopilotMessageList)
- DashboardView 801→742 行 (RadarChart 抽 154 行)
- Sidebar 308, Topbar 461 (387 双字段 → t())
- 新增 11 文件 + 5 修改 (LanguageContext 注册 copilot/dashboard namespace)
- **lint 0 新增错, 0 显示中文残留**
- 5 路由实跳待 chrome 验证 (Wave-1C 21/21)

### Wave-2B [3-data] ge (D→I)
- 新增 `engine/data-engine/.../transform/controller/TransformController.java` (217 行)
- 2 端点: GET /api/v1/engine/data/transform/meta + POST /api/v1/engine/data/transform/execute
- 5 单测 PASS, 三滤波器零改动
- 08-验收记录 + 7-integration/03-data/{01,05} 落盘

### Wave-2C [3-data/knowledge] cheng (K→C)
- **4 Key files**:
  1. `cognitive-engine/model/RuleRef.java` (04 文 §三 #1 Contract 落)
  2. `kb-engine/kb-engine-impl/.../MinerUHttpParser.java` (05 文 §三 MinerU 通道)
  3. `kb-engine/kb-engine-impl/.../KnowledgeExtractionService.java` (PMO-24 审批闭环 + 实体 Neo4j + 实体链接)
  4. `cognitive-engine/.../ReasoningPathBuilder.java` (04 §4.1 因果链→推理路径映射)
- **10/10 单测 PASS**
- 04/05 设计文档 §2 映射 (ReasoningPath / ReasoningStep / RuleRef / Justification / PrecedentRef)

## 3. 后端编译验证

```
mvn install -P enterprise -DskipTests → BUILD SUCCESS (5 模块)
mvn test -pl engine/data-engine/data-engine-impl,engine/cognitive-engine/cognitive-engine-impl,engine/kb-engine/kb-engine-impl → 15/15 PASS
```

## 4. 4 实际新文件 + 5 修改

```
引擎层 (5 new + 1 mod):
- cognitive-engine: +RuleRef, +ReasoningPathBuilder
- kb-engine:        +MinerUHttpParser, +KnowledgeExtractionService (重写)
- data-engine:      +TransformController, +TransformControllerTest

前端 (11 new + 5 mod):
[Wave-2A 11+5 见 02-1-Wave2A 报告]

文档 (4 新 + 1 改):
- docs/2-aispace/02-1-Wave2A-拆分+收口报告.md
- docs/3-data/02-1-Wave2B-ge-D到I-收口.md
- docs/3-data/03-1-Wave2C-cheng-K到C-收口.md
- docs/7-integration/03-data/01-需求.md (前排)
- docs/7-integration/03-data/05-验收记录.md
- docs/7-integration/01-sysman/05-RlsCrossTenantTest.mjs
- docs/7-integration/01-sysman/05-RLS验收报告.md
```

## 5. 下次 Wave-3 启动预测 [4-onto + 5-cognitive + 6-techdebt]

按 [07-按目录顺序执行规划-收敛版.md §二 Wave 划分](07-按目录顺序执行规划-收敛版.md) 主线:

| 拨 | 目录 | 主线 PMO | 文档 | 估算 |
|:--:|:--|:--|:--|:--|
| Wave-3.1 | **4-onto** | PMO-28~31 (本体建模 + 实体关系 + 版本 + 多租户) | PMO 5 份 | 7 人天 |
| Wave-3.2 | **5-cognitive** | PMO-32~36 + C3 (14.7 人天 重写) | Wave-1B 3 份 + C3 | 14.7 人天 |
| Wave-3.3 | **6-techdebt** | B1-E3 收尾 (伴随) | 9 PMO | 8 人天 |
| Wave-3.4 | **7-integration** | 7 域联调 (sysman/aispace/data/onto/cognitive/aispace/doc/techdebt) | 7 屏幕 | 10 人天 |
| Wave-3.5 | **08 总收口** | 72h Soak + v2.0 release | M5-M6 | 5 人天 |

## 6. 行 (即刻) 决策

| 选项 | 行动 |
|:--:|:----|
| **X (推荐)** | **暂停在此等你人工 review Wave-2 三路 + 4 份收口报告**。**如 OK 选 A 继续 Wave-3** |
| A | 主线程自动启 Wave-3 三拨并发 |
| B | 你手动 push / PR 提交 Wave-2A/B/C, 下次会话续 Wave-3 |
