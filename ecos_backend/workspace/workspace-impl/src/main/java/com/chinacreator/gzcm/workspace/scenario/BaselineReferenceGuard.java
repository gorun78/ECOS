package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.workspace.exception.ActionValidationException;
import com.chinacreator.gzcm.workspace.exception.SandboxReferenceForbiddenException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 基线引用守卫（详细设计-07 F07-09 / C-155）。
 *
 * <p><b>唯一</b>持有"FORMAL 运行禁止引用 SANDBOX 演练产物"的判定：目标 {@code baselineRunId}
 * 的 {@code run_mode = 'SANDBOX'} → 抛 {@link SandboxReferenceForbiddenException}（400 SANDBOX_REF_FORBIDDEN）。
 * 服务层与动作提案侧都只调用本类，禁两处各写一份（验收 {@code guardImplementedOnceNotTwice}）。</p>
 *
 * <p>数据源：{@code ecos_scenario_execution}（V209，R-25① 正交列 {@code run_mode}）。
 * 目标运行不存在 → 400（引用悬空，AMBIGUITY 传入方负责 WARN 日志）。</p>
 */
@Component
public class BaselineReferenceGuard {

    private final JdbcTemplate jdbc;

    public BaselineReferenceGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 校验 FORMAL 运行可引用该基线。
     *
     * @param baselineRunId 待引用的运行 id（非空即校验；空则直接放行——无引用无需拦截）
     * @throws SandboxReferenceForbiddenException 基线为 SANDBOX（400）
     * @throws BusinessException                  基线运行不存在（400，引用悬空）
     */
    public void assertFormalMayReference(String baselineRunId) {
        if (baselineRunId == null || baselineRunId.isBlank()) {
            return;
        }
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ecos_scenario_execution WHERE id = ? AND is_deleted = 0",
            Integer.class, baselineRunId);
        if (n == null || n < 1) {
            throw new BusinessException(400, "RUN-400: 基线运行不存在: " + baselineRunId);
        }
        String mode = jdbc.query(
            "SELECT run_mode FROM ecos_scenario_execution WHERE id = ? AND is_deleted = 0",
            rs -> rs.next() ? rs.getString("run_mode") : null,
            baselineRunId);
        if (mode == null) {
            throw new BusinessException(400, "RUN-400: 基线运行缺少 run_mode: " + baselineRunId);
        }
        if ("SANDBOX".equalsIgnoreCase(mode)) {
            throw new SandboxReferenceForbiddenException(
                "formal run cannot reference sandbox baseline: " + baselineRunId);
        }
    }

    /**
     * 校验动作提案锚定的预测运行存在且为 FORMAL（详细设计-07 F07-10-2）。
     *
     * <p>与 {@link #assertFormalMayReference} 同源同表（{@code ecos_scenario_execution.run_mode}），
     * 但语义相反侧：这里要求锚点<b>必须</b>是 FORMAL（动作是正式决策，不得挂在演练产物上），
     * 且 forecastRunId 为动作提案必填字段 —— 缺/空即 400，非静默放行。</p>
     *
     * @param forecastRunId 动作提案锚定的预测运行 id（必填）
     * @throws ActionValidationException 缺失/不存在/非 FORMAL（400 ACTION_FIELDS_MISSING）
     */
    public void assertForecastRunIsFormal(String forecastRunId) {
        if (forecastRunId == null || forecastRunId.isBlank()) {
            throw new ActionValidationException("forecastRunId 为动作提案必填字段");
        }
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ecos_scenario_execution WHERE id = ? AND is_deleted = 0",
            Integer.class, forecastRunId);
        if (n == null || n < 1) {
            throw new ActionValidationException("forecastRunId 指向的预测运行不存在: " + forecastRunId);
        }
        String mode = jdbc.query(
            "SELECT run_mode FROM ecos_scenario_execution WHERE id = ? AND is_deleted = 0",
            rs -> rs.next() ? rs.getString("run_mode") : null,
            forecastRunId);
        if (!"FORMAL".equalsIgnoreCase(mode)) {
            throw new ActionValidationException(
                "动作提案只能锚定 FORMAL 预测运行，当前 run_mode=" + mode + ": " + forecastRunId);
        }
    }
}
