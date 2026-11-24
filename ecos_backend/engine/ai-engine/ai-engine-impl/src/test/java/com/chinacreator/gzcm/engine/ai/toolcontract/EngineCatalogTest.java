package com.chinacreator.gzcm.engine.ai.toolcontract;

import com.chinacreator.gzcm.engine.ai.toolcontract.EngineCatalog.EngineEntry;
import com.chinacreator.gzcm.engine.ai.toolcontract.EngineCatalog.EngineKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Engine 同构映射守卫（详细设计-10 F10-02；验收名 {@code everyEngineMapsToLiveServicePrefix}）。
 *
 * <p>断言四件事实（单源 = {@link EngineCatalog}）：</p>
 * <ol>
 *   <li>每个 Engine 条目登记的 REST 前缀都是「在位服务前缀」形态（{@code /api/} 起、段边界 {@code /} 收），
 *       且前缀族互不重叠（重叠 = 属主单源被破坏）；</li>
 *   <li>门面型横切条目（runtime 底座）不声明 REST 前缀，因而不参与 REST 归属判定（F10-03 红线 2 的本意：
 *       W 域经进程内门面/工厂触达底座，禁自建 HTTP 直连）；</li>
 *   <li>{@code /api/v1/wagent} 属主唯一 = ai-engine（Decision/Option/Action 属主，R-32①）；</li>
 *   <li>禁入前缀（{@code /api/v1/cognition}、legacy {@code /api[/v1]/agent}）在解析层 fail-loud 归空。</li>
 * </ol>
 */
class EngineCatalogTest {

    @Test
    @DisplayName("F10-02：逐 Engine 校验在位服务前缀——形态合法、族间不重叠、门面型条目显式为空")
    void everyEngineMapsToLiveServicePrefix() {
        List<EngineEntry> entries = EngineCatalog.entries();
        assertEquals(EngineKey.values().length, entries.size(),
                "目录必须覆盖全部 EngineKey（附件五大 Engine + security/runtime 横切）");

        List<String> facadeOnly = new ArrayList<>();
        Set<String> claimedPrefixes = new HashSet<>();
        for (EngineEntry entry : entries) {
            assertNotNull(entry.attachmentName(), entry.key() + " 缺附件 Engine 名");
            assertNotNull(entry.ecosService(), entry.key() + " 缺 ECOS 服务名");
            assertNotNull(entry.ownerDocs(), entry.key() + " 缺属主卷/REQ 锚点");
            assertNotNull(entry.writeChannel(), entry.key() + " 缺写通道口径");

            if (entry.livePathPrefixes().isEmpty()) {
                facadeOnly.add(entry.key().name());
                continue;
            }
            for (String prefix : entry.livePathPrefixes()) {
                assertTrue(prefix.startsWith("/api/"),
                        entry.key() + " 前缀必须是 REST 服务前缀: " + prefix);
                assertTrue(prefix.endsWith("/"),
                        entry.key() + " 前缀须以 / 收段边界（防 /api/v1/agentx 型误匹配）: " + prefix);
                assertTrue(EngineCatalog.isLiveServicePrefix(prefix + "probe"),
                        entry.key() + " 前缀未通过自身在位校验: " + prefix);
                assertTrue(claimedPrefixes.add(prefix),
                        "前缀族被两个 Engine 同时认领（属主单源破坏）: " + prefix);
            }
        }
        // 门面型条目 = runtime 底座（进程内门面，无 REST 直连面）。
        assertEquals(List.of("RUNTIME"), facadeOnly, "只允许 runtime 横切条目为空前缀（门面型）");
    }

    @Test
    @DisplayName("F10-02：样本路径归属唯一 Engine（六引擎各命中一次，且只命中一次）")
    void samplePathsResolveToTheirOwnerEngine() {
        assertEquals(Optional.of(EngineKey.DATA),
                EngineCatalog.resolveLiveServicePrefix("/api/v1/datanet/facts/query"));
        assertEquals(Optional.of(EngineKey.DATA),
                EngineCatalog.resolveLiveServicePrefix("/api/v1/dq/checks"));
        assertEquals(Optional.of(EngineKey.DATA),
                EngineCatalog.resolveLiveServicePrefix("/api/v1/pipeline/runs"));
        assertEquals(Optional.of(EngineKey.ONTOLOGY),
                EngineCatalog.resolveLiveServicePrefix("/api/v1/ontology/entities"));
        assertEquals(Optional.of(EngineKey.KNOWLEDGE),
                EngineCatalog.resolveLiveServicePrefix("/api/v1/knowledge/profiles"));
        assertEquals(Optional.of(EngineKey.COGNITIVE),
                EngineCatalog.resolveLiveServicePrefix("/api/v1/cognitive/models"));
        assertEquals(Optional.of(EngineKey.DECISION_AI),
                EngineCatalog.resolveLiveServicePrefix("/api/v1/wagent/runs"));
        assertEquals(Optional.of(EngineKey.SECURITY),
                EngineCatalog.resolveLiveServicePrefix("/api/v1/security/policy-engine/evaluate"));
        // runtime 底座无 REST 面 ⇒ 任何 /api/v1/runtime 路径都不该被认领（禁绕过门面直连）。
        assertFalse(EngineCatalog.isLiveServicePrefix("/api/v1/runtime/tasks"));
        // 未登记的父前缀不构成归属（段边界有效性）。
        assertFalse(EngineCatalog.isLiveServicePrefix("/api/v1/engine/"));
    }

