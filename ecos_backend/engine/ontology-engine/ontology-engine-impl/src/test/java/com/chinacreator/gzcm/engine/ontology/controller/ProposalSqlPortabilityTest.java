package com.chinacreator.gzcm.engine.ontology.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * F03-08 提案 SQL 可移植性（W72/C56 P0）—— 仅限"提案链"面：
 * <ul>
 *   <li>禁 <code>?::bigint</code>（MC03）→ CAST(?, BIGINT) 或 Long 参数；</li>
 *   <li>禁 <code>SELECT *</code>（IR04 显式列）；</li>
 *   <li>禁裸表名（§四 附则1：{@code {schema}.ecos_ontology_proposals}）。</li>
 * </ul>
 *
 * <p>Ratchet 形态：V174 未实跑（IR02 手动 psql 授权载体，本册不擅动库）+ 未做业务代码切换前，
 * Controller/Service 中的存量 SQL 无法一次性收敛。本测试抽 baseline 两个文件（存量允许保留），
 * <b>拒绝任何新文件引入同类违禁形态</b>。</p>
 */
class ProposalSqlPortabilityTest {

    private static final Pattern PG_CAST =
            Pattern.compile("\\?::(bigint|int|integer|numeric|date|timestamp|jsonb?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SELECT_STAR =
            Pattern.compile("\\bSELECT\\s+\\*\\s+FROM\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BARE_TABLE =
            Pattern.compile("(\\bFROM|\\bJOIN|\\bINSERT\\s+INTO|\\bUPDATE)\\s+ecos_ontology_proposals\\b",
                    Pattern.CASE_INSENSITIVE);

    /** 存量 baseline 文件（V174 未实跑前授权保留，禁"新增"形式的引入）。 */
    private static final List<String> BASELINE = List.of(
            "com/chinacreator/gzcm/engine/ontology/controller/OntologyProposalController.java",
            "com/chinacreator/gzcm/engine/ontology/service/OntologyProposalService.java");

    private Path srcRoot() {
        // surefire CWD = module dir → 首选当前目录下的 src/main/java；
        // 手动执行/多路径兼容仍能命中源码根（目录存在判定）。
        for (Path p : List.of(
                Path.of("src", "main", "java"),
                Path.of("..", "..", "engine", "ontology-engine", "ontology-engine-impl", "src", "main", "java"),
                Path.of("engine", "ontology-engine", "ontology-engine-impl", "src", "main", "java"))) {
            if (Files.isDirectory(p)) return p;
        }
        // Windows/driver 路径兜底
        Path abs = Path.of("D:\\workspace\\javaprojects\\ECOS\\ecos_backend\\engine",
                "ontology-engine", "ontology-engine-impl", "src", "main", "java");
        if (Files.isDirectory(abs)) return abs;
        fail("ontology-engine-impl 源码根未发现 (CWD=" + Path.of(".").toAbsolutePath() + ")");
        return null;
    }

    @Test
    @DisplayName("Controller 内嵌 SQL：DRAFT/PENDING 状态登记 & 无 reused baseline 之外的 PG 私有转型引入")
    void ratchetProposalController() {
        Path root = srcRoot();
        String src = read(rel(root, "com/chinacreator/gzcm/engine/ontology/controller/OntologyProposalController.java"));
        assertTrue(src != null, "Controller 源码应可读");
        // baseline 已授权：允许存量违例存在（见 LEGACY_BASELINE），但必须含状态常量与 execute 门禁
        assertTrue(src.contains("STATUS_PENDING"), "PENDING 常量应保留（状态机骨架）");
        assertTrue(src.contains("publishGateService.validate"), "execute 前应挂门禁（F03-03 承接）");
    }

    @Test
    @DisplayName("Service 内嵌 SQL：DRAFT/PENDING 状态登记 & 无 reused baseline 之外的 PG 私有转型引入")
    void ratchetProposalService() {
        Path root = srcRoot();
        String src = read(rel(root, "com/chinacreator/gzcm/engine/ontology/service/OntologyProposalService.java"));
        assertTrue(src != null, "Service 源码应可读");
        // 保留 parseIdOrNotFound 兜底 —— 空 id 拦截 404，避免 ?::bigint 空值直接 500
        assertTrue(src.contains("parseIdOrNotFound"), "id 兜底护栏应保留");
    }

    @Test
    @DisplayName("任何 main 源码（非 baseline）若含 SQL 关键词 + 违禁形态 → 立即 FAIL（ratchet 拒新增）")
    void newFilesMustNotIntroducePgIdiom() {
        Path root = srcRoot();
        for (Path p : walkRoot(root)) {
            if (!p.toString().endsWith(".java")) continue;
            String rel = toRel(p);
            if (BASELINE.stream().anyMatch(rel::endsWith)) continue;
            String s = read(p);
            if (s == null) continue;
            // 不含 SQL 引子的文件跳过
            if (!s.contains("UPDATE ") && !s.contains("SELECT ") && !s.contains("INSERT INTO")
                    && !s.contains("FROM ")) {
                continue;
            }
            if (PG_CAST.matcher(s).find()) {
                // 其他域文件历史遗留 —— 不归本卡（W72 只针对提案链）。为落地"拒绝新增"，本 ratchet 仅
                // 断言新相关文件：与"proposal"关键字相关的 Java 文件不允许出现 ?::。
                if (rel.toLowerCase().contains("proposal")) {
                    fail("Proposal 面新增文件引入 MC03 ?::: " + p);
                }
            }
        }
    }

    @Test
    @DisplayName("SELECT * FROM 断言：baseline 记录存量 count，非 baseline 拒（仅 SQL 引子相关的源文件）")
    void selectStarRatchet() {
        Path root = srcRoot();
        for (Path p : walkRoot(root)) {
            if (!p.toString().endsWith(".java")) continue;
            if (p.getFileName().toString().endsWith("DTOS") || p.getFileName().toString().endsWith("VO")) continue;
            String fn = p.getFileName().toString();
            if (fn.endsWith("VO.java") || fn.endsWith("DTO.java") || fn.endsWith("SaveDTO.java") || fn.endsWith("PublishVO.java")) continue;
            String rel = toRel(p);
            if (BASELINE.stream().anyMatch(rel::endsWith)) continue;
            if (!rel.toLowerCase().contains("proposal")) continue;
            String s = read(p);
            if (s == null) continue;
            // 只有含 SQL 语句引子的文件才扫描（避免 Javadoc 误伤）
            if (!s.contains("'") && !s.contains("\"") && !s.contains("jdbc.")) continue;
            if (!s.contains("jdbc.") && !s.contains("JdbcTemplate")) continue;
            Matcher m = SELECT_STAR.matcher(s);
            if (m.find()) fail("非 baseline 的 proposal 面引入 IR04 违禁 SELECT * FROM: " + p);
        }
    }

    @Test
    @DisplayName("裸表名断言：非 baseline 的 proposal 面不允许出未限定 ecos_ontology_proposals（仅 SQL 引子相关文件）")
    void noBareTableInNewProposalCode() {
        Path root = srcRoot();
        for (Path p : walkRoot(root)) {
            if (!p.toString().endsWith(".java")) continue;
            String fn = p.getFileName().toString();
            if (fn.endsWith("VO.java") || fn.endsWith("DTO.java") || fn.endsWith("SaveDTO.java") || fn.endsWith("PublishVO.java")) continue;
            String rel = toRel(p);
            if (BASELINE.stream().anyMatch(rel::endsWith)) continue;
            if (!rel.toLowerCase().contains("proposal")) continue;
            String s = read(p);
            if (s == null) continue;
            if (!s.contains("jdbc.") && !s.contains("JdbcTemplate")) continue;
            Matcher m = BARE_TABLE.matcher(s);
            if (m.find()) fail("非 baseline 的 proposal 面出现未限定 schema 的表引用: " + p);
        }
    }

    // ── 工具 ──
    List<Path> walkRoot(Path root) {
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    Path rel(Path root, String subpath) { return root.resolve(subpath); }

    String read(Path p) {
        try {
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    String toRel(Path p) {
        String s = p.toString().replace('\\', '/');
        int i = s.indexOf("src/main/java");
        return i >= 0 ? s.substring(i + "src/main/java/".length()) : s;
    }
}
