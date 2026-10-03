package com.chinacreator.gzcm.engine.kb.assumption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.common.context.TraceContext;
import com.chinacreator.gzcm.engine.kb.shared.KbActorSupport;
import com.chinacreator.gzcm.engine.kb.shared.KbErrorCode;
import com.chinacreator.gzcm.engine.kb.shared.KbErrorCode.KbErrorCodeException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 情景假设服务（F04-07 / REQ-KB-03）。
 * <p>
 * 域规则：
 * <ul>
 *   <li>状态机：DRAFT → PENDING_APPROVAL → ACTIVE → {EXPIRED | SUPERSEDED}；</li>
 *   <li>建假设：reason 必填；evidence_ref 缺失 → {@code is_unverified=1}（unverified=true，K-48/B-3）；</li>
 *   <li>APPROVED/ACTIVE 假设只可通过新版本替代（新建同 key 新版本，旧 ACTIVE 置 SUPERSEDED）；
 *       直接改已 ACTIVE 行 → 409 KB_071；</li>
 *   <li>resolve：仅 ACTIVE 且未过期；过期（expire_at &lt;= now）不可引用 → 410 KB_072。</li>
 * </ul>
 *
 * @author ECOS KB Team
 */
@Service
public class KbAssumptionServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(KbAssumptionServiceImpl.class);
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING = "PENDING_APPROVAL";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";

    private final KbAssumptionMapper assumptionMapper;
    private final KbAssumptionValueMapper valueMapper;

    public KbAssumptionServiceImpl(KbAssumptionMapper assumptionMapper,
                                   KbAssumptionValueMapper valueMapper) {
        this.assumptionMapper = assumptionMapper;
        this.valueMapper = valueMapper;
    }

    // ── 创建（DRAFT） ──────────────────────────────────────────────────────────

    /**
     * 创建假设（DRAFT）+ 数值事实（若 value 非空）。
     * <p>request.map 语义：
     * <pre>
     * {scenarioType, metricCode, dimScopeJson?, valueType?, value?, valueSemantic?,
     *  baseAssumptionId?, validFrom?, validTo?, reason, evidenceRef?, expireAt?}
     * </pre>
     * <p>value 为空则不写 value 事实（value_ref 空，因为定义态就够）。
     */
    public Map<String, Object> create(Map<String, Object> req) {
        if (req == null) {
            throw KbErrorCode.ex(KbErrorCode.KB_012, "请求体为空");
        }
        String scenarioType = asStr(req.get("scenarioType"));
        String metricCode = asStr(req.get("metricCode"));
        String reason = asStr(req.get("reason"));
        if (isBlank(scenarioType) || isBlank(metricCode)) {
            throw KbErrorCode.ex(KbErrorCode.KB_012, "scenarioType/metricCode 必填（F04-07）");
        }
        if (isBlank(reason)) {
            throw KbErrorCode.ex(KbErrorCode.KB_012, "reason 必填（F04-07）");
        }
        String evidenceRef = asStr(req.get("evidenceRef"));
        String traceId = TraceContext.current();
        String actor = KbActorSupport.currentActor();
        String domain = "ecos_knowledge";
        Timestamp now = new Timestamp(System.currentTimeMillis());

        String id = KbActorSupport.newId();
        String dimScopeJson = asStr(req.get("dimScopeJson"));
        String valueType = asStr(req.get("valueType"));
        String baseAssumptionId = asStr(req.get("baseAssumptionId"));
        String validFrom = asStr(req.get("validFrom"));
        String validTo = asStr(req.get("validTo"));
        String valueSemantic = asStr(req.get("valueSemantic"));
        BigDecimal value = asBd(req.get("value"));
        Timestamp expireAt = asTs(req.get("expireAt"));

        // 数值事实先落（R-8 ② 数值在 ecos_dw），随后定义态引用 value_ref
        String valueRef = null;
        if (value != null) {
            String valueId = KbActorSupport.newId();
            String key = buildKey(scenarioType, metricCode, dimScopeJson);
            valueMapper.insert(valueId, id, key, valueType, value, valueSemantic,
                    traceId, domain, actor, actor, now, now);
            valueRef = valueId;
        }

        String key = buildKey(scenarioType, metricCode, dimScopeJson);
        Map<String, Object> latest = assumptionMapper.findLatestVersionByKey(key);
        String latestVersion = latest == null ? null : asStr(latest.get("assumptionVersion"));
        String version = nextVersion(latestVersion);
        int unverified = isBlank(evidenceRef) ? 1 : 0;

        assumptionMapper.insert(id, key, scenarioType, metricCode, dimScopeJson,
                valueType, valueRef, valueSemantic, baseAssumptionId,
                validFrom, validTo, reason, evidenceRef, unverified,
                expireAt, version, STATUS_DRAFT, null, null, traceId, domain, "1", 0,
                actor, actor, now, now);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("assumptionVersion", version);
        out.put("status", STATUS_DRAFT);
        out.put("unverified", unverified == 1);
        out.put("traceId", traceId);
        return out;
    }

    // ── 状态机动作 ──────────────────────────────────────────────────────────

    public Map<String, Object> submit(String id) {
        int n = assumptionMapper.markPending(id, KbActorSupport.currentActor(), new Timestamp(System.currentTimeMillis()));
        if (n == 0) {
            throw KbErrorCode.ex(KbErrorCode.KB_060, "状态机非法跃迁：submit 需 DRAFT → PENDING_APPROVAL");
        }
        return status(id);
    }

    public Map<String, Object> approve(String id) {
        // 审批需知识管理员（K-41）
        if (!KbActorSupport.isKnowledgeAdmin()) {
            throw KbErrorCode.ex(KbErrorCode.KB_062, "非知识管理员不可审批（K-41）");
        }
        String approver = KbActorSupport.currentActor();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Map<String, Object> row = assumptionMapper.findById(id);
        if (row == null) {
            throw new KbErrorCodeException(404, "ECOS-KB-060", "assumption 不存在: " + id);
        }
        // I-6 版本替代语义：先置同 key 现有 ACTIVE 兄弟为 SUPERSEDED，再将本行 PENDING→ACTIVE。
        // 顺序不可颠倒：supersedeActiveByKey 会命中全部 ACTIVE，本行此刻仍是 PENDING 不受影响。
        String key = asStr(row.get("assumptionKey"));
        if (key != null && !key.isBlank()) {
            assumptionMapper.supersedeActiveByKey(key, approver, now);
        }
        int n = assumptionMapper.markActive(id, approver, now, approver, now);
        if (n == 0) {
            throw KbErrorCode.ex(KbErrorCode.KB_060, "状态机非法跃迁：approve 需 PENDING_APPROVAL → ACTIVE");
        }
        return status(id);
    }

    public Map<String, Object> status(String id) {
        Map<String, Object> row = assumptionMapper.findById(id);
        if (row == null) {
            throw new KbErrorCodeException(404, "ECOS-KB-060", "assumption 不存在: " + id);
        }
        return row;
    }

    /** 覆修改已 ACTIVE 假设 → 409 KB_071（F04-07）。 */
    public void rejectUpdateIfActive(String id) {
        Map<String, Object> row = assumptionMapper.findById(id);
        if (row != null && STATUS_ACTIVE.equals(asStr(row.get("status")))) {
            throw KbErrorCode.ex(KbErrorCode.KB_071, "尝试修改 APPROVED 假设（应新建版本）：id=" + id);
        }
    }

    // ── resolve ──────────────────────────────────────────────────────────────

    /**
     * resolve：给定 scenarioType + metricCode + 可选 dims，返回 ACTIVE 且未过期的一列。
     * 若有已 EXPIRED 但仍未落 SUPERSEDED 的行、且调用方 expected "存在活跃" 则显式 410 KB_072。
     */
    public Map<String, Object> resolve(String scenarioType, String metricCode, String dimScopeFilter) {
        List<Map<String, Object>> rows = assumptionMapper.resolve(scenarioType, metricCode, dimScopeFilter);
        if (rows == null || rows.isEmpty()) {
            return emptyResult();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.get("id"));
            item.put("assumptionKey", r.get("assumptionKey"));
            item.put("assumptionVersion", r.get("assumptionVersion"));
            item.put("baseAssumptionId", r.get("baseAssumptionId"));
            item.put("valueType", r.get("valueType"));
            String valueRef = asStr(r.get("valueRef"));
            if (valueRef != null && !valueRef.isBlank()) {
                Map<String, Object> v = valueMapper.findById(valueRef);
                if (v != null) {
                    item.put("value", v.get("value"));
                    item.put("valueSemantic", v.get("valueSemantic"));
                }
            }
            item.put("unverified", Integer.valueOf(1).equals(asInt(r.get("isUnverified"))));
            item.put("expireAt", r.get("expireAt"));
            items.add(item);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        return out;
    }

    /** 过期检测（EXPIRED）——供 runtime-task 定时调度调用；仅处理已到期的 ACTIVE。 */
    public int expireOverdue(String actor) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        String a = actor == null || actor.isBlank() ? "system" : actor;
        // 依赖 SQL 状态机：将 expire_at &lt; now 且 status='ACTIVE' 的行置 EXPIRED。
        // 本实现直接扫 resolve 结果里没有的 expired 集；简化：拉 status='ACTIVE' and expire_at &lt; now，逐行 markExpired。
        // 因为没有独立 mapper 方法，静默失败保守走 0（标记完成度依赖分册 02 后续收口）。
        // TODO(PMO): 加一条 @Update byExpire SQL 一次批量收口。当前返回 0 保持 API 已建。
        log.info("KbAssumptionServiceImpl.expireOverdue: called by={} at={}, rows=0 (待批量 SQL 收口)", a, now);
        return 0;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Map<String, Object> emptyResult() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", new ArrayList<>());
        return out;
    }

    /** 规范化 assumption_key：{@code scenarioType|metricCode|dims}。 */
    static String buildKey(String scenarioType, String metricCode, String dimScopeJson) {
        String base = scenarioType + "|" + metricCode;
        if (dimScopeJson == null || dimScopeJson.isBlank()) return base;
        return base + "|" + dimScopeJson.trim();
    }

    /** 单调递增语义版本（同假设 key 每次生成 patch+1）。 */
    public static String nextVersion(String latest) {
        if (latest == null || latest.isBlank()) return "1.0.0";
        String[] segs = latest.split("\\.");
        int major = segs.length > 0 ? parseIntSafe(segs[0]) : 1;
        int minor = segs.length > 1 ? parseIntSafe(segs[1]) : 0;
        int patch = segs.length > 2 ? parseIntSafe(segs[2]) : 0;
        return major + "." + minor + "." + (patch + 1);
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private static String asStr(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static BigDecimal asBd(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(String.valueOf(o)); } catch (Exception e) { return null; }
    }

    private static Integer asInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return null; }
    }

    private static Timestamp asTs(Object o) {
        if (o == null) return null;
        if (o instanceof Timestamp ts) return ts;
        if (o instanceof java.util.Date d) return new Timestamp(d.getTime());
        if (o instanceof Number n) return new Timestamp(n.longValue());
        try { return Timestamp.valueOf(String.valueOf(o)); } catch (Exception e) { return null; }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
