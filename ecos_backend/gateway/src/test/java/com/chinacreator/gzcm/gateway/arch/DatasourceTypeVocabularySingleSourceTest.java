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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * <p><b>同批已做（#142/B4-3e-64 追加的对账腿，见下）</b>：① 承流面补 {@code supported-types} 出口
 * 并钉住它挂在 {@code /api/v1/datanet/datasource}（而非 F02-01 判定的兼容别名面）；② 前端弹窗改消费
 * 该出口，显示元数据面 {@code CONNECTION_TYPES} 不再是可选面；③ 显示回落 {@code mapDsType} 末腿由
 * {@code 'postgresql'} 改显式 {@code 'unknown'}（D-B4-93），且 {@code unknown} 不入选项面；
 * ④ 反方向对账——准入面每一枚都必须在前端显示面有归属（{@code unresolvedCanonical} 恒空），
 * 与正向「前端可选⇒后端承流」成对，堵住「后端扩一枚而前端下拉悄悄少一枚」。</p>
 *
 * <p><b>同批已做（#142/B4-3e-65 追加）</b>：⑤ 第三处显示通道归一——{@code helpers.ts#getSourceTypeLabel}
 * 的 5 枚私有说明表已删除，类型说明改由 {@code CONNECTION_TYPES.descKey} 承载（19 枚逐枚在位），
 * 由前端门 {@code b4ConnTypeDescChannel.spec.ts} 持有面值与行为对账，本门不重复钉（避免第二属主）。</p>
 *
 * <p><b>同批已做（#142/B4-3e-66 追加）</b>：⑥ 第四处显示通道归一——{@code helpers.ts#getSourceIcon}
 * 的 5 枚私有图标表已删除（{@code default} 曾把其余 14 枚已知类型一律折成 {@code fs} 的那枚图标），
 * 图标名改由 {@code CONNECTION_TYPES.iconName} 承载（19 枚逐枚在位且两两不重复），
 * 未解析读数表外单点持有；面值、{@code LucideIcon.tsx} 的 {@code iconMap} 桥接面与
 * {@code lucide-react} 导出面三侧对账由前端门 {@code b4ConnTypeIconChannel.spec.ts} 持有，
 * 本门不重复钉（同上，避免第二属主）。</p>
 *
 * <p><b>同批已做（#142/B4-3e-67 追加）</b>：⑦ 第四条通道——历史行标签通道 {@code mapDsType} 的
 * 18 条 {@code includes} 子串猜测链改 {@code mappers.ts#HISTORICAL_TYPE_ALIASES} 表驱动
 * （差距 <b>D-B4-96</b>：短子串把 {@code hdfs}／{@code lossless}／{@code OpenAPI} 折成具体类型、
 * 书写顺序把 {@code GaussDB(for MySQL)} 裁决成 mysql、{@code oss} 一枚被 s3 腿吞掉永不可达）。
 * 本门随之把<b>逆向对账的读取面</b>从「{@code mapDsType} 分支」迁到「别名表」——
 * 标签通道的行为属主是前端门 {@code b4MapDsTypeAliasTable.spec.ts}，本门只钉规范名回环；
 * 两份实现（Java {@link #resolveByAliasTable} 与前端 {@code resolveHistoricalType}）的
 * <b>整词门槛跨语言同值</b>由 {@link #thresholdIsSameOnBothSides()} 读前端字面量对账，
 * 数值分叉会让回环与真实显示读数各自恒绿。</p>
 *
 * <p><b>在册余项（台账 v1.0.149）</b>：{@code doris} 是否准入；被拒闭集 9 枚——{@code doris}/{@code s3}/
 * {@code oss}/{@code csv}/{@code sftp}/{@code kafka}/{@code mongodb}/{@code sap}/{@code rest_api}——的逐枚定档
 * （下线或接承流）：#142/B4-3e-64 把它们从<b>可选面</b>裁掉，但 19 枚显示元数据面仍在（历史连接记录要出名字），
 * 下线须另批处置；名称／说明／图标／历史标签四条显示通道至此均已归一（前三条到同一份显示元数据面，
 * 第四条到 {@code HISTORICAL_TYPE_ALIASES} 表）；另一枚候选面判定于台账 v1.0.148、此处沿用：
 * {@code Modals.tsx} 里以字符串出现的图标名
 * 实测 9 处站点／7 枚具名——弹窗标题装饰（{@code Database}、{@code Import}）、关闭钮（{@code X}×2）、
 * 加载态（{@code Loader2}）、失败态与测试结果三元式（{@code XCircle}×2、{@code CheckCircle}）、
 * 告警态（{@code AlertTriangle}×2）——<b>均为状态/装饰通道，不由数据源类型驱动</b>，
 * 与本族「类型→显示读数折叠」病灶不同形，不入单源表。</p>
 */
class DatasourceTypeVocabularySingleSourceTest {

    /** 独立钉值：前端 {@code CONNECTION_TYPES} 实列 19 枚（声明序）。 */
    private static final List<String> FRONTEND_TYPES_FROZEN = List.of(
            "postgresql", "mysql", "doris", "clickhouse", "oracle", "mssql", "dm", "kingbase",
            "gaussdb", "s3", "oss", "minio", "csv", "sftp", "fs", "kafka", "mongodb", "sap", "rest_api");

    /**
     * 独立钉值：实测「前端可选且后端承流」的 11 枚。
     * #142/B4-3e-71（B4 被拒闭集 9 枚逐枚定档）起：{@code doris} 由 REJECTED_FROZEN 剪出、
     * 加入本集——后端承流（{@code SqlConsoleRegressionDorisTest}＋{@code JdbcAdapterFactory}
     * DorisAdapter 注册）与前端承流（{@code CONNECTION_TYPES} 已列、三侧 i18n 齐备、
     * {@code feHost}／{@code feHttpPort} 字段备用）均在位，草案的
     * {@code registrable=false} 属「候选未裁」而非「应拒」。
     */
    private static final List<String> ACCEPTED_FROZEN = List.of(
            "postgresql", "mysql", "doris", "clickhouse", "oracle", "mssql", "dm", "kingbase",
            "gaussdb", "minio", "fs");
    /**
     * 闭集棘轮基线：后端承流面未覆盖的前端选项 <b>8 枚</b>（只减不增；新增即红，收口一枚须同时
     * 从本集删除并同步台账 v1.0.153）。#142/B4-3e-71 剪 doris（判「接承流」）后余 8 枚——
     * {@code s3}/{@code oss}（对象存储语义，MINIO 独立枚已承接，本批不下线）／
     * {@code csv}/{@code rest_api}（属 pipeline 规范类型 SOURCE_CSV／SOURCE_REST 的
     * business 面永远禁止——pipeline whitelist 与业务类型是两枚不同档，见词表
     * {@code pipelineWhitelist()}）／{@code sftp}/{@code kafka}/{@code mongodb}/{@code sap}
     * （无对应后端承流：mongodb 在词表但 {@code registrable=false}、sftp/kafka/sap 词表外不属任何登记）。
     */
    private static final List<String> REJECTED_FROZEN = List.of(
            "s3", "oss", "csv", "sftp", "kafka", "mongodb", "sap", "rest_api");

    /** 独立钉值：前端 {@code toApiType} 的别名规则面（收口前只有 fs→FILESYSTEM 一条）。 */
    private static final Map<String, String> API_ALIAS_FROZEN = Map.of("fs", "FILESYSTEM");

    /**
     * 跨语言同构门槛（#142/B4-3e-67）：alias 短于此值只做<b>整词</b>命中，禁子串包含。
     *
     * <p>本门与前端 {@code mappers.ts#WHOLE_WORD_ONLY_BELOW} 是两份实现（Java 侧只为逆向对账
     * 而存在，行为属主仍是前端），<b>数值不符会让回环判定与真实显示读数分叉</b>而两边各自恒绿——
     * 由 {@link #thresholdIsSameOnBothSides()} 逐字比对前端字面量，改一处必同批改另一处。</p>
     */
    private static final int CONTAIN_MIN_LEN_FROZEN = 5;

    private static final Path REPO_ROOT = locateRepoRoot();
    private static final Path TYPES_TS =
            REPO_ROOT.resolve("ecos_frontend/src/pages/data-workbench/types.ts");
    private static final Path BASE_TS =
            REPO_ROOT.resolve("ecos_frontend/src/pages/data-workbench/api/_base.ts");
    private static final Path MAPPERS_TS =
            REPO_ROOT.resolve("ecos_frontend/src/pages/data-workbench/api/mappers.ts");
    /** #142/B4-3e-64：新增三条面——承流出口本体、目录消费方弹窗、前端通道文件。 */
    private static final Path CARRIER_CONTROLLER =
            REPO_ROOT.resolve("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/data/controller/DataSourceController.java");
    private static final Path MODALS_TSX =
            REPO_ROOT.resolve("ecos_frontend/src/pages/data-workbench/Modals.tsx");
    private static final Path DATASOURCE_API_TS =
            REPO_ROOT.resolve("ecos_frontend/src/pages/data-workbench/api/datasource.ts");
    private static final Path PMO45_CONTROLLER =
            REPO_ROOT.resolve("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/data/controller/PMO45DataSourceController.java");

    // ─── 正向面：前端 19 枚逐枚判定承流／被拒 ──────────────────────────

    @Test
    @DisplayName("前端 CONNECTION_TYPES 19 枚逐枚对账：承流 11 枚（含 doris）、被拒 8 枚闭集只减不增")
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
        // 正向对照：承流判定面非空（11 枚），否则「全被拒＝0 新增」也会假绿
        assertEquals(ACCEPTED_FROZEN.size(), acceptedOf(frontend, API_ALIAS_FROZEN).size(),
                "承流枚数独立钉值 " + ACCEPTED_FROZEN.size());
    }

    // ─── 逆向面：11 枚规范名在前端标签通道各有归属 ─────────────────────

    @Test
    @DisplayName("逆向对账：承流面 11 枚规范名（#142/B4-3e-71 起含 DORIS）在前端别名表各有归属且可回环")
    void carrierFaceHasDisplayBranch() throws IOException {
        List<AliasRow> rows = parseHistoricalAliasTable(Files.readString(MAPPERS_TS, StandardCharsets.UTF_8));
        assertFalse(rows.isEmpty(), "HISTORICAL_TYPE_ALIASES 未解析到（解析器失明）");
        List<String> problems = displayAliasProblems(DataSourceTypeVocabulary.registrableTypes(), rows);
        assertTrue(problems.isEmpty(), () -> "显示面对账：" + problems);
    }

    /**
     * 跨语言门槛对账（#142/B4-3e-67）：本门 {@code resolveByAliasTable} 与前端
     * {@code resolveHistoricalType} 是两份实现，只有<b>整词/包含门槛同值</b>时逆向对账才不失明。
     *
     * <p>判据读前端真仓面的字面量（改名或删常量即红），并把门槛本身跑进回环：把
     * {@code POSTGRESQL} 的规范名换成短名形态 {@code dm} 与 {@code s3} 逐枚验证——
     * 门槛一旦被放宽到 1，{@code s3} 会在 {@code lossless} 这类串上假命中，本腿的
     * 反例夹具（{@link #thresholdCriterionGoesRedOnFixtures()}）即红。</p>
     */
    @Test
    @DisplayName("跨语言同构：前端 WHOLE_WORD_ONLY_BELOW 字面量与本门门槛同值 5")
    void thresholdIsSameOnBothSides() throws IOException {
        String mappers = Files.readString(MAPPERS_TS, StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("const\\s+WHOLE_WORD_ONLY_BELOW\\s*=\\s*(\\d+)\\s*;").matcher(mappers);
        assertTrue(m.find(), "mappers.ts 里读不到 WHOLE_WORD_ONLY_BELOW 字面量（前端门槛改名/内联＝跨语言对账失明）");
        assertEquals(CONTAIN_MIN_LEN_FROZEN, Integer.parseInt(m.group(1)),
                "前端整词门槛与本门 CONTAIN_MIN_LEN_FROZEN 不符 ⇒ 逆向回环与真实显示读数分叉，两边各自恒绿");

        List<AliasRow> rows = parseHistoricalAliasTable(mappers);
        assertEquals("dm", resolveByAliasTable("dm", rows), "短 alias 靠整词命中仍可归位（门槛不是禁短名）");
        assertEquals("s3", resolveByAliasTable("S3", rows), "同上：'S3' 整词命中 s3");
        assertNull(resolveByAliasTable("lossless", rows),
                "lossless 解析出值＝短 alias 子串猜测回潮（D-B4-96 复发）");
        assertNull(resolveByAliasTable("hdfs", rows),
                "hdfs 解析出值＝短 alias 子串猜测回潮（D-B4-96 复发）");
    }

    @Test
    @DisplayName("夹具：门槛形同虚设（短 alias 允许子串）时，同构判据具名红")
    void thresholdCriterionGoesRedOnFixtures() {
        // 反例夹具：给 s3 的 alias 面塞一枚 2 字符 'ss' —— 门槛 5 下它只能整词命中；
        // 门槛若被降到 1，'lossless' 会被折成 s3，本腿即红（堵住「门槛常量改了但没人管」）。
        List<AliasRow> poisoned = List.of(new AliasRow("s3", List.of("s3", "amazons3", "ss")));
        assertNull(resolveByAliasTable("lossless", poisoned),
                "门槛 5 之下 'ss' 只能整词命中；能解析出值即门槛失效");
        assertEquals("s3", resolveByAliasTable("ss", poisoned), "整词 'ss' 命中＝门槛未禁短名本身");

        List<String> problems = displayAliasProblems(Set.of("GAUSS"),
                List.of(new AliasRow("minio", List.of("minio"))));
        assertEquals(1, problems.size(), () -> "规范名 GAUSS 无显示归属须报 1 条，实报 " + problems);
        assertTrue(problems.get(0).contains("GAUSS"), problems.toString());
    }

    // ─── 承流出口面 ＋ 前端消费面（#142/B4-3e-64）───────────────────────

    @Test
    @DisplayName("承流面已导出 supported-types 出口，且未把新出口挂到兼容别名面")
    void carrierFaceExposesCatalog() throws IOException {
        String carrier = Files.readString(CARRIER_CONTROLLER, StandardCharsets.UTF_8);
        assertTrue(carrier.contains("@GetMapping(\"/supported-types\")"),
                "唯一承流面须自带类型目录出口——前端绑别名面等于新增一条「将被删除的依赖面」");
        assertTrue(carrier.contains("\"/api/v1/datanet/datasource\""),
                "出口须挂在 /api/v1/datanet/datasource 族根下");
        String classMapping = carrier.substring(0, carrier.indexOf("public class DataSourceController"));
        assertFalse(classMapping.contains("\"/api/v1/datasource\""),
                "承流面的类级映射不得吞下别名面族根（两族由两个 Controller 分持，F02-01 口径）");
        // 别名面自身保持原样（API 只增不改；本批不裁它，删除另批处置）
        assertTrue(Files.readString(PMO45_CONTROLLER, StandardCharsets.UTF_8)
                        .contains("@GetMapping(\"/supported-types\")"),
                "别名面既有出口属存量面，本批只增不改；若被删则历史调用方无过渡期");
    }

    @Test
    @DisplayName("前端弹窗消费属主目录而非本地显示面；通道走 dataClient 族根常量")
    void frontendConsumesOwnerCatalog() throws IOException {
        String modals = Files.readString(MODALS_TSX, StandardCharsets.UTF_8);
        StringBuilder imports = new StringBuilder();
        for (String line : modals.split("\n")) {
            if (line.trim().startsWith("import")) {
                imports.append(line).append('\n');
            }
        }
        assertFalse(imports.toString().contains("CONNECTION_TYPES"),
                "可选面一旦重新 import 显示元数据面＝本地第二事实源回潮（D-B4-78 病灶面）");
        assertTrue(imports.toString().contains("fetchSupportedDataSourceTypes"), "须引属主通道");
        assertTrue(imports.toString().contains("deriveSupportedOptions"), "下拉须由目录裁剪派生");

        String api = Files.readString(DATASOURCE_API_TS, StandardCharsets.UTF_8);
        assertTrue(api.contains("`${DATANET_DATASOURCE_BASE}/supported-types`"),
                "URL 须由 api/dataClient 的族根常量派生（D-B4-79：禁域内私设第二形态基址）");
        assertFalse(api.contains("/api/v1/datasource/supported-types"), "前端不得绑兼容别名面");
    }

    @Test
    @DisplayName("反向归属对账：准入面 11 枚（#142/B4-3e-71 起含 DORIS）在前端显示面各有归属（属主目录的 unresolvedCanonical 恒空）")
    void everyAcceptedTypeHasDisplayOwner() throws IOException {
        List<String> frontend = parseConnectionTypes(Files.readString(TYPES_TS, StandardCharsets.UTF_8));
        List<String> problems = displayOwnerProblems(frontend, API_ALIAS_FROZEN, realOwnerRequirements());
        assertTrue(problems.isEmpty(), () -> "反向归属对账：" + problems);
    }

    @Test
    @DisplayName("夹具：前端丢一枚显示归属（fs 下线／后端新增枚未接）⇒ 反向对账具名红")
    void displayOwnerCriterionGoesRed() {
        List<OwnerRequirement> real = realOwnerRequirements();
        assertEquals(11, real.size(),
                "准入面枚数独立钉值 11（#142/B4-3e-71 起含 DORIS；解析器失明＝空需求集会让本腿恒绿）");

        List<String> withoutFs = new ArrayList<>(FRONTEND_TYPES_FROZEN);
        withoutFs.remove("fs");
        List<String> p1 = displayOwnerProblems(withoutFs, API_ALIAS_FROZEN, real);
        assertEquals(1, p1.size(), () -> "FILESYSTEM 的唯一显示归属是 fs，下线须报 1 条具名红，实报 " + p1);
        assertTrue(p1.get(0).contains("FILESYSTEM"), p1.toString());

        List<String> p2 = displayOwnerProblems(FRONTEND_TYPES_FROZEN, API_ALIAS_FROZEN,
                List.of(new OwnerRequirement("POSTGRESQL", Set.of("POSTGRESQL")),
                        new OwnerRequirement("TimescaleNew", Set.of("TimescaleNew"))));
        assertEquals(1, p2.size(), () -> "新增承流名无显示归属须报 1 条，实报 " + p2);
        assertTrue(p2.get(0).contains("TimescaleNew"), p2.toString());
    }

    @Test
    @DisplayName("D-B4-93 收口：mapDsType 末腿＝显式 unknown，且 unknown 不入 19 枚选项面")
    void displayFallbackIsExplicitUnknown() throws IOException {
        String body = displayBranchBlock(Files.readString(MAPPERS_TS, StandardCharsets.UTF_8));
        String lastReturn = null;
        for (String rawLine : body.split("\n")) {
            String line = rawLine.trim();
            if (line.startsWith("return")) {
                lastReturn = line;
            }
        }
        assertEquals("return 'unknown';", lastReturn,
                "词表外名的显示回落必须是显式 unknown；回落具体类型＝按别族猜（详设 §4.7.2 禁项，D-B4-93）");

        List<String> frontend = parseConnectionTypes(Files.readString(TYPES_TS, StandardCharsets.UTF_8));
        assertFalse(frontend.contains("unknown"),
                "unknown 是「未解析」的读数而不是类型；进选项面即让用户能选一枚必然 400 的值");
        assertEquals(19, frontend.size(), "显示元数据面枚数仍由本门冻结为 19（前端切属主目录不改本面）");
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

        // ④ 显示面丢一枚归属（后端新增可登记类型而前端别名表未接）
        List<AliasRow> rows = new ArrayList<>();
        try {
            rows = parseHistoricalAliasTable(Files.readString(MAPPERS_TS, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new AssertionError("读取 mappers.ts 失败", e);
        }
        List<String> p4 = displayAliasProblems(Set.of("POSTGRESQL", "TimescaleNew"), rows);
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

    /**
     * 显示面判据（#142/B4-3e-67 起改读别名表）：每个规范名在前端标签通道必须落到一个前端合法
     * 类型名，且回环到同一规范名。
     *
     * <p>归位规则与前端 {@code resolveHistoricalType} 逐字同构（整词命中，或长度 ≥5 的 alias
     * 在归一化串上命中；最长命中者胜，同长分属两枚＝歧义）。本门只钉「11 枚规范名 ⇄ 显示枚 ⇄
     * {@code toApiType} 回环」三面同名，标签通道的行为面由前端门
     * {@code b4MapDsTypeAliasTable.spec.ts} 持有——本门不重复钉逐串读数，避免第二属主。</p>
     */
    private static List<String> displayAliasProblems(Set<String> canonicalTypes, List<AliasRow> rows) {
        List<String> out = new ArrayList<>();
        for (String canonical : canonicalTypes) {
            String target = resolveByAliasTable(canonical, rows);
            if (target == null) {
                out.add(canonical + " 在前端 HISTORICAL_TYPE_ALIASES 无显示归属"
                        + "（新增承流名须同批改前端别名表，禁靠默认回落）");
                continue;
            }
            DataSourceTypeVocabulary back = DataSourceTypeVocabulary.find(toApiType(target, API_ALIAS_FROZEN));
            if (back == null || !back.canonical().equals(canonical)) {
                out.add(canonical + " 的显示归属是 " + target
                        + "，回环解析为 " + (back == null ? "词表外" : back.canonical()) + "（三面不同名）");
            }
        }
        return out;
    }

    /**
     * 与前端同构的归位判据：命中集里最长 alias 所属类型胜；同长跨类型并列返回 null。
     * 门槛常量与前端 {@code WHOLE_WORD_ONLY_BELOW} 同值 5，两处不符即由本腿报红。
     * 大小写归一与前端一致（先 {@code toLowerCase()} 再切词/归一），调用方可传原文。
     */
    private static String resolveByAliasTable(String incomingRaw, List<AliasRow> rows) {
        String incoming = incomingRaw.toLowerCase(Locale.ROOT);
        String norm = incoming.replaceAll("[^a-z0-9]+", "");
        if (norm.isEmpty()) {
            return null;
        }
        List<String> tokens = new ArrayList<>();
        for (String token : incoming.split("[^a-z0-9]+")) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        int best = 0;
        String winner = null;
        boolean contested = false;
        for (AliasRow row : rows) {
            int bestForValue = 0;
            for (String alias : row.aliases()) {
                boolean hit = tokens.contains(alias)
                        || (alias.length() >= CONTAIN_MIN_LEN_FROZEN && norm.contains(alias));
                if (hit && alias.length() > bestForValue) {
                    bestForValue = alias.length();
                }
            }
            if (bestForValue <= 0) {
                continue;
            }
            if (bestForValue > best) {
                best = bestForValue;
                winner = row.value();
                contested = false;
            } else if (bestForValue == best && !row.value().equals(winner)) {
                contested = true;
            }
        }
        return contested ? null : winner;
    }

    /**
     * 反向归属判据（#142/B4-3e-64）：准入面的每一枚，前端显示面里必须有人能叫出它的名字。
     *
     * <p>与正向 {@link #crossFaceProblems} 成对：正向管「前端列了后端不承流的名」（用户选了撞 400），
     * 反向管「后端承流的名字前端解析不到」（下拉静默少一枚，属主目录把它报进
     * {@code unresolvedCanonical}）。命中口径与前端 {@code DISPLAY_TYPE_BY_API_NAME} 逐字一致：
     * 显示枚经 {@code toApiType} 大写后等于规范名<b>或</b>落在该枚的别名表里。</p>
     */
    private static List<String> displayOwnerProblems(
            List<String> frontend, Map<String, String> aliases, List<OwnerRequirement> requirements) {
        Set<String> displayNames = new LinkedHashSet<>();
        for (String f : frontend) {
            displayNames.add(toApiType(f, aliases).toUpperCase());
        }
        List<String> out = new ArrayList<>();
        for (OwnerRequirement r : requirements) {
            boolean hit = false;
            for (String n : r.names()) {
                if (displayNames.contains(n.toUpperCase())) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                out.add(r.canonical() + " 在前端显示面无归属（可登记名 " + r.names()
                        + " 无一命中）——前端下拉会缺这一枚，禁靠 unknown 兜过去");
            }
        }
        return out;
    }

    /** 由词表实时构造反向归属需求（规范名 ∪ 别名表），禁手抄第二份名表。 */
    private static List<OwnerRequirement> realOwnerRequirements() {
        List<OwnerRequirement> out = new ArrayList<>();
        for (DataSourceTypeVocabulary v : DataSourceTypeVocabulary.values()) {
            if (!v.registrable()) {
                continue;
            }
            Set<String> names = new LinkedHashSet<>();
            names.add(v.canonical());
            names.addAll(v.aliases());
            out.add(new OwnerRequirement(v.canonical(), names));
        }
        return out;
    }

    /** 一条反向归属需求：规范名 ＋ 它在显示面上可被叫出的全部名字。 */
    private record OwnerRequirement(String canonical, Set<String> names) {
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

    /** 定位 {@code mapDsType} 函数体（末腿判定用；标签通道自 #142/B4-3e-67 起不再由本门逐分支解析）。 */
    private static String displayBranchBlock(String mappersTs) {
        int start = mappersTs.indexOf("export function mapDsType");
        if (start < 0) {
            throw new IllegalStateException("mappers.ts 里找不到 mapDsType（前端显示通道改名＝本门失明）");
        }
        int end = mappersTs.indexOf("\n}", start);
        return mappersTs.substring(start, end < 0 ? mappersTs.length() : end);
    }

    /**
     * 解析前端 {@code HISTORICAL_TYPE_ALIASES} 数组块内的 {@code { value: 'x', aliases: ['a', 'b'] }}
     * 行（按声明序；#142/B4-3e-67 起本门逆向对账读这份表，不再读 {@code mapDsType} 的 {@code includes} 链）。
     *
     * <p>失明即红：声明改名／块未闭合／解析 0 行一律抛错，禁「空表＝无违例」的假绿。
     * 行的字面形态由前端门 {@code b4MapDsTypeAliasTable.spec.ts} 管（枚数／alias 语义／去重），
     * 本门只取「显示枚 ⇄ alias 集合」用于规范名回环，不重复钉行为面。</p>
     */
    private static List<AliasRow> parseHistoricalAliasTable(String mappersTs) {
        int start = mappersTs.indexOf("export const HISTORICAL_TYPE_ALIASES");
        if (start < 0) {
            throw new IllegalStateException(
                    "mappers.ts 里找不到 HISTORICAL_TYPE_ALIASES 声明（前端别名表改名＝本门失明）");
        }
        int end = mappersTs.indexOf("];", start);
        if (end < 0) {
            throw new IllegalStateException("HISTORICAL_TYPE_ALIASES 数组块未闭合");
        }
        List<AliasRow> out = new ArrayList<>();
        Pattern rowPattern = Pattern.compile(
                "\\{\\s*value:\\s*'([A-Za-z0-9_]+)',\\s*aliases:\\s*\\[([^\\]]*)\\]\\s*\\}");
        for (String rawLine : mappersTs.substring(start, end).split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) {
                continue;
            }
            if (!line.startsWith("{")) {
                continue; // 声明行（含类型标注）不是数据行
            }
            Matcher m = rowPattern.matcher(line);
            if (!m.find()) {
                throw new IllegalStateException("别名表出现无法归类的行（判据失明即红）：" + line);
            }
            List<String> aliases = new ArrayList<>();
            Matcher q = Pattern.compile("'([A-Za-z0-9_]+)'").matcher(m.group(2));
            while (q.find()) {
                aliases.add(q.group(1));
            }
            if (aliases.isEmpty()) {
                throw new IllegalStateException("别名表枚 " + m.group(1) + " 的 aliases 解析为空（判据失明即红）");
            }
            out.add(new AliasRow(m.group(1), aliases));
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("HISTORICAL_TYPE_ALIASES 解析为 0 枚（判据失明）");
        }
        return out;
    }

    /** 历史标签通道的一行：显示枚＋它可被叫出的全部原文 alias（小写）。 */
    private record AliasRow(String value, List<String> aliases) {
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
