package com.chinacreator.gzcm.common.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.chinacreator.gzcm.common.testscan.SourceCommentStripper;

/**
 * 发布 / 双写状态机 <b>单源零容差仓扫门</b>（#147D，收口《ECOS补充详细设计说明书-R7至R11-2026-10-16.md》
 * §2.9.6①③ 与 §3.1.4 DF-1）。
 *
 * <p><b>为什么需要一枚仓扫门</b>：状态词表、转换表、对账判定三张表一旦被某个域另抄一份
 * （「我这边的发布态叫 PUBLISHED / SUCCESS」），§2.9.6① 的红线「三者分别记录、禁把部分成功
 * 报完全成功」在类型面上就重新变得可绕过。本门把「唯一持有面」钉成可执行事实。</p>
 *
 * <p><b>本门钉六件事</b>：</p>
 * <ol>
 *   <li>16 态词表逐字齐全且只在属主文件声明；</li>
 *   <li>7 枚收口前全仓零命中的状态名，在 main 面不得以<b>字符串字面量</b>再现
 *       （消费侧只能用枚举引用，不得手写状态串）；</li>
 *   <li>六个类型（PublishState / PublishStateMachine / PublishReconciler / DualWriteOutcome /
 *       ReconciliationInput / RollbackPlan）在属主包外不得二次声明；</li>
 *   <li>转换表 16 枚起点全覆盖（{@code m.put(PublishState.} 计数 = 16）；</li>
 *   <li>属主包剥注释后不得出现 DML（§2.9.6② 回滚禁反向 SQL 的静态面）；</li>
 *   <li>判据能变红：合成夹具在仓外 {@code @TempDir} 取证。</li>
 * </ol>
 *
 * @author ECOS Runtime底座 #147D
 */
@Timeout(120)
class PublishStateMachineSingleSourceGateTest {

    private static final String OWNER_DIR =
            "ecos_backend/runtime/runtime-access/src/main/java/com/chinacreator/gzcm/runtime/access/git/publish/";
    private static final String OWNER_STATE = OWNER_DIR + "PublishState.java";
    private static final String OWNER_MACHINE = OWNER_DIR + "PublishStateMachine.java";

    /** §2.9.6① 的 16 态（主链 9 + 旁支 7），逐字口径。 */
    private static final Set<String> STATES = new LinkedHashSet<>(List.of(
            "DRAFT", "VALIDATING", "PENDING_APPROVAL", "APPROVED", "READY_TO_APPLY",
            "APPLYING", "APPLIED", "VERIFYING", "SUCCEEDED",
            "REJECTED", "PARTIAL_FAILURE", "FAILED", "ROLLBACK_PENDING", "ROLLING_BACK",
            "ROLLED_BACK", "DRIFTED"));

    /** 收口前全仓 main 面零命中的 7 枚（可作字符串零容差判据，见 PublishState 类注释）。 */
    private static final Set<String> ZERO_TOLERANCE_LITERALS = Set.of(
            "READY_TO_APPLY", "APPLYING", "VERIFYING", "PARTIAL_FAILURE",
            "ROLLBACK_PENDING", "ROLLING_BACK", "DRIFTED");

    /** 属主包内类型（禁在别处二次声明）。 */
    private static final List<String> OWNER_TYPES = List.of(
            "PublishState", "PublishStateMachine", "PublishReconciler",
            "DualWriteOutcome", "ReconciliationInput", "RollbackPlan");

