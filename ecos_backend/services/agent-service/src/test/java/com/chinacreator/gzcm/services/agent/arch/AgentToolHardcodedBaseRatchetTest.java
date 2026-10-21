package com.chinacreator.gzcm.services.agent.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * agent 工具腿内联 {@code localhost:8080} 基址的<b>棘轮门禁</b>（#142/B4-3e-8，登记差距 <b>D-B4-46</b>）。
 *
 * <p><b>背景</b>：既有 {@code EndpointResolverUsageArchTest}（data-engine）只钉 {@code localhost:1808x}
 * 形态，{@code :8080}（monolith 端口）形态全仓无门。本域 9 支工具此前都把
 * {@code http://localhost:8080} 整串内联在 {@code ForObject} 请求点上，绕开 route-manifest 两态寻址单源；
 * 切流即断，且现网 gateway 与 monolith 同端口时问题被掩盖（D-B4-43 的正身即此族的 security 裁决腿）。</p>
 *
 * <p><b>#142/B4-3e-8 收口 D-B4-43 后</b>：{@code CheckPermissionTool} 改取
 * {@link com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentToolEndpointResolver}
 * 单源基址，硬编码请求点消失（其 javadoc 里作为"收口前形态"提及的字面量不带 {@code ForObject(} 前缀，
 * 故不被本判据命中——锚定调用形态天然对注释安全，无需剥注释器）。
 * 其余 8 支工具的内联基址属<b>独立缺陷 D-B4-46</b>：它们命中非 security 族端点，
 * 各自需按 route-manifest 归属解析、不在本批顺手动刀。</p>
 *
 * <p><b>棘轮纪律</b>：清单＝实测 8 支，只准缩短，新增内联请求点即红；收口一支须同步删一项。
 * 这是 D-B4-44／45 同族做法，把"没人知道的第八个读侧"式的静默扩散关在门外。</p>
 */
class AgentToolHardcodedBaseRatchetTest {

    /** 命中判据：{@code ForObject("http://localhost:8080} —— 只有真实请求点才带此前缀。 */
    private static final String HARDCODED_SHAPE = "ForObject(\"http://localhost:8080";

    /** D-B4-46 在册基线（实测 8 支）；只准缩短，新增即红。 */
    private static final Set<String> BASELINE = Set.of(
            "AuditAccessLogTool.java",
            "ExecuteActionTool.java",
            "GetObjectPropertiesTool.java",
            "GetObjectRelationshipsTool.java",
            "GetSecurityProfileTool.java",
            "ListObjectTypesTool.java",
            "SearchOntologyGraphTool.java",
            "ValidateMappingTool.java");

    @Test
    @Timeout(value = 60)
    @DisplayName("工具内联 localhost:8080 请求点必须逐个在册（D-B4-46），新增即红、收口须同步删项")
    void hardcodedBaseSitesAreRatcheted() throws IOException {
        Path toolsDir = toolsDir();
        assertTrue(Files.isDirectory(toolsDir),
                "工具目录不可达，棘轮门禁不得对缺目录默认放行: " + toolsDir);

        Set<String> found = new TreeSet<>();
        try (Stream<Path> files = Files.list(toolsDir)) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                String code = Files.readString(p, StandardCharsets.UTF_8);
                if (code.contains(HARDCODED_SHAPE)) {
                    found.add(p.getFileName().toString());
                }
            }
        }
        assertEquals(BASELINE, found,
                "agent 工具内联基址面与 D-B4-46 在册清单不符（新增未登记＝又一条切流即断的腿；"
                        + "收口未删项＝棘轮失效）。实测=" + found);
    }

    @Test
    @DisplayName("已闭合的 CheckPermissionTool 不得回归硬编码请求点（D-B4-43 的基址半句）")
    void checkPermissionToolStaysOffHardcodedBase() throws IOException {
        Path tool = toolsDir().resolve("CheckPermissionTool.java");
        String code = Files.readString(tool, StandardCharsets.UTF_8);
        assertTrue(!code.contains(HARDCODED_SHAPE),
                "CheckPermissionTool 退回内联 localhost:8080 请求点 ⇒ D-B4-43 复发");
        assertTrue(code.contains("AgentToolEndpointResolver") && code.contains("securityBaseUrl()"),
                "CheckPermissionTool 未走基址单源解析器");
    }

    /** 从模块工作目录上溯定位 {@code ecos_backend}，再取工具源码目录（缺则 fail-loud）。 */
    private static Path toolsDir() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("ecos_backend"))) {
            dir = dir.getParent();
        }
        assertTrue(dir != null, "上溯未找到 ecos_backend 根，无法定位工具目录");
        Path tools = dir.resolve("ecos_backend")
                .resolve("services/agent-service/src/main/java/com/chinacreator/gzcm/services/agent/runtime/toolrouter/tools");
        assertTrue(Files.isDirectory(tools), "工具源码目录不存在: " + tools);
        return tools;
    }
}
