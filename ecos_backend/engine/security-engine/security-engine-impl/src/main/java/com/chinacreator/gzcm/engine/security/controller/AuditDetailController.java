package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.SystemErrorCode;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.engine.security.dao.AuditDao;
import com.chinacreator.gzcm.engine.security.service.AuditHashChainService;
import com.chinacreator.gzcm.sysman.audit.service.IAuditLogService;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 附录A 接口总表第 6 条（getAuditLogDetail，审计详情含链哈希）的契约路径端点（#146ab）。
 *
 * <p><b>与既有 {@link AuditController} 的关系（遮蔽面如实登记，不写「正交」）</b>：本类锚定设计文档字面
 * {@code GET /api/v1/security/audit/{id}}（getAuditLogDetail），而既有宿主 {@link AuditController}
 * 声明 {@code @RequestMapping({"/api/v1/audit", "/api/v1/security/audit"})}，其下另有
 * {@code GET /logs}、{@code GET /logs/{id}}、{@code GET /stats}、{@code GET /verify-integrity}、
 * {@code POST /verify-chain}、{@code POST /log}。因此本类的<b>模式</b> {@code /audit/{id}} 在
 * {@code /api/v1/security/audit} 前缀下与 {@code /audit/logs}、{@code /audit/stats}、
 * {@code /audit/verify-integrity} <b>字面重叠</b>（{@code id} 可取值为 "logs"）——这不是两棵互不相交的
 * 子树。判定依据是 Spring 的匹配特异性规则：字面量段优先于变量段，故 {@code GET /audit/logs}
 * 仍由 {@link AuditController} 服务，{@code GET /audit/{任意非 logs 字面量}} 落到本类；两者不构成
 * 注册期 ambiguous mapping 异常。判序证据取自本批行为门 {@code AuditDetailControllerContractTest
 * #literalSegmentWinsOverVariableSegment}（standaloneSetup 同时挂载本类与复刻既有宿主形态的探针，
 * 读 {@code MvcResult.getHandler()} 判处理器身份），并由 gateway {@code ApiPathUniquenessTest}
 * 在全量映射面上复验；<b>live 启动面本批未复验</b>。
 * 该重叠的<b>后果面</b>：传入 {@code id="logs"} 的请求语义上属既有列表端点，本类永远拿不到，
 * 而 {@code id="stats"} 之类会被本类按 400 {@code AUDIT_ID_FORMAT_INVALID} 拒——这是契约路径
 * 与设计文档字面的固有歧义，如实登记而不伪装成已消除。零改写 {@link AuditController} 源码
 * （API 只增不改红线）。</p>
 *
 * <p><b>数据面</b>：行 6 曾是 #146q「纯代码 P1 批不擅自固化未入单源的表列」的断链行——契约载荷
 * {@code prevHash}/{@code currentHash} 唯一数据面 {@code prev_hash}/{@code curr_hash} 在 DDL
 * 单源目录零 CREATE 载体。本批动作＝① {@code public.ecos_audit_log} 的单源锚定
 * （V295__ecos_audit_log_anchor.sql，16 列取库面实查）＋ ② 本端点经 {@link AuditDao} 裸名
 * ＋ JDBC currentSchema 链真读该表 ＋ ③ {@code detailJson} 直读存储列 {@code detail}
 * （整列未打戳＝null 即如实 null，不伪装填充值）。三键闭环后行 6 从「偏差」改读「已实现」。</p>
 *
 * <p><b>状态面口径（D-B4-59 第三族＝端点整体折成 {@code ResponseEntity}，同
 * #142/B4-3e-35／3e-37／#146ab 同型属主）</b>：全部失败腿写作
 * {@code return failure(ApiResponse.error(…))}、成功腿写作 {@code return ok(ApiResponse.success(…))}，
 * 状态行一律由 {@link #failure} 从信封 {@code code} 经 {@link DataBridgeException#resolveHttpStatus(int)}
 * 单源推出——本类源码内<b>零枚</b> {@code return ApiResponse.} 裸回出口（该形态会把状态行折回恒 200，
 * 并被 {@code SecurityDenialHttpStatusRatchetTest} 判据 A／B 记为新裸回持有者）：</p>
 * <ul>
 *   <li>200 正例：16 列真读成功；prevHash/currentHash/approvedBy/hashAlgorithm/detailJson/occurredAt
 *       取数列如录在位，null 即 null</li>
 *   <li>400 {@code AUDIT_ID_FORMAT_INVALID}：id 非数字或负数（bigint 主键）</li>
 *   <li>403 {@code PERMISSION_DENIED}：{@link UserContext} 无已鉴别主体（L2 门禁，fail-closed）。
 *       载体是 sysman 侧 ThreadLocal（由 {@code JwtAuthenticationFilter} 填充）而非 Spring
 *       {@code SecurityContext}——本批首跑实测把这条<b>判定顺序</b>钉了出来：L2 门禁<b>先于</b>
 *       id 形态校验与 DAO 读腿，故无主体时非法 id 仍报 403 而非 400（否则未鉴别调用方可用
 *       状态行差探测校验顺序）。live 侧该 ThreadLocal 是否在本引擎独立进程 :18081 被填充
 *       本批未复验，属如实登记的开放项。</li>
 *   <li>404 {@code AUDIT_LOG_NOT_FOUND}：真读库面 0 行（契约逐字「不存在才 404」）</li>
 *   <li>500 {@code AUDIT_DETAIL_READ_ERROR}：依赖在位但真读 DAO 抛异常——不报 404
 *       （DB 故障与「查无此行」不得同文）</li>
 *   <li>503 承流：依赖 Bean（IAuditLogService／AuditHashChainService）任一缺席 ⇒ 抛
 *       {@link DataBridgeException} 携带 {@link RlsExecutionContract#SEC_UNAVAILABLE_HTTP}
 *       ＋机读码 {@code ECOS-SYS-503}（D-B4-86 同源族；抛型而非信封，族差同 3e-37 末腿）</li>
 * </ul>
 *
 * <p><b>响应体口径</b>：固定 7 机读键（id/prevHash/currentHash/approvedBy/hashAlgorithm/detailJson/
 * occurredAt）＋ 6 描述键（action/resource/entityType/entityId/userId/result）。取 null 即 null，
 * 展示文案走前端 i18n，响应体不持中文文案键值。</p>
 *
 * @see AuditController 既有实现路径宿主（零改写约束）
 * @see AuditDao#findAuditDetailById(long) 本端点 DAO 腿（本批新增）
 */
@RestController
@RequestMapping("/api/v1/security")
public class AuditDetailController {

    private static final Logger log = LoggerFactory.getLogger(AuditDetailController.class);

    static final String ERR_ID_FORMAT = "AUDIT_ID_FORMAT_INVALID";
    static final String ERR_NOT_FOUND = "AUDIT_LOG_NOT_FOUND";
    static final String ERR_PERMISSION = "PERMISSION_DENIED";
    static final String ERR_READ = "AUDIT_DETAIL_READ_ERROR";
    static final String CODE_UNAVAILABLE = SystemErrorCode.ECOS_SYS_503;

    private final ObjectProvider<IAuditLogService> auditLogProvider;
    private final ObjectProvider<AuditHashChainService> hashChainProvider;
    private final AuditDao auditDao;

    public AuditDetailController(ObjectProvider<IAuditLogService> auditLogProvider,
                                 ObjectProvider<AuditHashChainService> hashChainProvider,
                                 AuditDao auditDao) {
        this.auditLogProvider = auditLogProvider;
        this.hashChainProvider = hashChainProvider;
        this.auditDao = auditDao;
    }

    /**
     * GET /api/v1/security/audit/{id} — 审计详情含链哈希。
     *
     * @param idPath 路径变量，bigint 主键（数字且非负；否则 400 {@code AUDIT_ID_FORMAT_INVALID}）
     */
    @GetMapping("/audit/{id}")
    public ResponseEntity<ApiResponse<?>> getDetail(@PathVariable("id") String idPath) {
        if (UserContext.getCurrentUserId() == null && UserContext.getCurrentUsername() == null) {
            return permissionDenied();
        }
        return doRead(idPath);
    }

    static ResponseEntity<ApiResponse<?>> permissionDenied() {
        return failure(ApiResponse.<Void>error(403, ERR_PERMISSION,
                "无已鉴别主体，审计详情按 L2 门禁 fail-closed 拒绝"));
    }

    static ResponseEntity<ApiResponse<?>> idInvalid(String rawId) {
        return failure(ApiResponse.<Void>error(400, ERR_ID_FORMAT,
                "audit id 必须是数字（落入 bigint 主键）且非负：\"" + rawId + "\""));
    }

    static ResponseEntity<ApiResponse<?>> notFound(String rawId) {
        return failure(ApiResponse.<Void>error(404, ERR_NOT_FOUND,
                "审计日志 \"" + rawId + "\" 不存在"));
    }

    ResponseEntity<ApiResponse<?>> doRead(String idPath) {
        if (auditLogProvider.getIfAvailable() == null || hashChainProvider.getIfAvailable() == null) {
            throw new DataBridgeException(RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                    RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                    CODE_UNAVAILABLE + ": 审计详情控制器依赖（IAuditLogService / AuditHashChainService）"
                            + " 未装配，按 fail-closed 拒绝服务（§4.7.2 不可用 ≠ 无权限 ≠ 空结果）");
        }
        long idNum;
        try {
            idNum = Long.parseLong(idPath.trim());
        } catch (NumberFormatException e) {
            return idInvalid(idPath);
        }
        if (idNum < 0L) {
            return idInvalid(idPath);
        }
        Map<String, Object> row;
        try {
            row = auditDao.findAuditDetailById(idNum);
        } catch (Exception e) {
            log.error("audit/{id} 真读 DAO 异常（依赖在位但读腿异常，不折成 404）: id={}", idNum, e);
            return failure(ApiResponse.<Void>error(500, ERR_READ,
                    "审计详情读腿异常，按不可用 ≠ 空结果处理"));
        }
        if (row == null) {
            return notFound(idPath);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", idNum);
        data.put("prevHash", row.get("prev_hash"));
        data.put("currentHash", row.get("curr_hash"));
        data.put("approvedBy", row.get("username"));
        data.put("hashAlgorithm", row.get("hash_algorithm"));
        data.put("detailJson", row.get("detail"));
        Object createdAt = row.get("created_at");
        data.put("occurredAt", createdAt == null ? null : createdAt.toString());
        data.put("action", coalesceStr(row.get("operation"), row.get("action")));
        data.put("resource", row.get("resource"));
        data.put("entityType", row.get("entity_type"));
        data.put("entityId", row.get("entity_id"));
        data.put("userId", row.get("user_id"));
        data.put("result", row.get("result"));
        return ok(ApiResponse.success(data));
    }

    private static String coalesceStr(Object primary, Object fallback) {
        if (primary != null && !primary.toString().isBlank()) return primary.toString();
        if (fallback != null && !fallback.toString().isBlank()) return fallback.toString();
        return null;
    }

    /**
     * 成功腿唯一出口：显式 200。{@code CODE_SUCCESS=0 ∉ [400,599]}，若经
     * {@link DataBridgeException#resolveHttpStatus(int)} 会被折成 500＝把成功判成失败。
     *
     * <p>载荷面取通配 {@code ApiResponse<?>} 而非类型参数 {@code <T>}：端点声明是
     * {@code ResponseEntity<ApiResponse<?>>}，而 {@code ResponseEntity} 不变型，
     * {@code ResponseEntity<ApiResponse<T>>} 推不出通配面（首次实跑即以此编译红暴露）。
     */
    static ResponseEntity<ApiResponse<?>> ok(ApiResponse<?> body) {
        return ResponseEntity.ok(body);
    }

    /**
     * 失败腿唯一状态行通路：状态行从信封 {@code code} 经
     * {@link DataBridgeException#resolveHttpStatus(int)} 单源推出，本类不写第二处状态字面量。
     */
    static ResponseEntity<ApiResponse<?>> failure(ApiResponse<?> body) {
        int status = DataBridgeException.resolveHttpStatus(body.getCode());
        return ResponseEntity.status(status).body(body);
    }
}
