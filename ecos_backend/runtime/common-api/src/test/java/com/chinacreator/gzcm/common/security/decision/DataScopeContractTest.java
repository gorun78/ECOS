package com.chinacreator.gzcm.common.security.decision;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DataScopeContract} 单源读数/写出契约（#142/B4-3d-3，差距 D-B4-20）。
 *
 * <p><b>为什么测试里允许出现键名字面量</b>：仓内其它用例的纪律是"期望值来自单源"，
 * 但本类断言的正是<b>单源本身的字面量取值</b> —— 若期望值也从
 * {@link DataScopeContract} 取，那把常量从 {@code "projectIds"} 改成
 * {@code "project_ids"} 就测不出来（生产者与断言同步漂移＝假绿）。
 * 这里的字面量是<b>独立见证</b>，与策略侧 {@code data_access.rego} 的
 * {@code object.get(data_scope, "projectIds", [])} 逐字对照。</p>
 */
class DataScopeContractTest {

    private static Map<String, Object> scope(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private static Map<String, Object> resource(Object dataScope) {
        return scope("dataScope", dataScope);
    }

    @Test
    @DisplayName("键面取值逐字钉：与 data_access.rego 读取的四个键名一致")
    void keyLiteralsMatchPolicySide() {
        assertEquals("resource", DataScopeContract.KEY_RESOURCE);
        assertEquals("dataScope", DataScopeContract.KEY_DATA_SCOPE);
        assertEquals("departmentId", DataScopeContract.KEY_DEPARTMENT_ID);
        assertEquals("projectIds", DataScopeContract.KEY_PROJECT_IDS);
    }

    @Test
    @DisplayName("read — 正常声明域两键都读到，顺序无关")
    void readParsesBothDeclaredKeys() {
        DataScopeContract.Declared d = DataScopeContract.read(
                resource(scope("projectIds", List.of("p1", "p2"), "departmentId", "d01")));

        assertEquals("d01", d.departmentId());
        assertEquals(List.of("p1", "p2"), d.projectIds());
        assertTrue(d.hasDepartment());
        assertTrue(d.hasProjectScope());
        assertFalse(d.empty());
    }

    @Test
    @DisplayName("read — 外部输入只白名单化不转换：非文本/非列表/null 元素一律丢弃")
    void readIsTypeSafeAndNeverInventsValues() {
        // departmentId=123（数字）⇒ 未声明；projectIds 非 List ⇒ 空；混入 null/空白 ⇒ 丢弃
        DataScopeContract.Declared d = DataScopeContract.read(resource(scope(
                "departmentId", 123,
                "projectIds", "p1",
                "extra", "ignored")));
        assertNull(d.departmentId(), "把 123 读成 \"123\" 等于替调用方伪造一个它没声明的域");
        assertTrue(d.projectIds().isEmpty(), "projectIds 非 List 时不得把标量当单元素列表");

        DataScopeContract.Declared dirty = DataScopeContract.read(resource(scope(
                "projectIds", Arrays.asList("p1", null, "  ", "p1", "p3"))));
        assertEquals(List.of("p1", "p3"), dirty.projectIds(), "空白/null 元素丢弃且不重复");

        assertEquals(0, DataScopeContract.read("not-a-map").projectIds().size());
        assertTrue(DataScopeContract.read(null).empty());
        assertTrue(DataScopeContract.read(scope("other", 1)).empty(), "缺 dataScope 段＝未声明，禁猜");
    }

    @Test
    @DisplayName("read — 空白 departmentId 视为未声明（禁把 \"\" 当合法域）")
    void blankDepartmentIsUndeclared() {
        DataScopeContract.Declared d = DataScopeContract.read(
                resource(scope("departmentId", "   ", "projectIds", List.of("p1"))));
        assertNull(d.departmentId());
        assertFalse(d.hasDepartment());
        assertTrue(d.hasProjectScope(), "项目集仍是声明过的，写出侧不得整段丢弃");
    }

    @Test
    @DisplayName("toResourceSegment — 未声明即不写；整段未声明返回 null（页面/导出载荷逐字节不变的前提）")
    void undeclaredKeysAreNeverWritten() {
        assertNull(DataScopeContract.toResourceSegment(null));
        assertNull(DataScopeContract.toResourceSegment(DataScopeContract.none()));
        assertNull(DataScopeContract.toResourceSegment(new DataScopeContract.Declared(null, List.of())));
        assertNull(DataScopeContract.toResourceSegment(
                new DataScopeContract.Declared("   ", new ArrayList<>())));
    }

    @Test
    @DisplayName("toResourceSegment — 只声明其一也照写（声明了空域 ≠ 未声明）")
    void partialDeclarationsAreWrittenAsDeclared() {
        Map<String, Object> deptOnly = DataScopeContract.toResourceSegment(
                new DataScopeContract.Declared("d01", List.of()));
        assertEquals(Map.of("dataScope", Map.of("departmentId", "d01")), deptOnly);

        Map<String, Object> projectsOnly = DataScopeContract.toResourceSegment(
                new DataScopeContract.Declared(null, List.of("p1")));
        assertEquals(Map.of("dataScope", Map.of("projectIds", List.of("p1"))), projectsOnly);
    }

    @Test
    @DisplayName("toResourceSegment — 键序是契约：departmentId → projectIds（oracle 文本深比的前提）")
    void keyOrderIsContract() {
        String json = String.valueOf(DataScopeContract.toResourceSegment(
                new DataScopeContract.Declared("d01", List.of("p1"))));
        int dept = json.indexOf("departmentId");
        int projects = json.indexOf("projectIds");
        assertTrue(dept >= 0 && projects > dept,
                "键序漂移会让回放可比性钉读成两条不同链路（录制器 SCOPE 工厂同形）: " + json);
    }

    @Test
    @DisplayName("read→write 往返：与录制形态一致的输入必须原样再现")
    void roundTripIsStable() {
        Map<String, Object> recorded = resource(scope("departmentId", "d01", "projectIds", List.of("p1")));
        assertEquals(String.valueOf(recorded),
                String.valueOf(DataScopeContract.toResourceSegment(DataScopeContract.read(recorded))));
    }

    @Test
    @DisplayName("textOrEmpty — 组织域 null 归一成 \"\"（否则未解析会被策略侧误标为越域）")
    void orgIdNormalizesToEmptyString() {
        assertEquals("", DataScopeContract.textOrEmpty(null));
        assertEquals("", DataScopeContract.textOrEmpty("   "), "空白组织域与未解析同态，禁让它读成一个真实域");
        assertEquals("d01", DataScopeContract.textOrEmpty("d01"));
        assertNull(DataScopeContract.text(null));
        assertNull(DataScopeContract.text("  "));
        assertEquals("x", DataScopeContract.text("x"));
    }
}
