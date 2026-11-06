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
    /** 单源内的 DROP（V315 起用于清退孤儿副本）：按脚本版本顺序与 CREATE 同序应用，判「跑完后的净落点」。 */
    private static final Pattern DROP_TABLE =
            Pattern.compile("(?i)drop\\s+table\\s+(?:if\\s+exists\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*\\.\\s*([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern QUALIFIED_LITERAL =
            Pattern.compile("\"([a-z_][a-z0-9_]*\\.[a-z_][a-z0-9_]*)\"");
    private static final Pattern SCRIPT_VERSION = Pattern.compile("V(\\d+)(?:\\.(\\d+))?.*\\.sql");

    /**
     * 允许多 schema 同名的表（其余一律判红）：{@code 表名 → 理由}。
     * <p>口径是「单源跑完后的净落点」（同序应用 CREATE/DROP），已被 V315 清退的 public 副本不再计入。</p>
     */
    private static final Map<String, String> ALLOWED_DUPLICATES = Map.of(
            "outbox_event", "域内 outbox 模式（V48 按 11 个业务域各建一张）",
            "ecos_business_scenario",
            "V123 认知域错放镜像（R-13 ① 只停写不迁移）与 V315 补建的 ecos_ai 读端并存，属登记在案的镜像而非重复缺陷");

    private static final Map<String, Integer> EXPECTED_DUPLICATE_WIDTH = Map.of(
            "outbox_event", 11,
            "ecos_business_scenario", 2);

    /**
     * 声明式目录（非读端）：{@code legacyWriteSuspended} 里登记的是 IR03「只停写、不 DROP、不改名」的旧形态，
     * 没人读它。豁免必须「仍在命中」——文件里若不再出现这类字面量，说明清单被清空，本豁免应同批删除。
     */
    private static final Map<String, String> NON_READ_SURFACES = Map.of(
            "ecos_backend/engine/cognitive-engine/cognitive-engine-api/src/main/java/com/chinacreator/gzcm/engine/cognitive/api/objects/CognitiveObjectClass.java",
            "认知对象目录的 legacyWriteSuspended 清单（IR03 只停写），登记的是历史落点而非读端");

    /**
     * 存量分叉（只减不增）：{@code 代码所读 schema.表 → 登记理由与修法}。
     * <p><b>2026-11-06 #147Z-6 起清零</b>：原 5 条分叉由 V315 一次性收口（补建代码读端 schema 的 5 张表
     * + 清退 public 孤儿副本，用户当日明确授权 DROP），此后本表应为空——任何新条目都意味着
     * 「DDL 建 A、代码读 B」的重现，处置见类注释，禁加白名单。</p>
     */
    private static final Map<String, String> KNOWN_FORKS = knownForks();

    private static Map<String, String> knownForks() {
        return new LinkedHashMap<>();
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
    @DisplayName("DDL 内多 schema 同名表只许在册的域内 outbox 与登记镜像")
    void duplicatedTableNamesAreLimitedToRegisteredPatterns() throws IOException {
        AnalyzeResult r = analyzeRepo(repoRoot());
        Set<String> duplicated = new TreeSet<>(r.duplicatedTables);
        assertEquals(new TreeSet<>(ALLOWED_DUPLICATES.keySet()), duplicated,
                "DDL 单源出现了新的多 schema 同名表（按跑完净落点计）：" + duplicated
                        + "（重复表会制造读写分叉；确属设计请登记到 ALLOWED_DUPLICATES 并写明理由）");
        for (Map.Entry<String, Integer> e : EXPECTED_DUPLICATE_WIDTH.entrySet()) {
            assertEquals(e.getValue(), r.ddlSchemasByTable.get(e.getKey()).size(),
                    e.getKey() + " 的 schema 覆盖数变了：" + r.ddlSchemasByTable.get(e.getKey()));
        }
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
        // 按脚本版本顺序同序应用 CREATE/DROP ⇒ 得到「单源跑完后的净落点」，而不是历史语句的简单并集
        List<String> files = new ArrayList<>(ddl.keySet());
        files.sort(DdlCodeSchemaAlignmentGateTest::compareScriptVersions);
        for (String file : files) {
            String sql = ddl.get(file);
            List<Op> ops = new ArrayList<>();
            Matcher c = CREATE_TABLE.matcher(sql);
            while (c.find()) {
                ops.add(new Op(c.start(), true, c.group(1).toLowerCase(Locale.ROOT), c.group(2).toLowerCase(Locale.ROOT)));
            }
            Matcher d = DROP_TABLE.matcher(sql);
            while (d.find()) {
                ops.add(new Op(d.start(), false, d.group(1).toLowerCase(Locale.ROOT), d.group(2).toLowerCase(Locale.ROOT)));
            }
            ops.sort((a, b) -> Integer.compare(a.pos, b.pos));
            for (Op op : ops) {
                Set<String> schemas = byTable.computeIfAbsent(op.table, k -> new TreeSet<>());
                if (op.create) {
                    schemas.add(op.schema);
                } else {
                    schemas.remove(op.schema);
                    if (schemas.isEmpty()) {
                        byTable.remove(op.table);
                    }
                }
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

    private record Op(int pos, boolean create, String schema, String table) {
    }

    /** V 号排序：主版本数值优先，子版本（如 V170.2）按第二段数值。 */
    private static int compareScriptVersions(String a, String b) {
        Matcher ma = SCRIPT_VERSION.matcher(a);
        Matcher mb = SCRIPT_VERSION.matcher(b);
        if (!ma.matches() || !mb.matches()) {
            return a.compareTo(b);
        }
        int c = Integer.compare(Integer.parseInt(ma.group(1)), Integer.parseInt(mb.group(1)));
        if (c != 0) {
            return c;
        }
        int na = ma.group(2) == null ? 0 : Integer.parseInt(ma.group(2));
        int nb = mb.group(2) == null ? 0 : Integer.parseInt(mb.group(2));
        return Integer.compare(na, nb);
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
