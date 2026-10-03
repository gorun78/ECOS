package com.chinacreator.gzcm.engine.data.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F02-04 (W57) —— 节点目录 ↔ 执行器能力一致性。
 *
 * <p><b>红线语义</b>（详细设计-02 §F02-04）：
 * 目录（{@link PipelineNodeTypesCatalog#SUPPORTED}）与执行器 switch 分支
 * 必须同源（铁律 §4.8.2「枚举边界一致」）。此处以目录为单一权威（Wave 4 收敛），
 * 断言：
 * <ol>
 *   <li>{@code SUPPORTED} 与 {@code Info} 内登记的 12 类集合<b>相等</b>——
 *   任何新增/删除一条目录条目忘记更新 SUPPORTED（或反之）都会 FAIL；</li>
 *   <li>{@code SOURCE_CDC} 是目录内 <b>唯一</b> {@code available=false} 项——
 *   新增/恢复可用必须同时改回 {@code ofAvailable}（否则本测试会 FAIL）；</li>
 *   <li>目录版本号 {@link PipelineNodeTypesCatalog#CATALOG_VERSION} 非空（
 *   GET /api/v1/pipeline/node-types 同源）。</li>
 * </ol>
 *
 * <p><b>为何不用字节码扫描执行器 switch</b>：
 * 引擎侧 {@code PipelineExecutionEngine.executeStep} 的 switch 分支
 * 是 legacy YAML 执行链（F02-03 已 {@code @Deprecated}），
 * 新链走 {@code PipelineExecutionService} 12 类型 executor 集合。
 * 目录是<b>唯一权威来源</b>（前端 NodePalette 与后端 saveValidate 同源），
 * 本测试断言目录内部一致性即可保证「不允许 UI 拖出必败节点」（W57）。
 */
@DisplayName("F02-04 节点目录与能力一致性")
class NodeCatalogParityTest {

    /** 反射 Info 表（不通过 API 直接暴露，避免生产误改）。 */
    @SuppressWarnings("unchecked")
    private static java.util.Map<String, PipelineNodeTypesCatalog.NodeTypeInfo> infoTable()
            throws ReflectiveOperationException {
        Field f = PipelineNodeTypesCatalog.class.getDeclaredField("INFO");
        f.setAccessible(true);
        return (java.util.Map<String, PipelineNodeTypesCatalog.NodeTypeInfo>) f.get(null);
    }

    @Test
    @DisplayName("SUPPORTED 与 Info 表登记的类型集合相等（新增类型必须双向登记）")
    void supportedMatchesInfoKeys() throws ReflectiveOperationException {
        java.util.Map<String, PipelineNodeTypesCatalog.NodeTypeInfo> info = infoTable();
        Set<String> infoKeys = info.keySet();
        Set<String> supported = PipelineNodeTypesCatalog.SUPPORTED;

        assertEquals(supported, infoKeys,
                "F02-04 违反：目录 SUPPORTED 与 Info 登记的节点类型不一致。\n"
                        + "SUPPORTED 独有 = " + diff(supported, infoKeys)
                        + "\nInfo 独有    = " + diff(infoKeys, supported)
                        + "（铁律 §4.8.2 枚举边界一致：新增节点类型必须双向登记，"
                        + "否则 create 拒与 UI 启灰会漂移）");

        // 12 类固定基线（源对源防漏）
        assertEquals(12, supported.size(),
                "F02-04：SUPPORTED 节点类型基线固定 12 类；实际 " + supported.size());
    }

    @Test
    @DisplayName("SOURCE_CDC 是目录内唯一 available=false 项（W57 用户不可拖出必败节点）")
    void sourceCdcIsOnlyUnavailable() throws ReflectiveOperationException {
        java.util.Map<String, PipelineNodeTypesCatalog.NodeTypeInfo> info = infoTable();
        Set<String> unavailable = info.entrySet().stream()
                .filter(e -> !e.getValue().available())
                .map(java.util.Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertEquals(Set.of("SOURCE_CDC"), unavailable,
                "F02-04：目录中 available=false 的节点类型必须仅 SOURCE_CDC；"
                        + "实际 " + unavailable + "。新增不可用节点或误标可用都会破坏 W57 用户侧保护");
    }

    @Test
    @DisplayName("CATEGORY 中的每个条目都有非空原因（不可用条目 unavailableReason 必填）")
    void unavailableTypesHaveReason() throws ReflectiveOperationException {
        java.util.Map<String, PipelineNodeTypesCatalog.NodeTypeInfo> info = infoTable();
        for (var e : info.entrySet()) {
            PipelineNodeTypesCatalog.NodeTypeInfo t = e.getValue();
            if (!t.available()) {
                assertFalse(t.unavailableReason() == null || t.unavailableReason().isBlank(),
                        "F02-04：不可用节点类型 " + t.type() + " 必须给出非空 unavailableReason（前端灰显文案）");
            }
        }
        assertTrue(info.containsKey("SOURCE_CDC"), "F02-04：SOURCE_CDC 必须登记于目录");
    }

    @Test
    @DisplayName("目录版本号非空（GET /api/v1/pipeline/node-types 同源）")
    void catalogVersionIsPopulated() {
        assertTrue(PipelineNodeTypesCatalog.CATALOG_VERSION != null
                        && !PipelineNodeTypesCatalog.CATALOG_VERSION.isBlank(),
                "F02-04：CATALOG_VERSION 必须非空；前端 NodePalette 断点续做依赖");
    }

    private static Set<String> diff(Set<String> a, Set<String> b) {
        Set<String> out = new LinkedHashSet<>(a);
        out.removeAll(b);
        return out;
    }
}
