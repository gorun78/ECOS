package com.chinacreator.gzcm.engine.cognitive2.schema;

import com.chinacreator.gzcm.engine.cognitive2.guard.CogScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 表名 {schema}. 限定门禁（设计-05 F05-13 / 数据库访问规范 §四附则1，分册 03 C69 同族机制
 * 扩展到认知契约面）：裸 {@code ecos_*} 表引用一律 FAIL（schema 白名单除外）。
 */
class SchemaDriftLintTest {

    private static final Pattern TABLE_TOKEN = Pattern.compile("\\becos_[a-z0-9_]+\\b");
    /** schema 名与已知非表 token（引用为 public.x / ecos_cognitive.x 时前字符是 '.' 不判裸）。 */
    private static final Set<String> SCHEMAS = Set.of(
            "ecos_cognitive", "ecos_control", "ecos_data", "ecos_ontology",
            "ecos_knowledge", "ecos_ai", "ecos_dw");

    @Test
    @DisplayName("认知契约面表引用全部 schema 限定（§四附则1）")
    void cognitiveTablesAreSchemaQualified() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (var walk = CogScope.streamInScope()) {
            walk.forEach(p -> {
                try {
                    String src = java.nio.file.Files.readString(p);
                    Matcher m = TABLE_TOKEN.matcher(src);
                    while (m.find()) {
                        String tok = m.group();
                        if (SCHEMAS.contains(tok)) continue;
                        int start = m.start();
                        boolean qualified = start > 0 && src.charAt(start - 1) == '.';
                        if (!qualified) {
                            offenders.add(p.getFileName() + ":" + CogScope.lineOf(src, start) + " :: " + tok);
                        }
                    }
                } catch (IOException ignored) {}
            });
        }
        assertTrue(offenders.isEmpty(),
                "裸表名违例（必须 {schema}. 限定）：\n" + String.join("\n", offenders));
    }
}
