package com.chinacreator.gzcm.workspace.forecast;

import com.chinacreator.gzcm.workspace.scenario.ScenarioDomainException;

/**
 * 认知域对预测 run 请求的拒绝（HTTP 状态码保真载体，差距分析 P0-4 / X-44/DG-9）。
 *
 * <p><b>为什么不用 {@code BusinessException}</b>：{@code BusinessException(int errorCode, String message)}
 * 实为 {@code super(400, errorCode, message)} —— 第一个参数是<b>业务码</b>而非 HTTP 状态，
 * HTTP 状态恒为 400；且 {@code WorkspaceExceptionHandler} 无其专项映射，落
 * {@code handleUnexpected} 折叠成 500。引擎回 401/409/422 若用它承载，真实语义在编排面就丢了
 * （409 幂等冲突与 422 口径不匹配看起来一样），故本类走 {@link ScenarioDomainException} 族，
 * 由 {@code WorkspaceExceptionHandler#handleDomain} 按 {@code getHttpStatus()} 原样出参。</p>
 *
 * <p>403 不入本类：复用既有单源载体 {@code ForbiddenOperationException}（code=PERMISSION_DENIED），
 * 避免同一语义两个 code；404/501 亦复用 {@link ForecastRunNotFoundException} /
 * {@link ForecastRunNotLiveException}。</p>
 */
public class ForecastRunRejectedException extends ScenarioDomainException {

    /** 缺参 / 无事实 / 口径不匹配（引擎 400、422 原样保留）。 */
    public static final String CODE_REJECTED = "FORECAST_RUN_REJECTED";
    /** 出站凭证缺失或失效（引擎 401）。 */
    public static final String CODE_UNAUTHENTICATED = "FORECAST_RUN_UNAUTHENTICATED";
    /** 同幂等键内容漂移或状态冲突（引擎 409）。 */
    public static final String CODE_CONFLICT = "FORECAST_RUN_CONFLICT";

    public ForecastRunRejectedException(int httpStatus, String code, String message) {
        super(httpStatus, code, message);
    }

    /** 引擎侧定位信息（{@code ECOS-COG-*} 码 / missing / 路径）原样挂到 details，禁丢弃。 */
    public ForecastRunRejectedException withEngineDetail(String engineErrorCode, String missing, String path) {
        if (engineErrorCode != null) {
            putDetail("engineErrorCode", engineErrorCode);
        }
        if (missing != null) {
            putDetail("missing", missing);
        }
        if (path != null) {
            putDetail("path", path);
        }
        return this;
    }
}
