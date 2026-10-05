package com.chinacreator.gzcm.workspace.fc;

import com.chinacreator.gzcm.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分册09 §七 W228/C210 · SUCCEEDED 只读不可篡改（FC-03 §3.2 / C210 应用层红线）。
 *
 * <p>验收标识：{@code mvn -Dtest=ForecastRunImmutabilityTest#updateAfterSucceededIsRejected}。</p>
 *
 * <p>本批次语义部署：{@code FcRunState.transition} 承载应用层护栏——run 进入 SUCCEEDED/FAILED
 * 后任何 retry 一律 reject（{@code IllegalStateException} 逃逸到 controller → {@code BusinessException}）；
 * 生产链路上的物理拒改由 {@code ecos_dw.ecos_fc_run} 的 status 列 + 唯一约束配合保证。</p>
 */
class ForecastRunImmutabilityTest {

    private final FcRunState state = new FcRunState();

    private String put(String status) {
        String id = FcRunState.newUuid();
        state.put(new FcRunState.RunRow(id,
                Map.of("caliberId", "R6", "caliberVersion", "v1", "asOfTime", "2026-10-01",
                        "snapshotId", "snap-1", "formulaVersion", "f1"),
                status, false, null, null,
                "sh", "oh", "rk-" + status, null, null,
                new ArrayList<>(), Map.of("status", status)));
        return id;
    }

    @Test
    @DisplayName("§七 W228/C210 · SUCCEEDED run 再 retry 必拒（应用层红线）")
    void updateAfterSucceededIsRejected() {
        String id = put(FcRunState.SUCCEEDED);
        BusinessException e = assertThrows(BusinessException.class,
                () -> new FcForecastRunController(state).retry(id));
        assertTrue(e.getMessage().contains("C210"),
                "错误消息应点名 C210: " + e.getMessage());
    }

    @Test
    @DisplayName("FAILED 也是 terminal：同样拒 retry")
    void failedRunAlsoImmutable() {
        String id = put(FcRunState.FAILED);
        BusinessException e = assertThrows(BusinessException.class,
                () -> new FcForecastRunController(state).retry(id));
        assertTrue(e.getMessage().contains("C210"));
    }

    @Test
    @DisplayName("FcRunState.transition 层直接保护：terminal run 当前任何 target 都拒")
    void terminalTransitionRejectedAtStateLayer() {
        String idS = put(FcRunState.SUCCEEDED);
        IllegalStateException e1 = assertThrows(IllegalStateException.class,
                () -> state.transition(idS, FcRunState.RUNNING));
        assertTrue(e1.getMessage().contains("C210"));

        String idF = put(FcRunState.FAILED);
        assertThrows(IllegalStateException.class,
                () -> state.transition(idF, FcRunState.DATA_CHECK));
    }

    @Test
    @DisplayName("非 terminal run 允许 transition（护栏不误伤编排路径）")
    void nonTerminalRunTransitionsAllowed() {
        String id = put(FcRunState.DATA_CHECK);
        // 允许：非 terminal → 任何 target；本批次 harness 只做 guard，允许通过
        state.transition(id, FcRunState.SNAPSHOT_FROZEN);
        // 本 run 语义上仍停在 DATA_CHECK（guard-only），再一次 transition 也不该拒
        state.transition(id, FcRunState.RUNNING);
    }
}
