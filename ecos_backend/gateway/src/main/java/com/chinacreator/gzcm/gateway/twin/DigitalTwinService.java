package com.chinacreator.gzcm.gateway.twin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * P3-4 数字孪生服务 — 设备注册、状态管理、设备影子、遥测存储。
 * <p>
 * 设备影子 (DeviceShadow): {reported: {value, ts}, desired: {threshold?}}
 * 遥测: 每设备保留最近 100 条。
 */
@Service
public class DigitalTwinService {

    private static final Logger log = LoggerFactory.getLogger(DigitalTwinService.class);

    /** 最大遥测保留条数 */
    private static final int MAX_TELEMETRY = 100;

    /** 设备影子: deviceId → {reported: {value, ts}, desired: {threshold?}} */
    private final Map<String, Map<String, Object>> deviceShadows = new ConcurrentHashMap<>();

    /** 遥测数据: deviceId → 最近100条 */
    private final Map<String, ConcurrentLinkedDeque<Map<String, Object>>> telemetryStore = new ConcurrentHashMap<>();

    /** 设备元信息 */
    private final List<Map<String, Object>> deviceList = new ArrayList<>();

    /** MQTT Broker 状态 */
    private volatile boolean mqttUp = false;

    /**
     * A8: JDBC 异步持久化 — 单线程 Executor 避免遥测写入阻塞主线程。
     * 使用 private constructor 注入 JdbcTemplate（可选依赖，没有则不持久化）。
     */
    private final JdbcTemplate jdbc;
    private final ExecutorService persistenceExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "telemetry-persist");
        t.setDaemon(true);
        return t;
    });

    public DigitalTwinService(@Autowired(required = false) JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        registerDevice("sensor-temp-01", "温度传感器", "temperature", "°C", 20.0, 45.0);
        registerDevice("sensor-pressure-01", "压力传感器", "pressure", "MPa", 0.2, 2.0);
        registerDevice("sensor-vibration-01", "振动传感器", "vibration", "mm/s", 0.0, 12.0);
        registerDevice("sensor-flow-01", "流量传感器", "flow", "L/min", 0.0, 120.0);
        registerDevice("plc-switch-01", "开关控制器", "switch", "on/off", 0.0, 1.0);
        log.info("P3-4 DigitalTwinService initialized with {} devices", deviceList.size());
    }

    /**
     * 由 Spring 注入 OPC-UA Server (可选，若 OPC-UA 未启用则为 null)。
     */
    private void registerDevice(String deviceId, String name, String type, String unit,
                                double min, double max) {
        Map<String, Object> dev = new LinkedHashMap<>();
        dev.put("deviceId", deviceId);
        dev.put("name", name);
        dev.put("type", type);
        dev.put("unit", unit);
        dev.put("min", min);
        dev.put("max", max);
        dev.put("status", "online");
        dev.put("registeredAt", Instant.now().toString());
        deviceList.add(dev);

        // 初始化影子
        Map<String, Object> shadow = new ConcurrentHashMap<>();
        shadow.put("reported", new ConcurrentHashMap<>(Map.of(
                "value", 0.0,
                "ts", Instant.now().toString()
        )));
        shadow.put("desired", new ConcurrentHashMap<>());
        deviceShadows.put(deviceId, shadow);

        // 初始化遥测存储
        telemetryStore.put(deviceId, new ConcurrentLinkedDeque<>());
    }

    // ── 设备信息 ──────────────────────────────────────

    public List<Map<String, Object>> getDeviceList() {
        return new ArrayList<>(deviceList);
    }

    public Map<String, Object> getDeviceStatus(String deviceId) {
        Map<String, Object> result = new LinkedHashMap<>();
        // 设备元信息
        deviceList.stream()
                .filter(d -> deviceId.equals(d.get("deviceId")))
                .findFirst()
                .ifPresent(d -> result.putAll(d));
        // 设备影子
        result.put("shadow", deviceShadows.getOrDefault(deviceId, Collections.emptyMap()));
        // 遥测条数
        ConcurrentLinkedDeque<Map<String, Object>> q = telemetryStore.get(deviceId);
        result.put("telemetryCount", q != null ? q.size() : 0);
        return result;
    }

    // ── 遥测 ──────────────────────────────────────────

    public void recordTelemetry(String deviceId, double value) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("deviceId", deviceId);
        point.put("value", value);
        point.put("ts", Instant.now().toString());

        ConcurrentLinkedDeque<Map<String, Object>> queue = telemetryStore.get(deviceId);
        if (queue == null) return;

        queue.addLast(point);
        while (queue.size() > MAX_TELEMETRY) {
            queue.pollFirst();
        }

        // 更新 reported 影子
        Map<String, Object> shadow = deviceShadows.get(deviceId);
        if (shadow != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> reported = (Map<String, Object>) shadow.get("reported");
            if (reported != null) {
                reported.put("value", value);
                reported.put("ts", point.get("ts"));
            }
        }

        // ── A8: 异步双写 JDBC 持久化 ──
        if (jdbc != null) {
            persistenceExecutor.submit(() -> {
                try {
                    jdbc.update(
                        "INSERT INTO ecos_device_telemetry (device_id, value, recorded_at) VALUES (?, ?, ?)",
                        deviceId, value, Timestamp.from(Instant.now()));
                } catch (Exception e) {
                    log.warn("Failed to persist telemetry for {}: {}", deviceId, e.getMessage());
                }
            });
        }

        // OPC-UA 同步将随 Milo API 兼容性修复后回来
    }

    public List<Map<String, Object>> getTelemetry(String deviceId, int limit) {
        ConcurrentLinkedDeque<Map<String, Object>> queue = telemetryStore.get(deviceId);
        if (queue == null) return Collections.emptyList();

        List<Map<String, Object>> all = new ArrayList<>(queue);
        int size = all.size();
        int start = Math.max(0, size - limit);
        return all.subList(start, size);
    }

    // ── 指令 ──────────────────────────────────────────

    public Map<String, Object> sendCommand(String deviceId, String command, Map<String, Object> params) {
        Map<String, Object> shadow = deviceShadows.get(deviceId);
        if (shadow == null) {
            return Map.of("status", "error", "message", "device not found: " + deviceId);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> desired = (Map<String, Object>) shadow.get("desired");

        Map<String, Object> cmdRecord = new LinkedHashMap<>();
        cmdRecord.put("command", command);
        cmdRecord.put("params", params);
        cmdRecord.put("ts", Instant.now().toString());

        if (desired != null) {
            desired.putAll(cmdRecord);
        }

        log.info("P3-4 Command sent to {}: command={}, params={}", deviceId, command, params);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "accepted");
        result.put("deviceId", deviceId);
        result.put("command", command);
        result.put("params", params);
        return result;
    }

    // ── P2-14 Reconciliation Loop ──────────────────────

    /**
     * P2-14 设备影子 Reconciliation Loop。
     * 每 5 秒检查所有设备影子的 desired vs reported 差异，
     * 如果 desired != reported，模拟设备自动收敛并记录事件。
     */
    @Scheduled(fixedRate = 5000)
    public void reconcileDeviceShadows() {
        int reconciled = 0;
        for (Map.Entry<String, Map<String, Object>> entry : deviceShadows.entrySet()) {
            String deviceId = entry.getKey();
            Map<String, Object> shadow = entry.getValue();

            @SuppressWarnings("unchecked")
            Map<String, Object> desired = (Map<String, Object>) shadow.get("desired");
            @SuppressWarnings("unchecked")
            Map<String, Object> reported = (Map<String, Object>) shadow.get("reported");

            if (desired == null || desired.isEmpty() || reported == null) {
                continue;
            }

            // 检查差异: 比较 desired 和 reported 的 value
            Object desiredValue = desired.get("value");
            Object reportedValue = reported.get("value");

            boolean hasDiff = false;
            if (desiredValue != null) {
                if (reportedValue == null) {
                    hasDiff = true;
                } else if (desiredValue instanceof Number && reportedValue instanceof Number) {
                    hasDiff = Math.abs(((Number) desiredValue).doubleValue() - ((Number) reportedValue).doubleValue()) > 0.001;
                } else {
                    hasDiff = !desiredValue.equals(reportedValue);
                }
            }

            for (Map.Entry<String, Object> de : desired.entrySet()) {
                if (!"value".equals(de.getKey()) && !"ts".equals(de.getKey()) && !"command".equals(de.getKey())) {
                    Object repVal = reported.get(de.getKey());
                    if (repVal == null || !de.getValue().equals(repVal)) {
                        hasDiff = true;
                        break;
                    }
                }
            }

            if (hasDiff) {
                // 模拟设备自动收敛: 将 desired 同步到 reported
                reported.put("value", desiredValue != null ? desiredValue : reportedValue);
                reported.put("ts", Instant.now().toString());
                // 同步其他 desired 字段
                for (Map.Entry<String, Object> de : desired.entrySet()) {
                    if (!"command".equals(de.getKey()) && !"params".equals(de.getKey())) {
                        reported.put(de.getKey(), de.getValue());
                    }
                }
                // 清除已执行的 desired 指令
                desired.remove("command");
                desired.remove("params");
                desired.remove("value");
                reconciled++;
                log.info("P2-14 Device shadow reconciled: deviceId={}, desired→reported", deviceId);
            }
        }

        if (reconciled > 0) {
            log.info("P2-14 Reconciliation loop: {}/{} devices reconciled", reconciled, deviceShadows.size());
        }
    }

    // ── 健康检查 ──────────────────────────────────────

    public void setMqttUp(boolean up) {
        this.mqttUp = up;
    }

    public Map<String, Object> getHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("mqtt", Map.of("status", mqttUp ? "UP" : "DOWN"));
        health.put("device_count", deviceList.size());
        health.put("twin_version", "P3-4");
        return health;
    }
}
