package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.sysman.audit.model.AuditEvent;
import com.chinacreator.gzcm.sysman.audit.service.IAuditLogService;
import com.chinacreator.gzcm.engine.security.service.AuditHashChainService;
import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping({"/api/v1/audit", "/api/v1/security/audit"})
public class AuditController {
    private static final Logger log = LoggerFactory.getLogger(AuditController.class);

    static final String ANONYMOUS_OPERATOR = "anonymous";

    /**
     * 依赖未装配（可选装配取不到 Bean）＝<b>不可用</b>态的显式承载（#142/B4-3e-56，差距 <b>D-B4-86</b>）。
     *
     * <p>详设 §4.7.2「不可用 ≠ 无权限 ≠ 空结果」＋铁律 §2.4（security 侧不可用一律 fail-closed）要求本态
     * 在返回面上<b>可被分辨</b>。收口前本类 <b>6 枚</b>未装配腿分四种折叠形态（逐枚勘察，登记时只记到
     * {@code internalError} 那两枚，成因见差距台账 B4-3e-56 段）：① {@code /logs}＝200＋空集、
     * ② {@code /stats}＝200＋全 0 计数、③ {@code /verify-integrity} 与 {@code /verify-chain}＝200＋
     * {@code valid=false} 载荷、④ 详情与写入两枚端点＝{@code ApiResponse.internalError("审计服务未就绪")}。
     * 前三种把「查不了」谎报成「没有数据」或「校验不通过」，第四种把「依赖缺席」谎报成「本服务自身故障」
     * ＝{@code code=-1/500} 且状态行仍恒 HTTP 200（裸回 {@code ApiResponse} 无 HTTP 状态能力，
     * 见 D-B4-59 族）——两侧都在禁列。</p>
     *
     * <p><b>为什么走抛型而不是改返回值</b>：{@code ApiResponse} 只描述 body、没有任何 HTTP 状态能力，
     * 改信封里的 {@code code} 写不出 503。本批沿用 3e-40~43／3e-52／3e-53／3e-55 在册承载族的<b>抛型形态</b>
     * ——端点签名一字不动（不触「既有签名不可变」红线），状态行交给宿主：抛
     * {@link DataBridgeException} 携带态，由双宿主 gateway／sysman-boot 的
     * {@code GlobalExceptionHandler}（#142/B4-3e-26／3e-29 已收口「携带码与状态行同号」）写上状态行。
     * 码面与行面同取 {@link RlsExecutionContract} 单源常量（与本模块 {@code DecisionController}、
     * data-engine {@code QueryController} 的不可用腿同源），禁在本类另立字面量。
     * 抛出后各端点<b>必须同时具备</b> {@code catch (DataBridgeException e) { throw e; }} 放行腿，
     * 否则同方法既有的 {@code catch (Exception)} 会把它再折回 500——该形态由
     * {@code AuditUnavailableCarrierTest} 逐枚钉住（守卫 6 枚／放行腿 6 枚）。</p>
     *
     * <p><b>真实空结果不受影响</b>：服务在位且查到零条仍是 200＋空集，正向对照同样入门。</p>
     */
    static DataBridgeException unassembled(String channel) {
        return new DataBridgeException(RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                RlsExecutionContract.SEC_UNAVAILABLE_CODE + ": " + channel + "未装配，审计面不可用，"
                        + "按 fail-closed 拒绝服务（§4.7.2 不可用 ≠ 无权限 ≠ 空结果）");
    }

    private final ObjectProvider<IAuditLogService> auditLogProvider;
    private final ObjectProvider<AuditHashChainService> hashChainProvider;  // P1-3: 哈希链验证

    public AuditController(ObjectProvider<IAuditLogService> auditLogProvider,
                           ObjectProvider<AuditHashChainService> hashChainProvider) {
        this.auditLogProvider = auditLogProvider;
        this.hashChainProvider = hashChainProvider;
    }

