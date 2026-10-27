package com.chinacreator.gzcm.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #140 门禁补实现批 · 子批 E-1 —— 迁移版本号全局唯一门禁（差距文档 §9.7.1/§15.3 具名类补齐）。
 *
 * <p><b>差距来源</b>：{@code docs/20-需求/ECOS详细设计从零实施版-实现差距分析-2026-10-14.md}
 * §9.7.1（篇8 §8.4.4 行）与 §十五 登记「`MigrationVersionUniquenessTest` 按名 find 零命中」，
 * 并核实既有载体 {@code MigrationSingleSourceTest} <b>只管辖 AI 新增段 V200~V204</b>，
 * 其类注释原文即写「存量 V50/V150/V155 重复版本属 R-22 裁决项，不在本域断言面」
 * ⇒ 单源目录的<b>全域</b>版本唯一面无人守护，正是"域内局部覆盖冒充全域不变量"的假绿形态。
 * 按 R9① 裁决（缺的门禁＝补代码实现）与 R9②（禁从清单删除、禁先写空转测试），本类是该门禁的实现。</p>
 *
 * <p><b>为什么值得单独一枚（裁决 D-#140E-1：补独立类，不改名归并）</b>：
 * Flyway 当前禁用（{@code spring.flyway.enabled: false}），同版本号多文件在运行期不报错，
 * 但（i）一旦启用即确定性崩溃；（ii）版本号是 DDL 落地登记的<b>主键</b>，重复=台账不可寻址；
 * （iii）既有 3 组重复中仅 V155 一组被《数据库访问规范》v1.3 §三「历史违例登记」点名，
 * V50 与 V150 两组<b>从未登记</b>＝本批实测新增发现。</p>
 *
 * <p><b>判定面</b>：仅文件名（不读 SQL 正文，无 DB 依赖）——
 * ① 命名文法：全部 {@code .sql} 必须匹配 Flyway 版本化迁移命名
 * {@code V<version>__<desc>.sql}（version=数字段以 {@code .} 或 {@code _} 分隔）；
 * ② 规范化唯一：{@code _}≡{@code .}（Flyway 官方口径）后版本键全局唯一，
 * 存量重复以 {@link #REGISTERED_DUPLICATE_VERSIONS} 登记式钉死（逐组带文件名与归因），
 * 新增重复即红，重复消失也必须同步收缩本名录（防豁免腐化成后门）；
 * ③ 版本族名录：点号/下划线扩展版本（{@code V4.1}、{@code V25_1}…）逐枚钉死，
 * 防止后来者用 {@code V288.1} 型小版本号绕过「新增脚本须顺延登记」的台账纪律；
 * ④ 台账锚：单源目录内最大基线版本 {@code = 294}（下一枚 {@code V295}），
 * 且脚本总数只增不减（下限 244）。</p>
 *
 * <p><b>反假绿四锁</b>：①扫描面非空 + 解析成功率 100%（解析不了的文件名直接进失败消息）；
 * ②重复检测的<b>反向对照</b>=解析器必须真的同时看见 V155 与 V50 的两枚实文件
 * （看不到已知重复＝分组逻辑退化，此时"零新增重复"是空转出来的绿）；
 * ③登记名录双向相等，且比的是<b>整组（键 + 组内成员清单）</b>而非仅键集
 * ——探针实测暴露了"只比键集"的漏检形态（往已重复的 V155 上再加第三枚文件时键集不变 ⇒ 恒绿），
 * 已按该凭证加强；④变异探针凭证（4 次，逐条原文见差距文档 §修订记录 v1.0.11）：
 * 注入 {@code V155__probe_dup.sql} ⇒ 2 例红（主判据 + 自检）；
 * 注入 {@code V281__probe_dup.sql}（落在原本唯一的版本键上）⇒ 主判据红；
 * 注入 {@code V288x__probe_badname.sql} ⇒ 5 例全红（命名文法）；
 * 注入 {@code V287.1__probe_family.sql} ⇒ 版本族名录红。探针全部用完即删，删后复跑 5/5 绿。</p>
 *
 * <p>与既有门禁的分工（不重复、不串语义）：{@code MigrationSingleSourceTest}=AI 域新增段的
 * 「无第二 DDL 源 + 段内版本唯一」；{@code db-migration-lint.ps1}=列形态与 schema 面；
 * 本类只管<b>版本键</b>。三者的存量基线各自单源，禁止互相引用计数。</p>
 *
 * <p>验收：{@code mvn -o test -pl gateway -Dtest='MigrationVersionUniquenessTest'}
 * （从 {@code ecos_backend/} 执行）。</p>
 */
@DisplayName("#140/E-1：迁移版本号全局唯一门禁（命名文法·规范化唯一·版本族名录·台账锚）")
class MigrationVersionUniquenessTest {

    private static final String MIGRATION_REL = "docs/50-db_script/02-flyway-migration/gateway";

    /** Flyway 版本化迁移命名文法（描述段禁空、禁再含连续下划线）。 */
    private static final Pattern LEGAL_NAME =
            Pattern.compile("^V(\\d+(?:[._]\\d+)*)__([^_][^/]*?)\\.sql$");

    /** 锁①：单源脚本总数下限（实测 244，只增不减；上限不钉，避免正常加脚本要改门禁）。
     *  2026-10-27 #141/B3 认知治理批：V291（3e-89 属主密级字典）+ V292（R4 Mind 聚合根）落地
     *  ⇒ 按实测订正 240→242（与 DrBaseline/Mc02/Mc03 同批改）。
     *  2026-10-27 #142/B4 #146i：V294（fc_* 三表＋演示主体 seed）落地。
     *  勘误：V293（#146f td_audit_log 注册）本批锚定时漏数（已入库既有脚本，非本批产出）⇒
     *  初值 243 系漏数，按实测订正 243→244（= V293 存量 1 枚 + V294 本批 1 枚，242 基线之上 2 枚）。 */
    private static final int SCRIPT_COUNT_FLOOR = 244;

    /**
     * 锁④：台账锚——基线最大版本与下一号（新增脚本必须用 {@code > } 本值的主版本号）。
     * 2026-10-16 二期 W2：V289（td_user IAM 读面补列）落地 ⇒ 287→289 / 下一号 288→290。
     * 2026-10-17 二期 W5（a982d713）：V290（kb 导航面代码契约列）落地，但该批只落脚本、未随批改本锚
     * ⇒ 本类两例红（登记册与实码分叉的实证）。按实测订正 289→290 / 下一号 290→291。
     * 2026-10-27 #141/B3 认知治理批：V291（#142/B4-3e-89 已 live 实跑）+ V292（R4 Mind 聚合根，
     * 仅落单源文件未实跑＝授权门控）落地 ⇒ 按实测订正 290→292 / 下一号 291→293。
     * 2026-10-27 #142/B4 #146i：本批新增 V294（fc_* 三表＋演示主体 seed，已 dev 实跑）。
     * V293（#146f td_audit_log 注册，已入库）＋ V294 使单源最大版本达 294 ⇒
     * 按实测订正 292→294 / 下一号 293→295。V288 仍空置：《数据库访问规范》§十 预留给 #144-c，不占。
     */
    private static final int MAX_BASELINE_VERSION = 294;
    private static final int NEXT_FREE_VERSION = 295;

    /**
     * 登记式历史违例（裁决 D-#140E-2：存量重复<b>不返工改名</b>，只钉死不扩散）。
     * 键=规范化版本键，值=该版本键下的实文件名（按字典序），逐组带归因。
     * 依据：IR03「只加不删」+《数据库访问规范》§三 处置口径「依 IR03 不做 DROP/ALTER 回改 remediation」；
     * V155 组已由规范 v1.3 §三 登记，V50/V150 两组由本批实测补登记（此前零处记载）。
     */
    private static final Map<String, List<String>> REGISTERED_DUPLICATE_VERSIONS = Map.of(
            "50", List.of("V50__ecos_agent_runtime.sql", "V50__ecos_domain_tenant_id.sql"),
            "150", List.of("V150__ecos_entity_table_mapping_doc_anchor_json.sql",
                    "V150__ecos_scenario_sandbox_layout.sql"),
            "155", List.of("V155__kb_nav_category.sql", "V155__lineage_node_pipeline_task_id.sql"));

    /**
     * 版本族名录（点号/下划线扩展版本，逐枚钉死 + 方言标注）。
     * 实测 14 枚；其中唯一用下划线作版本分隔符的是 {@code V25_1__…}（其余 13 枚用点号）
     * ⇒ 本名录同时钉住「方言不统一」这一存量事实，新增第二枚下划线版本即红（须先归一方言）。
     */
    private static final Set<String> PINNED_VERSION_FAMILY_FILES = Set.of(
            "V1.1__S1_CORE03_workflow_tables.sql",
            "V4.1__ontology_proposals.sql",
            "V4.2__function_audit.sql",
            "V4.3__ecos_ontology_optimistic_lock.sql",
            "V4.4__ecos_ontology_rls.sql",
            "V25_1__ecos_pipeline_node_fix.sql",
            "V164.1__datasource_credential_split.sql",
            "V165.1__dq_score_asset_repair.sql",
            "V167.1__ecos_caliber_tables.sql",
            "V168.1__metric_definition_conform.sql",
            "V169.1__ecos_ontology_audit_columns.sql",
            "V170.1__ecos_ontology_version_snapshot_text.sql",
            "V170.2__ecos_ontology_git_archive_pending.sql",
            "V171.1__function_audit_caller_backfill.sql");

    /** 单个脚本文件的版本解析结果。 */
    private record ScriptEntry(String fileName, String rawVersion, String normalizedVersion,
                               int majorVersion) {
    }

    // ────────────────────────────────────────────────────────────
    // ① 命名文法：全部 .sql 必须是可解析的 Flyway 版本化迁移名
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("命名文法：单源目录内每个 .sql 都能解析出版本键（解析不了=台账不可寻址，直接 FAIL）")
    void everyScriptNameIsParsableAndCountIsBaselined() throws IOException {
        List<String> names = scriptNames();
        assertTrue(names.size() >= SCRIPT_COUNT_FLOOR,
                "单源脚本数=" + names.size() + " 低于基线下限 " + SCRIPT_COUNT_FLOOR
                        + "（DDL 单源目录被搬空/批量改名 ⇒ 本门禁的扫描面会退化，见铁律 v2.2 §3.1）");

        List<String> illegal = new ArrayList<>();
        for (String n : names) {
            if (!LEGAL_NAME.matcher(n).matches()) illegal.add(n);
        }
        assertTrue(illegal.isEmpty(), "存在不符合 Flyway 版本化命名文法的脚本（V<version>__<desc>.sql）：\n"
                + String.join("\n", illegal));
    }

    // ────────────────────────────────────────────────────────────
    // ② 扫描器自检（锁②：反向对照——必须真的看见已知重复）
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("解析器自检：必须真的同时看见 V155 与 V50 两组重复、14 枚版本族文件、最大基线号 290")
    void scannerActuallySeesTheKnownShapes() throws IOException {
        List<ScriptEntry> entries = parseAll();
        Map<String, List<ScriptEntry>> groups = groupByVersion(entries);

        for (Map.Entry<String, List<String>> known : REGISTERED_DUPLICATE_VERSIONS.entrySet()) {
            List<ScriptEntry> g = groups.get(known.getKey());
            assertNotNull(g, "解析器看不到已知重复版本键 V" + known.getKey() + " ⇒ 分组逻辑退化");
            assertEquals(known.getValue(), sortedFileNames(g),
                    "版本键 V" + known.getKey() + " 的实文件清单与登记名录不一致（须先登记才可变更）");
        }

        Set<String> families = new TreeSet<>();
        for (ScriptEntry e : entries) {
            if (e.rawVersion().contains(".") || e.rawVersion().contains("_")) families.add(e.fileName());
        }
        assertEquals(PINNED_VERSION_FAMILY_FILES, families,
                "版本族（点号/下划线扩展版本）名录与实测不一致：新增此类小版本号须先登记，"
                        + "否则可用 V" + NEXT_FREE_VERSION + ".1 型版本号绕过顺延台账");

        int max = entries.stream().mapToInt(ScriptEntry::majorVersion).max().orElse(-1);
        assertEquals(MAX_BASELINE_VERSION, max,
                "单源最大基线版本号漂移（钉死 " + MAX_BASELINE_VERSION + "，下一枚应为 V"
                        + NEXT_FREE_VERSION + "）⇒ 台账锚失效，登记册与实码开始分叉");
    }

    // ────────────────────────────────────────────────────────────
    // ③ 规范化唯一（主判据）
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("版本键规范化（下划线≡点号，Flyway 口径）后全局唯一；存量重复 3 组已登记，不得新增")
    void noNewDuplicateNormalizedVersionKeys() throws IOException {
        Map<String, List<ScriptEntry>> groups = groupByVersion(parseAll());

        Map<String, List<String>> actualDupGroups = new TreeMap<>();
        List<String> duplicates = new ArrayList<>();
        for (Map.Entry<String, List<ScriptEntry>> g : groups.entrySet()) {
            if (g.getValue().size() > 1) {
                actualDupGroups.put(g.getKey(), sortedFileNames(g.getValue()));
                duplicates.add("V" + g.getKey() + " -> " + sortedFileNames(g.getValue()));
            }
        }
        Map<String, List<String>> registered = new TreeMap<>();
        for (Map.Entry<String, List<String>> r : REGISTERED_DUPLICATE_VERSIONS.entrySet()) {
            registered.put(r.getKey(), sortedOf(r.getValue()));
        }

        // 锁③：整组双向相等（键集 + 组内成员清单）——比"键集相等"更强：
        // 探针实测证明只比键集时，往已重复的 V155 上再加第三枚文件不会变红（键集不变＝漏检），
        // 故必须连成员清单一比一：新增碰撞（无论落在唯一键还是已重复键上）红，重复消失也必须同步收缩本名录。
        assertEquals(registered, actualDupGroups,
                "重复版本组（键+成员清单）与登记名录不一致。实测重复组：\n" + String.join("\n", duplicates)
                        + "\n（Flyway 启用后同版本键多文件即确定性崩溃；存量 3 组属裁决 D-#140E-2 不返工）");

        // 同版本键的重复必须"文件名可区分且描述段非空"，防止出现 V155__a.sql 与 V155__a.sql 型真冲突
        for (Map.Entry<String, List<ScriptEntry>> g : groups.entrySet()) {
            Set<String> distinct = new TreeSet<>(g.getValue().stream().map(ScriptEntry::fileName).toList());
            assertEquals(g.getValue().size(), distinct.size(),
                    "版本键 V" + g.getKey() + " 出现同名重复条目（目录内文件名冲突）");
            for (ScriptEntry e : g.getValue()) {
                Matcher m = LEGAL_NAME.matcher(e.fileName());
                assertTrue(m.matches() && !m.group(2).isBlank(),
                        e.fileName() + " 描述段为空或不可解析（Flyway 会拒绝该脚本）");
            }
        }
    }

    @Test
    @DisplayName("下划线版本方言唯一：全源只允许钉死的 1 枚 V25_1 型下划线分隔版本（混用二义即 FAIL）")
    void underscoreVersionDialectIsNotSpreading() throws IOException {
        List<String> underscoreFiles = new ArrayList<>();
        for (ScriptEntry e : parseAll()) {
            if (e.rawVersion().contains("_")) underscoreFiles.add(e.fileName());
        }
        assertEquals(List.of("V25_1__ecos_pipeline_node_fix.sql"), sortedOf(underscoreFiles),
                "点号与下划线两种版本方言并存会扩大（V25_1≡V25.1 的规范化歧义面随之内扩大）；"
                        + "新增扩展版本一律用点号，或先做存量归一并登记");
    }

    // ────────────────────────────────────────────────────────────
    // ④ 台账锚：新增主版本号必须顺延，不得插入既有区间
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("台账锚：主版本号连续性与下一号（实测最大 290 ⇒ 新脚本自 291 顺延，禁回填占号；V288 预留未占）")
    void baselineSequenceIsAddressableAndNextSlotIsKnown() throws IOException {
        Set<Integer> majors = new TreeSet<>(parseAll().stream()
                .map(ScriptEntry::majorVersion).toList());
        assertTrue(majors.contains(MAX_BASELINE_VERSION),
                "实测最大主版本号缺失 " + MAX_BASELINE_VERSION);
        assertTrue(!majors.contains(NEXT_FREE_VERSION),
                "V" + NEXT_FREE_VERSION + " 已被占用但门禁基线未同步（登记册 §修订记录须同批改本值）");
        // 主版本号集合非空且 >= 200 枚（V1~V290 区间内存量分布本就稀疏，不虚构连续性）
        assertTrue(majors.size() >= 200, "可解析主版本数=" + majors.size() + "（异常骤降=解析面退化）");
    }

    // ────────────────────────────────────────────────────────────
    // 判定与工具
    // ────────────────────────────────────────────────────────────

    private static List<ScriptEntry> parseAll() throws IOException {
        List<ScriptEntry> out = new ArrayList<>();
        for (String name : scriptNames()) {
            Matcher m = LEGAL_NAME.matcher(name);
            assertTrue(m.matches(), "命名文法解析失败: " + name);
            String raw = m.group(1);
            String normalized = raw.replace('_', '.');
            int major = Integer.parseInt(normalized.split("\\.")[0]);
            out.add(new ScriptEntry(name, raw, normalized, major));
        }
        assertTrue(out.size() >= SCRIPT_COUNT_FLOOR, "解析结果数小于基线下限（扫描面空转）");
        return out;
    }

    private static Map<String, List<ScriptEntry>> groupByVersion(List<ScriptEntry> entries) {
        Map<String, List<ScriptEntry>> out = new LinkedHashMap<>();
        for (ScriptEntry e : entries) {
            out.computeIfAbsent(e.normalizedVersion(), k -> new ArrayList<>()).add(e);
        }
        return out;
    }

    private static List<String> sortedFileNames(List<ScriptEntry> entries) {
        return sortedOf(entries.stream().map(ScriptEntry::fileName).toList());
    }

    private static List<String> sortedOf(List<String> in) {
        return in.stream().sorted(Comparator.<String>naturalOrder()).toList();
    }

    private static List<String> scriptNames() throws IOException {
        Path dir = repoRoot().resolve(MIGRATION_REL);
        assertTrue(Files.isDirectory(dir), "缺迁移 DDL 单源目录（铁律 v2.2 §3.1 权威单源）: " + dir);
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .map(p -> p.getFileName().toString())
                    .sorted(Comparator.<String>naturalOrder())
                    .toList();
        }
    }

    /** surefire 在 gateway 模块目录执行 ⇒ 向上找仓库根（含 ecos_backend/ 与 docs/）。 */
    private static Path repoRoot() {
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
}
