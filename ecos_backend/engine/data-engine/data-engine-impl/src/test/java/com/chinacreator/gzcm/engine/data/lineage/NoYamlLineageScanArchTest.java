package com.chinacreator.gzcm.engine.data.lineage;

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
 * F02-11 / W64 架构守卫（NoYamlLineageScanArchTest，源码扫描，无 DB）：
 * 血缘派生从 pipeline <b>definition</b> 派生后，业务代码<b>禁</b>再以
 * {@code yaml_content ILIKE/LIKE} 全表模糊扫 {@code ecos_pipeline_task} 反查血缘
 * （D-23 违规面：全表 ILIKE 扫 + 无索引 + 字符串拼接 LIKE 参数）。
 *
 * <p>扫描 data-engine-impl {@code src/main/**\.java}（含 {@code DataLineageService} 等），
 * 命中即 FAIL。注释行（trim 后以 {@code //} 或 {@code *}/{@code /*} 开头）忽略。
 * 存储侧（PipelineTask yaml 持久化）与 UI 层原有的 {@code yaml_content ILIKE} 匹配
 * 仍合法（例 {@code YamlTaskReadOnlyTest} 只针对 readOnly 校验，非血缘查询）——
 * 本测试仅限血缘服务包 {@code engine.data.service.DataLineageService} 与其同包。</p>
 */
@DisplayName("F02-11/W64 架构守卫：血缘服务禁 yaml_content ILIKE 模糊查")
class NoYamlLineageScanArchTest {

    /** 违规模式：数据引擎血缘/服务侧对 yaml_content 做 ILIKE/LIKE 模糊匹配。 */
    private static final Pattern YAMLC_LIKE = Pattern.compile(
            "yaml_content\\s+(?:ILIKE|LIKE)\\b", Pattern.CASE_INSENSITIVE);

    /** data-engine-impl 模块在仓库根下的相对前缀。 */
    private static final String MODULE_REL =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data";

    @Test
    @DisplayName("血缘/数据服务源码禁出现 yaml_content ILIKE/LIKE 全表模糊扫（D-23）")
    void noYamlContentLikeInDataEngineMainSource() throws IOException {
        Path moduleJavaRoot = findRepoRoot().resolve(MODULE_REL);
        assertTrue(Files.isDirectory(moduleJavaRoot),
                "缺模块源码根：" + moduleJavaRoot);
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(moduleJavaRoot)) {
            for (Path f : (List<Path>) files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                int lin = 0;
                for (String raw : lines) {
                    lin++;
                    String t = raw.trim();
                    if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) {
                        continue;
                    }
                    if (YAMLC_LIKE.matcher(raw).find()) {
                        violations.add(f.getFileName() + ":" + lin + ": " + t);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "F02-11/W64：数据域血缘/服务源码禁止 yaml_content ILIKE/LIKE 全表模糊扫"
                        + "（W64 血缘从 definition 派生 + 参数化写入，命中即 FAIL）：\n"
                        + String.join("\n", violations));
    }

    /** 从 user.dir 逐级上溯找到仓库根（含 .git 目录或 .git 文件）。 */
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
