package com.chinacreator.gzcm.sysman.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.config.entity.Config;
import com.chinacreator.gzcm.sysman.config.entity.SystemParam;
import com.chinacreator.gzcm.sysman.config.service.IConfigService;
import com.chinacreator.gzcm.sysman.config.service.IConfigVersionService;
import com.chinacreator.gzcm.sysman.config.service.ISystemParamService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 配置/参数列表 wire 键面保真门（#147BW · DoD DI-2 第二批）。
 *
 * <p><b>为什么需要本门</b>：两个列表读面原返 {@code Map<String,Object>}（键 data/total/page/pageSize），
 * 本次改强类型 VO。载体换了但**契约不能动**——前端/tests 按这四个键取值；键少一个或改名
 * 都是断契约。本门按**集合相等**断言键面（多键少键皆红），并钉住分页切片语义。</p>
 *
 * <p><b>变异自证</b>：把 VO 任一 getter 改名（如 {@code getPageSize→getSize}）⇒ 键集断言红；
 * 把越界页改回「回全部」⇒ 空页用例红。</p>
 */
class ConfigListWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static <T> ObjectProvider<T> provider(T bean) {
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                return bean;
            }

            @Override
            public T getObject(Object... args) {
                return bean;
            }

            @Override
            public T getIfAvailable() {
                return bean;
            }

            @Override
            public T getIfUnique() {
                return bean;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static Set<String> keysOf(Object value) {
        Map<String, Object> map = MAPPER.convertValue(value, Map.class);
        return new LinkedHashSet<>(map.keySet());
    }

    private static Config config(String id) {
        Config c = new Config();
        c.setConfigId(id);
        c.setConfigName("name-" + id);
        return c;
    }

    @Test
    @DisplayName("配置项列表键面 = data/total/page/pageSize（集合相等）且分页切片正确")
    void configListKeepsLegacyKeys() throws Exception {
        IConfigService service = mock(IConfigService.class);
        when(service.listConfigs(any())).thenReturn(List.of(config("1"), config("2"), config("3")));
        ConfigController controller = new ConfigController(
                provider(service), provider(mock(IConfigVersionService.class)));

        ApiResponse<ConfigController.ConfigPageVO> response = controller.list(null, null, 1, 2);

        assertEquals(Set.of("data", "total", "page", "pageSize"), keysOf(response.getData()));
        assertEquals(2, response.getData().getData().size());
        assertEquals(3, response.getData().getTotal());
        assertEquals(1, response.getData().getPage());
        assertEquals(2, response.getData().getPageSize());

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = MAPPER.convertValue(response, Map.class);
        assertEquals(ApiResponse.CODE_SUCCESS, envelope.get("code"));
        assertTrue(envelope.containsKey("data"), "成功信封的载荷必须在 data 键下");
    }

    @Test
    @DisplayName("配置项越界页 ⇒ data 为空集而非全量（分页语义不变）")
    void configListOutOfRangeIsEmpty() throws Exception {
        IConfigService service = mock(IConfigService.class);
        when(service.listConfigs(any())).thenReturn(List.of(config("1")));
        ConfigController controller = new ConfigController(
                provider(service), provider(mock(IConfigVersionService.class)));

        ApiResponse<ConfigController.ConfigPageVO> response = controller.list(null, null, 99, 20);

        assertTrue(response.getData().getData().isEmpty());
        assertEquals(1, response.getData().getTotal());
    }

    @Test
    @DisplayName("系统参数列表键面 = data/total/page/pageSize（集合相等）")
    void systemParamListKeepsLegacyKeys() throws Exception {
        ISystemParamService service = mock(ISystemParamService.class);
        SystemParam p = new SystemParam();
        p.setParamId("p1");
        when(service.listParams(any())).thenReturn(List.of(p));
        SystemParamController controller = new SystemParamController(provider(service));

        ApiResponse<SystemParamController.SystemParamPageVO> response = controller.list(null, 1, 20);

        assertEquals(Set.of("data", "total", "page", "pageSize"), keysOf(response.getData()));
        assertEquals(1, response.getData().getData().size());
        assertEquals(1, response.getData().getTotal());
    }
}
