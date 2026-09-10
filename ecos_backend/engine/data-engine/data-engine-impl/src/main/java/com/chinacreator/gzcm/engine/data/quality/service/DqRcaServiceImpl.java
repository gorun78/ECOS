package com.chinacreator.gzcm.engine.data.quality.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.engine.data.quality.DqRcaService;
import com.chinacreator.gzcm.engine.data.quality.model.DqRcaRequest;
import com.chinacreator.gzcm.engine.data.quality.model.DqRcaResult;

/**
 * DQ 根因分析服务实现 — 本波 stub（PMO-48-C T13）。
 *
 * <p><b>本波不真调 cognitive-engine</b>（pipeline 不一定在线，且 diagnose 契约待
 * PMO-48-D Phase 4 对齐）。直接返回固定 stub 结果：
 * <pre>
 *   rootCause    = "STUB: 根因分析引擎待接入"
 *   confidence   = 0.0
 *   causalChain  = []
 *   candidates   = []
 *   timestamp    = System.currentTimeMillis()
 * </pre></p>
 *
 * <p>PMO-48-D Phase 4 接入真实 cognitive-engine 时改造：</p>
 * <pre>
 *   @Value("${cognitive-engine.base-url:http://localhost:18089}") String base;
 *   RestTemplate rt = /* 注入 gateway 已配 RestTemplate *&#47;;
 *   rt.postForObject(base + "/api/v1/cognitive/diagnose", request, DqRcaResult.class);
 * </pre>
 *
 * <p>失败/超时不阻塞工单主流程：业务侧（{@code DqWorkOrderServiceImpl}）已
 * try/catch summary，本类即使抛 RuntimeException 也只打 error 日志，不阻塞。</p>
 *
 * <p>Bean 名 {@code ecosDqRcaService}（铁律 1.3 防多 Bean 冲突）。</p>
 *
 * @author PMO-48-C T13
 */
@Service("ecosDqRcaService")
public class DqRcaServiceImpl implements DqRcaService {

    private static final Logger log = LoggerFactory.getLogger(DqRcaServiceImpl.class);

    /** stub 阶段固定根因描述（PMO-48-D 接 cognitive 后替换为真实诊断结果） */
    private static final String STUB_ROOT_CAUSE = "STUB: 根因分析引擎待接入";

    /** stub 阶段固定置信度（0.0 = 不可信，工单 rca_confidence 列写 0.0） */
    private static final double STUB_CONFIDENCE = 0.0D;

    @Override
    public DqRcaResult runDiagnose(DqRcaRequest request) {
        // 本波 stub：不真连 cognitive-engine，仅留痕日志 + 固定返回值
        String ruleId = request != null ? request.getFailedRuleId() : null;
        String assetId = request != null ? request.getAssetId() : null;
        String workOrderId = request != null ? request.getWorkOrderId() : null;
        log.info("DqRcaService.runDiagnose (PMO-48-C T13 stub): ruleId={}, assetId={}, workOrderId={}, timeWindow={}",
                ruleId, assetId, workOrderId,
                request != null ? request.getTimeWindow() : null);
        return DqRcaResult.builder()
                .rootCause(STUB_ROOT_CAUSE)
                .confidence(STUB_CONFIDENCE)
                .causalChain(List.of())
                .candidates(List.of())
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
