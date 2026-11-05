package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.dto.MarketplaceAccessRequestVO;
import com.chinacreator.gzcm.sysman.dto.MarketplaceAssetListVO;
import com.chinacreator.gzcm.sysman.dto.MarketplaceAssetSearchVO;
import com.chinacreator.gzcm.sysman.dto.MarketplaceAssetVO;
import com.chinacreator.gzcm.sysman.dto.MarketplaceDashboardVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController("sysmanMarketplaceController")
@RequestMapping("/api/v1/marketplace")
public class MarketplaceController {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceController.class);

    /** 内存资产 store（行模型强类型 {@link MarketplaceAssetVO}，键集固定 11 枚）。 */
    private final ConcurrentHashMap<String, MarketplaceAssetVO> store = new ConcurrentHashMap<>();

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
        store.put(id, new MarketplaceAssetVO(id, name, nameZh, description, category,
                status, owner, version, tags, now, now));
    }

    @GetMapping("/assets")
    public ApiResponse<MarketplaceAssetListVO> listAssets(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) String status) {
        try {
            List<MarketplaceAssetVO> all = new ArrayList<>(store.values());
            if (category != null && !category.isBlank()) {
                all.removeIf(a -> !category.equals(a.category()));
            }
            if (status != null && !status.isBlank()) {
                all.removeIf(a -> !status.equals(a.status()));
            }
            all.sort((a, b) -> Long.compare(b.updatedAt(), a.updatedAt()));
            return ApiResponse.success(new MarketplaceAssetListVO(all, all.size()));
        } catch (Exception e) {
            log.error("查询资产列表失败", e);
            return ApiResponse.internalError("查询失败");
        }
    }

    @GetMapping("/assets/{id}")
    public ApiResponse<MarketplaceAssetVO> getAsset(@PathVariable String id) {
        try {
            MarketplaceAssetVO asset = store.get(id);
            if (asset == null) {
                return ApiResponse.notFound("资产不存在: " + id);
            }
            return ApiResponse.success(asset);
        } catch (Exception e) {
            log.error("查询资产失败, id={}", id, e);
            return ApiResponse.internalError("查询失败");
        }
    }

    @PostMapping("/assets")
    public ApiResponse<MarketplaceAssetVO> publishAsset(@RequestBody Map<String, Object> body) {
        try {
            String id = UUID.randomUUID().toString().replace("-", "");
            long now = System.currentTimeMillis();
            Object rawTags = body.getOrDefault("tags", List.of());
            List<String> tags = rawTags instanceof List<?> list
                    ? list.stream().filter(Objects::nonNull).map(String::valueOf).toList()
                    : List.of();
            MarketplaceAssetVO asset = new MarketplaceAssetVO(
                    id,
                    String.valueOf(body.getOrDefault("name", "Asset " + id.substring(0, 8))),
                    String.valueOf(body.getOrDefault("nameZh", "")),
                    String.valueOf(body.getOrDefault("description", "")),
                    String.valueOf(body.getOrDefault("category", "data")),
                    String.valueOf(body.getOrDefault("status", "draft")),
                    String.valueOf(body.getOrDefault("owner", "anonymous")),
                    String.valueOf(body.getOrDefault("version", "1.0.0")),
                    tags, now, now);
            store.put(id, asset);
            log.info("资产发布成功, id={}, name={}", id, asset.name());
            return ApiResponse.success(asset);
        } catch (Exception e) {
            log.error("发布资产失败", e);
            return ApiResponse.internalError("发布失败");
        }
    }

    @GetMapping("/search")
    public ApiResponse<MarketplaceAssetSearchVO> searchAssets(
            @RequestParam(value = "q", required = false) String query) {
        try {
            List<MarketplaceAssetVO> results;
            if (query == null || query.isBlank()) {
                results = new ArrayList<>(store.values());
            } else {
                String lowerQ = query.toLowerCase();
                results = new ArrayList<>();
                for (MarketplaceAssetVO asset : store.values()) {
                    String name = nullToEmpty(asset.name()).toLowerCase();
                    String nameZh = nullToEmpty(asset.nameZh()).toLowerCase();
                    String desc = nullToEmpty(asset.description()).toLowerCase();
                    String category = nullToEmpty(asset.category()).toLowerCase();
                    List<String> tags = asset.tags() != null ? asset.tags() : List.of();
                    boolean tagMatch = tags.stream().anyMatch(t -> nullToEmpty(t).toLowerCase().contains(lowerQ));
                    if (name.contains(lowerQ) || nameZh.contains(lowerQ)
                            || desc.contains(lowerQ) || category.contains(lowerQ) || tagMatch) {
                        results.add(asset);
                    }
                }
            }
            results.sort((a, b) -> Long.compare(b.updatedAt(), a.updatedAt()));
            return ApiResponse.success(new MarketplaceAssetSearchVO(results, results.size(), query));
        } catch (Exception e) {
            log.error("搜索资产失败, q={}", query, e);
            return ApiResponse.internalError("搜索失败");
        }
    }

    @GetMapping("/dashboard")
    public ApiResponse<MarketplaceDashboardVO> dashboard() {
        try {
            int total = store.size();
            Map<String, Long> byCategory = new LinkedHashMap<>();
            Map<String, Long> byStatus = new LinkedHashMap<>();
            for (MarketplaceAssetVO asset : store.values()) {
                String cat = asset.category() != null ? asset.category() : "unknown";
                String stat = asset.status() != null ? asset.status() : "unknown";
                byCategory.merge(cat, 1L, Long::sum);
                byStatus.merge(stat, 1L, Long::sum);
            }
            List<MarketplaceAssetVO> recent = new ArrayList<>(store.values());
            recent.sort((a, b) -> Long.compare(b.updatedAt(), a.updatedAt()));
            if (recent.size() > 5) {
                recent = recent.subList(0, 5);
            }
            return ApiResponse.success(new MarketplaceDashboardVO(
                    total, byCategory, byStatus, recent, System.currentTimeMillis()));
        } catch (Exception e) {
            log.error("获取仪表盘数据失败", e);
            return ApiResponse.internalError("获取仪表盘失败");
        }
    }

    @PostMapping("/request-access")
    public ApiResponse<MarketplaceAccessRequestVO> requestAccess(@RequestBody Map<String, Object> body) {
        try {
            String assetId = String.valueOf(body.getOrDefault("assetId", ""));
            String requester = String.valueOf(body.getOrDefault("requester", "anonymous"));
            String reason = String.valueOf(body.getOrDefault("reason", ""));
            if (assetId.isEmpty() || !store.containsKey(assetId)) {
                return ApiResponse.badRequest("资产不存在: " + assetId);
            }
            String requestId = UUID.randomUUID().toString().replace("-", "");
            log.info("访问请求已提交, requestId={}, assetId={}, requester={}", requestId, assetId, requester);
            return ApiResponse.success(new MarketplaceAccessRequestVO(
                    requestId, assetId, requester, reason, "pending", System.currentTimeMillis()));
        } catch (Exception e) {
            log.error("提交访问请求失败", e);
            return ApiResponse.internalError("请求失败");
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
