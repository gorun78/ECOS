package com.chinacreator.gzcm.engine.kb;

import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;

import java.util.List;
import java.util.Optional;

/**
 * 合规规则数据访问契约 —— 引擎间只调 API 不调 Impl（架构铁律 §2.1）。
 * 实现在 kb-engine-impl，cognitive-engine-impl 等其余引擎仅依赖此接口。
 *
 * <p>方法签名对齐 {@code ComplianceRuleMapper}（MyBatis @Mapper 接口，位于 kb-engine-impl），
 * 注意 {@link #findById} 参数为 String（与 sys_compliance_rule.id 列类型一致），不是 Long。
 */
public interface ComplianceRuleProvider {

    /** 按 domain 查询合规规则列表（domain 为空时建议调用 {@link #findAll()}） */
    List<ComplianceRule> findByDomain(String domain);

    /** 返回全部合规规则 */
    List<ComplianceRule> findAll();

    /** 按主键 id 查询单条合规规则，不存在返回 {@link Optional#empty()} */
    Optional<ComplianceRule> findById(String id);
}
