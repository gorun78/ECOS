package com.chinacreator.gzcm.gateway.arch;

import com.chinacreator.gzcm.runtime.access.connector.DataSourceTypeVocabulary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据源类型<b>跨面对账门</b>：前端枚举面 ↔ 后端承流面（#142/B4-3e-63，收口差距 <b>D-B4-78</b>）。
 *
 * <p><b>本门为什么必须存在</b>：D-B4-78 的原始形态是「同一套类型名在 4 个面各自演化」——
 * 前端 {@code pages/data-workbench/types.ts} 的 {@code CONNECTION_TYPES} 列了 19 项，
 * 后端可登记的承流面只有 10 项，两处中间没有任何机器对账。直接后果就是前端选了
 * {@code gaussdb}（→ {@code GAUSSDB}）被后端以「不支持的数据源类型」400 拒，
 * 而 {@code doris}／{@code s3}／{@code kafka} 之类选了也只是把 400 留给用户去撞。
 * 承流侧词表已由 {@link DataSourceTypeVocabulary} 收为单源（同批），本门把<b>前端面钉成对照物</b>：
 * 逐枚判定「前端可选 ⇒ 后端承流，或被显式登记为未承接」，被拒面为<b>闭集棘轮（只减不增）</b>。</p>
 *
 * <p>判据全部读真实仓面文本（禁把 19 枚抄成第二份手抄表），枚数独立钉值 19：
 * 解析器失明（读到 0 枚）时「空被拒集＝绿」的假绿路径由 {@link #criterionGoesRedOnFixtures()} 的夹具堵住。</p>
 *
 * <p><b>本批未做（在册余项，见台账 v1.0.145）</b>：前端把 {@code CONNECTION_TYPES} 改为消费
 * {@code GET /api/v1/datasource/supported-types}（唯一属主出口已存在，前端零消费）；
 * {@code mapDsType} 词表外名回落 {@code postgresql} 的显示面 fail-open（新登记 D-B4-93）；
 * {@code doris} 是否准入、{@code s3}/{@code oss}/{@code sftp}/{@code kafka}/{@code sap}/{@code rest_api}/
 * {@code csv} 幻影选项的处置。</p>
 */
class DatasourceTypeVocabularySingleSourceTest {

    /** 独立钉值：收口前前端 {@code CONNECTION_TYPES} 实列 19 枚（声明序）。 */
    private static final List<String> FRONTEND_TYPES_FROZEN = List.of(
            "postgresql", "mysql", "doris", "clickhouse", "oracle", "mssql", "dm", "kingbase",
            "gaussdb", "s3", "oss", "minio", "csv", "sftp", "fs", "kafka", "mongodb", "sap", "rest_api");

    /** 独立钉值：收口前实测「前端可选且后端承流」的 10 枚（本批 GAUSSDB 命名归一后由 9→10）。 */
    private static final List<String> ACCEPTED_FROZEN = List.of(
            "postgresql", "mysql", "clickhouse", "oracle", "mssql", "dm", "kingbase",
            "gaussdb", "minio", "fs");

    /** 闭集棘轮基线：后端承流面未覆盖的前端选项 9 枚（只减不增；新增即红，收口一枚须同时从本集删除）。 */
    private static final List<String> REJECTED_FROZEN = List.of(
            "doris", "s3", "oss", "csv", "sftp", "kafka", "mongodb", "sap", "rest_api");

    /** 独立钉值：前端 {@code toApiType} 的别名规则面（收口前只有 fs→FILESYSTEM 一条）。 */
    private static final Map<String, String> API_ALIAS_FROZEN = Map.of("fs", "FILESYSTEM");

    private static final Path REPO_ROOT = locateRepoRoot();
    private static final Path TYPES_TS =
            REPO_ROOT.resolve("ecos_frontend/src/pages/data-workbench/types.ts");
    private static final Path BASE_TS =
            REPO_ROOT.resolve("ecos_frontend/src/pages/data-workbench/api/_base.ts");
    private static final Path MAPPERS_TS =
            REPO_ROOT.resolve("ecos_frontend/src/pages/data-workbench/api/mappers.ts");

    // ─── 正向面：前端 19 枚逐枚判定承流／被拒 ──────────────────────────

    @Test
    @DisplayName("前端 CONNECTION_TYPES 19 枚逐枚对账：承流 10 枚不变，被拒 9 枚闭集只减不增")
    void frontendFaceReconciledWithCarrierFace() throws IOException {
        List<String> frontend = parseConnectionTypes(Files.readString(TYPES_TS, StandardCharsets.UTF_8));
        Map<String, String> apiAliases = parseApiTypeAliases(Files.readString(BASE_TS, StandardCharsets.UTF_8));

        assertEquals(FRONTEND_TYPES_FROZEN, frontend,
                "前端枚举面与冻结基线不符（枚数或顺序）—— 解析器读的是真仓面，扩面须同批改基线并登记台账");
        assertEquals(19, frontend.size(), "前端枚举面枚数独立钉值 19（防解析失明＝空面假绿）");
        assertEquals(API_ALIAS_FROZEN, apiAliases, "前端 toApiType 别名规则面与冻结基线不符");

        List<String> problems = crossFaceProblems(frontend, apiAliases);
        assertTrue(problems.isEmpty(), () -> "跨面对账：" + problems);
    }

    @Test
    @DisplayName("D-B4-78 病灶腿：前端 gaussdb→GAUSSDB 现由词表别名承流，不再落被拒面")
    void gaussdbAliasIsNowCarried() {
        assertEquals(DataSourceTypeVocabulary.GAUSS, DataSourceTypeVocabulary.find("GAUSSDB"),
                "同物异名归一：GAUSSDB 必须解析到规范名 GAUSS");
        assertTrue(DataSourceTypeVocabulary.find("GAUSSDB").registrable(),
                "GAUSS 属可登记承流面（命名归一，非准入扩面）");
        assertEquals("GAUSS", DataSourceTypeVocabulary.find("gaussdb").canonical());
    }

    @Test
    @DisplayName("被拒面为闭集：任何新幻影选项都要点名登记，禁静默通过")
    void rejectedFaceIsClosedSet() throws IOException {
        List<String> frontend = parseConnectionTypes(Files.readString(TYPES_TS, StandardCharsets.UTF_8));
        Set<String> rejected = new LinkedHashSet<>(rejectedOf(frontend, API_ALIAS_FROZEN));
        assertEquals(new LinkedHashSet<>(REJECTED_FROZEN), rejected,
                "被拒面必须逐枚等于闭集基线（新增＝前端又列了后端不承流的名；减少＝该枚已承接，须从基线删除并同步台账）");
        // 正向对照：承流判定面非空（10 枚），否则「全被拒＝0 新增」也会假绿
        assertEquals(10, acceptedOf(frontend, API_ALIAS_FROZEN).size(), "承流枚数独立钉值 10");
    }

    // ─── 逆向面：10 枚规范名在前端显示通道 mapDsType 各有归属 ─────────────

    @Test
    @DisplayName("逆向对账：承流面 10 枚规范名在前端 mapDsType 显示分支各有归属且可回环")
    void carrierFaceHasDisplayBranch() throws IOException {
        List<TokenBranch> branches = parseDisplayBranches(Files.readString(MAPPERS_TS, StandardCharsets.UTF_8));
        assertFalse(branches.isEmpty(), "mapDsType 分支未解析到（解析器失明）");
        List<String> problems = displayBranchProblems(DataSourceTypeVocabulary.registrableTypes(), branches);
        assertTrue(problems.isEmpty(), () -> "显示面对账：" + problems);
    }

    // ─── 夹具：证明判据能变红 ───────────────────────────────────────────

    @Test
    @DisplayName("夹具：前端扩一枚幻影／丢一枚承流／别名被删，判据各报具名红")
    void criterionGoesRedOnFixtures() {
        // ① 新增一枚词表外幻影选项
        List<String> withPhantom = new ArrayList<>(FRONTEND_TYPES_FROZEN);
        withPhantom.add("influxdb");
        List<String> p1 = crossFaceProblems(withPhantom, API_ALIAS_FROZEN);
        assertTrue(p1.stream().anyMatch(s -> s.contains("未登记的被拒选项") && s.contains("influxdb")),
                () -> "幻影选项须点名为未登记被拒面，实报 " + p1);

        // ② 丢掉一枚已承流选项（前端静默减面＝承流对账失覆盖，枚数钉值先红）
        List<String> lost = new ArrayList<>(FRONTEND_TYPES_FROZEN);
        lost.remove("gaussdb");
        List<String> p2 = crossFaceProblems(lost, API_ALIAS_FROZEN);
        assertTrue(p2.stream().anyMatch(s -> s.contains("枚数")), () -> "丢面须先报枚数红，实报 " + p2);
        assertTrue(p2.stream().anyMatch(s -> s.contains("gaussdb")), () -> "承流面缺 gaussdb 须点名，实报 " + p2);

        // ③ 别名规则被删（fs→FILESYSTEM）⇒ fs 落回被拒面，具名红
        List<String> p3 = crossFaceProblems(FRONTEND_TYPES_FROZEN, Map.of());
        assertTrue(p3.stream().anyMatch(s -> s.contains("未登记的被拒选项") && s.contains("fs")),
                () -> "实报 " + p3);

        // ④ 显示面丢一枚归属（后端新增可登记类型而前端 mapDsType 未接）
        List<TokenBranch> branches = new ArrayList<>();
        try {
            branches = parseDisplayBranches(Files.readString(MAPPERS_TS, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new AssertionError("读取 mappers.ts 失败", e);
        }
        List<String> p4 = displayBranchProblems(Set.of("POSTGRESQL", "TimescaleNew"), branches);
        assertEquals(1, p4.size(), () -> "新增承流名无显示归属须报 1 条，实报 " + p4);
        assertTrue(p4.get(0).contains("TimescaleNew"), p4.toString());
    }

    // ─── 判据实现（真实面与合成夹具共用）──────────────────────────────

    /** 前端名 → 后端上送名（{@code toApiType}：别名表命中取别名值，否则整名大写）。 */
    private static String toApiType(String frontendType, Map<String, String> aliases) {
        String alias = aliases.get(frontendType.toLowerCase());
        return alias != null ? alias : frontendType.toUpperCase();
    }

    private static List<String> acceptedOf(List<String> frontend, Map<String, String> aliases) {
        List<String> out = new ArrayList<>();
        for (String f : frontend) {
            DataSourceTypeVocabulary v = DataSourceTypeVocabulary.find(toApiType(f, aliases));
            if (v != null && v.registrable()) {
                out.add(f);
            }
        }
        return out;
    }

    private static List<String> rejectedOf(List<String> frontend, Map<String, String> aliases) {
        List<String> out = new ArrayList<>();
        for (String f : frontend) {
            DataSourceTypeVocabulary v = DataSourceTypeVocabulary.find(toApiType(f, aliases));
            if (v == null || !v.registrable()) {
                out.add(f);
            }
        }
        return out;
    }

    /** 跨面判据：枚数＋承流集＋被拒闭集＋别名规则四道，任一不符报具名红。 */
    private static List<String> crossFaceProblems(List<String> frontend, Map<String, String> aliases) {
        List<String> out = new ArrayList<>();
        if (frontend.size() != FRONTEND_TYPES_FROZEN.size()) {
            out.add("前端枚举面枚数 " + frontend.size() + " ≠ 冻结 " + FRONTEND_TYPES_FROZEN.size());
        }
        List<String> accepted = acceptedOf(frontend, aliases);
        if (!ACCEPTED_FROZEN.equals(accepted)) {
            Set<String> missing = new LinkedHashSet<>(ACCEPTED_FROZEN);
            missing.removeAll(accepted);
            Set<String> extra = new LinkedHashSet<>(accepted);
            extra.removeAll(ACCEPTED_FROZEN);
            out.add("承流集与基线不符 缺=" + missing + " 多=" + extra);
        }
        Set<String> rejected = new LinkedHashSet<>(rejectedOf(frontend, aliases));
        Set<String> closed = new LinkedHashSet<>(REJECTED_FROZEN);
        Set<String> newRejected = new LinkedHashSet<>(rejected);
        newRejected.removeAll(closed);
        if (!newRejected.isEmpty()) {
            out.add("出现未登记的被拒选项 " + newRejected
                    + "（前端又列了后端不承流的名：要么后端承流，要么前端下线，禁静默留着让用户撞 400）");
        }
        Set<String> shrunk = new LinkedHashSet<>(closed);
        shrunk.removeAll(rejected);
        if (!shrunk.isEmpty()) {
            out.add("闭集基线内 " + shrunk + " 已不在被拒面 ⇒ 该枚已被承流，须从 REJECTED_FROZEN 删除并同步台账");
        }
        return out;
    }

    /** 显示面判据：每个规范名在 mapDsType 里必须落到一个前端合法类型名，且回环到同一规范名。 */
    private static List<String> displayBranchProblems(Set<String> canonicalTypes, List<TokenBranch> branches) {
        List<String> out = new ArrayList<>();
        for (String canonical : canonicalTypes) {
            String incoming = canonical.toLowerCase();
            String target = null;
            for (TokenBranch b : branches) {
                if (incoming.contains(b.token())) {
                    target = b.target();
                    break;
                }
            }
            if (target == null) {
                out.add(canonical + " 在前端 mapDsType 无显示分支（新增承流名须同批改前端，禁靠默认回落）");
                continue;
            }
            DataSourceTypeVocabulary back = DataSourceTypeVocabulary.find(
                    toApiType(target, API_ALIAS_FROZEN));
            if (back == null || !back.canonical().equals(canonical)) {
                out.add(canonical + " 的显示分支落到 " + target
                        + "，回环解析为 " + (back == null ? "词表外" : back.canonical()) + "（三面不同名）");
            }
        }
        return out;
    }

    // ─── 仓面解析 ───────────────────────────────────────────────────────

    /** 解析 {@code CONNECTION_TYPES} 数组块内的 {@code value: 'x'}（按声明序）。 */
    private static List<String> parseConnectionTypes(String typesTs) {
        int start = typesTs.indexOf("export const CONNECTION_TYPES");
        if (start < 0) {
            throw new IllegalStateException("types.ts 里找不到 CONNECTION_TYPES 声明（前端面改名＝本门失明）");
        }
        int end = typesTs.indexOf("];", start);
        if (end < 0) {
            throw new IllegalStateException("CONNECTION_TYPES 数组块未闭合");
        }
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("value:\\s*'([A-Za-z0-9_]+)'").matcher(typesTs.substring(start, end));
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /** 解析 {@code API_TYPE_ALIASES} 对象内的 {@code key: 'VALUE'} 规则。 */
    private static Map<String, String> parseApiTypeAliases(String baseTs) {
        int start = baseTs.indexOf("API_TYPE_ALIASES");
        if (start < 0) {
            throw new IllegalStateException("_base.ts 里找不到 API_TYPE_ALIASES（前端归一规则改名＝本门失明）");
        }
        int end = baseTs.indexOf("};", start);
        Map<String, String> out = new LinkedHashMap<>();
        if (end < 0) {
            return out;
        }
        Matcher m = Pattern.compile("([A-Za-z0-9_]+):\\s*'([A-Za-z0-9_]+)'")
                .matcher(baseTs.substring(start, end));
        while (m.find()) {
            out.put(m.group(1), m.group(2));
        }
        return out;
    }

    /** 解析 {@code mapDsType} 的 {@code includes('token')}→{@code return 'target'} 分支（按出现序，逐行取）。 */
    private static List<TokenBranch> parseDisplayBranches(String mappersTs) {
        int start = mappersTs.indexOf("export function mapDsType");
        if (start < 0) {
            throw new IllegalStateException("mappers.ts 里找不到 mapDsType（前端显示通道改名＝本门失明）");
        }
        int end = mappersTs.indexOf("\n}", start);
        String block = mappersTs.substring(start, end < 0 ? mappersTs.length() : end);
        List<TokenBranch> out = new ArrayList<>();
        Pattern condPattern = Pattern.compile("if\\s*\\(\\s*(.*?)\\s*\\)\\s*return\\s+'([A-Za-z0-9_]+)'\\s*;");
        for (String rawLine : block.split("\n")) {
            String line = rawLine.trim();
            if (!line.startsWith("if")) {
                continue;
            }
            Matcher m = condPattern.matcher(line);
            if (!m.matches()) {
                throw new IllegalStateException("mapDsType 出现无法归类的分支行（判据失明即红）：" + line);
            }
            Matcher tok = Pattern.compile("includes\\('([A-Za-z0-9_]+)'\\)").matcher(m.group(1));
            boolean any = false;
            while (tok.find()) {
                out.add(new TokenBranch(tok.group(1), m.group(2)));
                any = true;
            }
            if (!any) {
                throw new IllegalStateException("mapDsType 分支行无 includes('x') 条件（判据失明即红）：" + line);
            }
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("mapDsType 分支解析为 0 条（判据失明）");
        }
        return out;
    }

    /** mapDsType 的一条显示分支：命中 token 即归到 target 前端类型名。 */
    private record TokenBranch(String token, String target) {
    }

    private static Path locateRepoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 12 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("ecos_frontend"))
                    && Files.isDirectory(dir.resolve("ecos_backend"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "无法定位仓库根：自 user.dir 向上 12 级未找到同时含 ecos_frontend 与 ecos_backend 的目录");
    }
}
