package com.chinacreator.gzcm.runtime.core.alert.service.impl;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chinacreator.gzcm.runtime.core.alert.mapper.AlertRecordMapper;
import com.chinacreator.gzcm.runtime.core.alert.mapper.entity.AlertRecordRow;
import com.chinacreator.gzcm.runtime.core.alert.vo.AlertItem;
import com.chinacreator.gzcm.runtime.core.alert.vo.AlertPage;
import com.chinacreator.gzcm.runtime.core.alert.vo.DltItem;
import com.chinacreator.gzcm.runtime.core.alert.vo.DltPage;
import com.chinacreator.gzcm.runtime.dlq.DltReplayService;
import com.chinacreator.gzcm.runtime.dlq.entity.DltEventRow;
import com.chinacreator.gzcm.runtime.dlq.mapper.DltEventMapper;

/**
 * 告警中心 / DLQ 运维管理面（§D.5.3，详细设计-00）。
 * <p>
 * gateway {@code MonitorAlertController} 的宿主侧落地：分页 SQL 查
 * {@code public.ecos_runtime_alert_record}（occurred_at DESC）/
 * {@code public.ecos_runtime_event_dlq}（first_seen_at DESC），
 * ack/close 走状态机 UPDATE（只 mark，<b>不自动 close</b> — 审计兜底 critical 告警
 * 的人工确认口径），DLQ 重放委托 runtime-event {@link DltReplayService}。
 * <p>
 * DB 异常一律向上抛（端点侧转 503，"store unavailable" 语义）；
 * 记录不存在返回 {@code null}（端点侧转 404）。
 */
@Service
public class AlertMonitorAdminService {

    private static final Logger log = LoggerFactory.getLogger(AlertMonitorAdminService.class);

    private final AlertRecordMapper recordMapper;
    private final DltEventMapper dltEventMapper;
    private final DltReplayService dltReplayService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AlertMonitorAdminService(AlertRecordMapper recordMapper,
                                    DltEventMapper dltEventMapper,
                                    DltReplayService dltReplayService) {
        this.recordMapper = recordMapper;
        this.dltEventMapper = dltEventMapper;
        this.dltReplayService = dltReplayService;
    }

    // ── 告警 ──────────────────────────────────────────────────────

    /** 分页列表（{items,total}；occurred_at DESC，SQL 保证） */
    public AlertPage pageAlerts(String severity, String status, int page, int size) {
        List<AlertRecordRow> rows = recordMapper.page(emptyToNull(severity), emptyToNull(status),
                size, (page - 1) * size);
        long total = recordMapper.count(emptyToNull(severity), emptyToNull(status), null);
        List<AlertItem> items = (rows == null ? List.<AlertRecordRow>of() : rows).stream()
                .map(this::toItem).collect(Collectors.toList());
        return new AlertPage(items, total, page, size);
    }

    /** 单条详情；不存在（或 is_deleted=1）返回 null → 端点 404 */
    public AlertItem getAlert(String id) {
        AlertRecordRow row = recordMapper.findById(id);
        return row == null ? null : toItem(row);
    }

    /**
     * ACK：status=acked、ack_by/ack_time；<b>只 mark 不自动 close</b>（人工口径）。
     * 不存在返回 null → 端点 404。
     */
    public AlertItem ackAlert(String id, String operator, String note) {
        AlertRecordRow existing = recordMapper.findById(id);
        if (existing == null) {
            return null;
        }
        String ackBy = operator != null && !operator.isBlank() ? operator : "monitor-api";
        recordMapper.ack(id, ackBy, Timestamp.from(Instant.now()));
        if (note != null && !note.isBlank()) {
            // 备注无独立列（V241 口径），透传到端点审计通道由 controller 落 sysman 审计
            log.info("[MonitorAlert] ack note id={} operator={} note={}", id, ackBy, note);
        }
        AlertRecordRow after = recordMapper.findById(id);
        return after == null ? toItem(existing) : toItem(after);
    }

    /** 关闭：status=closed、close_time；不存在返回 null → 端点 404 */
    public AlertItem closeAlert(String id, String operator) {
        AlertRecordRow existing = recordMapper.findById(id);
        if (existing == null) {
            return null;
        }
        recordMapper.close(id, operator, Timestamp.from(Instant.now()));
        AlertRecordRow after = recordMapper.findById(id);
        return after == null ? toItem(existing) : toItem(after);
    }

    // ── DLQ ───────────────────────────────────────────────────────

    /** DLQ 分页（{items,total}；first_seen_at DESC，SQL 保证） */
    public DltPage pageDltEvents(String status, int page, int size) {
        List<DltEventRow> rows = dltEventMapper.page(emptyToNull(status), size, (page - 1) * size);
        long total = dltEventMapper.count(emptyToNull(status));
        List<DltItem> items = (rows == null ? List.<DltEventRow>of() : rows).stream()
                .map(this::toDltItem).collect(Collectors.toList());
        return new DltPage(items, total, page, size);
    }

    /**
     * DLQ 重放入口（端点委托）：payload → republish 原 topic → attempts+1/status=replayed。
     *
     * @return {@link DltReplayService#NOT_FOUND}（端点转 404）或 "replayed"
     */
    public String replayDltEvent(String id) {
        return dltReplayService.replay(id);
    }

    // ── 行 → VO ───────────────────────────────────────────────────

    private AlertItem toItem(AlertRecordRow row) {
        AlertItem item = new AlertItem();
        item.setId(row.getId());
        item.setRuleCode(row.getRuleCode());
        item.setSeverity(row.getSeverity());
        item.setStatus(row.getStatus());
        item.setTitle(row.getTitle());
        item.setDetail(parseDetail(row.getDetailJson()));
        item.setSourceModule(row.getSourceModule());
        item.setTraceId(row.getTraceId());
        item.setAckBy(row.getAckBy());
        item.setAckTime(toLdt(row.getAckTime()));
        item.setCloseTime(toLdt(row.getCloseTime()));
        item.setOccurredAt(toLdt(row.getOccurredAt()));
        item.setCreateTime(toLdt(row.getCreateTime()));
        item.setUpdateTime(toLdt(row.getUpdateTime()));
        return item;
    }

    private DltItem toDltItem(DltEventRow row) {
        DltItem item = new DltItem();
        item.setId(row.getId());
        item.setTopic(row.getTopic());
        item.setDltTopic(row.getDltTopic());
        item.setPayload(row.getPayload());
        item.setErrorMessage(row.getErrorMessage());
        item.setAttempts(row.getAttempts() != null ? row.getAttempts().intValue() : 0);
        item.setFirstSeenAt(toLdt(row.getFirstSeenAt()));
        item.setReplayedAt(toLdt(row.getReplayedAt()));
        item.setStatus(row.getStatus());
        item.setTraceId(row.getTraceId());
        item.setCreateTime(toLdt(row.getCreateTime()));
        return item;
    }

    /** detail_json → Map（解析失败兜底 raw 串，不丢信息） */
    private Map<String, Object> parseDetail(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(detailJson, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            log.debug("[MonitorAlert] detail_json 解析失败，按 raw 透传: {}", e.getMessage());
            return Map.of("raw", detailJson);
        }
    }

    private static LocalDateTime toLdt(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
