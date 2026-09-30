package com.chinacreator.gzcm.engine.data.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F02-04 / W57 验收《NodeCatalogParityTest》— 节点类型目录与执行器 switch 同源对账。
 *
 * <p>架构铁律 §4.8.2「枚举边界一致」：{@link PipelineNodeTypesCatalog#SUPPORTED}
 * 是节点类型目录唯一权威；执行器 {@code PipelineExecutionService.executeNode} 的
 * switch 分支 label 是能力事实。二者必须同源一致，否则用户可拖出目录有/执行器拒
 * 或目录无/执行器认的漂移节点（W57/D-16）。</p>
 *
 * <p>对账两条（源码 + 反射，当前代码已合规，本测试永久守卫）：</p>
 * <ol>
 *   <li><b>目录 ↔ 执行器等值</b>：源码扫描 {@code PipelineExecutionService} 的
 *       {@code executeNode} switch（{@code return switch (type)} 块首个 {@code default} 之前的
 *       全部 {@code case "LABEL"}），与 {@code SUPPORTED} 集合<b>等值</b>。</li>
 *   <li><b>能力自洽</b>：{@code IMPLEMENTED ⊆ SUPPORTED}；{@code SUPPORTED\IMPLEMENTED}
 *       的每个类型 {@link PipelineNodeTypesCatalog#isAvailable(String)}=false 且
 *       {@link PipelineNodeTypesCatalog#unavailableReasonOf(String)} 非空；
 *       {@code SOURCE_CDC} 必为 available=false（W57 登记）。</li>
 * </ol>
 */
@DisplayName("F02-04/W57：节点目录 ↔ 执行器 switch 同源一致")
class NodeCatalogParityTest {

    /** 目录全集（权威）。 */
    private static final Set<String> SUPPORTED = PipelineNodeTypesCatalog.SUPPORTED;
    /** 执行器已实现子集（available=true）。 */
    private static final Set<String> IMPLEMENTED = PipelineNodeTypesCatalog.IMPLEMENTED;

    private static final Path EXECUTOR_REL = Paths.get(
            "ecos_backend", "engine", "data-engine", "data-engine-impl", "src", "main", "java",
            "com", "chinacreator", "gzcm", "engine", "data", "pipeline",
            "PipelineExecutionService.java");

    /** 执行器 switch 分支 label（大写枚举风格，含下划线）。 */
    private static final Pattern CASE_LABEL = Pattern.compile("case\\s+\"([A-Z][A-Z0-9_]*)\"");

    @Test
    @DisplayName("executeNode switch 分支 label 集合 == 目录 SUPPORTED 集合")
    void executorSwitchMatchesCatalog() throws IOException {
        Path executor = findRepoRoot().resolve(EXECUTOR_REL);
        assertTrue(Files.isRegularFile(executor), "缺 PipelineExecutionService 源文件: " + executor);
        String src = Files.readString(executor, StandardCharsets.UTF_8);

        Set<String> switchLabels = extractNodeSwitchLabels(src);
        assertFalse(switchLabels.isEmpty(), "未能从 executeNode 抽取任何 switch 分支 label（源结构变化需复核）");

        assertEquals(new LinkedHashSet<>(SUPPORTED), switchLabels,
                "节点目录 SUPPORTED 与执行器 executeNode switch 分支集合必须等值（铁律 §4.8.2 枚举边界一致）："
                        + "\n  目录(" + SUPPORTED.size() + ")=" + SUPPORTED
                        + "\n  执行器(" + switchLabels.size() + ")=" + switchLabels);
    }

    @Test
    @DisplayName("目录能力自洽：IMPLEMENTED⊆SUPPORTED，差集类型 available=false 且原因非空，SOURCE_CDC 必拒")
    void catalogCapabilitySelfConsistent() {
        // IMPLEMENTED 必为 SUPPORTED 子集（执行器实现的类型必须登记在册）
        assertTrue(SUPPORTED.containsAll(IMPLEMENTED),
                "IMPLEMENTED 必须是 SUPPORTED 子集（执行器实现的类型须在目录内）");

        // 差集 = 登记但执行器无真实实现的类型 → 保存侧必须拒（available=false）+ 原因非空
        Set<String> unavailable = new LinkedHashSet<>(SUPPORTED);
        unavailable.removeAll(IMPLEMENTED);
        assertEquals(subtract(SUPPORTED, IMPLEMENTED), unavailable);

        for (String type : unavailable) {
            assertFalse(PipelineNodeTypesCatalog.isAvailable(type),
                    "未实现的类型 " + type + " 必须 available=false（保存侧即拒，F02-04）");
            assertNotNull(PipelineNodeTypesCatalog.unavailableReasonOf(type),
                    "available=false 的 " + type + " 必有非空 unavailableReason");
            assertFalse(PipelineNodeTypesCatalog.unavailableReasonOf(type).isBlank(),
                    "available=false 的 " + type + " 的 unavailableReason 不得空白");
        }

        // W57 / D-16：SOURCE_CDC 登记在册但执行器未实现
        assertTrue(SUPPORTED.contains("SOURCE_CDC"), "目录必须登记 SOURCE_CDC");
        assertFalse(IMPLEMENTED.contains("SOURCE_CDC"), "SOURCE_CDC 执行器未实现，不得在 IMPLEMENTED");
        assertFalse(PipelineNodeTypesCatalog.isAvailable("SOURCE_CDC"),
                "SOURCE_CDC 必 available=false（W57）");
        assertNotNull(PipelineNodeTypesCatalog.unavailableReasonOf("SOURCE_CDC"));
        assertFalse(PipelineNodeTypesCatalog.unavailableReasonOf("SOURCE_CDC").isBlank());

        // 已实现类型必须 available=true + 原因为 null
        for (String type : IMPLEMENTED) {
            assertTrue(PipelineNodeTypesCatalog.isAvailable(type), "已实现类型 " + type + " 必 available=true");
            assertEquals(null, PipelineNodeTypesCatalog.unavailableReasonOf(type),
                    "已实现类型 " + type + " 的 unavailableReason 应为 null");
        }
    }

    // ── 工具 ─────────────────────────────────────────────────────────

    /** 从 executeNode 的 {@code return switch (type) { … default →}} 块抽取全部 case label。 */
    private static Set<String> extractNodeSwitchLabels(String src) {
        String anchor = "return switch (type)";
        int start = src.indexOf(anchor);
        assertTrue(start >= 0, "未找到 executeNode 的 `return switch (type)`（源结构变化需复核）");
        int endBound = src.indexOf("default ->", start);
        if (endBound < 0) {
            endBound = src.length();
        }
        String block = src.substring(start, endBound);
        Set<String> labels = new LinkedHashSet<>();
        Matcher m = CASE_LABEL.matcher(block);
        while (m.find()) {
            labels.add(m.group(1));
        }
        return labels;
    }

    private static Set<String> subtract(Set<String> a, Set<String> b) {
        Set<String> out = new LinkedHashSet<>(a);
        out.removeAll(b);
        return out;
    }

    static Path findRepoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path cursor = dir;
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve(".git"))
                    || Files.exists(cursor.resolve(".git"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        cursor = dir;
        while (cursor != null) {
            if (Files.exists(cursor.resolve("pom.xml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("无法从 user.dir=" + dir + " 定位仓库根（.git/pom.xml 均缺失）");
    }
}
