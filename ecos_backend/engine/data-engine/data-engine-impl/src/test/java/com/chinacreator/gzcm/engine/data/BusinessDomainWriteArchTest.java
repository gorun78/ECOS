package com.chinacreator.gzcm.engine.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C.3.6 — 业务域写入通道唯一性（{@code /write-channel}，M1；ST07 数据域二分）。
 *
 * <p>规则：{@code ecos_dw} 业务域五层表 <b>只允许</b> data-engine-impl
 * 内部通过 C.3.6 写通道（{@code WriteChannelController} → 服务 → Mapper）读写。
 * 任何 data-engine-impl 之外的 module 里出现 MyBatis {@code <mapper>} XML 且引用
 * {@code ecos_dw.} 限定表名 → FAIL（跨引擎越权写业务域表，违反 C.3.6 "网关唯一入口"）。
 *
 * <p>非严格 SQL 现场：Java 源码里出现 {@code "ecos_dw."} 字面量也一起扫（F02-14 A3
 * 时代 kb 里 {@code INSERT INTO ecos_knowledge.kb_doc_chunk} 就是同族反例；W65 已清偿，
 * 本测试永久守卫）。
 */
@DisplayName("C.3.6：非 data-engine 模块禁 MyBatis 直读写 ecos_dw 业务域表")
class BusinessDomainWriteArchTest {

    /** 唯一的合法承载 module 目录（相对 ecos_backend/）。 */
    private static final String DATA_ENGINE_IMPL = "engine/data-engine/data-engine-impl/";

    /** 触发条件 = {@code ecos_dw.} 带点源限定名（Java 或 XML 或 SQL 字面量）。 */
    private static final Pattern ECOS_DW_QUALIFIED = Pattern.compile(
            "ecos_dw\\.[A-Za-z_]\\w*");

    /** 白名单：非 data-engine 但仅出现在注释 / 文档 / SQL 白名单配置字面量（只用作校验）。 */
    private static final Set<String> KNOWN_PREEXISTING = Set.of(
            // ai-engine 里 AgentToolSqlWhitelist.java —— LLM UDF SQL 白名单表名配置字面量
            // （非 SQL 直读；W49/C38 QualifiedTableNameArchTest 已另守卫）。
            "AgentToolSqlWhitelist.java",
            "SecurityCheckerNodeDenyTest.java",
            // kb-engine 里 W62「A3 退出」双跑 diff 白名单与禁用断言 @Disabled 文案里
            // 出现 "ecos_dw.doc_chunk"（作为跨库写通道 REST 端点契约的字符串宣言），非 SQL 直读。
            "NoKbChunkWriteArchTest.java",
            "KbChunkWriteGoneArchTest.java",
            // 详细设计-06 F06-04 护栏测试族（ai-engine src/test）：护栏/裁决的被测输入<b>必须</b>
            // 是真实受保护 schema 的 SQL 字面量（ecos_dw.*），否则"拦住了什么"不可证；
            // 这些字符串只进 GuardrailsService/ToolGuardrail 的纯内存判定与 mock 白名单键，
            // 全链路零 JDBC/MyBatis 连接（同 SecurityCheckerNodeDenyTest.java 先例）。
            "ToolGuardrailChokepointTest.java",
            "ToolAdjudicationPayloadTest.java",
            "GuardrailAuditEventTest.java",
            "AgentToolPolicyGateTest.java");

    /**
     * <b>逐行</b>豁免（严于上面的文件名白名单）：只读载体描述符。
     *
     * <p>详细设计-05 §8.1 要求 C 层对象目录对每类对象给出「物理载体或引用态二选一」，
     * PREDICTION 的物理载体即 data-engine 侧 {@code ecos_dw.ecos_application_forecast_run}。
     * 该字面量是目录描述符、不是 SQL；cognitive 直写 ecos_dw 由
     * {@code ModuleDependencyArchTest#cognitiveDoesNotWriteEcosDwDirectly} 独立守卫
     * （ADR-14 / ST08，写入只经 data-engine 唯一写通道）。</p>
     *
     * <p>生效条件 = 命中行自带 {@link #READ_ONLY_CARRIER_TOKEN} <b>且</b>文件在
     * {@link #READ_ONLY_CARRIER_FILES} 内；同文件其它 {@code ecos_dw.} 字面量照旧 FAIL。
     * 豁免是否为死豁免由 {@code readOnlyCarrierExemptionStillLive} 反向守卫。</p>
     */
    private static final String READ_ONLY_CARRIER_TOKEN = "read-only-carrier-descriptor";

