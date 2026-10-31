package com.chinacreator.gzcm.engine.cognitive2.guard;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveR4ModelCategory;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 差距分析 §8.4.2 行 432「关键表字段」余项（#146ay）—— V296/V297 R4 分类与承参面形态门禁。
 *
 * <p>断言面（纯源码/DDL 扫描，不启动 Spring、零写共享库；dev PG 实跑凭证已登记
 * V296/V297 头注，本静态门不冒充 live 验收）：
 * <ol>
 *   <li><b>V296 DDL 形态冻结</b>：model 七列 + hypothesis 五列逐枚
 *       {@code ADD COLUMN IF NOT EXISTS} 在位；禁新增 jsonb/timestamptz/CHECK
 *       （V261/V292 同纪律：词表应用层单源守护，DDL 不建 CHECK）；</li>
 *   <li><b>V297 DDL 形态冻结</b>：hypothesis 承参过滤列 mind_id/scenario_id 两枚在位；
 *       同红线禁 jsonb/timestamptz；</li>
 *   <li><b>九值字面量单源</b>：整棵 ecos_backend 生产源中带引号独占存储字面量
 *       （DIAGNOSTIC/HYPOTHESIS_TESTING/PREDICTIVE/RISK/RELATIONAL/OPTIMIZATION/BEHAVIORAL
 *       七枚无歧义词）只许出现在 {@code CognitiveR4ModelCategory.java}——
 *       CAUSAL/SIMULATION 两枚词因与旧 model_type 四值词表（V124）及 CM 映射面合法共存
 *       （V261 三词表并存先例），不入零容差面，由 case③ 声明单源与 case④ 档位派生把守；</li>
 *   <li><b>枚举声明单源</b>：全仓生产源 {@code enum CognitiveR4ModelCategory} 恰一处，
 *       九枚常量集合与详设 §8.4.2 冻结清单逐一对应（数量 9、档位 1/1/1/2/2/2/2/3/3）；</li>
 *   <li><b>读链载体收口</b>：{@code HypothesisReader} 表常量 = 权威载体
 *       {@code ecos_cognitive.ecos_cognitive_hypothesis}（幻影公域载体
 *       {@code public.ecos_cognitive_hypothesis} 恒红），且过滤面
 *       {@code scenario_id = ?} / {@code mind_id = ?} 在位（V297 列位承流）。</li>
 * </ol>
 * 变异探针（交付时实跑取证后复原）：① V296 删任一列 ALTER → case①红；
 * ② 任意外部文件加带引号 "DIAGNOSTIC" → case③红；③ HypothesisReader 表常量回退 public.
 * 前缀 → case⑤红。</p>
 */
class V296R4FieldsGateTest {

    private static final Path REPO = CogScope.REPO;
    private static final Path MIGRATIONS = REPO.resolve("docs/50-db_script/02-flyway-migration/gateway");
    private static final Path V296 = MIGRATIONS.resolve("V296__cognitive_model_hypo_r4_fields.sql");
    private static final Path V297 = MIGRATIONS.resolve("V297__cognitive_hypo_scenario_mind_filter_cols.sql");
    private static final Path BACKEND = REPO.resolve("ecos_backend");

    private static final String SINGLE_SOURCE_FILE = "CognitiveR4ModelCategory.java";

    /** 七枚无歧义九值词（CAUSAL/SIMULATION 与旧词表合法共存，不入零容差面）。 */
    private static final Pattern R4_QUOTED_LITERAL = Pattern.compile(
            "\"(DIAGNOSTIC|HYPOTHESIS_TESTING|PREDICTIVE|RISK|RELATIONAL|OPTIMIZATION|BEHAVIORAL)\"");

    /** 九值分类枚举声明头（平行声明 = 分叉的单源）。 */
    private static final Pattern R4_ENUM_DECL = Pattern.compile(
            "\\benum\\s+(\\w*)\\s*(implements[^{]*)?\\{[^}{]*?\\bCAUSAL\\b[\\s\\S]{0,800}?\\bBEHAVIORAL\\b");

