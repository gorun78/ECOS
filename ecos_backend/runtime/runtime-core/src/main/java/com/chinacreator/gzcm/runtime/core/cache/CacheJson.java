package com.chinacreator.gzcm.runtime.core.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 缓存值 JSON 编解码单源（§2.9.8②；#147AH）。
 *
 * <p><b>为什么收敛到这里</b>：字符串通道只存字符串，各消费方若各自 new ObjectMapper，
 * 会出现「有的能解 LocalDateTime、有的解不出」的分裂。本类给出同一份编解码口径，
 * 但<b>不启用 Jackson 默认多态类型</b>——反序列化类型必须由调用方显式给出，
 * 避免共享缓存成为任意类实例化面。</p>
 *
 * <p>编解码失败一律返回 {@code null}（调用方按未命中回源），缓存异常不得上抛成业务失败。</p>
 *
 * @author ECOS Runtime #147AH
 */
public final class CacheJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    private CacheJson() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /** 序列化；失败返回 null（不缓存本轮值）。 */
    public static String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    /** 按显式类型反序列化；失败返回 null（按未命中处理）。 */
    public static <T> T read(String json, Class<T> type) {
        if (json == null || type == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
    }

    /** 按显式泛型类型反序列化；失败返回 null（按未命中处理）。 */
    public static <T> T read(String json, TypeReference<T> type) {
        if (json == null || type == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
    }
}
