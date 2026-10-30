package com.chinacreator.gzcm.engine.cognitive2.honesty;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.controller.CognitiveRunReplayController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 附录A 第 16 条 run 级运行回放「显式 501 承流面」门禁（2026-10-30 #146al）。
 *
 * <p>契约行单源 = 《ECOS详细设计说明书-从零实施版》第 5415 行（{@code replayCognitiveRun}，
 * 档位 C，备注「两种互斥签名定版一种，另一种不建映射」，「回放不落盘」）与第 10194 行第 8 篇
 * 机读码行 {@code ECOS-COG-503（501 + stub=true）}；2026-10-30 用户二值裁决定版 =
 * <b>无参纯 stub 形态</b>（0 入参 GET，回真实 501 + stub=true；定版另一种签名不建映射）。</p>
 *
 * <p><b>本门禁钉的是「承流形状诚实」，不是「能力成立」</b>——run 级回放能力仍未实现
 * （F05-12 守护项 1 推理结果不落盘 ⇒ 回放载体缺位），差距台账对该条的判定不因本批翻转，
 * 也不计入命中率。所以断言面刻意含三条防「把桩读成功」的负向腿：
 * 501 载荷不得含任何数据键；新方法面子形态必须恰好是一枚无参 GET（第三种签名形态的
 * 路由级由 {@code RouteUniquenessTest#cognitiveReplaySingleSignature} 双签白名单兜住）；
 * 信封必须出自 {@code CognitiveStubResponses} 工厂出口（接口面上 echo 的是常量单源路径串）。</p>
 *
 * <ol>
 *   <li>{@link #statusLineAndEnvelopeAreReal501()} — 真实 HTTP 501 + code=501 +
 *       {@code ECOS-COG-503} + 冻结 message + {@code data.stub=true}；</li>
 *   <li>{@link #stubPayloadFrozenKeysOnly()} — 载荷键面冻结为 6 枚，逐键值自证
 *       （owner/replay/contract path/missing 取证串），禁任何数据键回潮；</li>
 *   <li>{@link #surfaceIsSingleNoArgGetOnConstantPrefix()} — 类前缀单值 {@link CognitiveApiPaths#COGNITIVE}、
 *       恰一枚 {@code @GetMapping}（路径常量单源、0 参数）、零写映射（X-48 第三种签名在路由
 *       级被亮度门卡住，本测从形态面再钉一道）。</li>
 * </ol>
 */
@DisplayName("附录A 第 16 条 run 级 replay 501 承流面")
class RunReplayStubContractTest {

    /** 501 载荷的冻结键面（多一枚＝伪数据，少一枚＝定位取证缺项）。 */
    private static final Set<String> FROZEN_STUB_KEYS =
            Set.of("stub", "reason", "owner", "segment", "path", "missing");

    /** 任何「看起来像数据」的键名——出现即把未实现伪装成有实现。 */
    private static final Set<String> DATA_BEARING_KEYS =
            Set.of("chain", "trace", "traces", "runId", "runs", "steps", "nodes", "edges",
                    "confidence", "suggestions", "items", "total", "list");

    private final CognitiveRunReplayController controller = new CognitiveRunReplayController();

    @Test
    @DisplayName("真实 HTTP 501 + code=501 + ECOS-COG-503 + 冻结 message + stub=true（禁 200 包壳）")
    void statusLineAndEnvelopeAreReal501() {
        ResponseEntity<ApiResponse<Map<String, Object>>> re = controller.replayCognitiveRun();
        assertEquals(HttpStatus.NOT_IMPLEMENTED, re.getStatusCode(),
                "run 级回放必须真实 501 状态行，禁 200 包壳（DG-9/X-43）");
        ApiResponse<Map<String, Object>> body = re.getBody();
        assertNotNull(body, "响应体不得为空");
        assertEquals(501, body.getCode(), "信封 code 与状态行同号");
        assertEquals(CognitiveErrorCode.E503_NOT_IMPLEMENTED, body.getErrorCode(),
                "机读码取第 10194 行第 8 篇冻结行");
        assertEquals(CognitiveErrorCode.MSG_NOT_IMPLEMENTED, body.getMessage(),
                "message 只能是冻结字面量（F05-07 禁泄漏）");
        assertNotNull(re.getBody().getData(), "501 必须带 data 供前端渲染占位");
        assertEquals(Boolean.TRUE, re.getBody().getData().get("stub"),
                "DG-9 要求 data.stub=true（前端 501 分支判据）");
    }

    @Test
    @DisplayName("载荷键面冻结 6 枚 + 逐键值自证；任何数据键出现即伪实现回潮")
    void stubPayloadFrozenKeysOnly() {
        Map<String, Object> data = controller.replayCognitiveRun().getBody().getData();
        assertEquals(FROZEN_STUB_KEYS, data.keySet(),
                "载荷键面漂移（多一枚＝伪数据，少一枚＝取证缺项）");
        assertEquals("cognitive", data.get("owner"),
                "未实现面必须自证属主，禁让调用方误读成跨域依赖缺口");
        assertEquals("replay", data.get("segment"), "段名自证");
        assertEquals(CognitiveApiPaths.REPLAY, data.get("path"),
                "data.path 必须逐字等于契约行绝对路径（常量单源，禁各写一套）");
        String missing = (String) data.get("missing");
        assertNotNull(missing, "必须给缺载体取证，禁只回一句「未实现」");
        assertTrue(missing.startsWith("缺"), "missing 必须是缺载体取证串，实际=" + missing);
        assertTrue(missing.length() >= 20, "取证串不得退化成短语，实际=" + missing);
        for (String banned : DATA_BEARING_KEYS) {
            assertTrue(!data.containsKey(banned),
                    "501 载荷不得含数据键 " + banned + "（空列表/空对象同属伪装成功）");
        }
    }

    @Test
    @DisplayName("类前缀单值 COGNITIVE + 恰一枚无参 GET（常量单源路径）+ 零写映射")
    void surfaceIsSingleNoArgGetOnConstantPrefix() {
        RequestMapping root = CognitiveRunReplayController.class.getAnnotation(RequestMapping.class);
        assertNotNull(root, "承流面必须挂 @RequestMapping 前缀");
        assertEquals(1, root.value().length, "类级前缀必须单值（禁旧式 /api/ 双前缀）");
        assertEquals(CognitiveApiPaths.COGNITIVE, root.value()[0],
                "复用既有前缀 ⇒ VersionPrefixRewriteFilter 25 条基线零改动（该族 KEEP v1）");

        int gets = 0;
        Map<String, Object> writeMappings = new LinkedHashMap<>();
        for (Method m : CognitiveRunReplayController.class.getDeclaredMethods()) {
            PostMapping p = m.getAnnotation(PostMapping.class);
            PutMapping pu = m.getAnnotation(PutMapping.class);
            DeleteMapping d = m.getAnnotation(DeleteMapping.class);
            PatchMapping pa = m.getAnnotation(PatchMapping.class);
            if (p != null || pu != null || d != null || pa != null) {
                writeMappings.put(m.getName(), "write");
            }
            GetMapping g = m.getAnnotation(GetMapping.class);
            if (g == null) continue;
            gets++;
            assertEquals(1, g.value().length, m.getName() + "：路径必须单值");
            assertEquals(CognitiveApiPaths.REPLAY_REL, g.value()[0],
                    m.getName() + "：方法级必须挂相对段常量（字面量分叉＝CognitiveContractSingleSourceTest 判据）");
            assertEquals(0, m.getParameterCount(),
                    m.getName() + "：定版 = 无参 GET（0 入参），禁自造查询段把缺参当默认全量");
        }
        assertEquals(0, writeMappings.size(), "第 16 条是只读承流面，出现写映射即越界：" + writeMappings);
        assertEquals(1, gets, "契约面恰一枚 @GetMapping，实到 " + gets);
    }
}
