package com.chinacreator.gzcm.engine.cognitive2.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 认知域契约单源 + 死调用归一（设计-05 F05-01/08 / C99, C104, C105, C118 / W117/W121/W123/W136）。
 *
 * <p>源码盘点：所有 {@code /api/v1/cognitive*}、{@code /api/v1/world-model/*}、
 * {@code /api/v1/business/scenarios/%s/cognition*} 字面路径必须登记白名单（防漂移）。</p>
 */
class CognitiveContractSingleSourceTest {

    private static final Path REPO = Path.of("../../../..").toAbsolutePath().normalize();
    private static final Path COG_IMPL = REPO.resolve("ecos_backend/engine/cognitive-engine/cognitive-engine-impl");

    private static final Path FROZEN_DOC = REPO.resolve(
            "docs/30-设计/详细设计-05-认知域-cognitive-engine与aiming-2026-09-29.md");

    private static final Pattern PATH_LITERAL = Pattern.compile(
            "@\\w*(Mapping|Request|GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)\\s*\\(\\s*(?:value\\s*=\\s*)?[\"'](/api/[^\"']+)[\"']");

    @Test
    @DisplayName("impl 领域 /api 字面路径全部登记白名单（旧路径 alias 保留 + 新 scenario-cognition 前缀允许）")
    void noInlineCognitivePathLiterals() throws IOException {
        assertTrue(Files.isDirectory(COG_IMPL), "cognitive-engine-impl 目录未找到: " + COG_IMPL);
        Set<String> whitelist = deferredWhitelist();
        List<String> hits = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(COG_IMPL)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.toString().contains("/test/"))
                .forEach(p -> scan(p, hits));
        }
        List<String> offenders = new ArrayList<>();
        for (String h : hits) {
            boolean ok = false;
            for (String w : whitelist) {
                if (h.equals(w)) { ok = true; break; }
                if (w.endsWith("*") && h.startsWith(w.substring(0, w.length() - 1))) { ok = true; break; }
            }
            if (!ok) offenders.add(h);
        }
        assertTrue(offenders.isEmpty(),
                "契约单源门禁：以下 /api 字面路径未登记（应改用 CognitiveApiPaths 常量或加入白名单）：\n"
                        + String.join("\n", offenders)
                        + "\n—— 允许新前缀 /api/v1/business/scenarios/%s/cognition/**");
    }

    private static Set<String> deferredWhitelist() {
        Set<String> w = new LinkedHashSet<>();
        // 本条批次新增（本 design-05 交付，属主 cognitive，冻结 path template）
        w.add("/api/v1/business/scenarios/%s/cognition/detect");
        w.add("/api/v1/business/scenarios/%s/cognition/operation-eval");
        w.add("/api/v1/business/scenarios/%s/cognition/hypotheses");
        w.add("/api/v1/business/scenarios/%s/cognition/beliefs");
        // 类级 @RequestMapping 类前缀（子路径以 /xxx 补全，属既有 legacy，白名单登记类级前缀即可）
        w.add("/api/v1/cognitive");
        w.add("/api/v1/cognitive/scenario");
        w.add("/api/v1/cognitive/demo/wave3");
        w.add("/api/v1/cognitive/demo");
        w.add("/api/v1/engine/cognitive");
        w.add("/api/v1/world-model");
        // 既有 cognitive2 控制器（F05-08 Deprecation 兼容，非本批次新引入，禁止再新加）
        w.add("/api/v1/cognitive/hypotheses");
        w.add("/api/v1/cognitive/hypotheses/{id}");
        w.add("/api/v1/cognitive/hypotheses/{id}/invalidate");
        w.add("/api/v1/cognitive/beliefs");
        w.add("/api/v1/cognitive/beliefs/{id}");
        w.add("/api/v1/cognitive/beliefs/{variable}/update-by-evidence");
        w.add("/api/v1/cognitive/beliefs/{variable}/override");
        w.add("/api/v1/cognitive/beliefs/{variable}/{version}/replay");
        w.add("/api/v1/cognitive/evidence");
        w.add("/api/v1/cognitive/evidence/{id}");
        w.add("/api/v1/cognitive/diagnose");
        w.add("/api/v1/cognitive/diagnose/history");
        w.add("/api/v1/cognitive/diagnose/preflight");
        w.add("/api/v1/cognitive/planner");
        w.add("/api/v1/cognitive/planner/*");
        w.add("/api/v1/cognitive/counterfactual");
        w.add("/api/v1/cognitive/forecast");
        w.add("/api/v1/cognitive/models");
        w.add("/api/v1/cognitive/models/{id}");
        w.add("/api/v1/cognitive/scenarios");
        w.add("/api/v1/cognitive/scenarios/list");
        w.add("/api/v1/cognitive/mental-reviews");
        w.add("/api/v1/cognitive/mental-reviews/*");
        w.add("/api/v1/cognitive/config");
        w.add("/api/v1/cognitive/config/*");
        w.add("/api/v1/cognitive/decision");
        w.add("/api/v1/cognitive/decision/*");
        w.add("/api/v1/cognitive/provenance");
        w.add("/api/v1/cognitive/provenance/*");
        w.add("/api/v1/cognitive/wave3-demo");
        w.add("/api/v1/cognitive/wave3-demo/*");
        w.add("/api/v1/cognitive/pipeline");
        w.add("/api/v1/cognitive/pipeline/*");
        w.add("/api/v1/knowledge/reason");
        w.add("/api/v1/knowledge/reason/models");
        w.add("/api/v1/rules/causal-chain/{ruleId}");
        w.add("/api/v1/rules/impact-analysis");
        w.add("/api/v1/rules/audit-logs");
        w.add("/api/v1/engine/cognitive/health");
        w.add("/api/v1/engine/cognitive/stats");
        // world-model 桩返 501（F05-16 显式 stub；保留 path 前台不 404）
        w.add("/api/v1/world-model/state");
        w.add("/api/v1/world-model/scenarios");
        w.add("/api/v1/world-model/strategy/recommend");
        w.add("/api/v1/world-model/causal-graph");
        return w;
    }

    private void scan(Path p, List<String> hits) {
        try {
            Matcher m = PATH_LITERAL.matcher(Files.readString(p));
            while (m.find()) {
                String path = m.group(2);
                // 只归入本测试关注前缀
                if (path.startsWith("/api/v1/cognitive") || path.startsWith("/api/v1/knowledge")
                        || path.startsWith("/api/v1/rules") || path.startsWith("/api/v1/engine/cognitive")
                        || path.startsWith("/api/v1/world-model")
                        || path.startsWith("/api/v1/business/scenarios")) {
                    hits.add(path);
                }
            }
        } catch (IOException e) {
            fail("扫描失败: " + p + " -> " + e.getMessage());
        }
    }
}
