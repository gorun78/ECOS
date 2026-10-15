package com.chinacreator.gzcm.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #140 门禁补实现批 · 子批 D-2 —— 证据链（Evidence/Claim/绑定）合规门禁。
 *
 * <p><b>差距来源</b>：{@code docs/20-需求/ECOS详细设计从零实施版-实现差距分析-2026-10-14.md}
 * §10.7.1 与 §15.3 登记 {@code EvidenceChainTest} / {@code ClaimEvidenceTest} 门禁不存在。
 * 按 R9① 裁决（缺的门禁＝补代码实现，禁从清单删除、禁先写空转测试）落地本类。</p>
 *
 * <p><b>被守的红线（详细设计-10 §6.2/§6.3 + F10-24）</b>：
 * <ol>
 *   <li><b>引用型证据</b>：{@code ecos_ai.ecos_wagent_evidence} 无内容字段——只存来源四元组/快照引用/
 *       定位符/内容哈希/密级，业务明细正文永不入表、不入 DO、不入响应；</li>
 *   <li><b>正文主权在 Claim</b>：断言原文 {@code claim_text} 只能存在于 claim 表/DO/视图，
 *       证据面出现原文列即违例；</li>
 *   <li><b>链闭合</b>：三表名录 + {@code claim_evidence} 复合主键两端列（claim_id/evidence_id）齐备，
 *       链路任一端缺列 = 追溯断链；</li>
 *   <li><b>数字一致性承载</b>：{@code claim.numeric_value NUMERIC(18,6)} + {@code value_source} +
 *       {@code verify_status} 三列（金额唯一事实源纪律的校验面，禁 AI 生成金额）；</li>
 *   <li><b>DDL↔DO↔响应 三层同形</b>：列集、DO 字段（camel→snake）、trace 端点响应键集三层必须
 *       一致——任一层单方面加列/加字段即变红（防止"DDL 有列、DO 悄悄加正文缓存"式漂移）。</li>
 * </ol>
 *
 * <p><b>反假绿四锁</b>（对齐子批 C 口径）：① 三表名录与三张列集全部<b>双向等值</b>钉死（扫描面为空即红）；
 * ② <b>解析器自检</b>——必须真看到 {@code content_hash VARCHAR(64)}、{@code locator_text TEXT}、
 * {@code claim_text TEXT}、复合主键；③ <b>内容列谓词自证</b>——{@code content_body/payload_json/
 * embedding_vector} 必须判真、{@code content_hash/locator_text/snapshot_ref_json} 必须判假
 * （否则"零内容列"是被恒假谓词空转出来的）；④ <b>反射层自检</b>——同一段字段/键扫描必须抓到
 * {@code claim_text}（正文在允许侧），抓不到即扫描退化。</p>
 *
 * <p>验收：{@code mvn -o test -pl gateway -Dtest='WAgentEvidenceChainComplianceTest'}（从 {@code ecos_backend/} 执行）。
 * 无 DB 依赖：判定对象是 DDL 单源文本（铁律 v2.2 §3.1）+ gateway 编译期聚合的 wagent 字节码。</p>
 */
@DisplayName("#140/D-2：证据链引用型与三层同形门禁（evidence 无正文·链闭合·数字承载·DDL/DO/响应一致）")
class WAgentEvidenceChainComplianceTest {

    private static final String MIGRATION_REL = "docs/50-db_script/02-flyway-migration/gateway";
    private static final String SCRIPT = "V240__wagent_evidence_claim.sql";

    private static final String EVIDENCE_TABLE = "ecos_ai.ecos_wagent_evidence";
    private static final String CLAIM_TABLE = "ecos_ai.ecos_wagent_claim";
    private static final String JUNCTION_TABLE = "ecos_ai.ecos_wagent_claim_evidence";

    /** 锁①：V240 建表名录双向钉死（一脚本三表）。 */
    private static final Set<String> PINNED_TABLES = Set.of(EVIDENCE_TABLE, CLAIM_TABLE, JUNCTION_TABLE);

