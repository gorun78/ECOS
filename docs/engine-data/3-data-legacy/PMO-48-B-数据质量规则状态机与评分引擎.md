# PMO-48-B: 数据质量规则状态机 + 6 维度评分引擎（Phase 2）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../.trae/rules/架构铁律.md)
> 来源: 肖国荣
> 日期: 2026-09-10
> 上游方案: PMO-48 §2（规则生命周期）+ §4（评分）
> 子指令: PMO-48-A（Phase 1 已落地，留下 405/410 写拒绝桩 + Phase 2 待开端点）
> 状态: **执行中**
> 铁律:
>   1. 新表/新列走 Flyway V112，schema 只加不删
>   2. 规则状态机复用 kb-engine ComplianceRule 5 态模式（方案 §2.4）
>   3. 评分引擎 SPI 设计必须可扩展（方案 §4.4，未来加 Checklist/Defect 评估器不破坏主流程）
>   4. 写操作必走 auditLogger（铁律 2.4 #5）
>   5. 三滤波器已就绪（T2 已加），新端点 `/api/v1/dq/scores` 不在 `/api/v1/dq/**` 通配覆盖范围 → 需**在 T2 4 文件追加 `/api/v1/dq/scores/**` + `/api/dq/scores/**` 双路径**
>   6. 旧 `DqGovernanceController` 的 405 桩替换为真状态机方法（同文件改）

---

## §背景

Phase 1 已交付：
- DB 5 表（`ecos_dq.dq_rule`/`dq_rule_version`/`dq_rule_check`/`dq_alert_record`/`dq_work_order`）
- 三滤波器（`/api/v1/dq/**` 已通）
- 新端点 3 读 + 3 写拒绝（405 + 审计 REJECTED 桩）
- 6 维度注册表（硬编码映射 DIMENSIONS map）
- 前端 `/dq_dashboard` 3 Tab

Phase 2 目标：**让规则真正能维护（生命周期）+ 让 6 维能算分**。

### 与 KB 决策 #3/4 的衔接
- **决策 #3 Thymeleaf + SPA 双出口**：Phase 2 不出报告，只把 **6 维度评分数据**装到 SPA 的 radar。Phase 4 加报告时复用本波 `DqScoreEngine` SPI
- **决策 #4 RCA P0/P1 自动**：Phase 2 完保留 `dq_work_order.rca_result` 字段（Phase 1 表已留），Phase 3 T12 接 cognitive 触发

---

## §禁止清单（铁律 5.1 + 本指令特有）

1. 不跨 Phase 预建 Phase 3 文件（监控/告警/工单 Controller 禁止提前建）
2. 不改 Phase 1 已建 5 张表的字段（只加列）
3. 不改 T2 三滤波器既有条目（只追加 `/api/v1/dq/scores/**` 双路径）
4. 不 `throws Exception`、抛 `DataBridgeException` 子类
5. Entity 用 `@Getter+@Setter+@NoArgsConstructor`，DTO 用 `@Data`
6. 控制流 if/for/while 必须花括号
7. 不 `new JdbcTemplate / RestTemplate`（构造器注入或 `ObjectProvider`）
8. 前端不硬编码颜色 / 不硬编码中文 / 大文件 <800 行 / 图标仅 lucide-react
9. 三滤波器新路径必须自然语言验证（`curl /api/v1/dq/scores/system` 非 403/404）
10. 不删 Phase 1 已写的 405 桩，**替换为真方法**（保留方法签名，改方法体）
11. 状态机转换必须幂等（同一状态连续点 approve 不产生两条 version）
12. 评分引擎 SPI 禁止在 evaluate 方法里写库（只算+返回，落库由 `DqScoreService` 主动调）

---

## §Task（Phase 2 共 4 Task）

