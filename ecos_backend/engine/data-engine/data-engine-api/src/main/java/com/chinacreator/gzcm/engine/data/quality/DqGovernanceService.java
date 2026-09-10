package com.chinacreator.gzcm.engine.data.quality;

import java.util.List;

import com.chinacreator.gzcm.engine.data.quality.model.DqDimensionRegistryVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleDetailVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleQuery;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVO;
import com.chinacreator.gzcm.engine.data.quality.model.PageResult;

/**
 * DQ 治理服务接口 — 数据质量规则只读治理（PMO-48-A T3）。
 * <p>
 * 读 {@code ecos_dq.dq_rule / dq_rule_version}；响应前强制安全卡：
 * 敏感字段 mask 脱敏（铁律 2.4 #3）+ 读操作异步审计（铁律 2.4 #5）。
 * </p>
 *
 * @author PMO-48-A T3
 */
public interface DqGovernanceService {

    /**
     * 分页查询规则列表（过滤 + 分页，响应前脱敏 + 审计）。
     *
     * @param q 查询条件（全部字段可选）
     * @return 分页结果
     */
    PageResult<DqRuleVO> listRules(DqRuleQuery q);

    /**
     * 查询规则详情（全量字段 + 版本历史，响应前脱敏 + 审计）。
     *
     * @param id 规则 ID
     * @return 详情（不存在返回 null，由 Controller 判定 404）
     */
    DqRuleDetailVO getRuleDetail(String id);

    /**
     * 6 维 rule_type 映射注册表（维度编码 / 中文名 / rule_types / 默认权重 / 描述）。
     *
     * @return 维度注册表列表
     */
    List<DqDimensionRegistryVO> getDimensionRegistry();
}
