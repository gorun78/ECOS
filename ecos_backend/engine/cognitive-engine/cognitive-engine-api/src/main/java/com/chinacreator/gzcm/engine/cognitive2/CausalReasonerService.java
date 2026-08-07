package com.chinacreator.gzcm.engine.cognitive2;

import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainResult;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalEdge;
import com.chinacreator.gzcm.engine.cognitive2.model.DiagnosisRequest;

import java.util.List;

/**
 * 因果推理服务接口 — 提供因果图推断、因果效应估计和深层因果诊断。
 */
public interface CausalReasonerService {

    /**
     * 推断指定业务域的因果图（边列表）
     *
     * @param domain 业务域
     * @return 因果关系边列表
     */
    List<CausalEdge> inferCausalGraph(String domain);

    /**
     * 估计两个变量之间的因果效应
     *
     * @param source 源变量
     * @param target 目标变量
     * @return 因果效应值（-1.0 ~ 1.0）
     */
    double estimateCausalEffect(String source, String target);

    /**
     * 深层因果诊断 — 接收偏差指标，输出≥3层的因果链 + 根因 + 建议。
     *
     * @param request 诊断请求（指标名、偏差值、业务域、最大深度）
     * @return 因果诊断结果（根因、完整因果链、建议、受影响指标）
     */
    CausalChainResult diagnose(DiagnosisRequest request);
}
