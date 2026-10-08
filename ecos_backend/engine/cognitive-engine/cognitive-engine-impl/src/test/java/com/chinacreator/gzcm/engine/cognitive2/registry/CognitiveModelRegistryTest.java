package com.chinacreator.gzcm.engine.cognitive2.registry;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveModelType;
import com.chinacreator.gzcm.engine.cognitive.api.GateOutcome;
import com.chinacreator.gzcm.engine.cognitive.api.RegistryState;
import com.chinacreator.gzcm.engine.cognitive2.dao.ModelRegistryDao;
import com.chinacreator.gzcm.engine.cognitive2.service.ModelPublishGateEvaluator;
import com.chinacreator.gzcm.engine.cognitive2.service.ModelRegistryService;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.ModelContentHasher;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-COG-07 认知模型注册中心行为冻结（PRD-05 §6.2 验收 1/2/4 名 + 本批自设项）。
 *
 * <p>DAO 以 Mockito 替身注入：本测试裁决的是 <b>Service 层门禁/不可变/版本链/哈希逻辑</b>；
 * "PUBLISHED 行 UPDATE 必败" 的库内防线（DAO UPDATE 语句带 {@code registry_state <> 'PUBLISHED'}
 * 条件 → 0 行受影响）同样在此以 stub 返回 0 复现，两层防线共用同一显式 4xx 出口（禁静默）。
 * 库侧真实 SQL 行为依赖 V261 迁移实跑（§14.4 未授权项），测试不触库。</p>
 */
class CognitiveModelRegistryTest {

    private final ModelRegistryDao dao = mock(ModelRegistryDao.class);
    private final ModelRegistryService service =
            new ModelRegistryService(dao, new ModelPublishGateEvaluator());

    // ── 验收 1：已发布版本行不可变（PRD 冻结名）──

    @Test
    @DisplayName("publishedVersionRowIsImmutable：PUBLISHED 行禁 UPDATE（哈希/门禁/升格/重复发布全部 409）")
    void publishedVersionRowIsImmutable() {
        Map<String, Object> published = draftRow();
        published.put("registry_state", "PUBLISHED");
        published.put("content_hash", "f".repeat(64));
        when(dao.selectRowById("r1")).thenReturn(published);

        CognitiveGuardException h = assertThrows(CognitiveGuardException.class,
                () -> service.computeAndFreezeContentHash("r1"));
        assertEquals(CognitiveErrorCode.E409_MODEL_IMMUTABLE, h.errorCode);
        assertEquals(409, h.httpStatus);

        assertThrows(CognitiveGuardException.class, () -> service.evaluateAndRecordGates("r1"));
        assertThrows(CognitiveGuardException.class, () -> service.publish("r1", "auditor"));

        // 不可变 = 写 SQL 根本不该发出（不只是发了被拒）
        verify(dao, never()).updateContentHashIfDraft(anyString(), anyString());
        verify(dao, never()).updateGatesIfDraft(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(dao, never()).promoteToPublishedIfReady(anyString(), anyString());
    }

    @Test
    @DisplayName("publishedVersionRowIsImmutable（库内二次防线）：DRAFT 判定后并发升格 ⇒ UPDATE 0 行仍显式 409")
    void staleDraftRaceOnPublishIsRejected() {
        Map<String, Object> row = publishableDraftRow();
        when(dao.selectRowById("r1")).thenReturn(row);
        when(dao.promoteToPublishedIfReady("r1", "auditor")).thenReturn(0); // WHERE 守卫未放行

        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.publish("r1", "auditor"));
        assertEquals(CognitiveErrorCode.E409_MODEL_IMMUTABLE, e.errorCode);
    }

    // ── 验收 2：四类门禁全 PASS 才可升格（PRD 冻结名，参数化 4 门 × FAIL/PENDING）──

