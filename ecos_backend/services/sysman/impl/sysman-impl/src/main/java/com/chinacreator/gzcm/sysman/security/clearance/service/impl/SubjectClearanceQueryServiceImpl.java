package com.chinacreator.gzcm.sysman.security.clearance.service.impl;

import com.chinacreator.gzcm.sysman.dao.ClearanceLookupDao;
import com.chinacreator.gzcm.sysman.security.clearance.model.SubjectClearanceFact;
import com.chinacreator.gzcm.sysman.security.clearance.service.ISubjectClearanceQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 主体密级五档链的<b>属主单源实现</b>（#142/B4-3e-81）。
 *
 * <p>本类就是原先私有在 {@code ClearanceInterceptor.resolveUserClearanceLevel(...)} 里的那条链，
 * 逐档判据与原实现保持一致（命中条件、租户过滤形态、{@code COALESCE(-1)} 的「无配置」读数、
 * {@code EmptyResultDataAccessException} 按「无档案行」而非「断腿」处置），差别只有两处，
 * 且都是为了让交付形态可被复用：</p>
 *
 * <ol>
 *   <li><b>末端不再 {@code return 1}</b>：原实现在五档全空时硬编码回 L1，把「没档案」冒充成
 *       「L1 内部真值」。本类如实返未解析；拦截器仍在自己执行面上兜底（准入语义不变），
 *       security 引擎侧不兜底（未解析 ⇒ RLS 谓词拒绝 / {@code RLS_CONTEXT_UNRESOLVED}）。</li>
 *   <li><b>fail-soft 的 {@code catch} 不再静默</b>：原实现吞掉取数异常继续下探，调用方无从知道
 *       这个值是「链上真的没有更高档」还是「高档断了所以取了低档」。本类把断腿记进
 *       {@link SubjectClearanceFact#degradedTiers()}，由消费方判可信性。</li>
 * </ol>
 *
 * <p>数据访问仍走 {@link ClearanceLookupDao}（铁律 §2.5：本包不直持 {@code JdbcTemplate}），
 * SQL 未搬迁、未复制，只换调用方。</p>
 */
@Service
public class SubjectClearanceQueryServiceImpl implements ISubjectClearanceQueryService {

    private static final Logger log = LoggerFactory.getLogger(SubjectClearanceQueryServiceImpl.class);

    /** 用户级档案表（与 {@code ClearanceInterceptor} 原常量同源）。 */
    private static final String USER_PROFILE_TABLE = "td_user_security_profile";

    private final ClearanceLookupDao clearanceLookupDao;

    public SubjectClearanceQueryServiceImpl(ClearanceLookupDao clearanceLookupDao) {
        this.clearanceLookupDao = clearanceLookupDao;
    }

    @Override
    public SubjectClearanceFact resolveSubjectClearance(String userId, String tenantId, String orgId) {
        if (userId == null || userId.isBlank()) {
            // 没有主体就没有事实：不发起取数，也不给任何档位
            return SubjectClearanceFact.noSubject();
        }
        List<String> degraded = new ArrayList<>();

        // ── 1. 用户级 ────────────────────────────────
        Integer level = queryProfileRowLevel(USER_PROFILE_TABLE, "user_id", userId, tenantId,
                SubjectClearanceFact.TIER_USER, degraded);
        if (level != null) {
            return SubjectClearanceFact.resolved(SubjectClearanceFact.TIER_USER, level, degraded);
        }

        // ── 2. 角色级（取用户所有角色中最高的一档）──
        level = queryHighestRoleLevel(userId, tenantId, degraded);
        if (level != null) {
            return SubjectClearanceFact.resolved(SubjectClearanceFact.TIER_ROLE, level, degraded);
        }

        // ── 2.5 机构级 ───────────────────────────────
        if (orgId != null && !orgId.isBlank()) {
            level = queryScopedProfileLevel(clearanceLookupDao::orgClearanceLevel, orgId, tenantId,
                    SubjectClearanceFact.TIER_ORG, "orgId=" + orgId, degraded);
            if (level != null) {
                return SubjectClearanceFact.resolved(SubjectClearanceFact.TIER_ORG, level, degraded);
            }
        }

        // ── 2.8 租户级 ───────────────────────────────
        if (tenantId != null && !tenantId.isBlank()) {
            // 属主 DAO 的租户档只按 tenant_id 单键取数（id 值本身就是租户号），故不套双参方法引用
            level = queryScopedProfileLevel((idValue, ignored) ->
                    clearanceLookupDao.tenantClearanceLevel(idValue), tenantId, null,
                    SubjectClearanceFact.TIER_TENANT, "tenantId=" + tenantId, degraded);
            if (level != null) {
                return SubjectClearanceFact.resolved(SubjectClearanceFact.TIER_TENANT, level, degraded);
            }
        }

        // ── 3. 全局默认 ──────────────────────────────
        level = queryGlobalDefaultLevel(tenantId, degraded);
        if (level != null) {
            return SubjectClearanceFact.resolved(SubjectClearanceFact.TIER_GLOBAL, level, degraded);
        }

        // ── 4. 五档查尽：如实未解析（禁兜底成任何档位）──
        return SubjectClearanceFact.unresolved(degraded);
    }

    // ── 逐档查询（判据与原拦截器一致，异常面改为记录降级）──────

    /** 按行表 + id 列取一档（USER 档）。空行集＝该档无档案；取数异常＝该档断腿。 */
    private Integer queryProfileRowLevel(String tableName, String idColumn, String idValue,
                                         String tenantId, String tier, List<String> degraded) {
        try {
            List<Map<String, Object>> rows =
                    clearanceLookupDao.queryClearanceRows(tableName, idColumn, idValue, tenantId);
            if (rows == null || rows.isEmpty()) {
                return null;
            }
            return asLevel(rows.get(0).get("clearance_level"), tier, degraded);
        } catch (Exception e) {
            degraded.add(tier);
            log.warn("查询{}安全配置失败，记入降级档交回消费方（原实现为静默继续下探）", tableName, e);
            return null;
        }
    }

    /** 角色档：{@code COALESCE(MAX(...), -1)} 的 -1 是「无配置」读数，不是断腿。 */
    private Integer queryHighestRoleLevel(String userId, String tenantId, List<String> degraded) {
        try {
            Integer val = clearanceLookupDao.highestRoleClearanceLevel(userId, tenantId);
            return (val != null && val >= 0) ? val : null;
        } catch (Exception e) {
            degraded.add(SubjectClearanceFact.TIER_ROLE);
            log.warn("查询用户角色安全配置失败，记入降级档: userId={}", userId, e);
            return null;
        }
    }

    /** 机构/租户档的共用形态：无行（{@code EmptyResultDataAccessException}）＝该档无档案，其余异常＝断腿。 */
    private Integer queryScopedProfileLevel(ScopedLookup lookup, String idValue, String tenantId,
                                            String tier, String subjectHint, List<String> degraded) {
        try {
            return lookup.apply(idValue, tenantId);
        } catch (EmptyResultDataAccessException e) {
            log.debug("未查到{}级安全配置行: {}", tier, subjectHint);
            return null;
        } catch (Exception e) {
            degraded.add(tier);
            log.warn("查询{}级安全配置失败，记入降级档: {}", tier, subjectHint, e);
            return null;
        }
    }

    /** 全局默认档。 */
    private Integer queryGlobalDefaultLevel(String tenantId, List<String> degraded) {
        try {
            return clearanceLookupDao.globalDefaultClearanceLevel(tenantId);
        } catch (EmptyResultDataAccessException e) {
            log.debug("未查到全局默认安全配置行: tenantId={}", tenantId);
            return null;
        } catch (Exception e) {
            degraded.add(SubjectClearanceFact.TIER_GLOBAL);
            log.warn("查询全局默认安全配置失败，记入降级档", e);
            return null;
        }
    }

    /**
     * 列值归一：{@code Number} 才成立；{@code null}＝档案存在但未填密级（按该档无值继续下探，
     * 与列值本身缺失是同一形态，不是取数断了）；非数字非空＝应答形态不可解析＝断腿，
     * <b>禁</b>猜一个数字或当没这行。
     */
    private static Integer asLevel(Object val, String tier, List<String> degraded) {
        if (val == null) {
            return null;
        }
        if (val instanceof Number n) {
            return n.intValue();
        }
        degraded.add(tier);
        log.warn("{}级安全配置行的 clearance_level 形态不可解析: class={}, value={}", tier,
                val.getClass().getName(), val);
        return null;
    }

    /** 机构/租户档的取数形态（两处 DAO 方法签名一致，共用降级记账）。 */
    @FunctionalInterface
    private interface ScopedLookup {
        Integer apply(String idValue, String tenantId);
    }
}
