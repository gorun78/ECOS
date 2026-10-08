package com.chinacreator.gzcm.engine.cognitive2.seam;

import com.chinacreator.gzcm.engine.cognitive.api.objects.CognitiveObjectClass;
import com.chinacreator.gzcm.engine.cognitive2.guard.CogScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-COG-06 验收（PRD-05 §6.1 / 详细设计-05 §8.2 接缝验收 1）——C 层 11 类对象目录。
 *
 * <p>冻结标识：{@code mvn -Dtest=CognitiveObjectCatalogTest#everyCClassHasExactlyOneCarrierOrNone}、
 * {@code #bannedMisnomersAbsentFromCodeAndDocs}。</p>
 */
@DisplayName("C 层对象目录（REQ-COG-06）")
class CognitiveObjectCatalogTest {

    /** 附件第二册 §16.2 冻结的 11 类，多一类少一类都是契约漂移。 */
    private static final int FROZEN_COUNT = 11;

    @Test
    @DisplayName("11 类逐项：载体与引用态二选一，且物理载体唯一、schema 限定、不寄生 K/W 层")
    void everyCClassHasExactlyOneCarrierOrNone() {
        assertEquals(FROZEN_COUNT, CognitiveObjectClass.values().length,
                "C 层对象类数必须等于附件冻结的 11 类（新增先改 PRD-05 §6.1）");

        Set<String> declaredCarriers = new HashSet<>();
        for (CognitiveObjectClass c : CognitiveObjectClass.values()) {
            boolean hasCarrier = c.carrier().isPresent();
            boolean hasReference = c.reference().isPresent();
            assertTrue(hasCarrier ^ hasReference,
                    c.objectKey() + "：物理载体与引用态必须二选一、不得两态并存");

            if (hasCarrier) {
                String carrier = c.carrier().get();
                assertTrue(carrier.contains("."), c.objectKey() + "：载体必须 schema 限定（DR01）" + carrier);
                String schema = carrier.substring(0, carrier.indexOf('.')).toLowerCase(Locale.ROOT);
                assertFalse(Set.of("ecos_knowledge", "ecos_ai").contains(schema),
                        c.objectKey() + "：C 对象载体不得寄生 K 层（ecos_knowledge）或 W 层（ecos_ai）" + carrier);
                assertTrue(declaredCarriers.add(carrier),
                        c.objectKey() + "：物理载体必须唯一，不得两类共用一表 " + carrier);
            }
        }
        // 关键反例逐项点名（防目录被"顺手改名"绕过）
        assertEquals("ecos_cognitive.ecos_cognitive_evidence", CognitiveObjectClass.EVIDENCE.carrier().orElse(""));
        assertEquals("ecos_dw.ecos_application_forecast_run", CognitiveObjectClass.PREDICTION.carrier().orElse("")); /* read-only-carrier-descriptor */
        assertTrue(CognitiveObjectClass.COGNITIVE_MODEL_INSTANCE.reference().isPresent(),
                "模型运行实例必须是引用态，禁与资产表 ecos_cognitive_model 共用一表");
        assertTrue(CognitiveObjectClass.CONTEXT_SITUATION.reference().isPresent(),
                "情境属请求态（§五 REQ-COG-05 已判 Context 不建表）");
        // 停写镜像与在用载体不得同名（否则"停写"与"在用"无法区分）
        for (CognitiveObjectClass c : CognitiveObjectClass.values()) {
            c.carrier().ifPresent(carrier -> assertFalse(c.legacyWriteSuspended().contains(carrier),
                    c.objectKey() + "：同一表既登记为在用载体又登记为停写镜像 " + carrier));
        }
    }

    @Test
    @DisplayName("禁用误名（kb_cognitive_ 族 / kb_mind_registry / cog_·agt_·aim_ 建表形态）在代码面零命中")
    void bannedMisnomersAbsentFromCodeAndDocs() throws IOException {
        List<String> hits = new ArrayList<>();
        List<Path> aliasMappings = new ArrayList<>();

        Path backend = CogScope.REPO.resolve("ecos_backend");
        try (Stream<Path> walk = Files.walk(backend)) {
            walk.filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".xml"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/target/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/archive/"))
                    // 本类自身与 src/test 面豁免：禁名正则字面量必须写在这里，测试夹具不是"新代码使用"
                    .filter(p -> !p.toString().replace('\\', '/').contains("/src/test/"))
                    .forEach(p -> scanSource(p, hits, true, aliasMappings));
        }

        Path frontend = CogScope.REPO.resolve("ecos_frontend/src");
        if (Files.isDirectory(frontend)) {
            try (Stream<Path> walk = Files.walk(frontend)) {
                walk.filter(p -> p.toString().endsWith(".ts") || p.toString().endsWith(".tsx"))
                        .filter(p -> !p.toString().contains("__tests__"))
                        .forEach(p -> scanSource(p, hits, false, aliasMappings));
            }
        }

        // 契约文档面（本册 Cg-1/Cg-2 已订正的两份）：禁名只准出现在"禁用/订正"语境行
        for (String doc : List.of(
                "docs/40-实现/features/20260917-scenario-workbench/BUSINESS_SCENARIO_COGNITION_DOC.md",
                "docs/40-实现/features/20260917-scenario-workbench/BUSINESS_SCENARIO_SERVICE_DOC.md")) {
            Path p = CogScope.REPO.resolve(doc);
            if (!Files.isRegularFile(p)) {
                continue;
            }
            for (String line : Files.readAllLines(p)) {
                if (BANNED.matcher(line).find() && !CORRECTION_CONTEXT.matcher(line).find()) {
                    hits.add(p + " :: " + line.trim());
                }
            }
        }

        assertEquals(List.of(), hits, "禁用误名命中清单应为空（Q11 / ADR-8 §5 / DR01+ST07）");

        // 豁免的代价 = 反向核验：收豁免的文件必须仍在把别名指向 ecos_cognitive 真身，
        // 否则"别名映射"就成了把误名续命的通道。
        List<String> aliasViolations = new ArrayList<>();
        for (Path p : aliasMappings) {
            String src = CogScope.stripComments(readUtf8(p));
            var m = REAL_TABLE_CONSTANT.matcher(src);
            int seen = 0;
            while (m.find()) {
                seen++;
                if (!m.group(2).startsWith("ecos_cognitive.")) {
                    aliasViolations.add(p.getFileName() + " :: " + m.group(1) + " = " + m.group(2)
                            + "（真身落点必须 schema 限定于 ecos_cognitive，DR01/ST07）");
                }
            }
            if (seen == 0) {
                aliasViolations.add(p.getFileName() + " :: 含纯别名 case 标签，却未声明任何 *_REAL_TABLE 真身常量");
            }
        }
        assertTrue(aliasViolations.isEmpty(),
                "别名映射豁免失去合法性（豁免只给『旧名 → 真身』的确定性 resolve）：\n"
                        + String.join("\n", aliasViolations));
    }

    /**
     * 禁名口径：C 对象被误命名为 {@code kb_cognitive_hypothesis|belief|evidence|model|mind|registry}
     * 或 {@code kb_mind_registry}；以及 {@code cog_/agt_/aim_} 裸前缀的建表/函数形态
     * （PRD-05 §6.1 规格 2 的 regex 意图是"用它建表"，故要求后接 {@code (}）。
     *
     * <p>刻意不含 {@code kb_cognitive_pipeline}——它是库内真实存在的 knownLegacy 表
     * （V163 声明停写、IR03 只停写不 DROP/改名），属 F05-13 收敛面而非本项命名红线；
     * 把它算作违例等于要求本轮改 legacy 表名（未授权）。</p>
     */
    private static final Pattern BANNED = Pattern.compile(
            "kb_cognitive_(hypothesis|belief|evidence|model|mind|registry)|kb_mind_registry"
                    + "|(?<![\\w.])(?:cog|agt|aim)_[a-z][a-z0-9_]*\\s*\\(");

    /** 允许出现禁名的语境标记（订正说明/禁用声明，非新增使用）。 */
    private static final Pattern CORRECTION_CONTEXT =
            Pattern.compile("禁用|误名|订正|不得|作废|停写|原写|改为|明令|红线|已废");

    /**
     * 纯 switch 别名单行（形如 {@code case "kb_cognitive_hypothesis":}）—— 分册 04 F04-08 的
     * {@code CognitionContractGuard} 拿旧别名做「契约名 → 库内真身」确定性 resolve，标签是映射
     * <b>输入</b>而非新增使用；改它反而破坏 R-12 的兼容读路径。
     *
     * <p>豁免严格到"整行只有 case 标签 + 冒号"：SQL、常量、拼接形态一律不放行；把字面量拆串绕
     * 正则属游戏门禁，不做。</p>
     */
    private static final Pattern ALIAS_CASE_LABEL = Pattern.compile(
            "(?m)^\\s*case\\s+\"kb_cognitive_[a-z]+\"\\s*:\\s*$");

    /** 真身落点常量（与 {@link #ALIAS_CASE_LABEL} 配成正向核验）。 */
    private static final Pattern REAL_TABLE_CONSTANT = Pattern.compile(
            "\\b([A-Z0-9_]+_REAL_TABLE)\\s*=\\s*\"([^\"]*)\"");

    private static void scanSource(Path p, List<String> hits, boolean stripJavaComments,
                                   List<Path> aliasMappings) {
        try {
            String src = readUtf8(p);
            if (stripJavaComments) {
                src = CogScope.stripComments(src);
                if (ALIAS_CASE_LABEL.matcher(src).find()) {
                    aliasMappings.add(p);
                }
                src = ALIAS_CASE_LABEL.matcher(src).replaceAll(mr ->
                        java.util.regex.Matcher.quoteReplacement(mr.group().replaceAll("\\S", " ")));
            }
            var m = BANNED.matcher(src);
            while (m.find()) {
                hits.add(p + " :: line " + CogScope.lineOf(src, m.start()) + " :: " + m.group());
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取失败: " + p, e);
        }
    }

    /** 宽松 UTF-8 读（存量 buszhi/sysman impl 的 pom.xml 非 UTF-8，严格解码会抛 MalformedInputException 使门禁假崩）。 */
    private static String readUtf8(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

}