    @Test
    @DisplayName("F10-02/F10-03：禁入前缀黑名单与空值一律归空（认知正典 = /api/v1/cognitive，X-18→R-43）")
    void bannedAndBlankPathsResolveToEmpty() {
        for (String banned : EngineCatalog.BANNED_FOREIGN_PREFIXES) {
            assertFalse(EngineCatalog.isLiveServicePrefix(banned + "/anything"),
                    "禁入前缀被解析为在位服务: " + banned);
        }
        assertFalse(EngineCatalog.isLiveServicePrefix("/api/v1/cognition/models"));
        assertFalse(EngineCatalog.isLiveServicePrefix("/api/v1/agent/runs"));
        assertFalse(EngineCatalog.isLiveServicePrefix("/api/agent/runs"));
        assertFalse(EngineCatalog.isLiveServicePrefix(null));
        assertFalse(EngineCatalog.isLiveServicePrefix("  "));
    }

    @Test
    @DisplayName("R-32①：Decision/Option/Action 属主唯一 = ai-engine；cognitive ecos_decision* 定性只读 Decision Basis")
    void decisionOwnershipAndCognitiveReadOnly() {
        EngineEntry decision = EngineCatalog.entry(EngineKey.DECISION_AI).orElseThrow();
        assertTrue(decision.ecosService().contains("ai-engine"), "Decision 属主必须是 ai-engine: " + decision.ecosService());
        assertTrue(decision.livePathPrefixes().contains(EngineCatalog.WAGENT_BASE_PREFIX + "/"),
                "W 域正典前缀必须登记在 DECISION_AI: " + decision.livePathPrefixes());
        assertFalse(decision.readOnlyFromW(), "本域即属主，禁自我只读定性");

        EngineEntry cognitive = EngineCatalog.entry(EngineKey.COGNITIVE).orElseThrow();
        assertTrue(cognitive.readOnlyFromW(), "cognitive 既有 ecos_decision* = Decision Basis（只读引用，R-32①）");
        assertTrue(EngineCatalog.entry(EngineKey.SECURITY).orElseThrow().readOnlyFromW(),
                "security 横切：只调用不写库");
    }

    @Test
    @DisplayName("F10-02：落库引擎代号单源（V236 engine 列值名录；workspace 场景层不入工具面 = 红线 3）")
    void contractEngineCodesAreTheDdlVocabulary() {
        Set<String> codes = EngineCatalog.contractEngineCodesForDdlCheck();
        assertEquals(Set.of("data", "ontology", "kb", "cognitive", "ai", "security"), codes);
        assertFalse(codes.contains("workspace"), "workspace 场景层禁入工具面（红线 3）");
        assertFalse(codes.contains("agent"), "引擎代号禁出现 agent 别名（F10-01）");
        assertEquals("data", EngineCatalog.contractEngineCode(EngineKey.DATA));
        assertEquals("cognitive", EngineCatalog.contractEngineCode(EngineKey.COGNITIVE));
    }

    @Test
    @DisplayName("单源不可变：目录与代号集对外只读（防运行期被改写破坏属主裁定）")
    void catalogIsImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> EngineCatalog.entries().add(null));
        assertThrows(UnsupportedOperationException.class,
                () -> EngineCatalog.entry(EngineKey.DATA).orElseThrow().livePathPrefixes().add("/x/"));
        assertThrows(UnsupportedOperationException.class,
                () -> EngineCatalog.contractEngineCodesForDdlCheck().add("workspace"));
    }

    private static void assertNotNull(Object value, String message) {
        assertTrue(value != null && !String.valueOf(value).isBlank(), message + "（空值）");
    }
}
