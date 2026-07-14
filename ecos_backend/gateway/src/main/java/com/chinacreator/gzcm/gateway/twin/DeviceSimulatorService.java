package com.chinacreator.gzcm.gateway.twin;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * P3-4 设备模拟器 — 模拟 5 个工业设备，每 2 秒推送 MQTT 遥测。
 * <p>
 * 设备列表:
 * <ul>
 *   <li>sensor-temp-01 — 温度 25-40°C</li>
 *   <li>sensor-pressure-01 — 压力 0.5-1.5 MPa</li>
 *   <li>sensor-vibration-01 — 振动 0-10 mm/s</li>
 *   <li>sensor-flow-01 — 流量 0-100 L/min</li>
 *   <li>plc-switch-01 — 开关 0/1</li>
 * </ul>
 * Topic: ecos/devices/{deviceId}/telemetry
 */
@Service
@EnableScheduling
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true")
public class DeviceSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(DeviceSimulatorService.class);

    private static final int MQTT_PORT = 1883;
    private static final String MQTT_BROKER = "tcp://localhost:" + MQTT_PORT;
    private static final String TELEMETRY_TOPIC = "ecos/devices/%s/telemetry";

    private final DigitalTwinService twinService;
    private final Random random = new Random();
    private volatile MqttClient mqttClient;

    public DeviceSimulatorService(DigitalTwinService twinService) {
        this.twinService = twinService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            mqttClient = new MqttClient(MQTT_BROKER, "p3-4-simulator",
                    new MemoryPersistence());
            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setCleanSession(true);
            opts.setConnectionTimeout(10);
            opts.setAutomaticReconnect(true);
            mqttClient.connect(opts);
            twinService.setMqttUp(true);
            log.info("P3-4 DeviceSimulator connected to MQTT broker at {}", MQTT_BROKER);
        } catch (MqttException e) {
            log.warn("P3-4 DeviceSimulator MQTT connect failed (broker may not be ready yet): {}",
                    e.getMessage());
            twinService.setMqttUp(false);
        }
    }

    /** 每 2 秒为每个设备推送一条遥测 */
    @Scheduled(fixedRate = 2000)
    public void simulateDevices() {
        if (mqttClient == null || !mqttClient.isConnected()) {
            // 尝试重连
            try {
                if (mqttClient != null) {
                    mqttClient.reconnect();
                    twinService.setMqttUp(true);
                    log.info("P3-4 DeviceSimulator reconnected to MQTT broker");
                    return;
                }
            } catch (MqttException e) {
                twinService.setMqttUp(false);
                return;
            }
            return;
        }

        simulate("sensor-temp-01",       25.0 + random.nextDouble() * 15.0);   // 25-40
        simulate("sensor-pressure-01",    0.5 + random.nextDouble() * 1.0);    // 0.5-1.5
        simulate("sensor-vibration-01",   random.nextDouble() * 10.0);          // 0-10
        simulate("sensor-flow-01",        random.nextDouble() * 100.0);         // 0-100
        simulate("plc-switch-01",         random.nextBoolean() ? 1.0 : 0.0);    // 0 or 1
    }

    private void simulate(String deviceId, double value) {
        // 四舍五入到两位小数
        double v = Math.round(value * 100.0) / 100.0;

        // 更新内存遥测
        twinService.recordTelemetry(deviceId, v);

        // 构造 JSON 消息
        String payload = String.format(
                "{\"deviceId\":\"%s\",\"value\":%.2f,\"ts\":\"%s\"}",
                deviceId, v, java.time.Instant.now().toString());

        // 发布 MQTT
        try {
            MqttMessage msg = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            msg.setQos(0);
            msg.setRetained(false);
            mqttClient.publish(String.format(TELEMETRY_TOPIC, deviceId), msg);
        } catch (MqttException e) {
            log.debug("P3-4 MQTT publish failed for {}: {}", deviceId, e.getMessage());
        }
    }
}
