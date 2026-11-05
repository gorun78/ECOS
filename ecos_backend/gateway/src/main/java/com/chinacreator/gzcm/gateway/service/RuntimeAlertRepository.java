package com.chinacreator.gzcm.gateway.service;

import com.chinacreator.gzcm.gateway.dao.RuntimeAlertDao;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 详细设计-00 §1.6.4 铁律：Controller 不直接访问 JdbcTemplate，走 Service 层。
 * 该 Service 承担 F00-09 告警中心与 DLQ 重放的持久化（表由 V242/V243 建立）。
 *
 * <p>B6b 数据访问收口（架构铁律 §2.5 / IR06）：裸 SQL 全量下沉至
 * {@link RuntimeAlertDao}；本 Service 减薄为编排层，公共方法签名维持不变。</p>
 *
 * <p><b>#147CW</b>：列表两腿（{@code listAlerts}/{@code listDlq}）改强类型行 record；
 * 详情腿的 {@code SELECT *} 保留 {@code Map}（列集由 DDL 决定，禁把动态列冻结成固定键面）。</p>
 */
@Service
public class RuntimeAlertRepository {

    private static final Logger log = LoggerFactory.getLogger(RuntimeAlertRepository.class);
    private final RuntimeAlertDao dao;

    public RuntimeAlertRepository(RuntimeAlertDao dao) { this.dao = dao; }

    /** 告警行（V242 列表 12 列；时间列保持 JDBC 原值）。 */
    public record AlertRow(@JsonProperty("id") String id,
                           @JsonProperty("rule_code") String ruleCode,
                           @JsonProperty("severity") String severity,
                           @JsonProperty("status") String status,
                           @JsonProperty("title") String title,
                           @JsonProperty("source_module") String sourceModule,
                           @JsonProperty("trace_id") String traceId,
                           @JsonProperty("ack_by") String ackBy,
                           @JsonProperty("ack_time") Object ackTime,
                           @JsonProperty("close_time") Object closeTime,
                           @JsonProperty("occurred_at") Object occurredAt,
                           @JsonProperty("tenant_id") String tenantId) {

        static AlertRow fromMap(Map<String, Object> m) {
            return new AlertRow(str(m, "id"), str(m, "rule_code"), str(m, "severity"),
                    str(m, "status"), str(m, "title"), str(m, "source_module"),
                    str(m, "trace_id"), str(m, "ack_by"), m.get("ack_time"),
                    m.get("close_time"), m.get("occurred_at"), str(m, "tenant_id"));
        }
    }

    /** DLQ 行（V243 列表 8 列；时间列保持 JDBC 原值）。 */
    public record DlqRow(@JsonProperty("id") String id,
                         @JsonProperty("topic") String topic,
                         @JsonProperty("dlt_topic") String dltTopic,
                         @JsonProperty("error_message") String errorMessage,
                         @JsonProperty("attempts") Integer attempts,
                         @JsonProperty("first_seen_at") Object firstSeenAt,
                         @JsonProperty("replayed_at") Object replayedAt,
                         @JsonProperty("status") String status) {

        static DlqRow fromMap(Map<String, Object> m) {
            Object attempts = m.get("attempts");
            return new DlqRow(str(m, "id"), str(m, "topic"), str(m, "dlt_topic"),
                    str(m, "error_message"), attempts instanceof Number n ? n.intValue() : null,
                    m.get("first_seen_at"), m.get("replayed_at"), str(m, "status"));
        }
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public List<AlertRow> listAlerts(String severity, String status, int size, int offset) {
        try {
            return dao.listAlerts(severity, status, size, offset).stream()
                    .map(AlertRow::fromMap)
                    .toList();
        } catch (Exception ex) {
            log.error("listAlerts: {}", ex.getMessage()); throw ex;
        }
    }

    public int countAlerts(String severity, String status) {
        return dao.countAlerts(severity, status);
    }

    public List<Map<String, Object>> findAlertById(String id) {
        try {
            return dao.findAlertById(id);
        } catch (Exception ex) {
            log.error("findAlertById: {}", ex.getMessage()); throw ex;
        }
    }

    public int ackAlert(String id, String by) {
        try {
            return dao.ackAlert(id, by);
        } catch (Exception ex) {
            log.error("ackAlert: {}", ex.getMessage()); throw ex;
        }
    }

    /**
     * #142/B4-1 {@code closeAlert} 持久化编排（设计 §3.3 L2124）。
     * 与 {@link #ackAlert} 同结构：只编排 + 记错，裸 SQL 在 {@link RuntimeAlertDao#closeAlert(String)}。
     */
    public int closeAlert(String id) {
        try {
            return dao.closeAlert(id);
        } catch (Exception ex) {
            log.error("closeAlert: {}", ex.getMessage()); throw ex;
        }
    }

    public List<DlqRow> listDlq(String status, int size, int offset) {
        try {
            return dao.listDlq(status, size, offset).stream()
                    .map(DlqRow::fromMap)
                    .toList();
        } catch (Exception ex) {
            log.error("listDlq: {}", ex.getMessage()); throw ex;
        }
    }

    public int countDlq(String status) {
        return dao.countDlq(status);
    }

    public int replayDlq(String id) {
        try {
            return dao.replayDlq(id);
        } catch (Exception ex) {
            log.error("replayDlq: {}", ex.getMessage()); throw ex;
        }
    }
}
