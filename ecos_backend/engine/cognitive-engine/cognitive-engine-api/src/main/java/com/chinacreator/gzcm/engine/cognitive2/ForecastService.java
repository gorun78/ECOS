package com.chinacreator.gzcm.engine.cognitive2;

import com.chinacreator.gzcm.engine.cognitive2.model.ForecastRequest;
import com.chinacreator.gzcm.engine.cognitive2.model.ForecastResult;

/**
 * 时序预测服务接口 — 统计基线模型（PMO-51，只增不删）。
 *
 * <p>定位：认知引擎"预测"支柱（与因果/模拟/决策并列），回答"会怎样"。
 * 实现走统计基线（移动平均 + 线性外推 + KG 因子修正），不引入第三方 ML 框架，
 * 不新增 DB 表（推理结果实时计算）。</p>
 */
public interface ForecastService {

    /**
     * 执行时序预测。
     *
     * @param request 预测请求（指标名 / 业务域 / 历史序列 / 预测步数）
     * @return 预测结果（≥horizon 个预测点 + 置信区间 + 可解释条款）
     */
    ForecastResult forecast(ForecastRequest request);
}
