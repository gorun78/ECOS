package com.chinacreator.gzcm.workspace.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F07-03-2（必需边契约探测）门禁。
 *
 * <p>{@link LinkContractProbe} 是最小本地探针，判定一条必需边 present 的必要条件：
 * 两端类型均已绑定 + 存在该类型连边 + 契约校验通过（MAPPING 边要求 source_contract
 * 非空且非 {@code placeholder-} 前缀，破 X-48 占位契约骗画布）。本测试重点锁
 * <b>C-1 步骤 4 / 验收 probeFailureMarksEdgeMissingNotError</b>：探测失败一律降级为
 * missing（present=false），既不算 present 也不抛错让端点 500。</p>
 */
class EdgeContractProbeTest {

    private final EdgeContractProbe probe = new LinkContractProbe();

    private static EdgeContractProbe.BindingRef b(String id, String type) {
        return new EdgeContractProbe.BindingRef(id, type, null);
    }

    private static EdgeContractProbe.LinkRef link(String s, String t, String type, String contract) {
        return new EdgeContractProbe.LinkRef(s, t, type, contract);
    }

    private static final RequiredEdgePolicy.RequiredEdge MAPPING =
            new RequiredEdgePolicy.RequiredEdge("MAPPING", "DATASET", "OBJECT_TYPE", "label");

    @Test
    @DisplayName("两端绑定且存在带真实契约的连边 → present=true 且统计命中连边数")
    void presentWhenBothEndsBoundAndRealContractLinkExists() {
        List<EdgeContractProbe.BindingRef> bindings = List.of(
                b("d1", "DATASET"), b("o1", "OBJECT_TYPE"));
        List<EdgeContractProbe.LinkRef> links = List.of(
                link("d1", "o1", "MAPPING", "contract-abc"));
        EdgeContractProbe.EdgePresence p = probe.probe(MAPPING, bindings, links);

        assertTrue(p.present());
        assertEquals(1, p.contractCount());
        assertNull(p.probeError());
    }

    @Test
    @DisplayName("X-48 防线: source_contract 为占位前缀 → 该连边不算 present")
    void mappingEdgeWithPlaceholderContractIsNotPresent() {
        List<EdgeContractProbe.BindingRef> bindings = List.of(
                b("d1", "DATASET"), b("o1", "OBJECT_TYPE"));
        List<EdgeContractProbe.LinkRef> links = List.of(
                link("d1", "o1", "MAPPING", "placeholder-sc001-..."));
        EdgeContractProbe.EdgePresence p = probe.probe(MAPPING, bindings, links);

        assertFalse(p.present());
        assertEquals(0, p.contractCount());
    }

    @Test
    @DisplayName("X-48 防线: source_contract 为空 → MAPPING 边不算 present（缺失而非错误）")
    void mappingEdgeWithBlankContractIsMissingNotError() {
        List<EdgeContractProbe.BindingRef> bindings = List.of(
                b("d1", "DATASET"), b("o1", "OBJECT_TYPE"));
        List<EdgeContractProbe.LinkRef> links = List.of(
                link("d1", "o1", "MAPPING", "  "));
        EdgeContractProbe.EdgePresence p = probe.probe(MAPPING, bindings, links);

        assertFalse(p.present());
        assertEquals(0, p.contractCount());
        // 语义上是"边缺失"而非"探测出错"：probeError 应为空
        assertNull(p.probeError());
    }

    @Test
    @DisplayName("C-1 步骤 4: 一端类型未绑定 → missing 且不抛异常（不可 present、不可 500）")
    void missingWhenOneEndpointTypeUnbound() {
        // 只有 DATASET 端，没有 OBJECT_TYPE 端
        List<EdgeContractProbe.BindingRef> bindings = List.of(b("d1", "DATASET"));
        List<EdgeContractProbe.LinkRef> links = List.of(
                link("d1", "o1", "MAPPING", "contract-abc"));
        EdgeContractProbe.EdgePresence p = probe.probe(MAPPING, bindings, links);

        assertFalse(p.present());
        assertEquals(0, p.contractCount());
    }

    @Test
    @DisplayName("菱形类型不符: 连边存在但起/终点类型不合规范 → 不算 present")
    void linkWithWrongEndpointTypesDoesNotCount() {
        // 规范 MAPPING = DATASET → OBJECT_TYPE；此连边是 DATASET → KNOWLEDGE_BASE
        List<EdgeContractProbe.BindingRef> bindings = List.of(
                b("d1", "DATASET"), b("k1", "KNOWLEDGE_BASE"), b("o1", "OBJECT_TYPE"));
        List<EdgeContractProbe.LinkRef> links = List.of(
                link("d1", "k1", "MAPPING", "contract-abc"));
        EdgeContractProbe.EdgePresence p = probe.probe(MAPPING, bindings, links);

        assertFalse(p.present());
        assertEquals(0, p.contractCount());
    }

    @Test
    @DisplayName("端点名反查: 端点类型不可匹配仅因类型值，而非大小写 —— 契约侧要求边类型严格区分")
    void probeIsNotCrashedByExtraUnrelatedBindings() {
        List<EdgeContractProbe.BindingRef> bindings = List.of(
                b("d1", "DATASET"), b("o1", "OBJECT_TYPE"),
                b("k1", "KNOWLEDGE_BASE"), b("a1", "AI_AGENT"));
        List<EdgeContractProbe.LinkRef> links = List.of(
                link("d1", "o1", "MAPPING", "contract-abc"));
        EdgeContractProbe.EdgePresence p = probe.probe(MAPPING, bindings, links);

        assertTrue(p.present());
        assertNotNull(p);
    }

    @Test
    @DisplayName("多条有效连边全部计入 contractCount，占位不计")
    void multipleValidLinksAllCounted() {
        List<EdgeContractProbe.BindingRef> bindings = List.of(
                b("d1", "DATASET"), b("d2", "DATASET"), b("o1", "OBJECT_TYPE"));
        List<EdgeContractProbe.LinkRef> links = List.of(
                link("d1", "o1", "MAPPING", "c1"),
                link("d2", "o1", "MAPPING", "c2"),
                link("d1", "o1", "MAPPING", "placeholder-x")); // 第 3 条占位不计
        EdgeContractProbe.EdgePresence p = probe.probe(MAPPING, bindings, links);

        assertTrue(p.present());
        assertEquals(2, p.contractCount());
    }

    @Test
    @DisplayName("连边引用了不存在的绑定 id → 该连边跳过、不 NPE")
    void danglingLinkEndpointIsSkippedNotNpe() {
        List<EdgeContractProbe.BindingRef> bindings = List.of(
                b("d1", "DATASET"), b("o1", "OBJECT_TYPE"));
        // 指向 ghost 绑定 id
        List<EdgeContractProbe.LinkRef> links = List.of(
                link("d1", "ghost", "MAPPING", "contract-abc"));
        EdgeContractProbe.EdgePresence p = probe.probe(MAPPING, bindings, links);

        assertFalse(p.present());
        assertEquals(0, p.contractCount());
    }

    @Test
    @DisplayName("空连边集合 → missing（不抛、不错）")
    void noLinksAtAllIsMissing() {
        List<EdgeContractProbe.BindingRef> bindings = List.of(
                b("d1", "DATASET"), b("o1", "OBJECT_TYPE"));
        EdgeContractProbe.EdgePresence p = probe.probe(MAPPING, bindings, List.of());

        assertFalse(p.present());
        assertEquals(0, p.contractCount());
        assertNull(p.probeError());
    }
}
