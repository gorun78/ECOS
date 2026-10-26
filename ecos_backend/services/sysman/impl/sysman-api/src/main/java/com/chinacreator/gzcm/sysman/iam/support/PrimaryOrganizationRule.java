package com.chinacreator.gzcm.sysman.iam.support;

import com.chinacreator.gzcm.sysman.iam.entity.UserOrganization;

import java.util.List;
import java.util.TreeSet;

/**
 * 「唯一主机构」判据的<b>属主单源</b>（#142/B4-3e-82，收口台账 D-B4-99 b) 腿）。
 *
 * <p><b>缺陷本质</b>：同一条「主体的机构上下文」在仓内有两套互不相容的判据。
 * ① 策略面（security 引擎 {@code SubjectDepartmentResolutionService}，#142/B4-3e-80 建）
 * 要求主机构行<b>恰好</b>指向一个机构才写值，0 枚或多枚互异一律按未解析处置；
 * ② 准入面（sysman {@code ClearanceInterceptor#getCurrentOrgId}）走
 * {@code ClearanceLookupDao.findOrgById} 的 {@code is_primary = '1' … LIMIT 1}
 * 再取 {@code rows.get(0)}——同一用户登记了 2 枚互异主机构时，它<b>必然</b>交付其中一枚，
 * 交回哪枚由数据库返回顺序决定。两者对同一事实给出不同答案，且②把「事实不完备」
 * 谎报成「事实就是它」，机构级密级档因此可能落在一个主体并不属于的机构上。</p>
 *
 * <p><b>为什么放在 sysman-api</b>：机构隶属的真值属主是 sysman（表 {@code TD_USER_ORGANIZATION}），
 * 判据的输入类型 {@link UserOrganization} 也是属主 api 面的实体。铁律 #5 要求跨域事实经属主
 * api 门面，判据放在属主侧才能让「策略面」与「准入面」读同一份规则，而不是各自抄写；
 * 放在 {@code support} 包（而非 {@code service}）是因为它是<b>纯函数规则</b>，
 * 不是需要装配的 bean——两枚消费方各自取数、共用判定。</p>
 *
 * <p><b>判定纪律</b>（本类是唯一裁决点）：只认「主机构行去重后恰好一枚」。
 * 无主机构行、去重后 ≥2 枚、或主机构行机构号为空白 ⇒ {@code null}＝如实未解析。
 * 同一机构被重复登记为主机构按「恰好一枚」成立：多重性比对的是机构集合，不是行数。
 * {@code null} 交回调用方按各自语义处置（准入面＝跳过机构级继续下探；策略面＝未解析 ⇒
 * RLS 谓词拒绝 / {@code subject.department} 落空串），<b>禁</b>在本类内兜底成任何机构或档位。</p>
 *
 * <p><b>不归本类管的在册余项</b>：{@code PIPImpl} 走 {@code getUserOrganizations} 之后
 * {@code orgs.get(0)} 的「取其一」形态——改它等于改策略属性契约（{@code organizationId/Name/Code/Type}
 * 四枚 attribute 的取值来源），属裁决事项，本批只由门禁钉名不扩面（台账 D-B4-99 b) 余项）。
 * 另一枚 {@code PolicyInformationPointImpl} 交付的是机构号<b>全集</b>，不是取其一，不属本族。</p>
 */
public final class PrimaryOrganizationRule {

    /** 纯函数规则：无状态、不可实例化（禁长成需要注入的第二形态）。 */
    private PrimaryOrganizationRule() {
    }

    /**
     * 从属主交付的关联行集中裁决出唯一主机构。
     *
     * <p>入参是<b>调用方取回的行集</b>而不是 {@code userId}——本类不取数，
     * 免得规则长成第二个取数面（取数属主 api 门面 {@code IOrganizationService#getUserOrganizationAffiliations}，
     * 不可用态由调用方按各自语义承载：策略面抛不可用异常，准入面 fail-soft 跳档）。</p>
     *
     * @param affiliations 属主交付的用户-机构关联行（可为 {@code null}／空集＝无关联行）
     * @return 恰好一枚主机构时的机构号；无主机构行、机构号为空白、或去重后集合 ≠ 1 ⇒ {@code null}（如实未解析）
     */
    public static String uniquePrimaryOrg(List<UserOrganization> affiliations) {
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
    public static boolean isPrimaryRow(String isPrimary) {
        if (isPrimary == null) {
            return false;
        }
        String v = isPrimary.trim();
        return "1".equals(v) || "true".equalsIgnoreCase(v);
    }
}