    /** 锁①：evidence 20 列（V240 实测；新增列必须先改本名录 = 只加需登记）。 */
    private static final Set<String> PINNED_EVIDENCE_COLUMNS = Set.of(
            "id", "tenant_id", "org_id", "evidence_type", "source_engine", "source_object_type",
            "source_object_id", "source_version", "snapshot_ref_json", "locator_text", "content_hash",
            "classify_level", "is_valid", "domain", "version_no", "create_time", "update_time",
            "create_by", "update_by", "is_deleted");

    /** 锁①：claim 19 列（正文主权在此表）。 */
    private static final Set<String> PINNED_CLAIM_COLUMNS = Set.of(
            "id", "tenant_id", "org_id", "run_id", "step_id", "claim_text", "claim_type",
            "numeric_value", "numeric_unit", "value_source", "evidence_grade", "verify_status",
            "domain", "version_no", "create_time", "update_time", "create_by", "update_by", "is_deleted");

    /** 锁①：claim_evidence 13 列（复合主键两端 + 关系与覆盖率 + 通用列，无 surrogate id）。 */
    private static final Set<String> PINNED_JUNCTION_COLUMNS = Set.of(
            "claim_id", "evidence_id", "relation", "cover_ratio", "tenant_id", "org_id", "domain",
            "version_no", "create_time", "update_time", "create_by", "update_by", "is_deleted");

    /** 红线 1：内容承载列名词根（正文/载荷/原样副本/向量），任一表命中即违例。 */
    private static final Pattern CONTENT_ROOT = Pattern.compile(
            "(^|_)(content|body|payload|raw|embedding|vector|fulltext|full_text|document|markdown|html)(_|$)",
            Pattern.CASE_INSENSITIVE);

    /**
     * 钉死豁免（引用形态而非正文，逐条理由，禁静默扩大）：
     * {@code content_hash}=内容哈希锚定（正文不入库，完整性以哈希锚）、
     * {@code locator_text}=行指针/URI 定位符（引用非正文）、
     * {@code snapshot_ref_json}=口径快照的<b>引用</b>（指针 JSON，非明细副本）。
     */
    private static final Set<String> REFERENCE_ONLY_EXEMPTIONS = Set.of(
            "content_hash", "locator_text", "snapshot_ref_json");

    /** 红线 2：正文列名只允许出现在 claim 侧。 */
    private static final Set<String> TEXT_BEARER_ALLOWED_ONLY_IN_CLAIM = Set.of("claim_text");

    /** 锁③：内容列谓词的"必须判真"样本（探针语料，跑红即谓词失效）。 */
    private static final List<String> MUST_BE_CONTENT_COLUMN = List.of(
            "content_body", "payload_json", "embedding_vector", "raw_document", "full_text");

    /** 锁③：内容列谓词的"必须判假"样本（引用形态）。 */
    private static final List<String> MUST_NOT_BE_CONTENT_COLUMN = List.of(
            "content_hash", "locator_text", "snapshot_ref_json", "evidence_type", "cover_ratio");

    /** 响应面钉死：trace 端点 evidence 视图键集（12 键，引用型）。 */
    private static final Set<String> PINNED_EVIDENCE_VIEW_KEYS = Set.of(
            "evidence_id", "evidence_type", "source_engine", "source_object_type", "source_object_id",
            "source_version", "snapshot_ref_json", "locator_text", "content_hash", "classify_level",
            "is_valid", "create_time");

    /** 响应面钉死：claim 视图键集（10 键，正文在此侧合法）。 */
    private static final Set<String> PINNED_CLAIM_VIEW_KEYS = Set.of(
            "claim_id", "run_id", "step_id", "claim_text", "claim_type", "numeric_value",
            "numeric_unit", "value_source", "evidence_grade", "verify_status");

    /** 数字一致性承载（F10-24）：三列形态必须齐备，缺一即校验面空转。 */
    private static final Map<String, String> NUMERIC_CONSISTENCY_CARRIERS = Map.of(
            "numeric_value", "NUMERIC(18,6)",
            "value_source", "VARCHAR(24)",
            "verify_status", "VARCHAR(16)");

    private static final String CONTROLLER_FQN =
            "com.chinacreator.gzcm.engine.ai.wagent.controller.WAgentEvidenceTraceController";
    private static final String EVIDENCE_DO_FQN =
            "com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentEvidenceDO";
    private static final String CLAIM_DO_FQN =
            "com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentClaimDO";
    private static final String JUNCTION_DO_FQN =
            "com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentClaimEvidenceDO";

