package com.chinacreator.gzcm.engine.cognitive2.guard;

import com.chinacreator.gzcm.engine.cognitive2.dto.HypothesisSaveDTO;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveAuditFallback;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveHypothesisService;
import com.chinacreator.gzcm.engine.cognitive2.service.EvidenceStore;
import com.chinacreator.gzcm.engine.cognitive2.service.HypothesisStore;
import com.chinacreator.gzcm.engine.cognitive2.service.mental.MentalEventPublisher;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F05-15 X-39 验收：认知域审计写入 fail-closed——禁
 * {@code try { audit(...) } catch (Exception) { log.warn(...) }} 静默续行（无 rethrow / 无拒绝）。
 *
 * <p>两层验收：
 * <ol>
 *   <li>行为冻结名 {@code auditSinkFailureRejectsAction}（Mockito）：主审计通道（EventBusService）
 *       抛错且本地兜底（{@link CognitiveAuditFallback}）也失败 → 业务动作必须被拒绝（抛
 *       {@link BusinessException}），杜绝 log-warn 续行；</li>
 *   <li>M0 契约面源码扫描：范围 = {@link CogScope#inScope}，禁审计 fail-open 形状。</li>
 * </ol>
 */
class CognitiveAuditFailClosedTest {

    private static final Pattern AUDIT_FAIL_OPEN = Pattern.compile(
            "try\\s*\\{[^{}]*\\baudit\\b[^{}]*\\}\\s*catch\\s*\\(\\s*(?:Exception|Throwable)[^)]*\\)\\s*\\{[^}]*\\}",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HAS_RETHROW = Pattern.compile(
            "throw\\s+(?:new\\s+\\w+|\\w+)", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("X-39 行为面：审计 sink 异常 + 兜底失败 → register 动作被拒绝（fail-closed）")
    void auditSinkFailureRejectsAction() throws Exception {
        HypothesisStore store = mock(HypothesisStore.class);
        EvidenceStore evidenceStore = mock(EvidenceStore.class);
        MentalEventPublisher eventPublisher = mock(MentalEventPublisher.class);
        CognitiveAuditFallback fallback = mock(CognitiveAuditFallback.class);
        EventBusService eventBus = mock(EventBusService.class);

        // 主通道：publish 抛错（Kafka 不可用形态）
        doThrow(new RuntimeException("kafka unavailable"))
                .when(eventBus).publish(anyString(), any());
        // 兜底通道：本地落盘也失败（磁盘不可写形态）
        doThrow(new IOException("disk full")).when(fallback).write(anyMap());

        CognitiveHypothesisService service = new CognitiveHypothesisService(
                store, evidenceStore, new ObjectMapper(), eventPublisher, fallback);
        // eventBusService 为可选装配字段（历史 baseline），测试用反射注入故障 sink
        ReflectionTestUtils.setField(service, "eventBusService", eventBus);

        when(store.existsByCode("H-FCC-1")).thenReturn(false);

        HypothesisSaveDTO dto = new HypothesisSaveDTO();
        dto.setHypothesisCode("H-FCC-1");
        dto.setStatement("fail-closed 验收假设");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.register(dto),
                "审计双通道均失败时必须拒绝业务动作（X-39 fail-closed），禁止静默放行");
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("COG-503"),
                "拒绝异常应为 503 COG-503 审计不可记录语义，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("场景认知契约面审计 sink fail → 无 log-warn 静默续行")
    void contractSurfaceHasNoAuditFailOpen() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (var walk = CogScope.streamInScope()) {
            walk.forEach(p -> {
                String s;
                try { s = Files.readString(p); } catch (IOException e) { return; }
                var m = AUDIT_FAIL_OPEN.matcher(s);
                while (m.find()) {
                    if (!HAS_RETHROW.matcher(m.group(0)).find()) {
                        offenders.add(p + " @line " + CogScope.lineOf(s, m.start()));
                    }
                }
            });
        }
        assertTrue(offenders.isEmpty(),
                "场景认知契约面存在审计 fail-open（X-39）：\n" + String.join("\n", offenders));
    }
}
