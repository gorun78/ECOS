package com.chinacreator.gzcm.datanet.config;

import io.moquette.broker.Server;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * MQTT 嵌入式 Broker（数据层基础能力）。
 * <p>
 * 默认关闭。在系统配置中心开启：sys_config 表 {@code mqtt.enabled = true}。
 * 端口 1883，host 0.0.0.0，匿名连接，内存存储。
 */
@Configuration
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true")
public class MqttBrokerConfig {

    private static final Logger log = LoggerFactory.getLogger(MqttBrokerConfig.class);

    public static final int MQTT_PORT = 1883;
    public static final String MQTT_HOST = "0.0.0.0";

    @Bean(destroyMethod = "stopServer")
    public Server mqttBroker() {
        Properties props = new Properties();
        props.setProperty(IConfig.HOST_PROPERTY_NAME, MQTT_HOST);
        props.setProperty(IConfig.PORT_PROPERTY_NAME, String.valueOf(MQTT_PORT));
        props.setProperty(IConfig.ALLOW_ANONYMOUS_PROPERTY_NAME, "true");
        props.setProperty(IConfig.PERSISTENCE_ENABLED_PROPERTY_NAME, "false");

        IConfig config = new MemoryConfig(props);
        Server server = new Server();

        try {
            server.startServer(config);
            log.info("MQTT Broker (Moquette) started on {}:{}, anonymous=true", MQTT_HOST, MQTT_PORT);
        } catch (Exception e) {
            log.error("Failed to start MQTT Broker", e);
            throw new RuntimeException("MQTT Broker start failed", e);
        }

        return server;
    }
}
