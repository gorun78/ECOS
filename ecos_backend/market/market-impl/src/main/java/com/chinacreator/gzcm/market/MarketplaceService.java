package com.chinacreator.gzcm.market;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据市场业务服务 — 资产上下架、申请审批、评价体系
 */
@Service
public class MarketplaceService {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceService.class);

    private final MarketplaceRepository repository;

    public MarketplaceService(MarketplaceRepository repository) {
        this.repository = repository;
    }

    // ═══════════════ 资产管理 ═══════════════════

    public Map<String, Object> publishAsset(Map<String, Object> body) {
        MarketplaceAssetEntity entity = new MarketplaceAssetEntity();
        entity.setName(String.valueOf(body.getOrDefault("name", "")));
        entity.setDescription(String.valueOf(body.getOrDefault("description", "")));
        entity.setCategory(String.valueOf(body.getOrDefault("category", "数据集")));
        entity.setOwner(String.valueOf(body.getOrDefault("owner", "admin")));
        entity.setRating(BigDecimal.ZERO);
        entity.setPopularity(0);
        entity.setStatus("PUBLISHED");
        repository.insertAsset(entity);
        log.info("Asset published: id={}, name={}", entity.getId(), entity.getName());
        return assetToMap(entity);
    }

    public Optional<Map<String, Object>> updateAsset(Long id, Map<String, Object> body) {
        Optional<MarketplaceAssetEntity> existing = repository.findAssetById(id);
        if (existing.isEmpty()) return Optional.empty();

        String name = body.containsKey("name") ? String.valueOf(body.get("name")) : null;
        String description = body.containsKey("description") ? String.valueOf(body.get("description")) : null;
        String category = body.containsKey("category") ? String.valueOf(body.get("category")) : null;
        String status = body.containsKey("status") ? String.valueOf(body.get("status")) : null;

        repository.updateAsset(id, name, description, category, status);
        return repository.findAssetById(id).map(this::assetToMap);
    }

    public boolean shelfAsset(Long id, String action) {
        String newStatus = "PUBLISH".equalsIgnoreCase(action) ? "PUBLISHED" : "DEPRECATED";
        Optional<MarketplaceAssetEntity> existing = repository.findAssetById(id);
        if (existing.isEmpty()) return false;
        repository.updateAsset(id, null, null, null, newStatus);
        log.info("Asset {} {} -> {}", id, existing.get().getStatus(), newStatus);
        return true;
    }

    // ═══════════════ 访问申请 + 审批 ═══════════════════

    public Map<String, Object> requestAccess(Map<String, Object> body) {
        Long assetId = parseLong(body.get("assetId"));
        if (assetId == null) throw new IllegalArgumentException("assetId 不能为空");

        Optional<MarketplaceAssetEntity> asset = repository.findAssetById(assetId);
        if (asset.isEmpty()) throw new IllegalArgumentException("资产 " + assetId + " 不存在");

        MarketplaceAccessRequestEntity entity = new MarketplaceAccessRequestEntity();
        entity.setAssetId(assetId);
        entity.setReason(String.valueOf(body.getOrDefault("reason", "")));
        entity.setApplicant(String.valueOf(body.getOrDefault("applicant", "anonymous")));
        entity.setStatus("PENDING");
        repository.insertAccessRequest(entity);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", entity.getId());
        result.put("assetId", assetId);
        result.put("status", "PENDING");
        log.info("Access request created: {} for asset {}", entity.getId(), assetId);
        return result;
    }

    public List<Map<String, Object>> listRequests(String status, String applicant) {
        return repository.findAllRequests(status, applicant).stream()
            .map(this::requestToMap)
            .collect(Collectors.toList());
    }

    @Transactional
    public Optional<Map<String, Object>> approveRequest(Long requestId, boolean approved, String reviewerComment) {
        Optional<MarketplaceAccessRequestEntity> existing = repository.findRequestById(requestId);
        if (existing.isEmpty()) return Optional.empty();

        String newStatus = approved ? "APPROVED" : "REJECTED";
        repository.updateRequestStatus(requestId, newStatus);
        log.info("Access request {} {} by reviewer", requestId, newStatus);

        return repository.findRequestById(requestId).map(this::requestToMap);
    }

    // ═══════════════ 评价体系 ═══════════════════

    public Map<String, Object> addReview(Long assetId, Map<String, Object> body) {
        Optional<MarketplaceAssetEntity> asset = repository.findAssetById(assetId);
        if (asset.isEmpty()) throw new IllegalArgumentException("资产 " + assetId + " 不存在");

        MarketplaceReviewEntity review = new MarketplaceReviewEntity();
        review.setAssetId(assetId);
        review.setReviewer(String.valueOf(body.getOrDefault("reviewer", "anonymous")));
        review.setComment(String.valueOf(body.getOrDefault("comment", "")));
        BigDecimal rating = parseBigDecimal(body.get("rating"));
        review.setRating(rating != null ? rating : BigDecimal.valueOf(3));
        repository.insertReview(review);

        // Recalculate asset average rating
        recalcRating(assetId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reviewId", review.getId());
        result.put("assetId", assetId);
        result.put("rating", review.getRating());
        log.info("Review added: {} for asset {}, rating={}", review.getId(), assetId, review.getRating());
        return result;
    }

    public List<Map<String, Object>> listReviews(Long assetId) {
        return repository.findReviewsByAssetId(assetId).stream()
            .map(this::reviewToMap)
            .collect(Collectors.toList());
    }

    private void recalcRating(Long assetId) {
        List<MarketplaceReviewEntity> reviews = repository.findReviewsByAssetId(assetId);
        if (reviews.isEmpty()) return;
        BigDecimal avg = reviews.stream()
            .map(MarketplaceReviewEntity::getRating)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(reviews.size()), 2, RoundingMode.HALF_UP);
        repository.updateAssetRating(assetId, avg);
    }

    // ═══════════════ 搜索 ═══════════════════

    public List<Map<String, Object>> searchAssets(String keyword, String category) {
        return repository.searchAssets(keyword, category).stream()
            .map(this::assetToMap)
            .collect(Collectors.toList());
    }

    // ═══════════════ 本体关联 ═══════════════════

    public Map<String, Object> associateOntology(Long assetId, String ontologyEntityId) {
        Optional<MarketplaceAssetEntity> existing = repository.findAssetById(assetId);
        if (existing.isEmpty()) return null;
        repository.updateAssetOntology(assetId, ontologyEntityId);
        log.info("Asset {} linked to ontology entity {}", assetId, ontologyEntityId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assetId", assetId);
        result.put("ontologyEntityId", ontologyEntityId);
        return result;
    }

    public List<Map<String, Object>> getOntologyAssets(String entityId) {
        return repository.findAssetsByOntologyEntityId(entityId).stream()
            .map(this::assetToMap)
            .collect(Collectors.toList());
    }

    // ═══════════════ 仪表盘统计 ═══════════════════

    public Map<String, Object> getDashboard() {
        long totalAssets = repository.countAssets();
        long publishedCount = repository.countPublishedAssets();
        long pendingRequests = repository.countPendingRequests();
        BigDecimal avgRating = repository.getAvgRating();
        if (avgRating == null) avgRating = BigDecimal.ZERO;
        List<Map<String, Object>> topCategories = repository.getTopCategories(5);

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("totalAssets", totalAssets);
        dashboard.put("publishedCount", publishedCount);
        dashboard.put("pendingRequests", pendingRequests);
        dashboard.put("avgRating", avgRating);
        dashboard.put("topCategories", topCategories);
        return dashboard;
    }

    // ═══════════════ 映射方法 ═══════════════════

    private Map<String, Object> assetToMap(MarketplaceAssetEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("name", entity.getName());
        map.put("description", entity.getDescription());
        map.put("category", entity.getCategory());
        map.put("owner", entity.getOwner());
        map.put("rating", entity.getRating());
        map.put("popularity", entity.getPopularity());
        map.put("status", entity.getStatus());
        map.put("ontologyEntityId", entity.getOntologyEntityId());
        map.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);

        int hotScore = (entity.getPopularity() != null ? entity.getPopularity() : 0)
            + (entity.getRating() != null ? entity.getRating().multiply(BigDecimal.valueOf(100)).intValue() : 0);
        map.put("hotScore", hotScore);
        return map;
    }

    private Map<String, Object> requestToMap(MarketplaceAccessRequestEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("assetId", entity.getAssetId());
        map.put("reason", entity.getReason());
        map.put("applicant", entity.getApplicant());
        map.put("status", entity.getStatus());
        map.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        map.put("updatedAt", entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        return map;
    }

    private Map<String, Object> reviewToMap(MarketplaceReviewEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("assetId", entity.getAssetId());
        map.put("reviewer", entity.getReviewer());
        map.put("rating", entity.getRating());
        map.put("comment", entity.getComment());
        map.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        return map;
    }

    private Long parseLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number n) return n.longValue();
        try { return Long.valueOf(String.valueOf(obj)); }
        catch (NumberFormatException e) { return null; }
    }

    private BigDecimal parseBigDecimal(Object obj) {
        if (obj == null) return null;
        if (obj instanceof BigDecimal bd) return bd;
        if (obj instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(String.valueOf(obj)); }
        catch (NumberFormatException e) { return null; }
    }
}
