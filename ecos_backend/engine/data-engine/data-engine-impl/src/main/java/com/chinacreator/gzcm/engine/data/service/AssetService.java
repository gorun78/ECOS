package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.event.DataAssetSecurityTaggedEvent;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.data.dto.*;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.sysman.abac.model.AbacContext;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPermissionChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 数据资产 + 分级分类服务（PMO-data10）。
 *
 * <p>权威：data-engine 持有 (托管) {@code td_data_resource}（物理层）
 * 与 {@code ecos_data.ecos_data_asset / ecos_data_asset_field}（业务层），
 * 对外提供：<b>资产 CRUD</b> + <b>分级分类查询</b> + <b>字段级打标</b> +
 * <b>事件发布</b>（人工确认 → {@code ecos.data.security-tagged}）。</p>
 *
 * <p>架构边界（ADR-DATA10-01，见 docs/20-services/datanet/data-asset-security-classification-design.md）：</p>
 * <ul>
 *   <li>物理层 {@code td_data_resource}（V150 存量）— 字段元数据 (schema/DDL)</li>
 *   <li>业务层 {@code ecos_data.ecos_data_asset}（V154 新增）— 业务字段 + 分级 + 分类</li>
 *   <li>字段级敏感度 {@code ecos_data.ecos_data_asset_field}（V154 新增）— 脱敏/CLS 落到"列"</li>
 *   <li>打标事件 → security-engine 消费：CLS 列级过滤 + 行级 RLS 准入 + 脱敏</li>
 *   <li>LLM 推荐仅写 recommend_level，confirmed 必须人工点头才发事件（安全红线）</li>
 * </ul>
 *
 * @author DataBridge Datanet Team
 */
@Service
public class AssetService {

    private static final Logger log = LoggerFactory.getLogger(AssetService.class);

    /** 敏感字段 data_type 白名单（IR06 不暴露任意字符串） */
    private static final Set<String> VALID_DATA_TYPES = Set.of(
        "ID_CARD", "PHONE", "EMAIL", "BANK_CARD", "AMOUNT", "ADDRESS", "GENERAL");

    /** 字段敏感度白名单（L1..L4） */
    private static final Set<String> VALID_LEVELS = Set.of("L1", "L2", "L3", "L4");

    /** 脱敏策略白名单 */
    private static final Set<String> VALID_MASK_STRATEGIES = Set.of(
        "none", "middle4", "prefix3", "suffix4", "full");

    /** 业务分类状态白名单 */
    private static final Set<String> VALID_CATEGORY_STATUS = Set.of(
        "PENDING", "PENDING_CONFIRMATION", "CONFIRMED", "DISRUPTED");

    /** ID 白名单正则（防 SQL 注入 + 标号同时用） */
    private static final Pattern ID_PAT = Pattern.compile("^[a-zA-Z0-9_-]+$");

    /** 批量上限（IR06） */
    private static final int BATCH_LIMIT = 100;

    private final JdbcTemplate jdbc;
    private final ObjectProvider<EventBusService> eventBusProvider;
    private final ObjectProvider<IAbacPermissionChecker> abacProvider;

    public AssetService(JdbcTemplate jdbc, ObjectProvider<EventBusService> eventBusProvider,
                        ObjectProvider<IAbacPermissionChecker> abacProvider) {
        this.jdbc = jdbc;
        this.eventBusProvider = eventBusProvider;
        this.abacProvider = abacProvider;
    }

