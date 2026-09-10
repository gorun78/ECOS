package com.chinacreator.gzcm.engine.data.quality;

import java.util.List;

import com.chinacreator.gzcm.engine.data.quality.model.DqRuleDTO;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVersionVO;
import com.chinacreator.gzcm.engine.data.quality.model.LogicDeleteResult;

/**
 * DQ 规则生命周期状态机服务接口（PMO-48-B T7c）。
 * <p>
 * 承载规则全生命周期 7 动作（createDraft / updateDraft / deleteRule /
 * submit / approve / reject / deprecate / supersede / disable）+ 版本快照读写。
 * 状态转换合法性由 {@code DqRuleLifecycleServiceImpl} 内 TRANSITIONS 转换表强制校验，
 * 违例抛 {@code BusinessException}。
 * </p>
 * <p>
 * 安全卡（铁律 2.4）：每次写操作后异步 {@code auditWrite}；读操作 {@code auditRead}。
 * approve 动作写 {@code dq_rule_version} 版本快照（version+1，幂等行锁防重复）。
 * </p>
 *
 * @author PMO-48-B T7c
 */
public interface DqRuleLifecycleService {

    /**
     * 创建草稿规则（status=DRAFT, version=1）。
     *
     * @param dto 规则入参（id 为空，系统生成 UUID）
     * @return 新规则 ID
     */
    String createDraft(DqRuleDTO dto);

    /**
     * 编辑草稿规则（仅 DRAFT 状态可编辑）。
     *
     * @param ruleId 规则 ID
     * @param dto    待更新字段（非 null 字段覆盖）
     */
    void updateDraft(String ruleId, DqRuleDTO dto);

    /**
     * 逻辑删除规则（is_deleted=TRUE）。
     *
     * @param ruleId 规则 ID
     * @return 删除前快照（id + beforeDeleteAt）
     */
    LogicDeleteResult deleteRule(String ruleId);

    /**
     * 提交审核：DRAFT/REJECTED → IN_REVIEW。
     *
     * @param ruleId    规则 ID
     * @param submitter 提交人
     * @return 新状态 IN_REVIEW
     */
    String submit(String ruleId, String submitter);

    /**
     * 审批通过：IN_REVIEW → ACTIVE，写版本快照 + approved_at。
     *
     * @param ruleId   规则 ID
     * @param approver 审批人
     * @return 新状态 ACTIVE
     */
    String approve(String ruleId, String approver);

    /**
     * 驳回：IN_REVIEW → REJECTED。
     *
     * @param ruleId   规则 ID
     * @param rejector 驳回人
     * @param reason   驳回理由
     * @return 新状态 REJECTED
     */
    String reject(String ruleId, String rejector, String reason);

    /**
     * 废止：DRAFT/ACTIVE/REJECTED → DEPRECATED。
     *
     * @param ruleId   规则 ID
     * @param operator 操作人
     * @return 新状态 DEPRECATED
     */
    String deprecate(String ruleId, String operator);

    /**
     * 被替代：DRAFT/ACTIVE/DEPRECATED → SUPERSEDED。
     *
     * @param ruleId   规则 ID
     * @param operator 操作人
     * @param reason   替代原因
     * @return 新状态 SUPERSEDED
     */
    String supersede(String ruleId, String operator, String reason);

    /**
     * 临时停用：ACTIVE → DISABLED（不写版本快照）。
     *
     * @param ruleId   规则 ID
     * @param operator 操作人
     * @return 新状态 DISABLED
     */
    String disable(String ruleId, String operator);

    /**
     * 查询指定版本快照。
     *
     * @param ruleId        规则 ID
     * @param versionNumber 版本号
     * @return 版本快照 VO（不存在返回 null）
     */
    DqRuleVersionVO getVersion(String ruleId, int versionNumber);

    /**
     * 查询规则全部版本历史（按 versionNumber 降序）。
     *
     * @param ruleId 规则 ID
     * @return 版本列表
     */
    List<DqRuleVersionVO> listVersions(String ruleId);
}
