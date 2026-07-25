package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController("sysmanMarketplaceController")
@RequestMapping({"/api/marketplace", "/api/v1/marketplace"})
public class MarketplaceController {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceController.class);

    private final ConcurrentHashMap<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    public MarketplaceController() {
        long now = System.currentTimeMillis();
        seed("asset-001", "Customer Profile Dataset", "客户画像数据集",
                "Comprehensive customer profile data including demographics, preferences, and transaction history.",
                "data", "published", "Data Engineering Team", "1.2.0",
                List.of("customer", "profile", "demographics"), now);
        seed("asset-002", "Fraud Detection Model", "欺诈检测模型",
                "ML model for real-time fraud detection based on transaction patterns and anomaly scoring.",
                "model", "published", "AI Research Lab", "2.0.1",
                List.of("fraud", "ml", "detection"), now);
        seed("asset-003", "Supply Chain Ontology", "供应链本体",
                "Domain ontology covering suppliers, logistics, inventory, and procurement relationships.",
                "ontology", "draft", "Ontology Team", "0.9.0",
                List.of("supply-chain", "ontology", "logistics"), now);
        seed("asset-004", "Sentiment Analysis API", "情感分析API",
                "REST API for sentiment analysis on text data, supporting Chinese and English.",
                "service", "published", "NLP Team", "3.1.0",
                List.of("sentiment", "nlp", "api"), now);
        seed("asset-005", "Regulatory Compliance Rules", "合规规则集",
                "ABAC policy rules for regulatory compliance across financial and healthcare sectors.",
                "policy", "review", "Compliance Team", "1.0.0",
                List.of("compliance", "policy", "regulation"), now);
        log.info("MarketplaceController 初始化完成，已加载 {} 条种子数据", store.size());
    }

    private void seed(String id, String name, String nameZh, String description,
                      String category, String status, String owner, String version,
                      List<String> tags, long now) {
        Map<String, Object> asset = new LinkedHashMap<>();
        asset.put("id", id);
        asset.put("name", name);
        asset.put("nameZh", nameZh);
        asset.put("description", description);
        asset.put("category", category);
        asset.put("status", status);
        asset.put("owner", owner);
        asset.put("version", version);
        asset.put("tags", tags);
        asset.put("createdAt", now);
        asset.put("updatedAt", now);
        store.put(id, asset);
    }

    @GetMapping("/assets")
    public ApiResponse<Map<String, Object>> listAssets(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) String status) {
        try {
            List<Map<String, Object>> all = new ArrayList<>(store.values());
            if (category != null && !category.isBlank()) {
                all.removeIf(a -> !category.equals(a.get("category")));
            }
            if (status != null && !status.isBlank()) {
                all.removeIf(a -> !status.equals(a.get("status")));
            }
            all.sort((a, b) -> Long.compare((Long) b.get("updatedAt"), (Long) a.get("updatedAt")));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", all);
            result.put("total", all.size());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询资产列表失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/assets/{id}")
    public ApiResponse<?> getAsset(@PathVariable String id) {
        try {
            Map<String, Object> asset = store.get(id);
            if (asset == null) {
                return ApiResponse.notFound("资产不存在: " + id);
            }
            return ApiResponse.success(asset);
        } catch (Exception e) {
            log.error("查询资产失败, id={}", id, e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/assets")
    public ApiResponse<?> publishAsset(@RequestBody Map<String, Object> body) {
        try {
            String id = UUID.randomUUID().toString().replace("-", "");
            long now = System.currentTimeMillis();
            Map<String, Object> asset = new LinkedHashMap<>();
            asset.put("id", id);
            asset.put("name", body.getOrDefault("name", "Asset " + id.substring(0, 8)));
            asset.put("nameZh", body.getOrDefault("nameZh", ""));
            asset.put("description", body.getOrDefault("description", ""));
            asset.put("category", body.getOrDefault("category", "data"));
            asset.put("status", body.getOrDefault("status", "draft"));
            asset.put("owner", body.getOrDefault("owner", "anonymous"));
            asset.put("version", body.getOrDefault("version", "1.0.0"));
            asset.put("tags", body.getOrDefault("tags", new ArrayList<>()));
            asset.put("createdAt", now);
            asset.put("updatedAt", now);
            store.put(id, asset);
            log.info("资产发布成功, id={}, name={}", id, asset.get("name"));
            return ApiResponse.success(asset);
        } catch (Exception e) {
            log.error("发布资产失败", e);
            return ApiResponse.internalError("发布失败: " + e.getMessage());
        }
    }

    @GetMapping("/search")
    public ApiResponse<Map<String, Object>> searchAssets(@RequestParam(value = "q", required = false) String query) {
        try {
            List<Map<String, Object>> results;
            if (query == null || query.isBlank()) {
                results = new ArrayList<>(store.values());
            } else {
                String lowerQ = query.toLowerCase();
                results = new ArrayList<>();
                for (Map<String, Object> asset : store.values()) {
                    String name = String.valueOf(asset.getOrDefault("name", "")).toLowerCase();
                    String nameZh = String.valueOf(asset.getOrDefault("nameZh", "")).toLowerCase();
                    String desc = String.valueOf(asset.getOrDefault("description", "")).toLowerCase();
                    String category = String.valueOf(asset.getOrDefault("category", "")).toLowerCase();
                    @SuppressWarnings("unchecked")
                    List<String> tags = (List<String>) asset.getOrDefault("tags", new ArrayList<>());
                    boolean tagMatch = tags.stream().anyMatch(t -> t.toLowerCase().contains(lowerQ));
                    if (name.contains(lowerQ) || nameZh.contains(lowerQ)
                            || desc.contains(lowerQ) || category.contains(lowerQ) || tagMatch) {
                        results.add(asset);
                    }
                }
            }
            results.sort((a, b) -> Long.compare((Long) b.get("updatedAt"), (Long) a.get("updatedAt")));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", results);
            result.put("total", results.size());
            result.put("query", query);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("搜索资产失败, q={}", query, e);
            return ApiResponse.internalError("搜索失败: " + e.getMessage());
        }
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        try {
            int total = store.size();
            Map<String, Long> byCategory = new LinkedHashMap<>();
            Map<String, Long> byStatus = new LinkedHashMap<>();
            for (Map<String, Object> asset : store.values()) {
                String cat = String.valueOf(asset.getOrDefault("category", "unknown"));
                String stat = String.valueOf(asset.getOrDefault("status", "unknown"));
                byCategory.merge(cat, 1L, Long::sum);
                byStatus.merge(stat, 1L, Long::sum);
            }
            List<Map<String, Object>> recent = new ArrayList<>(store.values());
            recent.sort((a, b) -> Long.compare((Long) b.get("updatedAt"), (Long) a.get("updatedAt")));
            if (recent.size() > 5) {
                recent = recent.subList(0, 5);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalAssets", total);
            result.put("byCategory", byCategory);
            result.put("byStatus", byStatus);
            result.put("recentAssets", recent);
            result.put("timestamp", System.currentTimeMillis());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取仪表盘数据失败", e);
            return ApiResponse.internalError("获取仪表盘失败: " + e.getMessage());
        }
    }

    @PostMapping("/request-access")
    public ApiResponse<Map<String, Object>> requestAccess(@RequestBody Map<String, Object> body) {
        try {
            String assetId = String.valueOf(body.getOrDefault("assetId", ""));
            String requester = String.valueOf(body.getOrDefault("requester", "anonymous"));
            String reason = String.valueOf(body.getOrDefault("reason", ""));
            if (assetId.isEmpty() || !store.containsKey(assetId)) {
                return ApiResponse.badRequest("资产不存在: " + assetId);
            }
            String requestId = UUID.randomUUID().toString().replace("-", "");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("requestId", requestId);
            result.put("assetId", assetId);
            result.put("requester", requester);
            result.put("reason", reason);
            result.put("status", "pending");
            result.put("createdAt", System.currentTimeMillis());
            log.info("访问请求已提交, requestId={}, assetId={}, requester={}", requestId, assetId, requester);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("提交访问请求失败", e);
            return ApiResponse.internalError("请求失败: " + e.getMessage());
        }
    }
}
