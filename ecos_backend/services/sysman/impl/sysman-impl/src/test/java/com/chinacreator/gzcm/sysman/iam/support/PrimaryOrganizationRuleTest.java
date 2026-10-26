package com.chinacreator.gzcm.sysman.iam.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chinacreator.gzcm.sysman.iam.entity.UserOrganization;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 「唯一主机构」属主单源判据的行为门（#142/B4-3e-82，收口台账 D-B4-99 b) 腿）。
 *
 * <p>本门盯的是判据本体（形态单源由 common-api 的
 * {@code PrimaryOrganizationRuleSingleSourceRatchetTest} 钉）。四件必须可分辨的事：</p>
 * <ol>
 *   <li><b>不唯一 ⇒ 未解析</b>：两枚互异主机构交付 {@code null}，而不是「数据库先给哪枚算哪枚」。
 *       这条是本批唯一的行为面改动，也是 {@link #twoDistinctPrimaryRowsYieldNoOrg} 存在的理由——
 *       改前的准入面在这里必然交回一枚机构号，机构级密级档因此可落在主体并不属于的机构上；</li>
 *   <li><b>顺序无关</b>：同一组行的两种排列必须给出同一答案（把裁决权从 JDBC 驱动手里拿走，
 *       这才是「确定性」的可执行定义）；</li>
 *   <li><b>多重性比的是机构集合，不是行数</b>：同一机构被重复登记为主机构仍算「恰好一枚」——
 *       否则一次脏写就能把主体从机构上下文里整体摘除，那是收紧过头；</li>
 *   <li><b>事实残缺不猜</b>：主机构行机构号为空白 ⇒ 未解析（禁替主体编一个机构），
 *       非主机构行里的合法机构号不参与裁决（禁把「隶属」当「主隶属」）。</li>
 * </ol>
 *
 * <p>验收：{@code mvn -o -f ecos_backend/pom.xml -pl services/sysman/impl/sysman-impl test
 * -Dtest='PrimaryOrganizationRuleTest'}</p>
 */
@Timeout(60)
class PrimaryOrganizationRuleTest {

    private static UserOrganization row(String orgId, String isPrimary) {
        UserOrganization r = new UserOrganization();
        r.setUserId("u-1");
        r.setOrgId(orgId);
        r.setIsPrimary(isPrimary);
        return r;
    }

    @Test
    @DisplayName("行集为空或为 null ⇒ 未解析（不发起裁决，也不给空串机构号）")
    void emptyOrNullYieldsNoOrg() {
        assertNull(PrimaryOrganizationRule.uniquePrimaryOrg(null));
        assertNull(PrimaryOrganizationRule.uniquePrimaryOrg(List.of()));
    }

    @Test
    @DisplayName("没有主机构行 ⇒ 未解析，即便隶属多枚机构")
    void affiliationWithoutPrimaryYieldsNoOrg() {
        assertNull(PrimaryOrganizationRule.uniquePrimaryOrg(
                List.of(row("ORG-A", "0"), row("ORG-B", "0"), row("ORG-C", null))));
    }

    @Test
    @DisplayName("恰好一枚主机构 ⇒ 交付该机构号（含读数前后空白归一）")
    void exactlyOnePrimaryRowIsDeliveredTrimmed() {
        assertEquals("ORG-A", PrimaryOrganizationRule.uniquePrimaryOrg(
                List.of(row("ORG-A", "1"), row("ORG-B", "0"))));
        assertEquals("ORG-A", PrimaryOrganizationRule.uniquePrimaryOrg(List.of(row("  ORG-A ", "1"))));
    }

    @Test
    @DisplayName("两枚互异主机构 ⇒ 未解析（禁取其一：本批唯一行为面改动）")
    void twoDistinctPrimaryRowsYieldNoOrg() {
        assertNull(PrimaryOrganizationRule.uniquePrimaryOrg(List.of(row("ORG-A", "1"), row("ORG-B", "1"))),
                "多枚主机构时交回任何一枚＝把「事实不完备」谎报成「事实就是它」，"
                        + "机构级密级档会落在主体并不属于的机构上");
    }

    @Test
    @DisplayName("同一组行的两种排列给出同一答案：裁决权不在 JDBC 顺序上")
    void resultIsOrderIndependent() {
        List<UserOrganization> first = List.of(row("ORG-A", "1"), row("ORG-B", "1"));
        List<UserOrganization> reversed = new ArrayList<>(first);
        java.util.Collections.reverse(reversed);
        assertEquals(PrimaryOrganizationRule.uniquePrimaryOrg(first),
                PrimaryOrganizationRule.uniquePrimaryOrg(reversed));

        List<UserOrganization> single = List.of(row("ORG-A", "1"), row("ORG-B", "0"));
        List<UserOrganization> singleReversed = List.of(row("ORG-B", "0"), row("ORG-A", "1"));
        assertEquals("ORG-A", PrimaryOrganizationRule.uniquePrimaryOrg(single));
        assertEquals("ORG-A", PrimaryOrganizationRule.uniquePrimaryOrg(singleReversed));
    }

    @Test
    @DisplayName("同一机构重复登记为主机构 ⇒ 仍算恰好一枚（比的是机构集合，不是行数）")
    void duplicatedSameOrgPrimaryRowsStillCountAsOne() {
        assertEquals("ORG-A", PrimaryOrganizationRule.uniquePrimaryOrg(
                List.of(row("ORG-A", "1"), row("ORG-A", "1"))));
        // 机构号带不同空白形态也属同一枚：trim 后才进集合，否则脏写能把主体摘空
        assertEquals("ORG-A", PrimaryOrganizationRule.uniquePrimaryOrg(
                List.of(row(" ORG-A", "1"), row("ORG-A ", "1"))));
    }

    @Test
    @DisplayName("主机构行机构号为空白 ⇒ 未解析（禁替主体编一个机构）")
    void primaryRowWithBlankOrgIdIsNotResolved() {
        assertNull(PrimaryOrganizationRule.uniquePrimaryOrg(List.of(row(null, "1"))));
        assertNull(PrimaryOrganizationRule.uniquePrimaryOrg(List.of(row("   ", "1"))));
        // 残缺行混在有效主机构行里同样整体未解析：不「跳过残缺行取那枚好的」
        assertNull(PrimaryOrganizationRule.uniquePrimaryOrg(List.of(row("ORG-A", "1"), row("", "1"))));
    }

    @Test
    @DisplayName("行集含 null 元素：忽略该行，不影响其余裁决")
    void nullElementIsIgnored() {
        List<UserOrganization> rows = new ArrayList<>();
        rows.add(null);
        rows.add(row("ORG-A", "1"));
        assertEquals("ORG-A", PrimaryOrganizationRule.uniquePrimaryOrg(rows));
    }

    @Test
    @DisplayName("is_primary 读数归一：'1'/'true'（含大小写与空白）为主，其余一律非主")
    void primaryReadingIsNormalizedNotGuessed() {
        assertTrue(PrimaryOrganizationRule.isPrimaryRow("1"));
        assertTrue(PrimaryOrganizationRule.isPrimaryRow(" 1 "));
        assertTrue(PrimaryOrganizationRule.isPrimaryRow("true"));
        assertTrue(PrimaryOrganizationRule.isPrimaryRow("TRUE"));
        assertFalse(PrimaryOrganizationRule.isPrimaryRow(null), "缺列值不猜主机构");
        assertFalse(PrimaryOrganizationRule.isPrimaryRow("0"));
        assertFalse(PrimaryOrganizationRule.isPrimaryRow("Y"), "白名单外的读数一律按非主机构行处置");
        assertFalse(PrimaryOrganizationRule.isPrimaryRow(""));
    }

    @Test
    @DisplayName("两枚互异主机构且其中一枚带空白：仍按不唯一处置（禁被 trim 掩盖成一枚）")
    void distinctOrgsAreDistinctEvenWithWhitespace() {
        assertNull(PrimaryOrganizationRule.uniquePrimaryOrg(
                List.of(row(" ORG-A ", "1"), row("ORG-B", "1"))));
    }
}
