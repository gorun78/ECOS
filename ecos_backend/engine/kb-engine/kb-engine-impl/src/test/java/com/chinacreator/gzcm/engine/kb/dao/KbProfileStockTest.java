package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.engine.kb.service.profile.KbProfileDO;
import com.chinacreator.gzcm.engine.kb.service.profile.KbProfileService;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * 画像存量两计 {@code profileStock}（WAgent Readiness {@code know.sample_size} 度量属主取数）行为门。
 * 钉三件事：① 全量口径 SQL 形态（无谓词、is_deleted=0 恒在）且两计正确返回；
 * ② metricCode 过滤参数化落位（值走绑定参数，禁拼接）；
 * ③ 无行时 {@code COALESCE(SUM)=0} ⇒ {@code sampleSum=0} / {@code profiles=0}，
 * 禁回 null 制造「无存量」口径歧义。
 *
 * <p>DAO 腿用 mock {@code JdbcTemplate} + PgJDBC 语义最小复刻（next() 前取列抛 24000，
 * 与 {@code DqScoreDaoCursorTest} 同族）；Service 腿用内存 fake store（禁 @SpringBootTest），
 * 校验键面固定结构与的业务侧 null→0 兜底。</p>
 */
class KbProfileStockTest {

    private KbProfileStoreDao dao;
    private JdbcTemplate jdbcTemplate;

    /** 假结果集数据：列 1 = COUNT(*)，列 2 = COALESCE(SUM(sample_count),0)（null = SUM 空集）。 */
    private Integer fakeCount;
    private Long fakeSum;