    /** 只读载体描述符豁免的唯一登记面（相对 ecos_backend/，路径须逐字命中）。 */
    private static final Set<String> READ_ONLY_CARRIER_FILES = Set.of(
            "engine/cognitive-engine/cognitive-engine-api/src/main/java/com/chinacreator/gzcm/"
                    + "engine/cognitive/api/objects/CognitiveObjectClass.java",
            "engine/cognitive-engine/cognitive-engine-impl/src/test/java/com/chinacreator/gzcm/"
                    + "engine/cognitive2/seam/CognitiveObjectCatalogTest.java");

    /**
     * <b>第二类逐行豁免（2026-10-15 追加，不改第一类语义）</b>：DDL 形态门禁的表名登记字面量。
     *
     * <p>触发场景＝本仓新增的静态门禁 {@code gateway/.../WAgentDdlShapeComplianceTest}：
     * 它按 ST03-A ④「金额实列唯一落业务域 ecos_dw」钉死一张承载表名做清单对账，
     * 该字面量<b>不建连、不发 SQL、不读写任何表</b>，只当集合元素用。若不做逐行豁免，
     * 结果是「守 DDL 形态的门禁被 C.3.6 写通道门禁判成越权」＝两条门禁互相打脸，
     * 而把表名拆开拼接以躲正则＝给本门禁制造假绿口子，二者都不可取。</p>
     *
     * <p>与第一类同样严格：命中行必须自带 {@link #DDL_INVENTORY_TOKEN}，
     * 同文件其它 {@code ecos_dw.} 字面量照旧 FAIL，并由
     * {@code ddlInventoryExemptionStillLive} 反向守卫该登记不为死豁免。</p>
     */
    private static final String DDL_INVENTORY_TOKEN = "ddl-shape-inventory-only";

    /** DDL 形态门禁登记表字面量的唯一登记面（相对 ecos_backend/，路径须逐字命中）。 */
    private static final Set<String> DDL_INVENTORY_FILES = Set.of(
            "gateway/src/test/java/com/chinacreator/gzcm/gateway/WAgentDdlShapeComplianceTest.java");

    /**
     * <b>第三类登记面（2026-10-16 追加，#142/B4-3d-5）</b>：安全裁决护栏／夹具测试的表名字面量。
     *
     * <p><b>触发场景</b>：本目标前序批次（#142/B4-1、B4-3a、B4-3c、CLS 契约批）在六枚
     * 非 data-engine 测试类里以 {@code ecos_dw.*} 真实受保护表名做裁决输入或策略夹具常量
     * （RLS 谓词渲染、OPA 部门范围、决策数据域载荷、AI fail-closed、CLS 列面契约、V246 策略单源夹具）。
     * 全量跑 {@code data-engine-impl} 才暴露：此前各批只按 {@code -Dtest} 选择性跑本域用例，
     * 从未跑过这枚横切门禁 ⇒ <b>本目标自己制造的假绿漏点</b>，非并行会话变更。</p>
     *
     * <p><b>为什么登记而非放宽扫描面</b>：C.3.6 禁的是「跨引擎直读写业务域表」，判定依据是<b>建连与读写载体</b>，
     * 不是表名字符串本身。这六枚文件全链路零 JDBC/MyBatis 连接，表名只进纯内存裁决与 mock 键
     * （同 {@link #KNOWN_PREEXISTING} 里 F06-04 护栏族先例）。两种更弱的修法一律不采用：
     * ① 把扫描面收窄到 {@code src/main} ⇒ 整个 test 树从此免检，给越权写开了口子；
     * ② 只按文件名登记 ⇒ 同名文件任意扩面都跟着豁免。本登记面按<b>相对路径逐字命中</b>，
     * 并由 {@code guardrailFixtureRegistryStillLiveAndConnectionFree} 反向守卫：
     * 登记文件不存在、或已无 {@code ecos_dw.} 字面量（死豁免）、<b>或出现任一建连/读写载体</b>
     * （豁免当场撤销、门禁变红）。</p>
     */
    private static final Set<String> GUARDRAIL_FIXTURE_FILES = Set.of(
            "engine/ai-engine/ai-engine-impl/src/test/java/com/chinacreator/gzcm/engine/ai/security/AiRlsFailClosedTest.java",
            "engine/security-engine/security-engine-impl/src/test/java/com/chinacreator/gzcm/engine/security/rolechannel/V246SeedFixtures.java",
            "engine/security-engine/security-engine-impl/src/test/java/com/chinacreator/gzcm/engine/security/rolechannel/OpaDeptTest.java",
            "engine/security-engine/security-engine-impl/src/test/java/com/chinacreator/gzcm/engine/security/rolechannel/DecisionDataScopePayloadTest.java",
            "engine/security-engine/security-engine-impl/src/test/java/com/chinacreator/gzcm/engine/security/RlsWhereClauseRenderTest.java",
            "runtime/common-api/src/test/java/com/chinacreator/gzcm/common/security/cls/ColumnSurfaceContractTest.java");

