package com.chinacreator.gzcm.engine.kb.repository;

import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code sys_compliance_rule} 时间列面与 DDL 单源的符合性门（#147Y）。
 *
 * <p><b>为什么整张重写</b>：本类前身（P0-4 v7 反向摄验）断言的是「注解 SQL 里必须出现
 * {@code EXTRACT(EPOCH FROM created_at) * 1000::BIGINT} / {@code TO_TIMESTAMP(...)}」——
 * 纯字面形态断言，与库里真实列型无关。而 V100 把本表 {@code effective_date/expiry_date/
 * created_at/updated_at} 定为 {@code BIGINT}，于是那份「绿」恰好把缺陷钉死：2026-11-02
 * gateway :8080 首次真起后 live 实测两条 500——读腿
 * {@code function pg_catalog.extract(unknown, bigint) does not exist}、写腿
 * {@code column "effective_date" is of type bigint but expression is of type timestamp
 * with time zone}。形态断言能跟着错误代码一起长绿，是本仓「假绿」里最贵的一类（它还主动
 * 阻止正确的修法），故本批不修补丁，改为把判据锚到 DDL 单源：列型是唯一事实源，SQL 必须服从它。</p>
 *
 * <p><b>反一刀切（同批钉住，禁把判据扩成「全仓不许 EXTRACT」）</b>：同族
 * {@link ExpertRuleMapper} 打的是 {@code ecos_knowledge.expert_rule}，该表由 V53 声明
 * {@code created_at TIMESTAMP DEFAULT NOW()} ⇒ 它的 EXTRACT/TO_TIMESTAMP 形态<b>是正确的</b>。
 * 两张表两种列型，任何「顺手统一」都会把好的那一面改坏，所以本门同时钉住这一不对称。</p>
 *
 * <p><b>能变红</b>：除生产面判据外，另跑一条把判据施加在「原缺陷形态 SQL」上的夹具腿
 * （不动生产码即可证明判据有效），以及两条 DDL 锚点回读腿（DDL 改列型时必须红，逼同批重裁，
 * 禁只改一侧静默放行）。纯反射/源面扫描，不依赖 Spring 上下文或外部 DB。</p>
 */
@ExtendWith(MockitoExtension.class)
class ComplianceRuleMapperTest {

    /** 本表四枚时间列（DDL 单源 V100 口径 = BIGINT epoch 毫秒）。 */
    private static final List<String> EPOCH_COLUMNS =
            List.of("effective_date", "expiry_date", "created_at", "updated_at");

    /** 对 BIGINT 列非法的时间函数/强转形态（本批缺陷的字面指纹）。 */
    private static final List<String> TIMESTAMP_SHAPE_TOKENS =
            List.of("EXTRACT(", "TO_TIMESTAMP(", "::BIGINT");

    @Mock
    private ComplianceRuleMapper mapper;

    // ── 锚点①：DDL 单源声明本表四列为 BIGINT ────────────────────

    @Test
    @DisplayName("DDL 单源锚点：V100 把 sys_compliance_rule 四枚时间列定为 BIGINT")
    void ddlSingleSourceDeclaresEpochColumnsAsBigint() throws IOException {
        String block = createTableBlock(readDdl("V100__compliance_tables.sql"), "sys_compliance_rule");
        for (String col : EPOCH_COLUMNS) {
            assertTrue(matchesColumnType(block, col, "BIGINT"),
                    "DDL 锚点失配：sys_compliance_rule." + col + " 不再是 BIGINT。"
                            + "列型一变，本门与 ComplianceRuleMapper 的 SQL 形态必须同批重裁，"
                            + "禁只改一侧（否则读腿/写腿立刻在 live 500）");
        }
    }

    // ── 判据②：本 Mapper 全语句面零时间函数形态（缺陷类判据） ────

