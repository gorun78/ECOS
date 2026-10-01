package com.chinacreator.gzcm.buszhi.workflow;

import static org.junit.jupiter.api.Assertions.assertTrue;

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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W85/O-17 审批表可达性守卫（《详细设计-03-本体域》W85，设计分册 03 §E.3/§E.4）。
 *
 * <p>W85 根因：{@code ecos_workflow_approval}（及 {@code _v2/_instance/_task/_log}）经
 * V173 只存在于控制域 {@code ecos_ontology} schema；代码里任何<b>裸表名</b>
 * （前字符既非 {@code .} 亦非反引号）在运行时必然 <b>relation does not exist</b>
 * —— 结构性必败（R-7 归属①）。</p>
 *
 * <p>本守卫静态扫描 {@code ecos_backend/services/buszhi}（main + test 全量 .java）：
 * 去除注释后，任何 SQL 串中出现「非 {@code .}/{@code`} 前缀」的
 * {@code ecos_workflow[a-z_]*} 表名引用 → FAIL。白名单 = 本守卫族自身
 * （自扫描正则/断言字符串含表名字面量，属自反引用）。</p>
 */
@DisplayName("W85/M0：workflow 系列表必须 schema 限定（裸 ecos_workflow_* 引用即 red）")
class WorkflowApprovalTableReachableTest {

    /**
     * BARE = 「非 {@code .}/{@code`} 前缀」的 {@code ecos_workflow*}。
     * 前缀排除 {@code .}（schema 限定，合法）与 `` ` ``（反引号限定，合法）。
     */
    private static final Pattern BARE = Pattern.compile("(?<![.`])ecos_workflow[a-z0-9_]*\\b");

    /** 白名单：本守卫族自身（正则/断言字面量必然含表名，属自反引用不是 SQL 事实）。 */
    private static final List<String> SELF_GUARD_FILES = List.of(
            "WorkflowApprovalTableReachableTest.java",
            "WorkflowSingleModelArchTest.java",
            "WorkflowSoftDeleteTest.java"
    );

    @Test
    @DisplayName("buszhi 全量 .java 无裸 ecos_workflow* 表引用（须 schema. 限定）")
    void noBareWorkflowTableRefs() throws IOException {
        Path buszhi = locateBuszhi();
        assertTrue(Files.isDirectory(buszhi), "未定位到 ecos_backend/services/buszhi: " + buszhi);

        List<String> violations = new ArrayList<>();
        int[] scanned = {0};
        try (Stream<Path> walk = Files.walk(buszhi)) {
            walk.filter(Files::isRegularFile)
                .filter(f -> f.getFileName().toString().endsWith(".java"))
                .forEach(f -> scan(f, violations, scanned));
        }

        assertTrue(scanned[0] > 0, "buszhi 目录未扫描到任何 .java（路径漂移？）");
        assertTrue(violations.isEmpty(),
            "W85/M0 违例：以下 Java 源含裸 ecos_workflow* 表引用（未见 schema. 或反引号限定，运行必 relation not exist）。"
            + "修正方向：改走 {schema}.ecos_workflow* 限定名（见 WorkflowApprovalRepository.SCHEMA）。命中：\n"
            + String.join("\n", violations));
    }

    private void scan(Path file, List<String> violations, int[] scanned) {
        if (SELF_GUARD_FILES.contains(file.getFileName().toString())) {
            return;
        }
        String src;
        try {
            src = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return;
        }
        scanned[0]++;
        String stripped = WorkflowArchSources.stripComments(src);
        Matcher m = BARE.matcher(stripped);
        while (m.find()) {
            // 仅当所在行含 SQL 动词（SELECT/INSERT/UPDATE/DELETE/FROM/JOIN/INTO/WHERE/VALUES/CREATE/ALTER）
            // 时才视为「SQL 表引用」；JavaDoc/字符串说明不提 SQL 动词的走注释兜底不报。
            int lineStart = stripped.substring(0, m.start()).lastIndexOf('\n');
            int lineEnd = stripped.indexOf('\n', m.end());
            String line = (lineEnd < 0 ? stripped : stripped.substring(0, lineEnd))
                    .substring(lineStart < 0 ? 0 : lineStart + 1);
            if (line.toUpperCase().contains("SELECT ") || line.toUpperCase().contains("INSERT ")
                    || line.toUpperCase().contains("UPDATE ") || line.toUpperCase().contains("DELETE ")
                    || line.toUpperCase().contains("FROM ") || line.toUpperCase().contains("JOIN ")
                    || line.toUpperCase().contains("INTO ") || line.toUpperCase().contains("WHERE ")) {
                violations.add(file.toString().replace('\\', '/') + " : \"" + m.group() + "\"（裸表名，须 schema 限定）");
            }
        }
    }

    static Path locateBuszhi() {
        return findRepoRoot().resolve("ecos_backend").resolve("services").resolve("buszhi");
    }

    static Path findRepoRoot() {
        Path cursor = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve(".git"))) return cursor;
            cursor = cursor.getParent();
        }
        cursor = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (cursor != null) {
            if (Files.isRegularFile(cursor.resolve("ecos_backend" + java.io.File.separator + "pom.xml"))) return cursor;
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("无法从 user.dir 定位仓库根（.git / ecosystem pom 均缺）");
    }
}