    private static final Pattern ENUM_CONSTANT =
            Pattern.compile("(?m)^\\s{4}([A-Z][A-Z0-9_]*)\\s*[(,;]");
    private static final Pattern CLASS_DECL = Pattern.compile(
            "\\b(?:class|enum|interface|record)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern TRANSITION_ROW =
            Pattern.compile("m\\.put\\(PublishState\\.[A-Z_]+\\s*,");
    private static final Pattern DML = Pattern.compile(
            "(?i)\\b(INSERT\\s+INTO|UPDATE\\s+[A-Za-z_.]+\\s+SET|DELETE\\s+FROM|DROP\\s+TABLE|ALTER\\s+TABLE|TRUNCATE\\s+TABLE)\\b");
    private static final int MIN_SCANNED_MAIN_FILES = 2000;

    @Test
    @DisplayName("16 态词表逐字齐全，且只在属主文件声明")
    void stateVocabularyIsCompleteAndOwned() throws IOException {
        Path root = locateRepoRoot();
        String stripped = SourceCommentStripper.strip(readRequired(root, OWNER_STATE));
        Set<String> declared = new LinkedHashSet<>();
        Matcher m = ENUM_CONSTANT.matcher(stripped);
        while (m.find()) {
            declared.add(m.group(1));
        }
        assertEquals(STATES, declared,
                "PublishState 的枚举常量与 §2.9.6① 16 态不一致（禁增删改名）");
        assertEquals(16, declared.size());
    }

    @Test
    @DisplayName("7 枚零容差状态名在 main 面不得以字符串字面量再现")
    void zeroToleranceStateNamesAreNotStringLiteralsElsewhere() throws IOException {
        Path root = locateRepoRoot();
        List<Path> javaFiles = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), javaFiles);
        assertScanFaceTrustworthy(javaFiles);
        List<String> violations = new ArrayList<>();
        for (Path f : javaFiles) {
            String stripped = SourceCommentStripper.strip(
                    Files.readString(f, StandardCharsets.UTF_8));
            for (String name : ZERO_TOLERANCE_LITERALS) {
                if (quotedLiteral(name).matcher(stripped).find()) {
                    violations.add(relativize(root, f) + " → \"" + name + "\"");
                }
            }
        }
        assertEquals(List.of(), violations,
                "状态名只能经枚举引用，禁手写状态串（否则单源可被绕过）");
    }

    @Test
    @DisplayName("六个状态机类型不得在属主包外二次声明")
    void typesAreDeclaredOnlyInOwnerPackage() throws IOException {
        Path root = locateRepoRoot();
        List<Path> javaFiles = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), javaFiles);
        assertScanFaceTrustworthy(javaFiles);
        List<String> violations = new ArrayList<>();
        for (Path f : javaFiles) {
            String rel = relativize(root, f);
            if (rel.startsWith(OWNER_DIR)) {
                continue;
            }
            String stripped = SourceCommentStripper.strip(
                    Files.readString(f, StandardCharsets.UTF_8));
            Matcher m = CLASS_DECL.matcher(stripped);
            while (m.find()) {
                if (OWNER_TYPES.contains(m.group(1))) {
                    violations.add(rel + " 二次声明 " + m.group(1));
                }
            }
        }
        assertEquals(List.of(), violations,
                "发布状态机单源在 runtime-access git.publish 包，禁第二套同名类型（§2.9.6①）");
    }

    @Test
    @DisplayName("转换表 16 枚起点全覆盖（m.put 计数 = 16）")
    void transitionTableCoversAllStates() throws IOException {
        Path root = locateRepoRoot();
        String stripped = SourceCommentStripper.strip(readRequired(root, OWNER_MACHINE));
        int rows = count(TRANSITION_ROW, stripped);
        assertEquals(16, rows,
                "转换表起点数 " + rows + " ≠ 16：新增态须同批改转换表；删态须同批改 §2.9.6① 与单测");
    }

    @Test
    @DisplayName("属主包剥注释后零 DML：回滚禁反向 SQL 的静态面")
    void ownerPackageIsPureRulesWithoutDml() throws IOException {
        Path root = locateRepoRoot();
        Path dir = root.resolve(OWNER_DIR);
        assertTrue(Files.isDirectory(dir), "属主包不在位：" + OWNER_DIR);
        List<String> violations = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path f : walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java")).toList()) {
                String stripped = SourceCommentStripper.strip(
                        Files.readString(f, StandardCharsets.UTF_8));
                Matcher m = DML.matcher(stripped);
                while (m.find()) {
                    violations.add(relativize(root, f) + " → " + m.group(1));
                }
            }
        }
        assertEquals(List.of(), violations,
                "§2.9.6②：回滚只能发布已知历史版本，禁止在状态机包内出现任何反向 DML");
    }

    @Test
    @DisplayName("判据能变红：合成夹具证明谓词不是恒真")
    void predicatesCanGoRed(@TempDir Path tmp) throws IOException {
        Path fixture = tmp.resolve("FakeOwner.java");
        Files.writeString(fixture, "class FakeOwner { enum PublishState { READY_TO_APPLY } }\n"
                + "// INSERT INTO ecos_x VALUES (1)\n", StandardCharsets.UTF_8);
        String stripped = SourceCommentStripper.strip(Files.readString(fixture));

        assertEquals(1, count(ENUM_CONSTANT, "    READY_TO_APPLY,\n"));
        assertTrue(quotedLiteral("APPLYING").matcher("String s = \"APPLYING\";").find());
        assertFalse(quotedLiteral("APPLYING").matcher("PublishState s = PublishState.APPLYING;").find());
        assertTrue(DML.matcher(stripped).find() == false, "注释里的 DML 不得计入（剥注释有效）");
        assertTrue(DML.matcher(SourceCommentStripper.strip("String sql = \"DELETE FROM t\";")).find());

        Matcher m = CLASS_DECL.matcher(stripped);
        assertTrue(m.find());
        assertEquals("FakeOwner", m.group(1));
    }

    private static Pattern quotedLiteral(String name) {
        return Pattern.compile('"' + name + '"');
    }

    private static int count(Pattern p, String s) {
        int n = 0;
        Matcher m = p.matcher(s);
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static void assertScanFaceTrustworthy(List<Path> javaFiles) {
        assertTrue(javaFiles.size() >= MIN_SCANNED_MAIN_FILES,
                "扫描面缩水：实扫 main 面 java 文件 " + javaFiles.size() + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "，判据不可信（读不到 ≠ 无违例）");
    }

    private static String readRequired(Path root, String rel) throws IOException {
        Path f = root.resolve(rel);
        if (!Files.isRegularFile(f)) {
            fail("登记属主文件不在位（改名/挪目录须同批改本门判据）：" + rel);
        }
        return Files.readString(f, StandardCharsets.UTF_8);
    }

    private static String relativize(Path root, Path f) {
        return root.relativize(f).toString().replace('\\', '/');
    }

    private static void collectJavaUnder(Path dir, List<Path> out) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .map(p -> p.toAbsolutePath().normalize())
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return s.contains("/src/main/java/")
                                && !s.contains("/target/") && !s.contains("/archive/");
                    })
                    .forEach(out::add);
        }
    }

    private static Path locateRepoRoot() {
        Path cur = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path p = cur; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("ecos_backend"))) {
                return p;
            }
        }
        return fail("无法定位仓库根（找不到 ecos_backend/），扫描面不可信：user.dir=" + cur);
    }
}
