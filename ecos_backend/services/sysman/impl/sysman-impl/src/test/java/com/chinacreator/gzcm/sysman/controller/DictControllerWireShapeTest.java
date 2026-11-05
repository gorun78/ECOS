package com.chinacreator.gzcm.sysman.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.dict.entity.SysDict;
import com.chinacreator.gzcm.sysman.dict.service.IDictService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 字典读面 wire 键面保真门（#147BX · DoD DI-2 第三批）。
 *
 * <p><b>钉的是什么</b>：三类字典列表与删除回执原由 {@code Map} 组装（键 dictType/itemCount/
 * items/subsystem/success/dictCode），本批改强类型 VO。键面必须**集合相等**——多键少键皆红；
 * null 语义也要保：{@code listTypes} 对空类型仍输出 {@code subsystem: null}（键在值为 null）。</p>
 *
 * <p><b>变异自证</b>：把 {@code DictTypeEntryVO#getDictType} 加 {@code @JsonProperty("dict_type")}
 * ⇒ 键集断言红；把 {@code subsystem} 改成「null 时不输出」⇒ 该用例红。</p>
 */
class DictControllerWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private static Set<String> keysOf(Object value) {
        Map<String, Object> map = MAPPER.convertValue(value, Map.class);
        return new LinkedHashSet<>(map.keySet());
    }

    private static SysDict dict(String type, String code, String subsystem) {
        SysDict d = new SysDict();
        d.setDictType(type);
        d.setDictCode(code);
        d.setDictLabel("label-" + code);
        d.setSubsystem(subsystem);
        return d;
    }

    private static IDictService serviceWithTwoTypes() {
        IDictService service = mock(IDictService.class);
        SysDict a1 = dict("a", "a1", "G1");
        SysDict a2 = dict("a", "a2", "G1");
        when(service.listDictTypes()).thenReturn(List.of("a", "b"));
        when(service.getDictItems("a")).thenReturn(List.of(a1, a2));
        when(service.getDictItems("b")).thenReturn(List.of());
        return service;
    }

    @Test
    @DisplayName("listDictTypes：条目键 = dictType/itemCount/items（集合相等），items 为真实字典行")
    void listDictTypesKeepsLegacyKeys() {
        DictController controller = inject(new DictController(), serviceWithTwoTypes());

        ApiResponse<List<DictController.DictTypeEntryVO>> resp = controller.listDictTypes();
        List<DictController.DictTypeEntryVO> data = resp.getData();
        assertEquals(2, data.size());
        assertEquals(Set.of("dictType", "itemCount", "items"), keysOf(data.get(0)));
        assertEquals("a", data.get(0).getDictType());
        assertEquals(2, data.get(0).getItemCount());
        assertEquals(2, data.get(0).getItems().size());
        assertEquals(0, data.get(1).getItemCount());
    }

    @Test
    @DisplayName("listTypes：subsystem 为 null 时键仍在（旧 Map.put(null) 语义不得丢）")
    void listTypesKeepsNullSubsystemKey() {
        DictController controller = inject(new DictController(), serviceWithTwoTypes());

        List<DictController.DictTypeSummaryVO> data = controller.listTypes().getData();
        assertEquals(Set.of("dictType", "itemCount", "subsystem"), keysOf(data.get(0)));
        assertEquals("G1", data.get(0).getSubsystem());

        Map<String, Object> emptyType = MAPPER.convertValue(data.get(1), Map.class);
        assertTrue(emptyType.containsKey("subsystem"), "空类型的 subsystem 键必须存在（值为 null）");
        assertNull(data.get(1).getSubsystem());
    }

    @Test
    @DisplayName("listBySubsystem：分组键 G1/G0，条目键集与 listDictTypes 一致")
    void listBySubsystemGroupsAndKeepsKeys() {
        DictController controller = inject(new DictController(), serviceWithTwoTypes());

        Map<String, List<DictController.DictTypeEntryVO>> grouped = controller.listBySubsystem().getData();
        assertEquals(Set.of("G1", "G0"), grouped.keySet());
        assertEquals(Set.of("dictType", "itemCount", "items"), keysOf(grouped.get("G1").get(0)));
        assertEquals("b", grouped.get("G0").get(0).getDictType(), "无条目类型归 G0");
    }

    @Test
    @DisplayName("deleteDictItem：回执键 = success/dictType/dictCode（集合相等）")
    void deleteResultKeepsLegacyKeys() {
        IDictService service = mock(IDictService.class);
        when(service.getDictItem("a", "a1")).thenReturn(dict("a", "a1", "G1"));
        DictController controller = inject(new DictController(), service);

        ApiResponse<DictController.DictDeleteResultVO> resp = controller.deleteDictItem("a", "a1");
        assertEquals(Set.of("success", "dictType", "dictCode"), keysOf(resp.getData()));
        assertTrue(resp.getData().isSuccess());
        assertEquals("a", resp.getData().getDictType());
        assertEquals("a1", resp.getData().getDictCode());
    }

    @Test
    @DisplayName("refresh/usage：属主 Map 原样透传（本层不复制键面）")
    void ownerMapsPassThroughUnchanged() {
        IDictService service = mock(IDictService.class);
        Map<String, Object> refreshed = new LinkedHashMap<>();
        refreshed.put("types", 3);
        refreshed.put("items", 9);
        refreshed.put("elapsedMs", 12L);
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("dictType", "a");
        usage.put("usedByCount", 0);
        when(service.refreshCache()).thenReturn(refreshed);
        when(service.getDictUsage("a")).thenReturn(usage);
        DictController controller = inject(new DictController(), service);

        assertSame(refreshed, controller.refreshCache().getData());
        assertSame(usage, controller.getDictUsage("a").getData());
    }

    private static DictController inject(DictController controller, IDictService service) {
        try {
            java.lang.reflect.Field f = DictController.class.getDeclaredField("dictService");
            f.setAccessible(true);
            f.set(controller, service);
            return controller;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
