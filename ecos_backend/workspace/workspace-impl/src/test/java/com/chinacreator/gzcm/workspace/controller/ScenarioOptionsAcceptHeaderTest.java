package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.scenario.AvailableItemVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D131 回归（卷01 V-WS-085 取证派生）：选项池跨服务 GET 必须钉 {@code Accept: application/json}。
 *
 * <p>实证病灶：出站只带 Authorization／信任头、不设 Accept ⇒ 对端按 {@code *&#47;*} 协商回 XML ⇒
 * {@code Map} 反序列化把 {@code data} 读成 {@link java.util.LinkedHashMap}（而非 List）⇒
 * 列表腿 items 恒空（live：datasets 池 0 条，而 /datanet/datasource 实有 1 条）。</p>
 */
class ScenarioOptionsAcceptHeaderTest {

    @Test
    @DisplayName("D131: 选项池 GET 带 Accept: application/json, 且列表载荷投影出真实条目")
    void availableDatasetsPinsJsonAcceptAndProjectsList() {
        RestTemplate datanet = mock(RestTemplate.class);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 0);
        body.put("message", "ok");
        body.put("data", List.of(Map.of("datasourceId", "ds-1", "datasourceName", "DemoPG")));
        when(datanet.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(body));

        ScenarioOptionsController controller = new ScenarioOptionsController(
                datanet, mock(RestTemplate.class), mock(RestTemplate.class),
                mock(RestTemplate.class), mock(RestTemplate.class));

        ApiResponse<?> resp = controller.availableDatasets();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Void>> entityCap = ArgumentCaptor.forClass(HttpEntity.class);
        verify(datanet).exchange(eq("/datanet/datasource"), eq(HttpMethod.GET), entityCap.capture(), eq(Map.class));
        assertTrue(entityCap.getValue().getHeaders().getAccept().contains(MediaType.APPLICATION_JSON),
                "出站 GET 必须钉 Accept: application/json；缺省会被 XML 协商读成 Map，选项池恒空");

        List<?> items = (List<?>) resp.getData();
        assertEquals(1, items.size(), "上游 1 条数据源应投影为 1 个选项，不得读空");
        AvailableItemVO item = (AvailableItemVO) items.get(0);
        assertEquals("ds-1", item.getId());
        assertEquals("DemoPG", item.getName());
    }
}