    /**
     * 打标前 ABAC 校验（security-engine 裁决）：
     * action=data.asset.security-tag，resource 带 assetId/resourceId/domain。
     * 若 ABAC 不可用则放行（生产告警）。
     * DENY 抛 {@link SecurityException}；NOT_APPLICABLE/PERMIT 放行。
     */
    private void abacCheck(String assetId, String resourceId, String operator, String action) {
        IAbacPermissionChecker checker = abacProvider.getIfAvailable();
        if (checker == null) {
            log.warn("ABAC 不可用，动作 {} 放行 (asset={} resource={})", action, assetId, resourceId);
            return;
        }
        AbacContext ctx = new AbacContext();
        Map<String, Object> subject = new HashMap<>();
        subject.put("userId", operator == null ? "system" : operator);
        ctx.setSubject(subject);
        Map<String, Object> resource = new HashMap<>();
        resource.put("assetId", assetId);
        resource.put("resourceId", resourceId);
        resource.put("action", action);
        ctx.setResource(resource);
        try {
            IAbacPermissionChecker.Decision d = checker.check(ctx);
            if (d == IAbacPermissionChecker.Decision.DENY) {
                log.warn("ABAC DENY asset={} action={} by={}", assetId, action, operator);
                throw new SecurityException("ASSET-040: ABAC 拒绝 (asset=" + assetId + " action=" + action + ")");
            }
        } catch (SecurityException se) {
            throw se;
        } catch (IAbacPermissionChecker.PolicyEvaluationException e) {
            // 默认 Fail-Closed 安全红线（PMO-data10）
            log.error("ABAC 策略评估异常 (asset={} action={}): {}", assetId, action, e.getMessage(), e);
            throw new SecurityException("ASSET-041: ABAC 策略评估失败，操作被拒绝", e);
        }
    }

    // ── CRUD：资产 ─────────────────────────────────────────────

    /** 创建资产（资产全新生成 UUID；含唯一索引约束 (resource_id, domain)）。 */
    @Transactional
    public DataAssetVO createAsset(DataAssetSaveDTO dto) {
        requireId("resourceId", dto.getResourceId());
        requireId("assetName", dto.getAssetName());
        String assetId = randomId();
        String level = normalizeLevel(dto.getSensitivityLevel());
        String domain = (dto.getDomain() == null || dto.getDomain().isBlank()) ? "default" : dto.getDomain();
        String operator = (dto.getOperator() == null || dto.getOperator().isBlank()) ? "system" : dto.getOperator();
        Timestamp now = new Timestamp(System.currentTimeMillis());

        // 1. 校验 resource_id 存在于 td_data_resource（V150 存量表，列简校验，IR01 经 data-engine 三层访问）
        Integer resourceExists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM td_data_resource WHERE resource_id = ?", Integer.class, dto.getResourceId());
        if (resourceExists == null || resourceExists == 0) {
            throw new IllegalArgumentException("ASSET-001: resource_id 不存在于 td_data_resource");
        }

        // 2. 防重 (resource_id, domain)
        Integer dup = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ecos_data.ecos_data_asset WHERE resource_id = ? AND domain = ?",
            Integer.class, dto.getResourceId(), domain);
        if (dup != null && dup > 0) {
            throw new IllegalArgumentException("ASSET-002: 同 domain 下该资源已存在业务资产");
        }

        // 3. 同步冗余 td_data_resource 的 resourceName/resourceType/layer/zone 等元数据
        List<Map<String, Object>> resourceRows = jdbc.queryForList(
            "SELECT resource_id, resource_name, resource_type, datasource_id, " +
            "description, field_count, record_count, layer, zone, last_sync_time " +
            "FROM td_data_resource WHERE resource_id = ?", dto.getResourceId());
        if (resourceRows.isEmpty()) {
            throw new IllegalStateException("ASSET-003: td_data_resource 查询结果异常");
        }
        DataAssetVO vo = fromPhysicalResourceRow(resourceRows.get(0));

