package com.chinacreator.gzcm.engine.cognitive2.service.mental;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.cognitive2.service.EvidenceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 周一故障复盘聚合服务（PMO-59 P3b T4 / 复盘口径定稿落地点）。
 *
 * <p><b>口径定稿</b>（指令 §实现决策 7，二选一说明）：Kafka {@code ecos.cognitive} 事件
 * <b>未落 PG</b>（全仓无事件镜像表）→ 本端点采用<b>基于三表版本链 + V130 impact 表重建</b>：
 * belief 版本时间线（每次更新一行=一条 COGNITIVE_BELIEF_UPDATED 事件等价物）+
 * hypothesis 失效时点（=COGNITIVE_HYPOTHESIS_INVALIDATED 等价物）+ evidence 登记时间线
 * + run 作废留痕（V130，=COGNITIVE_RUN_SUPERSEDED 等价物）。Kafka 侧事件镜像落库
 * 属 runtime-event 演进项（Phase 4+），不在本 Phase 加表（防加塞）。</p>
 *
 * <p>tag 参数白名单校验：复盘 tag 与 {@link MentalEventPublisher#REVIEW_TAG} 同源
 * （用户确认决策 ② 预留口径），非法 tag 400 拒绝（防无差别全量导出）。</p>
 */
@Service
public class MentalReviewService {

    private static final Logger log = LoggerFactory.getLogger(MentalReviewService.class);

    /** 复盘 tag 白名单（与事件 faultContext.reviewTag 生产侧同源；新增 tag 走代码评审扩充） */
    private static final List<String> REVIEW_TAGS = List.of(MentalEventPublisher.REVIEW_TAG);

    private final JdbcTemplate jdbc;
    private final EvidenceStore evidenceStore;

    public MentalReviewService(JdbcTemplate jdbc, EvidenceStore evidenceStore) {
        this.jdbc = jdbc;
        this.evidenceStore = evidenceStore;
    }

    /**
     * 按 reviewTag 聚合心智层复盘结构。
     *
     * @param tag   复盘 tag（必填，白名单校验）
     * @param since 时间下界（可选，ISO-8601 LocalDateTime；缺省=全量）
     * @return 复盘结构 {tag, since, reconstructionMode, beliefTimelines, hypotheses, evidence, runImpacts, summary}
     */
    public Map<String, Object> aggregate(String tag, String since) {
        if (tag == null || REVIEW_TAGS.stream().noneMatch(t -> t.equals(tag.trim()))) {
            throw new BusinessException(400,
                "COG-400: 非法复盘 tag=" + tag + "（白名单: " + REVIEW_TAGS + "）");
        }
        LocalDateTime sinceTs = parseSince(since);
        String tagNorm = tag.trim();
        log.info("复盘聚合请求: tag={} since={}", tagNorm, sinceTs);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("tag", tagNorm);
        resp.put("since", sinceTs == null ? null : sinceTs.toString());
        resp.put("reconstructionMode", "three-table-version-chain+V130-impact（Kafka 事件未落 PG，按三表版本链重建口径，P3b T4 定稿）");
        resp.put("beliefTimelines", beliefTimelines(sinceTs));
        resp.put("hypotheses", hypotheses(sinceTs));
        resp.put("evidence", evidenceTimeline(sinceTs));
        resp.put("runImpacts", runImpacts(sinceTs));
        resp.put("summary", buildSummary((List<Map<String, Object>>) resp.get("beliefTimelines"),
            (List<Map<String, Object>>) resp.get("hypotheses"),
            (List<Map<String, Object>>) resp.get("evidence"),
            (List<Map<String, Object>>) resp.get("runImpacts")));
        return resp;
    }

    /** belief 版本时间线（variable 分组，版本升序；since 过滤按版本行 update_time）。 */
    private List<Map<String, Object>> beliefTimelines(LocalDateTime since) {
        StringBuilder sql = new StringBuilder(
            "SELECT variable_name, domain, version, distribution, is_manual_override, " +
            "last_evidence_id, status, update_time FROM ecos_cognitive_belief " +
            "WHERE is_deleted = 0");
        List<Object> args = new ArrayList<>();
        if (since != null) {
            sql.append(" AND update_time >= ?");
            args.add(java.sql.Timestamp.valueOf(since));
        }
        sql.append(" ORDER BY variable_name, domain, version");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());

        // variable(+domain) 分组保序
        Map<String, Map<String, Object>> grouped = new TreeMap<>();
        for (Map<String, Object> row : rows) {
            String key = row.get("variable_name") + "|" + row.get("domain");
            Map<String, Object> group = grouped.computeIfAbsent(key, k -> {
                Map<String, Object> g = new LinkedHashMap<>();
                g.put("variableName", row.get("variable_name"));
                g.put("domain", row.get("domain"));
                g.put("versions", new ArrayList<Map<String, Object>>());
                return g;
            });
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("version", row.get("version"));
            v.put("distribution", evidenceStore.parseJsonObjectListText(String.valueOf(row.get("distribution"))));
            v.put("manualOverride", Boolean.TRUE.equals(row.get("is_manual_override")));
            v.put("lastEvidenceId", evidenceStore.fieldString(row, "last_evidence_id"));
            v.put("status", evidenceStore.fieldString(row, "status"));
            v.put("updatedAt", evidenceStore.fieldTime(row, "update_time") == null
                ? null : evidenceStore.fieldTime(row, "update_time").toString());
            ((List<Map<String, Object>>) group.get("versions")).add(v);
        }
        return new ArrayList<>(grouped.values());
    }

    /** 假设时间线（since 按 invalid_at/update_time；全列显式清单，0 SELECT *）。 */
    private List<Map<String, Object>> hypotheses(LocalDateTime since) {
        StringBuilder sql = new StringBuilder(
            "SELECT h.id, h.hypothesis_code, h.domain, h.metric_ref, h.status, h.is_valid, " +
            "h.invalid_at, h.invalid_reason, h.update_time, h.evidence_ids " +
            "FROM ecos_cognitive_hypothesis h WHERE h.is_deleted = 0");
        List<Object> args = new ArrayList<>();
        if (since != null) {
            sql.append(" AND (h.invalid_at IS NULL OR h.invalid_at >= ? OR h.update_time >= ?)");
            args.add(java.sql.Timestamp.valueOf(since));
            args.add(java.sql.Timestamp.valueOf(since));
        }
        sql.append(" ORDER BY h.update_time DESC, h.id");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("id", evidenceStore.fieldString(row, "id"));
            h.put("hypothesisCode", evidenceStore.fieldString(row, "hypothesis_code"));
            h.put("domain", evidenceStore.fieldString(row, "domain"));
            h.put("metricRef", evidenceStore.fieldString(row, "metric_ref"));
            h.put("status", evidenceStore.fieldString(row, "status"));
            h.put("isValid", Boolean.TRUE.equals(row.get("is_valid")));
            h.put("invalidAt", evidenceStore.fieldTime(row, "invalid_at") == null
                ? null : evidenceStore.fieldTime(row, "invalid_at").toString());
            h.put("invalidReason", evidenceStore.fieldString(row, "invalid_reason"));
            h.put("updatedAt", evidenceStore.fieldTime(row, "update_time") == null
                ? null : evidenceStore.fieldTime(row, "update_time").toString());
            h.put("evidenceIds", evidenceStore.parseJsonListText(String.valueOf(row.get("evidence_ids"))));
            out.add(h);
        }
        return out;
    }

    /** 证据登记时间线（since 按 create_time）。 */
    private List<Map<String, Object>> evidenceTimeline(LocalDateTime since) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, evidence_code, source_type, confidence, is_conflict, status, " +
            "effective_time, create_time FROM ecos_cognitive_evidence WHERE is_deleted = 0");
        List<Object> args = new ArrayList<>();
        if (since != null) {
            sql.append(" AND create_time >= ?");
            args.add(java.sql.Timestamp.valueOf(since));
        }
        sql.append(" ORDER BY create_time ASC, id");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", evidenceStore.fieldString(row, "id"));
            e.put("evidenceCode", evidenceStore.fieldString(row, "evidence_code"));
            e.put("sourceType", evidenceStore.fieldString(row, "source_type"));
            e.put("confidence", row.get("confidence") instanceof Number n ? n.doubleValue() : 0d);
            e.put("isConflict", Boolean.TRUE.equals(row.get("is_conflict")));
            e.put("status", evidenceStore.fieldString(row, "status"));
            e.put("effectiveTime", evidenceStore.fieldTime(row, "effective_time") == null
                ? null : evidenceStore.fieldTime(row, "effective_time").toString());
            e.put("createTime", evidenceStore.fieldTime(row, "create_time") == null
                ? null : evidenceStore.fieldTime(row, "create_time").toString());
            out.add(e);
        }
        return out;
    }

    /** run 作废留痕（V130 impact 表，since 按 superseded_at）。 */
    private List<Map<String, Object>> runImpacts(LocalDateTime since) {
        StringBuilder sql = new StringBuilder(
            "SELECT event_id, hypothesis_id, run_id, auto_detected, superseded_at, detail " +
            "FROM ecos_cognitive_run_invalidation WHERE is_deleted = 0");
        List<Object> args = new ArrayList<>();
        if (since != null) {
            sql.append(" AND superseded_at >= ?");
            args.add(java.sql.Timestamp.valueOf(since));
        }
        sql.append(" ORDER BY superseded_at DESC, id");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("eventId", evidenceStore.fieldString(row, "event_id"));
            r.put("hypothesisId", evidenceStore.fieldString(row, "hypothesis_id"));
            r.put("runId", evidenceStore.fieldString(row, "run_id"));
            r.put("autoDetected", Boolean.TRUE.equals(row.get("auto_detected")));
            r.put("supersededAt", evidenceStore.fieldTime(row, "superseded_at") == null
                ? null : evidenceStore.fieldTime(row, "superseded_at").toString());
            r.put("detail", evidenceStore.fieldString(row, "detail"));
            out.add(r);
        }
        return out;
    }

    /** 汇总计数（summary 段）。 */
    private Map<String, Object> buildSummary(List<Map<String, Object>> beliefTimelines,
                                             List<Map<String, Object>> hypotheses,
                                             List<Map<String, Object>> evidence,
                                             List<Map<String, Object>> runImpacts) {
        int beliefVersions = 0;
        for (Map<String, Object> g : beliefTimelines) {
            beliefVersions += ((List<?>) g.get("versions")).size();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("beliefVariables", beliefTimelines.size());
        summary.put("beliefVersions", beliefVersions);
        summary.put("hypothesesTotal", hypotheses.size());
        summary.put("hypothesesInvalidated",
            (int) hypotheses.stream().filter(h -> !Boolean.TRUE.equals(h.get("isValid"))).count());
        summary.put("evidenceCount", evidence.size());
        summary.put("runsSuperseded", runImpacts.size());
        return summary;
    }

    /** since 参数解析（可选；非法 ISO 格式 400）。 */
    private LocalDateTime parseSince(String since) {
        if (since == null || since.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(since.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "COG-400: since 须为 ISO-8601 日期时间（如 2026-09-14T00:00:00）: " + since);
        }
    }
}
