package com.chinacreator.gzcm.workspace.blueprint;

import java.util.Map;

/**
 * 能力契约读侧端口（补充设计 v1.0 §6.6 验收门槛「required capability 契约可用」）。
 *
 * <p>本接口是 ai-engine/Model &amp; Capability Center 对本域的<b>只读</b>抽象，
 * workspace 不感知能力中心的具体实现（跨 service 禁直连 impl，架构铁律 §2.5）。
 * 默认由 {@link LocalCapabilityReadFallback} 提供空实现桩（能力中心尚未接线时，
 * required 能力一律判为「不可用」，fail-closed，不伪造可用性）；
 * 能力中心落地后以 {@code @Primary} 或条件装配覆盖本桩即可，不改本接口签名。</p>
 */
public interface CapabilityReadService {

    /**
     * 查询指定能力某契约版本是否可用。
     *
     * @param capabilityId    能力 ID（如 DATA_PROFILE / ONTOLOGY_CANDIDATE_GENERATE）
     * @param contractVersion 契约版本（可选，null/空 = 任意已发布版本）
     * @return 可用性与说明；实现禁抛异常，禁返回 null（未知视为不可用）
     */
    CapabilityAvailability availability(String capabilityId, String contractVersion);

    /** 能力可用性；{@code available=false} 时 {@code reason} 必须给出可读原因（禁静默）。 */
    final class CapabilityAvailability {
        private final boolean available;
        private final String reason;
        private final Map<String, Object> detail;

        public CapabilityAvailability(boolean available, String reason, Map<String, Object> detail) {
            this.available = available;
            this.reason = reason;
            this.detail = detail == null ? Map.of() : detail;
        }

        public static CapabilityAvailability available() {
            return new CapabilityAvailability(true, "OK", Map.of());
        }

        public static CapabilityAvailability unavailable(String reason) {
            return new CapabilityAvailability(false, reason, Map.of());
        }

        public boolean isAvailable() {
            return available;
        }

        public String getReason() {
            return reason;
        }

        public Map<String, Object> getDetail() {
            return detail;
        }
    }
}
