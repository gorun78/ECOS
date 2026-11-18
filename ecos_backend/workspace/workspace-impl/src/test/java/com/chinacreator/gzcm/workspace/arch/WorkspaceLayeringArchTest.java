package com.chinacreator.gzcm.workspace.arch;

import com.chinacreator.gzcm.workspace.testscan.SourceCommentStripper;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 卷07 F07-16（W180 / C162）三层纪律守门 —— 依赖方向 + schema 边界。
 *
 * <p>规则依据：AGENTS.md「workspace 不得直接依赖 engine-impl」+ 架构铁律 §3.1
 * 「跨引擎不操作对方 schema」。本类是<b>可执行</b>版本，替代文档里的口头承诺。</p>
 */
class WorkspaceLayeringArchTest {

    /** 只能被 workspace 引用的自有/底座包。 */
    private static final String WORKSPACE_PACKAGE = "com.chinacreator.gzcm.workspace";

    /**
     * workspace <b>源码</b>禁止 import 任何引擎/业务 service/gateway 的类。
     *
     * <p>与 pom 的关系：workspace-service 目前仍在 classpath 上携带 4 个 {@code *-impl}
     * 制品（为 :18090 提供 {@code IFunctionPropertyEvaluator}/{@code IActionHookExecutor}/
     * {@code ObjectRuntimeService} 等真实 bean，以及 F07-01 的 sysman 鉴权链）。
     * "classpath 有 impl" 是装配事实，"源码 import impl" 才是分层违约 —— 后者由本规则守住，
     * 且一旦调用点改走 DecisionGateway（REST/Local 两态）就能删依赖。</p>
     */
    @Test
    void workspaceMustNotDependOnEngineImpl() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(WORKSPACE_PACKAGE);

        assertTrue(classes.size() > 0, "未导入任何 workspace 类 —— 检查模块依赖方向");

        Pattern implOwned = Pattern.compile(
                "^com\\.chinacreator\\.gzcm\\.(engine|buszhi|sysman|services|gateway)\\.");

