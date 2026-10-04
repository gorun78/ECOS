package com.chinacreator.gzcm.engine.cognitive2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分册05 E.3 / F05-02 要点3 离线写路径护栏（护 R-13 ① 双表新建后 legacy 名不重建、无双写）。
 *
 * <p>背景（实查）：{@code workspace/.../ScenarioMindService.java:119} 至今仍在写
 * {@code INSERT INTO ecos_scenario_mind}（V146 legacy 名，库内无对象）——属 §14.4 待裁
 * 未授权切链<b>越界不动</b>；本护栏不代它切链，只锁两条独立性：</p>
 * <ol>
 *   <li>认知 orchestrator 主源码<b>零</b> {@code INSERT INTO kb_mind_registry}（PRD 误名 ADR-8 已
 *       明令禁用，任何新增引用都是回归）；</li>
 *   <li>认知 orchestrator 主源码<b>零</b>{@code INSERT INTO ecos_scenario_mind}（V146 legacy 名不
 *       从认知侧复活，切链只由 workspace 侧按 §14.4 授权切）。</li>
 * </ol>
 * <p>workspace 侧 legacy 现存写路径在<b>另一分冊窗口</b>的授权闸内保留（本护栏不越域代切），
 * 难以用一条 ratchet 表达"允许 legacy 但只减不增"故只锁认知侧零。</p>
 */
final class ScenarioMindWritePathTest {

    private static final Pattern INSERT_KB_MIND =
        Pattern.compile("\\bINSERT\\s+INTO\\s+kb_mind_registry\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSERT_LEGACY_SCENARIO_MIND =
        Pattern.compile("\\bINSERT\\s+INTO\\s+ecos_scenario_mind\\b", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("认知主源码零 kb_mind_registry INSERT（PRD 误名零复活）")
    void noInsertIntoKbMindRegistry() throws IOException {
        List<Path> files = CognitiveDocPaths.cognitiveMainJava();
        int hits = CognitiveDocPaths.countHits(files, INSERT_KB_MIND);
        assertEquals(0, hits, "认知侧出现 kb_mind_registry INSERT——ADR-8/Q11 已明令该名为 PRD 误名");
    }

    @Test
    @DisplayName("认知主源码零 legacy ecos_scenario_mind INSERT（V146 legacy 名不复活）")
    void noInsertIntoDroppedLegacyMindTable() throws IOException {
        List<Path> files = CognitiveDocPaths.cognitiveMainJava();
        int hits = CognitiveDocPaths.countHits(files, INSERT_LEGACY_SCENARIO_MIND);
        // workspace/.../ScenarioMindService.java:119 属另一分冊批窗口留待裁，不在本护栏扫描范围
        // （认知 engine main 内应零命中）
        assertTrue(hits == 0,
            "认知主源码出现 INSERT INTO ecos_scenario_mind——V146 legacy 名不重建（E.3 定版口径）");
    }
}