    @Test
    @DisplayName("判据：ComplianceRuleMapper 每条语句都不得对 BIGINT 列用时间函数/强转")
    void noTimestampFunctionShapeOnAnyStatement() {
        List<String> violations = collectTimestampShapeViolations(ComplianceRuleMapper.class);
        assertTrue(violations.isEmpty(),
                "对 BIGINT 列使用时间函数/::BIGINT 强转（live 必 500）：" + violations);
        // 扫描面自证：本类带 SQL 注解的方法实测 7 枚（4 SELECT + INSERT + UPDATE + DELETE + COUNT），
        // 低于下限说明判据根本没吃到东西，不得按「零违例＝绿」理解
        int scanned = countSqlAnnotatedMethods(ComplianceRuleMapper.class);
        assertTrue(scanned >= 6, "扫描面异常：ComplianceRuleMapper 带 SQL 注解的方法数=" + scanned + " < 下限 6");
    }

    // ── 判据③：读腿按列直取 + COALESCE 归 0（与原 CASE 语义等价） ──

    @Test
    @DisplayName("判据：四枚 SELECT 一律 COALESCE(col, 0) AS camelCase 直取 epoch 毫秒")
    void selectFaceReadsEpochColumnsDirectly() {
        for (String methodName : List.of("findById", "findByDomain", "findAll", "findByStatus")) {
            String sql = sqlOf(ComplianceRuleMapper.class, methodName).toUpperCase(Locale.ROOT);
            for (String alias : List.of("EFFECTIVEDATE", "EXPIRYDATE", "CREATEDAT", "UPDATEDAT")) {
                assertTrue(sql.contains(" AS " + alias),
                        methodName + " 缺 " + alias.toLowerCase(Locale.ROOT) + " 别名（列名≠POJO 字段，MyBatis 吃不到）");
            }
            for (String col : EPOCH_COLUMNS) {
                assertTrue(sql.contains("COALESCE(" + col.toUpperCase(Locale.ROOT) + ", 0)"),
                        methodName + " 读腿必须 COALESCE(" + col + ", 0)（NULL 归 0 由 SQL 端承担，语义同原 CASE）");
            }
        }
    }

    // ── 判据④：写腿用 NULLIF 哨兵，不再用 CASE WHEN + 函数 ───────

    @Test
    @DisplayName("判据：INSERT/UPDATE 用 NULLIF(#{x}, 0) 承载 0⇔NULL 哨兵，created/updated 直传")
    void writeFaceUsesNullifSentinelAndPassesEpochThrough() {
        for (String methodName : List.of("insert", "update")) {
            String sql = sqlOf(ComplianceRuleMapper.class, methodName);
            assertTrue(sql.contains("NULLIF(#{effectiveDate}, 0)"),
                    methodName + " 的 effective_date 写入必须是 NULLIF(#{effectiveDate}, 0)");
            assertTrue(sql.contains("NULLIF(#{expiryDate}, 0)"),
                    methodName + " 的 expiry_date 写入必须是 NULLIF(#{expiryDate}, 0)");
        }
        String insert = sqlOf(ComplianceRuleMapper.class, "insert");
        assertTrue(insert.contains("#{createdAt}") && insert.contains("#{updatedAt}"),
                "INSERT 必须直传 #{createdAt}/#{updatedAt}（BIGINT 列无需任何转换）");
        assertTrue(sqlOf(ComplianceRuleMapper.class, "update").contains("updated_at=#{updatedAt}"),
                "UPDATE 必须直置 updated_at=#{updatedAt}");
    }

    // ── 反一刀切⑤：expert_rule 那一面确实是 TIMESTAMP，写法必须保持 ──

    @Test
    @DisplayName("反一刀切：V53 声明 expert_rule.created_at 为 TIMESTAMP，其 Mapper 保持时间函数形态")
    void expertRuleFaceIsGenuinelyTimestampAndMustNotBeUnified() throws IOException {
        String block = createTableBlock(readDdl("V53__ecos_kb_engine.sql"), "ecos_knowledge.expert_rule");
        assertTrue(matchesColumnType(block, "created_at", "TIMESTAMP"),
                "expert_rule.created_at 不再是 TIMESTAMP ⇒ 本腿与 ExpertRuleMapper 需同批重裁");

        String expertSelect = sqlOf(ExpertRuleMapper.class, "findById").toUpperCase(Locale.ROOT);
        assertTrue(expertSelect.contains("EXTRACT(EPOCH FROM CREATED_AT)"),
                "ExpertRuleMapper 读腿必须保留 EXTRACT(EPOCH ...)——它的列真是 TIMESTAMP，"
                        + "把 #147Y 的判据扩到这张表就是把对的一面改坏");
        assertTrue(sqlOf(ExpertRuleMapper.class, "insert").toUpperCase(Locale.ROOT).contains("TO_TIMESTAMP("),
                "ExpertRuleMapper 写腿必须保留 TO_TIMESTAMP（两表两种列型，禁顺手统一）");
    }

