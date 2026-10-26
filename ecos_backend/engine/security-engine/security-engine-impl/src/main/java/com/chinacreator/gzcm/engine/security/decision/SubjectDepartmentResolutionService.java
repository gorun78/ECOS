package com.chinacreator.gzcm.engine.security.decision;

import com.chinacreator.gzcm.sysman.iam.entity.UserOrganization;
import com.chinacreator.gzcm.sysman.iam.service.IOrganizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * 主体域（{@code subject.department} / RLS {@code CONTEXT_ORG}）的<b>服务端真值解析单源</b>
 * （#142/B4-3e-80，差距 D-B4-23 主体侧腿）。
 *
 * <p><b>缺陷本质</b>：详设-01 W40 约定「机构只服务端解析」，登记原文把这条腿判为
 * 「现网无单源」（{@code td_user} 无 {@code org_id} 列）⇒ {@code /decision} 与
 * {@code /rls/apply} 长期把 {@code orgId} 写死 {@code null}。其后果是双向的：
 * ① 依赖 {@code CONTEXT_ORG} 的 RLS 谓词恒按「未解析」拒绝；
 * ② 护栏 {@code ecos.data_access} 的 AI 列判据是「声明域 vs 主体域」比对，主体一侧恒空串
 * ⇒ §4.7.2 p1「域内 ALLOW」<b>结构上不可达</b>——即使 #142/B4-3e-79 已把声明一侧换成真值。</p>
 *
 * <p><b>单源在哪</b>：机构隶属的真值属主是 sysman（表 {@code TD_USER_ORGANIZATION}，
 * {@code is_primary} 是该表的既有列，写入侧 {@code assignUserToOrg} 已按 '1'/'0' 归一）。
 * 本类经 sysman 的 api 门面 {@link IOrganizationService#getUserOrganizationAffiliations}
 * 只读取回关联行——沿用铁律 #5「跨域事实经属主 api 门面」，与本模块
 * {@code PIPImpl}／{@code PolicyInformationPointImpl} 注入 {@code IOrganizationService}
 * 的既有通道同源，<b>不</b>新建 REST 一跳，也不在 security 侧抄写对方表的 SQL。</p>
 *
 * <p><b>为什么不复用现成的 {@code getUserOrganizations}</b>：那条把 {@code is_primary}
 * 只用作 {@code ORDER BY}、投影里没有它 ⇒ 消费方分不出「0 枚主机构」与「2 枚主机构」，
 * 只能取第一条。仓内两处既有实现正是这个形态（{@code PIPImpl} 的 {@code orgs.get(0)}、
 * sysman {@code ClearanceLookupDao.findOrgById} 的 {@code LIMIT 1}）——那是把「事实不完备」
 * 谎报成「事实就是它」，与 D-B4-29/D-B4-36 在声明侧禁掉的「取其一」同罪。本类要求属主把
 * 多重性如实交付（属主侧只增读面见同批 {@code Organization-sql.xml}
 * {@code getUserOrganizationAffiliations}），判定留在这里。</p>
 *
 * <p><b>判定纪律（本类是唯一裁决点）</b>：主机构行<b>恰好</b>指向一个机构时才返回该机构号。
 * 其余形态（无主机构行、多枚互异主机构、主机构行机构号为空）一律 {@code null}＝如实未解析，
 * 由下游按既有语义处置（RLS 谓词拒绝；OPA 侧 {@code subject.department} 落空串 ⇒
 * 策略理由 {@code AI_SUBJECT_DEPARTMENT_UNRESOLVED}，禁伪装成
 * {@code AI_SCOPE_OUT_OF_SUBJECT_DEPARTMENT}——那是「越权」，而这里是「事实不完备」）。</p>
 *
 * <p><b>不可用纪律（§4.7.2「不可用 ≠ 无权限 ≠ 空结果」）</b>：属主门面未装配 / 取数抛错 ⇒
 * 抛 {@link SubjectSourceUnavailableException}，由调用方转不可用态（{@code /decision} 走
 * 既有 503 + {@code ECOS-SEC-501} 出口，{@code /rls/apply} 走 {@code RlsController}
 * 的 {@code DataBridgeException} 抛型承载）。<b>禁</b>折成「该用户没有主机构」——那会把
 * 部署/取数缺陷伪装成用户没权限，与 D-B4-83／D-B4-84 刚收口的折叠族同形。</p>
 */
@Service
public class SubjectDepartmentResolutionService {

    private static final Logger log = LoggerFactory.getLogger(SubjectDepartmentResolutionService.class);

    /** 属主不可达 / 应答不可解析的机读理由（进异常 message，供日志与门禁区分形态）。 */
    public static final String REASON_SOURCE_UNAVAILABLE = "SUBJECT_DEPARTMENT_SOURCE_UNAVAILABLE";

    private final IOrganizationService organizationService;

    /**
     * 生产装配：{@code Optional} 只用于<b>装配面</b>（boot 调试态可能不带 sysman bean），
     * 取数面仍 fail-loud——缺席时 {@link #resolvePrimaryOrgId(String)} 抛不可用态，
     * 禁把「没接上属主」折叠成「该用户没有机构」。
     */
    @Autowired
    public SubjectDepartmentResolutionService(Optional<IOrganizationService> organizationServiceProvider) {
        this.organizationService = organizationServiceProvider == null
                ? null : organizationServiceProvider.orElse(null);
    }

    /** 测试装配：直接给属主门面（可为 {@code null}＝未装配形态的取证）。 */
    SubjectDepartmentResolutionService(IOrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    /**
     * 解析主体的真值机构（＝策略侧的 {@code subject.department}）。
     *
     * @param userId 主体用户 id（只取信任链读数，禁取调用方自报）
     * @return 恰好一枚主机构时的机构号；事实不完备（0 枚／多枚互异／机构号为空）时 {@code null}
     * @throws SubjectSourceUnavailableException 属主门面未装配或取数失败（调用方转不可用态，禁折叠）
     */
    public String resolvePrimaryOrgId(String userId) {
        if (userId == null || userId.isBlank()) {
            // 没有主体就没有事实：不取数、不伪造（调用方本应在无 userId 时已拒绝裁决）
            return null;
        }
        if (organizationService == null) {
            throw unavailable("IOrganizationService 未装配，主体域无法服务端解析（禁回落到调用方自报机构）");
        }
        List<UserOrganization> affiliations;
        try {
            affiliations = organizationService.getUserOrganizationAffiliations(userId);
        } catch (Exception e) {
            // 取数失败＝事实不可用；OrganizationException／DAO 包装异常／运行时异常同一处置，禁吞
            throw unavailable("属主取数失败: userId=" + userId
                    + " (" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + ")");
        }
        if (affiliations == null) {
            // 门面返回 null 不是「没有关联行」而是契约破坏（属主侧 list 恒非 null）
            throw unavailable("属主返回 null 关联行集，形态不可解析: userId=" + userId);
        }
        String orgId = uniquePrimaryOrg(affiliations);
        if (orgId == null) {
            log.warn("主体域解析未果（无主机构或多枚主机构歧义），按未解析处置: userId={}, rows={}",
                    userId, affiliations.size());
        }
        return orgId;
    }

    /**
     * 真值判定单点：主机构行是否<b>恰好</b>指向一个机构。
     *
     * <p>包可见以便用例直读判定的四条腿（无主机构／多枚互异／机构号为空／同机构重复行），
     * 不靠属主夹具凑形态。同一机构被重复登记为主机构（{@code PK(user_id, org_id)} 下
     * 只可能来自属主数据修复）按「恰好一枚」成立——多重性比对的是机构集合，不是行数。</p>
     *
     * @return 唯一主机构号；无主机构行、机构号为空、或去重后集合 ≠ 1 ⇒ {@code null}
     */
    static String uniquePrimaryOrg(List<UserOrganization> affiliations) {
        if (affiliations == null || affiliations.isEmpty()) {
            return null;
        }
        TreeSet<String> distinct = new TreeSet<>();
        for (UserOrganization row : affiliations) {
            if (row == null || !isPrimaryRow(row.getIsPrimary())) {
                continue;
            }
            String orgId = row.getOrgId();
            if (orgId == null || orgId.isBlank()) {
                // 主机构行却没有机构号＝事实残缺，禁替主体编一个机构
                return null;
            }
            distinct.add(orgId.trim());
        }
        return distinct.size() == 1 ? distinct.first() : null;
    }

    /**
     * {@code is_primary} 读数归一。
     *
     * <p>存在理由：列形是 {@code VARCHAR(1) DEFAULT '1'}（属主写入侧 {@code assignUserToOrg}
     * 已按 '1'/'0' 归一），但多方言 DDL 与 {@code mapToObject} 的取值链可能交回
     * {@code "true"}／带空白的形态；不认全这些会把有主机构的主体误判成「无主机构」。
     * 除此之外不猜：任何其它值（含 {@code null}）都按非主机构行处理。</p>
     */
    private static boolean isPrimaryRow(String isPrimary) {
        if (isPrimary == null) {
            return false;
        }
        String v = isPrimary.trim();
        return "1".equals(v) || "true".equalsIgnoreCase(v);
    }

    private static SubjectSourceUnavailableException unavailable(String reason) {
        log.warn("主体域真值源不可用（fail-closed 转不可用态，禁折叠成未解析/越权）: {}", reason);
        return new SubjectSourceUnavailableException(REASON_SOURCE_UNAVAILABLE + ": " + reason);
    }

    /** 真值源不可用（属主门面未装配 / 取数失败 / 应答形态不可解析）——调用方必须转不可用态，禁当「没有机构」。 */
    public static class SubjectSourceUnavailableException extends RuntimeException {
        public SubjectSourceUnavailableException(String message) {
            super(message);
        }
    }
}
