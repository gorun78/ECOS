package com.chinacreator.gzcm.runtime.core.cache.spring;

import java.util.concurrent.Callable;

import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.runtime.core.cache.CacheKey;
import com.chinacreator.gzcm.runtime.core.cache.RuntimeCacheManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Spring Cache 的<b>薄适配</b>（§2.9.8⑤；#147AF/D）——一个 Cache 名映射到 Runtime 的字符串通道。
 *
 * <p><b>为什么是薄壳而不是实现</b>：缓存键规范、TTL、陈旧上限、失效与远端通道必须只有一份事实源
 * （{@link RuntimeCacheManager}）。本类只做三件事：把 Spring 的 {@code (cacheName, key)}
 * 映射成 {@link CacheKey}、序列化值、把调用转给 Runtime——<b>不自建存储、不读配置</b>。</p>
 *
 * <p><b>三条硬口径</b>：① 无服务端租户上下文 ⇒ 不缓存（权限先于缓存，禁跨租户串值）；
 * ② 键含冒号/空白 ⇒ 不缓存（键段歧义会让 A 命中 B）；③ 值用显式信封 {@code {"t":类名,"v":值}}
 * 且<b>只允许 {@code com.chinacreator.gzcm} 包内类型</b>反序列化（不启用 Jackson 默认多态类型，
 * 防反序列化面被滥用）。</p>
 *
 * @author ECOS Runtime #147AF
 */
public class RuntimeSpringCache implements Cache {

    static final String OBJECT_TYPE = "spring-cache";
    static final String VERSION = "1";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 反序列化白名单前缀（禁任意类，防反序列化面被滥用）。 */
    static final String TYPE_WHITELIST_PREFIX = "com.chinacreator.gzcm.";

    private final String name;
    private final RuntimeCacheManager cache;

    public RuntimeSpringCache(String name, RuntimeCacheManager cache) {
        this.name = name;
        this.cache = cache;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return cache;
    }

    @Override
    public ValueWrapper get(Object key) {
        CacheKey cacheKey = key(key);
        if (cacheKey == null) {
            return null;
        }
        return cache.getString(cacheKey).map(this::deserialize).map(SimpleValueWrapper::new).orElse(null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Class<T> type) {
        ValueWrapper wrapper = get(key);
        if (wrapper == null || wrapper.get() == null) {
            return null;
        }
        Object value = wrapper.get();
        if (type != null && !type.isInstance(value)) {
            return null; // 类型不符按未命中（与 RuntimeCacheManager.get 同口径）
        }
        return (T) value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper wrapper = get(key);
        if (wrapper != null && wrapper.get() != null) {
            return (T) wrapper.get();
        }
        try {
            T loaded = valueLoader.call();
            put(key, loaded);
            return loaded;
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
    }

    @Override
    public void put(Object key, Object value) {
        CacheKey cacheKey = key(key);
        if (cacheKey == null || value == null) {
            return;
        }
        try {
            cache.putString(cacheKey, serialize(value), 0L);
        } catch (Exception ignore) {
            // 序列化失败只是不缓存（调用方仍拿到真实值）
        }
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        ValueWrapper existing = get(key);
        if (existing != null) {
            return existing;
        }
        put(key, value);
        return null;
    }

    @Override
    public void evict(Object key) {
        CacheKey cacheKey = key(key);
        if (cacheKey != null) {
            cache.evict(cacheKey);
        }
    }

    @Override
    public void clear() {
        cache.invalidateDomain(cache.env(), name);
    }

    /** 显式类型信封：只有白名单包内类型可写入，故反序列化面不依赖 Jackson 默认多态类型。 */
    private String serialize(Object value) throws Exception {
        String type = value.getClass().getName();
        if (!type.startsWith(TYPE_WHITELIST_PREFIX)) {
            throw new IllegalArgumentException("非白名单类型不缓存: " + type);
        }
        ObjectNode envelope = MAPPER.createObjectNode();
        envelope.put("t", type);
        envelope.set("v", MAPPER.valueToTree(value));
        return MAPPER.writeValueAsString(envelope);
    }

    private Object deserialize(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root == null || !root.isObject()) {
                return null;
            }
            JsonNode typeNode = root.get("t");
            JsonNode valueNode = root.get("v");
            if (typeNode == null || !typeNode.isTextual() || valueNode == null) {
                return null;
            }
            String type = typeNode.asText();
            if (!type.startsWith(TYPE_WHITELIST_PREFIX)) {
                return null;
            }
            return MAPPER.treeToValue(valueNode, Class.forName(type));
        } catch (Exception e) {
            return null; // 脏值按未命中
        }
    }

    /** Spring 键 → Runtime 键；不满足隔离/可寻址条件返回 null（不缓存）。 */
    CacheKey key(Object key) {
        if (key == null) {
            return null;
        }
        String tenant = TenantContextHolder.getTenantId();
        if (tenant == null || tenant.isBlank()) {
            return null;
        }
        String objectId = String.valueOf(key);
        try {
            return cache.key(name, OBJECT_TYPE, tenant.trim(), objectId, VERSION);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