    /**
     * 登记面内<b>禁止</b>出现的建连／读写载体。出现任一即视为真实越权读写，豁免失效（见
     * {@code guardrailFixtureRegistryStillLiveAndConnectionFree}）。刻意用全限定/精确形态，
     * 不吃 {@code flyway} 这类出现在文档路径里的宽松词，避免反向守卫自己误判。
     */
    private static final Pattern CONNECTION_BEARING = Pattern.compile(
            "java\\.sql\\.(Connection|Statement|PreparedStatement|DriverManager)"
                    + "|javax\\.sql\\.DataSource|DataSource"
                    + "|JdbcTemplate|SqlSession|DriverManager"
                    + "|createStatement|prepareStatement|@Mapper\\b");

    @Test
    @DisplayName("非 data-engine 模块禁 ecos_dw 限定表名直读直查（Java + XML 同扫）")
    void noEcosDwOutsideDataEngine() throws IOException {
        Path backend = repoRoot().resolve("ecos_backend");
        assertTrue(Files.isDirectory(backend), "ecos_backend 缺");

        List<String> violations = new ArrayList<>();
        Set<String> offenders = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(backend)) {
            for (Path f : walk.filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return !s.contains("/target/") ;
                    })
                    .filter(p -> !p.toString().endsWith("/") && p.getFileName() != null)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.endsWith(".java") || n.endsWith(".xml");
                    })
                    .toList()) {
                // 只列文件
                if (!Files.isRegularFile(f)) continue;
                String name = f.getFileName().toString();
                if (KNOWN_PREEXISTING.contains(name)) continue;
                // Windows 分隔符归一：repoRoot/repo 均为绝对 FileSystem 绝对路径，relativize 会带 \
                String rel = backend.relativize(f).toString().replace('\\', '/');
                if (rel.startsWith(DATA_ENGINE_IMPL)) continue;  // 合法目标侧
                // 第三类登记面：相对路径逐字命中才豁免（严于文件名白名单），
                // 其「纯字面量、零建连」前提由 guardrailFixtureRegistryStillLiveAndConnectionFree 守卫
                if (GUARDRAIL_FIXTURE_FILES.contains(rel)) continue;
                FileCheck.check(rel, name, f, violations);
                if (!violations.isEmpty()
                        && violations.get(violations.size() - 1).startsWith(name + ":")) {
                    offenders.add(name);
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "C.3.6 越权：以下非 data-engine 文件含 ecos_dw 限定名（应走 /write-channel）:\n"
                        + String.join("\n", violations)
                        + "\noffenders: " + offenders);
    }

    @Test
    @DisplayName("data-engine-impl 里至少 1 处 ecos_dw 限定名存在（write-channel 唯一入口非过检）")
    void dataEngineOwnsEcosDwTables() throws IOException {
        Path impl = repoRoot().resolve("ecos_backend").resolve(
                Path.of("engine").resolve("data-engine").resolve("data-engine-impl").resolve("src/main"));
        int hits = 0;
        try (Stream<Path> walk = Files.walk(impl)) {
            for (Path f : (List<Path>) walk
                    .filter(p -> p.toString().endsWith(".xml")
                            || p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(f, StandardCharsets.UTF_8);
                if (ECOS_DW_QUALIFIED.matcher(text).find()) hits++;
            }
        }
        assertTrue(hits >= 2,
                "data-engine-impl 内 ecos_dw 限定名应 ≥2 处（write-channel fact mapper 家族），实际 " + hits);
    }

    @Test
    @DisplayName("只读载体描述符豁免不得沦为死豁免或越权扩面")
    void readOnlyCarrierExemptionStillLive() throws IOException {
        assertExemptionStillLive(READ_ONLY_CARRIER_FILES, READ_ONLY_CARRIER_TOKEN);
    }

    @Test
    @DisplayName("DDL 形态门禁登记表豁免同样不得沦为死豁免或越权扩面")
    void ddlInventoryExemptionStillLive() throws IOException {
        assertExemptionStillLive(DDL_INVENTORY_FILES, DDL_INVENTORY_TOKEN);
    }

    @Test
    @DisplayName("安全护栏夹具登记面：文件在位、字面量仍在用、且全篇零建连零读写载体")
    void guardrailFixtureRegistryStillLiveAndConnectionFree() throws IOException {
        Path backend = repoRoot().resolve("ecos_backend");
        List<String> stale = new ArrayList<>();
        for (String rel : GUARDRAIL_FIXTURE_FILES) {
            Path f = backend.resolve(Path.of(rel.replace('/', java.io.File.separatorChar)));
            if (!Files.isRegularFile(f)) {
                stale.add(rel + " :: 登记文件已不存在（豁免须随之删除）");
                continue;
            }
            String text = Files.readString(f, StandardCharsets.UTF_8);
            int literals = 0;
            for (String raw : text.split("\\R", -1)) {
                String t = raw.trim();
                if (t.isEmpty() || t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) continue;
                if (ECOS_DW_QUALIFIED.matcher(raw).find()) literals++;
            }
            // 死豁免反向守卫：字面量一旦不再出现，该行就该从登记面里删掉，不许留空壳豁免
            if (literals == 0) {
                stale.add(rel + " :: 无任何 ecos_dw 字面量（死豁免，登记应随之删除）");
            }
            // 越权扩面反向守卫：登记的前提是「纯字面量夹具」，出现建连/读写载体即刻撤销豁免
            Matcher carrier = CONNECTION_BEARING.matcher(text);
            if (carrier.find()) {
                stale.add(rel + " :: 出现建连/读写载体「" + carrier.group()
                        + "」（C.3.6 豁免只覆盖纯字面量夹具，真实读写必须撤销豁免）");
            }
        }
        assertTrue(stale.isEmpty(), "安全护栏夹具登记面失真:\n" + String.join("\n", stale));
    }

    /**
     * 逐行豁免登记面的通用反向守卫：登记文件必须存在、必须至少有一行带标记，
     * 且<b>不得</b>存在未带标记的 {@code ecos_dw.} 限定名行（那意味着豁免面被拿来掩护真实读写）。
     */
    private void assertExemptionStillLive(Set<String> registry, String token) throws IOException {
        Path backend = repoRoot().resolve("ecos_backend");
        List<String> stale = new ArrayList<>();
        for (String rel : registry) {
            Path f = backend.resolve(Path.of(rel.replace('/', java.io.File.separatorChar)));
            if (!Files.isRegularFile(f)) {
                stale.add(rel + " :: 登记文件已不存在（豁免须随之删除）");
                continue;
            }
            int marked = 0;
            int unmarked = 0;
            for (String raw : Files.readString(f, StandardCharsets.UTF_8).split("\\R", -1)) {
                String t = raw.trim();
                if (t.isEmpty() || t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) continue;
                if (!ECOS_DW_QUALIFIED.matcher(raw).find()) continue;
                if (raw.contains(token)) marked++;
                else unmarked++;
            }
            if (marked == 0) stale.add(rel + " :: 无任何带标记的 ecos_dw 描述符行（死豁免）");
            // 未标记行本应被 noEcosDwOutsideDataEngine 拦下；此处冗余断言，防豁免面被放宽
            assertEquals(0, unmarked,
                    rel + " :: 存在未带标记的 ecos_dw 限定名（" + token + " 豁免不得覆盖）");
        }
        assertTrue(stale.isEmpty(), "逐行豁免登记面失真:\n" + String.join("\n", stale));
    }

    /** 命中行是否落在两类<b>逐行</b>豁免登记面之内（文件登记 + 行内标记，二者缺一不豁免）。 */
    private static boolean exempted(String rel, String raw) {
        return (READ_ONLY_CARRIER_FILES.contains(rel) && raw.contains(READ_ONLY_CARRIER_TOKEN))
                || (DDL_INVENTORY_FILES.contains(rel) && raw.contains(DDL_INVENTORY_TOKEN));
    }

    /** 单文件扫描：Java 忽略 行首 // * /* 注释；XML 忽略 行首 <!--。 */
    private static final class FileCheck {
        static void check(String rel, String name, Path f, List<String> violations)
                throws IOException {
            String text = Files.readString(f, StandardCharsets.UTF_8);
            int lin = 0;
            for (String raw : text.split("\\R", -1)) {
                lin++;
                String t = raw.trim();
                if (t.isEmpty()) continue;
                if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
                        || t.startsWith("--") || t.startsWith("<!--")) {
                    continue;
                }
                // 仅 -File 文档纯叙述（.md 不进入）；.java / .xml 只信实际字面量
                if (ECOS_DW_QUALIFIED.matcher(raw).find()) {
                    // 逐行豁免：登记面内的命中行必须自带对应类别标记，缺标记照旧记违规
                    if (exempted(rel, raw)) {
                        continue;
                    }
                    violations.add(name + ":" + lin + " (" + rel + "): " + t);
                    return; // 每文件只记首个（长行降噪）
                }
            }
        }
    }

    private static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path root = dir;
        while (root != null) {
            if (Files.isRegularFile(root.resolve(".git")) || Files.isDirectory(root.resolve(".git"))) {
                return root;
            }
            root = root.getParent();
        }
        Path cur = dir;
        while (cur != null) {
            if (Files.isDirectory(cur.resolve("ecos_backend"))) return cur;
            cur = cur.getParent();
        }
        throw new IllegalStateException("未找到 ECOS 仓库根（缺 .git）");
    }
}