    /** 上游捕获的 SQL 与绑定参数（供断言口径）。 */
    private final List<String> capturedSql = new ArrayList<>();
    private final List<Object[]> capturedArgs = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        dao = new KbProfileStoreDao("ecos_knowledge");
        jdbcTemplate = mock(JdbcTemplate.class);
        Field field = JdbcTemplateDaoSupport.class.getDeclaredField("jdbcTemplate");
        field.setAccessible(true);
        field.set(dao, jdbcTemplate);
        doAnswer(inv -> {
            // queryForObject varargs 重载：Mockito 将 varargs <b>扁平</b>进 getArguments()，
            // 实参序 = [sql, rowMapper, 绑定参数...]，按 RowMapper 位置切分，不绑死形参位。
            Object[] args = inv.getArguments();
            RowMapper<?> mapper = null;
            int mapperIdx = -1;
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof RowMapper<?> m) {
                    mapper = m;
                    mapperIdx = i;
                    break;
                }
            }
            String sql = (String) args[0];
            Object[] bind = new Object[args.length - mapperIdx - 1];
            System.arraycopy(args, mapperIdx + 1, bind, 0, bind.length);
            capturedSql.add(sql);
            capturedArgs.add(bind);
            // RowMapperResultSetExtractor 契约复刻：先 next() 定位首行，再 mapRow；
            // 未定位取列由假 ResultSet 按 PgJDBC 24000 抛错兜底（假绿即红）。
            ResultSet rs = fakeRs();
            if (!rs.next()) {
                throw new IllegalStateException("聚合两计查询恒返回一行，假结果集不得空");
            }
            return mapper.mapRow(rs, 0);
        }).when(jdbcTemplate).queryForObject(anyString(), any(RowMapper.class), any(Object[].class));
    }

    private void prime(int count, long sum) {
        fakeCount = count;
        fakeSum = sum;
    }

    private void primeEmpty() {
        fakeCount = 0;
        fakeSum = 0L; // SUM 空集经 COALESCE 在 DB 侧即 0（DAO 腿验证 DB 契约；Service 腿另验 null 兜底）
    }

    // ── ① 全量口径：两计正确，SQL 无谓词 ─────────────────────────────────

    @Test
    void fullStockCountsRowsAndSumsSamples() {
        prime(2, 350L);

        // 全量口径三过滤全空：DAO 不内置默认值，status=null ⇒ SQL 无谓词
        Map<String, Object> out = dao.profileStock(null, null, null);

        assertEquals(2, ((Number) out.get("profiles")).intValue());
        assertEquals(350L, ((Number) out.get("sampleSum")).longValue());
        String sql = capturedSql.get(0);
        assertTrue(sql.startsWith("SELECT COUNT(*), COALESCE(SUM(sample_count), 0) FROM ecos_knowledge.ecos_kb_profile WHERE is_deleted = 0"),
                "全量口径 SQL 形态必须精确（schema 限定 + is_deleted=0 恒在），实际=" + sql);
        assertFalse(sql.contains("status"), "status=null 不得拼入谓词");
        assertFalse(sql.contains("metric_code"), "metricCode=null 不得拼入谓词");
        assertTrue(capturedArgs.get(0).length == 0, "全量口径零绑定参数");
    }

    // ── ② metricCode 过滤：值参数化落位，禁拼接 ──────────────────────────

    @Test
    void metricCodeFilterIsParameterized() {
        prime(1, 77L);

        Map<String, Object> out = dao.profileStock("PUBLISHED", "delivery_cycle", null);

        assertEquals(1, ((Number) out.get("profiles")).intValue());
        assertEquals(77L, ((Number) out.get("sampleSum")).longValue());
        String sql = capturedSql.get(0);
        assertTrue(sql.endsWith(" AND status = ? AND metric_code = ?"),
                "status 与 metric_code 均须占位符化，实际=" + sql);
        Object[] args = capturedArgs.get(0);
        assertEquals(2, args.length, "恰好两枚绑定参数");
        assertEquals("PUBLISHED", args[0]);
        assertEquals("delivery_cycle", args[1], "metricCode 值只能走绑定参数（防注入）");
        assertFalse(sql.contains("delivery_cycle"), "SQL 文本禁出现 metricCode 值拼接");
    }

    // ── ③ 无行时两计为 0，禁 null ───────────────────────────────────────

    @Test
    void emptyResultReturnsZeroNotNull() {
        primeEmpty();

        Map<String, Object> out = dao.profileStock("PUBLISHED", null, null);

        assertEquals(0, ((Number) out.get("profiles")).intValue(), "空集 COUNT(*) 必须为 0");
        assertEquals(0L, ((Number) out.get("sampleSum")).longValue(),
                "COALESCE(SUM(...),0) 空集必须为 0，不得回 null 制造口径歧义");
    }

    // ── ④ domain 多枚 IN 展开参数化 ─────────────────────────────────────

    @Test
    void domainListExpandsToInPlaceholders() {
        prime(1, 5L);

        dao.profileStock(null, null, List.of("kb", " data "));

        String sql = capturedSql.get(0);
        assertTrue(sql.contains(" AND domain IN (?, ?)"),
                "domain 逐枚 trim 后按 IN (?,...) 展开，实际=" + sql);
        assertFalse(sql.contains("data"), "SQL 文本禁出现 domain 值拼接");
        Object[] args = capturedArgs.get(0);
        assertEquals(2, args.length);
        assertEquals("kb", args[0]);
        assertEquals("data", args[1], "空白 domain 过滤 + trim 后绑定");
    }

    // ── Service 层：键面固定 + 业务侧兜底 ────────────────────────────────

    static final class FakeStore implements com.chinacreator.gzcm.engine.kb.service.profile.KbProfileStore {
        final Map<String, Object> stock;

        FakeStore(Map<String, Object> stock) {
            this.stock = stock;
        }

        @Override
        public List<KbProfileDO> query(String metricCode, String status, String domain, String profileKeyPrefix) {
            throw new UnsupportedOperationException();
        }

        @Override
        public KbProfileDO findLatestByKey(String profileKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public KbProfileDO findById(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void insert(KbProfileDO row) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Object> profileStock(String status, String metricCode, List<String> domains) {
            return stock;
        }
    }

    @Test
    void servicePinsFixedKeySurface() {
        FakeStore store = new FakeStore(Map.of("profiles", 3, "sampleSum", 900L));
        KbProfileService svc = new KbProfileService(store, (m, d, f, t) -> List.of());

        Map<String, Object> out = svc.profileStock("PUBLISHED", "bound_rate", " dw , kb ");

        assertTrue(out.keySet().equals(List.of("profiles", "sampleSum", "statusFilter",
                        "metricCode", "domain", "evidence").stream().collect(java.util.stream.Collectors.toSet())),
                "键面必须恰为六键 profiles/sampleSum/statusFilter/metricCode/domain/evidence，actual=" + out.keySet());
        assertEquals(3, out.get("profiles"));
        assertEquals(900L, out.get("sampleSum"));
        assertEquals("PUBLISHED", out.get("statusFilter"));
        assertEquals("bound_rate", out.get("metricCode"));
        assertEquals("dw , kb", out.get("domain"), "domain 回显 trim 后原始入参（多枚拆分在 Service 侧）");
        Map<?, ?> evidence = assertInstanceOf(Map.class, out.get("evidence"));
        assertEquals("ecos_knowledge.ecos_kb_profile", evidence.get("table"));
    }

    @Test
    void serviceNormalizesBlankFiltersAndNullFallback() {
        // DAO 侧返回 sampleSum=null（防御性口径）⇒ Service 侧兜底 0，禁 null 出端点面
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("profiles", 0);
        raw.put("sampleSum", null);
        FakeStore store = new FakeStore(raw);
        KbProfileService svc = new KbProfileService(store, (m, d, f, t) -> List.of());

        Map<String, Object> out = svc.profileStock("  ", "  ", null);

        assertEquals(0, out.get("profiles"));
        assertEquals(0L, out.get("sampleSum"), "DAO 侧 null 样本合计必须被兜底为 0");
        assertNull(out.get("statusFilter"), "空白 status 归一为 null（不过滤）");
        assertNull(out.get("metricCode"), "空白 metricCode 归一为 null（不过滤）");
        assertNull(out.get("domain"), "缺省 domain 回显 null");
    }

    // ── 假 ResultSet（PgJDBC 语义：next() 前取列抛 24000；标签大小写不敏感）──

    private ResultSet fakeRs() {
        int[] cursor = {-1};
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if ("next".equals(name)) {
                cursor[0]++;
                // 两计永远单行（聚合结果恒一行）
                return cursor[0] == 0;
            }
            if ("close".equals(name) || "clearWarnings".equals(name)) {
                return null;
            }
            if ("wasNull".equals(name)) {
                return false;
            }
            if (name.equals("getObject") && args != null && args[0] instanceof Number col) {
                if (cursor[0] < 0) {
                    throw new SQLException("The result set is not positioned on a row.", "24000");
                }
                return col.intValue() == 2 ? fakeSum : fakeCount;
            }
            if (name.equals("getInt") && args != null && args[0] instanceof Number col) {
                if (cursor[0] < 0) {
                    throw new SQLException("The result set is not positioned on a row.", "24000");
                }
                if (col.intValue() == 1) {
                    return fakeCount == null ? 0 : fakeCount;
                }
                throw new SQLException("Column " + col + " not requested as int.", "07000");
            }
            Class<?> rt = method.getReturnType();
            if (rt == boolean.class) return false;
            if (rt == int.class) return 0;
            if (rt == long.class) return 0L;
            return null;
        };
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                new Class<?>[] {ResultSet.class}, handler);
    }
}
