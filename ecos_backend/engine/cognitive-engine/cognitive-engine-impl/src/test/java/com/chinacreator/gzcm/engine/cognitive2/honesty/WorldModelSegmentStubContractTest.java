package com.chinacreator.gzcm.engine.cognitive2.honesty;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.WorldModelSegment;
import com.chinacreator.gzcm.engine.cognitive2.controller.CognitiveWorldModelReadController;
import com.chinacreator.gzcm.engine.cognitive2.controller.WorldModelController;
import com.chinacreator.gzcm.engine.cognitive2.guard.CogScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 附录A 第 17 条 world-model 四段「显式 501 承流面」门禁（2026-10-28 #146w）。
 *
 * <p>契约行单源 = 《ECOS详细设计说明书-从零实施版》第 5415 行四 handler
 * （{@code getWorldModelEntities / getWorldModelRelations / getWorldModelTraces / getWorldModelRules}）
 * 与第 10194 行第 8 篇机读码行 {@code ECOS-COG-503（501 + stub=true）}；
 * 处置口径单源 = 详细设计-05 <b>DG-9</b>／X-43／W136（M0）。</p>
 *
 * <p><b>本门禁钉的是「承流形状诚实」，不是「能力成立」</b>——世界模型能力仍未实现，
 * 差距台账对第 17 条的判定不因本批翻转。所以断言面刻意含三条防「把桩读成功」的负向腿：
 * 501 载荷不得含任何数据键；契约四段名与 legacy 四名两两不相交（偏差本身仍在册）；
 * 两套「未实现」信封必须出自同一工厂出口（前端 501 分支只该有一套判据）。</p>
 *
 * <ol>
 *   <li>{@link #everySegmentCarriesRealStatusLineAndEvidence()} — 四段各回真实 HTTP 501 +
 *       {@code code=501} + {@code ECOS-COG-503} + {@code data.stub=true} + 定位四键；</li>
 *   <li>{@link #stubPayloadCarriesNoData()} — 载荷键面冻结为 6 枚，禁任何「看起来有数据」的键
 *       （X-43 判死的是 200 + 固定值，空列表同属伪数据）；</li>
 *   <li>{@link #segmentEnumAndPathConstantsAgree()} — 段名／相对段／绝对段三处形态由
 *       {@link WorldModelSegment} 与 {@link CognitiveApiPaths} 互证，禁字面量分叉；</li>
 *   <li>{@link #surfaceIsReadOnlyWithLiteralSegments()} — 类前缀单值 {@code COGNITIVE}、恰四枚 GET、
 *       零写映射、零路径变量（未知段名必须真实 404，不得被 501 掩蔽）、零 {@code replay} 字样
 *       （附录A 第 16 条定版未裁）；</li>
 *   <li>{@link #legacyStubFaceShapeIsUnchanged()} — legacy {@code /api/v1/world-model} 四路径与
 *       信封一字未动（只增不改）；</li>
 *   <li>{@link #contractSegmentsDoNotReplaceLegacyFaces()} — 契约段名与 legacy 面名称不相交，
 *       第 17 条「契约名 ≠ 实现名」偏差继续在册；</li>
 *   <li>{@link #oneFactoryForBothNotImplementedFaces()} — 「未实现」原因串在全认知主源只出现一次，
 *       双桩面不得各写一份信封。</li>
 * </ol>
 */
@DisplayName("附录A 第 17 条 world-model 四段 501 承流面")
class WorldModelSegmentStubContractTest {

    /** 契约行原文四段路径（从零实施版第 5415 行），逐字比对用。 */
    private static final Map<WorldModelSegment, String> CONTRACT_PATHS = contractPaths();

    /** 新面 501 载荷的冻结键面（多一枚＝伪数据，少一枚＝定位取证缺项）。 */
    private static final Set<String> FROZEN_STUB_KEYS =
            Set.of("stub", "reason", "owner", "segment", "path", "missing");

    /** legacy 面载荷的冻结键面（本批不得顺带扩键）。 */
    private static final Set<String> LEGACY_STUB_KEYS = Set.of("stub", "reason");

    /** 任何「看起来像数据」的键名——出现即把未实现伪装成有实现。 */
    private static final List<String> DATA_BEARING_KEYS = List.of(
            "entities", "relations", "traces", "rules", "confidence", "items", "total",
            "edges", "nodes", "state", "causalGraph", "recommendation", "list");

    private static final String SOURCE_UNDER_TEST =
            "com/chinacreator/gzcm/engine/cognitive2/controller/CognitiveWorldModelReadController.java";

    private final CognitiveWorldModelReadController controller = new CognitiveWorldModelReadController();

    private static Map<WorldModelSegment, String> contractPaths() {
        Map<WorldModelSegment, String> m = new LinkedHashMap<>();
        m.put(WorldModelSegment.ENTITIES, "/api/v1/cognitive/world-model/entities");
        m.put(WorldModelSegment.RELATIONS, "/api/v1/cognitive/world-model/relations");
        m.put(WorldModelSegment.TRACES, "/api/v1/cognitive/world-model/traces");
        m.put(WorldModelSegment.RULES, "/api/v1/cognitive/world-model/rules");
        return m;
    }

    /** 段 → 契约 handler 名（第 5415 行原文命名）。 */
    private static String handlerName(WorldModelSegment segment) {
        return switch (segment) {
            case ENTITIES -> "getWorldModelEntities";
            case RELATIONS -> "getWorldModelRelations";
            case TRACES -> "getWorldModelTraces";
            case RULES -> "getWorldModelRules";
        };
    }

    /** 反射按<b>契约 handler 名</b>调用——命名与路径由 ④ 的注解断言绑死，此处不靠方法声明顺序。 */
    @SuppressWarnings("unchecked")
    private ResponseEntity<ApiResponse<Map<String, Object>>> invoke(WorldModelSegment segment) {
        try {
            Method m = CognitiveWorldModelReadController.class.getMethod(handlerName(segment));
            return (ResponseEntity<ApiResponse<Map<String, Object>>>) m.invoke(controller);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("契约 handler 不可调用：" + handlerName(segment), e);
        }
    }

    // ── ① 状态行 + 定位取证 ──────────────────────────────────────────

    @Test
    @DisplayName("四段各回真实 HTTP 501 + ECOS-COG-503 + stub=true + 定位四键")
    void everySegmentCarriesRealStatusLineAndEvidence() {
        for (WorldModelSegment segment : WorldModelSegment.values()) {
            ResponseEntity<ApiResponse<Map<String, Object>>> re = invoke(segment);
            assertEquals(HttpStatus.NOT_IMPLEMENTED, re.getStatusCode(),
                    segment + " 必须真实 501 状态行，禁 200 包壳（DG-9）");

            ApiResponse<Map<String, Object>> body = re.getBody();
            assertNotNull(body, segment + " 响应体不得为空");
            assertEquals(501, body.getCode(), segment + "：信封 code 与状态行同号");
            assertEquals(CognitiveErrorCode.E503_NOT_IMPLEMENTED, body.getErrorCode(),
                    segment + "：机读码取第 8 篇冻结行");
            assertEquals(CognitiveErrorCode.MSG_NOT_IMPLEMENTED, body.getMessage(),
                    segment + "：message 只能是冻结字面量（F05-07 #4 禁泄漏）");

            Map<String, Object> data = body.getData();
            assertNotNull(data, segment + "：501 必须带 data 供前端渲染占位（禁与真实空态混淆）");
            assertEquals(Boolean.TRUE, data.get("stub"), segment + "：DG-9 要求 data.stub=true");
            assertEquals("cognitive", data.get("owner"),
                    segment + "：未实现面必须自证属主，禁让调用方误读成跨域依赖缺口");
            assertEquals(segment.pathSegment(), data.get("segment"), segment + "：段名自证");
            assertEquals(CONTRACT_PATHS.get(segment), data.get("path"),
                    segment + "：data.path 必须逐字等于契约行路径");

            String missing = (String) data.get("missing");
            assertNotNull(missing, segment + "：必须给缺载体取证，禁只回一句「未实现」");
            assertTrue(missing.startsWith("缺"), segment + "：missing 必须是缺载体取证串，实际=" + missing);
            assertTrue(missing.length() >= 20, segment + "：取证串不得退化成短语，实际=" + missing);
        }
    }

    // ── ② 载荷不含任何数据键 ─────────────────────────────────────────

    @Test
    @DisplayName("501 载荷键面冻结为 6 枚；任何数据形态键出现即伪实现回潮")
    void stubPayloadCarriesNoData() {
        for (WorldModelSegment segment : WorldModelSegment.values()) {
            Map<String, Object> data = invoke(segment).getBody().getData();
            assertEquals(FROZEN_STUB_KEYS, data.keySet(),
                    segment + "：载荷键面漂移（多一枚＝伪数据，少一枚＝取证缺项）");
            for (String banned : DATA_BEARING_KEYS) {
                assertTrue(!data.containsKey(banned),
                        segment + "：501 载荷不得含数据键 " + banned + "（空列表/空对象同属伪装成功）");
            }
            // 反向补强：X-43 判死的固定值形态，一个字段都不准留在响应里
            assertNull(data.get("confidence"), segment + "：禁固定 confidence（X-43 原伪实现形态）");
        }
    }

    // ── ③ 段名 / 路径单源互证 ────────────────────────────────────────

    @Test
    @DisplayName("WorldModelSegment 与 CognitiveApiPaths 互证：段名/相对段/绝对段不得字面量分叉")
    void segmentEnumAndPathConstantsAgree() {
        assertEquals(4, WorldModelSegment.values().length, "契约四段，多一段或少一段都是漂移");

        Set<String> seenSegments = new HashSet<>();
        Map<String, WorldModelSegment> byPath = new LinkedHashMap<>();
        for (WorldModelSegment segment : WorldModelSegment.values()) {
            assertTrue(seenSegments.add(segment.pathSegment()), "段名重复：" + segment.pathSegment());
            byPath.put(CONTRACT_PATHS.get(segment), segment);

            assertEquals("/world-model/" + segment.pathSegment(), segment.relativePath(),
                    segment + "：相对段必须由 WORLD_MODEL_REL + 段名拼装，禁各写一套");
            assertTrue(segment.relativePath().startsWith(CognitiveApiPaths.WORLD_MODEL_REL + "/"),
                    segment + "：相对段必须挂在 " + CognitiveApiPaths.WORLD_MODEL_REL + " 之下");
            assertEquals(CONTRACT_PATHS.get(segment),
                    CognitiveApiPaths.COGNITIVE + segment.relativePath(),
                    segment + "：枚举拼出的绝对路径与契约行不符");
            assertNotNull(segment.missingCarrier(), segment + "：缺载体取证不得为空");
        }
        assertEquals(4, byPath.size(), "四段路径必须两两不同");

        // 枚举侧段名与常量侧段名互证（注解值必须是编译期常量，故两侧都留，靠本断言锁死）
        assertEquals(CognitiveApiPaths.WORLD_MODEL_SEGMENT_ENTITIES, WorldModelSegment.ENTITIES.pathSegment());
        assertEquals(CognitiveApiPaths.WORLD_MODEL_SEGMENT_RELATIONS, WorldModelSegment.RELATIONS.pathSegment());
        assertEquals(CognitiveApiPaths.WORLD_MODEL_SEGMENT_TRACES, WorldModelSegment.TRACES.pathSegment());
        assertEquals(CognitiveApiPaths.WORLD_MODEL_SEGMENT_RULES, WorldModelSegment.RULES.pathSegment());

        // 绝对常量（文档/契约引用侧）与枚举拼装的形态一致
        assertEquals(CONTRACT_PATHS.get(WorldModelSegment.ENTITIES), CognitiveApiPaths.WORLD_MODEL_ENTITIES);
        assertEquals(CONTRACT_PATHS.get(WorldModelSegment.RELATIONS), CognitiveApiPaths.WORLD_MODEL_RELATIONS);
        assertEquals(CONTRACT_PATHS.get(WorldModelSegment.TRACES), CognitiveApiPaths.WORLD_MODEL_TRACES);
        assertEquals(CONTRACT_PATHS.get(WorldModelSegment.RULES), CognitiveApiPaths.WORLD_MODEL_RULES);
    }

    // ── ④ 表面形态：只读 + 字面段 + 零 replay ─────────────────────────

    @Test
    @DisplayName("类前缀单值 COGNITIVE + 恰四枚 GET + 零写映射 + 零路径变量 + 零 replay 字样")
    void surfaceIsReadOnlyWithLiteralSegments() throws IOException {
        RequestMapping root = CognitiveWorldModelReadController.class.getAnnotation(RequestMapping.class);
        assertNotNull(root, "承流面必须挂 @RequestMapping 前缀");
        assertEquals(1, root.value().length, "类级前缀必须单值（禁 /api/ 旧前缀双写）");
        assertEquals(CognitiveApiPaths.COGNITIVE, root.value()[0],
                "复用既有前缀 ⇒ VersionPrefixRewriteFilter 不新增条目（该族 KEEP v1，25 条基线不动）");

        Set<String> handlerNames = new HashSet<>();
        int gets = 0;
        for (Method m : CognitiveWorldModelReadController.class.getDeclaredMethods()) {
            assertTrue(m.getAnnotation(PostMapping.class) == null
                            && m.getAnnotation(PutMapping.class) == null
                            && m.getAnnotation(DeleteMapping.class) == null
                            && m.getAnnotation(PatchMapping.class) == null,
                    m.getName() + "：第 17 条是只读契约面，出现写映射即越界");
            GetMapping get = m.getAnnotation(GetMapping.class);
            if (get == null) continue;
            gets++;
            handlerNames.add(m.getName());
            assertEquals(1, get.value().length, m.getName() + "：路径必须单值");
            String value = get.value()[0];
            assertTrue(!value.startsWith("/api/"),
                    m.getName() + "：方法级只能用相对段（绝对常量会拼成双前缀——SUP-D 教训）：" + value);
            assertTrue(!value.contains("{") && !value.contains("*"),
                    m.getName() + "：段名必须是字面量。用 {segment}/通配会把拼错的段也答成 501，"
                            + "令「未实现」覆盖「不存在」，两态不可分：" + value);
            assertEquals(0, m.getParameterCount(),
                    m.getName() + "：契约行入参为 —（无参），禁自造查询段把缺参当成默认全量");
            for (Annotation[] pas : m.getParameterAnnotations()) {
                for (Annotation a : pas) assertTrue(!(a instanceof PathVariable), "本面无路径变量");
            }
        }
        assertEquals(4, gets, "契约四段 ⇒ 恰四枚 @GetMapping，实到 " + gets);

        Set<String> expectedNames = new HashSet<>();
        for (WorldModelSegment s : WorldModelSegment.values()) expectedNames.add(handlerName(s));
        assertEquals(expectedNames, handlerNames, "handler 名与契约行原文不一致：" + handlerNames);

        // 源码面（剥注释）：零 replay 字样——第 16 条互斥签名定版未裁，本批不建 replay 映射，
        // 且认知域 replay 映射唯一性判据（恰 1 枚，RouteUniquenessTest）不得被本类撞动。
        String src = CogScope.stripComments(Files.readString(CogScope.MAIN.resolve(SOURCE_UNDER_TEST)));
        assertTrue(!src.toLowerCase().contains("replay"),
                "承流面代码出现 replay 字样：附录A 第 16 条定版未裁，禁顺带建映射");
    }

    // ── ⑤ legacy 面一字未动 ──────────────────────────────────────────

    @Test
    @DisplayName("legacy /api/v1/world-model 四路路径与 {stub,reason} 信封一字未动（只增不改）")
    void legacyStubFaceShapeIsUnchanged() {
        RequestMapping root = WorldModelController.class.getAnnotation(RequestMapping.class);
        assertEquals("/api/v1/world-model", root.value()[0], "legacy 前缀不得被本批改动");

        Map<String, String> legacy = new LinkedHashMap<>();
        for (Method m : WorldModelController.class.getDeclaredMethods()) {
            GetMapping g = m.getAnnotation(GetMapping.class);
            PostMapping p = m.getAnnotation(PostMapping.class);
            if (g != null) legacy.put(g.value()[0], m.getName());
            if (p != null) legacy.put(p.value()[0], m.getName());
        }
        Map<String, String> expectedLegacy = new LinkedHashMap<>();
        expectedLegacy.put("/state", "getCurrentState");
        expectedLegacy.put("/scenarios", "simulate");
        expectedLegacy.put("/strategy/recommend", "recommendStrategy");
        expectedLegacy.put("/causal-graph", "getCausalGraph");
        assertEquals(expectedLegacy, legacy,
                "legacy 四端点路径/方法名漂移（本批只补 cognitive 面，不裁 legacy 面）");

        // 信封：旧面只出 {stub, reason}；新增三键只属新面，旧分支不得被迫改判
        ResponseEntity<ApiResponse<Map<String, Object>>> re = new WorldModelController().getCurrentState();
        assertEquals(HttpStatus.NOT_IMPLEMENTED, re.getStatusCode(),
                "legacy 面状态行不得被本批改动（真实 501 是 DG-9 底线）");
        Map<String, Object> legacyData = re.getBody().getData();
        assertEquals(LEGACY_STUB_KEYS, legacyData.keySet(), "legacy 载荷键面漂移：" + legacyData.keySet());
        assertEquals(Boolean.TRUE, legacyData.get("stub"));
        assertNull(legacyData.get("segment"), "旧面不得被本批顺带塞入 segment");
        assertNull(legacyData.get("missing"), "旧面不得被本批顺带塞入 missing");
    }

    // ── ⑥ 偏差继续在册：契约段名 ≠ legacy 名 ─────────────────────────

    @Test
    @DisplayName("契约四段与 legacy 四名两两不相交 ⇒ 第 17 条偏差仍在册（端点载体在位 ≠ 能力成立）")
    void contractSegmentsDoNotReplaceLegacyFaces() {
        Set<String> contract = new HashSet<>();
        for (WorldModelSegment s : WorldModelSegment.values()) contract.add(s.pathSegment());
        assertEquals(Set.of("entities", "relations", "traces", "rules"), contract);

        Set<String> legacy = Set.of("state", "scenarios", "strategy/recommend", "causal-graph");
        assertTrue(Collections.disjoint(contract, legacy),
                "两套世界模型面不得同名互替（附录A 第 17 条登记的正是「契约名 ≠ 实现名」）");
        assertEquals("/api/v1/cognitive/world-model",
                CognitiveApiPaths.COGNITIVE + CognitiveApiPaths.WORLD_MODEL_REL, "承流族前缀形态");
    }

    // ── ⑦ 双桩面同源 ────────────────────────────────────────────────

    @Test
    @DisplayName("两套「未实现」信封同源：原因串在全认知主源只出现一次")
    void oneFactoryForBothNotImplementedFaces() throws IOException {
        String reasonLiteral = "\"capability not implemented";
        List<String> owners = new ArrayList<>();
        try (Stream<Path> walk = CogScope.streamMainAll()) {
            for (Path p : walk.toList()) {
                String src = CogScope.stripComments(Files.readString(p));
                int hits = occurrences(src, reasonLiteral);
                if (hits > 0) owners.add(p.getFileName() + ":" + hits);
            }
        }
        assertEquals(List.of("CognitiveStubResponses.java:1"), owners,
                "「未实现」原因串必须单源在桩工厂；多处出现＝两套文案必然漂移，"
                        + "前端 501 分支只会认其中一套");
    }

    private static int occurrences(String s, String needle) {
        int i = 0, n = 0;
        while ((i = s.indexOf(needle, i)) >= 0) { n++; i += needle.length(); }
        return n;
    }
}