    // ── 能变红⑥：判据施加于「原缺陷形态」必须报违例 ──────────────

    @Test
    @DisplayName("能变红：把判据施加在原缺陷 SQL 上必须报出违例（证明本门不是永绿）")
    void predicateBitesOnTheLegacyDefectiveShape() {
        String legacySelect = "SELECT CASE WHEN effective_date IS NULL THEN 0 "
                + "ELSE EXTRACT(EPOCH FROM effective_date) * 1000::BIGINT END AS effectiveDate "
                + "FROM sys_compliance_rule";
        List<String> hits = timestampShapeViolations(legacySelect);
        assertFalse(hits.isEmpty(), "判据对原缺陷形态零命中＝判据失效（永绿门），本批收口不成立");
        assertTrue(hits.stream().anyMatch(h -> h.contains("EXTRACT(")), "应命中 EXTRACT( 形态：" + hits);
        assertTrue(hits.stream().anyMatch(h -> h.contains("::BIGINT")), "应命中 ::BIGINT 形态：" + hits);

        String legacyInsert = "INSERT INTO sys_compliance_rule (created_at) "
                + "VALUES (TO_TIMESTAMP(#{createdAt} / 1000.0))";
        assertTrue(timestampShapeViolations(legacyInsert).stream()
                .anyMatch(h -> h.contains("TO_TIMESTAMP(")), "写腿原形态应命中 TO_TIMESTAMP(");

        // 反向自检：收口后的形态不得报违例（否则上面各腿属误报）
        assertTrue(timestampShapeViolations("SELECT COALESCE(created_at, 0) AS createdAt "
                + "FROM sys_compliance_rule").isEmpty(), "合法形态被误报＝判据过宽");
    }

    // ── POJO 面：时间字段仍是 long 毫秒（wire 契约不因本批变化） ────

    @Test
    @DisplayName("POJO 锚点：ComplianceRule/ExpertRule 时间字段为 long，出参契约不因本批变化")
    void pojoTimeFieldsStayPrimitiveLong() throws Exception {
        assertEquals(long.class, ComplianceRule.class.getDeclaredField("effectiveDate").getType(),
                "effectiveDate 必须是 long，SQL 改型不得顺手动 wire 面");
        assertEquals(long.class, ComplianceRule.class.getDeclaredField("expiryDate").getType(),
                "expiryDate 必须是 long");
        // createdAt/updatedAt 声明在基类 ExpertRule；getDeclaredField 不含继承字段，按声明处断言
        Class<?> owner = ComplianceRule.class.getSuperclass();
        assertEquals(long.class, owner.getDeclaredField("createdAt").getType(),
                "createdAt 必须是 long（声明处=" + owner.getSimpleName() + "）");
        assertEquals(long.class, owner.getDeclaredField("updatedAt").getType(),
                "updatedAt 必须是 long（声明处=" + owner.getSimpleName() + "）");
    }

    // ── 行为面：mock 一次读，用 verify 真计数（前身是恒 1 的假计数） ──

    @Test
    @DisplayName("行为面：findById 走 Mapper 通道，单次调用可被 verify 计数")
    void findByIdInvokesMapperOnce() {
        ComplianceRule row = new ComplianceRule();
        row.setId("c-1");
        row.setName("大额审批");
        row.setEffectiveDate(0L);
        row.setCreatedAt(1_700_000_000_000L);
        when(mapper.findById("c-1")).thenReturn(row);

        ComplianceRule got = mapper.findById("c-1");

        assertNotNull(got);
        assertEquals("c-1", got.getId());
        assertEquals(1_700_000_000_000L, got.getCreatedAt());
        verify(mapper, times(1)).findById("c-1");
    }

    // ── helpers ─────────────────────────────────────────────

