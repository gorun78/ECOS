package com.chinacreator.gzcm.runtime.dlq;

import java.sql.Timestamp;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.runtime.dlq.entity.DltEventRow;
import com.chinacreator.gzcm.runtime.dlq.mapper.DltEventMapper;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;

/**
 * DLQ 重放服务（C.5.2 重放入工队列）。
 * <p>
 * 运维在告警中心点「重放」→ {@code POST /api/v1/monitor/dlq/{id}/replay} →
 * 本服务：从表取 payload → 重新 {@link EventBusService#publish} 到原 topic →
 * attempts+1、status=replayed、replayed_at=now。
 * <p>
 * 语义：publish 幂等委托给事件总线（Kafka 在/不在都能发，失败仅 WARN 不返回异常，
 * 与设计 §2.4 "不阻塞主流程" 一致）。
 */
@Service
public class DltReplayService {

    private static final Logger log = LoggerFactory.getLogger(DltReplayService.class);

    /** 资源不存在（404 语义） */
    public static final String NOT_FOUND = "NOT_FOUND";

    private final DltEventMapper dltEventMapper;
    private final EventBusService eventBus;

    public DltReplayService(DltEventMapper dltEventMapper, EventBusService eventBus) {
        this.dltEventMapper = dltEventMapper;
        this.eventBus = eventBus;
    }

    /**
     * 重放指定 DLQ 记录。
     *
     * @param id dlq 记录 id
     * @return 重放后的 status（replayed）；记录不存在时返回 {@link #NOT_FOUND}（端点转 404）
     */
    public String replay(String id) {
        if (id == null || id.isBlank()) {
            return NOT_FOUND;
        }
        DltEventRow row;
        try {
            row = dltEventMapper.findById(id);
        } catch (Exception e) {
            log.warn("[DltReplay] 查 DLQ 记录失败 id={} errType={} msg={}",
                    id, e.getClass().getSimpleName(), e.getMessage());
            return NOT_FOUND;
        }
        if (row == null) {
            return NOT_FOUND;
        }
        // 重新发布到原业务 topic。payload 存的是原始 JSON 串：先反序列化成通用对象
        // （Map/List/标量），保证 Kafka 路径 JSON 序列化往返不引入字符串引号；
        // 解析失败才按原始字符串发布（兼容非 JSON 载荷）。
        Object payload = row.getPayload();
        try {
            payload = new com.fasterxml.jackson.databind.ObjectMapper().readValue(row.getPayload(), Object.class);
        } catch (Exception e) {
            log.debug("[DltReplay] payload 非 JSON 对象，按原始字符串重放 id={} errType={}",
                    id, e.getClass().getSimpleName());
        }
        try {
            eventBus.publish(row.getTopic(), payload);
        } catch (Exception e) {
            log.warn("[DltReplay] 重放 publish 失败 id={} topic={} errType={}",
                    id, row.getTopic(), e.getClass().getSimpleName(), e);
        }
        Timestamp now = Timestamp.from(Instant.now());
        try {
            dltEventMapper.markReplayed(id, now);
        } catch (Exception e) {
            log.warn("[DltReplay] 重放标记落库失败 id={} errType={} msg={}",
                    id, e.getClass().getSimpleName(), e.getMessage());
        }
        log.info("[DltReplay] 重放完成 id={} topic={} → status=replayed", id, row.getTopic());
        return "replayed";
    }
}
