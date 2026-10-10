package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.PolicyEffect;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.StepType;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunStepDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunStepDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Step 留痕行引用型纪律（详细设计-10 F10-14 / §6.3，冻结验收名）。
 *
 * <p>V234 行只存 {@code *_ref_text}（确定性规范 JSON 快照）+ {@code *_hash}（sha256 实列），
 * 无独立正文字段；审批凭证 token 正文永不入行（只落 sha256 引用面）。</p>
 */
class StepRecorderTest {

    private static WAgentRunDO run(String id, int ceiling) {
        WAgentRunDO run = new WAgentRunDO();
        run.setId(id);
        run.setTenantId("t1");
        run.setOrgId("o1");
        run.setAutomationCeiling(ceiling);
        return run;
    }

    private static WAgentPlan.Step toolStep(String key, int level) {
        return new WAgentPlan.Step(key, StepType.TOOL.value(), "query_project_profit", "1",
                level, List.of(), Map.of(), false, false, null);
    }

    @Test
    @DisplayName("F10-14/§6.3：留痕行=引用+哈希面——input/output 只落 ref_text+sha256，行内无正文字段、无 token 正文")
    void stepRowContainsRefsAndHashOnly() {
        WAgentRunStepDao stepDao = mock(WAgentRunStepDao.class);
        when(stepDao.findByRunStepAttempt(any(), any(), any())).thenReturn(Optional.empty());
        StepWriter writer = new StepWriter(stepDao);

        // 输入带嵌套结构与非字典序插入序，检验规范形（key 递归排序、迭代序不参与产物）
        Map<String, Object> nestedInner = new LinkedHashMap<>();
        nestedInner.put("zeta", 1);
        nestedInner.put("alpha", "正文密级内容-A");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("project", nestedInner);
        input.put("asOfDate", "2026-09-30");

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("profit", 123);
        output.put("breakdown", Map.of("b", 2, "a", 1));

        WAgentRunStepDO row = writer.write(new StepWriter.StepWriteRequest(
                run("r1", 1), toolStep("s1", 1), 0,
                WAgentStatus.StepStatus.SUCCEEDED.value(), input, output,
                PolicyEffect.ALLOW, "r-allow", null, null, null, null, null, "u1"));

        ArgumentCaptor<WAgentRunStepDO> cap = ArgumentCaptor.forClass(WAgentRunStepDO.class);
        verify(stepDao).insert(cap.capture());
        WAgentRunStepDO persisted = cap.getValue();

        // 引用列 = 确定性规范 JSON；哈希列 = 对该引用串实算的 sha256（引用与哈希互证）
        String canonicalInput = writer.canonicalJson(input);
        assertEquals(canonicalInput, persisted.getInputRefText());
        assertEquals(WAgentHashSupport.sha256Hex(canonicalInput), persisted.getInputHash());
        assertEquals(StepWriter.HASH_LENGTH, persisted.getInputHash().length());
        assertTrue(persisted.getInputHash().matches("[0-9a-f]{64}"));
        assertEquals(WAgentHashSupport.sha256Hex(persisted.getOutputRefText()),
                persisted.getOutputHash());
        assertEquals(StepWriter.HASH_LENGTH, persisted.getOutputHash().length());

        // 规范形键序：嵌套对象按字典序重排（"alpha" 先于 "zeta"、"a" 先于 "b"）
        assertTrue(persisted.getInputRefText().indexOf("\"alpha\"")
                < persisted.getInputRefText().indexOf("\"zeta\""));
        assertTrue(persisted.getOutputRefText().indexOf("\"a\"")
                < persisted.getOutputRefText().indexOf("\"b\""));
        assertEquals(row.getId(), persisted.getId());

        // 行形态纪律：DO 无正文/内容类字段（引用+哈希是唯一 payload 面）
        for (Field f : WAgentRunStepDO.class.getDeclaredFields()) {
            String name = f.getName().toLowerCase(java.util.Locale.ROOT);
            assertFalse(name.contains("content") || name.contains("body") || name.contains("approvaltoken"),
                    "V234 行不得出现正文/凭证列: " + f.getName());
        }

        // L3 审批凭证：token 正文不落行（只允许引用面存在），null 输出 ⇒ 双列皆 null
        WAgentRunStepDO l3Row = writer.write(new StepWriter.StepWriteRequest(
                run("r2", 3), toolStep("s9", 3), 0,
                WAgentStatus.StepStatus.SUCCEEDED.value(), Map.of("k", 1), null,
                PolicyEffect.ALLOW, "r-allow", "SECRET-TOKEN-正文", null, null, null, null, "u1"));
        for (Field f : WAgentRunStepDO.class.getDeclaredFields()) {
            f.setAccessible(true);
            try {
                Object v = f.get(l3Row);
                assertFalse(v instanceof String && ((String) v).contains("SECRET-TOKEN-正文"),
                        "token 正文禁入列: " + f.getName());
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
        assertNull(l3Row.getOutputRefText());
        assertNull(l3Row.getOutputHash());
    }
}
