package com.chinacreator.gzcm.engine.ontology.gate;

import com.chinacreator.gzcm.engine.ontology.service.CaliberService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * V1 口径门禁（F03-03 / PRD-03 §1.4-1）：提案 payload 中每个指标必带 {@code caliber_id}，
 * 且对应 {@code caliber_version.status = APPROVED}。违反 → {@code ECOS-ONTO-040}。
 *
 * <p>口径服务不可用（同 JVM = bean 异常）→ fail-closed，由 {@link PublishGateService} 捕获后转
 * 500 {@code ECOS-ONTO-042}，禁"跳过口径校验先发布"。
 */
public class MetricCaliberGuard {

    public static final String CODE = "ECOS-ONTO-040";

    private final CaliberService caliberService;

    public MetricCaliberGuard(CaliberService caliberService) {
        this.caliberService = caliberService;
    }

    public List<GateViolation> evaluate(GateContext ctx) {
        List<GateViolation> out = new ArrayList<>();
        for (GateContext.MetricRef m : ctx.getMetrics()) {
            if (m.caliberId == null || m.caliberId.isBlank()) {
                GateViolation v = new GateViolation(GateViolation.Gate.V1_CALIBER, CODE,
                        "指标 " + m.code + " 发布态缺少 caliber_id（口径绑定必填）");
                v.addRef(m.code);
                out.add(v);
                continue;
            }
            if (m.formulaVersion == null || m.formulaVersion.isBlank()) {
                GateViolation v = new GateViolation(GateViolation.Gate.V1_CALIBER, CODE,
                        "指标 " + m.code + " 缺少 formula_version");
                v.addRef(m.code);
                out.add(v);
                continue;
            }
            if (!caliberService.isVersionApproved(m.caliberId, m.formulaVersion)) {
                GateViolation v = new GateViolation(GateViolation.Gate.V1_CALIBER, CODE,
                        "指标 " + m.code + " 绑定的口径版本 " + m.formulaVersion + " 非 APPROVED");
                v.addRef(Objects.toString(m.code));
                out.add(v);
            }
        }
        return out;
    }
}