    /** DDL 侧枚举/词表 CHECK（应用层单源纪律违例）。 */
    private static final Pattern ENUM_CHECK_CONSTRAINT = Pattern.compile(
            "(?i)CHECK\\s*\\([^)]*(model_category|\\bphase\\b|confidence)");

    // ────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("case① V296 DDL 形态冻结：model 七列 + hypothesis 五列逐枚 ADD COLUMN IF NOT EXISTS；禁 jsonb/timestamptz/CHECK")
    void v296DdlShapeIsFrozen() throws IOException {
        assertTrue(Files.isRegularFile(V296), "V296 单源文件缺失：" + V296);
        String ddl = CogScope.stripComments(Files.readString(V296));
        // model 七列（§8.4.2 九类分类面缺口列）
        for (String col : List.of("model_key", "description", "phase", "compute_owner",
                "io_contract_json", "limits_json", "model_category")) {
            assertTrue(ddl.contains("ADD COLUMN IF NOT EXISTS " + col),
                    "V296 model 缺 R4 列：" + col);
        }
        // hypothesis 五列（承参与信心面缺口列）
        for (String col : List.of("subject_type", "subject_id", "reason", "confidence",
                "evidence_ids_json")) {
            assertTrue(ddl.contains("ADD COLUMN IF NOT EXISTS " + col),
                    "V296 hypothesis 缺承参/信心列：" + col);
        }
        // scale 精度面（详设 §8.4.2 confidence 精度 9,6）
        assertTrue(ddl.replaceAll("\\s+", "").contains("confidenceNUMERIC(9,6)"),
                "V296 confidence 必须 NUMERIC(9,6)（详设精度，禁裸 NUMERIC 漂移）");
        // 红线：禁 JSONB（MC02）/ timestamptz（MC03 家族）/ 词表 CHECK（V261/V292 同纪律）
        assertFalse(ddl.contains("jsonb"), "V296 出现 JSONB（MC02）");
        assertFalse(ddl.contains("timestamptz"), "V296 出现 timestamptz（MC03 家族纪律）");
        assertFalse(ENUM_CHECK_CONSTRAINT.matcher(ddl).find(),
                "V296 DDL 建了 model_category/phase 词表 CHECK（词表单源在认知域 Java 契约层，禁 DDL CHECK）");
    }

    @Test
    @DisplayName("case② V297 DDL 形态冻结：hypothesis mind_id/scenario_id 两枚承参过滤列在位；同红线")
    void v297DdlShapeIsFrozen() throws IOException {
        assertTrue(Files.isRegularFile(V297), "V297 单源文件缺失：" + V297);
        String ddl = CogScope.stripComments(Files.readString(V297));
        for (String col : List.of("mind_id", "scenario_id")) {
            assertTrue(ddl.contains("ADD COLUMN IF NOT EXISTS " + col),
                    "V297 缺承参过滤列：" + col);
        }
        assertFalse(ddl.contains("jsonb"), "V297 出现 JSONB（MC02）");
        assertFalse(ddl.contains("timestamptz"), "V297 出现 timestamptz（MC03 家族纪律）");
        assertFalse(ENUM_CHECK_CONSTRAINT.matcher(ddl).find(),
                "V297 DDL 建了词表 CHECK（应用层单源守护纪律）");
    }

    @Test
    @DisplayName("case③ 九值字面量单源：七个无歧义带引号字面量只准出现在 CognitiveR4ModelCategory.java")
    void r4LiteralsExistOnlyInEnumContract() throws IOException {
        List<String> offenders = new ArrayList<>();
        forEachMainJava(src -> {
            String code = CogScope.stripComments(src.text());
            Matcher m = R4_QUOTED_LITERAL.matcher(code);
            while (m.find()) {
                if (!SINGLE_SOURCE_FILE.equals(src.file().getFileName().toString())) {
                    offenders.add(src.file() + ":" + CogScope.lineOf(code, m.start())
                            + " :: \"" + m.group(1) + "\"");
                }
            }
        });
        assertTrue(offenders.isEmpty(),
                "R4 九值字面量出现枚举属主之外的副本（单源 = CognitiveR4ModelCategory，请改引 code()）：\n"
                        + String.join("\n", offenders));
    }

