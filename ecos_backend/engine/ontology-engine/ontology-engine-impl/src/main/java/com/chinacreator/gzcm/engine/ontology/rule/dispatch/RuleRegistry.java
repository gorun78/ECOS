package com.chinacreator.gzcm.engine.ontology.rule.dispatch;

import com.chinacreator.gzcm.engine.ontology.model.OntologyRule;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RuleRegistry 最小实现（§6.2.2「RuleRegistry 登记/路由收口点」；#147G）。
 *
 * <p>目标态 RuleRegistry 承担统一生命周期（建模→解析→编译→发布→执行→追踪→演进）。
 * 本批只落<b>可验证的登记面</b>：规则元数据（载体/层/作用域/版本/Git 引用）的
 * 一致性与「未接入即 not_available」语义，<b>不</b>伪造编译/追踪能力。</p>
 *
 * <p>判据：{@code version_no}/{@code git_ref} 缺一即 {@code not_available}
 * （与 §6.2.6⑧「缺 version_no/git_ref 拒发布」同形）；载体/层必须齐备且闭集内。</p>
 *
 * @author ECOS Ontology #147G
 */
@Service
public class RuleRegistry {

    /** 登记状态：元数据齐备可追踪。 */
    public static final String STATUS_REGISTERED = "registered";

    /** 登记状态：缺版本/引用/双维，版本与追踪不可用（禁假成功）。 */
    public static final String STATUS_NOT_AVAILABLE = "not_available";

    /**
     * 登记一条规则并返回可读档案。
     *
     * <p>返回键（additive）：{@code status} / {@code ruleId} / {@code ruleCarrier} /
     * {@code ruleLayer} / {@code scope} / {@code versionNo} / {@code gitRef} / {@code reason}。
     * 未注册成功时不臆造版本号与提交号。</p>
     */
    public Map<String, Object> register(OntologyRule rule) {
        Map<String, Object> doc = new LinkedHashMap<>();
        if (rule == null) {
            doc.put("status", STATUS_NOT_AVAILABLE);
            doc.put("reason", "规则为空");
            return doc;
        }
        doc.put("ruleId", rule.getId());
        doc.put("ruleCarrier", rule.getRuleCarrier());
        doc.put("ruleLayer", rule.getRuleLayer());
        doc.put("scope", rule.getScope());
        doc.put("versionNo", rule.getVersionNo());
        doc.put("gitRef", rule.getGitRef());

        String missing = missingOf(rule);
        if (missing != null) {
            doc.put("status", STATUS_NOT_AVAILABLE);
            doc.put("reason", missing);
            return doc;
        }
        if (RuleLayerTarget.fromLayer(rule.getRuleLayer()) == null) {
            doc.put("status", STATUS_NOT_AVAILABLE);
            doc.put("reason", "rule_layer 不在五值闭集内: " + rule.getRuleLayer());
            return doc;
        }
        doc.put("status", STATUS_REGISTERED);
        doc.put("reason", "元数据齐备（载体/层/作用域/版本/Git 引用）");
        return doc;
    }

    /** 版本与追踪是否可用（未接入资产返回 false，消费侧须渲染 not_available 空态）。 */
    public boolean isTraceable(OntologyRule rule) {
        return rule != null && missingOf(rule) == null;
    }

    private static String missingOf(OntologyRule rule) {
        if (rule.getRuleCarrier() == null || rule.getRuleCarrier().isBlank()) {
            return "缺 rule_carrier（三载体边界未绑定，禁发布）";
        }
        if (rule.getRuleLayer() == null || rule.getRuleLayer().isBlank()) {
            return "缺 rule_layer（五层归属未登记，禁发布）";
        }
        if (rule.getScope() == null || rule.getScope().isBlank()) {
            return "缺 scope（租户/组织/领域未登记，禁发布）";
        }
        if (rule.getVersionNo() == null || rule.getVersionNo().isBlank()) {
            return "缺 version_no（版本不可追踪，禁发布）";
        }
        if (rule.getGitRef() == null || rule.getGitRef().isBlank()) {
            return "缺 git_ref（历史不在 Git 归档单通道，禁发布）";
        }
        return null;
    }
}
