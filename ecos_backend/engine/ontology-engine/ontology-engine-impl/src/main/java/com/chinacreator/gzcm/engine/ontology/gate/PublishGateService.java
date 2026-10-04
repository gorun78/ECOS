package com.chinacreator.gzcm.engine.ontology.gate;

import com.chinacreator.gzcm.engine.ontology.service.ICaliberService;
import com.chinacreator.gzcm.engine.ontology.service.MetricUnitAnalyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 发布门禁（F03-03 / W75/C59，P0）—— 提案执行前唯一语义关口。
 *
 * <p>编排 V1/C57 口径 + V2 单位/可加性 + V3 血缘列存在性（经 data-engine REST，fail-loud）。
 * 三态：通过 → 继续；违反 → 逐条返回；依赖不可用 → 503；口径服务不可用 → 500（fail-closed）。
 *
 * <p>{@code executeProposal} 与 {@code approveAndPublish} 复用本服务（同一套校验，
 * 避免分册 02 的"两条发布路径两套校验"孪生教训）。每个 guard 输出一条可观测 INFO
 * （C.7）：{@code proposalId gate=V1|V2|V3 result=PASS|FAIL code=ECOS-ONTO-0xx traceId={}}。
 */
@Service
public class PublishGateService {

    private static final Logger log = LoggerFactory.getLogger(PublishGateService.class);

    private final MetricCaliberGuard v1;
    private final MetricUnitDerivationGuard v2;
    private final DatanetColumnGuard v3;
    private final ICaliberService caliberService;

    public PublishGateService(MetricUnitAnalyzer unitAnalyzer,
                              @Lazy ICaliberService caliberService,
                              DatanetColumnGuard v3) {
        this.v1 = new MetricCaliberGuard(caliberService);
        this.v2 = new MetricUnitDerivationGuard(unitAnalyzer);
        this.v3 = v3;
        this.caliberService = caliberService;
    }

    /**
     * @param proposalId  提案 id（可观测日志用）
     * @param traceId     全链 traceId（可为 null，日志侧回退）
     * @param ctx         门禁上下文
     */
    public GateResult validate(String proposalId, String traceId, GateContext ctx) {
        List<GateViolation> violations = new ArrayList<>();

        // ── 可用性短路：口径服务同 JVM 宿主下不可用 → fail-closed 500（F03-01 异常与降级）──
        if (!caliberService.isAvailable()) {
            log.error("proposalId={} gate=ALL result=FAIL code=ECOS-ONTO-042 (caliber unavailable) traceId={}",
                    proposalId, traceId);
            return GateResult.serviceUnavailable("ECOS-ONTO-042", 500, "口径服务不可用");
        }

        // ── V1 口径 ──
        try {
            List<GateViolation> v1r = v1.evaluate(ctx);
            violations.addAll(v1r);
            logGate(proposalId, "V1", v1r, "ECOS-ONTO-040", traceId);
        } catch (RuntimeException e) {
            log.error("proposalId={} gate=V1 result=FAIL code=ECOS-ONTO-042 traceId={} cause={}",
                    proposalId, traceId, e.getMessage());
            return GateResult.serviceUnavailable("ECOS-ONTO-042", 500, "口径服务校验失败: " + e.getMessage());
        }

        // ── V2 单位/可加性 ──
        List<GateViolation> v2r = v2.evaluate(ctx);
        violations.addAll(v2r);
        logGate(proposalId, "V2", v2r, firstCode(v2r, "ECOS-ONTO-021"), traceId);

        // ── V3 血缘（经 data-engine，fail-loud 三态）──
        DatanetColumnGuard.Outcome v3r = v3.evaluate(ctx.getLineageRefs());
        if (v3r.isDependencyDown()) {
            log.error("proposalId={} gate=V3 result=DEP_DOWN code=ECOS-ONTO-050 traceId={}", proposalId, traceId);
            return v3r.getDependencyDown();
        }
        violations.addAll(v3r.getViolations());
        logGate(proposalId, "V3", v3r.getViolations(), "ECOS-ONTO-030", traceId);

        boolean passed = violations.isEmpty();
        if (passed) {
            log.info("proposalId={} gate=ALL result=PASS traceId={}", proposalId, traceId);
        }
        return GateResult.of(passed, violations, null);
    }

    private void logGate(String proposalId, String gate, List<GateViolation> vs, String code, String traceId) {
        String result = vs.isEmpty() ? "PASS" : "FAIL";
        log.info("proposalId={} gate={} result={} code={} traceId={}",
                proposalId, gate, result, code, traceId != null ? traceId : UUID.randomUUID());
    }

    private String firstCode(List<GateViolation> vs, String dflt) {
        for (GateViolation v : vs) {
            if (v.getCode() != null) return v.getCode();
        }
        return dflt;
    }
}
