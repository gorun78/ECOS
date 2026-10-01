package com.chinacreator.gzcm.buszhi.workflow;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W86/MC 单模型架构守卫（《详细设计-03-本体域》W86，R-7 归属① + 模型 b + DR05）。
 *
 * <p>工作流域单一模型 = {@code {schema}.ecos_workflow}（nodes/edges 形态）。
 * 本守卫扫描 {@code services/buszhi/.../buszhi/workflow/} 包（main + test）下的 .java，禁止：</p>
 * <ul>
 *   <li>{@code ?::jsonb} — 参数化类型强转，多库不兼容（MC03）；JSON 文本一律 String 绑定；</li>
 *   <li>{@code DELETE FROM ecos_workflow} — 物理删除（DR05 / W86/O-18，须软删 {@code is_deleted=1}）；</li>
 *   <li>{@code SELECT * FROM ecos_workflow} — 显式列清单缺失（IR04）。</li>
 * </ul>
 *
 * <p>配套：软删语义回归由 {@link WorkflowSoftDeleteTest}（Mockito）承担；
 * 表名 schema 限定可达性由 {@link WorkflowApprovalTableReachableTest} 承担。
 *
 * <p>2026-09-30（W86/M1；W149 载体；F03-12/MC 单模型红线）。
 */
@DisplayName("W86/MC：workflow 单模型 — 禁 ?::jsonb / 物理 DELETE / SELECT * FROM ecos_workflow")
class WorkflowSingleModelArchTest {

    @Test
    @DisplayName("workflow 包内禁 ?::jsonb / DELETE FROM ecos_workflow / SELECT * FROM ecos_workflow")
    void noJdbcCastsNoPhysicalDeleteNoSelectStar() throws IOException {
        Path wfDir = WorkflowApprovalTableReachableTest.locateBuszhi()
                .resolve("impl").resolve("buszhi-impl").resolve("src")
                .resolve("main").resolve("java").resolve("com").resolve("chinacreator").resolve("gzcm").resolve("buszhi").resolve("workflow");
        // 同时兜底扫 test 包（防回归把违例写进测试 SQL）
        Path wfTest = WorkflowApprovalTableReachableTest.locateBuszhi()
                .resolve("impl").resolve("buszhi-impl").resolve("src")
                .resolve("test").resolve("java").resolve("com").resolve("chinacreator").resolve("gzcm").resolve("buszhi").resolve("workflow");

        List<String> violations = new ArrayList<>();
        scanDir(wfDir, violations);
        if (Files.isDirectory(wfTest)) {
            scanDir(wfTest, violations);
        }

        assertTrue(violations.isEmpty(),
            "W86/MC 单模型违例（含 ?::jsonb / 物理 DELETE FROM ecos_workflow / SELECT * FROM ecos_workflow）：\n"
            + String.join("\n", violations)
            + " 修正方向：JSON 文本以 ? 绑定 String；删除改软删 is_deleted=1；SELECT 用显式列清单。");
    }

    private void scanDir(Path dir, List<String> violations) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                .filter(f -> f.getFileName().toString().endsWith(".java"))
                .forEach(f -> scanFile(f, violations));
        }
    }

    private void scanFile(Path f, List<String> violations) {
        String src;
        try {
            src = Files.readString(f, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return;
        }
        // 只扫代码事实：去注释后判（?::jsonb / DELETE FROM / SELECT * FROM 三红线均可出现在真实 SQL 串）
        String stripped = WorkflowArchSources.stripComments(src);
        if (stripped.contains("::jsonb")) {
            violations.add(f.toString() + " : 含 ?::jsonb（参数化 JSON 类型强转，MC03 多库不兼容）");
        }
        if (upperContains(stripped, "DELETE FROM ECOS_WORKFLOW") || upperContains(stripped, "DELETE FROM ECOS_WORKFLOW_V2")) {
            violations.add(f.toString() + " : 含物理 DELETE FROM ecos_workflow（DR05/W86 须软删 is_deleted=1）");
        }
        if (upperContains(stripped, "SELECT * FROM ECOS_WORKFLOW")) {
            violations.add(f.toString() + " : 含 SELECT * FROM ecos_workflow（IR04 须显式列清单）");
        }
    }

    /** 大小写不敏感子串（SQL 关键词大写化后 contains）。 */
    private static boolean upperContains(String s, String needle) {
        return s.toUpperCase().replace("\n", " ").contains(needle);
    }
}
