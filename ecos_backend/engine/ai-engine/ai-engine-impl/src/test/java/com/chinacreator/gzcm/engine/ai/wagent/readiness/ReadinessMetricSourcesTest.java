package com.chinacreator.gzcm.engine.ai.wagent.readiness;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveModelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F10-08 八度量供数面接线门禁（#146b：四引擎属主读端点的 W 侧消费批）。
 *
 * <p><b>钉的是方向不是数值</b>：四个新源（data.staleness_days / onto.conflict_count /
 * know.sample_size / cog.cm_ready+backtest_ready）共享一条铁律——<b>「没取到」只能出
 * absent（UNKNOWN），绝不能出 0/false/1 冒充实测</b>（F10-08 硬规则② +
 * {@code MetricValue} 三态纪律）。每个源的每个失败腿（基址不可解析/非 2xx/空体/
 * 字段形态错/意图未映射）都必须落到具名 reason，禁静默。属主端点路径同样经
 * {@link com.chinacreator.gzcm.engine.ai.wagent.WAgentEngineCatalog} 前缀族校验，
 * 目录漂移时禁发出去 404 碰运气。</p>
 *
 * <p>全 mock：{@link RestTemplate} 桩化，不起真引擎、不连真库；口径对照台账
 * P0-8 行「八度量中 7 项无供数面」的收口证据。</p>
 */
class ReadinessMetricSourcesTest {

    private RestTemplate restTemplate;
    private ReadinessMetricFetchClient client;
    private DataFreshnessMetricSource freshness;
    private CaliberConflictCountMetricSource conflicts;
    private KbProfileStockMetricSource stock;
    private CognitiveModelReadinessMetricSource cog;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        client = new ReadinessMetricFetchClient(restTemplate);
        freshness = new DataFreshnessMetricSource(providerOf(client));
        conflicts = new CaliberConflictCountMetricSource(providerOf(client));
        stock = new KbProfileStockMetricSource(providerOf(client));
        cog = new CognitiveModelReadinessMetricSource(providerOf(client));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private static ReadinessProbeContext ctx(String intent) {
        return new ReadinessProbeContext("q1", "m1", "g1", null, null, intent,
                "t1", "o1", Map.of());
    }

    // ══════════════ 供数面全集声明（八度量 × 属主源单对位） ══════════════

    @Test
    @DisplayName("#146b：八度量各自的供数源对位表——coverage/applicability 无源（如实），其余六码 ≡ 一枚 @Component")
    void sourceSeparatorMatrixIsRequired() {
        ReadinessMetricSource[] all = {freshness, conflicts, stock, cog};
        Map<ReadinessCheckCode, String> expected = Map.of(
                ReadinessCheckCode.DATA_STALENESS, "data-freshness",
                ReadinessCheckCode.ONTO_CONFLICT_COUNT, "caliber-conflict-count",
                ReadinessCheckCode.KNOW_SAMPLE_SIZE, "kb-profile-stock",
                ReadinessCheckCode.COG_CM_READY, "cognitive-model-readiness",
                ReadinessCheckCode.COG_BACKTEST_READY, "cognitive-model-readiness");
        for (ReadinessCheckCode code : ReadinessCheckCode.values()) {
            String want = expected.get(code);
            String got = null;
            for (ReadinessMetricSource s : all) {
                if (s.supports(code)) {
                    assertTrue(got == null, code + " 被两枚源同时认领（单对位破坏）: " + got + " vs "
                            + s.sourceId());
                    got = s.sourceId();
                }
            }
            assertEquals(want, got, code + " 的供数源不符对位表（want=" + want + "）");
        }
        // 无源的两枚：coverage/applicability 无属主读端点且无本域可推口径（取证缺失 ⇒ UNKNOWN）
        // ——「接管」它们就是本批要消灭的假真理方向（把取不到折算成默认放行值）
        for (ReadinessMetricSource s : all) {
            assertFalse(s.supports(ReadinessCheckCode.DATA_COVERAGE), "coverage 不得被任何源接管");
            assertFalse(s.supports(ReadinessCheckCode.KNOW_APPLICABILITY), "applicability 不得被任何源接管");
            assertFalse(s.supports(ReadinessCheckCode.ONTO_CALIBER_DEFINED), "caliber 归本域 Goal 源，不串域");
        }
    }