    @Test
    @DisplayName("case④ 枚举声明单源 + 冻结九枚集合：全仓恰一处声明；三阶段档位 1/1/1/2/2/2/2/3/3")
    void r4EnumIsDeclaredExactlyOnceAndFrozen() throws IOException {
        List<String> declarations = new ArrayList<>();
        forEachMainJava(src -> {
            String code = CogScope.stripComments(src.text());
            if (R4_ENUM_DECL.matcher(code).find() && !code.contains("enum CognitiveR4ModelCategory")) {
                declarations.add(src.file() + "（非属主的九值平行枚举）");
            }
            if (code.contains("enum CognitiveR4ModelCategory")) {
                declarations.add(SINGLE_SOURCE_FILE);
            }
        });
        assertEquals(List.of(SINGLE_SOURCE_FILE), declarations.stream().sorted().toList(),
                "enum CognitiveR4ModelCategory 声明必须全仓恰一处，无九值平行枚举");
        // 冻结九枚集合（详设 §8.4.2；改词表 = 契约变更 = 先改需求单源）
        assertEquals(List.of("CAUSAL", "DIAGNOSTIC", "HYPOTHESIS_TESTING", "PREDICTIVE",
                "SIMULATION", "RISK", "RELATIONAL", "OPTIMIZATION", "BEHAVIORAL"),
                java.util.Arrays.stream(CognitiveR4ModelCategory.values())
                        .map(CognitiveR4ModelCategory::code).toList(),
                "九值分类词表集合漂移（禁增删改序）");
        assertEquals(List.of(1, 1, 1, 2, 2, 2, 2, 3, 3),
                java.util.Arrays.stream(CognitiveR4ModelCategory.values())
                        .map(CognitiveR4ModelCategory::routePhase).toList(),
                "三期路线档位漂移（一期 3 / 二期 4 / 三期 2，phase 派生单源）");
        // fromCode fail-loud 词表纪律：词表外 ⇒ empty（禁静默默认）
        assertEquals(java.util.Optional.empty(), CognitiveR4ModelCategory.fromCode("BOGUS"));
        assertEquals(java.util.Optional.empty(), CognitiveR4ModelCategory.fromCode(" "));
        assertEquals(CognitiveR4ModelCategory.CAUSAL,
                CognitiveR4ModelCategory.fromCode("causal").orElseThrow(), "fromCode 大小写容错面漂移");
    }

    @Test
    @DisplayName("case⑤ 读链载体收口：HypothesisReader 指向权威载体 + scenario/mind 过滤面在位")
    void hypothesisReaderPointsAtAuthoritativeCarrier() throws IOException {
        String reader = CogScope.stripComments(Files.readString(
                CogScope.MAIN.resolve("com/chinacreator/gzcm/engine/cognitive2/dao/HypothesisReader.java")));
        assertTrue(reader.contains("\"ecos_cognitive.ecos_cognitive_hypothesis\""),
                "HypothesisReader 表常量漂移（须 ecos_cognitive.ecos_cognitive_hypothesis 权威载体）");
        assertFalse(reader.contains("public.ecos_cognitive_hypothesis"),
                "HypothesisReader 仍指幻影公域载体 public.ecos_cognitive_hypothesis（dev PG 无此关系）");
        assertTrue(reader.contains("scenario_id = ?"), "E3 读链缺 scenario_id 必带过滤（V297 承流）");
        assertTrue(reader.contains("mind_id = ?"), "E3 读链缺 mind_id 严格等值过滤（详设 §8.4.2）");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

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
