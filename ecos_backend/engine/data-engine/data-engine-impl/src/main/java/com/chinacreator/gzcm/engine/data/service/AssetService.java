package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.event.DataAssetSecurityTaggedEvent;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.exception.ForbiddenException;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.engine.data.dao.AssetDao;
import com.chinacreator.gzcm.engine.data.dto.*;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.sysman.abac.model.AbacContext;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPermissionChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
 * <p>原始 SQL 已下沉至 {@link AssetDao}（架构铁律 §2.5 #1 / IR06）。</p>
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

    private final AssetDao assetDao;
    private final ObjectProvider<EventBusService> eventBusProvider;
    private final ObjectProvider<IAbacPermissionChecker> abacProvider;

    public AssetService(AssetDao assetDao, ObjectProvider<EventBusService> eventBusProvider,
                        ObjectProvider<IAbacPermissionChecker> abacProvider) {
        this.assetDao = assetDao;
        this.eventBusProvider = eventBusProvider;
        this.abacProvider = abacProvider;
    }

    /**
     * 打标前 ABAC 校验（security-engine 裁决）：
     * action=data.asset.security-tag，resource 带 assetId/resourceId/domain。
     * <p><b>2026-10-12 复盘订正（fail-open → fail-closed）</b>：ABAC 不可用（Bean 未装配）时
     * 原实现仅 log.warn 放行，与全平台 fail-closed（架构铁律 #3 默认 DENY）相悖；
     * 现对齐 OpaPolicyService 常量化 fail-close 先例 → 默认拒绝抛异常。
     * DENY 拒绝；NOT_APPLICABLE/PERMIT 放行。
     *
     * <p><b>#142/B4-3e-55（差距 D-B4-85）三态分离</b>：收口前「Bean 未装配」（原第 92 行）／
     * 「策略 DENY」（原第 108 行）／「策略评估抛异常」（原第 115 行）三条腿<b>都</b>抛
     * {@link SecurityException}，而全仓主源码面
     * {@code @ExceptionHandler(SecurityException.class)} 实扫 <b>0</b> 命中（登记为
     * {@code AssetServiceAbacTriStateCarrierTest} 的判据 A 前提）⇒ 三态在 HTTP 面一律落进
     * {@code GlobalExceptionHandler.handleAny}＝<b>500＋{@code ECOS-SYS-500}</b>：
     * 「裁决机构没答」（该触发运维告警、可重试）与「用户没权限」（该走权限申诉）被折成同一枚
     * 服务端内部错误，违详设 §4.7.2「不可用 ≠ 无权限 ≠ 空结果」。现按既有具名承载分态，
     * <b>不放行</b>语义一条不减：</p>
     * <ul>
     *   <li><b>不可用</b>（未装配／评估异常）→ 抛 {@link DataBridgeException}，携带
     *       {@link RlsExecutionContract#SEC_UNAVAILABLE_HTTP}＝503 与机读码
     *       {@link RlsExecutionContract#SEC_UNAVAILABLE_CODE}＝{@code ECOS-SEC-501}，
     *       与本模块 {@code PeriodCloseService.abacUnavailable}（#142/B4-3e-13 同族收口）同源，
     *       禁在本域另立「ABAC 不可用」码面；</li>
     *   <li><b>拒绝</b>（DENY）→ 抛 {@link ForbiddenException}（{@code extends DataBridgeException}
     *       且自带 403）→ {@code handleForbidden} 显式命中 ⇒ 状态行 403＋机读码
     *       {@code ECOS-SYS-403}，与本模块 {@code PeriodCloseService} 的 DENY 腿同形；</li>
     *   <li><b>放行</b>（PERMIT／NOT_APPLICABLE）→ 正常返回，不落库前置的拒绝码不出现。</li>
     * </ul>
     * <p>三态各自可寻址且互斥：503 不得写成 403，403 不得写成 500，两者都不得升成 200。
     * {@code ASSET-040／042／041} 三枚串保留为<b>消息内标签</b>（供日志检索），
     * 对外机读码一律由承载提供；原「{@code ASSET-041}＝操作被拒绝」的措辞按 §4.7.2 订正为
     * 「未获裁决」，不再把评估失败说成拒绝。</p>
     *
     * <p><b>#142/B4-3e-60（差距 D-B4-90）本批收口第 4 腿</b>：{@code checker.check(..)} 的接口契约
     * 只声明受检的 {@link IAbacPermissionChecker.PolicyEvaluationException}，实现体抛出的<b>任何
     * 未分类运行时异常</b>（如 HTTP 传输层故障、NPE）原会漏出本方法、落进
     * {@code GlobalExceptionHandler.handleAny}＝<b>500</b>——即「裁决机构没答」在状态行上被报成
     * 「本服务自身故障」。本批把它并入既有不可用承载（{@code catch (RuntimeException)} →
     * 503＋{@code ECOS-SEC-501}，消息内标签沿用 {@code ASSET-041} 并附原异常简单类名供运维检索、
     * {@code cause} 保留），理由＝从调用方视角「check() 抛任何异常」的语义都是<b>没拿到裁决</b>，
     * 与评估异常同归不可用态；该形态因此不再需要独立的第四枚机读码面（单源纪律同 D-B4-85）。
     * <b>与 DENY 的 403 严格互斥</b>：未分类异常一律不得写成「操作被拒绝」。
     * 互斥的工程前提＝DENY 判定腿<b>不得</b>写在该 {@code try} 内——{@link ForbiddenException}
     * 继承 {@code DataBridgeException} 而后者是 {@code RuntimeException}，写在 try 内会被
     * 自己的兜底腿捕获并折成 503（＝本批要禁的折叠形态），故结构上钉成
     * 「try 只取裁决 → catch 两枚 503 承载 → try 外判 DENY 抛 403」三段。</p>
     *
     * <p><b>同批删除的原 {@code catch (SecurityException)} 保护腿（D-B4-77 死面族）</b>：
     * 全仓主源码面 {@code IAbacPermissionChecker} 实现面对该型零抛出（{@code throw new
     * SecurityException} 实扫命中仅 ai {@code FileToolService} 与 security
     * {@code OpaPolicyService} 两文件，均与本接口无关），该腿恒不触发；保留它反而会让读代码的人
     * 以为「ABAC 实现会抛 SecurityException 且被原样上抛」。删除后该形态若真出现，
     * 由上面的 {@code RuntimeException} 承载腿接住并显式报 503，<b>覆盖面只增不减</b>。</p>
     */
    private void abacCheck(String assetId, String resourceId, String operator, String action) {
        IAbacPermissionChecker checker = abacProvider.getIfAvailable();
        if (checker == null) {
            // fail-closed：security 不可用不放行（对齐铁律 #3），承载＝「未获裁决」503 面（D-B4-85）
            log.error("ABAC 不可用，动作 {} 未获裁决，按 fail-closed 拒绝 (asset={} resource={})",
                action, assetId, resourceId);
            throw new DataBridgeException(RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                RlsExecutionContract.SEC_UNAVAILABLE_CODE + ": ASSET-042 未获安全裁决（ABAC Bean 未装配），"
                    + "动作 " + action + " 按 fail-closed 拒绝，非「用户无权限」"
                    + " (§4.7.2 / 铁律 §2.4) (asset=" + assetId + ")");
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
        // 裁决读取与拒绝判定分两段：ForbiddenException 系 unchecked 且继承 DataBridgeException，
        // 若在同一段 try 内抛出会被下面「未分类运行时异常」腿自我吞掉＝把 403 折成 503，
        // 正是 D-B4-90 立志要禁的反向折叠，故 DENY 腿必须落在 try 之外。
        IAbacPermissionChecker.Decision decision;
        try {
            decision = checker.check(ctx);
        } catch (IAbacPermissionChecker.PolicyEvaluationException e) {
            // 默认 Fail-Closed 安全红线（PMO-data10）：评估失败＝裁决不可得＝503，非「操作被拒绝」
            log.error("ABAC 策略评估异常 (asset={} action={}): {}", assetId, action, e.getMessage(), e);
            throw new DataBridgeException(RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                RlsExecutionContract.SEC_UNAVAILABLE_CODE + ": ASSET-041 未获安全裁决（ABAC 策略评估失败），"
                    + "动作 " + action + " 按 fail-closed 拒绝，非「用户无权限」"
                    + " (§4.7.2 / 铁律 §2.4) (asset=" + assetId + ")", e);
        } catch (RuntimeException unexpected) {
            // D-B4-90（#142/B4-3e-60）：check() 的契约只声明受检的 PolicyEvaluationException，
            // 实现体任何未分类运行时异常（如 HTTP 传输层故障）都会「裁决没答」——必须与
            // DENY 的 403／内部错误的 500 分态：落同一枚 503 不可用承载，保留原类型名供运维检索。
            log.error("ABAC 裁决腿未分类异常 (asset={} action={} type={}): {}",
                assetId, action, unexpected.getClass().getName(), unexpected.getMessage(), unexpected);
            throw new DataBridgeException(RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                RlsExecutionContract.SEC_UNAVAILABLE_CODE + ": ASSET-041 未获安全裁决（ABAC 裁决腿异常，"
                    + unexpected.getClass().getSimpleName() + "），动作 " + action
                    + " 按 fail-closed 拒绝，非「用户无权限」 (§4.7.2 / 铁律 §2.4) (asset=" + assetId + ")",
                unexpected);
        }
        // 拒绝态：策略答了「不行」＝403（D-B4-85），禁与「裁决没答」的 503 共用一枚载体
        if (decision == IAbacPermissionChecker.Decision.DENY) {
            log.warn("ABAC DENY asset={} action={} by={}", assetId, action, operator);
            throw new ForbiddenException(
                "ASSET-040: ABAC 策略拒绝，动作 " + action + " 无权执行 (asset=" + assetId + ")");
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

        // 1. 校验 resource_id 存在于 td_data_resource
        Integer resourceExists = assetDao.countResourceById(dto.getResourceId());
        if (resourceExists == null || resourceExists == 0) {
            throw new IllegalArgumentException("ASSET-001: resource_id 不存在于 td_data_resource");
        }

        // 2. 防重 (resource_id, domain)
        Integer dup = assetDao.countAssetByResourceAndDomain(dto.getResourceId(), domain);
        if (dup != null && dup > 0) {
            throw new IllegalArgumentException("ASSET-002: 同 domain 下该资源已存在业务资产");
        }

        // 3. 同步冗余 td_data_resource 的 resourceName/resourceType/layer/zone 等元数据
        List<Map<String, Object>> resourceRows = assetDao.physicalResourceRow(dto.getResourceId());
        if (resourceRows.isEmpty()) {
            throw new IllegalStateException("ASSET-003: td_data_resource 查询结果异常");
        }
        DataAssetVO vo = fromPhysicalResourceRow(resourceRows.get(0));

        // 4. 写入 asset
        assetDao.insertAsset(assetId, dto, level, domain, operator, now);

        // 5. 同步补齐 label/缓存
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

        assetDao.updateAsset(assetId, dto, level, operator);

        log.info("Asset updated: id={} level={}", assetId, level);
        return getAsset(assetId);
    }

    /** 逻辑删除资产（IR03 不真删；只 is_deleted=1 + version+1）。 */
    @Transactional
    public boolean deleteAsset(String assetId, String operator) {
        requireId("assetId", assetId);
        String op = (operator == null || operator.isBlank()) ? "system" : operator;
        int n = assetDao.softDeleteAsset(assetId, op);
        if (n > 0) {
            log.info("Asset soft-deleted: id={} by={}", assetId, op);
        }
        return n > 0;
    }

    /** 查资产详情（含 level dictionary join + category name join + 命中字段计数）。 */
    public DataAssetVO getAsset(String assetId) {
        requireId("assetId", assetId);
        try {
            List<DataAssetVO> rows = assetDao.findAssetRows(assetId);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            log.error("getAsset 失败 (assetId={}): {}", assetId, e.getMessage(), e);
            return null;
        }
    }

    // ── 分页列表 ─────────────────────────────────────────────

    /** 资产分页列表（按数据湖存储分层规范 §五 合法性矩阵过滤）。 */
    public List<DataAssetVO> listAssets(DataAssetQueryDTO q) {
        Integer registered;
        try {
            registered = assetDao.countRegisteredAssets();
        } catch (Exception e) {
            log.warn("ecos_data 表未建（V154 未 apply），fallback 走物理层占位视图: {}", e.getMessage());
            return assetDao.listPhysicalFallback(q);
        }
        if (registered != null && registered == 0
            && (q.getKeyword() == null || q.getKeyword().isBlank())
            && (q.getSensitivityLevel() == null || q.getSensitivityLevel().isBlank())
            && (q.getCategoryId() == null || q.getCategoryId().isBlank())
            && (q.getCategoryStatus() == null || q.getCategoryStatus().isBlank())
            && (q.getOwner() == null || q.getOwner().isBlank())) {
            return assetDao.listPhysicalFallback(q);
        }
        return assetDao.listAssets(q);
    }

    // ── 字段列表 / 打标 ─────────────────────────────────────

    /** 字段级敏感度分页列表（可选 confirmed=true 过滤）。 */
    public DataAssetFieldPageVO listFields(String assetId, Boolean confirmed, int page, int pageSize) {
        requireId("assetId", assetId);
        DataAssetFieldPageVO result = new DataAssetFieldPageVO();
        result.setPage(page);
        result.setPageSize(pageSize);

        Long total = assetDao.countFields(assetId);
        result.setTotal(total == null ? 0L : total);

        Long confirmedCount = assetDao.countConfirmedFields(assetId);
        result.setConfirmedCount(confirmedCount == null ? 0 : confirmedCount.intValue());

        List<DataAssetFieldVO> items = assetDao.listFieldRows(assetId, confirmed, page, pageSize);
        result.setItems(items);
        return result;
    }

    /** 字段级打标（安全红线：人工确认才触发 Kafka 事件 <code>ecos.data.security-tagged</code>）。 */
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
        abacCheck(assetId, asset.getResourceId(), dto.getOperator(), "data.asset.security-tag");
        boolean confirmed = Boolean.TRUE.equals(dto.getConfirmed());
        String operator = (dto.getOperator() == null || dto.getOperator().isBlank()) ? "system" : dto.getOperator();

        int updated = 0;
        List<DataAssetSecurityTaggedEvent.TaggedField> eventFields = new ArrayList<>();
        for (DataAssetFieldTagDTO.FieldTagItem it : dto.getFields()) {
            if (it == null || it.getFieldId() == null) continue;
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
            Integer dup = assetDao.countFieldDup(assetId, it.getFieldId());
            if (dup != null && dup > 0) {
                assetDao.updateField(assetId, it, dataType, level, mask, confirmed, operator);
            } else {
                assetDao.insertField(assetId, it, fieldAssetId, dataType, level, mask, confirmed, operator, now);
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
            String maxLevel = assetDao.maxConfirmedLevel(assetId);
            if (maxLevel == null) maxLevel = "L1";
            assetDao.updateAssetAfterConfirmedTag(assetId, maxLevel, operator);

            // 2. 发 Kafka 事件（data-engine → security-engine 消费生成 CLS/RLS）
            if (!eventFields.isEmpty()) {
                publishSecurityTaggedEvent(asset, maxLevel, eventFields, operator);
            } else {
                log.info("资产 {} 打标完成（无 L3/L4 命中字段，无需发安全事件），by={}", assetId, operator);
            }
        } else {
            assetDao.updateAssetAfterPendingTag(assetId, operator);
        }

        log.info("Asset {} 打标字段 {} 个，confirmed={}，by={}", assetId, updated, confirmed, operator);
        return getAsset(assetId);
    }

    // ── 公开：分级/分类字典 ─────────────────────────────────

    /** 4 级字典（data-engine 唯一权威；security 不应再自持）。 */
    public List<Map<String, Object>> listLevelDefs() {
        return assetDao.listLevelDefs();
    }

    /** 业务分类树（≤3 级，按 level 排序，便于前端 grid 渲染）。 */
    public List<Map<String, Object>> listCategoryTree() {
        return assetDao.listCategoryTree();
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

    /** 物理资源行 → 资产 VO 基础映射。 */
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
