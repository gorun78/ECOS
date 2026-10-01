package com.chinacreator.gzcm.engine.ontology;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * W78/C62 验收 — 指标定义<b>语义二分</b>：
 *
 * <ol>
 *   <li>{@code public.ecos_biz_metric}（75 行 demo 数值，外键ecos_wm_goal）= <b>业务域指标实例/结果</b>，
 *       本体域**零读写**（治 O-12 孤表定性）；</li>
 *   <li>{@code ecos_ontology.metric_definition}（0 行）= 旧<b>定义表</b>，R-4 a+b 下停写；</li>
 *   <li>{@code ecos_ontology.ecos_metric_definition}（V168.1 新建）= <b>唯一可写入口</b>（新写路径落此）。
 * </ol>
 *
 * <p>纯源码 lint：不启 DataSource（§14.4 未授权），只扫本体引擎源码字符串常量，
 * 证明"本体域不读不写业务域指标实例表"这条读表隔离红线。
 */
class OntologyNoForeignTableAccessArchTest {

    /**
     * 只扫<b>生产代码</b>（src/main）——本测试自身的 test 源码里引用表名是合法描述，不纳入判定。
     * F03-09 红线针对的是生产读表路径，非测试描述文字。
     */
    private static final String[] ENGINE_MAIN_ROOTS = {
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main",
            "ecos_backend/engine/ontology-engine/ontology-engine-api/src/main",
            "ecos_backend/engine/ontology-engine/ontology-engine-boot/src/main"
    };

    /** 本体域不应读写的<b>业务域指标实例</b>表（孤儿业务表）。 */
    private static final String FOREIGN_BIZ_METRIC = "ecos_biz_metric";

    @Test
    void ontologyCodeShouldNotReferenceForeignBizMetricTable() throws IOException {
        List<Path> files = collectJavaFiles();
        assertTrue(!files.isEmpty(), "未扫到任何本体引擎 .java 源文件，测试基线异常");

        List<String> hits = new java.util.ArrayList<>();
        for (Path f : files) {
            String s = Files.readString(f, StandardCharsets.UTF_8);
            // 只拦截 SQL/字符串常量中的真实表名，命中的位置必须不再是合法边界（如 "ecos_biz_metric_result" 应该放行）
            if (containsBareTableName(s, FOREIGN_BIZ_METRIC)) {
                hits.add(f.toString());
            }
        }
        assertTrue(hits.isEmpty(),
            "本体域源码不应引用业务域指标实例表 `" + FOREIGN_BIZ_METRIC + "`（F03-09 语义二分）；命中：\n  "
            + String.join("\n  ", hits));
    }

    /** 是否存在"裸表名"引用（前后不接 _.）。 */
    private static boolean containsBareTableName(String src, String table) {
        int i = 0;
        while ((i = src.indexOf(table, i)) >= 0) {
            char before = i == 0 ? ' ' : src.charAt(i - 1);
            char after = i + table.length() < src.length() ? src.charAt(i + table.length()) : ' ';
            boolean prefixOk = (before != '_' && (before == ' ' || before == '"' || before == '\''
                || before == '\n' || before == '\t' || before == '(' || before == '['));
            boolean suffixOk = (after != '_' && (after == ' ' || after == '"' || after == '\''
                || after == '\n' || after == '\t' || after == ')' || after == ']' || after == ','
                || after == '.'));
            if (prefixOk && suffixOk) {
                // ".ecos_biz_metric" 项目考虑 schema 限定 Foo.ecos_biz_metric 也算裸（跨 schema 都禁）
                return true;
            }
            i += table.length();
        }
        return false;
    }

    private static List<Path> collectJavaFiles() throws IOException {
        Path base = locateRoot();
        List<Path> out = new java.util.ArrayList<>();
        for (String rel : ENGINE_MAIN_ROOTS) {
            Path root = base.resolve(rel);
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> s = Files.walk(root)) {
                s.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .forEach(out::add);
            }
        }
        return out;
    }

    private static Path locateRoot() {
        Path cur = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 10 && cur != null; i++) {
            if (Files.exists(cur.resolve("ecos_backend")) && Files.exists(cur.resolve("ecos_frontend"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        fail("无法定位仓库根目录以扫描 ontology-engine 源码");
        return null;
    }
}