| Task | 文件/路径 | 操作 | curl / 浏览器 E2E 验收 |
|:--|:--|:--|:--|
| **T7 规则状态机 + 三滤波器 scores** | `gateway/.../db/migration/V112__ecos_dq_scoring_and_version.sql` + `data-engine-api/.../quality/DqRuleLifecycleService.java`（接口）+ `data-engine-impl/.../quality/service/DqRuleLifecycleServiceImpl.java` + 改 `DqGovernanceController.java`（替换 405 桩）+ 改 3 滤波器文件（追加 scores 双路径） | ① V112 加 `dq_score_snapshot` + `dq_score_asset` 2 表 + `dq_rule` 加 `approved_at/applied_at` 2 列；② Lifecycle Service：`submit/approve/reject/deprecate/supersede/disable` 6 个转换 + 每次 approve 写 `dq_rule_version` 快照 + `dq_rule.version+1` + 状态转换合法性校验；③ Controller 替换 Phase 1 的 405 桩：`POST /api/v1/dq/rules`（创建草稿 DRAFT）+ `PUT /api/v1/dq/rules/{id}`（编辑）+ `DELETE`（逻辑删除）+ `POST /api/v1/dq/rules/{id}/submit`/`approve`/`reject`/`deprecate`/`supersede`/`disable`；④ 三滤波器 3 文件追加 `/api/v1/dq/scores/**` + `/api/dq/scores/**`；⑤ 旧端点 `DqController.ensureGovernanceSchema` 可移除（V112 另路建表） | `curl POST /api/v1/dq/rules` 返 code=200 且 status=DRAFT；`POST /api/v1/dq/rules/{id}/submit` 返 status=IN_REVIEW；`POST /api/v1/dq/rules/{id}/approve` 返 version=2 + approvedBy 写入；`GET /api/v1/dq/rules/{id}/versions` 含两条；旧 `POST /api/v1/ecos/dq/rules` 仍 410 |
| **T8 评分引擎 + SPA 雷达** | 新增 `data-engine-impl/.../quality/scoring/{DqScoreEngine,DqCompletenessEvaluator,DqAccuracyEvaluator,DqConsistencyEvaluator,DqFreshnessEvaluator,DqUniquenessEvaluator,DqValidityEvaluator,DimensionScore,ScoringContext,DqScoreService,DqScoreMapper,DqScoreServiceImpl}.java`（API model：`DqScoreVO`/`DqAssetScoreVO`/`DqScoreTrendVO`）+ 新增 `DqScoreController.java`（4 端点）+ 改前端 `data-quality/api.ts`（加 `fetchDqScores`/`fetchDqScoresTrend`/`fetchDqScoresByGrade`/`fetchDqScoreSystem` 4 函数）+ 改 `DimensionTab.tsx`（接真实评分 + 健康度徽章 A-F）+ 改 `RuleCenterTab.tsx`（卡片顶部健康度） | ① `DqScoreEngine` SPI + 6 评估器（COMPLETENESS/ACCURACY/CONSISTENCY/FRESHNESS/UNIQUENESS/VALIDITY），每个抽 sample 1000 行 IStorageAdapter 拉样本计算；② `DqScoreService.recomputeForAsset(recomputeType, assetType, assetId)` → 写 `dq_score_snapshot` + 重算 asset 加权 → `dq_score_asset.rolled_score` 与 `grade`（A/B/C/D/F 95/85/70/50 分界）；③ Controller：`GET /api/v1/dq/scores?assetId=&dim=`、`GET /api/v1/dq/scores/trend?days=30`、`GET /api/v1/dq/scores/grade?grade=F`、`GET /api/v1/dq/scores/system` | `curl GET /api/v1/dq/scores/system` 返 overallScore 0-1 含 6 维 broken-down；新评一张表的 active rule 后 `curl` 该资产 `GET /api/v1/dq/scores?assetId=x&dim=UNIQUENESS` 返 0.92 之类；浏览器维度 Tab 雷达图显示 6 维真实分值 |
| **T9 前端规则中心状态机交互** | 改 `data-quality/RuleCenterTab.tsx`（顶状态/active 标记 + 审核对话框 + 版本时间线）+ 改 `HealthTab.tsx`（保留旧兼容加 Phase 2 提示）+ i18n 增 `dw.dqRule.statusMachine.*`（submit/approve/reject/deprecate/supersede/versions） | 前端「新建假规则」按钮实际 POST `/api/v1/dq/rules`（Phase 1 的 toast「Phase 2 就绪」移除）；列表右上角状态徽章（DRAFT 灰 / IN_REVIEW 黄 / ACTIVE 绿 / DEPRECATED 橙 / SUPERSEDED 灰划线 / REJECTED 红）；ACTIVE/IN_REVIEW 行有「审核」「废止」「废弃」按钮调 T7 端点；「版本历史」抽屉调 `GET /api/v1/dq/rules/{id}/versions` | 浏览器新建一条 DRAFT 规则 → submit → approve → 列表行变 ACTIVE 绿色 + 版本时间线出现 v1→v2 → 点「废止」→ DEPRECATED 橙色 → 同 ID 再新建会提示已废止（supersede 提示） |
| **T10 集成测试 + 审计闭环** | 新增 `data-engine-impl/src/test/java/com/chinacreator/gzcm/engine/data/quality/DqLifecycleAndScoreIT.java`（`@SpringBootTest`）+ 新增 `DqScoreEngineSpiTest.java`（纯单测 6 评估器输入/期望值）| IT：规则 CRUD 草稿→submit→approve→再编辑（必须 DRAFT 才能 edit）→supersede（生成 v3）→disable（status=DISABLED 不再执行）；评分：模拟 1 条 UNIQUE 评估器 5 行样本 2 重复 → 期望 0.6；SPI dispatch 命中正确评估器；异常：approve 一个 DRAFT（应失败，需先 submit）；审计：每个动作返回的 `auditId` 非空 | `mvn test -pl engine/data-engine/data-engine-impl` 全绿（≥8 case）+ `grep "audit" DqRuleLifecycleServiceImpl.java` 命中"submit"/approve"/"reject"/"deprecate"/"supersede"/"disable" 6 处 |

