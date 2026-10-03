package com.chinacreator.gzcm.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * W43→C32（详细设计-02 §7.1，M1 离线护栏 / 不触库 / 不 Spring）。
 *
 * <p>《详细设计-02》§一 D-3 实测 5 套前缀同日同进程并活 → C.2.1 裁决为收敛到 2 套核心前缀
 * 并在单源台账 {@code gateway/src/main/resources/route/route-manifest.json} 里登记；
 * 附带存根参见 {@link GatewayRouteManifestTest}（结构完整性 + 与 docs 版本同步断言）。
 *
 * <p>本护栏是 <b>W43 视角</b>的一条姊妹断言：从「数据域」视角验证台账确实把
 * 5 数据域目标前缀（C.2.1 三家表）都登记在 owner=datanet 底下并统一 monolith 态，
 * 防未来 S3 切流或增量路由改动让"单源真源"滑回"双源并存"（W43 核心关切）。</p>
 *
 * <p>白线断言（防空文件静默绿）：manifest 里 datanet owner 前缀总数 ≥ 4，且前缀里
 * <b>每个核心 4 条</b>都必须实登（防止"名字改了 owner 还在但核心表被删"这种半绿）。</p>
 */
class RouteManifestParityTest {

    // C.2.1 数据域 4 条核心前缀（合并别名不算 core）
    private static final List<String> DATA_CORE_PREFIXES = List.of(
            "/api/v1/datanet",
            "/api/v1/engine/data",
            "/api/v1/data",
            "/api/v1/dq");

    private final JsonNode root = loadManifest();
    private final JsonNode entries = requireEntries(root);

    private static JsonNode loadManifest() {
        try (InputStream is = RouteManifestParityTest.class
                .getResourceAsStream("/route/route-manifest.json")) {
            assertNotNull(is, "classpath 缺 /route/route-manifest.json（构建期 gateway/src/main/resources 未拷贝进 test classpath）");
            return new ObjectMapper().readTree(new BufferedInputStream(is));
        } catch (Exception e) {
            throw new IllegalStateException("route-manifest.json 读取/解析失败: " + e.getMessage(), e);
        }
    }

    private static JsonNode requireEntries(JsonNode root) {
        JsonNode e = root.get("entries");
        assertNotNull(e, "manifest 缺 entries 数组");
        assertTrue(e.isArray(), "entries 必须 array");
        assertTrue(e.size() >= 5, "健康台账应登记 ≥5 条前缀; 实际 " + e.size());
        return e;
    }

    @Test
    @DisplayName("台账结构完整：prefix 语法 + owner 非空 + 端口双态 + mode 在 {monolith, service} 内；S3 前一律 monolith")
    void entryWellformed_andMonolithPreS3() {
        Set<String> seenPrefixes = new HashSet<>();
        int i = 0;
        for (JsonNode e : entries) {
            String pfx = text(e, "prefix");
            String owner = text(e, "owner");
            String mode = text(e, "mode");
            assertNotNull(pfx, "entry[" + i + "] 缺 prefix");
            assertFalse(pfx.isEmpty(), "entry[" + i + "] prefix 为空");
            assertTrue(pfx.startsWith("/api"), "prefix 必须 /api 起头（BFF 才代理）; 实际 " + pfx);
            assertFalse(seenPrefixes.contains(pfx), "prefix 双登（单源违约）: " + pfx);
            seenPrefixes.add(pfx);

            assertNotNull(owner, "entry[" + i + "] 缺 owner: " + pfx);
            assertFalse(owner.isEmpty(), "entry[" + i + "] owner 为空: " + pfx);

            JsonNode ports = e.get("ports");
            assertNotNull(ports, "entry[" + i + "] 缺 ports: " + pfx);
            JsonNode mono = ports.get("monolith");
            JsonNode svc = ports.get("service");
            assertNotNull(mono, "entry[" + i + "] ports.monolith 缺失: " + pfx);
            assertNotNull(svc, "entry[" + i + "] ports.service 缺失: " + pfx);
            assertEquals(8080, mono.asInt(), "S3 前 monolith 端口一律 8080（gateway）; 实际 " + mono.asInt() + "（" + pfx + "）");
            assertTrue(svc.asInt() >= 18000 && svc.asInt() <= 18999,
                    "service 端口必须 180xx 段位; 实际 " + svc.asInt() + "（" + pfx + "）");

            assertTrue("monolith".equals(mode) || "service".equals(mode),
                    "mode 只能 monolith|service（分册 00 C.2.3 两态寻址）; 实际 " + mode + "（" + pfx + "）");
            assertTrue("monolith".equals(mode),
                    "S3 前 mode 必须 monolith（ADR-15 S0 现状）; 发现切流 service 态: " + pfx);
            i++;
        }
        assertTrue(seenPrefixes.size() >= 5, "去重后条目 <5（台账被压掉）; 实际 " + seenPrefixes.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/datanet", "/api/v1/engine/data", "/api/v1/data", "/api/v1/dq"})
    @DisplayName("C.2.1 数据域 4 条核心前缀全部登记且 owner=datanet（单源台账守）")
    void dataCorePrefixRegistered(String prefix) {
        for (JsonNode e : entries) {
            if (prefix.equals(text(e, "prefix"))) {
                assertEquals("datanet", text(e, "owner"),
                        prefix + " owner 必须=datanet（数据域前缀一律归属 datanet 域）");
                // artifact 限断言「承流在 data-engine 或 datanet service」两态之一（S0 monolith fat-JAR
                // 情况下实际是 data-engine-impl；S3 切流后 /api/v1/datanet → services/datanet）；
                // 此处不硬绑单一 artifact，只护「artifact 字段合法存在且指向 data/datlanet 系」
                String artifact = text(e, "artifact");
                assertNotNull(artifact, prefix + " artifact 字段必须存在");
                assertTrue(artifact.contains("data") || artifact.contains("datanet"),
                        prefix + " artifact 应指向数据域承流模块; 实际 " + artifact);
                return;
            }
        }
        throw new AssertionError("数据域核心前缀未登记到单源台账: " + prefix);
    }

    @Test
    @DisplayName("数据域前缀总量 assertion：datanet owner 的前缀数 ≥ 4（core 4 条白名单）")
    void dataOwnerFleetNotEmpty() {
        int dataOwned = 0;
        for (JsonNode e : entries) {
            if ("datanet".equals(text(e, "owner"))) {
                dataOwned++;
            }
        }
        assertTrue(dataOwned >= 4, "datanet owner 前缀数 <4（C.2.1 核心 + alias 期望；实际 " + dataOwned + "）");
    }

    @Test
    @DisplayName("同一 service 端口不得被两个不同 owner 占用（切流时防 gateway 路由混淆）")
    void servicePortNotSharedAcrossOwners() {
        // svc 端口 → 声明它的 owner（首个）
        Map<Long, String> portOwner = new HashMap<>();
        List<String> collisions = new ArrayList<>();
        for (JsonNode e : entries) {
            String owner = text(e, "owner");
            long svc = e.get("ports").get("service").asLong();
            String first = portOwner.putIfAbsent(svc, owner);
            if (first != null && !first.equals(owner)) {
                collisions.add("svc=" + svc + " 同时被 " + first + " 与 " + owner + " 声明");
            }
        }
        assertTrue(collisions.isEmpty(), "跨 owner service 端口撞用:\n  " + String.join("\n  ", collisions));
    }

    private static String text(JsonNode e, String field) {
        JsonNode v = e.get(field);
        return v == null ? null : v.asText();
    }
}
