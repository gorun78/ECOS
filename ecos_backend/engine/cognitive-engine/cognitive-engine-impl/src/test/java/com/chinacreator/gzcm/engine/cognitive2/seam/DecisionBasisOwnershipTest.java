package com.chinacreator.gzcm.engine.cognitive2.seam;

import com.chinacreator.gzcm.engine.cognitive2.guard.CogScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-COG-08 验收（PRD-05 §6.3）：Decision Basis 表族归属与写权威单源。
 *
 * <p>冻结标识：{@code mvn -Dtest=DecisionBasisOwnershipTest#decisionCaseHasSingleWritableCarrier}
 * 与 {@code #agentPathCannotWriteDecisionBasis}。</p>
 *
 * <p><b>本测试能证什么、不能证什么（诚实边界）</b>：
 * 规格 2 要求"先实测两侧**行数**与**读写调用点**判定权威，实测前本项记待复核，禁止据推断改代码"。
 * 调用点面是静态可测的 ⇒ 本类以源码扫描钉住"写口 ≤1"这条不变量；
 * 行数面需对 ecos-postgres 实跑 SELECT ⇒ 属 §14.4 未授权项，
 * <b>验收时该半项记"未执行"</b>，本类因此<b>不</b>断言权威落在哪个 schema
 * （断言了就是"据推断改代码"的反面：据推断定代码）。</p>
 *
 * <p>当前静态实测基线（2026-10-08）：{@code ecos_decision_case} 在 ecos_backend 全部生产源
 * （.java/.xml，含 Mapper）中<b>零写口</b>；认知侧唯一决策写点是
 * {@code DecisionDao} 的未限定表名 {@code ecos_decision} / {@code ecos_decision_causal_link}。</p>
 */
@DisplayName("Decision Basis 归属与写权威（REQ-COG-08）")
class DecisionBasisOwnershipTest {

    /** Decision Basis 表族（规格 1：V103/V151 起 + ecos_agent 域 1 张，只定性不改表）。 */
    private static final String FAMILY = "ecos_decision(?:_[a-z0-9]+)*";

    /** 写语句形态：INSERT INTO / UPDATE / DELETE FROM，可带 schema 限定。 */
    private static final Pattern WRITE_ON_FAMILY = Pattern.compile(
            "(?i)\\b(?:INSERT\\s+INTO|UPDATE|DELETE\\s+FROM)\\s+((?:[a-z0-9]+\\.)?)(" + FAMILY + ")\\b");

    private static final Pattern WRITE_ON_CASE = Pattern.compile(
            "(?i)\\b(?:INSERT\\s+INTO|UPDATE|DELETE\\s+FROM)\\s+((?:[a-z0-9]+\\.)?ecos_decision_case)\\b");

    /** W（Agent）侧源码根：ai 引擎 + aiming 服务 + workspace 场景层。 */
    private static final List<String> AGENT_ROOTS = List.of(
            "ecos_backend/engine/ai-engine",
            "ecos_backend/services/aiming",
            "ecos_backend/workspace");

    @Test
    @DisplayName("ecos_decision_case 应用侧写口 ≤1 个载体；有写口必须 schema 限定（DR01）")
    void decisionCaseHasSingleWritableCarrier() throws IOException {
        Set<String> carriers = new LinkedHashSet<>();
        List<String> unqualified = new ArrayList<>();
        for (SourceLine sl : productionSources()) {
            Matcher m = WRITE_ON_CASE.matcher(sl.code());
            while (m.find()) {
                String target = m.group(1).toLowerCase();
                carriers.add(target);
                if (!target.contains(".")) {
                    unqualified.add(sl.where() + " -> " + target);
                }
            }
        }

        // 规格 2 的"单源"不变量：写口载体数 ≤1（0 = 存量只定性、应用侧尚未开写，同样合规）。
        assertTrue(carriers.size() <= 1,
                "ecos_decision_case 出现多侧并存写口（违规格 2 单源）: " + carriers);
        // 若将来开写口，必须显式限定 schema，否则"写的是哪一侧"不可判（DR01 / 双写歧义）。
        assertTrue(unqualified.isEmpty(),
                "写口未 schema 限定，无法判定落在哪一侧载体（DR01）: " + unqualified);

        // 规格 1：表族定性为 C 层资产 ⇒ 本批不得出现"改表/改名/DROP"形态（只增不改 + IR03）。
        List<String> ddlTampering = new ArrayList<>();
        Path migration = CogScope.REPO.resolve("docs/50-db_script/02-flyway-migration/gateway");
        try (Stream<Path> walk = Files.walk(migration, 1)) {
            for (Path p : walk.filter(f -> f.toString().endsWith(".sql")).toList()) {
                String code = CogScope.stripComments(readUtf8(p));
                Matcher m = Pattern.compile("(?i)\\b(DROP\\s+TABLE|RENAME\\s+TO)\\s+([a-z0-9_.]*"
                        + FAMILY + ")\\b").matcher(code);
                while (m.find()) {
                    ddlTampering.add(p.getFileName() + ": " + m.group(0));
                }
            }
        }
        assertTrue(ddlTampering.isEmpty(), "Decision Basis 表族被 DROP/改名（违规格 1 只定性）: " + ddlTampering);
    }

    @Test
    @DisplayName("W（Agent）侧对 Decision Basis 只读：无写路由（规格 3）")
    void agentPathCannotWriteDecisionBasis() throws IOException {
        List<String> violations = new ArrayList<>();
        List<String> agentHeaders = new ArrayList<>();
        for (String root : AGENT_ROOTS) {
            Path dir = CogScope.REPO.resolve(root);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                List<Path> files = walk.filter(p -> p.toString().endsWith(".java")
                                || p.toString().endsWith(".xml"))
                        .filter(p -> !p.toString().replace('\\', '/').contains("/target/"))
                        .filter(p -> !p.toString().replace('\\', '/').contains("/archive/"))
                        .filter(p -> !p.toString().replace('\\', '/').contains("/src/test/"))
                        .toList();
                for (Path p : files) {
                    String code = CogScope.stripComments(readUtf8(p));
                    Matcher m = WRITE_ON_FAMILY.matcher(code);
                    while (m.find()) {
                        violations.add(root + " :: " + p.getFileName() + " :: " + m.group(0));
                    }
                    // 只读端点面：W 侧引用决策依据只准带 decisionId + version + hash（读侧登记，供人工核对）
                    if (code.toLowerCase().contains("ecos_decision")) {
                        agentHeaders.add(p.getFileName().toString());
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "Agent/kb/ai 侧出现 Decision Basis 写路由（违规格 3，含\"经代理写\"）: " + violations);
        // 读侧可以为零（当前 W 侧不经 SQL 引用决策表，走 cognitive REST）；非零时列名提示复核引用形态。
        System.out.println("[REQ-COG-08] W 侧引用 ecos_decision* 的文件（应仅为只读）: " + agentHeaders);
    }

    private List<SourceLine> productionSources() throws IOException {
        Path backend = CogScope.REPO.resolve("ecos_backend");
        List<SourceLine> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(backend)) {
            List<Path> files = walk.filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".xml"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/target/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/archive/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/src/test/"))
                    .toList();
            for (Path p : files) {
                out.add(new SourceLine(p.getFileName().toString(), CogScope.stripComments(readUtf8(p))));
            }
        }
        return out;
    }

    private record SourceLine(String where, String code) {
        @Override
        public String toString() {
            return where;
        }
    }

    /** 宽松 UTF-8 读（存量 buszhi/sysman impl 的 pom.xml 非 UTF-8，严格解码会抛 MalformedInputException 使门禁假崩）。 */
    private static String readUtf8(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

}
