package com.chinacreator.gzcm.engine.data;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F02-01（详细设计-02，DATA P0）—— 数据源<b>单承流面，禁两份 SQL</b>。
 *
 * <p>设计 F02-01 验收「{@code DatasourceSingleSurfaceArchTest}：源码断言
 * {@code /api/v1/datasource}（{@code PMO45DataSourceController}，兼容别名）与
 * {@code /api/v1/datanet/datasource}（{@code DataSourceController}）的 handler 均委托<b>同一
 * service</b>（{@code DataSourceRegistryService}）；且<b>两个 Controller 内都不出现内联 SQL /
 * 直连驱动</b>（源内无 DML 语句、无 {@code JdbcTemplate}/{@code DriverManager}）——否则即"两份 SQL"。</p>
 *
 * <p>文本层权威护栏（对齐 {@link NoDriverManagerArchTest} 先例）：先定位两个 Controller 源文件，
 * 再分别断言 —— ① 均声明 {@code DataSourceRegistryService} 依赖；② 共享 CRUD 动词
 * （register/listAll/getById/update/remove）都经该 surface 转发（不写各一份 SQL）；③ 无任何内联
 * DML/直连驱动；④ 两前缀路径存在。若某 Controller 出现 SQL 或在别处自拼查/写路径，本测试即红。</p>
 */
@DisplayName("F02-01 数据源单承流面 / 禁两份 SQL（源码护栏，P0）")
class DatasourceSingleSurfaceArchTest {

    private static final Pattern DML =
            Pattern.compile("(?i)\\b(INSERT\\s+INTO|SELECT\\s+.+\\s+FROM|UPDATE\\s+\\w+\\s+SET|DELETE\\s+FROM)");
    private static final Pattern DIRECT =
            Pattern.compile("\\b(JdbcTemplate|java\\.sql\\.DriverManager|DriverManager\\.getConnection)\\b");

    @Test
    @DisplayName("DatasourceSingleSurfaceArchTest — 两 controller 均委托 DataSourceRegistryService、共享 CRUD 同 surface、无内联 SQL")
    void bothControllersDelegateToSameService_noInlineSql() throws IOException {
        Path root = RlsInjectionGuardArchTest.resolveModuleSrcMainJava();
        String primary = readUnder(root, "DataSourceController.java");
        String alias = readUnder(root, "PMO45DataSourceController.java");
        assertTrue(primary != null, "未找到 DataSourceController（/api/v1/datanet/datasource 承流面）");
        assertTrue(alias != null, "未找到 PMO45DataSourceController（/api/v1/datasource 兼容别名）");

        // ① 同一 service 依赖（single surface = DataSourceRegistryService，非各一份）
        assertTrue(primary.contains("DataSourceRegistryService"),
                "承流面 DataSourceController 必须依赖 DataSourceRegistryService");
        assertTrue(alias.contains("DataSourceRegistryService"),
                "别名 PMO45DataSourceController 必须依赖同一 DataSourceRegistryService（不得另立一套）");

        // ② 共享 CRUD 动词经同一 surface 转发（两文件都调用这组方法，杜绝"两份 SQL/两套查询"）
        String[] crud = {"register(", "listAll(", "getById(", ".update(", ".remove("};
        for (String verb : crud) {
            assertTrue(primary.contains(verb), "承流面缺共享 CRUD 动词: " + verb);
            assertTrue(alias.contains(verb), "兼容别名缺共享 CRUD 动词（应委托同一 service）: " + verb);
        }

        // ③ 前缀路径存在（与 C.2.1 路由单源一致）
        assertTrue(primary.contains("/api/v1/datanet/datasource"), "承流面前缀应含 /api/v1/datanet/datasource");
        assertTrue(alias.contains("/api/v1/datasource"), "兼容别名前缀应含 /api/v1/datasource");

        // ④ 两文件均无内联 DML + 无直连驱动（禁两份 SQL 的正向护栏）
        assertNoSqlOrDirect("DataSourceController", primary);
        assertNoSqlOrDirect("PMO45DataSourceController", alias);
    }

    private void assertNoSqlOrDirect(String file, String src) {
        List<String> hits = new ArrayList<>();
        var dml = DML.matcher(src);
        while (dml.find()) hits.add("DML: " + dml.group().trim());
        var direct = DIRECT.matcher(src);
        while (direct.find()) hits.add("direct: " + direct.group().trim());
        assertTrue(hits.isEmpty(),
                "F02-01 违规 —— " + file + " 内出现内联 SQL / 直连驱动（源应只委托 DataSourceRegistryService）:\n"
                        + String.join("\n", hits));
    }

    /** 在 src/main/java 子树内按文件名唯一定位并读取源文本。 */
    private String readUnder(Path root, String fileName) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> matched = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(fileName))
                    .toList();
            if (matched.isEmpty()) {
                return null;
            }
            if (matched.size() > 1) {
                fail("存在多份 " + fileName + "，单承流面护栏无法判定: " + matched);
                return null;
            }
            return Files.readString(matched.get(0), StandardCharsets.UTF_8);
        }
    }
}
