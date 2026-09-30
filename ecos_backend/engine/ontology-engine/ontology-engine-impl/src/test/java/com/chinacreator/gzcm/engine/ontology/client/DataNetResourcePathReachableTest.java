package com.chinacreator.gzcm.engine.ontology.client;

import com.chinacreator.gzcm.common.exception.DataBridgeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * F02-15 验收 — {@code DataNetResourcePathReachableTest}（字符串契约单测，不起 HTTP）。
 *
 * <p>断言改造后 {@code listResourceFields} 的目标 URL 前缀与 data-engine-impl
 * {@code AssetController}（{@code @RequestMapping("/api/v1/datanet")}
 * + {@code @GetMapping("/assets/{assetId}/fields")}）真实映射一致：
 * {@code <base>/api/v1/datanet/assets/{assetId}/fields}。
 * 旧路径 {@code /api/v1/datanet/resources/{resourceId}/fields} 404（D-14），禁回归。
 */
class DataNetResourcePathReachableTest {

    @Test
    @DisplayName("listResourceFields URL = 真实 data-engine AssetController 映射（/assets/{id}/fields）")
    void fieldsUrlMatchesRealAssetControllerMapping() {
        DataNetResourceClient client =
                new DataNetResourceClient(new RestTemplate(), () -> "http://localhost:8080");
        String url = client.buildFieldsUrl("asset-42");
        assertEquals("http://localhost:8080/api/v1/datanet/assets/asset-42/fields", url,
                "必须命中 AssetController @RequestMapping(/api/v1/datanet) + /assets/{assetId}/fields");
    }

    @Test
    @DisplayName("旧 /resources/{id}/fields 路径已废弃（D-14 404），禁回归")
    void legacyResourcesPathRemoved() {
        DataNetResourceClient client =
                new DataNetResourceClient(new RestTemplate(), () -> "http://localhost:8080");
        String url = client.buildFieldsUrl("r-1");
        // 结构性断言：不得再走已 404 的 /datanet/resources/ 前缀
        assertEquals(false, url.contains("/datanet/resources/"),
                "旧路径 /api/v1/datanet/resources/{id}/fields 在 data-engine 中 404（D-14）: " + url);
    }

    @Test
    @DisplayName("寻址失败时 buildFieldsUrl 同样 fail-loud（URL 构建不吞寻址错误）")
    void buildFieldsUrlFailsLoudOnAddressingFailure() {
        DataNetResourceClient client = new DataNetResourceClient(new RestTemplate(), () -> {
            throw new DataBridgeException(503, 503,
                    "ECOS-DATA-041: datanet 服务寻址失败（route-manifest 缺 datanet 条目）");
        });
        DataBridgeException ex = assertThrows(DataBridgeException.class,
                () -> client.buildFieldsUrl("x"));
        assertTrueMessageContains(ex);
    }

    private static void assertTrueMessageContains(DataBridgeException ex) {
        if (!ex.getMessage().contains("ECOS-DATA-041")) {
            throw new AssertionError("寻址失败必须带 ECOS-DATA-041: " + ex.getMessage());
        }
    }
}
