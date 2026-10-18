package com.chinacreator.gzcm.engine.ai.wagent.tool;

import com.chinacreator.gzcm.engine.ai.service.ToolExecutorService.ContractToolDeclaration;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentToolContractDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentToolContractDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 详细设计-10 F10-17/F10-19 验收 —— {@link WAgentContractToolSource} 的读取端地板（#143/B5-1）。
 *
 * <p>这一层是"契约目录进咽喉"的准入面：地板放宽一寸，咽喉就多出一种"没登记也敢执行"的形态；
 * 地板收紧到误杀，则 F10-19 的 12 工具永远只能停在目录里。因此每个拒绝分支都配一条
 * 同装置的放行对照（{@link #activeRestContractBecomesExecutableDeclaration()}），
 * 禁把「返回空」写成恒真断言。</p>
 *
 * <p><b>词表与三处判据同源</b>：active 口径与 {@code PlanValidator#checkToolAndContract}、
 * {@code EngineEndpointInvoker#invoke} 同用 {@code ToolContractStatus.ACTIVE}；
 * 前缀归属与 {@code WAgentEngineCatalogTest} 的在位前缀族同用
 * {@code isLiveServicePrefix}。本类不另立判据。</p>
 */
class WAgentContractToolSourceTest {

    private static final String NAME = "query_project_profit";
    private static final String VERSION = "1.0.0";
    private static final String LIVE_PATH = "/api/v1/datanet/facts/STAGE";
    private static final String SCHEMA = "{\"type\":\"object\",\"properties\":{\"projectId\":{\"type\":\"string\"}}}";

    private final WAgentToolContractDao dao = mock(WAgentToolContractDao.class);
    private final WAgentContractToolSource source = new WAgentContractToolSource(dao);

    /** V266 形态的一行契约（status=active 才可执行；其余字段按 seed 的列名给值）。 */
    private WAgentToolContractDO row(String status, String kind, String path, String method,
                                     String sideEffect, String operationClass) {
        WAgentToolContractDO r = new WAgentToolContractDO();
        r.setName(NAME);
        r.setVersion(VERSION);
        r.setStatus(status);
        r.setEndpointKind(kind);
        r.setEndpointUrl(path);
        r.setEndpointMethod(method);
        r.setSideEffect(sideEffect);
        r.setOperationClass(operationClass);
        r.setInputSchemaJson(SCHEMA);
        r.setTimeoutMs(3000);
        return r;
    }

    private void stub(WAgentToolContractDO r) {
        when(dao.findByNameAndVersion(NAME, VERSION)).thenReturn(Optional.of(r));
    }

    private Optional<ContractToolDeclaration> find() {
        return source.findActive(NAME, VERSION);
    }

    // ── 放行面 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("F10-19 active+rest+在位前缀 → 声明体（方法归一大写、超时取契约列、operation 由声明折算）")
    void activeRestContractBecomesExecutableDeclaration() {
        stub(row("active", "rest", LIVE_PATH, "get", "none", null));

        ContractToolDeclaration d = find().orElseThrow();

        assertEquals(NAME, d.name());
        assertEquals(VERSION, d.version());
        assertEquals(LIVE_PATH, d.endpointPath(), "咽喉只拿相对路径，绝对化在服务端基址处完成");
        assertEquals("GET", d.httpMethod(), "声明大小写不得影响出向方法");
        assertEquals("read", d.operation());
        assertEquals(SCHEMA, d.inputSchemaJson());
        assertEquals(3000L, d.timeoutMs().longValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUGGEST", "WRITE"})
    @DisplayName("F10-19 SUGGEST 类候选与 WRITE 同判 write（宁严勿宽：写判成读 = 绕过 OPA 写面）")
    void suggestAndWriteBothCollapseToWriteOperation(String operationClass) {
        stub(row("active", "rest", "/api/v1/wagent/candidates", "POST", "draft", operationClass));

        assertEquals("write", find().orElseThrow().operation());
    }

    @Test
    @DisplayName("F10-17 operation_class 为 NULL 时按 side_effect 回落（DO 单源，本层不自建第二套折算）")
    void sideEffectFallbackIsUsedWhenOperationClassMissing() {
        stub(row("active", "rest", "/api/v1/cognitive/forecast", "POST", "production", null));
        assertEquals("write", find().orElseThrow().operation());

        stub(row("active", "rest", LIVE_PATH, "GET", "none", null));
        assertEquals("read", find().orElseThrow().operation());
    }

    // ── 拒绝面（逐条与放行面成对，禁恒真） ──────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"registered", "validated", "draft", "deprecated", "retired"})
    @DisplayName("F10-17 非 active 版不进咽喉（V266 seed 落 registered 属设计形态，禁改 seed 直发）")
    void nonActiveStatusIsNotExecutable(String status) {
        stub(row(status, "rest", LIVE_PATH, "GET", "none", "READ"));

        assertFalse(find().isPresent(), "非 active 不可执行: status=" + status);
    }

    @Test
    @DisplayName("F10-19 endpoint_kind 非 rest（jdbc 直连／反射）在读取端二次拒绝（V265 黑名单口径）")
    void nonRestEndpointKindIsRejected() {
        for (String kind : new String[]{"jdbc", "reflection", "in_jvm", null, ""}) {
            stub(row("active", kind, LIVE_PATH, "GET", "none", "READ"));
            assertFalse(source.findActive(NAME, VERSION).isPresent(),
                    "endpoint_kind 非 rest 不得进咽喉: " + kind);
        }
    }

    @Test
    @DisplayName("D-B5-2 /api/v1/task/submit 无在位 Engine 归属 → 拒绝出向（submit_readiness_task 现存形态）")
    void unattributablePrefixIsRejected() {
        stub(row("active", "rest", "/api/v1/task/submit", "POST", "none", "READ"));

        assertFalse(find().isPresent(),
                "前缀不可归属 = 路由表无属主，禁把请求打到猜的基址上（缺口需登记而非静默放行）");
        verify(dao, times(1)).findByNameAndVersion(NAME, VERSION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/cognition/models", "/api/v1/agent/runs", "/api/agent/runs"})
    @DisplayName("X-18/R-43 禁入前缀族（三向重叠与 legacy 编排面）不得成为契约目标")
    void bannedForeignPrefixIsRejected(String path) {
        stub(row("active", "rest", path, "GET", "none", "READ"));

        assertFalse(find().isPresent(), "禁入前缀: " + path);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1:18082/api/v1/datanet/facts", "/api/v1/datanet/../../etc",
            "/datanet/facts", "", "   "})
    @DisplayName("F10-19 端点必须是 /api/v1/ 相对形态：绝对 URL／上跳／空前缀一律拒绝")
    void nonRelativeRestPathIsRejected(String path) {
        stub(row("active", "rest", path, "GET", "none", "READ"));

        assertFalse(find().isPresent(), "非相对路径: [" + path + "]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DELETE", "PUT", "PATCH", "head", "TRACE", ""})
    @DisplayName("F10-19 出向方法白名单只开放 GET/POST；其余形态需先补设计再登记")
    void methodOutsideWhitelistIsRejected(String method) {
        stub(row("active", "rest", LIVE_PATH, method, "none", "READ"));

        assertFalse(find().isPresent(), "方法不在白名单: [" + method + "]");
    }

    @Test
    @DisplayName("F10-19 词表外的 side_effect 值不折算成 operation（拒绝，禁兜底猜 read）")
    void unknownSideEffectVocabularyIsRejected() {
        stub(row("active", "rest", LIVE_PATH, "GET", "chaos", null));

        assertFalse(find().isPresent(), "词表外值必须 fail-closed，不得默认成 read 放行");
    }

    @Test
    @DisplayName("F10-17 版本空白不查目录（无版本即不猜最新版，咽喉侧同一口径）")
    void blankVersionNeverQueriesCatalog() {
        assertFalse(source.findActive(NAME, null).isPresent());
        assertFalse(source.findActive(NAME, "   ").isPresent());
        assertFalse(source.findActive(null, VERSION).isPresent());
        assertFalse(source.findActive("", VERSION).isPresent());
        verify(dao, never()).findByNameAndVersion(any(), any());
    }

    @Test
    @DisplayName("F10-17 目录未命中该 name+version 组合 → 空（禁按名取其它版本回填）")
    void catalogMissIsNotBackfilledByOtherVersion() {
        when(dao.findByNameAndVersion(NAME, VERSION)).thenReturn(Optional.empty());
        WAgentToolContractDO newer = row("active", "rest", LIVE_PATH, "GET", "none", "READ");
        newer.setVersion("2.0.0");
        when(dao.findByNameAndVersion(NAME, "2.0.0")).thenReturn(Optional.of(newer));

        assertFalse(source.findActive(NAME, VERSION).isPresent());
        assertTrue(source.findActive(NAME, "2.0.0").isPresent(), "命中版必须放行（拒绝态非恒真）");
    }
}