    @GetMapping("/logs")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        IAuditLogService service = auditLogProvider.getIfAvailable();
        if (service == null) {
            log.error("IAuditLogService 未装配，审计日志查询不可用（fail-closed，D-B4-86）");
            throw unassembled("审计日志服务 IAuditLogService");
        }
        try {
            IAuditLogService.AuditQueryCondition cond = new IAuditLogService.AuditQueryCondition();
            cond.setUserId(userId);
            if (action != null) cond.setEventType(action);
            if (resourceType != null) cond.setResource(resourceType);
            cond.setPage(page);
            cond.setPageSize(pageSize);
            List<AuditEvent> logs = service.query(cond);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", logs);
            result.put("total", logs.size());
            result.put("page", page);
            result.put("pageSize", pageSize);
            return ApiResponse.success(result);
        } catch (DataBridgeException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询审计日志失败", e);
            return ApiResponse.internalError("查询审计日志失败");
        }
    }

    @GetMapping("/logs/{id}")
    public ApiResponse<?> get(@PathVariable String id) {
        IAuditLogService service = auditLogProvider.getIfAvailable();
        if (service == null) {
            log.error("IAuditLogService 未装配，审计日志详情不可用（fail-closed，D-B4-86）");
            throw unassembled("审计日志服务 IAuditLogService");
        }
        try {
            AuditEvent log = service.getById(id);
            if (log == null) return ApiResponse.notFound("日志不存在");
            return ApiResponse.success(log);
        } catch (DataBridgeException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询审计日志详情失败: id={}", id, e);
            return ApiResponse.internalError("查询审计日志详情失败");
        }
    }

    /**
     * 审计统计 — 供前端安全审计页统计卡片使用
     * 返回: {todayCount, failureCount, activeUsers, anomalyIps, totalCount, successCount}
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        IAuditLogService service = auditLogProvider.getIfAvailable();
        if (service == null) {
            log.error("IAuditLogService 未装配，审计统计不可用（fail-closed，D-B4-86）");
            throw unassembled("审计日志服务 IAuditLogService");
        }
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            // Query all logs for stats (page 1, large page size)
            IAuditLogService.AuditQueryCondition cond = new IAuditLogService.AuditQueryCondition();
            cond.setPage(1);
            cond.setPageSize(1000);
            List<AuditEvent> allLogs = service.query(cond);

            // Today count
            java.time.LocalDate today = java.time.LocalDate.now();
            long todayCount = allLogs.stream()
                .filter(l -> l.getTimestamp() != null && l.getTimestamp().toLocalDate().equals(today))
                .count();

            // Failure count
            long failureCount = allLogs.stream()
                .filter(l -> "FAILURE".equals(l.getResult()) || "FAILED".equals(l.getResult()))
                .count();

            // Active users (distinct userIds)
            long activeUsers = allLogs.stream()
                .map(AuditEvent::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

            // Anomaly IPs (IPs with >3 failure events)
            Map<String, Long> ipFailureCount = allLogs.stream()
                .filter(l -> "FAILURE".equals(l.getResult()) || "FAILED".equals(l.getResult()))
                .filter(l -> l.getIpAddress() != null)
                .collect(java.util.stream.Collectors.groupingBy(AuditEvent::getIpAddress, java.util.stream.Collectors.counting()));
            long anomalyIps = ipFailureCount.values().stream().filter(c -> c > 3).count();

            result.put("todayCount", todayCount);
            result.put("failureCount", failureCount);
            result.put("activeUsers", activeUsers);
            result.put("anomalyIps", anomalyIps);
            result.put("totalCount", (long) allLogs.size());
            result.put("successCount", allLogs.stream().filter(l -> "SUCCESS".equals(l.getResult())).count());
            return ApiResponse.success(result);
        } catch (DataBridgeException e) {
            throw e;
        } catch (Exception e) {
            log.error("审计统计失败", e);
            return ApiResponse.internalError("审计统计失败");
        }
    }

    /**
     * P1-3: 验证审计日志哈希链完整性
     * 检测审计日志是否被篡改
     */
    @GetMapping("/verify-integrity")
    public ApiResponse<Map<String, Object>> verifyIntegrity() {
        AuditHashChainService chain = hashChainProvider.getIfAvailable();
        if (chain == null) {
            log.error("AuditHashChainService 未装配，哈希链全量校验不可用（fail-closed，D-B4-86）");
            throw unassembled("哈希链服务 AuditHashChainService");
        }
        try {
            return ApiResponse.success(chain.verifyHashChain());
        } catch (DataBridgeException e) {
            throw e;
        } catch (Exception e) {
            log.error("审计哈希链验证失败", e);
            return ApiResponse.internalError("审计哈希链验证失败");
        }
    }

    /**
     * 详设-01 B 章 3.2 交互时序⑦ / §5.2 契约：POST verify-chain {from,to} → {valid, brokenAt[]}。
     * 增量哈希链区间校验（区别于全量 GET /verify-integrity 的首断即停）。
     */
    @PostMapping("/verify-chain")
    public ApiResponse<Map<String, Object>> verifyChain(@RequestBody Map<String, Object> body) {
        AuditHashChainService chain = hashChainProvider.getIfAvailable();
        if (chain == null) {
            log.error("AuditHashChainService 未装配，哈希链区间校验不可用（fail-closed，D-B4-86）");
            throw unassembled("哈希链服务 AuditHashChainService");
        }
        try {
            Long from = asLong(body.get("from"));
            Long to = asLong(body.get("to"));
            if (from == null || to == null) {
                return ApiResponse.badRequest("from/to 必填且须为整数（审计日志 id 闭区间）");
            }
            if (from > to) {
                return ApiResponse.badRequest("from 不能大于 to");
            }
            return ApiResponse.success(chain.verifyHashChainRange(from, to));
        } catch (DataBridgeException e) {
            throw e;
        } catch (Exception e) {
            log.error("审计哈希链区间校验失败", e);
            return ApiResponse.internalError("审计哈希链区间校验失败");
        }
    }

    static Long asLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 写入审计日志端点 — 异步落库，不阻塞调用方。
     * 请求体: {userId, action, resource, result, detail}
     */
    @PostMapping("/log")
    public ApiResponse<Map<String, Object>> writeLog(@RequestBody Map<String, Object> body,
                                                     HttpServletRequest request) {
        IAuditLogService service = auditLogProvider.getIfAvailable();
        if (service == null) {
            log.error("IAuditLogService 未装配，审计写入不可用（fail-closed，D-B4-86）");
            throw unassembled("审计日志服务 IAuditLogService");
        }
        try {
            // 构造审计事件
            AuditEvent event = new AuditEvent();
            event.setEventId(UUID.randomUUID().toString());
            event.setTimestamp(LocalDateTime.now());
            event.setUserId(asString(body.get("userId")));
            event.setAction(asString(body.get("action")));
            event.setResource(asString(body.get("resource")));
            event.setResult(asString(body.get("result")));
            event.setEventType(asString(body.get("action")));  // action 复用为 eventType
            event.setIpAddress(request != null ? request.getRemoteAddr() : null);
            event.setUserAgent(request != null ? request.getHeader("User-Agent") : null);

            // detail 放入 details Map
            Object detail = body.get("detail");
            if (detail != null) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("detail", detail);
                // 透传调用方提供的额外字段
                for (Map.Entry<String, Object> e : body.entrySet()) {
                    String k = e.getKey();
                    if (!"userId".equals(k) && !"action".equals(k) && !"resource".equals(k)
                            && !"result".equals(k) && !"detail".equals(k)) {
                        details.put(k, e.getValue());
                    }
                }
                event.setDetails(details);
            }

            // 异步写入（IAuditLogService.log 内部已用 CompletableFuture.runAsync 异步落库）
            // N-24 取证：主体仍由调用方自报（信任模型待裁 Q15），此处旁录服务端已认证主体供对账
            log.info("审计摄取: claimedUserId={} action={} ingestActor={} eventId={}",
                    event.getUserId(), event.getAction(), resolveIngestActor(), event.getEventId());
            service.log(event);

            return ApiResponse.success(Map.of("status", "accepted"));
        } catch (DataBridgeException e) {
            throw e;
        } catch (Exception e) {
            log.error("写入审计日志失败", e);
            return ApiResponse.internalError("写入审计日志失败");
        }
    }

    /**
     * 服务端已认证主体（仅供旁录对账，不改写入库的自报主体 —— 待裁 Q15）。
     * 未认证时记 anonymous，不伪装成看似可信的 admin/system。
     */
    String resolveIngestActor() {
        String userId = UserContext.getCurrentUserId();
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        String username = UserContext.getCurrentUsername();
        if (username != null && !username.isBlank()) {
            return username;
        }
        return ANONYMOUS_OPERATOR;
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
