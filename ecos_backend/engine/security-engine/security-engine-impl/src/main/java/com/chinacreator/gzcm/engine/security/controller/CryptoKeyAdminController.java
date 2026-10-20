package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.engine.security.crypto.KeyManagementService.KeyManagementException;
import com.chinacreator.gzcm.engine.security.crypto.service.CryptoKeyRegistryService;
import com.chinacreator.gzcm.engine.security.service.SecurityAuditService;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import com.chinacreator.gzcm.sysman.security.MinimumClearance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 详设-01 §4.3.1 L2585-2586（#142/B4-1）密钥登记与轮换端点 —— 诚实实现。
 *
 * <p><b>契约逐条落点</b>：
 * <ol>
 *   <li>{@code GET /api/v1/security/crypto/keys}（{@code listCryptoKeys}，L4，query {@code status}）
 *       → {@code {items[{keyId,algorithm,keyVersion,status,masterKeyRef,rotateAt,retiredAt}], total}}，
 *       <b>永不返回密钥材料</b>：响应由 {@link CryptoKeyRegistryService#project} 逐字段投影，
 *       七键之外无任何字段，且本端点从不调 {@code getKey/getKeyBytes}
 *       （单测 {@code CryptoKeyAdminContractTest} 把键集合与「材料字段零出现」钉死）；</li>
 *   <li>{@code POST /api/v1/security/crypto/keys/{keyId}/rotate}（{@code rotateCryptoKey}，
 *       <b>L4 且二次确认</b>，body {@code {reason}}）→ {@code {keyId,newKeyVersion,retiredVersion}}；
 *       主密钥缺失 → 500 {@code ECOS-SEC-430}（§4.3.3「禁静默建钥」）。</li>
 * </ol>
 *
 * <p><b>二次确认取档依据</b>：契约只写「L4 且二次确认」，未写确认失败的状态码。
 * 本端点沿用同域既有范式 {@link ImpersonateController}（{@code confirm!=true} → HTTP 428
 * {@code PRECONDITION_REQUIRED} + errorCode，前端据 errorCode 弹确认框后带 {@code confirm=true} 重试）。
 * 方向为 <b>deny 收紧</b>，不违反「API 只增不改」（本路径此前无 Controller，属纯新增面）。
 * 与 impersonate 的差别：impersonate 确认过仍 501（能力底座不存在），本端点底座
 * （{@code KeyManagementService#rotateKey}）真实存在，确认后执行真轮换。</p>
 *
 * <p><b>428/400/500/404/503 分支全部写审计</b>（action {@code CRYPTO_KEY_ROTATE}，
 * 含服务端已认证主体、目标 keyId、结果与原因摘要），满足 L4 操作面「强审计留痕」；
 * 审计失败只 WARN 不阻断响应（与 {@link ImpersonateController} 同口径）。</p>
 *
 * <p><b>三滤波器</b>：Controller 直接映射 {@code /api/security/crypto} 与
 * {@code /api/v1/security/crypto} 双路径（与 {@link RlsController}/{@link ImpersonateController} 同风格），
 * VersionPrefixRewriteFilter 无需登记；前缀 {@code /api/v1/security/} 已被 SecurityConfig
 * 认证面与 AnonymousEndpointRegistry 覆盖，非匿名。方法级 {@link MinimumClearance}(4)
 * 由 {@code ClearanceInterceptor.resolveRequiredLevel} 优先于路径规则（路径默认 L2）取用，
 * 故 L4 门槛真实生效；档位不足由拦截器统一出 403。</p>
 *
 * <p><b>数据面现状（不谎称收口）</b>：容器内 {@code KeyManagementService} 实现是
 * {@code KeyManagementServiceFullImpl}（纯内存），{@code ecos_security_crypto_key}（V165）
 * 无 DAO/Mapper。因此未建钥的干净 JVM 上 {@code GET /keys} 返回<b>空清单</b>属真实状态；
 * 「密钥台账落库」登记为差距文档 B4-1 后续收口项，本批不用假数据填充。</p>
 */
@RestController
@RequestMapping({"/api/security/crypto", "/api/v1/security/crypto"})
public class CryptoKeyAdminController {

    private static final Logger log = LoggerFactory.getLogger(CryptoKeyAdminController.class);

    /** 428：轮换缺二次确认（详设「L4 且二次确认」的失败分支，前端据此弹确认框）。 */
    public static final String ERROR_ROTATE_CONFIRM_REQUIRED = "CRYPTO_KEY_ROTATE_CONFIRM_REQUIRED";

    /** §4.3.3 L2600+：security-engine 底座不可用 → 503，操作 DENY（码单源见 {@link RlsExecutionContract#SEC_UNAVAILABLE_CODE}）。 */
    public static final String ERROR_SEC_UNAVAILABLE = RlsExecutionContract.SEC_UNAVAILABLE_CODE;

    /** §4.3.3：加密主密钥缺失 → 500 {@code ECOS-SEC-430}（单源取 service 常量，禁两处漂移）。 */
    public static final String ERROR_MASTER_KEY_MISSING =
            CryptoKeyRegistryService.ERROR_MASTER_KEY_MISSING;

    /** 审计动作名（单测与查询面 {@code action} 过滤共用同一常量）。 */
    public static final String AUDIT_ACTION = "CRYPTO_KEY_ROTATE";

    private final CryptoKeyRegistryService registryService;
    private final SecurityAuditService securityAuditService;

    public CryptoKeyAdminController(CryptoKeyRegistryService registryService,
                                    SecurityAuditService securityAuditService) {
        this.registryService = registryService;
        this.securityAuditService = securityAuditService;
    }

    /**
     * GET /api/v1/security/crypto/keys —— L4，只出登记元数据，永不出密钥材料。
     *
     * <p>契约返回形态是 {@code {items[...]}}；此处并列给出 {@code total} 供分页消费方核对，
     * 属「只增」字段，不改变 items 语义。</p>
     */
    @GetMapping("/keys")
    @MinimumClearance(level = 4)
    public ResponseEntity<ApiResponse<Map<String, Object>>> listKeys(
            @RequestParam(required = false) String status) {
        String traceId = resolveTraceId();
        try {
            List<Map<String, Object>> items = registryService.listKeyRegistrations(status);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("items", items);
            out.put("total", items.size());
            out.put("statusFilter", status);
            // 诚实标注台账面：底座是内存实现，清单为空不等于「平台无密钥治理」，
            // 而是「当前 JVM 未建钥/台账未落库」，避免空列表被误读成收口完成。
            out.put("registrySource", "IN_MEMORY_KEY_MANAGEMENT_SERVICE");
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>success(out)
                    .withTraceId(traceId));
        } catch (KeyManagementException | RuntimeException e) {
            log.warn("密钥清单查询失败（fail-closed，不返回空清单冒充成功）: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.<Map<String, Object>>error(
                            HttpStatus.SERVICE_UNAVAILABLE.value(), ERROR_SEC_UNAVAILABLE,
                            "密钥登记面不可用，操作已拒绝")
                            .withTraceId(traceId));
        }
    }

    /**
     * POST /api/v1/security/crypto/keys/{keyId}/rotate —— L4 且二次确认，body {@code {reason, confirm}}。
     *
     * <p>分支顺序（每一步都可能中止，中止即写审计）：
     * reason 缺失 → 400；{@code confirm!=true} → 428；主密钥缺失 → 500 {@code ECOS-SEC-430}
     * （<b>不调用 rotateKey，即不建任何新钥</b>）；keyId 未登记 → 404；底座异常 → 503；
     * 成功 → 200 {@code {keyId,newKeyVersion,retiredVersion}}。</p>
     */
    @PostMapping("/keys/{keyId}/rotate")
    @MinimumClearance(level = 4)
    public ResponseEntity<ApiResponse<Map<String, Object>>> rotate(
            @PathVariable String keyId,
            @RequestBody(required = false) Map<String, Object> body) {

        Map<String, Object> req = body != null ? body : Map.of();
        String reason = asString(req.get("reason"));
        boolean confirmed = Boolean.TRUE.equals(req.get("confirm"))
                || "true".equalsIgnoreCase(asString(req.get("confirm")));
        String actor = resolveActor();
        String traceId = resolveTraceId();

        if (keyId == null || keyId.isBlank()) {
            audit(actor, keyId, "REJECTED_BAD_REQUEST", "missing keyId");
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Map<String, Object>>badRequest("keyId 必填")
                            .withTraceId(traceId));
        }

        // 契约 body 明确要求 reason（轮换属 L4 高危操作，无因由不留痕即拒）
        if (reason == null || reason.isBlank()) {
            audit(actor, keyId, "REJECTED_BAD_REQUEST", "missing reason");
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Map<String, Object>>badRequest("reason 必填：密钥轮换必须登记原因")
                            .withTraceId(traceId));
        }

        if (!confirmed) {
            // ① 二次确认缺失 → 428（对齐 ImpersonateController 范式；未执行任何轮换）
            audit(actor, keyId, "REJECTED_CONFIRM_REQUIRED", reason);
            log.warn("密钥轮换拒绝（缺二次确认）: actor={} keyId={} traceId={}", actor, keyId, traceId);
            Map<String, Object> hint = new LinkedHashMap<>();
            hint.put("requiredFlag", "confirm=true");
            hint.put("keyId", keyId);
            hint.put("message", "密钥轮换属 L4 高危操作，必须显式携带 confirm=true（二次确认）");
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                    .body(ApiResponse.<Map<String, Object>>error(
                            HttpStatus.PRECONDITION_REQUIRED.value(),
                            ERROR_ROTATE_CONFIRM_REQUIRED,
                            "密钥轮换需要二次确认：请显式携带 confirm=true")
                            .withTraceId(traceId));
        }

        try {
            // ② 已确认：真轮换（缺主密钥时 service 先抛，底座 rotateKey 根本不会被调用）
            Map<String, Object> rotated = registryService.rotate(keyId, actor, reason);
            audit(actor, keyId, "SUCCESS",
                    "reason=" + reason + " newKeyVersion=" + rotated.get("newKeyVersion")
                            + " retiredVersion=" + rotated.get("retiredVersion"));
            log.info("密钥轮换执行: actor={} keyId={} newKeyVersion={} retiredVersion={}",
                    actor, keyId, rotated.get("newKeyVersion"), rotated.get("retiredVersion"));
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>success(rotated)
                    .withTraceId(traceId));
        } catch (CryptoKeyRegistryService.MasterKeyMissingException e) {
            // ③ §4.3.3 ECOS-SEC-430 = 500「加密主密钥缺失，禁静默建钥」
            audit(actor, keyId, "REJECTED_MASTER_KEY_MISSING", reason);
            log.error("密钥轮换拒绝（主密钥缺失，未建钥）: actor={} keyId={} traceId={}",
                    actor, keyId, traceId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<Map<String, Object>>error(
                            HttpStatus.INTERNAL_SERVER_ERROR.value(), ERROR_MASTER_KEY_MISSING,
                            "加密主密钥缺失，已拒绝静默建钥（平台侧 critical）")
                            .withTraceId(traceId));
        } catch (CryptoKeyRegistryService.KeyNotRegisteredException e) {
            audit(actor, keyId, "REJECTED_NOT_REGISTERED", reason);
            // 契约 §4.3.3 的 ECOS-SEC-* 族未定义 404 成员（该表只覆盖 501/502/401/402/410/411/412/420/430/440），
            // 故此处不私造码：沿用 ApiResponse.notFound 的裸 404 形态，
            // 「errorCode 统一包络（含 404 是否补码）」属 B4-2 错误码族统一批的裁决面，已在差距文档登记。
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.<Map<String, Object>>notFound(
                            "密钥未登记，无可轮换版本: " + keyId)
                            .withTraceId(traceId));
        } catch (KeyManagementException e) {
            audit(actor, keyId, "FAILED_ENGINE_UNAVAILABLE", reason);
            log.warn("密钥轮换失败（底座异常）: keyId={} error={}", keyId, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.<Map<String, Object>>error(
                            HttpStatus.SERVICE_UNAVAILABLE.value(), ERROR_SEC_UNAVAILABLE,
                            "密钥引擎不可用，轮换已拒绝")
                            .withTraceId(traceId));
        }
    }

    /** 强审计留痕：无论哪个分支（含成功与被拒），均写一条 action=CRYPTO_KEY_ROTATE。 */
    private void audit(String actor, String keyId, String result, String detail) {
        try {
            String id = UUID.randomUUID().toString().replace("-", "");
            securityAuditService.asyncWrite(id, null, actor, AUDIT_ACTION,
                    "crypto:key:" + keyId, result, detail, null);
        } catch (Exception e) {
            log.warn("密钥轮换审计留痕失败（不阻断响应）: {}", e.getMessage());
        }
    }

    /** 服务端已认证主体（不可得时记 anonymous，不伪装）。 */
    String resolveActor() {
        String userId = UserContext.getCurrentUserId();
        if (userId != null && !userId.isBlank()) return userId;
        String username = UserContext.getCurrentUsername();
        if (username != null && !username.isBlank()) return username;
        return "anonymous";
    }

    /** MDC traceId 优先；缺省生成 t- 前缀 UUID（保证每个分支都带可追踪 id）。 */
    private String resolveTraceId() {
        try {
            String tid = org.slf4j.MDC.get("traceId");
            if (tid != null && !tid.isBlank()) return tid;
        } catch (Exception ignored) {
            // MDC 不可用属平台面差异，降级为自造 traceId，不影响主流程
        }
        return "t-" + UUID.randomUUID();
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
