package com.chinacreator.gzcm.sysman.portal;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F03-14 / W88 / 铁律 IR01 架构守卫《SysmanNoBuszhiTableArchTest》—
 * sysman 禁止直查 buszhi 域的 workflow 表。
 *
 * <p>架构铁律 IR01（数据域只由本域引擎访问）：workflow 表（历史命名
 * {@code ecos_workflow} / {@code ecos_workflow_v2}）属 buszhi 域，sysman（IAM）
 * 不得用 MyBatis/SQL 直接 {@code FROM}/{@code INTO} 该表，一律经 buszhi REST 单点
 * （见 {@code PortalSearchQueryService#searchWorkflows}）。原先
 * {@code searchWorkflows} 直接以 SQL 语法查该 workflow 表（FROM 该表）属跨域越界，
 * 现已改为 REST 调用，本守卫对 {@code services/sysman/} 下所有 {@code .java}
 * 源码扫描，断言<b>不存在</b>以 SQL 关键字（{@code FROM}/{@code INTO}/{@code JOIN}）
 * 引用 workflow 表 token 的语句。</p>
 *
 * <p>判定规则（最稳健）：任一 {@code .java} 文件只要出现
 * SQL 关键字后紧跟 workflow 表 token（即对该表的 SQL 直查/写入/连接）即违规 ——
 * sysman 域对 workflow 表零 SQL 直引是硬约束，任何出现都有越界嫌疑，故按
 * 绝对禁止口径报告文件+行号。文档性提及（非 SQL 语法）不在此列。</p>
 */
@DisplayName("F03-14/W88/IR01：sysman 全模块源码零 ecos_workflow 表引用（只经 buszhi REST）")
class SysmanNoBuszhiTableArchTest {

    /**
     * workflow 表 token 出现在 SQL 上下文中：须经 {@code FROM}/{@code INTO}/{@code JOIN}
     * 紧邻（区分真实 SQL 语句与文档性提及）。历史命名 {@code ecos_workflow} /
     * {@code ecos_workflow_v2} / 任意 {@code ecos_workflow_*} 变体。
     */
    private static final Pattern WORKFLOW_TABLE_SQL = Pattern.compile(
            "(?:FROM|INTO|JOIN)\\s+ecos_workflow[a-z0-9_]*", Pattern.CASE_INSENSITIVE);

    private static final String SYSMAN_REL = "ecos_backend/services/sysman";

    @Test
    @DisplayName("services/sysman 下所有 .java（main+test）不含 ecos_workflow* 表 token")
    void sysmanSourceHasNoWorkflowTableToken() throws IOException {
        Path root = findRepoRoot();
        Path sysmanDir = root.resolve(SYSMAN_REL);
        assertTrue(Files.isDirectory(sysmanDir), "缺 sysman 模块目录: " + sysmanDir);

        List<String> violations = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(sysmanDir)) {
            for (Path f : (List<Path>) stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .toList()) {
                List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                int lin = 0;
                for (String raw : lines) {
                    lin++;
                    if (WORKFLOW_TABLE_SQL.matcher(raw).find()) {
                        violations.add(root.relativize(f) + ":" + lin
                                + ": 引用 workflow 表 token: " + raw.trim());
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "W88/IR01：sysman 域禁止直查 buszhi 的 workflow 表（ecos_workflow*），"
                        + "一律经 buszhi REST 单点：\n" + String.join("\n", violations));
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
