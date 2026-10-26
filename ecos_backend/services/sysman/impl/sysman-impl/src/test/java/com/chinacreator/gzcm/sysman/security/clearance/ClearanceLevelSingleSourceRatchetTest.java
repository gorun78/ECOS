package com.chinacreator.gzcm.sysman.security.clearance;

import com.chinacreator.gzcm.sysman.security.clearance.model.ClearanceLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-B4-99 a)＋c) 关账棘轮门（#142/B4-3e-88）。
 *
 * <p>登记原文（台账 v1.0.169 行 ⑧）：a)「无档案硬编码 L1」＝ClearanceInterceptor 的
 * {@code return 1;} 与两宿主 {@code populateProfileResult} 的三枚 L1 硬编码；
 * c) 档位口径三分裂＝DDL V24 列注释 0-4／Java 三枚私有 LEVEL_NAMES 0-4／字典
 * V12 与 V40 codes 1-5，且列值 0 无字典项。
 *
 * <p>本门判据（读得准是对方讲不讲，不是账本自洽）：
 * <ol>
 *   <li>全仓 main 源（剥注释后）私有档位词表字面量清零——档位名只许出自
 *       属主单源 {@link ClearanceLevel#NAMES} 一处，副本复活即刻红；</li>
 *   <li>准入面五档全断兜底腿取 {@code ClearanceLevel.NO_PROFILE_FALLBACK}，
 *       {@code return 1;} 字面腿清零（历史值＝1 保留在常量、不留在散点）；</li>
 *   <li>两宿主 {@code populateProfileResult} 缺省三键（clearanceLevel／level／levelName）
 *       全部取单源常量，无裸 {@code : 1}／{@code , 1)}／{@code "L1内部"} 字面；</li>
 *   <li>行为钉值＝nameOf 的越界兜底与闭表逐项；</li>
 *   <li>合成复活夹具＝判据对「私表复活＋硬编码兜底」两形态必红，防恒绿。</li>
 * </ol>
 */
class ClearanceLevelSingleSourceRatchetTest {

    private static final Pattern LEVEL_NAMES_DECL =
            Pattern.compile("String\\[\\]\\s+LEVEL_NAMES");
    private static final Pattern L1_LITERAL =
            Pattern.compile("\"L1[内]部\"");
    private static final Pattern RETURN_BARE_ONE =
            Pattern.compile("return\\s+1\\s*;");
    private static final String[] NAME_TABLE = {"L0公开", "L1内部", "L2保密", "L3机密", "L4绝密"};

    // ── 扫描面工具 ─────────────────────────────────────────────

