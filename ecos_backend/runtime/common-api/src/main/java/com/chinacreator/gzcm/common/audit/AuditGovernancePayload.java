package com.chinacreator.gzcm.common.audit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计事件载荷 → 治理列的白名单提取（#147M）。
 *
 * <p>Kafka/内存总线上的审计事件是 {@code Map}，其中可能混有非列键（如 {@code eventId}）。
 * 本类按 {@link AuditEventContract#FIELDS} 白名单提取（{@code timestamp} 除外，落既有
 * {@code created_at} 由消费者决定），<b>不做动态列拼接</b>——DAO 收到的是固定列名集合。</p>
 *
 * <p>缺席或不认识的键一律丢弃（不臆造、不报错）；{@code approver_ids} 若以集合传入则逗号连接。</p>
 *
 * @author ECOS Control Domain #147M
 */
public final class AuditGovernancePayload {

    private AuditGovernancePayload() {
    }

    /** 提取治理列；无任何治理键时返回空 Map。 */
    public static Map<String, Object> extract(Map<String, Object> event) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (event == null) {
            return out;
        }
        for (String field : AuditEventContract.FIELDS) {
            if ("timestamp".equals(field)) {
                continue;
            }
            Object value = event.get(field);
            if (value == null) {
                // 兼容驼峰载荷（epoch/UI 侧）只认契约蛇形键，避免"猜键"造成错列
                continue;
            }
            if (value instanceof Iterable<?> it) {
                StringBuilder sb = new StringBuilder();
                for (Object o : it) {
                    if (o == null) continue;
                    if (sb.length() > 0) sb.append(',');
                    sb.append(o);
                }
                value = sb.length() == 0 ? null : sb.toString();
            }
            if (value != null) {
                out.put(field, value);
            }
        }
        return out;
    }

    /** 是否含有任一治理键（消费者据此决定是否补写，避免无谓 UPDATE）。 */
    public static boolean hasGovernanceKeys(Map<String, Object> event) {
        return !extract(event).isEmpty();
    }
}
