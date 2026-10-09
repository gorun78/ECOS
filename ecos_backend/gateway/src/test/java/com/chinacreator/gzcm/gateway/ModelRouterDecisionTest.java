package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-06 §八 接缝义务 4 —— <b>Model Router 风险等级只来自 ABAC</b>（C255，ADR-20，REQ-AI-08）。
 *
 * <p>C255 的铁律：<b>风险等级只来自 security-engine ABAC</b>（{@code :530}）；引擎内自写
 * 一套"模型路由 / 风险判定"组件即 FAIL。且 {@code model-runtime} 新模块提案<b>作废</b>——
 * 不允许在 reactor 里新建一个自持风险/等级决策、据此选模型的路由器。
 *
 * <p>守卫判据（源扫描，主源码、剔除 archive 遗留）：全 reactor 不得出现任何自持的
 * 模型路由器 / 风险路由决策<b>组件类</b>（类名命中 {@code ModelRouter / ModelSelector /
 * ModelDispatcher / ModelChooser / ModelRoute / RiskRouter / ModelRisk} 之一）。
 *
 * <p><b>边界</b>：业务 LLM 工具 {@code calculateRisk}（输入概率×影响→数值风险分，属数据产物、
 * 非访问/等级决策）<b>不</b>在拦截面内；本守卫只针对"据自写风险等级沿途选模型"的<b>路由器组件</b>，
 * 与业务取数工具无关。</p>
 *
 * <p>验收：{@code mvn -Dtest=ModelRouterDecisionTest#riskLevelComesOnlyFromAbac}</p>
 */
public class ModelRouterDecisionTest {

    /** 自持"模型路由 / 风险据此选模型"组件类的命中模式（类定义级，非任意提及）。 */
    private static final Pattern ROUTER_CLASS_DEF = Pattern.compile(
            "(?:class|interface|record|enum)\\s+" +
                    "(ModelRouter|ModelSelector|ModelDispatcher|ModelChooser|ModelRoute\\w*" +
                    "|RiskRouter|ModelRiskDecision\\w*)\\b");

    @Test
    void riskLevelComesOnlyFromAbac() throws IOException {
        Path backend = backendRoot();
        List<Path> mainSources = mainSources(backend);
        assertTrue(!mainSources.isEmpty(), "未找到任何主源码，后端根定位异常: " + backend);

        List<String> offenders = new ArrayList<>();
        for (Path src : mainSources) {
            String rel = backend.relativize(src).toString().replace('\\', '/');
            // archive/ 下的遗留不参与"本 reactor 新建组件"判定
            if (rel.contains("/archive/")) {
                continue;
            }
            String srcText = new String(Files.readAllBytes(src), StandardCharsets.UTF_8);
            Matcher m = ROUTER_CLASS_DEF.matcher(srcText);
            while (m.find()) {
                offenders.add(rel + " → " + m.group(1));
            }
        }

        assertTrue(offenders.isEmpty(),
                "出现自持模型路由/风险决策组件类（C255：风险等级只来自 security-engine ABAC，"
                        + "引擎内自写判定=FAIL，model-runtime 新模块作废）：" + offenders);
    }

    // ── helpers ────────────────────────────────────────────────────────

    private static List<Path> mainSources(Path backend) throws IOException {
        try (Stream<Path> walk = Files.walk(backend)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .sorted()
                    .toList();
        }
    }

    private static Path backendRoot() {
        Path cur = Paths.get("").toAbsolutePath();
        while (cur != null) {
            if (Files.isDirectory(cur.resolve("gateway")) && Files.isRegularFile(cur.resolve("pom.xml"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("定位不到 ecos_backend 根（pom.xml + gateway/）");
    }
}
