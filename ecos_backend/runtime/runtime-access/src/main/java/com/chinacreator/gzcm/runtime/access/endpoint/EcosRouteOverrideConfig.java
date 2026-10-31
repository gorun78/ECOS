package com.chinacreator.gzcm.runtime.access.endpoint;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

/**
 * {@link EcosRouteOverride} 装配：{@code ecos.route.<service>} 前缀下全部键绑成
 * service→值 覆写表。
 *
 * <p>既有键面共存：{@code ecos.route.datanet-base-url}（URL 覆写位，各引擎
 * {@code @Value} 读取）不是形态键，绑定时<b>显式排除</b>，不参与闭集校验
 * （把它当 service 覆写会因值非 monolith|service 触发 fail-loud 假红）。</p>
 */
@Configuration
public class EcosRouteOverrideConfig {

    private static final Log log = LogFactory.getLog(EcosRouteOverrideConfig.class);

    /** 既有 URL 覆写位（非形态键），绑定时排除。 */
    static final String LEGACY_URL_KEY = "datanet-base-url";

    @Bean
    public EcosRouteOverride ecosRouteOverride(ConfigurableEnvironment env,
                                                ObjectProvider<ServiceEndpointResolver> resolver) {
        EcosRouteOverride o = new EcosRouteOverride();
        Map<String, String> raw = Binder.get(env)
                .bind("ecos.route", Bindable.mapOf(String.class, String.class))
                .orElseGet(java.util.Collections::emptyMap);
        o.bind(shapeOnly(raw));
        if (o.hasOverrides()) {
            log.info("E.5 两态路由覆写在位: " + o.overridesView());
        }
        resolver.ifAvailable(r -> r.attachOverride(o));
        return o;
    }

    /** 从原始键面剔除既有 URL 覆写位，只留形态键（可测纯函数：门禁直接钉此腿）。 */
    static Map<String, String> shapeOnly(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) return java.util.Collections.emptyMap();
        java.util.Map<String, String> shape = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> e : raw.entrySet()) {
            if (LEGACY_URL_KEY.equals(e.getKey())) continue;
            shape.put(e.getKey(), e.getValue());
        }
        return shape;
    }
}
