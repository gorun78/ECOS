package com.chinacreator.gzcm.engine.data.fact.service;

import com.chinacreator.gzcm.common.data.DataDomainErrorCode;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.exception.ForbiddenException;
import com.chinacreator.gzcm.engine.data.fact.dao.PeriodStatusMapper;
import com.chinacreator.gzcm.engine.data.fact.period.DatabasePeriodLock;
import com.chinacreator.gzcm.engine.data.fact.period.OpenPeriodsPeriodLock;
import com.chinacreator.gzcm.engine.data.model.PeriodCloseRequestVO;
import com.chinacreator.gzcm.engine.data.model.PeriodCloseResultVO;
import com.chinacreator.gzcm.engine.data.model.PeriodStatusVO;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineSecurityService;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 期间关账服务 — 详细设计-09 v1.2 §十一 H-3 交付①（关账真身）。
 *
 * <p>控制流：
 * <ol>
 *   <li>期间归一：{@code 202501 → 2025-01}；非法形态 400 {@code ECOS-DATA-023}；</li>
 *   <li><b>角色门槛（OPA，禁硬编码角色映射）</b>：经 {@link PipelineSecurityService#evaluate}
 *       同一咽喉调 security-engine policy-engine（action {@code data.period.close}），
 *       role 取调用方 token claims（roles 声明）原样透传；
 *       DENY / security 不可用 ⇒ {@link ForbiddenException} 403 <b>fail-closed</b>，
 *       不落库不发事件；</li>
 *   <li>幂等关账：无登记行 → INSERT(CLOSED)；OPEN 行 → UPDATE→CLOSED（version_no 进位）；
 *       已 CLOSED 行 → 回放（alreadyClosed=true，零写入、零第二次审计事件，首写留痕不改）；
 *       并发双写由 UPDATE 的 {@code WHERE status='OPEN'} 双保险收敛；</li>
 *   <li>成功后 {@link DatabasePeriodLock#forceRefresh()} 即时失效缓存（导入链下一请求即 409 拒关账期）；</li>
 *   <li>新关闭 → {@code ecos.audit} 事件经 runtime-event {@link EventBusService} 单通道
 *       （后端规范 §九：禁自封装 Kafka；ST06）。</li>
 * </ol>
 *
 * <p><b>关账后不覆盖历史 run 数据</b>：本服务只写控制域状态行（V278），
 * 不触达任何事实/运行表；历史 ACTUAL 的封闭性由既有导入链 409 {@code ECOS-DATA-022}
 * （PeriodLock 命中）+ PUBLISHED 行不可变守卫 {@code ECOS-DATA-021} 承接（PRD-09 §5.1）。
 */
@Service
public class PeriodCloseService {

    private static final Logger log = LoggerFactory.getLogger(PeriodCloseService.class);

    static final String AUDIT_TOPIC = "ecos.audit";               // ST06（与 FactImportService 同字面量，避免 common 依赖环）
    static final String OPA_ACTION_PERIOD_CLOSE = "data.period.close";
    static final String OPA_RESOURCE_PREFIX = "ecos_data_period_status:";
    static final String STATUS_OPEN = "OPEN";
    static final String STATUS_CLOSED = "CLOSED";

    private static final Pattern YYYY_MM = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");
    private static final Pattern YYYYMM = Pattern.compile("^\\d{6}$");

    private final PeriodStatusMapper periodStatusMapper;
    private final PipelineSecurityService securityService;
    private final OpenPeriodsPeriodLock configFallback;
    private final Optional<DatabasePeriodLock> dbLock;
    private final Optional<EventBusService> eventBus;

    @Autowired
    public PeriodCloseService(PeriodStatusMapper periodStatusMapper,
                              PipelineSecurityService securityService,
                              OpenPeriodsPeriodLock configFallback,
                              @Autowired(required = false) Optional<DatabasePeriodLock> dbLock,
                              @Autowired(required = false) Optional<EventBusService> eventBus) {
        this.periodStatusMapper = periodStatusMapper;
        this.securityService = securityService;
        this.configFallback = configFallback;
        this.dbLock = dbLock == null ? Optional.empty() : dbLock;
        this.eventBus = eventBus == null ? Optional.empty() : eventBus;
    }

    /**
     * 关账（幂等）。
     *
     * @param rawPeriod 路径参数 yyyMM：接受 {@code 2025-01} 或 {@code 202501}，归一为 YYYY-MM
     * @param request   可空体（reason/detailJson 附言）
     * @throws DataBridgeException 400 ECOS-DATA-023（期间形态非法）
     * @throws ForbiddenException  403（OPA DENY 或 security-engine 不可用，fail-closed）
     */
    public PeriodCloseResultVO close(String rawPeriod, PeriodCloseRequestVO request) {
        String period = normalizePeriod(rawPeriod);

        // ① OPA 角色门槛（fail-closed：DENY/不可用都进 403，禁硬编码角色映射——role 原样透传 claims）
        PipelineSecurityService.AllowedResult verdict = securityService.evaluate(
                OPA_ACTION_PERIOD_CLOSE, OPA_RESOURCE_PREFIX + period, currentRoleClaims());
        if (!verdict.allowed()) {
            throw new ForbiddenException(DataDomainErrorCode.ECOS_DATA_031
                    + "：关账被拒绝（security-engine OPA，fail-closed）action=" + OPA_ACTION_PERIOD_CLOSE
                    + ", period=" + period + ", reason=" + verdict.reason());
        }

        String actor = currentActor();
        String reason = request == null ? null : trimToNull(request.getReason());
        String detailJson = request == null ? null : trimToNull(request.getDetailJson());

        // ② 幂等判定
        Map<String, Object> existing = periodStatusMapper.selectByPeriod(period);
        boolean newlyClosed;
        Timestamp closedAt;
        String closedBy;
        if (existing != null && STATUS_CLOSED.equalsIgnoreCase(str(existing.get("status")))) {
            newlyClosed = false;                       // 回放：零写入、零第二次审计（首写留痕）
            closedAt = asTimestamp(existing.get("closed_at"));
            closedBy = str(existing.get("closed_by"));
        } else if (existing == null) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", UUID.randomUUID().toString());               // MC01 应用侧 UUID
            row.put("period", period);
            row.put("status", STATUS_CLOSED);
            row.put("closedBy", actor);
            row.put("closedAt", Timestamp.valueOf(LocalDateTime.now()));
            row.put("closeReason", reason);
            row.put("closeDetailJson", detailJson);
            row.put("domain", "default");                               // DR08
            row.put("versionNo", "1");                                  // DR07
            row.put("isDeleted", 0);                                    // DR06
            row.put("createBy", actor);
            row.put("updateBy", actor);
            periodStatusMapper.insertRow(row);
            newlyClosed = true;
            closedAt = (Timestamp) row.get("closedAt");
            closedBy = actor;
        } else {
            // OPEN 行 → 单向 UPDATE（并发双写：affected=0 时按回放处理）
            int updated = periodStatusMapper.updateOpenToClosed(period, actor, reason, detailJson,
                    nextVersion(str(existing.get("version_no"))), actor);
            if (updated == 0) {
                Map<String, Object> reread = periodStatusMapper.selectByPeriod(period);
                newlyClosed = false;
                closedAt = reread == null ? Timestamp.valueOf(LocalDateTime.now()) : asTimestamp(reread.get("closed_at"));
                closedBy = reread == null ? actor : str(reread.get("closed_by"));
            } else {
                newlyClosed = true;
                closedAt = Timestamp.valueOf(LocalDateTime.now());
                closedBy = actor;
            }
        }

        // ③ 锁缓存即时失效（DB 形态在位才需要；config 形态无缓存概念）
        dbLock.ifPresent(DatabasePeriodLock::forceRefresh);

        // ④ 审计事件（仅新关闭；重复关账不产生第二条 ecos.audit）
        if (newlyClosed) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventType", "DATA_PERIOD_CLOSED");
            payload.put("period", period);
            payload.put("actor", actor);
            payload.put("closedAt", iso(closedAt));
            payload.put("reason", reason);
            payload.put("table", "ecos_data.ecos_data_period_status");
            payload.put("policyReason", verdict.reason());
            publish(AUDIT_TOPIC, payload);
            log.info("[PeriodClose] 期间 {} 关账 by {}（OPA allow: {}）", period, actor, verdict.reason());
        }

        PeriodCloseResultVO out = new PeriodCloseResultVO();
        out.setPeriod(period);
        out.setStatus(STATUS_CLOSED);
        out.setClosedBy(closedBy);
        out.setClosedAt(iso(closedAt));
        out.setAlreadyClosed(!newlyClosed);
        out.setReason(reason);
        return out;
    }

    /**
     * 期间状态查询（诊断/前端置灰用）。DB 不可读 → 配置期锁降级判定 + degraded=true。
     */
    public PeriodStatusVO status(String rawPeriod) {
        String period = normalizePeriod(rawPeriod);
        PeriodStatusVO vo = new PeriodStatusVO();
        vo.setPeriod(period);
        try {
            Map<String, Object> row = periodStatusMapper.selectByPeriod(period);
            vo.setDegraded(false);
            if (row == null) {
                vo.setStatus(STATUS_OPEN);       // 未登记 = OPEN（禁把缺省推断为 CLOSED）
            } else {
                vo.setStatus(str(row.get("status")));
                vo.setClosedBy(str(row.get("closed_by")));
                vo.setClosedAt(iso(asTimestamp(row.get("closed_at"))));
                vo.setCloseReason(str(row.get("close_reason")));
            }
            return vo;
        } catch (Exception e) {
            log.warn("[PeriodClose] period_status 表不可读，降级配置期锁判定 period={}: {}", period, e.getMessage());
            vo.setDegraded(true);
            vo.setStatus(configFallback.isClosed(period) ? STATUS_CLOSED : STATUS_OPEN);
            return vo;
        }
    }

    // ── 内部 ──────────────────────────────────────────────────────

    /** yyyMM 归一：202501 → 2025-01；2025-01 原样；其余 400。 */
    static String normalizePeriod(String raw) {
        if (raw == null || raw.isBlank()) {
            throw badRequest("期间不能为空（yyyMM 路径参数）");
        }
        String p = raw.trim().toUpperCase(Locale.ROOT);
        if (YYYYMM.matcher(p).matches()) {
            p = p.substring(0, 4) + "-" + p.substring(4);
        }
        if (!YYYY_MM.matcher(p).matches()) {
            throw badRequest("期间形态非法: " + raw + "（合法: yyyy-MM 或 yyyMM，如 2025-01 / 202501）");
        }
        return p;
    }

    /** version_no 十进制进位（DR07；非数字形态回退 "2"，保守递增）。 */
    static String nextVersion(String versionNo) {
        if (versionNo == null || versionNo.isBlank()) {
            return "2";
        }
        try {
            return String.valueOf(Long.parseLong(versionNo.trim()) + 1);
        } catch (NumberFormatException e) {
            return "2";
        }
    }

    /** 调用方角色声明原值（token claims 的 roles，逗号拼接透传 OPA；禁在本域解释语义）。 */
    static String currentRoleClaims() {
        try {
            UserContext ctx = UserContext.getCurrent();
            if (ctx == null) {
                return null;
            }
            Object roles = ctx.getClaim("roles");
            if (roles == null) {
                roles = ctx.getClaim("role");
            }
            if (roles == null) {
                return null;
            }
            if (roles instanceof Collection<?> c) {
                StringBuilder sb = new StringBuilder();
                for (Object o : c) {
                    if (sb.length() > 0) {
                        sb.append(',');
                    }
                    sb.append(o);
                }
                return sb.toString();
            }
            return String.valueOf(roles);
        } catch (Exception e) {
            log.debug("[PeriodClose] role claims 读取失败（OPA 按空角色裁决即 DENY）: {}", e.getMessage());
            return null;
        }
    }

    /** UserContext 当前操作者；无上下文（如测试）→ "system"（与 FactImportService 同口径）。 */
    static String currentActor() {
        String u = UserContext.getCurrentUserId();
        if (u == null) {
            u = UserContext.getCurrentUsername();
        }
        return (u == null || u.isBlank()) ? "system" : u;
    }

    private void publish(String topic, Object payload) {
        eventBus.ifPresent(bus -> {
            try {
                bus.publish(topic, payload);
            } catch (Exception e) {
                // C.8 降级矩阵：Kafka 不可用不阻断关账结果（WARN；事件补投归运维面）
                log.warn("[PeriodClose] event bus publish failed topic={}: {}", topic, e.getMessage(), e);
            }
        });
    }

    private static DataBridgeException badRequest(String msg) {
        return new DataBridgeException(400, 400, DataDomainErrorCode.ECOS_DATA_023 + "：" + msg, null);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static Timestamp asTimestamp(Object o) {
        if (o instanceof Timestamp t) {
            return t;
        }
        if (o instanceof java.util.Date d) {
            return new Timestamp(d.getTime());
        }
        if (o instanceof LocalDateTime ldt) {
            return Timestamp.valueOf(ldt);
        }
        if (o instanceof String s && !s.isBlank()) {
            try {
                return Timestamp.valueOf(LocalDateTime.parse(s,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    static String iso(Timestamp t) {
        return t == null ? null : t.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