    /** 收集某 Mapper 接口所有带 SQL 注解方法的时间函数形态违例（含方法名定位）。 */
    private static List<String> collectTimestampShapeViolations(Class<?> mapperType) {
        List<String> out = new ArrayList<>();
        for (Method m : mapperType.getDeclaredMethods()) {
            String sql = sqlOf(m);
            if (sql == null) {
                continue;
            }
            for (String hit : timestampShapeViolations(sql)) {
                out.add(m.getName() + " -> " + hit);
            }
        }
        return out;
    }

    /** 单条 SQL 的违例 token 清单（判据本体，与生产码解耦以便夹具腿复用）。 */
    private static List<String> timestampShapeViolations(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        List<String> hits = new ArrayList<>();
        for (String token : TIMESTAMP_SHAPE_TOKENS) {
            if (upper.contains(token.toUpperCase(Locale.ROOT))) {
                hits.add(token);
            }
        }
        return hits;
    }

    private static int countSqlAnnotatedMethods(Class<?> mapperType) {
        int n = 0;
        for (Method m : mapperType.getDeclaredMethods()) {
            if (sqlOf(m) != null) {
                n++;
            }
        }
        return n;
    }

    private static String sqlOf(Class<?> mapperType, String methodName) {
        for (Method m : mapperType.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                String sql = sqlOf(m);
                if (sql != null) {
                    return sql;
                }
            }
        }
        throw new AssertionError("method not found or has no SQL annotation: " + methodName);
    }

    private static String sqlOf(Method m) {
        Select sel = m.getAnnotation(Select.class);
        if (sel != null) {
            return String.join(" ", sel.value());
        }
        Insert ins = m.getAnnotation(Insert.class);
        if (ins != null) {
            return String.join(" ", ins.value());
        }
        Update upd = m.getAnnotation(Update.class);
        if (upd != null) {
            return String.join(" ", upd.value());
        }
        Delete del = m.getAnnotation(Delete.class);
        if (del != null) {
            return String.join(" ", del.value());
        }
        return null;
    }

    /** 从 Flyway 单源目录读一枚迁移脚本全文。 */
    private static String readDdl(String fileName) throws IOException {
        Path ddl = repoRoot().resolve("docs/50-db_script/02-flyway-migration/gateway").resolve(fileName);
        assertTrue(Files.isRegularFile(ddl), "DDL 单源脚本缺失：" + ddl);
        return Files.readString(ddl, StandardCharsets.UTF_8);
    }

    /** 截取 {@code CREATE TABLE [IF NOT EXISTS] <name> (...)} 的列定义块（含 schema 限定名）。 */
    private static String createTableBlock(String ddl, String tableName) {
        String normalized = ddl.replace('\r', '\n');
        String marker = "CREATE TABLE IF NOT EXISTS " + tableName;
        int from = indexOfIgnoreCase(normalized, marker);
        assertTrue(from >= 0, "DDL 里找不到 " + marker);
        int open = normalized.indexOf('(', from);
        int close = normalized.indexOf("\n)", open);
        assertTrue(open > 0 && close > open, "CREATE TABLE " + tableName + " 列定义块解析失败");
        return normalized.substring(open + 1, close);
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        return haystack.toUpperCase(Locale.ROOT).indexOf(needle.toUpperCase(Locale.ROOT));
    }

    /** 列名 → 声明类型前缀匹配（剥行内注释、逐行、列首词判定）。 */
    private static boolean matchesColumnType(String block, String column, String typePrefix) {
        for (String raw : block.split("\n")) {
            String line = raw.contains("--") ? raw.substring(0, raw.indexOf("--")) : raw;
            String trimmed = line.trim();
            if (trimmed.regionMatches(true, 0, column, 0, column.length())
                    && trimmed.length() > column.length()
                    && !Character.isLetterOrDigit(trimmed.charAt(column.length()))) {
                String rest = trimmed.substring(column.length()).trim().toUpperCase(Locale.ROOT);
                return rest.startsWith(typePrefix.toUpperCase(Locale.ROOT));
            }
        }
        return false;
    }

    private static Path repoRoot() {
        for (Path p = Paths.get("").toAbsolutePath(); p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("docs/50-db_script/02-flyway-migration/gateway"))) {
                return p;
            }
        }
        throw new AssertionError("未定位到仓库根（docs/50-db_script/02-flyway-migration/gateway 不可达）");
    }
}
