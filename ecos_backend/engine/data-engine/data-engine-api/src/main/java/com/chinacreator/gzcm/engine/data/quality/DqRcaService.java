package com.chinacreator.gzcm.engine.data.quality;

import com.chinacreator.gzcm.engine.data.quality.model.DqRcaRequest;
import com.chinacreator.gzcm.engine.data.quality.model.DqRcaResult;

/**
 * DQ 根因分析服务契约（PMO-48-C T13）。
 *
 * <p>调 cognitive-engine（木·C）的 {@code POST /api/v1/cognitive/diagnose}
 * 获取根因分析结果。</p>
 *
 * <p><b>本波 stub 说明（PMO-48-C T13）</b>：本波不真调 cognitive-engine
 * （pipeline 可能未启动，且 cognitive-engine 的 diagnose 契约待 Phase 4 对齐），
 * 直接返回固定 stub 结果（{@code rootCause="STUB: 根因分析引擎待接入", confidence=0.0,
 * causalChain=[]}）。PMO-48-D Phase 4 接入真实 cognitive-engine，届时实现
 * HTTP 调用：构造器注入 {@code RestTemplate}（gateway 已配），POST 到
 * {@code http://localhost:18089/api/v1/cognitive/diagnose}，body 为
 * {@link DqRcaRequest}，响应为 {@link DqRcaResult}。</p>
 *
 * <p>失败/超时时不阻塞工单主流程，写 {@code rca_confidence=0.0} + warning 日志。</p>
 *
 * <p>Bean 名 {@code ecosDqRcaService}（铁律 1.3 防多 Bean 冲突）。</p>
 *
 * @author PMO-48-C T13
 */
public interface DqRcaService {

    /**
     * 执行一次根因分析。
     *
     * @param request RCA 请求（failedRuleId / assetId / timeWindow / relatedPipelineIds）
     * @return RCA 结果（本波 stub 返回 confidence=0.0）
     */
    DqRcaResult runDiagnose(DqRcaRequest request);
}
