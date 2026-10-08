package com.chinacreator.gzcm.engine.cognitive2.arch;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveModelType;
import com.chinacreator.gzcm.engine.cognitive2.guard.CogScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-COG-07 规格 1 枚举单源门禁（详设-05 D.2 规则 3；本批 #86 自设项）。
 *
 * <p>三条可机械执行的单源纪律：</p>
 * <ol>
 *   <li><b>字面量单源</b>：整棵 {@code ecos_backend} 生产源（src/main/java）中，
 *       独占式存储字面量 {@code "CM-0x"}（引号包裹、全匹配）只准出现在
 *       {@code CognitiveModelType.java} 的枚举构造参数里——其他文件想要 CM 值
 *       必须引用枚举（{@code CognitiveModelType.CM_07.code()}），禁私设副本。
 *       注释先剥离：Javadoc 里"CM-01~CM-07"的规格陈述不是副本；</li>
 *   <li><b>声明单源</b>：全仓生产源只准一处 {@code enum CognitiveModelType} 声明，
 *       且枚举常量 CM_01~CM_07 的<b>集合</b>必须与 PRD-05 冻结的 7 类逐一对应
 *       （数量 7、编码连续）——新增/删除类型先改需求单源再改契约；</li>
 *   <li><b>DDL 无枚举 CHECK</b>：迁移单源目录（gateway）内所有 SQL 对
 *       {@code cognitive_model_type / registry_state / gate_*} 不得建 CHECK 约束
 *       （V261 头注：枚举单源在 Java 契约层，禁 DDL 建 CHECK，R-15 同纪律；
 *       只判 CHECK 语句，注释里的"禁建 CHECK"声明不算违例——CHECK 正则要求
 *       {@code CHECK\s*\(} 形态，注释文本无括号跟随）。</li>
 * </ol>
 *
 * <p>扫描根 = {@link CogScope#REPO}（测试 CWD 为模块目录，4 级上溯）。
 * {@code scripts/} 下的历史备份不在 ecos_backend 内，天然排除。</p>
 */
class CognitiveModelTypeSingleSourceTest {

    private static final Path BACKEND = CogScope.REPO.resolve("ecos_backend");
    private static final Path MIGRATIONS = CogScope.REPO.resolve(
            "docs/50-db_script/02-flyway-migration/gateway");

    private static final String SINGLE_SOURCE_FILE = "CognitiveModelType.java";

    /** 独占存储字面量："CM-01"…"CM-07"（引号紧邻编码，整串即值）。 */
    private static final Pattern QUOTED_CM_LITERAL = Pattern.compile("\"CM-0[1-7]\"");

    /** 枚举声明头（重复声明 = 分叉的单源）。 */
    private static final Pattern CM_ENUM_DECL = Pattern.compile(
            "\\benum\\s+(\\w*)\\s*(implements[^{]*)?\\{[^}{]*?\\bCM_01\\b[\\s\\S]{0,600}?\\bCM_07\\b");

    /** 枚举 CHECK 约束（DDL 侧私设第二单源）。 */
    private static final Pattern ENUM_CHECK_CONSTRAINT = Pattern.compile(
            "(?i)CHECK\\s*\\([^)]*(cognitive_model_type|registry_state|gate_contract|gate_regression|gate_backtest|gate_calibration)");

    @Test
    @DisplayName("CM 存储字面量单源：\"CM-0x\" 独占字面量只准出现在 CognitiveModelType.java")
    void cmStorageLiteralsExistOnlyInEnumContract() throws IOException {
        List<String> offenders = new ArrayList<>();
        forEachMainJava(src -> {
            String code = CogScope.stripComments(src.text());
            Matcher m = QUOTED_CM_LITERAL.matcher(code);
            while (m.find()) {
                if (!SINGLE_SOURCE_FILE.equals(src.file().getFileName().toString())) {
                    offenders.add(src.file() + ":" + CogScope.lineOf(code, m.start())
                            + " :: " + m.group());
                }
            }
        });
        assertTrue(offenders.isEmpty(),
                "CM 字面量出现枚举契约之外的副本（单源 = CognitiveModelType，请改为引用 code()）：\n"
                        + String.join("\n", offenders));
    }

    @Test
    @DisplayName("CM 枚举声明单源：全仓生产源只准一处声明 CM_01~CM_07 枚举")
    void cmEnumIsDeclaredExactlyOnce() throws IOException {
        List<String> declarations = new ArrayList<>();
        forEachMainJava(src -> {
            String code = CogScope.stripComments(src.text());
            if (CM_ENUM_DECL.matcher(code).find() && !code.contains("enum CognitiveModelType")) {
                declarations.add(src.file() + "（非 CognitiveModelType 的 CM 枚举副本）");
            }
            if (code.contains("enum CognitiveModelType")) {
                declarations.add(src.file().getFileName().toString());
            }
        });
        assertEquals(List.of(SINGLE_SOURCE_FILE), declarations.stream().sorted().toList(),
                "enum CognitiveModelType 声明必须全仓恰一处，且无 CM_01~CM_07 平行枚举");
    }

    @Test
    @DisplayName("冻结枚举集合与 PRD-05 一致：7 类、编码 CM-01~CM-07 连续无缺")
    void cmEnumMatchesFrozenPrdSet() {
        CognitiveModelType[] values = CognitiveModelType.values();
        assertEquals(7, values.length, "REQ-COG-07 规格 1 冻结 CM 类型为 7 类（新增先改需求单源）");
        for (int i = 0; i < values.length; i++) {
            assertEquals("CM-0" + (i + 1), values[i].code(), "编码必须连续 CM-01..CM-07");
        }
        // 展示名与 PRD-05 冻结清单逐字对应（改中文名 = 契约变更 = 先改需求单源）
        List<String> labels = List.of("规则推理", "统计推断", "预测", "因果",
                "情景·反事实", "案例推理", "LLM 假设·语义推理");
        for (int i = 0; i < labels.size(); i++) {
            assertEquals(labels.get(i), values[i].label(), values[i].code() + " 展示名漂移");
        }
    }

    @Test
    @DisplayName("DDL 侧禁枚举 CHECK：迁移单源目录对 5 个枚举列零 CHECK 约束")
    void migrationsDeclareNoEnumCheckConstraints() throws IOException {
        assertTrue(Files.isDirectory(MIGRATIONS),
                "迁移单源目录缺失：" + MIGRATIONS + "（门禁失效即红，禁静默跳过）");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> sqls = Files.list(MIGRATIONS)) {
            for (Path sql : sqls.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) {
                String text = Files.readString(sql);
                Matcher m = ENUM_CHECK_CONSTRAINT.matcher(text);
                while (m.find()) {
                    offenders.add(sql.getFileName() + ":" + CogScope.lineOf(text, m.start())
                            + " :: " + m.group(1));
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "DDL 建了枚举 CHECK（枚举单源在 cognitive-engine-api，V261 头注禁建；R-15 同纪律）：\n"
                        + String.join("\n", offenders));
    }

    // ── helpers ──

    private record Src(Path file, String text) {}

    @FunctionalInterface
    private interface SrcConsumer { void accept(Src src) throws IOException; }

    /** 遍历 ecos_backend 全部生产源（src/main/java，排除 target），逐文件读入。 */
    private static void forEachMainJava(SrcConsumer consumer) throws IOException {
        assertTrue(Files.isDirectory(BACKEND), "扫描根缺失：" + BACKEND);
        try (Stream<Path> walk = Files.walk(BACKEND)) {
            List<Path> files = walk
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/target/"))
                    .filter(p -> p.toString().endsWith(".java"))
                    .sorted().toList();
            for (Path p : files) {
                consumer.accept(new Src(p, Files.readString(p)));
            }
        }
    }
}