    // ────────────────────────────────────────────────────────────
    // ① 扫描面 + 解析器自检
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("V240 三表名录双向钉死，且解析器真能看见哈希列/定位符列/原文列/复合主键")
    void scriptInventoryAndScannerActuallySeeTheChain() throws IOException {
        Map<String, String> bodies = chainTableBodies();
        assertEquals(new TreeSet<>(PINNED_TABLES), new TreeSet<>(bodies.keySet()),
                "V240 建表名录与钉死三表不一致（链上任一表改名/新增都须先登记）");

        // 解析器自检：三条已知特征必须被真的看到，否则后面的"零内容列"是空转出来的假绿
        String evidence = bodies.get(EVIDENCE_TABLE);
        assertTrue(hasColumn(evidence, "content_hash"), "解析器看不到 evidence.content_hash（哈希锚定列）");
        assertTrue(matchesType(evidence, "content_hash", "VARCHAR(64)"),
                "evidence.content_hash 非 VARCHAR(64)（哈希形态漂移=完整性锚不可信）");
        assertTrue(hasColumn(evidence, "locator_text"), "解析器看不到 evidence.locator_text（定位符列）");
        assertTrue(matchesType(evidence, "snapshot_ref_json", "TEXT"),
                "evidence.snapshot_ref_json 非 TEXT（MC02：JSON 语义以 TEXT 承载）");
        assertTrue(hasColumn(bodies.get(CLAIM_TABLE), "claim_text"), "解析器看不到 claim.claim_text（正文主权列）");
        assertTrue(Pattern.compile("PRIMARY\\s+KEY\\s*\\(\\s*claim_id\\s*,\\s*evidence_id\\s*\\)",
                        Pattern.CASE_INSENSITIVE).matcher(bodies.get(JUNCTION_TABLE)).find(),
                "解析器在绑定表体内看不到复合主键 ⇒ 链闭合断言将空转");
    }

    // ────────────────────────────────────────────────────────────
    // ② 内容列谓词自证（锁③）
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("内容列谓词自证：正文/载荷/向量样本必须判真，哈希/定位符/快照引用必须判假")
    void contentColumnPredicateIsNotVacuous() {
        for (String probe : MUST_BE_CONTENT_COLUMN) {
            assertTrue(isContentBearing(probe), "内容列谓词漏判探针样本（谓词退化 ⇒ 门禁恒绿）: " + probe);
        }
        for (String reference : MUST_NOT_BE_CONTENT_COLUMN) {
            assertFalse(isContentBearing(reference), "内容列谓词误判引用形态（会把合法列判成违例）: " + reference);
        }
    }

    // ────────────────────────────────────────────────────────────
    // ③ §6.3 引用型红线 + 列集双向钉死
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("§6.3：evidence 列集等值钉死且零内容承载列；正文列 claim_text 只允许在 claim 表")
    void evidenceTableIsReferenceOnlyAndColumnsArePinned() throws IOException {
        Map<String, String> bodies = chainTableBodies();
        Set<String> evidenceColumns = columnNames(bodies.get(EVIDENCE_TABLE));
        assertEquals(new TreeSet<>(PINNED_EVIDENCE_COLUMNS), new TreeSet<>(evidenceColumns),
                EVIDENCE_TABLE + " 列集与钉死名录不一致（加列=需登记；出现正文列须先破红线再改本门禁）");

        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, String> table : bodies.entrySet()) {
            for (String column : columnNames(table.getValue())) {
                boolean allowedReference = REFERENCE_ONLY_EXEMPTIONS.contains(column);
                boolean allowedBearer = table.getKey().equals(CLAIM_TABLE)
                        && TEXT_BEARER_ALLOWED_ONLY_IN_CLAIM.contains(column);
                if (isContentBearing(column) && !allowedReference && !allowedBearer) {
                    violations.add(table.getKey() + "." + column
                            + " 是内容承载列（§6.3：证据链只存引用与哈希，正文主权仅在 claim.claim_text）");
                }
            }
        }
        assertTrue(violations.isEmpty(), "证据链内容承载违例：\n" + String.join("\n", violations));