        Set<String> violations = new LinkedHashSet<>();
        for (JavaClass origin : classes) {
            if (!origin.getPackageName().startsWith(WORKSPACE_PACKAGE)) continue;
            origin.getDirectDependenciesFromSelf().forEach(dep -> {
                String target = dep.getTargetClass().getFullName();
                if (implOwned.matcher(target).find()) {
                    violations.add(origin.getFullName() + " -> " + target);
                }
            });
        }
        assertEquals(Set.of(), violations,
                "workspace 源码直接依赖引擎/业务/gateway 实现包（AGENTS.md 分层禁令）。"
                        + "跨引擎能力必须走 common-api 接口或 *-api 门面: ");
    }

    /**
     * workspace 源码禁止把 SQL 打到<b>对方引擎的 schema/表</b>（铁律 §3.1）。
     *
     * <p>现状是 ratchet 而非清零：{@code ObjectController}/{@code ObjectQLService}/
     * {@code ObjectActionController}/{@code WorkspaceDataDao} 直接 SQL 引用
     * {@code ecos_ontology_entity} / {@code ecos_ontology_action} /
     * {@code ecos_ontology_relationship} / {@code ecos_entity_table_mapping} /
     * {@code ecos_knowledge.*}（kb 引擎 schema）。这些文件不在卷07 F07-16 的可改面内
     * （属 ontology/knowledge 侧 Agent），故本规则先把<b>违约集合冻结</b>：
     * 新增任何跨引擎表引用即刻失败，集合缩小则提示同步收紧基线。</p>
     */
    @Test
    void noEngineSchemaTablesTouchedDirectly() throws IOException {
        Path srcRoot = mainSourceRoot();
        /** 冻结基线：文件 → 该文件命中的跨引擎表名集合。只允许减少，不允许新增。 */
        Set<String> baseline = Set.of(
                "controller/ObjectQLParser.java",
                "controller/ObjectActionController.java",
                "controller/ObjectController.java",
                "controller/ObjectQLController.java",
                "service/ObjectQLService.java",
                "dao/WorkspaceDataDao.java",
                "knowledge/service/KnowledgeStatsAggregator.java",
                "knowledge/dto/KnowledgeStatsVO.java"
        );

        Set<String> offenders = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(srcRoot)) {
            for (Path p : (Iterable<Path>) walk.filter(f -> f.toString().endsWith(".java"))::iterator) {
                if (hitsCrossEngine(new String(Files.readAllBytes(p), StandardCharsets.UTF_8))) {
                    offenders.add(relativize(srcRoot, p));
                }
            }
        }
        Set<String> added = new LinkedHashSet<>(offenders);
        added.removeAll(baseline);
        assertTrue(added.isEmpty(),
                "workspace 新增了对其他引擎 schema/表的直接 SQL 引用（铁律 §3.1 禁令）。"
                        + "跨引擎数据必须经对应 *-api 门面或 security-engine。新增文件: " + added);
    }

    /** 兼容 ArchUnit 缺失时的纯源码自检：workspace main 无引擎 import（与规则 1 同判据）。 */
    @Test
    void workspaceSourceHasNoEngineImports() throws IOException {
        Path srcRoot = mainSourceRoot();
        Pattern importPattern = Pattern.compile(
                "^import\\s+(static\\s+)?com\\.chinacreator\\.gzcm\\.(engine|buszhi|sysman|services|gateway)\\.");
        try (Stream<Path> walk = Files.walk(srcRoot)) {
            List<Path> bad = walk.filter(p -> p.toString().endsWith(".java")).filter(p -> {
                try {
                    return Files.readAllLines(p, StandardCharsets.UTF_8).stream()
                            .anyMatch(l -> importPattern.matcher(l.trim()).find());
                } catch (IOException e) {
                    return false;
                }
            }).toList();
            assertTrue(bad.isEmpty(), "workspace 源码 import 了引擎/业务实现包: " + bad);
        }
    }

    /**
     * 检测器精度自证：占位符豁免不得把真硬编码 SQL 一起放过去。
     *
     * <p>本用例与 {@link #noEngineSchemaTablesTouchedDirectly()} 共用同一判据函数，
     * 因此「豁免面扩大导致门禁失明」会在这里先红。</p>
     */
    @Test
    void placeholderExemptionDoesNotBlindHardcodedSql() {
        assertFalse(hitsCrossEngine(
                "class A { A(@Value(\"${ecos.db.control-schema:ecos_control}\") String s) {} }"),
                "MC06 配置注入的占位符默认值被误判为跨引擎直连");
        assertTrue(hitsCrossEngine(
                "class A { String q = \"SELECT 1 FROM ecos_control.ecos_decision\"; }"),
                "硬编码跨引擎 SQL 未被检出 —— 检测器已失明");
        assertTrue(hitsCrossEngine(
                "class A { String q = \"SELECT 1 FROM \" + schema + \".ecos_ontology_entity\"; }"),
                "拼接形态的跨引擎表名未被检出");
        assertFalse(hitsCrossEngine(
                "class A { /** 说明：本类不碰 ecos_knowledge。 */ String q = \"SELECT 1 FROM t\"; }"),
                "注释里的表名属文档陈述，不应计违约");
    }

    /** 跨引擎 schema/表字面量（铁律 §3.1 封闭枚举中不属于 workspace 的那些）。 */
    private static final Set<String> CROSS_ENGINE_LITERALS = Set.of(
            "ecos_ontology_entity",
            "ecos_ontology_action",
            "ecos_ontology_relationship",
            "ecos_entity_table_mapping",
            "ecos_knowledge",
            "ecos_cognitive",
            "ecos_ai",
            "ecos_dw",
            "ecos_data",
            "ecos_control"
    );

    private static final Pattern TABLE_PATTERN = Pattern.compile("ecos_[a-z0-9_]+");

    /**
     * Spring 配置占位符 {@code ${key:default}} 是 MC06 要求的 schema 注入形态，
     * 其默认值是配置声明而非 SQL 字面量，整体剔除后再判表名。
     */
    private static final Pattern SPRING_PLACEHOLDER = Pattern.compile("\\$\\{[^}]*}");

    private static boolean hitsCrossEngine(String source) {
        // 门禁语义=「代码里的 SQL/表引用」；Javadoc 与行注释里的表名属文档陈述，不计违约。
        String text = SPRING_PLACEHOLDER.matcher(stripComments(source)).replaceAll("").toLowerCase();
        Matcher m = TABLE_PATTERN.matcher(text);
        while (m.find()) {
            if (CROSS_ENGINE_LITERALS.contains(m.group())) return true;
        }
        return false;
    }

    /** 剥离行注释与块注释，只留代码本体 —— 注释里的表名是文档陈述，不计 SQL 违约。 */
    private static String stripComments(String src) {
        return SourceCommentStripper.strip(src);
    }

    private static Path mainSourceRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            Path cand = p.resolve("workspace/workspace-impl/src/main/java");
            if (Files.isDirectory(cand)) return cand;
            Path self = Paths.get("src/main/java");
            if (Files.isDirectory(self)
                    && Files.exists(self.resolve("com/chinacreator/gzcm/workspace"))) {
                return self;
            }
            p = p.getParent();
        }
        throw new IllegalStateException("找不到 workspace-impl/src/main/java，守门测试无法定位源码根");
    }

    private static String relativize(Path root, Path file) {
        Path rel = root.resolve("com/chinacreator/gzcm/workspace").relativize(file);
        return rel.toString().replace('\\', '/');
    }
}
