package com.chinacreator.gzcm.market;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * JdbcTemplate 仓库 — 数据市场 CRUD（资产、访问申请、评价）
 */
@Repository
public class MarketplaceRepository {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceRepository.class);

    private final JdbcTemplate jdbc;

    public MarketplaceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ═══════════════ RowMappers ═══════════════════

    private final RowMapper<MarketplaceAssetEntity> ASSET_MAPPER = (rs, rowNum) -> {
        MarketplaceAssetEntity e = new MarketplaceAssetEntity();
        e.setId(rs.getLong("id"));
        e.setName(rs.getString("name"));
        e.setDescription(rs.getString("description"));
        e.setCategory(rs.getString("category"));
        e.setOwner(rs.getString("owner"));
        e.setRating(rs.getBigDecimal("rating"));
        e.setPopularity(rs.getInt("popularity"));
        e.setStatus(rs.getString("status"));
        e.setOntologyEntityId(rs.getString("ontology_entity_id"));
        e.setCreatedAt(rs.getTimestamp("created_at") != null
            ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        return e;
    };

    private final RowMapper<MarketplaceAccessRequestEntity> REQ_MAPPER = (rs, rowNum) -> {
        MarketplaceAccessRequestEntity e = new MarketplaceAccessRequestEntity();
        e.setId(rs.getLong("id"));
        e.setAssetId(rs.getLong("asset_id"));
        e.setReason(rs.getString("reason"));
        e.setApplicant(rs.getString("applicant"));
        e.setStatus(rs.getString("status"));
        e.setCreatedAt(rs.getTimestamp("created_at") != null
            ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        e.setUpdatedAt(rs.getTimestamp("updated_at") != null
            ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
        return e;
    };

    private final RowMapper<MarketplaceReviewEntity> REVIEW_MAPPER = (rs, rowNum) -> {
        MarketplaceReviewEntity e = new MarketplaceReviewEntity();
        e.setId(rs.getLong("id"));
        e.setAssetId(rs.getLong("asset_id"));
        e.setReviewer(rs.getString("reviewer"));
        e.setRating(rs.getBigDecimal("rating"));
        e.setComment(rs.getString("comment"));
        e.setCreatedAt(rs.getTimestamp("created_at") != null
            ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        return e;
    };

    // ═══════════════ Asset CRUD ═══════════════════

    public List<MarketplaceAssetEntity> findAllAssets(String sort, int limit) {
        String orderClause = switch (sort.toLowerCase()) {
            case "recommended" -> "rating DESC";
            case "newest"      -> "created_at DESC";
            case "highvalue"   -> "popularity DESC";
            default            -> "popularity DESC";
        };
        return jdbc.query(
            "SELECT * FROM ecos_marketplace_asset WHERE status = 'PUBLISHED' ORDER BY " + orderClause + " LIMIT ?",
            ASSET_MAPPER, limit);
    }

    public Optional<MarketplaceAssetEntity> findAssetById(Long id) {
        List<MarketplaceAssetEntity> list = jdbc.query(
            "SELECT * FROM ecos_marketplace_asset WHERE id = ?", ASSET_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public long countAssets() {
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM ecos_marketplace_asset", Long.class);
        return c != null ? c : 0;
    }

    public long insertAsset(MarketplaceAssetEntity entity) {
        String sql = """
            INSERT INTO ecos_marketplace_asset (name, description, category, owner, rating, popularity, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
            """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, entity.getName());
            ps.setString(2, entity.getDescription());
            ps.setString(3, entity.getCategory());
            ps.setString(4, entity.getOwner());
            ps.setBigDecimal(5, entity.getRating() != null ? entity.getRating() : BigDecimal.ZERO);
            ps.setInt(6, entity.getPopularity() != null ? entity.getPopularity() : 0);
            ps.setString(7, entity.getStatus() != null ? entity.getStatus() : "PUBLISHED");
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) entity.setId(key.longValue());
        return entity.getId() != null ? entity.getId() : 0;
    }

    public int updateAsset(Long id, String name, String description, String category, String status) {
        return jdbc.update("""
            UPDATE ecos_marketplace_asset SET
                name = COALESCE(?, name),
                description = COALESCE(?, description),
                category = COALESCE(?, category),
                status = COALESCE(?, status)
            WHERE id = ?
            """, name, description, category, status, id);
    }

    public int updateAssetRating(Long assetId, BigDecimal rating) {
        return jdbc.update(
            "UPDATE ecos_marketplace_asset SET rating = ? WHERE id = ?", rating, assetId);
    }

    // ═══════════════ Access Request CRUD ═══════════════

    public int insertAccessRequest(MarketplaceAccessRequestEntity entity) {
        return jdbc.update("""
            INSERT INTO ecos_marketplace_access_request (asset_id, reason, applicant, status, created_at, updated_at)
            VALUES (?, ?, ?, 'PENDING', NOW(), NOW())
            """, entity.getAssetId(), entity.getReason(), entity.getApplicant());
    }

    public List<MarketplaceAccessRequestEntity> findAllRequests(String status, String applicant) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ecos_marketplace_access_request WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status.toUpperCase());
        }
        if (applicant != null && !applicant.isBlank()) {
            sql.append(" AND applicant = ?");
            params.add(applicant);
        }
        sql.append(" ORDER BY created_at DESC");
        return jdbc.query(sql.toString(), REQ_MAPPER, params.toArray());
    }

    public Optional<MarketplaceAccessRequestEntity> findRequestById(Long id) {
        List<MarketplaceAccessRequestEntity> list = jdbc.query(
            "SELECT * FROM ecos_marketplace_access_request WHERE id = ?", REQ_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int updateRequestStatus(Long id, String status) {
        return jdbc.update(
            "UPDATE ecos_marketplace_access_request SET status = ?, updated_at = NOW() WHERE id = ?", status, id);
    }

    // ═══════════════ Review CRUD ═══════════════

    public long insertReview(MarketplaceReviewEntity entity) {
        String sql = """
            INSERT INTO ecos_marketplace_review (asset_id, reviewer, rating, comment, created_at)
            VALUES (?, ?, ?, ?, NOW())
            """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, entity.getAssetId());
            ps.setString(2, entity.getReviewer());
            ps.setBigDecimal(3, entity.getRating());
            ps.setString(4, entity.getComment());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) entity.setId(key.longValue());
        return entity.getId();
    }

    public List<MarketplaceReviewEntity> findReviewsByAssetId(Long assetId) {
        return jdbc.query(
            "SELECT * FROM ecos_marketplace_review WHERE asset_id = ? ORDER BY created_at DESC",
            REVIEW_MAPPER, assetId);
    }

    // ═══════════════ Ontology Association ═══════════════

    public int updateAssetOntology(Long assetId, String ontologyEntityId) {
        return jdbc.update(
            "UPDATE ecos_marketplace_asset SET ontology_entity_id = ? WHERE id = ?",
            ontologyEntityId, assetId);
    }

    public List<MarketplaceAssetEntity> findAssetsByOntologyEntityId(String entityId) {
        return jdbc.query(
            "SELECT * FROM ecos_marketplace_asset WHERE ontology_entity_id = ?",
            ASSET_MAPPER, entityId);
    }

    // ═══════════════ Dashboard Stats ═══════════════

    public long countPublishedAssets() {
        Long c = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ecos_marketplace_asset WHERE status = 'PUBLISHED'", Long.class);
        return c != null ? c : 0;
    }

    public long countPendingRequests() {
        Long c = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ecos_marketplace_access_request WHERE status = 'PENDING'", Long.class);
        return c != null ? c : 0;
    }

    public BigDecimal getAvgRating() {
        return jdbc.queryForObject(
            "SELECT COALESCE(AVG(rating), 0) FROM ecos_marketplace_asset WHERE status = 'PUBLISHED'",
            BigDecimal.class);
    }

    public List<Map<String, Object>> getTopCategories(int limit) {
        return jdbc.queryForList(
            "SELECT category, COUNT(*) AS cnt FROM ecos_marketplace_asset" +
            " WHERE status = 'PUBLISHED' GROUP BY category ORDER BY cnt DESC LIMIT ?",
            limit);
    }

    // ═══════════════ Search ═══════════════

    public List<MarketplaceAssetEntity> searchAssets(String keyword, String category) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ecos_marketplace_asset WHERE status = 'PUBLISHED'");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (name ILIKE ? OR description ILIKE ?)");
            String like = "%" + keyword + "%";
            params.add(like);
            params.add(like);
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        sql.append(" ORDER BY popularity DESC LIMIT 50");
        return jdbc.query(sql.toString(), ASSET_MAPPER, params.toArray());
    }
}
