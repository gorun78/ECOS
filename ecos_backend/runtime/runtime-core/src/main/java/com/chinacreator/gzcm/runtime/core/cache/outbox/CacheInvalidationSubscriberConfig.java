package com.chinacreator.gzcm.runtime.core.cache.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.chinacreator.gzcm.runtime.core.cache.RuntimeCacheManager;

/**
 * 缓存失效广播的订阅端（§2.9.8④ 跨节点失效；#147AF/D）。
 *
 * <p>收到 {@value CacheInvalidationRelay#CHANNEL} 消息后走 {@code invalidateDomainLocal}：
 * 清本节点进程内两级存储，并对该域前缀再做一次 Redis 清理（发布端已清过一次，
 * 这里重复是幂等的自愈——发布端那次若因 Redis 抖动失败，本节点这次补上）。
 * <b>绝不再入队宣告</b>（防 广播→订阅→入队→发布 成环）。
 * Redis 不可用时不建容器（无远程通道可订阅），不影响本节点既有语义。</p>
 *
 * @author ECOS Runtime #147AF
 */
@Configuration
public class CacheInvalidationSubscriberConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationSubscriberConfig.class);

    @Bean
    public RedisMessageListenerContainer cacheInvalidationListenerContainer(
            ObjectProvider<StringRedisTemplate> templates,
            ObjectProvider<RuntimeCacheManager> cache) {
        StringRedisTemplate template = templates == null ? null : templates.getIfAvailable();
        if (template == null || template.getConnectionFactory() == null) {
            return null; // 无 Redis 通道：不建容器
        }
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(template.getConnectionFactory());
        MessageListener listener = (message, pattern) -> {
            try {
                String body = new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8);
                String env = extract(body, "env");
                String domain = extract(body, "domain");
                RuntimeCacheManager manager = cache == null ? null : cache.getIfAvailable();
                if (manager != null && env != null && domain != null && !domain.isBlank()) {
                    // Local 变体：只执行失效，不再入队宣告，否则 广播→订阅→入队→发布 成环。
                    manager.invalidateDomainLocal(env, domain);
                    log.debug("收到跨节点失效广播，已清本节点域缓存: env={}, domain={}", env, domain);
                }
            } catch (RuntimeException e) {
                log.warn("处理缓存失效广播失败（忽略）: {}", e.getMessage());
            }
        };
        container.addMessageListener(listener, new ChannelTopic(CacheInvalidationRelay.CHANNEL));
        return container;
    }

    /** 从扁平 JSON 提取字符串字段（负载结构固定且极小，无需完整 Jackson 解析）。 */
    static String extract(String json, String field) {
        if (json == null) {
            return null;
        }
        String key = "\"" + field + "\":\"";
        int i = json.indexOf(key);
        if (i < 0) {
            return null;
        }
        int start = i + key.length();
        int end = json.indexOf('"', start);
        return end < 0 ? null : json.substring(start, end);
    }
}
