package com.chinacreator.gzcm.engine.cognitive2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分册05 X-23 / W121/C102 认知侧路由归属护栏（网关级完整性由 gateway 侧承担）。
 *
 * <p>守护面：</p>
 * <ol>
 *   <li><b>类级 {@code @RequestMapping} 字面路径条目 ≤ 18</b>（2026-10-04 实测；17 处
 *       分发器 + 1 处在 CognitivePlannerController 走数组形式额外多 1 条 = 18）。
 *       新引入即红，需先过网关三滤波器（VersionPrefixRewrite + SecurityConfig permitAll
 *       + ClearanceInterceptor）后再放行。</li>
 *   <li><b>/api/v1/engine/cognitive legacy 双拥有面冻结为 1</b>（认知侧副拥有者）——认知
 *       侧当前由 CognitiveEngineOpenHealthController 单文件持有；主拥有者
 *       AiEngineStatusController 在 ai 引擎。字面串不能用两次。</li>
 * </ol>
 */
final class RouteUniquenessTest {

    @Test
    @DisplayName("认知 impl 类级 @RequestMapping 路径字面条目 只减不增（baseline 18 = 17 分发器 + 1 Planner 双路径；新引入即红）")
    void classLevelRequestMappingPathRatchetFreeze() throws IOException {
        List<Path> files = CognitiveDocPaths.cognitiveMainJava();
        int total = 0;
        for (Path f : files) {
            if (!f.getFileName().toString().endsWith("Controller.java")) continue;
            String body = Files.readString(f, StandardCharsets.UTF_8);
            for (String line : body.split("\n")) {
                String stripped = line.trim();
                if (!stripped.startsWith("@RequestMapping")) continue;
                int hits = stripped.split("\"/api", -1).length - 1;
                total += hits;
            }
        }
        assertTrue(total <= 18,
            "类级 @RequestMapping 字面路径条目 %d > 18（超出 2026-10-04 基线，新路由须先过网关三滤波器登记）"
                .formatted(total));
    }

    @Test
    @DisplayName("/api/v1/engine/cognitive 认知侧类级 @RequestMapping 冻结为 1（legacy 双拥有副方，主方 = ai 引擎）")
    void legacyDoubleOwnershipFrozen() throws IOException {
        List<Path> files = CognitiveDocPaths.cognitiveMainJava();
        int hits = 0;
        for (Path f : files) {
            if (!f.getFileName().toString().endsWith("Controller.java")) continue;
            String body = Files.readString(f, StandardCharsets.UTF_8);
            for (String line : body.split("\n")) {
                String stripped = line.trim();
                if (stripped.startsWith("@RequestMapping") && stripped.contains("/api/v1/engine/cognitive")) {
                    hits++;
                }
            }
        }
        assertEquals(1, hits,
            "认知侧 @RequestMapping 声明 /api/v1/engine/cognitive 类级命中 %d ≠ 1——legacy 双拥有面漂移"
                .formatted(hits));
    }
}
