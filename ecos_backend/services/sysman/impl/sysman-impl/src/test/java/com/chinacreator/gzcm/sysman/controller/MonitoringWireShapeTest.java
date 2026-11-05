package com.chinacreator.gzcm.sysman.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 运行监控面 wire 键面保真门（#147CL · DoD DI-2 第十六批）。
 *
 * <p><b>钉的是什么</b>：仪表盘 4 键；指标 5 键 / 曲线点 4 键 / 进程 5 键 / 告警 4 键；
 * 自检 3 键与条目 3 键；列表长度（6 指标、6 点、3 进程、2 告警、3 自检项）与 summary 文案前缀。
 * 数据取值为运行态真实值，本门只钉结构与文案口径，不钉具体数值。</p>
 *
 * <p><b>变异自证</b>：把 {@link MonitoringController.MetricVO#value} 改名 ⇒ 指标键集红。</p>
 */
class MonitoringWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return MAPPER.convertValue(value, Map.class);
    }

    private static List<String> keysOf(Object value) {
        return new ArrayList<>(asMap(value).keySet());
    }

    @Test
    @DisplayName("仪表盘：4 键；指标/曲线点/进程/告警键面与条数")
    void dashboardShape() {
        MonitoringController controller = new MonitoringController();

        MonitoringController.DashboardVO data = controller.dashboard().getData();
        assertEquals(List.of("systemMetrics", "chartData", "processes", "alerts"), keysOf(data));

        assertEquals(6, data.systemMetrics().size());
        assertEquals(List.of("label", "value", "desc", "icon", "color"),
                keysOf(data.systemMetrics().get(0)));

        assertEquals(6, data.chartData().size());
        assertEquals(List.of("time", "cpu", "memory", "requests"), keysOf(data.chartData().get(0)));

        assertEquals(3, data.processes().size());
        assertEquals(List.of("name", "status", "pid", "cpu", "memory"),
                keysOf(data.processes().get(0)));
        assertEquals("SysManApplication", data.processes().get(0).name());

        assertEquals(2, data.alerts().size());
        assertEquals(List.of("id", "message", "type", "time"), keysOf(data.alerts().get(0)));
    }

    @Test
    @DisplayName("自检：3 键；条目 3 键；summary 前缀与三条腿齐")
    void diagnosticsShape() {
        MonitoringController controller = new MonitoringController();

        MonitoringController.DiagnosticsVO data = controller.diagnostics().getData();
        assertEquals(List.of("summary", "items", "timestamp"), keysOf(data));
        assertTrue(data.summary().startsWith("系统诊断完成："), "summary 文案前缀不得漂");
        assertEquals(3, data.items().size(), "数据库/JVM/文件系统三条腿必须齐");
        assertEquals(List.of("name", "status", "detail"), keysOf(data.items().get(0)));
        assertEquals("数据库连接", data.items().get(0).name());
        assertEquals("JVM状态", data.items().get(1).name());
        assertEquals("文件系统", data.items().get(2).name());
    }
}