---

### §Task 工期

| Task | 人日 |
|:--|:--:|
| T7 规则状态机 + V112 + scores 三滤波器 | 3 |
| T8 评分引擎 + 6 评估器 + score API | 3 |
| T9 前端状态机交互 + 版本时间线 | 2.5 |
| T10 IT + 单测 | 1.5 |
| 合计 | **10 人日** |

---

### §Task 依赖与并行

```
T7 (V112 + Lifecycle + scores 滤波器) ──┐
                                        ├→ T9 (前端) ──→ 联调
T8 (评分引擎 + ScoreController) ────────┘         │
                              依赖 T7 的 V112 表  │
                              （T8 跑前必须 T7 先 mvn install 落 .m2）│
                                              T10 (IT)
                                              依赖 T7+T8 完成
```

**分发策略**：
1. **第 1 波**：T7（后端基础，含 V112 建表）单独发，它是后面所有的前置
2. **第 2 波**：T7 完成后，T8（评分引擎）与 T9（前端）并行发
3. **第 3 波**：T8+T9 完成后发 T10（IT）

---

### §安全自检卡

| 卡 | 验收命令 | 阈值 |
|:--|:--|:--:|
| 状态机审计 | grep DqRuleLifecycleServiceImpl 中 "auditWrite\|auditRead" | ≥ 6 |
| 评分 SPI 含 mask | grep DqScoreEngine* 中 "mask|ISecretService" | ≥ 1（PII/共识字段脱敏） |
| 评分 SPI 含 audit | grep DqScoreService* 中 "audit" | ≥ 1 |
| scores 三滤波器 | grep 3 文件 `/api/v1/dq/scores` | 4 文件 ≥ 1（双路径 2 处） |

---

### §风险与回退

| 风险 | 概率 | 缓解 |
|:--|:--|:--:|
| V112 在 Phase 1 V111 之后 Flight 高版本可能改过 schema | 低 | 先 `mvn flyway:info` 确认 V111 已 applied；V112 只 CREATE IF NOT EXISTS |
| 旧 `DqController.ensureGovernanceSchema` 与 V112 重复建表 | 高 | T7 删除 `DqController` 内 4 个 ensureGovernance* 私有方法（Phase 1 临时脚手架，V112 接管） |
| 评分 SPI 不同实现的边界值（如 NULL/空表） | 中 | 每个评估器 headComment 注明：`null样本 → 0.0`、`空表 → 1.0`（真空完整）或 `0.0` 视语义、Parameters 缺字段 → fallthrough 0.0 |
| 评分服务与 Lifecycle 状态机竞争（approve 同时 recompute） | 低 | `recomputeForAsset` 加 `synchronized` 或 DB 行锁（SELECT ... FOR UPDATE 限 1 条） |
| 大表评分 1000 行超时 | 中 | 评估器 thread 60s 超时自动 0.0 + 写 warning 日志，不阻塞主流程 |
| `Map.entry` LinkedHashMap 顺序稳定性 | 低 | DIMENSIONS 用 `static final Map` + 初始化序保 6 维度展示顺序（前端雷达不依赖后端序，按 dimension 枚举） |

---

### §产出物清单（Phase 2 完成标志）

- `gateway/.../db/migration/V112__ecos_dq_scoring_and_version.sql`
- `data-engine-api/.../quality/model/`：`DqScoreVO`/`DqAssetScoreVO`/`DqScoreTrendVO`/`LifecycleResult.java`
- `data-engine-impl/.../quality/scoring/`：8 个评分类（引擎 + 6 SPI + 上下文）
- `data-engine-impl/.../quality/service/DqRuleLifecycleServiceImpl.java` + `DqScoreService*.java`
- `data-engine-impl/.../quality/mapper/DqScoreMapper.java`
- `data-engine-impl/.../quality/controller/DqScoreController.java`
- 改：`DqGovernanceController.java`（替换 3 桩为真方法）+ `DqController.java`（删 ensureGovernance* 4 方法）+ 3 滤波器文件（scores 双路径）+ `DqRuleVO.java`（加 approvedBy/status 展示字段）
- 前端：`data-quality/api.ts`（+4 函数）+ `DimensionTab.tsx`（接真实推导）+ `RuleCenterTab.tsx`（状态机 + 时间线）
- `data-engine/src/test/java/.../quality/DqLifecycleAndScoreIT.java` + `DqScoreEngineSpiTest.java`
- `data-engine/AGENTS.md` 追加「DQ 生命周期 API + 评分 API + 评估器 SPI」3 段
