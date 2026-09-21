package com.chinacreator.gzcm.engine.kb.repository;

import com.chinacreator.gzcm.engine.kb.ComplianceRuleProvider;
import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * {@link ComplianceRuleProvider} 的 kb-engine-impl 侧实现（委托 {@link ComplianceRuleMapper}）。
 *
 * <p>架构铁律 §2.1：cognitive-engine 等跨引擎消费者只依赖 kb-engine-api 的
 * {@code ComplianceRuleProvider} 接口，不直接 import 本类（kb-engine-impl）或
 * {@code ComplianceRuleMapper}（MyBatis @Mapper 接口）。
 */
@Service
public class ComplianceRuleProviderImpl implements ComplianceRuleProvider {

    /** MyBatis Mapper，负责 sys_compliance_rule 表的 SQL 读写（PG schema: sys_man） */
    private final ComplianceRuleMapper mapper;

    /**
     * 构造器注入。
     *
     * @param mapper 合规规则 Mapper（来自 kb-engine-impl 自身的 MyBatis 配置）
     */
    public ComplianceRuleProviderImpl(ComplianceRuleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ComplianceRule> findByDomain(String domain) {
        return mapper.findByDomain(domain);
    }

    @Override
    public List<ComplianceRule> findAll() {
        return mapper.findAll();
    }

    @Override
    public Optional<ComplianceRule> findById(String id) {
        ComplianceRule rule = mapper.findById(id);
        return Optional.ofNullable(rule);
    }
}