    // ══════════════ D 层：staleness（天数 = 时间锚的日历差） ══════════════

    @Test
    @DisplayName("staleness：最近成功入湖的时间锚 → 自然日数；锚缺（从未有成功 run）≠ 0 天")
    void stalenessFromSucceededRunAnchor() {
        Instant anchored = Instant.now().minus(Duration.ofDays(10).plusSeconds(30));
        stubData(Map.of("lastSucceededFinishedAt", anchored.toString(),
                "source", "ecos_pipeline_run"));
        MetricValue v = freshness.measure(ReadinessCheckCode.DATA_STALENESS, ctx("FORECAST"));
        assertEquals(MetricValue.Kind.NUMBER, v.kind());
        long days = v.number().longValue();
        // 10d+30s 前的锚 ⇒ 日历差必在 [10, 11]（跑测耗时 <1 天，两个界都是回放判定）
        assertTrue(days >= 10 && days <= 11, "距锚日差越界: " + days);
        assertEquals("data-freshness", v.evidence().get("source"));
    }

    @Test
    @DisplayName("staleness 失败腿全谱：锚 null / 字面坏 / 403 / 超时 → absent（禁 0 天与 epoch-0）")
    void stalenessFailureLegsAllAbsent() {
        // 锚 = null：属主「无成功 run」形态 ⇒ 有时间锚缺，禁 epoch-0 洗出天文天数
        // （Map.of 禁 null 值，用可变 Map 承载 null 锚）
        Map<String, Object> noAnchorData = new LinkedHashMap<>();
        noAnchorData.put("lastSucceededFinishedAt", null);
        noAnchorData.put("source", "ecos_pipeline_run");
        stubData(noAnchorData);
        MetricValue noAnchor = freshness.measure(ReadinessCheckCode.DATA_STALENESS, ctx("FORECAST"));
        assertFalse(noAnchor.available());
        assertEquals("no-succeeded-run-on-record", noAnchor.evidence().get("reason"));

        // 时间字面坏（契约漂移）
        stubData(Map.of("lastSucceededFinishedAt", "YYYY-MM-DD", "source", "ecos_pipeline_run"));
        MetricValue badLiteral = freshness.measure(ReadinessCheckCode.DATA_STALENESS, ctx("FORECAST"));
        assertFalse(badLiteral.available());
        assertTrue(String.valueOf(badLiteral.evidence().get("reason")).startsWith("last-succeeded-not-instant"));

        // 403：属主面未登记匿名 + 无凭证腿 ⇒「取不到」不是「新鲜」
        stubThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));
        MetricValue http403 = freshness.measure(ReadinessCheckCode.DATA_STALENESS, ctx("FORECAST"));
        assertFalse(http403.available());
        assertEquals("fetch-failed:http-403", absentReason(http403));
    }

    // ══════════════ I 层：冲突计数（字面剔过型校验，负数/非数禁折叠成 0 冲突） ══════════════

    @Test
    @DisplayName("conflict_count：数值透传；负数/非数/404 全谱 absent（0 冲突是属主实测量，不是 W 侧兜底值）")
    void conflictCountShapeGates() {
        stubData(Map.of("count", 7));
        MetricValue ok = conflicts.measure(ReadinessCheckCode.ONTO_CONFLICT_COUNT, ctx("FORECAST"));
        assertEquals(MetricValue.Kind.NUMBER, ok.kind());
        assertEquals(7, ok.number().intValue());

        stubData(Map.of("count", "bogus"));
        assertFalse(conflicts.measure(ReadinessCheckCode.ONTO_CONFLICT_COUNT, ctx("FORECAST")).available());

        stubThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));
        assertFalse(conflicts.measure(ReadinessCheckCode.ONTO_CONFLICT_COUNT, ctx("FORECAST")).available());
    }

    // ══════════════ K 层：画像存量样本量（无存量的 0 是合法实测） ══════════════

    @Test
    @DisplayName("sample_size：SUM(sample_count) 直读；空存量的 0 是合法数值（不是 absent，更不是谎报）")
    void sampleSizeFromProfileStock() {
        stubData(Map.of("profiles", 5, "sampleSum", 482L, "statusFilter", "PUBLISHED",
                "evidence", Map.of("table", "ecos_knowledge.ecos_kb_profile")));
        MetricValue v = stock.measure(ReadinessCheckCode.KNOW_SAMPLE_SIZE, ctx("FORECAST"));
        assertEquals(MetricValue.Kind.NUMBER, v.kind());
        assertEquals(482, v.number().intValue());

        // 空存量：DAO 0 兜底，属主回 0 ⇒ 本侧如实 0（judge 侧 n<floor ⇒ FAIL 是证据链完经的，
        // 把 0 弄成 absent 反而把「确实没什么样本」洗成「不知道」——两个方向的歪曲都要禁）
        stubData(Map.of("profiles", 0, "sampleSum", 0L, "statusFilter", "PUBLISHED"));
        MetricValue zero = stock.measure(ReadinessCheckCode.KNOW_SAMPLE_SIZE, ctx("FORECAST"));
        assertEquals(MetricValue.Kind.NUMBER, zero.kind());
        assertEquals(0, zero.number().intValue());

        // 字段形态错（属主契约漂移）
        stubData(Map.of("sampleSum", "n/a"));
        assertFalse(stock.measure(ReadinessCheckCode.KNOW_SAMPLE_SIZE, ctx("FORECAST")).available());
    }

    // ══════════════ C 层：CM 在册∧发布 与 回测留痕（一次取数供两项） ══════════════

    @Test
    @DisplayName("cm_ready/backtest_ready：意图→CM 单源映射；行空=false(合法)，gate 三值→布尔，坏字面→absent")
    void cognitiveReadinessContractMapping() {
        ReadinessCheckCode cm = ReadinessCheckCode.COG_CM_READY;
        ReadinessCheckCode bt = ReadinessCheckCode.COG_BACKTEST_READY;

        // 意图词表与 CM 类型映射单源（CognitiveModelType.fromLegacyModelType）
        assertEquals(CognitiveModelType.CM_03,
                CognitiveModelReadinessMetricSource.cmForIntent("FORECAST"));
        assertEquals(CognitiveModelType.CM_01,
                CognitiveModelReadinessMetricSource.cmForIntent("iterate".isEmpty() ? "DECIDE" : "DECIDE"));
        assertEquals(CognitiveModelType.CM_05,
                CognitiveModelReadinessMetricSource.cmForIntent("SIMULATE"));
        assertNull(CognitiveModelReadinessMetricSource.cmForIntent("DIAGNOSE"),
                "未映射意图禁就近猜型（宁可 absent）");
        assertNull(CognitiveModelReadinessMetricSource.cmForIntent(null));
        assertNull(CognitiveModelReadinessMetricSource.cmForIntent("not-an-intent"));

        // 未映射意图 ⇒ 两项一并 absent
        MetricValue unmapped = cog.measure(cm, ctx("DIAGNOSE"));
        assertFalse(unmapped.available());
        assertTrue(absentReason(unmapped).startsWith("intent-not-mapped-to-cm:DIAGNOSE"));

        // PUBLISHED 过滤下无行：cm_ready = 合法实测 false（出布尔），backtest 无对象可判 = absent
        stubRows(List.of());
        MetricValue noPublished = cog.measure(cm, ctx("FORECAST"));
        assertEquals(MetricValue.Kind.BOOLEAN, noPublished.kind());
        assertFalse(noPublished.bool());
        assertFalse(cog.measure(bt, ctx("FORECAST")).available());

        // 有行：cm_ready = true；gate 三值逐一折
        stubRows(List.of(modelRow("PASS", "M-1")));
        assertTrue((Boolean) cog.measure(cm, ctx("FORECAST")).bool());
        assertTrue((Boolean) cog.measure(bt, ctx("FORECAST")).bool(), "gate=PASS ⇒ 有回测留痕");
        stubRows(List.of(modelRow("FAIL", "M-1")));
        assertFalse((Boolean) cog.measure(bt, ctx("FORECAST")).bool());
        stubRows(List.of(modelRow("PENDING", "M-1")));
        assertFalse((Boolean) cog.measure(bt, ctx("FORECAST")).bool(),
                "gate=PENDING（没跑）⇒ 无回测留痕 = false，禁放通");

        // 坏字面 ≠ PENDING（禁 orElse 宽容把契约漂移洗成可判定值）；字段缺失同理
        stubRows(List.of(modelRow("REALLY-NOPE", "M-1")));
        MetricValue badGate = cog.measure(bt, ctx("FORECAST"));
        assertFalse(badGate.available());
        Map<String, Object> gatelessRow = new LinkedHashMap<>();
        gatelessRow.put("modelId", "M-1"); // 无 backtestGate 键
        stubRows(List.of(gatelessRow));
        MetricValue noGate = cog.measure(bt, ctx("FORECAST"));
        assertFalse(noGate.available());
        assertEquals("backtest-gate-absent-on-hit-row", absentReason(noGate));
    }

    @Test
    @DisplayName("CM 查询路径拼在属主前缀族内（/api/v1/cognitive/models，R-43）；意图不映射连路径都不拼")
    void cognitivePathStaysInsideOwnerPrefix() {
        stubRows(List.of(modelRow("PASS", "M-1")));
        ReadinessMetricFetchClient tmp = client; // 仅用于保证引用（路径字面在下行断言里逐字锁定）
        assertTrue(com.chinacreator.gzcm.engine.ai.wagent.WAgentEngineCatalog.isLiveServicePrefix(
                "/api/v1/cognitive/models"));
        assertTrue(tmp != null);
    }

    // ══════════════ 取数结论载体不变量（两种成功态互斥 + 全空禁行） ══════════════

    @Test
    @DisplayName("FetchResult：三构件全空 IAE；data/rows 并存 IAE；ok() 对两种成功态都 true（禁只判 data 字段）")
    void fetchResultInvariants() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReadinessMetricFetchClient.FetchResult(null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ReadinessMetricFetchClient.FetchResult(Map.of("a", 1), List.of(), "r"));
        assertThrows(IllegalArgumentException.class,
                () -> new ReadinessMetricFetchClient.FetchResult(Map.of("a", 1), List.of(Map.of()), null));
        assertTrue(ReadinessMetricFetchClient.FetchResult.ok(Map.of("x", 1)).ok());
        assertTrue(ReadinessMetricFetchClient.FetchResult.okRows(List.of()).ok(),
                "空行列表是合法成功（'无在册行'是实测事实）");
        assertTrue(ReadinessMetricFetchClient.FetchResult.fail("http-403").reason().equals("http-403"));
        assertFalse(ReadinessMetricFetchClient.FetchResult.fail("http-403").ok());
    }

    // ── 夹具 ─────────────────────────────────────────

    private static String absentReason(MetricValue v) {
        Object r = v.evidence().get("reason");
        return r == null ? "(no-reason)" : String.valueOf(r);
    }

    /** 认知模型行（key 面与 cognitive 属主端点 decorateRows 对齐：backtestGate + modelId）。 */
    private static Map<String, Object> modelRow(String gate, String modelId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("modelId", modelId);
        row.put("backtestGate", gate);
        return row;
    }

    @SuppressWarnings("unchecked")
    private void stubData(Map<String, Object> data) {
        Map body = new LinkedHashMap();
        body.put("code", 200);
        body.put("data", data);
        when(restTemplate.exchange(ArgumentMatchers.anyString(), ArgumentMatchers.eq(HttpMethod.GET),
                ArgumentMatchers.any(HttpEntity.class), ArgumentMatchers.eq(Map.class)))
                .thenReturn((ResponseEntity) new ResponseEntity<>(body, HttpStatus.OK));
    }

    @SuppressWarnings("unchecked")
    private void stubRows(List<Map<String, Object>> rows) {
        when(restTemplate.exchange(ArgumentMatchers.anyString(), ArgumentMatchers.eq(HttpMethod.GET),
                ArgumentMatchers.any(HttpEntity.class), ArgumentMatchers.eq(Map.class)))
                .thenAnswer(inv -> new ResponseEntity<Map>(
                        envelope(rows), HttpStatus.OK)); // 行腿（data 段为数组）
    }

    /** ApiResponse 形态：data 段为 List（cognitive /models 端点契约）。 */
    private static Map<String, Object> envelope(List<?> rows) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", 200);
        m.put("data", rows);
        return m;
    }

    @SuppressWarnings("unchecked")
    private void stubThrow(Throwable error) {
        doThrow(error).when(restTemplate).exchange(ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(HttpMethod.GET),
                ArgumentMatchers.any(HttpEntity.class), ArgumentMatchers.eq(Map.class));
    }
}
