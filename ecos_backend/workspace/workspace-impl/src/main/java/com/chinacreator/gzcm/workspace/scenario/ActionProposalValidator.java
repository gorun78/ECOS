package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.workspace.exception.ActionValidationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 动作提案五必填服务端校验（详细设计-07 F07-10-2 / ActionProposalValidator）。
 *
 * <p>Req-FC-04 五必填：负责人({@code assignee}) / 截止日({@code deadline}) /
 * 审批人({@code approver}) / 预期影响({@code expectedImpact}) / 控制指标({@code controlMetric})。
 * 缺失<b>累积成一条清单</b>再一次性抛 400（{@link ActionValidationException}），
 * 让前端能逐字段提示，而非"缺一个报一个"要发五次（前端 UX 约定，服务端仍是真值源）。
 * {@code forecastRunId} 的存在性与 FORMAL 判定不在本类，由
 * {@link BaselineReferenceGuard} 单源持有（F07-09 共用，禁重写）。</p>
 */
@Component
public class ActionProposalValidator {

    /** 五必填字段名（稳定顺序，供前端逐字段定位）。 */
    public static final List<String> MANDATORY_FIELDS = List.of(
            "assignee", "deadline", "approver", "expectedImpact", "controlMetric");

    /**
     * 校验五必填；缺失则抛 400（携带全部缺失字段名）。{@code null} DTO 视作全缺。
     */
    public void validate(ActionProposalDTO dto) {
        List<String> missing = new ArrayList<>();
        if (dto == null) {
            missing.addAll(MANDATORY_FIELDS);
        } else {
            if (blank(dto.getAssignee())) missing.add("assignee");
            if (blank(dto.getDeadline())) missing.add("deadline");
            if (blank(dto.getApprover())) missing.add("approver");
            if (blank(dto.getExpectedImpact())) missing.add("expectedImpact");
            if (blank(dto.getControlMetric())) missing.add("controlMetric");
        }
        if (!missing.isEmpty()) {
            throw new ActionValidationException("动作提案缺少必填字段: " + String.join(", ", missing));
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