    private static List<Path> mainJavaFiles() throws IOException {
        Path backend = findRepoRoot().resolve("ecos_backend");
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(backend)) {
            s.filter(p -> p.toString().endsWith(".java"))
             .filter(p -> p.toString().contains("src" + File.separator + "main"))
             .filter(p -> !p.toString().contains("/target/"))
             .sorted()
             .forEach(out::add);
        }
        return out;
    }

    private static Path findRepoRoot() throws IOException {
        Path cwd = Path.of("").toAbsolutePath();
        Path p = cwd;
        while (p != null) {
            if (Files.isDirectory(p.resolve("ecos_backend")) && Files.isDirectory(p.resolve("docs"))) {
                return p;
            }
            p = p.getParent();
        }
        throw new IOException("repo root not found from " + cwd);
    }

    private static String stripComments(String src) {
        // 剥块注释与行注释（判决面＝活代码；「只写在注释里」不算复活）
        String noBlock = src.replaceAll("(?s)/\\*.*?\\*/", " ");
        StringBuilder sb = new StringBuilder();
        for (String line : noBlock.split("\n", -1)) {
            int i = line.indexOf("//");
            sb.append(i >= 0 ? line.substring(0, i) : line).append('\n');
        }
        return sb.toString();
    }

    // ── ① 全仓私有档位词表字面量清零 ──────────────────────────

    @Test
    @DisplayName("档位名表字面量全仓（剥注释后）只在属主单源 ClearanceLevel 一处")
    void privateNameTableIsZeroOutsideOwnerSingleSource() throws IOException {
        Path root = findRepoRoot();
        List<Path> allFiles = mainJavaFiles();
        assertTrue(allFiles.size() > 500,
                "扫描面塌陷：main 源 java 仅 " + allFiles.size() + " 枚（基线 >500），判据假绿");
        List<String> hits = new ArrayList<>();
        for (Path f : allFiles) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            String src = stripComments(Files.readString(f, StandardCharsets.UTF_8));
            if (rel.contains("clearance/model/ClearanceLevel.java")) {
                assertTrue(LEVEL_NAMES_DECL.matcher(src).find() || src.contains("NAMES ="),
                        "属主单源 ClearanceLevel 自身词表丢失＝单源塌陷");
                continue;
            }
            if (LEVEL_NAMES_DECL.matcher(src).find()) {
                hits.add(rel);
            }
        }
        assertTrue(hits.isEmpty(),
                "私有档位名表字面量在 ClearanceLevel 之外复活: " + hits);
    }

    @Test
    @DisplayName("「L1内部」字面量在 main 源（剥注释后）只在属主单源出现")
    void l1LabelLiteralIsZeroOutsideOwnerSingleSource() throws IOException {
        Path root = findRepoRoot();
        List<String> hits = new ArrayList<>();
        for (Path f : mainJavaFiles()) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            String src = stripComments(Files.readString(f, StandardCharsets.UTF_8));
            if (rel.contains("clearance/model/ClearanceLevel.java")) {
                continue;
            }
            if (L1_LITERAL.matcher(src).find()) {
                hits.add(rel);
            }
        }
        assertTrue(hits.isEmpty(),
                "「L1内部」字面散点仍在（应取 ClearanceLevel.nameOf）: " + hits);
    }

    // ── ② 准入面兜底腿取单源常量 ──────────────────────────────

    @Test
    @DisplayName("ClearanceInterceptor 无档案兜底腿＝NO_PROFILE_FALLBACK，return 1 字面归零")
    void admissionFallbackLegTakesOwnerConstant() throws IOException {
        Path root = findRepoRoot();
        Path file = root.resolve("ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/security/ClearanceInterceptor.java");
        assertTrue(Files.isRegularFile(file), "ClearanceInterceptor 找不到 " + file);
        String src = stripComments(Files.readString(file, StandardCharsets.UTF_8));
        assertTrue(src.contains("return ClearanceLevel.NO_PROFILE_FALLBACK;"),
                "准入面无档案兜底腿未取属主单源常量");
        assertFalse(RETURN_BARE_ONE.matcher(src).find(),
                "准入面 return 1; 硬编码兜底腿复活");
    }

    // ── ③ 两宿主缺省三键取单源 ────────────────────────────────

    @Test
    @DisplayName("engine 侧 SecurityConfigService 缺省三键全取属主单源常量")
    void enginePopulateDefaultsTakeOwnerConstants() throws IOException {
        Path root = findRepoRoot();
        Path file = root.resolve("ecos_backend/engine/security-engine/security-engine-impl/src/main/java/com/chinacreator/gzcm/engine/security/service/SecurityConfigService.java");
        String src = stripComments(Files.readString(file, StandardCharsets.UTF_8));
        assertTrue(src.contains("ClearanceLevel.NO_PROFILE_FALLBACK"),
                "engine 侧缺省三键未改取属主单源常量");
        assertFalse(src.contains("\"L1内部\""), "engine 侧 levelName 缺省字面复活");
        assertFalse(src.contains("profile.getLevel() != null ? profile.getLevel() : 1"),
                "engine 侧 level 缺省裸字面 1 复活");
        assertFalse(src.contains("profile.getClearanceLevel() != null ? profile.getClearanceLevel() : 1"),
                "engine 侧 clearanceLevel 缺省裸字面 1 复活");
    }

    @Test
    @DisplayName("属主侧 SecurityProfileController 缺省三键全取属主单源常量")
    void ownerPopulateDefaultsTakeOwnerConstants() throws IOException {
        Path root = findRepoRoot();
        Path file = root.resolve("ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/controller/SecurityProfileController.java");
        String src = stripComments(Files.readString(file, StandardCharsets.UTF_8));
        assertTrue(src.contains("ClearanceLevel.NO_PROFILE_FALLBACK"),
                "属主侧缺省三键未改取属主单源常量");
        assertFalse(src.contains("\"L1内部\""), "属主侧 levelName 缺省字面复活");
        assertFalse(src.contains("profile.getLevel() != null ? profile.getLevel() : 1"),
                "属主侧 level 缺省裸字面 1 复活");
        assertFalse(src.contains("profile.getClearanceLevel() != null ? profile.getClearanceLevel() : 1"),
                "属主侧 clearanceLevel 缺省裸字面 1 复活");
    }

    // ── ④ 行为钉值 ────────────────────────────────────────────

    @Test
    @DisplayName("nameOf 闭表逐枚与越界兜底钉值（与旧三枚副本口径逐字一致）")
    void nameOfPinsClosedTableAndOverflowFallback() {
        for (int i = 0; i < NAME_TABLE.length; i++) {
            assertEquals(NAME_TABLE[i], ClearanceLevel.nameOf(i), "档位 " + i);
        }
        assertEquals("L5", ClearanceLevel.nameOf(5), "连续上界 5 须走越界兜底");
        assertEquals("L-1", ClearanceLevel.nameOf(-1), "下界外兜底形态");
        assertEquals(5, ClearanceLevel.NAMES.length, "闭表长度＝5（0-4 五档）");
        assertEquals(1, ClearanceLevel.NO_PROFILE_FALLBACK, "无档案兜底值保持历史决策 1（只上收单源、不改值）");
    }

    // ── ⑤ 合成复活夹具 ────────────────────────────────────────

    @Test
    @DisplayName("合成复活夹具：私表字面＋裸字面兜底两形态判据必红（防恒绿）")
    void syntheticRevivalFixturesTripEveryJudgment() {
        String revival = String.join("\n",
                "class T {",
                "    private static final String[] LEVEL_NAMES = {\"L0公开\", \"L1内部\", \"L2保密\", \"L3机密\", \"L4绝密\"};",
                "    int fallback() {",
                "        return 1;",
                "    }",
                "    int color() { return 1; } // 正常业务里的 return 1 与档位无关，勿误伤",
                "    String lbl() { return \"L1内部\"; }",
                "}");
        String stripped = stripComments(revival);
        assertTrue(LEVEL_NAMES_DECL.matcher(stripped).find(), "夹具私表判据未命中＝判据坏");
        assertTrue(L1_LITERAL.matcher(stripped).find(), "夹具 L1 字面判据未命中＝判据坏");
        assertTrue(RETURN_BARE_ONE.matcher(stripped).find(), "夹具裸 return 1 判据未命中＝判据坏");

        // 合规形态：走单源常量的方法体不应被三条判据中的 any-hit 误伤（按 ClearLevel 命中面计数）
        Map<String, Integer> witness = new LinkedHashMap<>();
        witness.put("const", stripped.contains("ClearanceLevel.NO_PROFILE_FALLBACK") ? 1 : 0);
        assertEquals(0, witness.get("const"), "夹具中不得含合规形态（夹具＝纯复活体）");
    }
}