        // 反向确认：三表列数下限（防止"整表列被清空"式假绿）
        assertEquals(20, evidenceColumns.size(), "evidence 列数基线 20");
        assertEquals(19, columnNames(bodies.get(CLAIM_TABLE)).size(), "claim 列数基线 19");
        assertEquals(13, columnNames(bodies.get(JUNCTION_TABLE)).size(), "claim_evidence 列数基线 13");
    }

    // ────────────────────────────────────────────────────────────
    // ④ 链闭合 + 数字一致性承载
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("链闭合：绑定表两端列 NOT NULL 且指向 claim.id/evidence.id；claim 三列数字一致性承载齐备")
    void chainClosureAndNumericCarriers() throws IOException {
        Map<String, String> bodies = chainTableBodies();
        String junction = bodies.get(JUNCTION_TABLE);
        Set<String> junctionColumns = columnNames(junction);
        assertEquals(new TreeSet<>(PINNED_JUNCTION_COLUMNS), new TreeSet<>(junctionColumns),
                JUNCTION_TABLE + " 列集与钉死名录不一致");
        for (String[] endpoint : List.of(
                new String[] {"claim_id", CLAIM_TABLE},
                new String[] {"evidence_id", EVIDENCE_TABLE})) {
            String endColumn = endpoint[0];
            String anchorTable = endpoint[1];
            assertTrue(Pattern.compile("\\b" + endColumn + "\\s+VARCHAR\\(36\\)\\s+NOT\\s+NULL",
                            Pattern.CASE_INSENSITIVE).matcher(junction).find(),
                    "绑定表 " + endColumn + " 必须是 VARCHAR(36) NOT NULL 端点列（链闭合前提）");
            String anchorBody = bodies.get(anchorTable);
            assertTrue(anchorBody != null, "锚表体内缺 " + anchorTable + "（链指向不存在的表）");
            assertTrue(Pattern.compile("\\bid\\s+VARCHAR\\(36\\)[^\\n]*\\bPRIMARY\\s+KEY\\b",
                            Pattern.CASE_INSENSITIVE).matcher(anchorBody).find(),
                    anchorTable + " 缺 MC01 形态 id VARCHAR(36) PRIMARY KEY ⇒ 绑定端 " + endColumn
                            + " 的引用目标不可解析（链断裂）");
            assertTrue(columnNames(anchorBody).contains("id"),
                    anchorTable + " 列集内没有 id（列解析器退化 ⇒ 上面两条闭合断言会空转）");
        }
        assertFalse(columnNames(junction).contains("id"),
                "绑定表出现 surrogate id（V240 明载复合主键即业务唯一性本体，加 id = 双主键口径分裂）");

        String claim = bodies.get(CLAIM_TABLE);
        for (Map.Entry<String, String> carrier : NUMERIC_CONSISTENCY_CARRIERS.entrySet()) {
            assertTrue(matchesType(claim, carrier.getKey(), carrier.getValue()),
                    "claim." + carrier.getKey() + " 形态非 " + carrier.getValue()
                            + "（F10-24 数字一致性校验面缺一即空转）");
        }
        assertTrue(Pattern.compile("\\brun_id\\s+VARCHAR\\(36\\)\\s+NOT\\s+NULL", Pattern.CASE_INSENSITIVE)
                        .matcher(claim).find(),
                "claim.run_id 必须 NOT NULL（断言必须挂在 Run 上，否则追溯链无根）");
    }

    // ────────────────────────────────────────────────────────────
    // ⑤ DDL ↔ DO ↔ 响应 三层同形
    // ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("三层同形：DO 字段（camel→snake）与 DDL 列集等值，DO 侧同样零正文字段（claim_text 为反向对照）")
    void dataObjectsMirrorTheDdlExactly() throws Exception {
        Map<String, String> bodies = chainTableBodies();
        assertMirror(EVIDENCE_DO_FQN, EVIDENCE_TABLE, bodies.get(EVIDENCE_TABLE), "evidence");
        assertMirror(CLAIM_DO_FQN, CLAIM_TABLE, bodies.get(CLAIM_TABLE), "claim");
        assertMirror(JUNCTION_DO_FQN, JUNCTION_TABLE, bodies.get(JUNCTION_TABLE), "claim_evidence");

        // 锁④：字段扫描必须真的看到 claim 侧正文列（否则上面"等值"可能建立在空集上）
        Set<String> claimFields = doColumns(CLAIM_DO_FQN);
        assertTrue(claimFields.contains("claim_text"),
                "WAgentClaimDO 未声明 claimText（正文承载位丢失或字段扫描退化）: " + claimFields);
        assertFalse(doColumns(EVIDENCE_DO_FQN).contains("claim_text"),
                "evidence DO 出现 claimText（正文副本进引用型证据面 = §6.3 违例）");
    }

    @Test
    @DisplayName("响应面同形：trace 端点 evidence/claim 视图键集等值钉死，证据视图零正文键")
    void traceResponsesExposeOnlyReferenceKeys() throws Exception {
        Class<?> controller = Class.forName(CONTROLLER_FQN);
        Map<String, Object> evidenceView = invokeView(controller, "evidenceView", EVIDENCE_DO_FQN);
        Map<String, Object> claimView = invokeView(controller, "claimView", CLAIM_DO_FQN);

        assertEquals(new TreeSet<>(PINNED_EVIDENCE_VIEW_KEYS), new TreeSet<>(evidenceView.keySet()),
                "evidence 响应键集与钉死名录不一致（加键=先裁正文承载面，禁把明细正文塞进追溯响应）");
        assertEquals(new TreeSet<>(PINNED_CLAIM_VIEW_KEYS), new TreeSet<>(claimView.keySet()),
                "claim 响应键集与钉死名录不一致");

        List<String> offenders = new ArrayList<>();
        for (String key : evidenceView.keySet()) {
            if (isContentBearing(key) && !REFERENCE_ONLY_EXEMPTIONS.contains(key)) {
                offenders.add("evidenceView." + key);
            }
        }
        assertTrue(offenders.isEmpty(), "证据追溯响应出现内容键：\n" + String.join("\n", offenders));

        // 反向对照：同一扫描必须看到 claim 侧的合法正文键
        assertTrue(claimView.containsKey("claim_text"),
                "claimView 未见 claim_text（键扫描退化 ⇒ 上面'零内容键'不可信）");
    }

    // ────────────────────────────────────────────────────────────
    // 判定与工具
    // ────────────────────────────────────────────────────────────

    /** 内容承载判定 = 词根命中 且 不在钉死的引用形态豁免内。 */
    private static boolean isContentBearing(String column) {
        return CONTENT_ROOT.matcher(column).find() && !REFERENCE_ONLY_EXEMPTIONS.contains(column);
    }

    private static void assertMirror(String doFqn, String table, String body, String label)
            throws ClassNotFoundException {
        Set<String> ddl = new TreeSet<>(columnNames(body));
        Set<String> doSide = new TreeSet<>(doColumns(doFqn));
        List<String> onlyInDdl = new ArrayList<>(ddl);
        onlyInDdl.removeAll(doSide);
        List<String> onlyInDo = new ArrayList<>(doSide);
        onlyInDo.removeAll(ddl);
        assertTrue(onlyInDdl.isEmpty() && onlyInDo.isEmpty(),
                table + " 与 " + doFqn + " 不同形（DDL 独有以下列 " + onlyInDdl
                        + " / DO 独有以下字段 " + onlyInDo + "）—— 三层同形红线：列、字段、响应键必须同步登记");
    }

    /** DO 字段（含通用列基类继承字段）→ snake_case 列名集合。 */
    private static Set<String> doColumns(String doFqn) throws ClassNotFoundException {
        Set<String> out = new LinkedHashSet<>();
        Class<?> clazz = Class.forName(doFqn);
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isSynthetic() || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                out.add(toSnake(field.getName()));
            }
            clazz = clazz.getSuperclass();
        }
        assertFalse(out.isEmpty(), "DO 字段扫描为空（反射面退化）: " + doFqn);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invokeView(Class<?> controller, String methodName, String doFqn)
            throws Exception {
        Class<?> doType = Class.forName(doFqn);
        Method method = controller.getDeclaredMethod(methodName, doType);
        method.setAccessible(true);
        Object instance = doType.getDeclaredConstructor().newInstance();
        Object result = method.invoke(null, instance);
        assertNotNull(result, methodName + " 返回空（视图方法已不被调用 = 门禁空转）");
        return (Map<String, Object>) result;
    }

    private static String toSnake(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** V240 三表表体（已剥注释、括号配平）。 */
    private static Map<String, String> chainTableBodies() throws IOException {
        Path file = repoRoot().resolve(MIGRATION_REL).resolve(SCRIPT);
        assertTrue(Files.isRegularFile(file), "缺 V240 证据三表脚本（DDL 单源漂移，铁律 v2.2 §3.1）: " + file);
        String sql = stripComments(Files.readString(file, StandardCharsets.UTF_8));
        Pattern create = Pattern.compile(
                "CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+([A-Za-z0-9_]+)\\.([A-Za-z0-9_]+)\\s*\\(",
                Pattern.CASE_INSENSITIVE);
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = create.matcher(sql);
        while (m.find()) {
            int open = sql.indexOf('(', m.end() - 1);
            int depth = 0, i = open;
            for (; i < sql.length(); i++) {
                char c = sql.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) break;
                }
            }
            assertTrue(depth == 0, SCRIPT + " 的 " + m.group(2) + " 括号未配平（解析前提失效）");
            out.put(m.group(1) + "." + m.group(2), sql.substring(open + 1, i));
        }
        return out;
    }

    private static Set<String> columnNames(String body) {
        Set<String> out = new LinkedHashSet<>();
        for (String raw : splitTopLevel(body)) {
            String frag = raw.trim();
            Matcher m = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z][A-Za-z0-9_]*(?:\\s*\\([^)]*\\))?)",
                    Pattern.CASE_INSENSITIVE).matcher(frag);
            if (!m.find()) {
                continue;
            }
            String name = m.group(1);
            if (Set.of("constraint", "primary", "unique", "foreign", "check").contains(name.toLowerCase())) {
                continue;
            }
            out.add(name.toLowerCase());
        }
        return out;
    }

    private static List<String> splitTopLevel(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(body.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(body.substring(start));
        return parts;
    }

    private static boolean hasColumn(String body, String column) {
        return Pattern.compile("(^|[^A-Za-z0-9_])" + Pattern.quote(column) + "([^A-Za-z0-9_]|$)")
                .matcher(body).find();
    }

    private static boolean matchesType(String body, String column, String type) {
        return Pattern.compile("\\b" + Pattern.quote(column) + "\\s+" + Pattern.quote(type)
                + "(?![A-Za-z0-9_(])", Pattern.CASE_INSENSITIVE).matcher(body).find();
    }

    private static Path repoRoot() throws IOException {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve("ecos_backend")) && Files.isDirectory(p.resolve("docs"))) {
                return p;
            }
            Path parent = p.getParent();
            if (parent == null || parent.equals(p)) break;
            p = parent;
        }
        throw new IOException("cwd 未锚定 ECOS 仓库根（ecos_backend/ 与 docs/ 未同时命中）: " + p);
    }

    /** 剥注释：块注释 + 整行 {@code --} + 逐行首个 {@code --} 到行尾（D-15 教训）。 */
    private static String stripComments(String sql) {
        StringBuilder sb = new StringBuilder();
        boolean inBlock = false;
        for (String line : sql.split("\r?\n")) {
            String cur = line;
            if (inBlock) {
                int end = cur.indexOf("*/");
                if (end < 0) {
                    sb.append('\n');
                    continue;
                }
                inBlock = false;
                cur = cur.substring(end + 2);
            }
            while (true) {
                int b = cur.indexOf("/*");
                if (b < 0) break;
                int e = cur.indexOf("*/", b + 2);
                if (e < 0) {
                    cur = cur.substring(0, b);
                    inBlock = true;
                    break;
                }
                cur = cur.substring(0, b) + cur.substring(e + 2);
            }
            int d = cur.indexOf("--");
            if (d >= 0) cur = cur.substring(0, d);
            sb.append(cur).append('\n');
        }
        return sb.toString();
    }
}
