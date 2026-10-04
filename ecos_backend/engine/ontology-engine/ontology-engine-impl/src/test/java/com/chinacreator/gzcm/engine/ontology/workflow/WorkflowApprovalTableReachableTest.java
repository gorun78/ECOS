package com.chinacreator.gzcm.engine.ontology.workflow;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W85 / C69（详细设计-03 §七.1，M0 P0，O-17）— buszhi/workflow 审批表 schema 漂移离线护栏。
 *
 * <p><b>问题</b>：审批记录仓库源码曾以<b>裸表名</b>查询审批表；V47 曾将其迁到 {@code ecos_workflow}，
 * V173 又在 {@code ecos_ontology} 重落合规形态（append-only IR03）。裸名在最前 schema 搜集下指向查不到 /
 * 历史漂位——结构必败（relation does not exist），CI 不覆盖即静默落 runtime 500。本护栏把"跨 schema
 * 缺表"从运行期提前到构建期。</p>
 *
 * <p><b>离线走查（零 live 库，不 Spring 容器）</b>——V173 将审批表落到 <b>{@code ecos_ontology}</b>
 * 真源（本册担管 schema），故本护栏落在 ontology-engine-impl 侧、以 <b>仓根相对路径</b>走查
 * buszhi-impl 源码与 gateway 单源 DDL：</p>
 * <ol>
 *   <li>V173 含合规建表句 {@code CREATE TABLE … ecos_ontology.ecos_workflow_approval (}（防未来挪真源）；</li>
 *   <li>buszhi-impl 全部 {@code src/main/java} 去注释后，禁出现<b>裸表名</b>（未被 {@code ecos_ontology.}
 *       前缀修饰的审批表 token）——任何非注释代码（含 SQL 字符串）出现即红；</li>
 *   <li>正面对称：审批仓库显式引用 schema 限定名 &ge; 3 处（4 SQL：findById / findByTaskId /
 *       findByInstanceId / insert）；</li>
 *   <li>白线：V47 历史 {@code SET SCHEMA} 迁移段不含合规建表句（真源只在 V173，不叠中心）。</li>
 * </ol>
 *
 * <p><b>跨栏边界纪律（同校订三十六）</b>：本护栏<b>只锁认领表</b>（审批表），不扩到
 * {@code ecos_workflow_*} 其余兄弟表（主表 / 实例 / 日志 v2 归 W86/O-18 双模型 + 物理删除面，各有 C 收口）；
 * 且<b>只锁 schema 限定</b>——{@code SELECT *} 与 {@code ?::jsonb} 方言列归 W86/C70，不本项扩。
 * 本文件自身不写裸表名连续可 hit 字面量（防 self-lock，同校订三十六 docstring 陷阱）。</p>
 */
class WorkflowApprovalTableReachableTest {

    private static final String V173 = "V173__workflow_model_converge.sql";
    private static final String V47  = "V47__ecos_schema_isolation.sql";

    /** 认领表名（拆二段拼接，防本文件在 self-scan 里被自己的 pattern 字面量咬到——同校订三十六）。 */
    private static final String TABLE = "ecos_workflow" + "_approval";
    /** 合规限定名（真源形态）。 */
    private static final String QUALIFIED = "ecos_ontology." + TABLE;
    /** 裸表名：前后都<b>不</b>是 schema 限定前缀（前面无 {@code .} 分隔，后面不接 ident）→ 只匹配真裸名。 */
    private static final Pattern BARE = Pattern.compile("(?<![\\w.])" + Pattern.quote(TABLE) + "(?![\\w.])");
    /** 合规建表锚（V173 真源形态）。 */
    private static final Pattern QUALIFIED_CREATE = Pattern.compile(
            "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?ecos_ontology\\." + Pattern.quote(TABLE) + "\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("V173 单源目录承载合规新表 CREATE TABLE ecos_ontology.<审批表>（形态锚定）")
    void v173OfficialCreateTableAnchorPresent() throws IOException {
        Path v173 = resolveMigrationDir().resolve(V173);
        assertTrue(Files.isRegularFile(v173), "单源目录缺失 " + V173 + "（W85 真源）");
        String body = Files.readString(v173, StandardCharsets.UTF_8);
        assertTrue(QUALIFIED_CREATE.matcher(body).find(),
                "V173 未含合规建表句 'CREATE TABLE … ecos_ontology." + TABLE + " (' —— 单源漂移");
    }

    @Test
    @DisplayName("buszhi-impl 源码去注释后无裸表名（未被 schema 前缀限定）—— 违规即红")
    void buszhiImplCodeHasNoBareApprovalTableName() throws IOException {
        Path buszhiSrc = resolveBuszhiSrcMainJava();
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(buszhiSrc)) {
            List<Path> javas = files
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .collect(Collectors.toList());
            assertTrue(!javas.isEmpty(), "buszhi-impl src/main 未找到 .java（扫描路径异常）");
            for (Path p : javas) {
                String raw = Files.readString(p, StandardCharsets.UTF_8);
                String code = stripComments(raw);
                if (BARE.matcher(code).find()) {
                    hits.add(rel(buszhiSrc, p) + " : 非注释/非 schema 限定处出现裸审批表名（应限定 ecos_ontology.，W85）");
                }
            }
        }
        assertTrue(hits.isEmpty(),
                "发现 W85 违规（buszhi-impl 审批表未 schema 限定，IR05/ST07 违）：\n" + String.join("\n", hits));
    }

    @Test
    @DisplayName("正面对称 — 审批仓库显式引用 schema 限定名 ≥ 3 处（findById/findByTaskId/findByInstanceId/insert）")
    void repositoryReferencesSchemaQualifiedTableName() throws IOException {
        Path repo = resolveBuszhiSrcMainJava()
                .resolve("com").resolve("chinacreator").resolve("gzcm").resolve("buszhi").resolve("workflow")
                .resolve("WorkflowApprovalRepository.java");
        assertTrue(Files.isRegularFile(repo), "WorkflowApprovalRepository.java 未找到（" + repo + "）");
        String body = Files.readString(repo, StandardCharsets.UTF_8);
        int hits = countOccurrences(body, QUALIFIED);
        assertTrue(hits >= 3,
                "WorkflowApprovalRepository.java 有 " + hits + " 处 '" + QUALIFIED + "'，预期 >= 3（W85 schema 限定切链）");
    }

    @Test
    @DisplayName("白线 — V47 历史 SET SCHEMA 迁移段不含合规建表句（真源只在 V173，V47 不叠中心）")
    void v47HistoricalAltersAreOutOfScope() throws IOException {
        Path v47 = resolveMigrationDir().resolve(V47);
        assertTrue(Files.isRegularFile(v47), "V47 文件名存在性预期成立");
        String v47body = Files.readString(v47, StandardCharsets.UTF_8);
        assertTrue(v47body.contains(TABLE),
                "V47 应仍含历史迁移段（IR02/03 不删只读，历史形态不动）");
        assertTrue(!QUALIFIED_CREATE.matcher(v47body).find(),
                "V47 内不应出现 ecos_ontology." + TABLE + " 的 CREATE TABLE（真源在 V173，不叠中心）");
    }

    // ─────────────────────────── helpers ───────────────────────────

    /** ECOS 仓根（含 {@code docs/} + {@code ecos_backend/}）：从本类编译输出（target/test-classes）上溯锚定。 */
    private Path resolveRoot() {
        Path cur;
        try {
            cur = Path.of(WorkflowApprovalTableReachableTest.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            fail("无法解析本类 codeSource 定位仓根: " + e.getMessage());
            return null; // unreachable
        }
        for (int i = 0; i < 16 && cur != null; i++) {
            if (Files.isDirectory(cur.resolve("docs")) && Files.isDirectory(cur.resolve("ecos_backend"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        fail("无法从本类 codeSource（target/test-classes）上溯到 ECOS 仓根（含 docs + ecos_backend）");
        return null; // unreachable
    }

    private Path resolveMigrationDir() {
        return resolveRoot().resolve("ecos_backend").resolve("gateway")
                .resolve("src").resolve("main").resolve("resources")
                .resolve("db").resolve("migration");
    }

    private Path resolveBuszhiSrcMainJava() {
        Path src = resolveRoot().resolve("ecos_backend").resolve("services")
                .resolve("buszhi").resolve("impl").resolve("buszhi-impl")
                .resolve("src").resolve("main").resolve("java");
        assertTrue(Files.isDirectory(src), "buszhi-impl src/main/java 不可解析（" + src + "）");
        return src;
    }

    private static String rel(Path root, Path p) {
        try {
            return root.relativize(p).toString().replace('\\', '/');
        } catch (Exception e) {
            return p.toString();
        }
    }

    private static int countOccurrences(String hay, String needle) {
        int n = 0, idx = 0;
        while ((idx = hay.indexOf(needle, idx)) >= 0) {
            n++;
            idx += needle.length();
        }
        return n;
    }

    /** 剥 Javadoc / 行注释 / 块注释（不排除字符串字面——SQL 字符串正是违规体，须保留）。 */
    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        boolean inBlock = false;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            char next = (i + 1 < src.length()) ? src.charAt(i + 1) : 0;
            if (inBlock) {
                if (c == '*' && next == '/') {
                    inBlock = false;
                    i++;
                }
                continue;
            }
            if (c == '/' && next == '/') {
                while (i < src.length() && src.charAt(i) != '\n') i++;
                i--;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlock = true;
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}

