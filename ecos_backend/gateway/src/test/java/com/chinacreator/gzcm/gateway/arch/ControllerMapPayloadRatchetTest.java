package com.chinacreator.gzcm.gateway.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Controller 弱类型出入参「只减不增」棘轮门（DoD DI-2；#147AC 核定 → 本门落地）。
 *
 * <p><b>为什么是棘轮而不是等值</b>：DoD 判据是「无 {@code Map<String,Object>}」，实测存量
 * **2320 行 / 230 个 Controller 文件**（2026-11-02 实扫）。一次性清退属跨域契约重构，不在任何
 * 单批可达范围；若只把该行记成「未达成」而不设闸，存量会继续膨胀。故本门钉住「总数不增」：
 * 新增代码必须用强类型 DTO/VO，存量按域分批清退（每清退一处就把基线调低一次）。
 *
 * <p><b>口径</b>：main 源 {@code *Controller.java} 内**行级**命中
 * {@code Map<String,\s*Object>}（与核定时的 {@code grep -rn | wc -l} 同尺），排除
 * {@code target/} 与 {@code archive/}。基线变更必须同批给出清退证据（改小）或明确理由（改大=不许）。</p>
 *
 * <p><b>基线台账（改小必须留这一行的来源）</b>：
 * <ul>
 *   <li>2302 ＝ #147AC（2026-11-02）核定值；</li>
 *   <li>2297 ＝ #147Z（2026-11-04）HEAD 面复测值，差额 −5 行<b>全部</b>来自
 *       {@code OntologyGitController}（6→1，commit {@code 279bd553}「[§2.9.9 VG-2] 本体 Git
 *       服务契约去 Map」），其余 6 枚自核定以来改过的 Controller 计数未变——即本次下调
 *       <b>是真实清退，不是把判据放松</b>。</li>
 *   <li>2265 ＝ #147BT（2026-11-05）HEAD 面复测值，差额 −32 行<b>全部</b>来自
 *       {@code services/datanet/GitController}（33→1）：请求体 {@code Map} 改 DTO
 *       （commit/tag/rollback/branch），八条响应改 VO；剩余 1 枚是 {@code toDiffVO} 承接属主
 *       {@link com.chinacreator.gzcm.runtime.access.git.GitService#diff} 返回值
 *       {@code Map<String,Object>} 的形参（属主契约未改，不在本批范围）。</li>
 *   <li>2261 ＝ #147BW（2026-11-05）HEAD 面复测值，差额 −4 行<b>全部</b>来自 sysman
 *       配置/参数列表读面：{@code ConfigController} 3→1、{@code SystemParamController} 3→1
 *       （分页响应改强类型 {@code ConfigPageVO}/{@code SystemParamPageVO}）；各剩 1 枚是
 *       服务契约 {@code listConfigs}/{@code listParams(Map<String,Object>)} 的入参。wire 键面
 *       由 {@code ConfigListWireShapeTest} 3 例按集合相等钉住（键改名即红）。</li>
 *   <li>2249 ＝ #147BX（2026-11-05）HEAD 面复测值，差额 −12 行<b>全部</b>来自 sysman
 *       {@code DictController}（14→2）：三类字典列表改 {@code DictTypeEntryVO}/
 *       {@code DictTypeSummaryVO}、删除回执改 {@code DictDeleteResultVO}；剩 2 枚是
 *       {@code refreshCache}/{@code getDictUsage} 两条透传腿的签名（属主 {@code IDictService}
 *       仍返 Map，改契约须连属主一并改，另批处理）。键面由 {@code DictControllerWireShapeTest}
 *       5 例按集合相等钉住（含「空类型 {@code subsystem} 键仍在、值为 null」的 null 语义）。</li>
 *   <li>2229 ＝ #147BY（2026-11-05）HEAD 面复测值，差额 −20 行<b>全部</b>来自 sysman
 *       {@code DictTableController}（20→0）：请求体改 record（{@code DictTableRequest}/
 *       {@code DictColumnRequest}/{@code ReorderRequest}），响应改 record VO；建表/改表回执
 *       <b>不含 {@code columns} 键</b>（旧 Map 即无，另立 {@code DictTableBaseVO}，避免用可空
 *       columns 造出多余键）。键面由 {@code DictTableWireShapeTest} 6 例按集合相等钉住
 *       （含键序、ISO 时间串、tags 数组、缺省字段不落值、400 校验不丢）。</li>
 *   <li>2215 ＝ #147BZ（2026-11-05）HEAD 面复测值，差额 −14 行<b>全部</b>来自 sysman
 *       权限/角色域：{@code PermissionController} 6→0、{@code RoleController} 8→0
 *       （请求体改 DTO、{@code toMap} 改 VO）。<b>更新语义逐字保旧</b>：旧实现按
 *       {@code containsKey} 判定，DTO 用 {@code xxxPresent} 标记保住「显式 null 仍写空」
 *       （Jackson 对显式 null 仍调 setter、缺席不调）；建角色别名与默认值同守。键面由
 *       {@code RolePermissionWireShapeTest} 5 例钉住（含「显式 null 写空 / 未提供不动」双向）。</li>
 *   <li>2193 ＝ #147CA（2026-11-05）HEAD 面复测值，差额 −22 行<b>全部</b>来自 sysman
 *       {@code SysConfigController}（22→0）：行面由属主 {@code SysConfigQueryService} 以
 *       record 返回（{@code ConfigGroupRow} 用 {@code @JsonProperty} 钉住旧列名），SQL 也从
 *       Controller 收回服务。键面由 {@code SysConfigWireShapeTest} 4 例钉住：根列表 12 列名键、
 *       改值回执 snake_case 3 键与三别名、元数据 {@code consumed_by} 条件出现、审计 4+6 键。
 *       变异实红（去掉 {@code config_group} 注解 ⇒ 键名与键序同时漂）。</li>
 *   <li>2183 ＝ #147CB（2026-11-05）HEAD 面复测值，差额 −10 行<b>全部</b>来自 sysman
 *       {@code OrganizationController}（10→0）：请求体改 DTO、平铺/树视图改 VO。树节点
 *       {@code children} 用属性级 NON_NULL 保「仅子集非空时出现」；建机构缺省
 *       {@code orgType=DEPARTMENT} 与自动 {@code ORG_<millis>} 同守。键面由
 *       {@code OrganizationWireShapeTest} 5 例钉住（变异实红：去 NON_NULL ⇒ 叶子多
 *       {@code "children": null}）。</li>
 *   <li>2177 ＝ #147CD（2026-11-05）HEAD 面复测值，差额 −6 行来自 security 域两枚小控制器：
 *       {@code SecurityEngineStatusController} 5→1（status/start/stop 改 VO；剩 1 枚是
 *       {@code /config} 透传 {@code IEngine#getConfig()} 的底座契约）、{@code SecurityAuditController}
 *       2→0（请求改 7 字段 DTO、回执改 3 键 VO）。键面与「action 必填/缺省字段落 null」
 *       由 {@code SecuritySmallControllerWireShapeTest} 4 例钉住（VO 键改名变异实红）。</li>
 *   <li>2169 ＝ #147CE（2026-11-05）HEAD 面复测值，差额 −8 行<b>全部</b>来自
 *       {@code ImpersonateController}（8→0）：请求体改 {@code ImpersonateRequest}
 *       （{@code confirm} 保留布尔/字符串双形态），响应载荷位改 {@code Void}；
 *       同批删除两段从未使用的局部 Map（428 的 hint、501 的 detail，死代码）。
 *       状态行与错误码判据不变（428/501/400），门禁 {@code ImpersonateControllerTest}
 *       6 例（含 JSON 双形态绑定），变异实红（绕开 confirm 守卫 ⇒ 428 变 501）。</li>
 *   <li>2152 ＝ #147CF（2026-11-05）HEAD 面复测值，差额 −17 行<b>全部</b>来自
 *       {@code CryptoKeyAdminController}（17→0）：清单改 {@code KeyListVO}、轮换改
 *       {@code KeyRotation}、请求改 {@code RotateRequest}；属主
 *       {@code CryptoKeyRegistryService} 的 {@code project}/{@code rotate} 同步由 Map 改
 *       record {@code KeyRegistration}/{@code KeyRotation}。同批删除 428 分支未使用的局部
 *       Map。契约七键/三键键集断言由 {@code CryptoKeyAdminContractTest} 10 例钉住
 *       （序列化后键集相等；组件改名变异实红）。</li>
 *   <li>2144 ＝ #147CG（2026-11-05）HEAD 面复测值，差额 −8 行来自 security 域两枚控制器：
 *       {@code DataMaskingController} 5→0（请求改 {@code MaskApplyRequest} 并保留「非字符串元素
 *       ⇒ 400」口径；响应改两枚 VO；属主 {@code DataMaskingService} 的样例/结果改 record，
 *       {@code error} 仍仅未知规则时出现——属性级 NON_NULL，变异实红）；{@code AbacController}
 *       7→4（列表 4 键改 VO；剩 4 枚是审计载荷/描述辅助的 Map，属 audit 事件契约）。</li>
 *   <li>2138 ＝ #147CH（2026-11-05）HEAD 面复测值，差额 −6 行<b>全部</b>来自
 *       {@code CryptoAuditController}（6→0）：登记/分页/验证载荷位改 record，请求改
 *       {@code CryptoAuditRecordRequest}（5 字段缺省值与旧 getOrDefault 一致）；属主
 *       {@code CryptoAuditService#chainVerify} 同步改 {@code ChainVerifyResult}
 *       （{@code tampered[].issue} 仍仅异常时出现）。键集由 {@code CryptoAuditControllerTest}
 *       钉住（序列化后五键/三键/四键相等；组件改名变异实红）。</li>
 *   <li>2121 ＝ #147CI（2026-11-05）HEAD 面复测值，差额 −17 行<b>全部</b>来自 sysman
 *       {@code UserController}（17→0）：列表/角色 SQL 收回 {@code UserQueryService}
 *       （行面 record；时间列保留 JDBC Timestamp 由同一 ObjectMapper 渲染）、详情/创建/更新改
 *       {@code UserVO}、请求改 DTO（更新用 {@code xxxPresent} 保 containsKey 语义）、
 *       强制下线/随机密码/批量创建回执改 VO（批量 {@code errors} 仅非空时出现——初版发空数组
 *       被 {@code UserControllerWireShapeTest} 当场判红后修正）。</li>
 *   <li>2093 ＝ #147CJ（2026-11-05）HEAD 面复测值，差额 −28 行<b>全部</b>来自 sysman
 *       {@code TenantController}（28→0）：SQL/行映射全部收回 {@code TenantService}
 *       （行面 record 12/7/2/3/2/3 键用 {@code @JsonProperty} 钉住旧列名，时间列保留 JDBC 类型）、
 *       建/改请求改 DTO（改租户 {@code xxxPresent} 保 containsKey 语义）、配额/用量/账单响应改 VO；
 *       键面与「¥ 计费公式」由 {@code TenantControllerWireShapeTest} 5 例钉住
 *       （变异实红：去 {@code tenant_name} 注解 ⇒ 键名与键序同漂）。</li>
 *   <li>2057 ＝ #147CK（2026-11-05）HEAD 面复测值，差额 −36 行<b>全部</b>来自 datanet
 *       {@code DqDashboardController}（36→0）：RowMapper 由拼 Map 改 record（规则 13/问题 15/
 *       执行 8 键 snake），列表改 {@code DataPage}、看板/因果偏差/目标追踪改 VO；两个 405 墓碑腿
 *       去掉从不使用的请求体参数（缺体请求此前先落 400，与「旧写面一律 405」不一致）。
 *       <b>取值口径逐字保留</b>：{@code created_at}/{@code detected_at}/{@code executed_at}
 *       是 LocalDateTime 文本，{@code updated_at}/{@code resolved_at} 是原始 Timestamp（旧实现
 *       本就不一致）。门禁 {@code DqDashboardWireShapeTest} 6 例（含 RowMapper 直调），
 *       变异实红（去 {@code rule_type} 注解 ⇒ 键名与键序同漂）。</li>
 *   <li>2027 ＝ #147CL（2026-11-05）HEAD 面复测值，差额 −30 行<b>全部</b>来自 sysman
 *       {@code MonitoringController}（30→0）：仪表盘/自检由自建 Map 改 VO（指标 5 键、曲线点 4、
 *       进程 5、告警 4、自检 3＋条目 3 键），数据来源与取值口径零改动；门禁
 *       {@code MonitoringWireShapeTest} 2 例，变异实红（指标 {@code value} 改名 ⇒ 键集红）。</li>
 *   <li>2012 ＝ #147CN（2026-11-05）HEAD 面复测值，差额 −15 行来自 ai 域
 *       {@code WAgentReadinessController}（16→1）：评估/缺项/缺口/补齐四视图改 record
 *       （snake 键与 layers 的 D/I/K/C 由 {@code @JsonProperty} 钉住），请求体改 DTO 且保留
 *       「非字符串 ⇒ E-VALIDATION」语义；剩 1 枚是 {@code deferred_actions}（条目由派发侧
 *       {@code DispatchResult} 定义，属主契约另批）。本批新增两条键面断言（平铺 16 键、
 *       layers 4 键、缺项 7、缺口 12、补齐 10）——原 MockMvc 用例只断言了取值，键改名漏网，
 *       补断言后变异（去 {@code superseded_by} 注解）实红。</li>
 *   <li>1996 ＝ #147CO（2026-11-05）HEAD 面复测值，差额 −16 行<b>全部</b>来自 ai 域
 *       {@code WAgentEvidenceTraceController}（16→0）：claim/evidence/关联/反向索引四视图改
 *       record，两处条件键用属性级 NON_NULL（{@code evidence} 仅跨租户过滤后仍可取时出现、
 *       {@code run_id} 仅 Claim 可取时出现）；{@code claimView} 仍出 10 键基视图（与既有
 *       {@code WAgentEvidenceChainComplianceTest} 钉死名录一致），端点再追加
 *       {@code evidence_links}。**该门禁的 {@code invokeView} 同步支持 record 载体**（序列化后
 *       键面判等，与旧 Map 判据同尺）；变异实红（去 {@code content_hash} 注解 ⇒ 键集红）。</li>
 *   <li>1977 ＝ #147CP（2026-11-05）HEAD 面复测值，差额 −19 行来自 ai 域 wagent 候选面：
 *       {@code WAgentCandidateController} 14→0（列表 2 键、条目 19 键、详情/评审 20 键、
 *       评审行 7 键、五段校验 5 键，键序与旧 {@code LinkedHashMap} 逐键对齐），另有
 *       <b>共享降级出口</b> {@code WAgentDegradation.notImplemented} 由 Map 改 record
 *       {@code NotImplementedVO}（两键 {@code implemented}/{@code pending}），四处调用方签名
 *       随之收窄：{@code WAgentKillSwitchController} 1→0、{@code WAgentDecisionActionController}
 *       3→0、{@code WAgentToolContractController} 去掉 1 行签名。键面由
 *       {@code WAgentCandidateWireShapeTest} 4 例按集合相等＋键序钉住，变异实红
 *       （去 {@code basis_json} 注解 ⇒ {@code basisJson} 冒到首位且键集不等）。</li>
 *   <li>1962 ＝ #147CQ（2026-11-05）HEAD 面复测值，差额 −15 行来自 ai 域 wagent run 面：
 *       {@code WAgentRunController} 17→2（Run 8/进度能力位 4/详情 9/异步建 Run 11/SSE 降级 5 键；
 *       剩 2 枚为 {@code RunInputRequest.input} 自由映射入参与 {@code ok(Map)} 历史重载——前者键面由
 *       调用方定义、后者待 ToolContract 批次一并清退），同批把属主
 *       {@code WAgentOrchestrator#timeline} 由 Map 改 {@code RunTimelineVO}（事件 4/步骤 8 键）。
 *       键面由 {@code WAgentRunWireShapeTest} 6 例按集合相等＋键序钉住；**变异实红**：把
 *       {@code execution_dispatch}/{@code progress_channel} 的属性级 NON_NULL 改 ALWAYS ⇒
 *       同步通道多出两键、9 键断言红。</li>
 *   <li>1952 ＝ #147CR（2026-11-05）HEAD 面复测值，差额 −10 行来自 ai 域工具契约面：
 *       {@code WAgentToolContractController} 9→0（摘要 10 键/完整契约 28 键/#15 再追加
 *       {@code permission_filter} 共 29 键，三层键面与旧 Map 逐键同名同序；{@code contractView}
 *       的单源投影改由 {@code ToolContractVO.of} 承担，{@code /agent/tools/{name}} 的
 *       {@code contract} 字段同源不漂），同批删掉 {@code WAgentRunController.ok(Map)} 历史重载
 *       （其唯一使用者已强类型化，RunController 2→1）。键面由 {@code WAgentToolContractWireShapeTest}
 *       2 例与既有 {@code AgentToolDescribeContractTest} 4 例双向钉住；变异实红
 *       （去 {@code side_effect} 注解 ⇒ {@code sideEffect} 冒到首位）。</li>
 *   <li>1947 ＝ #147CS（2026-11-05）HEAD 面复测值，差额 −5 行<b>全部</b>来自 ai 域
 *       {@code WAgentGoalController}（5→0）：{@code view()} 12 键改 {@code GoalVO}（键序与旧
 *       Map 逐键一致，{@code as_of_date} 仍为 ISO 串、全推断提醒位仍为确定性布尔），并删除本类
 *       自建的 {@code ok(Map)} 改为复用 {@code WAgentRunController.ok}。键面由
 *       {@code WAgentGoalWireShapeTest} 2 例钉住，变异实红（去 {@code as_of_date} 注解 ⇒
 *       {@code asOfDate} 冒到首位）。{@code #2} 的 PATCH 体仍为 {@code Map<String,String>}
 *       （口径字段任意子集，键面由 {@link java.util.Set} 单源判定，不属本门违例类型）。</li>
 *   <li>1938 ＝ #147CT（2026-11-05）HEAD 面复测值，差额 −9 行<b>全部</b>来自 ai 域
 *       {@code WAgentQuestionController}（10→1）：建问题 READY 9 键/CLARIFY 12 键（澄清三键
 *       属性级 NON_NULL）、详情 7 键或 8 键；**条件键 + 显式 null 的逐字保真**用「存在位
 *       {@code @JsonIgnore} + {@code @JsonProperty} 方法返回 {@code JsonNode}（缺行时
 *       {@code NullNode}）」实现，旧 Map 的 {@code put(key,null)} 语义不漂。剩 1 枚是
 *       {@code QuestionCreateRequest.slots} 自由 JSON 入参（§5.2 #3 键面由调用方定义）。
 *       键面由 {@code WAgentQuestionWireShapeTest} 5 例钉住（含「行缺失但键仍在、值为 null」），
 *       变异实红（去掉存在位的 {@code @JsonIgnore} ⇒ {@code latestRunPresent} 泄漏成键）。</li>
 *   <li>1919 ＝ #147CU（2026-11-05）HEAD 面复测值，差额 −19 行<b>全部</b>来自 gateway
 *       {@code GatewayDiagnosticsController}（19→0）：制品/过滤链/档位矩阵/总响应改 record，
 *       {@code ORDER} 大写键与 {@code traceId} 恒在键位逐字保旧；{@code routeManifest} 按实测
 *       订正为<b>原始 manifest 文档</b>（旧双重 put 让摘要从未出网），字段改 {@code JsonNode}。
 *       同批把反退化下界 {@code MIN_LINES} 2000 → 1900（清退跨过整百线，下界不同步会把真清退
 *       判成「扫描面退化」）；键面由 {@code GatewayDiagnosticsWireShapeTest} 6 例钉住。</li>
 *   <li>1901 ＝ #147CV（2026-11-05）HEAD 面复测值，差额 −18 行来自 gateway {@code TaskController}
 *       （25→7）：任务 9 键/状态 6 键/列表 4 键/详情 2 键/提交·执行·批量回执/类型目录全部改 record；
 *       旧实现用 {@code Map.of} 拼装（键序不确定），改后键序确定并由
 *       {@code TaskControllerWireShapeTest} 5 例钉住。剩 7 枚为属主契约
 *       （{@code queryTasks(Map condition)} 入参、{@code getTaskStats()}/{@code health()} 自由结果）
 *       与自由 JSON {@code params}。变异实红（{@code progress} 改名 progressPct）。</li>
 *   <li>1879 ＝ #147CW（2026-11-05）HEAD 面复测值，差额 −22 行来自 gateway
 *       {@code RuntimeAlertV1Controller}（28→6）：列表页 4 键 + 告警行 12 键 / DLQ 行 8 键
 *       （V242/V243 显式 SELECT 列，snake 键与 JDBC 时间值逐字保留）、ack/关闭/重放回执改 record；
 *       属主 {@code RuntimeAlertRepository} 的列表两腿同步改强类型行（详情腿
 *       {@code findAlertById} 是 {@code SELECT *}，动态列<b>不</b>冻结，保留 Map）。
 *       剩 6 枚全部来自该 {@code SELECT *} 详情腿与 {@code closeTime} 回查。状态行与 404/400/503
 *       分支判据零改动（既有 {@code AlertCloseEndpointContractTest} 9 例随新签名改写、判据未放宽）；
 *       键面由 {@code RuntimeAlertWireShapeTest} 3 例钉住，变异实红（{@code rule_code}→ruleCode）。
 *       反退化下界同批 1900 → 1800（跨整百线）。</li>
 *   <li>1865 ＝ #147CX（2026-11-05）HEAD 面复测值，差额 −14 行来自 ai 域
 *       {@code AgentToolController}（23→9）：执行面 4 键/参数条目 4 或 6 键（{@code default}/
 *       {@code enum} 条件出现）、描述 6 键（双真值面缺席如实 null）、校验 3~5 键（NON_NULL）、
 *       干跑 7 键 + 裁决摘要 6 键；{@code obligations} 键名取
 *       {@code GuardrailDecisionContract.KEY_OBLIGATIONS} 常量。剩 9 枚为自由 JSON 面
 *       （{@code arguments}/{@code body}/{@code parseJson} 入口）与 {@code functionCallSchema}、
 *       {@code obligations} 属主契约。键面由 {@code AgentToolWireShapeTest} 4 例钉住，
 *       变异实红（{@code functionCallSchema}→functionCallSchemaX）。</li>
 *   <li>1856 ＝ #147CY（2026-11-05）HEAD 面复测值，差额 −9 行来自 ai 域
 *       {@code AIPAgentController}（26→17）：列表摘要 6 键 / 详情 11 键 / 删除回执 2 键改 record；
 *       请求体与 {@code metadata}/{@code capability} JSON、模板目录、{@code AgentTestResult#toMap}
 *       等自由面与属主契约保留。{@code containsKey}/{@code getOrDefault} 语义与归档/审计腿零改动；
 *       {@code AipDeleteAgentStatusFidelityTest} 6 例随签名改写、状态行判据未放宽；
 *       键面由 {@code AipAgentWireShapeTest} 3 例钉住，变异实红（{@code modelProvider}→model_provider）。</li>
 *   <li>1810 ＝ #147CZ（2026-11-05）HEAD 面复测值，差额 −46 行来自 buszhi 工作流双宿主：
 *       {@code WorkflowController} 32→9 与 {@code OntologyWorkflowApiController} 32→9。属主
 *       {@code WorkflowService}（定义行 10 键/预览 5 键/导出 11 键含 {@code $schema}/
 *       校验 4 键）与 {@code WorkflowInstanceService}（实例 10 键 + 启动腿尾部 {@code definition}
 *       3 键，属性级 NON_NULL）改 record，双控制器适配层直接消费（仍用 {@code convertValue}
 *       维持 VO 投影单源）。剩 9 枚/侧为自由面：测试/预览上下文、校验定义 payload、
 *       DTO→Map 转接、引擎执行结果（steps/context 动态）。**变异实红并抓到一层真护栏**：
 *       键名改 {@code published_at} 时 {@code convertValue} 直接抛
 *       {@code UnrecognizedPropertyException}（strict），说明 VO 投影不会静默吞掉改名。
 *       键面由 {@code WorkflowWireShapeTest} 6 例钉住（含导出 {@code $schema} 尾键与
 *       VO 级 NON_NULL 省略语义）；buszhi-impl 全量见下。</li>
 *   <li>1788 ＝ #147DA（2026-11-05）HEAD 面复测值，差额 −22 行来自 ai 域
 *       {@code AgentConfigController}（27→5）：Agent 行 10 键/列表包裹 2 键/删除回执 1 键/
 *       模型 3 键/Prompt 模板 6 或 7 键（{@code agentId} 条件出现）/测试回执三态并集（成功 9、
 *       依赖不可用 4、失败 5）改 record，属主 {@code AgentConfigService} 同步改行面；
 *       剩 5 枚为请求体自由 Map（创建/更新/绑定/测试）。反退化下界同批 1800 → **1700**
 *       （清退再次跨整百线）。键面由 {@code AgentConfigWireShapeTest} 5 例钉住，
 *       变异实红（{@code modelProvider}→model_provider）。</li>
 *   <li>1766 ＝ #147DB（2026-11-05）HEAD 面复测值，差额 −22 行来自 ai 域
 *       {@code AgentSessionOpsController}（24→2）：上下文 13 键、压缩回执 13 键（keyRefs 两键
 *       恒 null 仍出键）、用量 9 键 + 桶 5 键（estimated 恒 false、costRatio 恒 null）、
 *       裁决台账 4 键 + 条目 7 键（{@code policyRef} 键名取契约常量）+ 过滤回显 5 键改 record；
 *       内部聚合容器改 {@code Map<String,Bucket>}（不受本门判据约束但避开弱类型载荷）。
 *       剩 2 枚为属主 {@code GuardrailDecisionLedgerDao#listFiltered} 台账行。既有 12 例随签名
 *       改写全绿；键面由 {@code AgentSessionOpsWireShapeTest} 4 例钉住，变异实红
 *       （{@code quotaRemaining}→quota_remaining）。</li>
 *   <li>1758 ＝ #147DC（2026-11-05）HEAD 面复测值，差额 −8 行来自 security 域
 *       {@code SecurityConfigController}（24→16）：读数 12 键（source 首键 + 视图 11 键、
 *       档案空列 NON_NULL 不出键）、更新回执（动态 {@code user_id}/{@code role_id} 列 +
 *       条件字段）、分页 4 键、删除回执 2 键改 record；属主
 *       {@code SecurityConfigService#populateProfileResult} 同步改 {@code SecurityProfileView}
 *       （缺省三键仍取 {@link com.chinacreator.gzcm.sysman.security.clearance.model.ClearanceLevel}
 *       单源常量，D-B4-99 棘轮面未放宽）。剩 16 枚为请求体自由 Map（三处 upsert 入口）与
 *       审计事件载荷（{@code buildAuditPayload}/{@code publishAudit} 的动态 extra 契约）。
 *       键面由 {@code SecurityConfigWireShapeTest} 4 例钉住，变异实红（source→source_）。</li>
 *   <li>1753 ＝ #147DD（2026-11-05）HEAD 面复测值，差额 −5 行来自 ontology 域
 *       {@code OntologyService#listAllObjects()} 行面强类型：新增 {@code OntologyEntityRow}
 *       （11 键，与 {@code entityToMap} 同键同序）后，四个消费面一次收口——
 *       {@code OntologyEntityGlobalController} 1→0、{@code OntologyDataController.listObjectTypes}
 *       1→0、{@code LineageController.listLineageEntities} −1、{@code OntologyExportController}
 *       导出腿 −2（行变量与循环类型；{@code linkTypes} 仍收动态行 {@code List<Object>}）。
 *       键面由 {@code OntologyEntityRowWireShapeTest} 1 例钉住，变异实红
 *       （{@code entityType}→entity_type）。</li>
 *   <li>1723 ＝ #147DE（2026-11-05）HEAD 面复测值，差额 −30 行来自 sysman
 *       {@code CausalController}（31→1）：节点 4 键/边 5 键/图 4 键/路径 5 键/路径列表 2 键/
 *       对比场景 4 键/投影节点 5 键（原 4 键 + {@code impactFactor}）/投影边 6 键
 *       （+{@code projectedWeight}）/差异 4 键/对比 3 键全部改 record；属主
 *       {@code CausalQueryService#listNodes/listEdges} 同步改行 record（DAO 仍出 Map，行转换在服务层）。
 *       内存种子与 DB 兜底两条腿共用同一记录类型；剩 1 枚为 {@code /compare} 的自由请求体
 *       （只读 {@code scenarioIds}）。键面与 BFS 行为由 {@code CausalWireShapeTest} 4 例钉住
 *       （含 n1→n2→n3 权重 1.57 的实算），变异实红（{@code weight}→weight_）。</li>
 *   <li>1699 ＝ #147DF（2026-11-05）HEAD 面复测值，差额 −24 行来自 ai 域
 *       {@code DiagnosticAgentController}（31→7）：工具条目 DB 7 键 / 硬编码 5 键（{@code id}、
 *       {@code schema} 条件出现）、Agent 配置 6 键、偏差/因果链/场景行、对话 5 键、端到端诊断
 *       5 键、**工具执行回执四态并集**（诊断三腿 + 知识图谱腿 + 通用腿，条件键 NON_NULL、
 *       {@code executedAt} 恒为尾键）改 record；顺带删除 chat 中从未使用的
 *       {@code promptTemplate}/{@code datasetContext} 两个死变量。剩 7 枚为请求体自由 Map 与
 *       知识图谱透传（{@code params}/{@code graphResult}/{@code data}）。反退化下界同批
 *       1700 → **1600**（清退跨整百线）。键面由 {@code DiagnosticAgentWireShapeTest} 4 例钉住
 *       （DAO 行经 {@code Proxy} 假 ResultSet 喂真实 RowMapper），变异实红
 *       （{@code deviationPct}→deviation_pct）。</li>
 *   <li>1689 ＝ #147DG（2026-11-05）HEAD 面复测值，差额 −10 行来自 datanet
 *       {@code TransformController}（23→13）：步骤条目 3 键/元数据 2 键/输出面（{@code columns}、
 *       {@code rows} 条件出现，空输出为 {@code {}}）/统计 4 键/结果 5 键（{@code statistics}
 *       条件出现）改 record；请求体 {@code {input, chain}} 与 DataFrame {@code rows}/{@code data}
 *       仍为自由 JSON（管道 DSL 与行数据）。既有 {@code TransformControllerTest} 10 例改写为
 *       「强类型载荷经 ObjectMapper 转回 Map 断言」，判据零放宽；变异实红
 *       （{@code totalSteps}→total_steps）。</li>
 *   <li>1679 ＝ #147DH（2026-11-05）HEAD 面复测值，差额 −10 行来自 ai 域
 *       {@code AgentLoopController}（31→21）：会话 8 键、消息 8 键、会话列表 2 键、
 *       会话详情 8 键（{@code messages} 仅非空时出现）、消息分页 4 键改 record；
 *       时间字段保持毫秒 {@code Long}（旧 Map 同值）。chat/SSE 帧与工具记录仍为动态结构。
 *       键面由 {@code AgentLoopSessionWireShapeTest} 4 例钉住（含「无消息不出键」与
 *       page size 上限 500），变异实红（{@code lastActiveAt}→last_active_at）；
 *       既有 {@code AgentLoopSseFrameSequenceTest} 15 例与 SSE 单源门 10 例复跑绿。</li>
 *   <li>1659 ＝ #147DI（2026-11-05）HEAD 面复测值，差额 −20 行来自 datanet
 *       {@code LineageCompatController}（31→11）：影响 7 键 + 节点 6 键、解析 5 键 + 血缘视图
 *       2 键改 record；BFS 队列条目改内部 record。**节点/边集合仍以 {@code Object} 承载
 *       解析器原样 JSON**（节点可有 {@code id} 或 {@code table}、边可有 {@code source/target}
 *       或 {@code source_node_id/target_node_id}），不冻结动态 schema；剩余 11 枚即
 *       属主 {@code DataLineageService#getLineage} 的 Map 行与动态 JSON 同化辅助。
 *       键面由 {@code LineageCompatWireShapeTest} 3 例钉住（hop=1 风险 80、空 payload
 *       空结果），变异实红（{@code impactedNodes}→impacted_nodes）。</li>
 *   <li>1640 ＝ #147DJ（2026-11-05）HEAD 面复测值，差额 −19 行来自 ontology 域
 *       {@code CaliberController}（19→0）：当前生效行 18 键（别名列序冻结、null 值照常出现）、
 *       版本读回 10 键、版本历史 16 键、审批 4 键 / 退休 3 键（{@code approvedBy} 仅审批腿）、
 *       绑定回执 4 键、冲突计数 {@code count + evidence.criterion} 改 record；
 *       {@code detail.versions[]} 同步由 {@code List<Map>} 改 {@code CaliberVersionHistoryVO}。
 *       **属主 {@code CaliberService} 行 Map 未改**（DAO 显式列清单契约），转换在 dto 的
 *       {@code of(Map)}（与 #147DI 同口径），控制器内以 {@code var} 承接属主行。
 *       键面由 {@code CaliberWireShapeTest} 6 例钉住（顺序敏感；含 null 键与退休腿三键），
 *       变异实红（{@code version_status}→versionStatus）。</li>
 *   <li>1629 ＝ #147DK（2026-11-05）HEAD 面复测值，差额 −11 行来自 datanet 域
 *       {@code DqController}（20→9）：10 个 F02-08 停用写端点 + {@code legacyWriteBlocked}
 *       改 {@code ApiResponse<Void>}（停用腿从不读请求体，入参一并删除；HTTP 语义不变），
 *       错误信封 wire 不变（{@code data} 恒 null + NON_NULL）。剩 9 枚为 legacy 只读行
 *       （旧表 {@code ecos_dq_rule_v2}/{@code ecos_quality_rule} 由 {@code DqService} 以 Map 出，
 *       属主契约未改）。新增 {@code DqLegacyWriteGateTest} 2 例（十腿 409 + 信封无 data 键），
 *       变异实红（409→410）；{@code data-engine-impl} 全量 **587/0/0/1**。</li>
 *   <li>1610 ＝ #147DL（2026-11-05）HEAD 面复测值，差额 −19 行来自 datanet 域
 *       {@code DataEngineConfigController}（19→0）：配置行收成 {@code DataEngineConfigRow}
 *       （{@code config_key}/{@code config_value} 两列，null 键跳过、null 值折空串，与原分组等价），
 *       分段视图 3 层 {@code segments[].subgroups[].id/configs}、更新回执 {@code updated/total}、
 *       刷新回执 {@code refreshed/cache_size}（snake 键）改 record；分段/子分区固定顺序与
 *       {@code pipeline-advanced} 精确覆盖原样保留。属主 {@code SysConfigService#getByGroup}
 *       仍出 Map（转换在 dto 的 {@code fromRows}）。键面与顺序由
 *       {@code DataEngineConfigWireShapeTest} 3 例钉住（四段顺序、空 semantic 段、未知键落
 *       global/general、缺省补插），变异实红（{@code cache_size}→cacheSize）；
 *       {@code data-engine-impl} 全量 **590/0/0/1**。</li>
 *   <li>1602 ＝ #147DM（2026-11-05）HEAD 面复测值，差额 −8 行来自 ontology 域
 *       {@code OntologyDataController}（25→17）：读面记录 9 键（{@code recordKey/status} 在前、
 *       {@code properties} 居中、{@code createdBy/createdAt/updatedAt} 条件键 NON_NULL）、
 *       分页 5 键、创建回执 7 键（{@code status} 尾键，独立 record 保序）、更新回执 7 键
 *       （{@code updatedAt} 尾键）改 record；{@code properties} 属对象类型 EAV 自定义属性，
 *       原样透传不冻结。剩 17 枚为自由请求体（{@code properties} 用户自定义）与
 *       {@code payload} JSON 存取（DB 列契约）。键面由 {@code OntologyDataWireShapeTest} 3 例
 *       钉住（三腿三种落键形态 + 条件键省略），变异实红（{@code recordKey}→record_key）；
 *       {@code ontology-engine-impl} 全量 **301/0/0/0**。</li>
 *   <li>1586 ＝ #147DN（2026-11-05）HEAD 面复测值，差额 −16 行来自 sysman 域
 *       {@code MarketplaceController}（18→2）：内存 store 改 {@code MarketplaceAssetVO}
 *       （11 键：id/name/nameZh/description/category/status/owner/version/tags/createdAt/updatedAt，
 *       时间保持毫秒 {@code long}），列表 2 键 / 搜索 3 键（{@code query} 恒出现含 null）/
 *       仪表盘 5 键 / 访问申请 6 键改 record；publish 缺省值与 tags 字符串数组收敛。
 *       剩 2 枚为写面自由请求体。键面与顺序由 {@code MarketplaceWireShapeTest} 4 例钉住，
 *       变异实红（{@code updatedAt}→updated_at）；{@code sysman-impl} 全量 **238/0/0/0**；
 *       反退化下界同批 1600 → **1500**（清退跨整百线）。</li>
 *   <li>1571 ＝ #147DO（2026-11-05）HEAD 面复测值，差额 −15 行来自 gateway 监控面
 *       {@code MonitorController}（15→0）：仪表盘 7 键 + 系统资源 4 键（{@code cpu_cores/
 *       cpu_load/heap_used_mb/heap_max_mb}）、时间序列 3 键、进程 5 键、健康 4 键
 *       （{@code database.error} 仅探测抛异常时出现）、告警统计 3 键改 record；
 *       {@code recent_alerts}/{@code today_by_level} 为告警表动态列（属主
 *       {@code MonitorService} 行，原样透传）。键面与顺序由 {@code MonitorWireShapeTest}
 *       3 例钉住（含 error 条件键），变异实红（{@code uptime_ms}→uptimeMs）。同批修
 *       {@code SqlDialectDerivationTest} 的 B_ILIKE 钉表归属（用户查询 SQL 已下沉
 *       {@code UserQueryService}，修复 #147AS~#147BN 间遗留的跨门红线）。</li>
 *   <li>1561 ＝ #147DP（2026-11-05）HEAD 面复测值，差额 −10 行来自 buszhi 域
 *       {@code WorkflowTaskController}（14→4）：任务行 11 键（id/instanceId/nodeId/taskType/
 *       title/assignee/status/priority/dueDate/completedAt/createdAt，空值键保留）在
 *       列表/详情/签收/完成/转签/驳回六腿同形，{@code WorkflowTaskService} 同步由
 *       {@code toMap} 改 {@code toVO}；列表信封 {@code data+total}、统计 4 键改 record；
 *       剩 4 枚为流转请求体（完成表单自由字段）。键面与顺序由
 *       {@code WorkflowTaskWireShapeTest} 3 例钉住，变异实红（{@code instanceId}→instance_id）；
 *       {@code buszhi-impl} 全量 **26/0/0/0**。</li>
 *   <li>1553 ＝ #147DQ（2026-11-05）HEAD 面复测值，差额 −8 行来自 buszhi 域
 *       {@code WorkflowApprovalController}（12→4）：通过/驳回回执 2 键（approvalId/decision）、
 *       转签 3 键（taskId/decision/newAssignee）、加签 2 键（taskId/status）改 record，
 *       {@code WorkflowApprovalService} 四个方法同步返强类型；实例审批腿
 *       {@code WorkflowInstanceApprovalController} 改读 {@code approvalId()}，
 *       既有 {@code WorkflowInstanceApprovalContractTest} 9 例同步改桩复跑绿；剩 4 枚为
 *       审批请求体（意见/表单自由字段）。键面由 {@code WorkflowApprovalWireShapeTest} 3 例钉住，
 *       变异实红（{@code newAssignee}→new_assignee）；{@code buszhi-impl} 全量 **29/0/0/0**。</li>
 *   <li>1542 ＝ #147DR（2026-11-05）HEAD 面复测值，差额 −11 行来自 sysman 域
 *       {@code PortalSearchController}（11→0）：应答 5 键（q/type/total/items/rlsApplied）、
 *       命中项 4 键（type/id/name/url，对齐前端 {@code SearchHit}）改 record；打分/段合并
 *       迁入 {@code PortalSearchItemVO}（标题 2 分、描述 1 分规则不变，内部 score 不出 wire），
 *       {@code id/name} 保持 {@code Object}（四表主键/名称列类型不一）。键面与顺序由
 *       {@code PortalSearchWireShapeTest} 3 例钉住（去重、type 过滤单腿、空 q 400），
 *       变异实红（{@code rlsApplied}→rls_applied）；{@code sysman-impl} 全量 **241/0/0/0**。</li>
 *   <li><b>存量面分档快照（2026-11-05 · #147DS · 不产代码）</b>：HEAD 实扫 **1542 行 / 202 命中文件**
 *       （工作树 1553/205；差 11 行 / 3 文件为并发会话在途面）。分布（工作树计）：≥20 行 **9** 文件、
 *       10~19 行 **47**、1~9 行 **149**。≥20 的 9 枚全部落在既有分档②「先裁模型 schema 再定型」：
 *       {@code ObjectController}(58，他人未提交在途)、{@code MetadataController}(49，元数据动态列)、
 *       {@code LineageController}(39，内存双形行)、{@code OntologyProposalController}(36，{@code SELECT *}+payload)、
 *       {@code WorldModelGraphController}(25，两源同键不同型)、{@code CognitivePlannerController}(25，链节点任意对象)、
 *       {@code ClassificationController}(23，LLM 自由 JSON)、{@code AgentLoopController}(21，SSE 帧/工具记录)、
 *       {@code QueryController}(20，SQL 结果动态列＋RLS 四态)。1~9 行档多数是「对外已 VO、剩余为
 *       JSONB / 内存 store」的内部转换行（如 {@code OntologyMappingController}、{@code OntologyExportController}、
 *       {@code VersionDiffController}）。结论：<b>无 schema 裁决即无可机械清退面</b>，后续批次须先裁对应域模型，
 *       禁为降数字冻结动态 payload / 反向改 wire。</li>
 *   <li>1517 ＝ #147DT（2026-11-05）HEAD 面复测值，差额 −25 行来自 sysman 域
 *       {@code WorldModelGraphController}（25→0）：**双源同键不同型裁决**——目标行 22 键/
 *       因果节点 5 键/因果边 5 键改 record 并<b>键面冻结、值型按源透传</b>：DB 路
 *       {@code id/parentId} 数值、{@code createdAt} ISO 字符串；内存兜底 id/parentId 业务字符串、
 *       时间毫秒；因数值/日期/阈值列两源形态不一，这些位保持 {@code Object}（机械强转即改 wire，
 *       与 #147DI 节点/边同口径）。树形 {@code children} 仅非空时出现；节点 {@code type} /
 *       边 {@code description} 为条件键（内存兜底 4 键 vs DB 5 键）；列表/因果图/删除回执改 record。
 *       键面与两源值型由 {@code WorldModelGraphWireShapeTest} 4 例钉住，变异实红
 *       （{@code type}→node_type）；{@code sysman-impl} 全量 **245/0/0/0**。</li>
 *   <li>1499 ＝ #147DU（2026-11-05）HEAD 面复测值，差额 −18 行来自 cognitive 域
 *       {@code CognitivePlannerController}（25→7）：计划体 10 键、建议 2 键（index/suggestion）、
 *       优化建议 4 键（parameter/currentValue/suggestedValue/reason）改 record；**两处显式豁免**——
 *       {@code parameters}（调用方任意参数包）与 {@code chain[]}（推理节点展开字段随
 *       {@code CausalChainNode} 演进、兜底层为 4 键固定形）保留 {@code Object}，不冻结 payload
 *       （与 #147DI 节点/边同口径）。剩 7 枚为两个自由请求体与其参数包转换。键面与链双形由
 *       {@code CognitivePlannerWireShapeTest} 3 例钉住（兜底 4 键 / 推理 9 字段 / 数值 +10%），
 *       变异实红（{@code planId}→plan_id）；{@code cognitive-engine-impl} 全量 **552/0/0/0**；
 *       反退化下界同批 1500 → **1400**（清退跨整百线）。</li>
 *   <li>1462 ＝ #147DV（2026-11-05）HEAD 面复测值，差额 −37 行来自 ontology 域
 *       {@code LineageController}（39→2）：内存边记录改 {@code LineageRecordVO}——**两建边腿同表异键**，
 *       手工腿 8 键（含 transform/description、无 eventId）、解析腿 7 键（含 eventId、无前两键），
 *       条件键 NON_NULL 逐字保留；部分更新改不可变副本（`withUpdated`）；图 nodes[{id}] /
 *       edges 4 键、追溯 3 键、事件 6 键（nodes/edges 为解析器原样 JSON）、影响分析 6 键
 *       （impactedObjects 动态行透传、impactedNodes 补 path 缺省、totalRisk/severity 计算单源）改 record。
 *       剩 2 枚为手工建/改边自由请求体。键面与两腿条件键由 {@code LineageWireShapeTest} 3 例钉住，
 *       变异实红（{@code lineageType}→lineage_type）；{@code ontology-engine-impl} 全量 **304/0/0/0**。</li>
 *   <li>1457 ＝ #147DW（2026-11-05）HEAD 面复测值，差额 −5 行来自 ai 域
 *       {@code AgentLoopController}（21→16）：{@code chat}/{@code chatInSession} 回执改
 *       {@code AgentChatResultVO}（6 基键 + 错误三件套/{@code retryable}/{@code toolCalls} 条件键，
 *       与旧 Map 同序同形），{@code AiResponseSupport} 增泛型 {@code fromLoopResult} 重载
 *       （Map 调用仍走具体重载；失败态状态行与信封逐字段同源），删 {@code resultToMap} 死方法；
 *       {@code toolCalls} 元素为属主工具记录动态结构，原样透传。新增
 *       {@code AgentChatResultWireShapeTest} 3 例（成功 6 键 / 失败条件键与状态行 / 空工具记录不出键），
 *       变异实红（{@code retryable}→retryable_flag）；{@code ai-engine-impl} 全量 **658/0/0/0**；
 *       同批按「增面登记」把 {@code WAgentForbiddenDependencyArchTest} 源码面基线 264 → 265。</li>
 *   <li>1444 ＝ #147DX（2026-11-05）HEAD 面复测值，差额 −13 行来自 ai 域
 *       {@code AIPGuardrailController}（15→2）：内存护栏行改 {@code AipGuardrailVO}（10 键，
 *       {@code rule/priority/enabled} 为请求体原值透传位保持 {@code Object}），部分更新改不可变副本；
 *       审计日志 8 键改 {@code AipAuditLogVO}；删除回执 2 键；剩 2 枚为建/改自由请求体。
 *       键面由 {@code AipGuardrailWireShapeTest} 3 例钉住，变异实红（{@code createdAt}→created_at）；
 *       {@code ai-engine-impl} 全量 **661/0/0/0**；源码面基线按增面 265 → 268。</li>
 *   <li>1427 ＝ #147DY（2026-11-05）HEAD 面复测值，差额 −17 行来自 kb 域
 *       {@code OntologyTreeController}（17→0）：远端载荷解码（{@code data} 数组/单对象两形态、
 *       entity 行 {@code code/name/entityType/domainId/sortOrder}、sortOrder 容错转 int）
 *       迁入 {@code OntologyTreeRemotePayload}（dto，外部输入不冻结 schema）；控制器只保留
 *       {@code fetchData} 取 {@code data} 段 + 强类型 {@code OntologyTreeVO} 组树；删除两枚
 *       未被使用的私有 legacy 装载器（{@code loadEntityCodes/loadEntityBriefs}）与重复解码方法。
 *       键面由 {@code OntologyTreeWireShapeTest} 2 例钉住（分桶/条目/摘要键面、
 *       单对象兼容、实体腿不可达降级空表），变异实红（单对象兼容分支短路）；
 *       {@code kb-engine-impl} 全量 **357/0/0/2**（2 例既有 skip）。</li>
 *   <li>1413 ＝ #147DZ（2026-11-05）HEAD 面复测值，差额 −14 行来自 ai 域
 *       {@code AIPModelController}（16→2）：内存模型行改 {@code AipModelVO}——**内置腿 8 键/
 *       手工注册腿 10 键**（{@code endpoint/apiKeyRef} 为条件键 NON_NULL，缺省空串），
 *       部分更新改不可变副本；健康检查 4 键改 {@code AipModelHealthVO}；删除复用 2 键回执；
 *       剩 2 枚为建/改自由请求体。键面由 {@code AipModelWireShapeTest} 3 例钉住，
 *       变异实红（{@code latencyMs}→latency_ms）；{@code ai-engine-impl} 全量 **664/0/0/0**；
 *       源码面基线按增面 268 → 270。</li>
 *   <li>1399 ＝ #147EA（2026-11-05）HEAD 面复测值，差额 −14 行来自 ai 域
 *       {@code NLQController}（15→1）：回执改 {@code NlqResultVO}
 *       （{@code translated/results[/agent_answer][/message]}，结果列名统一转小写，
 *       回退腿条件键 NON_NULL）；ObjectQL **动态 DSL** 的规范化（复合 filter→filters、
 *       复合查询取首子查询）与 JSON 文本化（转义/数字/布尔/列表逐字同旧）收口
 *       {@code ObjectQlText}；剩 1 枚为自由请求体。键面与文本化由
 *       {@code NlqResultWireShapeTest} 3 例钉住，变异实红（{@code agent_answer}→agentanswer）；
 *       {@code ai-engine-impl} 全量 **667/0/0/0**；源码面基线按增面 270 → 272；
 *       反退化下界同批 1400 → **1300**。</li>
 *   <li>1386 ＝ #147EB（2026-11-05）HEAD 面复测值，差额 −13 行来自 cognitive 域
 *       {@code DecisionController}（15→2）：决策行 8 键改 {@code CognitiveDecisionVO}
 *       （{@code createdAt} 保持 JDBC {@code Timestamp}，序列化形态不变）；record 1 键 / link 3 键
 *       回执改 record；影响图 {@code rootId/nodes/edges}（节点/边各 3 键）改
 *       {@code DecisionImpactVO}；合规检查 4 键改 {@code DecisionCheckVO}；剩 2 枚为 record/link
 *       自由请求体。键面由 {@code DecisionWireShapeTest} 3 例钉住，变异实红（{@code rootId}→root_id）；
 *       {@code cognitive-engine-impl} 全量 **555/0/0/0**。</li>
 *   <li>1370 ＝ #147EC（2026-11-05）HEAD 面复测值，差额 −16 行来自 ai 域
 *       {@code AIPPipelineController}（18→2）：内存 Pipeline 行改 {@code AipPipelineVO}
 *       （{@code steps} 自由结构透传；{@code lastExecutedAt} 执行后尾键 NON_NULL）；
 *       列表摘要 6 键、执行回执 4 键、删除复用 2 键回执；部分更新/执行回写改不可变副本；
 *       剩 2 枚为建/改自由请求体。键面由 {@code AipPipelineWireShapeTest} 3 例钉住，
 *       变异实红（{@code lastExecutedAt}→last_executed_at）；{@code ai-engine-impl} 全量
 *       **670/0/0/0**；源码面基线按增面 272 → 275。</li>
 *   <li>1364 ＝ #147ED（2026-11-05）HEAD 面复测值，差额 −6 行来自 workspace 域
 *       {@code ObjectStateMachineController}（14→8）：状态机视图 4 键（entityCode/states/
 *       transitions/totalTransitions）与转换规则 8 键改 record（{@code sortOrder} 保持 DB 原值），
 *       循环内逐键建 Map 收口 {@code StateTransitionVO.of}；剩 8 枚为引擎/服务契约行
 *       （{@code StateMachineEngine.getTransitionsInfo/executeTransition/listDefinitions/
 *       createDefinition} 与 demo 表状态查询，属 runtime 引擎契约，另批裁决）。键面由
 *       {@code StateMachineWireShapeTest} 2 例钉住，变异实红（{@code from}→from_status）；
 *       {@code workspace-impl} 全量 **389/0/0/3**（3 例既有 skip）。</li>
 *   <li>1351 ＝ #147EE（2026-11-05）HEAD 面复测值，差额 −13 行来自 workspace 域
 *       {@code WorkbookController}（14→1）：执行历史记录 6 键（{@code result} 为引擎原样结构）改
 *       {@code WorkbookExecutionRecordVO}；会话摘要 2 键、环境状态 {@code available[/message]}、
 *       健康检查 {@code sql/python/r} 三段改 record；**顺带删除一处死变量**（异常腿构造的
 *       {@code err} Map 从未进入响应）；剩 1 枚为引擎执行结果直出（SQL 行列 / Python/R 输出，
 *       结构随语言不同，属 runtime 运行器契约）。键面由 {@code WorkbookWireShapeTest} 2 例钉住，
 *       变异实红（{@code timestamp}→created_at）；{@code workspace-impl} 全量 **391/0/0/3**。</li>
 *   <li>1343 ＝ #147EF（2026-11-05）HEAD 面复测值，差额 −8 行来自 gateway 遥测面
 *       {@code TelemetryController}（12→4）：健康检查 6 键（成功腿含 {@code spans_stored/
 *       token_records}，异常腿两计数省略、{@code db} 落 error 文案）与 Token 汇总
 *       （{@code range[/totals][/by_model][/by_operation][/error]}）改 record；两段聚合结构为
 *       遥测存储原样形态，按 {@code Object} 透传。剩 4 枚为 trace 列表/详情（Span 行动态列，
 *       属遥测存储契约）。键面由 {@code TelemetryWireShapeTest} 2 例钉住（成功/异常两腿），
 *       变异实红（{@code by_model}→byModel）。</li>
 *   <li>1335 ＝ #147EG（2026-11-05）HEAD 面复测值，差额 −8 行来自 ontology 域
 *       {@code AutoDiscoverPreviewController}（10→2）：预览条目改
 *       {@code AutoDiscoverEntityPreviewVO}——**两腿同表异键**（可发现腿 8 键 / 跳过腿 4 键，
 *       条件键 NON_NULL），回执 7 键改 {@code AutoDiscoverPreviewVO}；建条目 Map 收口到
 *       VO 工厂；剩 2 枚为自由请求体与列定义缓存（data-engine REST 行）。既有
 *       {@code AutoDiscoverPreviewProbeCarryTest} 5 例（503 载体/404 分态/不过头）改泛型后复跑绿；
 *       新增 {@code AutoDiscoverPreviewWireShapeTest} 2 例钉两腿键面，变异实红
 *       （{@code fieldCount}→field_count）；{@code ontology-engine-impl} 全量 **306/0/0/0**。</li>
 *   <li>1324 ＝ #147EH（2026-11-05）HEAD 面复测值，差额 −11 行来自 ai 域
 *       {@code AgentEvalController}（12→1）：启动回执 3 键、摘要 9 键、列表 2 键、报告
 *       （10 基键 + {@code dimensionAvgs/items/hint} 条件尾键）、单题明细
 *       （10 键 + {@code dimensions} 条件尾键，{@code rawScore} 两位小数）改 record；
 *       删除两枚只被自身调用的私有 Map 构造器；剩 1 枚为自由请求体。键面由
 *       {@code AgentEvalWireShapeTest} 3 例钉住，变异实红（{@code dimensions}→dimension_scores）；
 *       {@code ai-engine-impl} 全量 **673/0/0/0**；源码面基线按增面 275 → 281。</li>
 *   <li>1314 ＝ #147EI（2026-11-05）HEAD 面复测值，差额 −10 行来自 kb 域
 *       {@code GraphSyncController}（13→3）：触发回执两腿（全量 3 键 / 单类型 4 键，
 *       {@code objectType} 条件键）、同步状态 2 键、dry-run 预览 7 键、按 job 回滚 3 键改 record；
 *       剩 3 枚为同步日志/任务列表/单 job 日志（{@code kg_sync_log} 行集合，属遥测存储契约）。
 *       键面由 {@code KgSyncWireShapeTest} 3 例钉住，变异实红（{@code ontologyVersions}→versions）；
 *       {@code kb-engine-impl} 全量 **360/0/0/2**（2 例既有 skip）。</li>
 *   <li>1301 ＝ #147EJ（2026-11-05）HEAD 面复测值，差额 −13 行来自 cognitive 域
 *       {@code CognitivePipelineController}（13→0）：创建回执 3 键、列表项 5 键、详情 7 键、
 *       执行回执 4 键、执行状态 7 键（{@code endTime} 运行中为 null 但保留键）改 record；
 *       创建请求体改 {@code CognitivePipelineCreateDTO}（节点 {@code config} 为任意 JSON，
 *       以 {@code Object} 承接、{@code toString} 落库语义与旧 Map 取值逐字一致；
 *       {@code dependsOn} 列表原样）。节点 ID/类型校验与错误文案不变。键面由
 *       {@code CognitivePipelineWireShapeTest} 3 例钉住（含 null 键保留、请求体解析），
 *       变异实红（{@code nodeStatuses}→nodeStatusesX）；{@code cognitive-engine-impl} 全量
 *       **558/0/0/0**。</li>
 *   <li>1291 ＝ #147EK（2026-11-05）HEAD 面复测值，差额 −10 行来自 cognitive 域
 *       {@code ScenarioController}（13→3）：演示模板条目 5 键（含 {@code demo=true}）与
 *       对比条目 4 键（{@code predictions} 为推演服务原样结构）改 record；对比请求体改
 *       {@code ScenarioCompareRequest}（{@code variables} 缺省空映射语义不变）。剩 3 枚为
 *       {@code /simulate} 自由请求体与 overrides 元素解析（保留泛型 Map，模式判别含自定义
 *       400 文案）。键面与请求体解析由 {@code ScenarioWireShapeTest} 2 例钉住，
 *       变异实红（{@code demo}→demoFlag）；既有 {@code CognitiveStubHonestyTest} 的模板
 *       断言改读 {@code demo()}（钉死语义不变），复跑全绿；{@code cognitive-engine-impl}
 *       全量 **560/0/0/0**；反退化下界同批 1300 → **1200**（清退跨整百线）。</li>
 *   <li>1284 ＝ #147EL（2026-11-05）HEAD 面复测值，差额 −7 行来自 ontology 域
 *       {@code OntologyExportController}（16→9）：内存 store 由「内部 Map → convertValue 转 VO」
 *       改为直存 {@code OntologyExportTaskVO}（{@code payload} 仍为 {@code Object} 动态结构豁免），
 *       删除两条只做同一转换的私有方法；成功腿 8 键 / 失败腿 8 键（{@code error} 出现、
 *       {@code payload} 省略）由 {@code OntologyExportWireShapeTest} 2 例钉住，
 *       并断言「直存 VO」与「旧 Map→convertValue」两条链路逐字段等价；变异实红
 *       （去掉 VO 的 {@code @JsonInclude(NON_NULL)} ⇒ 两腿均多出 {@code payload/error} 空键），
 *       还原后零残留；{@code ontology-engine-impl} 全量 **308/0/0/0**。剩 9 枚为导出载荷
 *       构建面（{@code ontologyService} 行集合与 JSON 载荷 Map，属 payload 动态结构豁免）。</li>
 *   <li>1281 ＝ #147EN（2026-11-05）HEAD 面复测值，差额 −3 行来自 datanet 域
 *       {@code DataLakeController}（12→9）：查询回执（sql/row_count/data，{@code row_count}
 *       下划线键 + 键序）与健康检查（duckdb/minio/[minio_http]/version，探针键条件出现）
 *       改嵌套 record；行数据、duckdb 探针与 minio 探活结果仍以 {@code Object} 原样透传
 *       （runtime 运行期结构）。键面与条件键由 {@code DataLakeWireShapeTest} 2 例钉住，
 *       变异实红（{@code row_count}→rowCount ⇒ 键名与键序双变）后还原零残留；
 *       {@code datanet} 全量 **37/0/0/0**。剩 9 枚为导出/查询入参自由体与
 *       {@code runtime} 存储探活 Map（属主契约）。</li>
 *   <li>1278 ＝ #147EO（2026-11-05）HEAD 面复测值，差额 −3 行来自 buszhi 域
 *       {@code WorkflowInstanceApprovalController}（7→4）：实例审批成功腿的读回行
 *       8 键（id/taskId/instanceId/approver/decision/opinion/formData/createdAt）改嵌套
 *       record {@code WorkflowApprovalRowVO}（{@code formData} 保持 JSONB 文本、
 *       {@code createdAt} 保持 {@code LocalDateTime}）；既有
 *       {@code WorkflowInstanceApprovalContractTest} 改读 record 访问器，键序断言改按
 *       record 组件声明序（与 Jackson wire 序同源）钉同一 8 键，判据未放宽；9 例复跑绿。
 *       变异实红（交换 taskId/instanceId 组件序 ⇒ 值错位 `expected: <task-1> but was: <inst-1>`），
 *       还原后零残留；{@code buszhi-impl} 全量 **29/0/0/0**。剩 4 枚为审批请求体与
 *       客户端身份键剥离函数（入参自由面）。</li>
 *   <li>1276 ＝ #147EQ（2026-11-05）HEAD 面复测值，差额 −2 行来自 gateway 配置面
 *       {@code SysConfigController}（7→5）：{@code PUT /{key}/reset} 回执 4 键
 *       （configKey/resetValue/previousValue/updated，{@code previousValue} 可为 null
 *       但保留键）改嵌套 record {@code ResetResultVO}；审计事件载荷（sys_config_reset）
 *       仍为事件总线动态结构豁免。键面与 null 键保留由 {@code SysConfigResetWireShapeTest}
 *       1 例钉住，变异实红（{@code resetValue}→resetValueX）后还原零残留；
 *       既有 {@code SysConfigAuditEgressTest} 7 例复跑绿（共 8/0/0/0）。</li>
 *   <li>1265 ＝ #147ER（2026-11-05）HEAD 面复测值，差额 −11 行来自 gateway 图谱面
 *       {@code EcosKnowledgeGraphController}（11→0）：图谱快照（nodes/edges/stats）、
 *       节点 5 键（含 {@code type=OntologyEntity}）、边 4 键（{@code relationshipType}）、
 *       统计 3 键、同步回执 4 键全部改嵌套 record；该控制器为聚合期 dead-twin
 *       （{@code @Deprecated} 且已从组件扫描排除），本批只换载体，handler 集合与
 *       节点/边清单（11/11）一字未动。键面与计数由 {@code EcosKnowledgeGraphWireShapeTest}
 *       2 例钉住（直调控制器断言键序与真实条数），变异实红
 *       （{@code relationshipType}→relType）后还原零残留。</li>
 *   <li>1258 ＝ #147ES（2026-11-05）HEAD 面复测值，差额 −7 行来自 gateway 事件诊断面
 *       {@code SystemEventTopicsV1Controller}（7→0）：topic 清单 6 键（topics/total/dltSuffix/
 *       lagSource/lastProducedAtSource/metricNotes）与单 topic 投影 6 键（name/dltTopic/
 *       lag/lastProducedAt/category/declaredIn）改嵌套 record，{@code trace()} 泛型化为
 *       {@code <T> ApiResponse<T>}；既有 {@code EventTopicsEndpointTest} 9 例逐条改读访问器，
 *       钉值一字未改（BUSINESS 与 COMPOSE_MIRROR 对等、lag=null≠0、NOT_WIRED、DLT 命名、
 *       探活出处、退化 503 不带 data），复跑 **9/0/0/0**；变异实红
 *       （去 {@code DLT_SUFFIX} 拼接 ⇒ dltTopic 与 <topic>.DLT 不符）后还原零残留。</li>
 *   <li>1252 ＝ #147ET（2026-11-05）HEAD 面复测值，差额 −6 行来自 gateway 引擎任务面
 *       {@code EngineTaskController}（8→2）：提交回执 3 键、任务详情 8 键
 *       （{@code progress/startedAt/completedAt} 原样透传）、汇聚行 5 键
 *       （取状态失败腿 UNKNOWN/0 同键面）改嵌套 record；剩 2 枚为提交请求体与
 *       {@code params} 自由参数包。键面与回退腿由 {@code EngineTaskWireShapeTest}
 *       2 例钉住，变异实红（{@code startedAt}→startedAtX）后还原零残留。</li>
 *   <li>1244 ＝ #147EV（2026-11-05）HEAD 面复测值，差额 −8 行来自 gateway 安全中心面
 *       {@code SecurityController}（12→4）：遮蔽回执两腿（成功 3 键 / 失败省略
 *       {@code original_length}，下划线键名）、解密回执 4 键、内存审计行 5 键、
 *       诊断报告 1 键改 record；审计存储改 {@code List<AuditLogVO>}（上限 100 与
 *       种子顺序不变）；剩 4 枚为请求体与行数据自由面。键面与条件键由
 *       {@code SecurityWireShapeTest} 2 例钉住，变异实红
 *       （{@code masked_value}→maskedValue）后还原零残留。</li>
 * </ul>
 *
 * <p><b>记账时必须打折的一枚读数</b>：#147BT 提交时工作树实读 <b>2276</b> = 2265 + 11，
 * 这 11 行<b>全部</b>是并发会话未提交的在途面（{@code ScenarioBlueprintController} 6→7，
 * 以及未跟踪新文件 {@code CognitiveFrameworkController}(4)／{@code CognitiveExecutionController}(4)／
 * {@code CognitiveMethodController}(2)）。按本门「上调基线一律不许」的铁口径，基线取
 * HEAD 面 2265，<b>不</b>为在途面放宽；工作树红在未提交面是门在按其判据正常工作。
 * <b>#147BW~#147CO 时该偏移不变</b>：工作树实读依次 2272 = 2261 + 11、2260 = 2249 + 11、
 * 2240 = 2229 + 11、2226 = 2215 + 11、2204 = 2193 + 11、2194 = 2183 + 11、
 * 2188 = 2177 + 11、2180 = 2169 + 11、2163 = 2152 + 11、2155 = 2144 + 11、
 * 2149 = 2138 + 11、2132 = 2121 + 11、2104 = 2093 + 11、2068 = 2057 + 11、
 * 2038 = 2027 + 11、2023 = 2012 + 11、2007 = 1996 + 11、1988 = 1977 + 11、1973 = 1962 + 11、1963 = 1952 + 11、1958 = 1947 + 11、1949 = 1938 + 11、1930 = 1919 + 11、1912 = 1901 + 11、1890 = 1879 + 11、1876 = 1865 + 11、1867 = 1856 + 11、1821 = 1810 + 11、1799 = 1788 + 11、1777 = 1766 + 11、1769 = 1758 + 11、1764 = 1753 + 11、1734 = 1723 + 11、1710 = 1699 + 11、1700 = 1689 + 11、1690 = 1679 + 11、1670 = 1659 + 11、1651 = 1640 + 11、1640 = 1629 + 11、1621 = 1610 + 11、1613 = 1602 + 11、1597 = 1586 + 11、1582 = 1571 + 11、1572 = 1561 + 11、1564 = 1553 + 11、1553 = 1542 + 11、1528 = 1517 + 11、1510 = 1499 + 11、1473 = 1462 + 11、1468 = 1457 + 11、1455 = 1444 + 11、1438 = 1427 + 11、1424 = 1413 + 11、1410 = 1399 + 11、1397 = 1386 + 11、1381 = 1370 + 11、1375 = 1364 + 11、1362 = 1351 + 11、1354 = 1343 + 11、1346 = 1335 + 11、1335 = 1324 + 11、1325 = 1314 + 11、1312 = 1301 + 11、1302 = 1291 + 11、1295 = 1284 + 11、1292 = 1281 + 11、1289 = 1278 + 11、1287 = 1276 + 11、1276 = 1265 + 11、1269 = 1258 + 11、1263 = 1252 + 11、1255 = 1244 + 11（同一批在途文件，未代改）。</p>
 *
 * <p><b>本门红了的正确处置</b>：只允许「把新增的 {@code Map<String,Object>} 改成 DTO/VO」或
 * 「同批给出净清退并把 {@link #BASELINE_LINES} 调低」。{@code Map<String,Object>} 出入参是
 * DoD DI-2 的违例本体，<b>上调基线一律不许</b>；共享工作树里若红差来自<b>他人未提交</b>的在途
 * 新增面，归因写进本报告即可，禁代改他人文件、更禁为其放宽基线。</p>
 *
 * @author ECOS Gateway #147AC/DI-2
 */
class ControllerMapPayloadRatchetTest {

    /**
     * HEAD 面实扫基线（行级命中）。只允许下调。
     * <p>#147CA 下调依据见类注释「基线台账」：2215 → 2193，−22 行来自 sysman 系统配置面。</p>
     */
    private static final int BASELINE_LINES = 1244;

    /** 扫描面下限（防扫描根漂移把门变成空转）。 */
    private static final int MIN_FILES = 200;
    // 反退化下界（非目标值）：清退跨过整百线时同步下调，防止扫描面塌成空集仍判绿。
    private static final int MIN_LINES = 1200;

    private static final Pattern MAP_PAYLOAD = Pattern.compile("Map<String,\\s*Object>");

    @Test
    @DisplayName("Controller 的 Map<String,Object> 行数不得超过基线 1244（只减不增）")
    void controllerMapPayloadDoesNotGrow() throws IOException {
        List<String> offenders = new ArrayList<>();
        int lines = 0;
        int files = 0;

        try (Stream<Path> s = Files.walk(repoRoot().resolve("ecos_backend"))) {
            List<Path> controllers = s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith("Controller.java"))
                    .filter(p -> !p.toString().contains("target"))
                    .filter(p -> !p.toString().contains("archive"))
                    .filter(p -> p.toString().contains("src" + java.io.File.separator + "main"))
                    .toList();
            files = controllers.size();
            for (Path file : controllers) {
                for (String line : Files.readAllLines(file)) {
                    if (MAP_PAYLOAD.matcher(line).find()) {
                        lines++;
                        if (offenders.size() < 20) {
                            offenders.add(file.getFileName() + ": " + line.trim());
                        }
                    }
                }
            }
        }

        assertTrue(files >= MIN_FILES,
                "扫描面退化：只看到 " + files + " 个 Controller（基线 " + MIN_FILES + "）");
        assertTrue(lines >= MIN_LINES,
                "命中面退化：只看到 " + lines + " 行 Map<String,Object>（基线 " + MIN_LINES + "）"
                        + "——若确已批量清退，请同批把 BASELINE_LINES 调低并留证据");
        assertTrue(lines <= BASELINE_LINES,
                "弱类型出入参在增长：" + lines + " 行 > 基线 " + BASELINE_LINES
                        + "（新增代码请用强类型 DTO/VO；样例行：\n" + String.join("\n", offenders) + "）");
    }

    @Test
    @DisplayName("反证：判据能变红（合成样例）且容忍空格变体")
    void predicateCatchesSpacingVariants() {
        assertTrue(MAP_PAYLOAD.matcher("public ApiResponse<Map<String,Object>> f() {").find());
        assertTrue(MAP_PAYLOAD.matcher("List<Map<String, Object>> rows = ...").find());
        assertTrue(!MAP_PAYLOAD.matcher("Map<String, String> m").find());
        assertTrue(!MAP_PAYLOAD.matcher("Map<String, MyDto> m").find());
    }

    /** Surefire CWD 漂移：向上探测同时含 ecos_backend/ 与 ecos-docker/ 的仓库根。 */
    private static Path repoRoot() {
        Path cur = Paths.get("").toAbsolutePath().normalize();
        for (int i = 0; i < 8 && cur != null; i++) {
            if (Files.isDirectory(cur.resolve("ecos_backend")) && Files.isDirectory(cur.resolve("ecos-docker"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("未找到仓库根，CWD=" + Paths.get("").toAbsolutePath());
    }
}