        // 4. 写入 asset
        jdbc.update(
            "INSERT INTO ecos_data.ecos_data_asset (" +
            "  asset_id, resource_id, asset_name, business_desc, owner, owner_org, data_grain, " +
            "  category_id, sensitivity_level, category_status, domain, " +
            "  create_time, update_time, create_by, update_by, version_no, is_deleted" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, '1', 0)",
            assetId,
            dto.getResourceId(),
            dto.getAssetName().trim(),
            dto.getBusinessDesc(),
            dto.getOwner(),
            dto.getOwnerOrg(),
            dto.getDataGrain(),
            dto.getCategoryId(),
            level,
            domain,
            now, now, operator, operator
        );

        // 5. 同步补齐 label/缓存（冗余字段 business-level 在 VO 里）
        vo.setAssetId(assetId);
        vo.setAssetName(dto.getAssetName().trim());
        vo.setBusinessDesc(dto.getBusinessDesc());
        vo.setOwner(dto.getOwner());
        vo.setOwnerOrg(dto.getOwnerOrg());
        vo.setDataGrain(dto.getDataGrain());
        vo.setCategoryId(dto.getCategoryId());
        vo.setSensitivityLevel(level);
        vo.setCategoryStatus("PENDING");
        vo.setDomain(domain);

        // 6. 审计 Kafka（ST06；失败不阻塞主路径）
        try {
            EventBusService bus = eventBusProvider.getIfAvailable();
            if (bus != null) {
                bus.publish(KafkaTopics.AUDIT, Map.of(
                    "action", "asset.create",
                    "asset_id", assetId,
                    "operator", operator,
                    "resource_id", dto.getResourceId()));
            }
        } catch (Exception e) {
            log.warn("asset.create Kafka 审计失败（不阻塞）: {}", e.getMessage());
        }

        log.info("Asset created: id={} resource={} level={}", assetId, dto.getResourceId(), level);
        return vo;
    }

    /** 更新资产（COALESCE 语义 — null 不修改；version_no 自增）。 */
    @Transactional
    public DataAssetVO updateAsset(String assetId, DataAssetSaveDTO dto) {
        requireId("assetId", assetId);
        DataAssetVO existing = getAsset(assetId);
        if (existing == null) {
            throw new IllegalArgumentException("ASSET-010: asset 不存在: " + assetId);
        }
        String level = (dto.getSensitivityLevel() == null)
            ? existing.getSensitivityLevel() : normalizeLevel(dto.getSensitivityLevel());
        String operator = (dto.getOperator() == null || dto.getOperator().isBlank()) ? "system" : dto.getOperator();

        // 001 级校验
        if (dto.getSensitivityLevel() != null && !VALID_LEVELS.contains(dto.getSensitivityLevel())) {
            throw new IllegalArgumentException("ASSET-011: 非法敏感度 " + dto.getSensitivityLevel());
        }

        jdbc.update(
            "UPDATE ecos_data.ecos_data_asset SET " +
            "  asset_name = COALESCE(?, asset_name), business_desc = COALESCE(?, business_desc), " +
            "  owner = COALESCE(?, owner), owner_org = COALESCE(?, owner_org), " +
            "  data_grain = COALESCE(?, data_grain), category_id = COALESCE(?, category_id), " +
            "  sensitivity_level = COALESCE(?, sensitivity_level), update_time = NOW(), " +
            "  update_by = ?, version_no = (CAST(version_no AS INT) + 1)::text " +
            "WHERE asset_id = ? AND is_deleted = 0",
            optStr(dto.getAssetName()), dto.getBusinessDesc(), dto.getOwner(), dto.getOwnerOrg(),
            optStr(dto.getDataGrain()), optStr(dto.getCategoryId()), level, operator, assetId);

        log.info("Asset updated: id={} level={}", assetId, level);
        return getAsset(assetId);
    }

    /** 逻辑删除资产（IR03 不真删；只 is_deleted=1 + version+1）。 */
    @Transactional
    public boolean deleteAsset(String assetId, String operator) {
        requireId("assetId", assetId);
        String op = (operator == null || operator.isBlank()) ? "system" : operator;
        int n = jdbc.update(
            "UPDATE ecos_data.ecos_data_asset SET is_deleted = 1, update_time = NOW(), " +
            "  update_by = ?, version_no = (CAST(version_no AS INT) + 1)::text WHERE asset_id = ? AND is_deleted = 0",
            op, assetId);
        if (n > 0) {
            log.info("Asset soft-deleted: id={} by={}", assetId, op);
        }
        return n > 0;
    }

    /** 查资产详情（含 level dictionary join + category name join + 命中字段计数）。 */
    public DataAssetVO getAsset(String assetId) {
        requireId("assetId", assetId);
        try {
            List<DataAssetVO> rows = jdbc.query(
                "SELECT a.asset_id, a.resource_id, r.resource_name, r.resource_type, r.datasource_id, " +
                "  a.asset_name, a.business_desc, a.owner, a.owner_org, a.data_grain, " +
                "  c.category_id, c.name AS category_name, " +
                "  a.sensitivity_level, l.level_name, a.category_status, " +
                "  a.last_tagged_by, a.last_tagged_at, a.domain, " +
                "  r.description, r.field_count, r.record_count, r.layer, r.zone, r.last_sync_time, " +
                "  (SELECT COUNT(*) FROM ecos_data.ecos_data_asset_field af " +
                "    WHERE af.asset_id = a.asset_id AND af.confirmed = TRUE AND af.is_deleted = 0) " +
                "    AS confirmed_field_count " +
                "FROM ecos_data.ecos_data_asset a " +
                "LEFT JOIN td_data_resource r ON r.resource_id = a.resource_id " +
                "LEFT JOIN ecos_data.ecos_data_category_tree c ON c.category_id = a.category_id AND c.is_deleted = 0 " +
                "LEFT JOIN ecos_data.ecos_data_level_def l ON l.level_code = a.sensitivity_level " +
                "WHERE a.asset_id = ? AND a.is_deleted = 0",
                assetRowMapper(),
                assetId);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            log.error("getAsset 失败 (assetId={}): {}", assetId, e.getMessage(), e);
            return null;
        }
    }

    // ── 分页列表 ─────────────────────────────────────────────

    /** 资产分页列表（按数据湖存储分层规范 §五 合法性矩阵过滤）。 */
    public List<DataAssetVO> listAssets(DataAssetQueryDTO q) {
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(
            "a.asset_id, a.resource_id, r.resource_name, r.resource_type, r.datasource_id, " +
            "  a.asset_name, a.business_desc, a.owner, a.owner_org, a.data_grain, " +
            "  c.category_id, c.name AS category_name, " +
            "  a.sensitivity_level, l.level_name, a.category_status, " +
            "  a.last_tagged_by, a.last_tagged_at, a.domain, " +
            "  r.description, r.field_count, r.record_count, r.layer, r.zone, r.last_sync_time, " +
            "  (SELECT COUNT(*) FROM ecos_data.ecos_data_asset_field af " +
            "    WHERE af.asset_id = a.asset_id AND af.confirmed = TRUE AND af.is_deleted = 0) " +
            "    AS confirmed_field_count ");
        sql.append("FROM ecos_data.ecos_data_asset a ");
        sql.append("LEFT JOIN td_data_resource r ON r.resource_id = a.resource_id ");
        sql.append("LEFT JOIN ecos_data.ecos_data_category_tree c ON c.category_id = a.category_id AND c.is_deleted = 0 ");
        sql.append("LEFT JOIN ecos_data.ecos_data_level_def l ON l.level_code = a.sensitivity_level ");
        sql.append("WHERE a.is_deleted = 0");

        List<Object> params = new ArrayList<>();
        if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
            sql.append(" AND (a.asset_name ILIKE ? OR a.business_desc ILIKE ? OR r.description ILIKE ?)");
            String kw = "%" + q.getKeyword() + "%";
            params.add(kw); params.add(kw); params.add(kw);
        }
        if (q.getSensitivityLevel() != null && !q.getSensitivityLevel().isBlank()) {
            sql.append(" AND a.sensitivity_level = ?");
            params.add(q.getSensitivityLevel());
        }
        if (q.getCategoryId() != null && !q.getCategoryId().isBlank()) {
            sql.append(" AND a.category_id = ?");
            params.add(q.getCategoryId());
        }
        if (q.getLayer() != null && !q.getLayer().isBlank()) {
            sql.append(" AND r.layer = ?");
            params.add(q.getLayer());
        }
        if (q.getZone() != null && !q.getZone().isBlank()) {
            sql.append(" AND r.zone = ?");
            params.add(q.getZone());
        }
        if (q.getDatasourceId() != null && !q.getDatasourceId().isBlank()) {
            sql.append(" AND r.datasource_id = ?");
            params.add(q.getDatasourceId());
        }
        if (q.getOwner() != null && !q.getOwner().isBlank()) {
            sql.append(" AND a.owner = ?");
            params.add(q.getOwner());
        }
        if (q.getResourceType() != null && !q.getResourceType().isBlank()) {
            sql.append(" AND r.resource_type = ?");
            params.add(q.getResourceType());
        }
        if (q.getDomain() != null && !q.getDomain().isBlank()) {
            sql.append(" AND a.domain = ?");
            params.add(q.getDomain());
        }
        if (q.getCategoryStatus() != null && !q.getCategoryStatus().isBlank()) {
            sql.append(" AND a.category_status = ?");
            params.add(q.getCategoryStatus());
        }
        sql.append(" ORDER BY a.update_time DESC");
        int page = (q.getPage() == null || q.getPage() < 1) ? 1 : q.getPage();
        int pageSize = (q.getPageSize() == null || q.getPageSize() < 1) ? 20 : q.getPageSize();
        sql.append(" LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add((page - 1) * pageSize);
        return jdbc.query(sql.toString(), assetRowMapper(), params.toArray());
    }

    // ── 字段列表 / 打标 ─────────────────────────────────────

    /** 字段级敏感度分页列表（可选 confirmed=true 过滤）。 */
    public DataAssetFieldPageVO listFields(String assetId, Boolean confirmed, int page, int pageSize) {
        requireId("assetId", assetId);
        DataAssetFieldPageVO result = new DataAssetFieldPageVO();
        result.setPage(page);
        result.setPageSize(pageSize);

        // 总数（不带 confirmed 过滤）
        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ecos_data.ecos_data_asset_field WHERE asset_id = ? AND is_deleted = 0",
            Long.class, assetId);
        result.setTotal(total == null ? 0L : total);

        // confirmed 总数
        Long confirmedCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ecos_data.ecos_data_asset_field " +
            "WHERE asset_id = ? AND confirmed = TRUE AND is_deleted = 0", Long.class, assetId);
        result.setConfirmedCount(confirmedCount == null ? 0 : confirmedCount.intValue());

        // 当页
        StringBuilder sql = new StringBuilder(
            "SELECT field_asset_id, asset_id, field_id, field_name, field_type, " +
            "data_type, field_sensitivity, mask_strategy, recommend_level, " +
            "recommend_source, confirmed " +
            "FROM ecos_data.ecos_data_asset_field WHERE asset_id = ? AND is_deleted = 0");
        List<Object> params = new ArrayList<>();
        params.add(assetId);
        if (confirmed != null) {
            if (confirmed) {
                sql.append(" AND confirmed = TRUE");
            } else {
                sql.append(" AND (confirmed = FALSE OR confirmed IS NULL)");
            }
        }
        sql.append(" ORDER BY field_name ASC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add((page - 1) * pageSize);
        List<DataAssetFieldVO> items = jdbc.query(sql.toString(), fieldRowMapper(), params.toArray());
        result.setItems(items);
        return result;
    }

    /**
     * 字段级打标（安全红线：人工确认才触发 Kafka 事件 <code>ecos.data.security-tagged</code>）。
     * 打标前强制 ABAC 裁决（security-engine 内部裁决，不暴露跨引擎 REST）。
     */
    @Transactional
    public DataAssetVO tagAssetFields(String assetId, DataAssetFieldTagDTO dto) {
        requireId("assetId", assetId);
        if (dto == null || dto.getFields() == null || dto.getFields().isEmpty()) {
            throw new IllegalArgumentException("ASSET-020: fields 不能为空");
        }
        if (dto.getFields().size() > BATCH_LIMIT) {
            throw new IllegalArgumentException("ASSET-021: 单次打标字段数超过上限 " + BATCH_LIMIT);
        }
        DataAssetVO asset = getAsset(assetId);
        if (asset == null) {
            throw new IllegalArgumentException("ASSET-022: asset 不存在: " + assetId);
        }
        // ABAC 前置（Fail-Closed）— 拒后即抛 SecurityException，后续仅允许 NO-OP
        abacCheck(assetId, asset.getResourceId(), dto.getOperator(), "data.asset.security-tag");
        boolean confirmed = Boolean.TRUE.equals(dto.getConfirmed());
        String operator = (dto.getOperator() == null || dto.getOperator().isBlank()) ? "system" : dto.getOperator();

        int updated = 0;
        List<DataAssetSecurityTaggedEvent.TaggedField> eventFields = new ArrayList<>();
        for (DataAssetFieldTagDTO.FieldTagItem it : dto.getFields()) {
            if (it== null || it.getFieldId() == null) continue;
            if (it.getFieldId().length() == 0) continue;
            if (!ID_PAT.matcher(it.getFieldId()).matches()) {
                throw new IllegalArgumentException("ASSET-023: 非法 fieldId 格式: " + it.getFieldId());
            }
            String level = it.getFieldSensitivity() == null ? "L1" : it.getFieldSensitivity();
            if (!VALID_LEVELS.contains(level)) {
                throw new IllegalArgumentException("ASSET-024: 非法字段敏感度 " + level);
            }
            String dataType = (it.getDataType() == null || it.getDataType().isBlank()) ? "GENERAL" : it.getDataType();
            if (!VALID_DATA_TYPES.contains(dataType)) {
                throw new IllegalArgumentException("ASSET-025: 非法数据类型 " + dataType);
            }
            String mask = (it.getMaskStrategy() == null || it.getMaskStrategy().isBlank()) ? "none" : it.getMaskStrategy();
            if (!VALID_MASK_STRATEGIES.contains(mask)) {
                throw new IllegalArgumentException("ASSET-026: 非法 mask_strategy " + mask);
            }
            String fieldAssetId = randomId();
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            // 唯一约束 (asset_id, field_id) —— V154 仅 PK + 普通 index，应用层保证
            Integer dup = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ecos_data.ecos_data_asset_field " +
                "WHERE asset_id = ? AND field_id = ? AND is_deleted = 0",
                Integer.class, assetId, it.getFieldId());
            if (dup != null && dup > 0) {
                // 已经是既有字段，更新
                jdbc.update(
                    "UPDATE ecos_data.ecos_data_asset_field SET " +
                    "field_name = COALESCE(?, field_name), " +
                    "field_type = COALESCE(?, field_type), " +
                    "data_type = ?, field_sensitivity = ?, mask_strategy = ?, " +
                    "recommend_level = COALESCE(?, recommend_level), " +
                    "recommend_source = COALESCE(?, recommend_source), " +
                    "confirmed = ?, update_time = NOW(), update_by = ?, " +
                    "version_no = (CAST(version_no AS INT) + 1)::text " +
                    "WHERE asset_id = ? AND field_id = ? AND is_deleted = 0",
                    optStr(it.getFieldName()), optStr(it.getFieldType()),
                    dataType, level, mask, it.getRecommendLevel(), it.getRecommendSource(),
                    confirmed, operator, assetId, it.getFieldId());
            } else {
                jdbc.update(
                    "INSERT INTO ecos_data.ecos_data_asset_field (" +
                    "  field_asset_id, asset_id, field_id, field_name, field_type, " +
                    "  data_type, field_sensitivity, mask_strategy, " +
                    "  recommend_level, recommend_source, confirmed, " +
                    "  create_time, update_time, create_by, update_by, version_no, is_deleted" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '1', 0)",
                    fieldAssetId, assetId, it.getFieldId(),
                    it.getFieldName() != null ? it.getFieldName() : it.getFieldId(),
                    it.getFieldType(), dataType, level, mask,
                    it.getRecommendLevel(), it.getRecommendSource(),
                    confirmed, now, now, operator, operator);
            }
            // 收集事件字段（仅 confirmed 命中，安全阀）
            if (confirmed && (level.equals("L3") || level.equals("L4"))) {
                eventFields.add(new DataAssetSecurityTaggedEvent.TaggedField(
                    it.getFieldId(), it.getFieldName(), dataType, level, mask));
            }
            updated++;
        }

        // 1. 同步资产敏感度（取 max level；都 L1 保持原值）+ 状态
        if (confirmed) {
            String maxLevel = jdbc.queryForObject(
                "SELECT COALESCE(MAX(field_sensitivity), 'L1') FROM ecos_data.ecos_data_asset_field " +
                "WHERE asset_id = ? AND confirmed = TRUE AND is_deleted = 0",
                String.class, assetId);
            if (maxLevel == null) maxLevel = "L1";
            jdbc.update(
                "UPDATE ecos_data.ecos_data_asset SET sensitivity_level = ?, " +
                "  category_status = 'CONFIRMED', last_tagged_by = ?, last_tagged_at = NOW(), " +
                "  update_time = NOW(), update_by = ?, version_no = (CAST(version_no AS INT) + 1)::text " +
                "WHERE asset_id = ? AND is_deleted = 0",
                maxLevel, operator, operator, assetId);

            // 2. 发 Kafka 事件（data-engine → security-engine 消费生成 CLS/RLS）
            if (!eventFields.isEmpty()) {
                publishSecurityTaggedEvent(asset, maxLevel, eventFields, operator);
            } else {
                log.info("资产 {} 打标完成（无 L3/L4 命中字段，无需发安全事件），by={}", assetId, operator);
            }
        } else {
            jdbc.update(
                "UPDATE ecos_data.ecos_data_asset SET category_status = 'PENDING_CONFIRMATION', " +
                "  update_time = NOW(), update_by = ?, version_no = (CAST(version_no AS INT) + 1)::text " +
                "WHERE asset_id = ? AND is_deleted = 0",
                operator, assetId);
        }

        log.info("Asset {} 打标字段 {} 个，confirmed={}，by={}", assetId, updated, confirmed, operator);
        return getAsset(assetId);
    }

    // ── 公开：分级/分类字典 ─────────────────────────────────

    /** 4 级字典（data-engine 唯一权威；security 不应再自持）。 */
    public List<Map<String, Object>> listLevelDefs() {
        return jdbc.queryForList(
            "SELECT level_code, level_value, level_name, level_description, " +
            "  mask_strategy_json, rls_strategy_json, sort_order " +
            "FROM ecos_data.ecos_data_level_def WHERE is_deleted = 0 ORDER BY level_value ASC");
    }

    /** 业务分类树（≤3 级，按 level 排序，便于前端 grid 渲染）。 */
    public List<Map<String, Object>> listCategoryTree() {
        return jdbc.queryForList(
            "SELECT category_id, name, parent_id, level, description, icon " +
            "FROM ecos_data.ecos_data_category_tree WHERE is_deleted = 0 ORDER BY level, name");
    }

    // ── 校验 / 工具 ────────────────────────────────────────

    /** 等级规范化（默认 L1，大小写不敏感转 L1..L4）。 */
    private static String normalizeLevel(String raw) {
        if (raw == null || raw.isBlank()) return "L1";
        String v = raw.trim().toUpperCase();
        if (!VALID_LEVELS.contains(v)) {
            throw new IllegalArgumentException("ASSET-030: 非法敏感度 " + raw);
        }
        return v;
    }

    private static void requireId(String name, String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ASSET-000: " + name + " 不能为空");
        }
        if (id.length() > 128 || !ID_PAT.matcher(id).matches()) {
            throw new IllegalArgumentException("ASSET-000: 非法 " + name + " 格式: " + id);
        }
    }

    private static String optStr(String s) { return (s == null || s.isBlank()) ? null : s; }

    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 资产行映射器（共享给 list/get 查询）。 */
    private static org.springframework.jdbc.core.RowMapper<DataAssetVO> assetRowMapper() {
        return new org.springframework.jdbc.core.RowMapper<>() {
            @Override
            public DataAssetVO mapRow(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
                DataAssetVO vo = new DataAssetVO();
                vo.setAssetId(rs.getString("asset_id"));
                vo.setResourceId(rs.getString("resource_id"));
                vo.setResourceName(rs.getString("resource_name"));
                vo.setResourceType(rs.getString("resource_type"));
                vo.setDatasourceId(rs.getString("datasource_id"));
                vo.setAssetName(rs.getString("asset_name"));
                vo.setBusinessDesc(rs.getString("business_desc"));
                vo.setOwner(rs.getString("owner"));
                vo.setOwnerOrg(rs.getString("owner_org"));
                vo.setDataGrain(rs.getString("data_grain"));
                vo.setCategoryId(rs.getString("category_id"));
                vo.setCategoryName(rs.getString("category_name"));
                vo.setSensitivityLevel(rs.getString("sensitivity_level"));
                vo.setLevelName(rs.getString("level_name"));
                vo.setCategoryStatus(rs.getString("category_status"));
                vo.setLastTaggedBy(rs.getString("last_tagged_by"));
                java.sql.Timestamp lastTaggedAt = rs.getTimestamp("last_tagged_at");
                if (lastTaggedAt != null) vo.setLastTaggedAt(lastTaggedAt.toLocalDateTime());
                vo.setDomain(rs.getString("domain"));
                vo.setDescription(rs.getString("description"));
                vo.setFieldCount(rs.getObject("field_count", Integer.class));
                vo.setRecordCount(rs.getObject("record_count", Long.class));
                vo.setLayer(rs.getString("layer"));
                vo.setZone(rs.getString("zone"));
                java.sql.Timestamp lastSync = rs.getTimestamp("last_sync_time");
                if (lastSync != null) vo.setLastSyncTime(lastSync.toLocalDateTime());
                vo.setConfirmedFieldCount(rs.getObject("confirmed_field_count", Integer.class));
                return vo;
            }
        };
    }

    /** 字段行映射器（listFields 用）。 */
    private static org.springframework.jdbc.core.RowMapper<DataAssetFieldVO> fieldRowMapper() {
        return (rs, i) -> {
            DataAssetFieldVO v = new DataAssetFieldVO();
            v.setFieldAssetId(rs.getString("field_asset_id"));
            v.setAssetId(rs.getString("asset_id"));
            v.setFieldId(rs.getString("field_id"));
            v.setFieldName(rs.getString("field_name"));
            v.setFieldType(rs.getString("field_type"));
            v.setDataType(rs.getString("data_type"));
            v.setFieldSensitivity(rs.getString("field_sensitivity"));
            v.setMaskStrategy(rs.getString("mask_strategy"));
            v.setRecommendLevel(rs.getString("recommend_level"));
            v.setRecommendSource(rs.getString("recommend_source"));
            v.setConfirmed(rs.getBoolean("confirmed"));
            return v;
        };
    }

    /** 物理资源行 → 资产 VO 基础映射（createAsset 用；不含 category/level join 字段）。 */
    private static DataAssetVO fromPhysicalResourceRow(Map<String, Object> row) {
        DataAssetVO vo = new DataAssetVO();
        vo.setResourceId((String) row.get("resource_id"));
        vo.setResourceName((String) row.get("resource_name"));
        vo.setResourceType((String) row.get("resource_type"));
        vo.setDatasourceId((String) row.get("datasource_id"));
        vo.setDescription((String) row.get("description"));
        Object fc = row.get("field_count");
        vo.setFieldCount(fc instanceof Number n ? n.intValue() : (fc == null ? null : Integer.parseInt(fc.toString())));
        Object rc = row.get("record_count");
        vo.setRecordCount(rc instanceof Number n ? n.longValue() : (rc == null ? null : Long.parseLong(rc.toString())));
        vo.setLayer((String) row.get("layer"));
        vo.setZone((String) row.get("zone"));
        if (row.get("last_sync_time") instanceof Timestamp ts) vo.setLastSyncTime(ts.toLocalDateTime());
        return vo;
    }

    /** 发事件 Kafka topic {DATA_SECURITY_TAGGED}（broker 不可用 fallback 内存路径）。 */
    private void publishSecurityTaggedEvent(DataAssetVO asset, String maxLevel,
                                            List<DataAssetSecurityTaggedEvent.TaggedField> fields,
                                            String operator) {
        try {
            EventBusService bus = eventBusProvider.getIfAvailable();
            if (bus == null) {
                log.warn("EventBusService 不可用，安全事件未发（内外部可重打）");
                return;
            }
            DataAssetSecurityTaggedEvent event = new DataAssetSecurityTaggedEvent(
                randomId(),
                asset.getAssetId(),
                asset.getResourceId(),
                asset.getResourceName(),
                asset.getResourceType(),
                asset.getDomain(),
                maxLevel,
                fields.size(),
                fields,
                operator,
                java.time.Instant.ofEpochMilli(System.currentTimeMillis()));
            bus.publish(KafkaTopics.DATA_SECURITY_TAGGED, event);
            log.info("已发数据资产安全事件: topic={} asset={} fields={}",
                KafkaTopics.DATA_SECURITY_TAGGED, asset.getAssetId(), fields.size());
        } catch (Exception e) {
            log.error("发数据资产安全事件失败 (asset={}): {}", asset.getAssetId(), e.getMessage(), e);
        }
    }
}
