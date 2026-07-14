package com.chinacreator.gzcm.sysman;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.market.MarketplaceAssetEntity;
import com.chinacreator.gzcm.market.MarketplaceRepository;
import com.chinacreator.gzcm.market.MarketplaceService;
import com.chinacreator.gzcm.market.controller.MarketplaceController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MarketplaceController 单元测试 — 覆盖 11 个 REST 端点
 */
@ExtendWith(MockitoExtension.class)
class MarketplaceControllerTest {

    @Mock
    private MarketplaceRepository repository;

    @Mock
    private MarketplaceService service;

    private MarketplaceController controller;

    @BeforeEach
    void setUp() {
        controller = new MarketplaceController(repository, service);
    }

    // ── 辅助方法 ──

    private MarketplaceAssetEntity createAsset(Long id, String name) {
        MarketplaceAssetEntity entity = new MarketplaceAssetEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setDescription("描述 " + id);
        entity.setCategory("数据集");
        entity.setOwner("admin");
        entity.setRating(BigDecimal.valueOf(4.0));
        entity.setPopularity(100);
        entity.setStatus("PUBLISHED");
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    // ═══════════════ 资产列表 ═══════════════════

    @Test
    void listAssets_shouldReturnPaginatedList() {
        when(repository.findAllAssets("popular", 10))
            .thenReturn(List.of(createAsset(1L, "资产A"), createAsset(2L, "资产B")));
        when(repository.countAssets()).thenReturn(2L);

        ApiResponse<Map<String, Object>> resp = controller.listAssets("popular", 10);

        assertNotNull(resp);
        assertTrue(resp.isSuccess());
        Map<String, Object> data = resp.getData();
        assertEquals(2L, data.get("total"));
        assertEquals("popular", data.get("sort"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        assertEquals(2, items.size());
        assertEquals("资产A", items.get(0).get("name"));
        assertEquals("资产B", items.get(1).get("name"));
    }

    @Test
    void listAssets_shouldHandleEmptyList() {
        when(repository.findAllAssets(anyString(), anyInt())).thenReturn(List.of());
        when(repository.countAssets()).thenReturn(0L);

        ApiResponse<Map<String, Object>> resp = controller.listAssets("newest", 5);

        assertTrue(resp.isSuccess());
        assertEquals(0L, resp.getData().get("total"));
    }

    // ═══════════════ 资产详情 ═══════════════════

    @Test
    void getAsset_shouldReturnAssetById() {
        when(repository.findAssetById(1L)).thenReturn(Optional.of(createAsset(1L, "资产A")));

        ApiResponse<Map<String, Object>> resp = controller.getAsset(1L);

        assertTrue(resp.isSuccess());
        assertEquals("资产A", resp.getData().get("name"));
        assertEquals("PUBLISHED", resp.getData().get("status"));
    }

    @Test
    void getAsset_shouldReturn404_whenNotFound() {
        when(repository.findAssetById(999L)).thenReturn(Optional.empty());

        ApiResponse<Map<String, Object>> resp = controller.getAsset(999L);

        assertFalse(resp.isSuccess());
        assertTrue(resp.getMessage().contains("999"));
    }

    // ═══════════════ 发布资产 ═══════════════════

    @Test
    void publishAsset_shouldReturnCreated() {
        Map<String, Object> body = Map.of("name", "新资产", "category", "API");
        when(service.publishAsset(body)).thenReturn(Map.of("id", 10, "name", "新资产"));

        ApiResponse<Map<String, Object>> resp = controller.publishAsset(body);

        assertTrue(resp.isSuccess());
        assertEquals("新资产", resp.getData().get("name"));
    }

    @Test
    void publishAsset_shouldHandleServiceException() {
        Map<String, Object> body = Map.of("name", "");
        when(service.publishAsset(body)).thenThrow(new IllegalArgumentException("名称不能为空"));

        ApiResponse<Map<String, Object>> resp = controller.publishAsset(body);

        assertFalse(resp.isSuccess());
        assertTrue(resp.getMessage().contains("名称不能为空"));
    }

    // ═══════════════ 更新资产 ═══════════════════

    @Test
    void updateAsset_shouldUpdateExisting() {
        Map<String, Object> body = Map.of("name", "已更新");
        when(service.updateAsset(1L, body)).thenReturn(Optional.of(Map.of("id", 1, "name", "已更新")));

        ApiResponse<Map<String, Object>> resp = controller.updateAsset(1L, body);

        assertTrue(resp.isSuccess());
        assertEquals("已更新", resp.getData().get("name"));
    }

    @Test
    void updateAsset_shouldReturn404_whenNotFound() {
        when(service.updateAsset(eq(99L), anyMap())).thenReturn(Optional.empty());

        ApiResponse<Map<String, Object>> resp = controller.updateAsset(99L, Map.of());

        assertFalse(resp.isSuccess());
        assertTrue(resp.getMessage().contains("99"));
    }

    // ═══════════════ 上下架 ═══════════════════

    @Test
    void shelfAsset_shouldPublish() {
        when(service.shelfAsset(1L, "publish")).thenReturn(true);

        ApiResponse<String> resp = controller.shelfAsset(1L, Map.of("action", "publish"));

        assertTrue(resp.isSuccess());
        assertTrue(resp.getData().contains("已上架"));
    }

    @Test
    void shelfAsset_shouldDeprecate() {
        when(service.shelfAsset(1L, "deprecate")).thenReturn(true);

        ApiResponse<String> resp = controller.shelfAsset(1L, Map.of("action", "deprecate"));

        assertTrue(resp.isSuccess());
        assertTrue(resp.getData().contains("已下架"));
    }

    // ═══════════════ 申请访问 ═══════════════════

    @Test
    void requestAccess_shouldCreateRequest() {
        Map<String, Object> body = Map.of("assetId", "1", "reason", "开发需要");
        when(service.requestAccess(body)).thenReturn(Map.of("requestId", "req-1", "status", "PENDING"));

        ApiResponse<Map<String, Object>> resp = controller.requestAccess(body);

        assertTrue(resp.isSuccess());
        assertEquals("req-1", resp.getData().get("requestId"));
    }

    // ═══════════════ 申请列表 ═══════════════════

    @Test
    void listRequests_shouldReturnFilteredList() {
        when(service.listRequests("PENDING", null))
            .thenReturn(List.of(Map.of("id", 1, "status", "PENDING")));

        ApiResponse<List<Map<String, Object>>> resp = controller.listRequests("PENDING", null);

        assertTrue(resp.isSuccess());
        assertEquals(1, resp.getData().size());
    }

    // ═══════════════ 审批 ═══════════════════

    @Test
    void approveRequest_shouldApprove() {
        when(service.approveRequest(1L, true, ""))
            .thenReturn(Optional.of(Map.of("id", 1, "status", "APPROVED")));

        ApiResponse<Map<String, Object>> resp = controller.approveRequest(1L,
            Map.of("approved", "true", "comment", ""));

        assertTrue(resp.isSuccess());
        assertEquals("APPROVED", resp.getData().get("status"));
    }

    // ═══════════════ 评价 ═══════════════════

    @Test
    void addReview_shouldAddReview() {
        Map<String, Object> body = Map.of("rating", 5, "comment", "很好");
        when(service.addReview(1L, body)).thenReturn(Map.of("reviewId", 1));

        ApiResponse<Map<String, Object>> resp = controller.addReview(1L, body);

        assertTrue(resp.isSuccess());
    }

    @Test
    void listReviews_shouldReturnReviews() {
        when(service.listReviews(1L))
            .thenReturn(List.of(Map.of("rating", 5, "comment", "很好")));

        ApiResponse<List<Map<String, Object>>> resp = controller.listReviews(1L);

        assertTrue(resp.isSuccess());
        assertEquals(1, resp.getData().size());
    }

    // ═══════════════ 搜索 ═══════════════════

    @Test
    void search_shouldReturnMatchingAssets() {
        when(service.searchAssets("test", "API"))
            .thenReturn(List.of(Map.of("id", 1, "name", "测试API")));

        ApiResponse<List<Map<String, Object>>> resp = controller.search("test", "API");

        assertTrue(resp.isSuccess());
        assertEquals("测试API", resp.getData().get(0).get("name"));
    }
}
