package com.chinacreator.gzcm.workspace.fc;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分册09 §七 W238/C220 · 导出走 SEC-01 同管道（RLS/CLS/脱敏），禁旁路（README §4.2 尾部）。
 *
 * <p>验收标识：{@code mvn -Dtest=ForecastExportChannelTest#csvUsesSameRlsPipelineAsQuery}。</p>
 *
 * <p>验收红线：</p>
 * <ul>
 *   <li>{@code GET /forecast-runs/{runId}/export?format=csv} 走 security-engine 三通道（RLS/CLS/脱敏），
 *       本层<b>不本地拼 CSV</b>、不缓存金额</li>
 *   <li>响应体只携带路由提示 + 三通道标记，禁止出现 "csv body" / "bytes" / 任何 data payload</li>
 * </ul>
 */
class ForecastExportChannelTest {

    private final FcRunState state = new FcRunState();
    private final FcForecastRunController controller = new FcForecastRunController(state);

    private String succeedRun() {
        String id = FcRunState.newUuid();
        state.put(new FcRunState.RunRow(id,
                Map.of("caliberId", "R6", "caliberVersion", "v1", "asOfTime", "2026-10-01",
                        "snapshotId", "snap-1", "formulaVersion", "f1"),
                FcRunState.SUCCEEDED, false, null, null,
                "sh", "oh", "rk-x", null, null,
                new ArrayList<>(), Map.of("events", new ArrayList<>())));
        return id;
    }

    @Test
    @DisplayName("§七 W238/C220 · export 响应必带 SEC-01 routing 提示，禁本地拼 CSV")
    void csvUsesSameRlsPipelineAsQuery() {
        String id = succeedRun();
        ApiResponse<Map<String, Object>> resp = controller.export(id, "csv");
        Map<String, Object> body = resp.getData();
        assertNotNull(body);
        // 响应体必含 routing / RLS 提示，绝无数据 payload
        String routingNote = String.valueOf(body.get("routingNote"));
        assertTrue(routingNote.contains("SEC-01"), "routing 应指向 SEC-01 同管道: " + routingNote);
        assertTrue(routingNote.contains("RLS/CLS/脱敏"), "routing 应显式带 RLS/CLS/脱敏三通道: " + routingNote);
        // 反锚点：绝对不能有数据 payload 字段（本地拼 CSV 就露馅了）
        assertTrue(!body.containsKey("csvBody"), "C220 拒：export 不得携带本地拼的 CSV body");
        assertTrue(!body.containsKey("bytes"), "C220 拒：export 不得携带 bytes payload");
        assertTrue(!body.containsKey("content"), "C220 拒：export 不得携带 content payload");
    }

    @Test
    @DisplayName("format=csv 默认值：未传 format 时应回 'csv'")
    void defaultsToCsv() {
        String id = succeedRun();
        ApiResponse<Map<String, Object>> resp = controller.export(id, null);
        assertEquals("csv", String.valueOf(resp.getData().get("format")));
    }
}
