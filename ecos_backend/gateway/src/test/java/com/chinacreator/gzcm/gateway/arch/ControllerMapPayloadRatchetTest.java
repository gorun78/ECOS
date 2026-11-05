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
 * 2038 = 2027 + 11、2023 = 2012 + 11、2007 = 1996 + 11、1988 = 1977 + 11、1973 = 1962 + 11、1963 = 1952 + 11、1958 = 1947 + 11、1949 = 1938 + 11、1930 = 1919 + 11、1912 = 1901 + 11、1890 = 1879 + 11（同一批在途文件，未代改）。</p>
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
    private static final int BASELINE_LINES = 1879;

    /** 扫描面下限（防扫描根漂移把门变成空转）。 */
    private static final int MIN_FILES = 200;
    // 反退化下界（非目标值）：清退跨过整百线时同步下调，防止扫描面塌成空集仍判绿。
    private static final int MIN_LINES = 1800;

    private static final Pattern MAP_PAYLOAD = Pattern.compile("Map<String,\\s*Object>");

    @Test
    @DisplayName("Controller 的 Map<String,Object> 行数不得超过基线 1879（只减不增）")
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
