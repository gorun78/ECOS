package com.chinacreator.gzcm.gateway;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * #144 门禁批 · 共享扫描底座（测试域内部件，非生产代码）。
 *
 * <p><b>为什么必须共享</b>（裁决 D-#140E-3 的落地形态）：{@code DrBaselineColumnsTest}、
 * {@code Mc02TypeWhitelistTest}、{@code Mc03TimeFormTest} 三枚门禁判的是同一批 DDL 文件，
 * 若各自实现一份解析器，就会出现「同一张表在 A 门禁算违例、在 B 门禁算合规」的双轨口径——
 * 这正是差距文档定义的假绿形态。三枚门禁一律经本类取数，解析口径单源。</p>
 *
 * <p><b>判定面</b>：{@code docs/50-db_script/02-flyway-migration/gateway/} 内全部 {@code .sql}
 * （DDL 单源目录，铁律 v2.2 §3.1）。只读文件、不连库。</p>
 *
 * <p><b>版本分桶</b>（与《数据库访问规范》v1.4 §三「存量不补；V150+ 新表必带」及 V200 彻底治理
 * 分界对齐）：
 * <ul>
 *   <li>{@link Bucket#LEGACY} = 主版本号 &lt; 150（治理前存量，规范明文不返工）</li>
 *   <li>{@link Bucket#MID} = 150 ≤ 主版本号 &lt; 200（规范已生效段）</li>
 *   <li>{@link Bucket#GOVERNED} = 主版本号 ≥ 200（V200 schema 治理之后，红线零容忍段）</li>
 * </ul>
 * GOVERNED 段门禁做<b>成员级</b>名录（逐表逐列钉死），LEGACY/MID 段做<b>计数级</b>钉死——
 * 理由与已知弱点在三枚门禁各自的类注释里写明，不允许在此处含糊。</p>
 *
 * <p><b>解析口径（三枚门禁共用，也是本类自身的被检面）</b>：
 * ① 逐行剥离 {@code --} 行注释（单引号内的 {@code --} 不算注释，避免把字面量当注释吃掉）；
 * ② {@code CREATE TABLE [IF NOT EXISTS] <名> ( ... )} 取平衡括号内的列定义，
 * 顶层逗号切分（括号深度 0 且不在引号内才算分隔）；
 * ③ 列名支持裸标识符与 <b>双引号标识符</b>（{@code "LOG_SEQ"}）——V285 实测两枚表全部用
 * 大写引号列名，解析器若只认裸标识符会「看到一张没有列的表」，从而把该表的 {@code BIGSERIAL}
 * 等类型违例整张漏掉（本类把「零列解析成功」的表单独登记为 {@link Table#parsedAnyColumn()}
 * 为 false 的清单，供门禁反向对照）；
 * ④ 约束行（PRIMARY/UNIQUE/CONSTRAINT/FOREIGN/CHECK/EXCLUDE/LIKE）不计为列；
 * ⑤ 类型串取列名后第一个标识符及其可选 {@code (p,s)} 与 {@code []}，统一大写去空格；
 * ⑥ {@code ALTER TABLE <名> ADD [COLUMN] [IF NOT EXISTS] <列> <类型>} 与
 * {@code MODIFY/ALTER COLUMN <列> TYPE <类型>} 一并纳入列面（存量收口大量走 ALTER）。</p>
 */
final class DdlShapeScanner {

    private static final String MIGRATION_REL = "docs/50-db_script/02-flyway-migration/gateway";

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([A-Za-z0-9_.\"]+)\\s*\\(");
    private static final Pattern ALTER_ADD = Pattern.compile(
            "(?i)ALTER\\s+TABLE\\s+([A-Za-z0-9_.\"]+)\\s+ADD\\s+(?:COLUMN\\s+)?(?:IF\\s+NOT\\s+EXISTS\\s+)?"
                    + "(\"?[A-Za-z0-9_]+\"?)\\s+([A-Za-z0-9_]+(?:\\s*\\([^)]*\\))?(?:\\s*\\[\\s*\\])?)");
    private static final Pattern ALTER_TYPE = Pattern.compile(
            "(?i)ALTER\\s+TABLE\\s+([A-Za-z0-9_.\"]+)\\s+ALTER\\s+COLUMN\\s+(\"?[A-Za-z0-9_]+\"?)\\s+TYPE\\s+"
                    + "([A-Za-z0-9_]+(?:\\s*\\([^)]*\\))?(?:\\s*\\[\\s*\\])?)");
    private static final Pattern VERSION_PREFIX = Pattern.compile("^V(\\d+)");
    private static final Pattern COLUMN_HEAD = Pattern.compile(
            "^(\"?[A-Za-z0-9_]+\"?)\\s+([A-Za-z0-9_]+(?:\\s*\\([^)]*\\))?(?:\\s*\\[\\s*\\])?)");
    private static final Pattern CONSTRAINT_HEAD = Pattern.compile(
            "^(?i)(PRIMARY|UNIQUE|CONSTRAINT|FOREIGN|CHECK|EXCLUDE|LIKE|PERIOD)\\b");

    private DdlShapeScanner() {
    }

    /** 版本分桶。 */
    enum Bucket {
        LEGACY, MID, GOVERNED
    }

    /** 一枚列（CREATE 或 ALTER 面）。 */
    record Column(String table, String name, String type) {
        String lowerName() {
            return name.toLowerCase(Locale.ROOT);
        }
    }

    /** 一枚 CREATE TABLE 的解析结果。 */
    record Table(String fileName, int majorVersion, Bucket bucket, String tableName, List<Column> columns) {

        /** 反向对照用：该表是否解析出了至少一枚列（false=解析器盲点，必须登记而非当作合规）。 */
        boolean parsedAnyColumn() {
            return !columns.isEmpty();
        }

        List<String> columnNames() {
            List<String> out = new ArrayList<>();
            for (Column c : columns) out.add(c.lowerName());
            return out;
        }
    }

    /** 扫描结果全集。 */
    record Scan(List<Table> tables, Map<String, List<String>> filesByBucket, List<String> unreadable) {

        List<Table> governed() {
            return filter(Bucket.GOVERNED);
        }

        List<Table> preGoverned() {
            List<Table> out = new ArrayList<>();
            for (Table t : tables) if (t.bucket() != Bucket.GOVERNED) out.add(t);
            return out;
        }

        List<Table> filter(Bucket bucket) {
            List<Table> out = new ArrayList<>();
            for (Table t : tables) if (t.bucket() == bucket) out.add(t);
            return out;
        }

        /** 表名（含 schema 限定原文）到解析结果的索引，供门禁做已知违例点的反向对照。 */
        Map<String, Table> byQualifiedName() {
            Map<String, Table> out = new LinkedHashMap<>();
            for (Table t : tables) out.put(t.fileName() + "#" + t.tableName(), t);
            return out;
        }
    }

    /** ALTER 面追加/改型的列（与 CREATE 面分开返回，便于门禁分别钉死）。 */
    static List<Column> alterColumns() throws IOException {
        List<Column> out = new ArrayList<>();
        for (Map.Entry<String, String> e : sources().entrySet()) {
            String src = e.getValue();
            collect(ALTER_ADD, src, out);
            collect(ALTER_TYPE, src, out);
        }
        return out;
    }

    private static void collect(Pattern p, String src, List<Column> sink) {
        Matcher m = p.matcher(src);
        while (m.find()) {
            sink.add(new Column(stripQuotes(m.group(1)), stripQuotes(m.group(2)), normType(m.group(3))));
        }
    }

    /** 全源扫描（文件名 + 注释剥离 + 建表解析）。 */
    static Scan scan() throws IOException {
        Map<String, String> srcs = sources();
        List<Table> tables = new ArrayList<>();
        Map<String, List<String>> filesByBucket = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : srcs.entrySet()) {
            String fileName = e.getKey();
            int major = majorVersion(fileName);
            Bucket bucket = bucket(major);
            filesByBucket.computeIfAbsent(bucket.name(), k -> new ArrayList<>()).add(fileName);
            Matcher m = CREATE_TABLE.matcher(e.getValue());
            while (m.find()) {
                String tableName = stripQuotes(m.group(1));
                int i = m.end() - 1;
                String body = balancedBody(e.getValue(), i);
                tables.add(new Table(fileName, major, bucket, tableName, parseColumns(body)));
            }
        }
        return new Scan(tables, filesByBucket, List.of());
    }

    /** 单源目录内全部 .sql 的注释剥离正文（文件名 → 正文），按文件名字典序。 */
    static Map<String, String> sources() throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        List<String> names;
        try (Stream<Path> s = Files.list(repoRoot().resolve(MIGRATION_REL))) {
            names = s.filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
        }
        for (String n : names) {
            out.put(n, stripLineComments(Files.readString(
                    repoRoot().resolve(MIGRATION_REL).resolve(n))));
        }
        return out;
    }

    static int scriptCount() throws IOException {
        return sources().size();
    }

    /**
     * 单源目录内某文件的<b>原文</b>（不剥注释）。
     *
     * <p>存在理由：MC03 类门禁的判据面是「正文」，而迁移脚本流行在头部写
     * {@code -- [MC03] 无 timestamptz/裸 cast ✓} 这类<b>自述合规声明</b>
     * （实测 {@code V200__ai_tool_registry.sql:60} 即此形态）。若门禁只吃剥离后的正文，
     * 就永远看不到"违例字样只存在于注释"这一事实，也就无法证明<b>剥离口径本身</b>是生效的。
     * 门禁据此做反向对照：同一文件原文须能命中、剥离后须不命中 ⇒ 剥离面是活的。</p>
     */
    static String rawSource(String fileName) throws IOException {
        return Files.readString(repoRoot().resolve(MIGRATION_REL).resolve(fileName));
    }

    /** DDL 单源目录相对路径（铁律 v2.2 §3.1 权威口径，失败消息引用用）。 */
    static String migrationDirRel() {
        return MIGRATION_REL;
    }

    // ────────────────────────────────────────────────────────────
    // 解析工具（口径单源）
    // ────────────────────────────────────────────────────────────

    static int majorVersion(String fileName) {
        Matcher m = VERSION_PREFIX.matcher(fileName);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    static Bucket bucket(int majorVersion) {
        if (majorVersion < 150) return Bucket.LEGACY;
        if (majorVersion < 200) return Bucket.MID;
        return Bucket.GOVERNED;
    }

    static String bucketLabel(Bucket b) {
        switch (b) {
            case LEGACY: return "V<150";
            case MID: return "V150~199";
            default: return "V200+";
        }
    }

    /** 逐行剥离 -- 行注释；单引号内的 -- 不剥（字面量保护）。 */
    static String stripLineComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        for (String line : src.split("\n", -1)) {
            boolean inSingle = false;
            int cut = -1;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '\'') {
                    inSingle = !inSingle;
                } else if (!inSingle && c == '-' && i + 1 < line.length() && line.charAt(i + 1) == '-') {
                    cut = i;
                    break;
                }
            }
            out.append(cut < 0 ? line : line.substring(0, cut)).append('\n');
        }
        return out.toString();
    }

    /** 从左括号位置起取平衡括号内正文（含引号保护），返回不含外层括号的体。 */
    private static String balancedBody(String src, int openParenIndex) {
        int depth = 0;
        boolean inSingle = false, inDouble = false;
        StringBuilder body = new StringBuilder();
        for (int i = openParenIndex; i < src.length(); i++) {
            char c = src.charAt(i);
            if (!inDouble && c == '\'') inSingle = !inSingle;
            if (!inSingle && c == '"') inDouble = !inDouble;
            if (!inSingle && !inDouble) {
                if (c == '(') {
                    depth++;
                    if (depth == 1) continue;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) return body.toString();
                }
            }
            body.append(c);
        }
        return body.toString();
    }

    /** 建表体 → 列清单（顶层逗号切分，约束行不计）。 */
    private static List<Column> parseColumns(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        boolean inSingle = false, inDouble = false;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (!inDouble && c == '\'') inSingle = !inSingle;
            if (!inSingle && c == '"') inDouble = !inDouble;
            if (!inSingle && !inDouble) {
                if (c == '(') depth++;
                else if (c == ')') depth--;
                else if (c == ',' && depth == 0) {
                    parts.add(cur.toString());
                    cur.setLength(0);
                    continue;
                }
            }
            cur.append(c);
        }
        parts.add(cur.toString());
        List<Column> cols = new ArrayList<>();
        for (String raw : parts) {
            String t = raw.trim();
            if (t.isEmpty() || CONSTRAINT_HEAD.matcher(t).find()) continue;
            Matcher cm = COLUMN_HEAD.matcher(t);
            if (!cm.find()) continue;
            cols.add(new Column("", stripQuotes(cm.group(1)), normType(cm.group(2))));
        }
        return cols;
    }

    private static String stripQuotes(String id) {
        return id.replace("\"", "");
    }

    /** 类型规范化：大写、去空白，保留 {@code (p,s)} 与 {@code []}。 */
    static String normType(String type) {
        return type.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    static Path repoRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve("ecos_backend")) && Files.isDirectory(p.resolve("docs"))) {
                return p;
            }
            Path parent = p.getParent();
            if (parent == null || parent.equals(p)) break;
            p = parent;
        }
        throw new IllegalStateException("cwd 未锚定 ECOS 仓库根（实寻 "
                + Paths.get("").toAbsolutePath() + "，应含 ecos_backend/ 与 docs/）");
    }

    static String simpleName(String qualifiedTable) {
        int dot = qualifiedTable.lastIndexOf('.');
        return dot < 0 ? qualifiedTable : qualifiedTable.substring(dot + 1);
    }
}
