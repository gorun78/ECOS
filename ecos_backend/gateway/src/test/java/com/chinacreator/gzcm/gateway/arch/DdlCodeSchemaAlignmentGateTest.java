package com.chinacreator.gzcm.gateway.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * DDL 单源 ↔ 代码读端 schema 一致性棘轮门（DoD DD-4；#147Z-3 扩面）。
 *
 * <p><b>本门为什么存在</b>：DD-4 原判据是「无新建注册表/重复版本表」，只看表名与注册表命名，
 * <b>看不见「同一张表在 DDL 建在 A schema、代码却按 B schema 读」</b>这一类。该缺陷不会让任何单测变红
 * ——单测 mock 掉 JDBC，活体又跑在已有手工建表的 dev 库上——但<b>从零建库按单源跑完，读端必因缺表失败</b>。
 * 现有实证：`public.ecos_ontology_git_archive_pending` 与代码读的 `ecos_ontology.…` 并存于 dev，
 * 而 DDL 单源只建前者。本门把判据换成「代码里出现的 {@code <schema>.<table>} 字面量，必须能在
 * DDL 单源里找到同 schema 同表的 CREATE」，存量逐条登记，之后<b>只减不增</b>。</p>
 *
 * <p><b>两条面</b>：DDL 面 = {@code docs/50-db_script/02-flyway-migration/gateway/*.sql} 的
 * {@code CREATE TABLE <schema>.<table>}（含 {@code IF NOT EXISTS}）；代码面 = {@code ecos_backend}
 * 各 {@code src/main/java} 的字符串字面量（剥注释）。只对 DDL 里确实存在同名表（任一 schema）的引用做判据，
 * 避免把外部库/其他 schema 的业务字面量误计。</p>
 *
 * <p><b>口径边界（刻意不查）</b>：① {@code ALTER TABLE … SET SCHEMA} 搬迁——本门按「单源跑完后的落点」
 * 判，搬迁语句在单源里若有，读者须把它视为建表同效，故本门只认 CREATE；如出现搬迁形态请同步扩展判据。
 * ② 动态拼接（{@code pendingSchema + "." + TABLE}）——静态判据不可见，只能靠「同一张表至少有一处字面量读端」
 * 或属主门禁兜住；本门不宣称覆盖。③ 声明式目录（{@code legacyWriteSuspended} 之类「只登记、不读」的清单）
 * 走 {@link #NON_READ_SURFACES} 文件级豁免，并要求豁免面「仍在命中」——豁免失效即红，防假绿。</p>
 *
 * <p><b>红了怎么处置</b>：新增分叉<b>一律不许加白名单</b>——要么把 DDL 补到代码所读的 schema
 * （新增迁移，须同批改 {@code MigrationVersionUniquenessTest} 钉值），要么把代码读端改到 DDL 已有的 schema。
 * 修好一条就把 {@link #KNOWN_FORKS} 对应条目删掉（只减不增）。共享工作树里若红差来自他人未提交面，
 * 只做归因，禁代改、禁放宽。</p>
 *
 * @author ECOS Gateway #147Z-3/DD-4
 */
class DdlCodeSchemaAlignmentGateTest {

    private static final String DDL_DIR = "docs/50-db_script/02-flyway-migration/gateway";

    /** DDL 面下限（防扫描根漂移把门变成空转）：实存 260+ 枚脚本。 */
    private static final int MIN_SQL_SCRIPTS = 250;
    /** DDL 建表下限：实测 218 张表。 */
    private static final int MIN_DDL_TABLES = 200;
    /** main java 扫描下限：实测 2500+ 枚。 */
    private static final int MIN_MAIN_JAVA_FILES = 2000;

    private static final Pattern CREATE_TABLE =
            Pattern.compile("(?i)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*\\.\\s*([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern QUALIFIED_LITERAL =
            Pattern.compile("\"([a-z_][a-z0-9_]*\\.[a-z_][a-z0-9_]*)\"");

    /** 域内多 schema 的同名表：outbox 模式按域各建一张，属设计而非重复缺陷。 */
    private static final Set<String> DOMAIN_SCOPED_TABLES = Set.of("outbox_event");
    private static final int DOMAIN_SCOPED_COUNT = 11;

    /**
     * 声明式目录（非读端）：{@code legacyWriteSuspended} 里登记的是 IR03「只停写、不 DROP、不改名」的旧形态，
     * 没人读它。豁免必须「仍在命中」——文件里若不再出现这类字面量，说明清单被清空，本豁免应同批删除。
     */
    private static final Map<String, String> NON_READ_SURFACES = Map.of(
            "ecos_backend/engine/cognitive-engine/cognitive-engine-api/src/main/java/com/chinacreator/gzcm/engine/cognitive/api/objects/CognitiveObjectClass.java",
            "认知对象目录的 legacyWriteSuspended 清单（IR03 只停写），登记的是历史落点而非读端");

    /**
     * 存量分叉（只减不增）：{@code 代码所读 schema.表 → 登记理由与修法}。
     * <p>逐条为 2026-11-05 实测（DDL 面 + live dev PG {@code information_schema} 双证），非推算。</p>
     */
    private static final Map<String, String> KNOWN_FORKS = knownForks();

    private static Map<String, String> knownForks() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ecos_ontology.ecos_ontology_git_archive_pending",
                "DD-4 主案：DDL 单源只建 public.（V170.2），runtime-task 硬编码 ecos_ontology.；"
                        + "修法=增量迁移在 ecos_ontology 建表（public. 侧 DROP 属不可逆，须授权）");
        m.put("ecos_control.ecos_runtime_audit_retry",
                "控制域表按 ST07 目标 schema 读 ecos_control.，DDL 只建 public.（V244）；dev 两处并存；"
                        + "修法=增量迁移补建 ecos_control. 侧");
        m.put("ecos_control.ecos_runtime_event_dlq",
                "DLQ 表读 ecos_control.，DDL 只建 public.（V243）；dev 两处都没有（live 缺表）；"
                        + "修法=增量迁移补建 ecos_control. 侧");
        m.put("ecos_control.ecos_security_asset",
                "安全资产目录读 ecos_control.，DDL 只建 public.；dev 仅 ecos_control. 有表；"
                        + "修法=增量迁移补建 ecos_control. 侧");
        m.put("ecos_ai.ecos_business_scenario",
                "场景只读 DAO 读 ecos_ai.（live 亦仅 ecos_ai.），DDL 单源却建在 ecos_cognitive.；"
                        + "修法=增量迁移补建 ecos_ai. 侧（ecos_cognitive. 侧为停写镜像，不删除）");
        return m;
    }

    @Test
    @DisplayName("代码读端 schema 必须在 DDL 单源里被建出：存量逐条登记，新增即红")
    void everyReadSchemaIsCreatedByTheDdlSingleSource() throws IOException {
        AnalyzeResult r = analyzeRepo(repoRoot());
        Set<String> forks = new TreeSet<>(r.forks);
        assertEquals(new TreeSet<>(KNOWN_FORKS.keySet()), forks,
                diffHint(forks));
    }

    @Test
    @DisplayName("登记有效性：每条分叉的「另一侧」必须真在 DDL 里，豁免面必须仍在命中")
    void registrationsAreStillMeaningful() throws IOException {
        AnalyzeResult r = analyzeRepo(repoRoot());
        for (String fork : KNOWN_FORKS.keySet()) {
            String table = fork.substring(fork.indexOf('.') + 1);
            assertTrue(r.ddlSchemasByTable.containsKey(table),
                    "登记项「" + fork + "」指向的表在 DDL 单源里根本不存在——登记已陈旧，请删除或改判");
            assertTrue(!r.ddlSchemasByTable.get(table).contains(schema(fork)),
                    "登记项「" + fork + "」的 schema 已被 DDL 建出——分叉已修好，请同批删除该登记");
        }
        for (Map.Entry<String, String> e : NON_READ_SURFACES.entrySet()) {
            Path p = repoRoot().resolve(e.getKey());
            assertTrue(Files.isRegularFile(p), "豁免面不存在：" + e.getKey() + "（" + e.getValue() + "）");
            String text = stripComments(Files.readString(p, StandardCharsets.UTF_8));
            assertTrue(QUALIFIED_LITERAL.matcher(text).find(),
                    "豁免面「" + e.getKey() + "」已不再含限定表名字面量——豁免失效，请同批删除本豁免");
        }
    }

    @Test
    @DisplayName("DDL 内多 schema 同名表只许是域内 outbox 模式")
    void duplicatedTableNamesAreLimitedToDomainScopedOutbox() throws IOException {
        AnalyzeResult r = analyzeRepo(repoRoot());
        Set<String> duplicated = new TreeSet<>(r.duplicatedTables);
        assertEquals(new TreeSet<>(DOMAIN_SCOPED_TABLES), duplicated,
                "DDL 单源出现了新的多 schema 同名表：" + duplicated + "（重复表会制造读写分叉；"
                        + "确属域内模式请登记到 DOMAIN_SCOPED_TABLES 并写明理由）");
        assertEquals(DOMAIN_SCOPED_COUNT, r.ddlSchemasByTable.get("outbox_event").size(),
                "域内 outbox 覆盖 schema 数变了：" + r.ddlSchemasByTable.get("outbox_event"));
    }

    @Test
    @DisplayName("扫描面有效：脚本数/建表数/源文件数不得退化")
    void scanSurfaceIsNotEmpty() throws IOException {
        AnalyzeResult r = analyzeRepo(repoRoot());
        assertTrue(r.sqlScripts >= MIN_SQL_SCRIPTS,
                "DDL 单源脚本数退化：" + r.sqlScripts + " < 下限 " + MIN_SQL_SCRIPTS + "（扫描根漂了？）");
        assertTrue(r.ddlSchemasByTable.size() >= MIN_DDL_TABLES,
                "DDL 建表数退化：" + r.ddlSchemasByTable.size() + " < 下限 " + MIN_DDL_TABLES);
        assertTrue(r.mainJavaFiles >= MIN_MAIN_JAVA_FILES,
                "main 源扫描面退化：" + r.mainJavaFiles + " < 下限 " + MIN_MAIN_JAVA_FILES);
    }

    @Test
    @DisplayName("变异自证：同 schema 不误报、异 schema 必红、注释与无关字面量不误计")
    void mutationProvesTheClassifier(@TempDir Path tmp) throws IOException {
        Map<String, String> ddl = new LinkedHashMap<>();
        ddl.put("V1__x.sql", "CREATE TABLE IF NOT EXISTS public.t_probe (id varchar(36));");
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("svc/src/main/java/demo/Ok.java", "class Ok { static final String T = \"public.t_probe\"; }");
        sources.put("svc/src/main/java/demo/Bad.java", "class Bad { static final String T = \"ecos_control.t_probe\"; }");
        sources.put("svc/src/main/java/demo/Notes.java", "class Notes { /** ecos_ai.t_probe */ // ecos_ai.t_probe\n }");
        sources.put("svc/src/main/java/demo/Irrelevant.java", "class Irrelevant { static final String T = \"ecos_ai.not_a_table\"; }");

        AnalyzeResult r = analyze(ddl, sources, NON_READ_SURFACES.keySet());
        assertEquals(Set.of("ecos_control.t_probe"), r.forks,
                "判据失效：异 schema 读端必须被点名，同 schema/注释/无关字面量必须不误计 → " + r.forks);
    }

    // ================= 扫描实现 =================

    private record AnalyzeResult(Set<String> forks, Set<String> duplicatedTables,
                                 Map<String, Set<String>> ddlSchemasByTable,
                                 int sqlScripts, int mainJavaFiles) {
    }

    private static AnalyzeResult analyzeRepo(Path root) throws IOException {
        Map<String, String> ddl = new LinkedHashMap<>();
        Path ddlDir = root.resolve(DDL_DIR);
        List<Path> scripts = new ArrayList<>();
        if (Files.isDirectory(ddlDir)) {
            try (Stream<Path> s = Files.list(ddlDir)) {
                scripts = s.filter(p -> p.getFileName().toString().endsWith(".sql")).toList();
            }
        }
        for (Path p : scripts) {
            ddl.put(p.getFileName().toString(), Files.readString(p, StandardCharsets.UTF_8));
        }

        Map<String, String> sources = new LinkedHashMap<>();
        Path base = root.resolve("ecos_backend");
        if (Files.isDirectory(base)) {
            try (Stream<Path> s = Files.walk(base)) {
                for (Path p : s.filter(Files::isRegularFile).toList()) {
                    String rel = rel(root, p);
                    if (!rel.contains("/src/main/java/") || !rel.endsWith(".java")) {
                        continue;
                    }
                    sources.put(rel, Files.readString(p, StandardCharsets.UTF_8));
                }
            }
        }
        AnalyzeResult r = analyze(ddl, sources, NON_READ_SURFACES.keySet());
        return new AnalyzeResult(r.forks, r.duplicatedTables, r.ddlSchemasByTable, scripts.size(), sources.size());
    }

    private static AnalyzeResult analyze(Map<String, String> ddl, Map<String, String> sources,
                                         Set<String> nonReadSurfaces) {
        Map<String, Set<String>> byTable = new TreeMap<>();
        for (String sql : ddl.values()) {
            Matcher m = CREATE_TABLE.matcher(sql);
            while (m.find()) {
                byTable.computeIfAbsent(m.group(2).toLowerCase(Locale.ROOT), k -> new TreeSet<>())
                        .add(m.group(1).toLowerCase(Locale.ROOT));
            }
        }
        Set<String> forks = new TreeSet<>();
        for (Map.Entry<String, String> e : sources.entrySet()) {
            if (nonReadSurfaces.contains(e.getKey())) {
                continue;
            }
            Matcher m = QUALIFIED_LITERAL.matcher(stripComments(e.getValue()));
            while (m.find()) {
                String ref = m.group(1).toLowerCase(Locale.ROOT);
                String table = ref.substring(ref.indexOf('.') + 1);
                Set<String> schemas = byTable.get(table);
                if (schemas != null && !schemas.contains(schema(ref))) {
                    forks.add(ref);
                }
            }
        }
        Set<String> duplicated = new TreeSet<>();
        for (Map.Entry<String, Set<String>> e : byTable.entrySet()) {
            if (e.getValue().size() > 1) {
                duplicated.add(e.getKey());
            }
        }
        return new AnalyzeResult(forks, duplicated, byTable, 0, 0);
    }

    private static String schema(String qualified) {
        return qualified.substring(0, qualified.indexOf('.'));
    }

    private static String diffHint(Set<String> forks) {
        Set<String> registered = new TreeSet<>(KNOWN_FORKS.keySet());
        Set<String> unregistered = new TreeSet<>(forks);
        unregistered.removeAll(registered);
        Set<String> stale = new TreeSet<>(registered);
        stale.removeAll(forks);
        return "代码读端 schema 与 DDL 单源不一致（从零建库必断）。"
                + "\n新增（禁加白名单：补 DDL 或改读端）：" + unregistered
                + "\n已修好但登记未删（陈旧豁免=假绿）：" + stale;
    }

    /** 剥 // 与块注释（与仓扫门族同法：注释里的表名不计）。 */
    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        boolean inBlock = false;
        boolean inStr = false;
        boolean inChar = false;
        char prev = 0;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            char n = i + 1 < src.length() ? src.charAt(i + 1) : 0;
            if (inBlock) {
                if (c == '*' && n == '/') {
                    inBlock = false;
                    i++;
                } else if (c == '\n') {
                    out.append('\n');
                }
                prev = 0;
                continue;
            }
            if (inStr) {
                out.append(c);
                if (c == '"' && prev != '\\') {
                    inStr = false;
                }
                prev = c;
                continue;
            }
            if (inChar) {
                out.append(c);
                if (c == '\'' && prev != '\\') {
                    inChar = false;
                }
                prev = c;
                continue;
            }
            if (c == '"') {
                inStr = true;
                out.append(c);
                prev = c;
                continue;
            }
            if (c == '\'') {
                inChar = true;
                out.append(c);
                prev = c;
                continue;
            }
            if (c == '/' && n == '/') {
                while (i < src.length() && src.charAt(i) != '\n') {
                    i++;
                }
                out.append('\n');
                prev = 0;
                continue;
            }
            if (c == '/' && n == '*') {
                inBlock = true;
                i++;
                prev = 0;
                continue;
            }
            out.append(c);
            prev = c;
        }
        return out.toString();
    }

    private static String rel(Path root, Path p) {
        return root.relativize(p).toString().replace('\\', '/');
    }

    /** Surefire CWD 漂移：向上探测同时含 ecos_backend/ 与 ecos-docker/ 的仓库根。 */
    private static Path repoRoot() {
        Path cur = Paths.get("").toAbsolutePath().normalize();
        for (int i = 0; i < 8 && cur != null; i++) {
            if (Files.isDirectory(cur.resolve("ecos_backend")) && Files.isDirectory(cur.resolve("ecos-docker"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("未找到仓库根，CWD=" + Paths.get("").toAbsolutePath());
    }
}
