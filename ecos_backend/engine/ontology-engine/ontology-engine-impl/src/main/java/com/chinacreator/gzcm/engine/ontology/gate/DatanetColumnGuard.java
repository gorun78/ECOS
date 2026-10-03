package com.chinacreator.gzcm.engine.ontology.gate;

import com.chinacreator.gzcm.common.exception.DataAccessException;
import com.chinacreator.gzcm.engine.ontology.client.DataNetResourceClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * V3 血缘门禁（F03-03 / C.5 / PRD-03 §1.4-3）：指标/映射引用的事实表列**真实存在**。
 *
 * <p>经 data-engine 元数据 REST（{@link DataNetResourceClient}，fail-loud），<b>禁跨 schema 直查
 * ecos_dw / information_schema 拼 SQL</b>（铁律 C.3 读写边界 + E.5 接缝）。
 *
 * <p>三态判据：
 * <ul>
 *   <li>列存在 → 通过；</li>
 *   <li>列不存在 → {@code ECOS-ONTO-030}（violation，拒绝）；</li>
 *   <li>data-engine 不可用/响应非法（{@link DataAccessException}）→ <b>依赖不可用</b>，
 *       返回 {@link GateResult#dependencyDown}（整体 503 {@code ECOS-ONTO-050}，fail-closed）。</li>
 * </ul>
 */
public class DatanetColumnGuard {

    public static final String CODE = "ECOS-ONTO-030";
    public static final String CODE_UNAVAILABLE = "ECOS-ONTO-050";
    public static final String DEPENDENCY = "datanet";

    private static final Logger log = LoggerFactory.getLogger(DatanetColumnGuard.class);

    private final DataNetResourceClient datanetClient;

    public DatanetColumnGuard(DataNetResourceClient datanetClient) {
        this.datanetClient = datanetClient;
    }

    /**
     * @return null = 通过；GateResult（dependencyErrors 非空）= 依赖不可用需 503；
     *         否则调用方自行把返回的 violation 并入集会。此处用 {@link Outcome} 区分三态。
     */
    public Outcome evaluate(List<GateContext.LineageRef> refs) {
        if (refs == null || refs.isEmpty()) return Outcome.pass();
        List<GateViolation> violations = new ArrayList<>();
        for (GateContext.LineageRef ref : refs) {
            if (ref.resourceId == null || ref.resourceId.isBlank()) {
                GateViolation v = new GateViolation(GateViolation.Gate.V3_LINEAGE, CODE,
                        "血缘引用缺少 resourceId: " + ref.physicalColumn);
                v.addRef(ref.physicalColumn);
                violations.add(v);
                continue;
            }
            List<Map<String, Object>> fields;
            try {
                fields = datanetClient.listMetadataFields(ref.resourceId);
            } catch (DataAccessException e) {
                log.error("V3 依赖 data-engine 元数据不可用（fail-closed 503 ECOS-ONTO-050）: {}", ref.resourceId, e);
                return Outcome.dependencyDown(
                        GateResult.dependencyDown(DEPENDENCY, CODE_UNAVAILABLE,
                                "V3 血缘校验依赖 data-engine 元数据端点不可用，未执行（fail-closed）"));
            } catch (Exception e) {
                log.error("V3 data-engine 元数据调用异常（fail-closed 503）: {}", ref.resourceId, e);
                return Outcome.dependencyDown(
                        GateResult.dependencyDown(DEPENDENCY, CODE_UNAVAILABLE,
                                "V3 血缘校验依赖 data-engine 不可用，未执行（fail-closed）"));
            }
            boolean found = fields != null && fields.stream()
                    .anyMatch(f -> ref.physicalColumn != null
                            && (ref.physicalColumn.equalsIgnoreCase(str(f.get("fieldName")))
                                || ref.physicalColumn.equalsIgnoreCase(str(f.get("physicalColumn")))));
            if (!found) {
                GateViolation v = new GateViolation(GateViolation.Gate.V3_LINEAGE, CODE,
                        "血缘列 " + ref.physicalColumn + " 在资源 " + ref.resourceId + " 中不存在");
                v.addRef(ref.physicalColumn);
                violations.add(v);
            }
        }
        return Outcome.of(violations);
    }

    private static String str(Object o) { return o != null ? o.toString() : null; }

    /** 三态结果封装。 */
    public static final class Outcome {
        private final List<GateViolation> violations;
        private final GateResult dependencyDown;

        private Outcome(List<GateViolation> violations, GateResult dependencyDown) {
            this.violations = violations;
            this.dependencyDown = dependencyDown;
        }

        static Outcome pass() { return new Outcome(new ArrayList<>(), null); }
        static Outcome of(List<GateViolation> violations) { return new Outcome(violations, null); }
        static Outcome dependencyDown(GateResult r) { return new Outcome(new ArrayList<>(), r); }

        public boolean isDependencyDown() { return dependencyDown != null; }
        public GateResult getDependencyDown() { return dependencyDown; }
        public List<GateViolation> getViolations() { return violations; }
    }
}