    @ParameterizedTest(name = "{0}={1} ⇒ 拒发布 422")
    @CsvSource({
            "gate_contract,   FAIL",
            "gate_contract,   PENDING",
            "gate_regression, FAIL",
            "gate_regression, PENDING",
            "gate_backtest,   FAIL",
            "gate_backtest,   PENDING",
            "gate_calibration,FAIL",
            "gate_calibration,PENDING",
    })
    void publishRequiresAllFourGates(String gateColumn, String outcome) {
        Map<String, Object> row = publishableDraftRow();
        row.put(gateColumn, outcome);
        when(dao.selectRowById("r1")).thenReturn(row);

        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.publish("r1", "auditor"));
        assertEquals(CognitiveErrorCode.E422_GATES_NOT_PASSED, e.errorCode);
        assertEquals(422, e.httpStatus);
        assertTrue(e.missing.contains(gateColumn.replace("gate_", "") + ":" + outcome),
                "4xx 必须点名未过门禁（禁静默成功），missing=" + e.missing);
        verify(dao, never()).promoteToPublishedIfReady(anyString(), anyString());
    }

    @Test
    @DisplayName("publishRequiresAllFourGates：content_hash 未冻结 ⇒ 同样 422 点名 content_hash")
    void publishRequiresFrozenContentHash() {
        Map<String, Object> row = publishableDraftRow();
        row.remove("content_hash");
        when(dao.selectRowById("r1")).thenReturn(row);

        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.publish("r1", "auditor"));
        assertEquals(CognitiveErrorCode.E422_GATES_NOT_PASSED, e.errorCode);
        assertEquals("content_hash", e.missing);
    }

    @Test
    @DisplayName("全 PASS + 哈希已冻结 ⇒ 升格放行（正向可失败对照，非永真断言）")
    void publishAllowsWhenAllGatesPassAndHashFrozen() {
        Map<String, Object> published = publishableDraftRow();
        published.put("registry_state", "PUBLISHED");
        // 第一次读 = DRAFT 待升格行；升格后再读 = PUBLISHED 留痕
        when(dao.selectRowById("r1")).thenReturn(publishableDraftRow(), published);
        when(dao.promoteToPublishedIfReady("r1", "auditor")).thenReturn(1);

        Map<String, Object> result = service.publish("r1", "auditor");
        assertEquals("PUBLISHED", result.get("registry_state"));
        verify(dao).promoteToPublishedIfReady("r1", "auditor");
    }

    // ── 验收 4：未知枚举字面量拒绝（PRD 冻结名）──

    @ParameterizedTest
    @ValueSource(strings = {"CM-08", "CM-00", "cm-01x", "LLM", ""})
    @DisplayName("modelTypeRejectsUnknownEnum：注册中心端点对未知/空 cognitive_model_type fail-loud 400")
    void modelTypeRejectsUnknownEnum(String raw) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("modelId", "m");
        body.put("cognitiveModelType", raw);
        // 空串走"未给出"分支 ⇒ 旧 modelType 也缺 ⇒ 同样未映射拒 400
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.registerOnRegistry(body));
        assertEquals(CognitiveErrorCode.E400_MODEL_TYPE_UNMAPPED, e.errorCode);
        assertEquals(400, e.httpStatus);
    }

    @Test
    @DisplayName("registerOnRegistry：旧值不可映射（如 ASTROLOGY）⇒ 400C，禁静默默认 CM 值")
    void unmappedLegacyTypeFailsLoud() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("modelId", "m");
        body.put("modelType", "ASTROLOGY");
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.registerOnRegistry(body));
        assertEquals(CognitiveErrorCode.E400_MODEL_TYPE_UNMAPPED, e.errorCode);
    }

    @Test
    @DisplayName("registerOnRegistry：CM-07 无旧值对应也可显式注册（枚举完整性）")
    void cm07RegistersExplicitly() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("modelId", "m");
        body.put("cognitiveModelType", "CM-07");
        when(dao.maxVersion("m")).thenReturn(0);
        when(dao.latestRowId("m")).thenReturn(null);
        when(dao.getModel("m")).thenReturn(List.of(insertedRow("r-new", "m", "CM-07", 1)));

        Map<String, Object> row = service.registerOnRegistry(body);
        assertEquals("CM-07", row.get("cognitiveModelType"));
        assertEquals("CM-07", row.get("modelType")); // 旧列无对应值 ⇒ 落 CM 字面量（Javadoc 已声明）
    }

    // ── 自设项：版本链 supersedes + 旧端点向后兼容 ──

    @Test
    @DisplayName("newVersionRowSupersedesPreviousRow：新版本行 version=max+1 且 supersedes_id 指旧行")
    void newVersionRowSupersedesPreviousRow() {
        when(dao.maxVersion("m")).thenReturn(3);
        when(dao.latestRowId("m")).thenReturn("corm_old123");
        when(dao.getModel("m")).thenReturn(List.of(insertedRow("corm_new", "m", "FORECAST", 4)));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("modelId", "m");
        body.put("modelType", "forecast");
        service.registerModel(body);

        ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> modelType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> version = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> cm = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> supersedes = ArgumentCaptor.forClass(String.class);
        verify(dao).insertModel(id.capture(), eq("m"), modelType.capture(), version.capture(),
                any(), any(), eq("active"), cm.capture(), supersedes.capture());
        assertEquals(4, version.getValue(), "version 必须 = max+1");
        assertEquals("FORECAST", modelType.getValue(), "旧列大写形态保持 PMO-51 原行为");
        assertEquals("CM-03", cm.getValue(), "FORECAST→CM-03 映射与 V261 补形段一致");
        assertEquals("corm_old123", supersedes.getValue());
    }

    @Test
    @DisplayName("legacyRegisterModelBackwardCompatible：旧端点原校验消息与默认值行为不变")
    void legacyRegisterModelBackwardCompatible() {
        Map<String, Object> noModelId = new LinkedHashMap<>();
        noModelId.put("modelType", "FORECAST");
        BusinessException e1 = assertThrows(BusinessException.class,
                () -> service.registerModel(noModelId));
        assertTrue(e1.getMessage().startsWith("CORM-400: modelId 必填"));

        Map<String, Object> badType = new LinkedHashMap<>();
        badType.put("modelId", "m");
        badType.put("modelType", "TELEPATHY");
        BusinessException e2 = assertThrows(BusinessException.class,
                () -> service.registerModel(badType));
        assertTrue(e2.getMessage().contains("非法 modelType（FORECAST/CAUSAL/SIMULATION/DECISION）"));
    }

    @Test
    @DisplayName("V261 映射表逐字复算：旧四值→CM 与补形段一致；未映射值返回 empty 不默认")
    void legacyCmMappingMatchesV261() {
        assertEquals(CognitiveModelType.CM_03, CognitiveModelType.fromLegacyModelType("FORECAST").orElseThrow());
        assertEquals(CognitiveModelType.CM_04, CognitiveModelType.fromLegacyModelType("causal").orElseThrow());
        assertEquals(CognitiveModelType.CM_05, CognitiveModelType.fromLegacyModelType("SIMULATION").orElseThrow());
        assertEquals(CognitiveModelType.CM_01, CognitiveModelType.fromLegacyModelType("DECISION").orElseThrow());
        assertTrue(CognitiveModelType.fromLegacyModelType("MYSTIC").isEmpty());
        assertTrue(CognitiveModelType.fromLegacyModelType(null).isEmpty());
    }

    // ── 自设项：门禁真实裁决（fail-closed，非 return true 桩）──

    @Test
    @DisplayName("gateEvaluationFailClosed：regression 本仓不可评测恒 PENDING；contract/backtest/calibration 按证据判")
    void gateEvaluationIsRealAndFailClosed() {
        Map<String, Object> row = draftRow();
        row.put("cognitive_model_type", null);      // 未映射 ⇒ contract FAIL
        row.put("backtest_score", null);            // 无回测证据 ⇒ backtest FAIL
        Map<String, ModelPublishGateEvaluator.GateResult> r =
                new ModelPublishGateEvaluator().evaluate(row);
        assertEquals(GateOutcome.FAIL, r.get(GateOutcome.GATE_CONTRACT).outcome());
        assertEquals(GateOutcome.FAIL, r.get(GateOutcome.GATE_BACKTEST).outcome());
        assertEquals(GateOutcome.PENDING, r.get(GateOutcome.GATE_REGRESSION).outcome(),
                "回归集载体不存在 ⇒ 必须 PENDING fail-closed，禁假绿 PASS");
        assertEquals(GateOutcome.FAIL, r.get(GateOutcome.GATE_CALIBRATION).outcome());
        // 全 PASS 判定器在 PENDING 面前必须不放行
        assertTrue(!ModelPublishGateEvaluator.allPassed(r));

        // 证据齐备行：contract PASS / backtest PASS(0.87) / calibration PASS
        Map<String, Object> good = draftRow();
        good.put("features", "{\"algorithm\":\"x\",\"calibration\":{\"method\":\"platt\",\"error\":0.03}}");
        good.put("backtest_score", new BigDecimal("0.87"));
        Map<String, ModelPublishGateEvaluator.GateResult> g =
                new ModelPublishGateEvaluator().evaluate(good);
        assertEquals(GateOutcome.PASS, g.get(GateOutcome.GATE_CONTRACT).outcome());
        assertEquals(GateOutcome.PASS, g.get(GateOutcome.GATE_BACKTEST).outcome());
        assertEquals(GateOutcome.PASS, g.get(GateOutcome.GATE_CALIBRATION).outcome());
        assertEquals(GateOutcome.PENDING, g.get(GateOutcome.GATE_REGRESSION).outcome());
    }

    // ── 自设项：哈希规范化稳定性 ──

    @Test
    @DisplayName("contentHashCanonicalizationIgnoresFieldOrder：同内容不同字段序/尾零/Map 迭代序 ⇒ 同哈希；异内容 ⇒ 异哈希")
    void contentHashCanonicalizationIgnoresFieldOrder() {
        Map<String, Object> a = draftRow();
        a.put("features", "{\"b\":2,\"a\":1,\"nested\":{\"y\":true,\"x\":null}}");
        a.put("backtest_score", new BigDecimal("0.50"));
        Map<String, Object> b = draftRow();
        b.put("features", "{\"nested\":{\"x\":null,\"y\":true},\"a\":1,\"b\":2}");
        b.put("backtest_score", new BigDecimal("0.5"));
        // Map 自身迭代序反转对照
        Map<String, Object> c = new LinkedHashMap<>();
        for (String k : List.of("backtest_score", "features", "status", "version",
                "cognitive_model_type", "model_type", "model_id", "id")) {
            c.put(k, a.get(k));
        }

        String ha = ModelContentHasher.sha256Hex(a);
        assertEquals(64, ha.length(), "SHA-256 hex 定长 64（V261 content_hash VARCHAR(64)）");
        assertEquals(ha, ModelContentHasher.sha256Hex(b), "JSON 键序/数值尾零差异不得影响哈希");
        assertEquals(ha, ModelContentHasher.sha256Hex(c), "行 Map 迭代序不得影响哈希");

        Map<String, Object> d = new LinkedHashMap<>(a);
        d.put("features", "{\"b\":3,\"a\":1}");
        assertNotEquals(ha, ModelContentHasher.sha256Hex(d), "内容差异必须换哈希");
    }

    @Test
    @DisplayName("contentHashFreezeOnDraft：DRAFT 行哈希冻结走 DAO 守卫写；0 行受影响 ⇒ 409（禁静默）")
    void contentHashFreezeOnDraft() {
        Map<String, Object> withHash = publishableDraftRow();
        when(dao.selectRowById("r1")).thenReturn(draftRow(), withHash);
        when(dao.updateContentHashIfDraft(eq("r1"), anyString())).thenReturn(1);

        Map<String, Object> result = service.computeAndFreezeContentHash("r1");
        assertEquals("a".repeat(64), result.get("content_hash"));

        // 竞态：DRAFT 判定后行已被升格 ⇒ DAO 守卫 0 行 ⇒ 显式 409
        when(dao.selectRowById("r2")).thenReturn(draftRow());
        when(dao.updateContentHashIfDraft(eq("r2"), anyString())).thenReturn(0);
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.computeAndFreezeContentHash("r2"));
        assertEquals(CognitiveErrorCode.E409_MODEL_IMMUTABLE, e.errorCode);
    }

    @Test
    @DisplayName("gateRecordOnDraft：门禁裁决落列（regression 恒 PENDING ⇒ 升格必被 422 拒，fail-closed 端到端）")
    void gateRecordOnDraftKeepsPublishFailClosed() {
        Map<String, Object> evaluated = publishableDraftRow();
        evaluated.put("gate_regression", "PENDING");
        when(dao.selectRowById("r1")).thenReturn(draftRow(), evaluated);
        when(dao.updateGatesIfDraft(eq("r1"), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1);

        Map<String, Object> gates = service.evaluateAndRecordGates("r1");
        assertEquals(Boolean.FALSE, gates.get("allPassed"),
                "regression 不可评测 ⇒ allPassed 必为 false（禁假绿）");

        // 即使操作者强推发布：库内留痕 regression=PENDING ⇒ 422，升格 SQL 不发
        when(dao.selectRowById("r1")).thenReturn(evaluated);
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.publish("r1", "auditor"));
        assertEquals(CognitiveErrorCode.E422_GATES_NOT_PASSED, e.errorCode);
        verify(dao, never()).promoteToPublishedIfReady(anyString(), anyString());
    }

    // ── fixtures ──────────────────────────────────────────

    private static Map<String, Object> draftRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "r1");
        row.put("model_id", "m");
        row.put("model_type", "FORECAST");
        row.put("version", 2);
        row.put("features", "{\"algorithm\":\"moving-average\"}");
        row.put("backtest_score", new BigDecimal("0.91"));
        row.put("status", "active");
        row.put("cognitive_model_type", "CM-03");
        row.put("registry_state", RegistryState.DRAFT.name());
        row.put("content_hash", null);
        row.put("supersedes_id", "r0");
        row.put("gate_contract", "PENDING");
        row.put("gate_regression", "PENDING");
        row.put("gate_backtest", "PENDING");
        row.put("gate_calibration", "PENDING");
        return row;
    }

    private static Map<String, Object> publishableDraftRow() {
        Map<String, Object> row = draftRow();
        row.put("content_hash", "a".repeat(64));
        row.put("gate_contract", "PASS");
        row.put("gate_regression", "PASS");   // 门禁列存证 PASS（升格判据读库内留痕，见类 Javadoc）
        row.put("gate_backtest", "PASS");
        row.put("gate_calibration", "PASS");
        return row;
    }

    private static Map<String, Object> insertedRow(String id, String modelId, String modelType, int version) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("model_id", modelId);
        row.put("model_type", modelType);
        row.put("version", version);
        row.put("status", "active");
        row.put("registry_state", "DRAFT");
        row.put("supersedes_id", null);
        row.put("content_hash", null);
        row.put("cognitive_model_type", "CM-03");
        return row;
    }
}
