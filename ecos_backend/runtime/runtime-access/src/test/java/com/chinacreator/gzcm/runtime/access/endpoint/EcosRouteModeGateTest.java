package com.chinacreator.gzcm.runtime.access.endpoint;

import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver.RouteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #146ax E.5 两态路由开关（《从零实施版》§2.5.3 / 风险面 E.5 第 2 项）形态单源与
 * 上界腿钉值。夹具＝test classpath route-manifest.json（既有单源清单，真实条目
 * sysman/datanet/workspace）：
 * <ul>
 *   <li>覆写位闭集 {@code monolith|service}，fail-loud（拼错键值禁静默回落）；</li>
 *   <li>三级取面：显式覆写 → manifest route → 默认 MONOLITH；</li>
 *   <li>manifest route=monolith 条目即使运营键写 service 也回落 monolith port（上界腿）；</li>
 *   <li>既有 URL 覆写位 {@code ecos.route.datanet-base-url} 非形态键，绑定时排除不误判。</li>
 * </ul>
 */
@DisplayName("#146ax/E.5：两态路由开关形态单源 fail-loud 与上界腿")
class EcosRouteModeGateTest {

    private static ServiceEndpointResolver resolverWith(EcosRouteOverride ovr) {
        ServiceEndpointResolver r = new ServiceEndpointResolver();
        if (ovr != null) r.attachOverride(ovr);
        return r;
    }

    @Test
    void overrideOutOfRangeValueFailsLoud() {
        EcosRouteOverride o = new EcosRouteOverride();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> o.bind(Map.of("datanet", "mono-lith")));
        assertTrue(ex.getMessage().contains("ecos.route.datanet"),
                "fail-loud 须点名出错键：实际=" + ex.getMessage());
        assertTrue(ex.getMessage().contains("monolith|service"));
    }

    @Test
    void overrideClosedSetIsCaseInsensitiveTrimmed() {
        EcosRouteOverride o = new EcosRouteOverride();
        o.bind(Map.of("Datanet", " SERVICE "));
        assertEquals(RouteMode.SERVICE, o.literalFor("datanet"));
        assertTrue(o.hasOverrides());
    }

    @Test
    void noBindingMeansNoOverrideMaterial() {
        EcosRouteOverride o = new EcosRouteOverride();
        o.bind(Map.of());
        assertFalse(o.hasOverrides());
        assertNull(o.literalFor("datanet"));
        // datanet 在 manifest route=service → 无覆写时取 SERVICE；未知 service → 默认 MONOLITH
        assertEquals(RouteMode.SERVICE, resolverWith(null).effectiveMode("datanet"));
        assertEquals(RouteMode.MONOLITH, resolverWith(null).effectiveMode("nope"));
    }

    @Test
    void effectiveModeThreeLevels() {
        // ①覆写优先：manifest route=service 的 workspace 被显式压回 MONOLITH
        EcosRouteOverride toMono = new EcosRouteOverride();
        toMono.bind(Map.of("workspace", "monolith"));
        assertEquals(RouteMode.MONOLITH, resolverWith(toMono).effectiveMode("workspace"));

        // ②覆写提升：manifest route=monolith 的 sysman 被显式提升 SERVICE
        EcosRouteOverride toSvc = new EcosRouteOverride();
        toSvc.bind(Map.of("sysman", "service"));
        assertEquals(RouteMode.SERVICE, resolverWith(toSvc).effectiveMode("sysman"));

        // ③无覆写照 manifest / 缺省
        assertEquals(RouteMode.MONOLITH, resolverWith(null).effectiveMode("sysman"));
    }

    @Test
    void manifestRouteMonolithCapsServiceOverride() {
        // 运营键写 service，但 manifest route=monolith（未切流）⇒ 端口仍 monolith
        EcosRouteOverride toSvc = new EcosRouteOverride();
        toSvc.bind(Map.of("sysman", "service"));
        ServiceEndpointResolver r = resolverWith(toSvc);
        assertEquals(RouteMode.SERVICE, r.effectiveMode("sysman"), "覆写面在位");
        assertEquals("http://localhost:8080",
                r.resolve("sysman", RouteMode.SERVICE).orElseThrow(),
                "未切流条目的 SERVICE 覆写不改变运行期端口（上界腿）");
    }

    @Test
    void resolvedServiceEntryKeepsServicePort() {
        ServiceEndpointResolver r = resolverWith(null);
        // workspace route=service：SERVICE 取 servicePort 18090，MONOLITH 取 8080
        assertEquals("http://localhost:18090",
                r.resolve("workspace", RouteMode.SERVICE).orElseThrow());
        assertEquals("http://localhost:8080",
                r.resolve("workspace", RouteMode.MONOLITH).orElseThrow());
        // 未知 service 未登记 → 空（调用方自决降级）
        assertFalse(r.resolve("unknown-svc", RouteMode.SERVICE).isPresent());
    }

    @Test
    void legacyUrlOverrideKeyIsExcludedFromShapeBinding() {
        // URL 覆写位不是形态键：值非 monolith|service 也不进闭集校验，且不参与形态面
        Map<String, String> shape = EcosRouteOverrideConfig.shapeOnly(Map.of(
                "datanet-base-url", "http://10.0.0.8:18082",
                "workspace", "service"));
        assertEquals(1, shape.size());
        assertEquals("service", shape.get("workspace"));
        EcosRouteOverride o = new EcosRouteOverride();
        o.bind(shape);
        assertEquals(RouteMode.SERVICE, o.literalFor("workspace"));
        assertNull(o.literalFor("datanet-base-url"));
    }
}
