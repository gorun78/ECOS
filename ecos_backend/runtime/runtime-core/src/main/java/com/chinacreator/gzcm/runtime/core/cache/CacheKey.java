package com.chinacreator.gzcm.runtime.core.cache;

import java.util.Objects;

/**
 * 缓存键单源（《补充详设 R7~R11》§2.9.8③；#147J）。
 *
 * <p>格式固定为 {@code ecos:{env}:{domain}:{objectType}:{scope}:{objectId}:v{version}}，
 * 六段语义缺一不可：</p>
 * <ul>
 *   <li>{@code env} — 部署环境隔离（同 Redis 多环境不串）；</li>
 *   <li>{@code domain} — 控制域/业务域；</li>
 *   <li>{@code objectType} — 缓存对象类型（14 类矩阵之一）；</li>
 *   <li>{@code scope} — 租户/机构/用户等隔离域（<b>权限先于缓存</b>，无 scope 的键一律拒绝）；</li>
 *   <li>{@code objectId} — 对象标识；</li>
 *   <li>{@code version} — 数据版本（版本化缓存，主动失效靠它）。</li>
 * </ul>
 *
 * <p>任何段含 {@code :} 或为空即抛 {@link IllegalArgumentException}——键拼接歧义会让
 * 「A 租户命中 B 租户缓存」，这是缓存层最严重的越权形态。</p>
 *
 * @author ECOS Runtime底座 #147J
 */
public record CacheKey(String env, String domain, String objectType,
                       String scope, String objectId, String version) {

    private static final String PREFIX = "ecos";

    public CacheKey {
        requireSegment("env", env);
        requireSegment("domain", domain);
        requireSegment("objectType", objectType);
        requireSegment("scope", scope);
        requireSegment("objectId", objectId);
        requireSegment("version", version == null ? null : version.replaceFirst("^v", ""));
    }

    /** 规范化字符串（{@code version} 自带 {@code v} 前缀，调用方不必重复）。 */
    @Override
    public String toString() {
        return PREFIX + ':' + env + ':' + domain + ':' + objectType + ':' + scope + ':' + objectId
                + ":v" + version.replaceFirst("^v", "");
    }

    /** 解析外部传入的键（测试/运维排查用）；不合法返回 {@code null}。 */
    public static CacheKey parse(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split(":", -1);
        if (parts.length != 7 || !PREFIX.equals(parts[0])) {
            return null;
        }
        try {
            return new CacheKey(parts[1], parts[2], parts[3], parts[4], parts[5], parts[6]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void requireSegment(String name, String value) {
        Objects.requireNonNull(value, "缓存键段 " + name + " 不能为空（权限先于缓存：无 scope 的键禁止）");
        String v = value.trim();
        if (v.isEmpty()) {
            throw new IllegalArgumentException("缓存键段 " + name + " 不能为空");
        }
        if (v.indexOf(':') >= 0) {
            throw new IllegalArgumentException("缓存键段 " + name + " 含冒号，会造成键歧义：" + v);
        }
    }
}
