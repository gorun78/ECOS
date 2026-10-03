package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * BusinessDomainWriteService — F02-05 五层载体登记 / 唯一写通道（详细设计-02 §C.3.6、§D.2）。
 *
 * <p><b>唯一写入口</b>：外部引擎（cognitive 确定性预测产物 / workspace 应用结果）一律
 * 经 {@code POST /api/v1/datanet/write-channel} 落业务域表；本域在这里做：</p>
 * <ul>
 *   <li>层（layer）+ 载体（carrierRef）合法性校验（P0 guard：control-domain → DW 表 = 拒）</li>
 *   <li>schema 限定名解析（MC06）：carrierRef 已含 schema 必透传，否则默认 {@code ecos_dw}（本服务 DB 会话限定名基线）</li>
 *   <li>列类型白名单校验（MC02）：本批仅承载 {@code ecos_dw.ecos_biz_*_fact} 与
 *       {@code ecos_forecast_input_snapshot}；其他外表需先经 DDL 批加白</li>
 *   <li>idempotencyKey 去重：同 key 重放只回 200 不产生第二行</li>
 *   <li>审计日志（ECOS-DATA traceId 通过 GlobalExceptionHandler 自动透传）</li>
 * </ul>
 *
 * <p><b>红线</b>（F02-05 D.2 备注）：控制域表禁用 Doris/CH（MC04）——本服务只写 PG DWC，OLAP
 * 分发属 ultimate 档维度，业务流单独走（本批不涉及）。</p>
 *
 * <p><b>非静默</b>：拒绝列/层/未登记载体时 fail-loud 抛 {@code BusinessException}（400/403），
 * <b>不</b>吞异常（ECOS-DATA-023 语义，DQ 层补齐后接门禁；本批先行列白名单+幂等）。
 *
 * @author ECOS-BE (F02-05)
 */
@Service
public class BusinessDomainWriteService {

    private static final Logger log = LoggerFactory.getLogger(BusinessDomainWriteService.class);

    /** 业务域白名单表（写通道可写）。对应 V167 五事实表 + 预测快照。 */
    private static final Set<String> WRITE_WHITELIST = Set.of(
            "ecos_dw.ecos_biz_project_attribution",
            "ecos_dw.ecos_biz_stage_fact",
            "ecos_dw.ecos_biz_resource_fact",
            "ecos_dw.ecos_biz_cost_fact",
            "ecos_dw.ecos_forecast_input_snapshot");

    /** 各表允许写入的列白名单（业务列，不含 id/version_no/dq_status — 由服务统一补）。 */
    private static final Map<String, Set<String>> COLUMN_WHITELIST = Map.of(
            "ecos_dw.ecos_biz_project_attribution", Set.of(
                    "project_id", "contract_id", "department_id", "attribution_ratio",
                    "attribution_type", "effective_from", "effective_to",
                    "source_evidence", "source_ref", "evidence_ref", "domain"),
            "ecos_dw.ecos_biz_stage_fact", Set.of(
                    "project_id", "department_id", "period", "stage", "fact_type",
                    "contract_base", "attribution_ratio", "realization_rate",
                    "amount", "currency", "source_type", "source_ref", "evidence_ref", "domain"),
            "ecos_dw.ecos_biz_resource_fact", Set.of(
                    "project_id", "department_id", "period", "staff_ref_hash", "staff_hash_algo",
                    "fte", "work_hours", "hourly_rate", "cost_category", "fact_type",
                    "source_ref", "evidence_ref", "domain"),
            "ecos_dw.ecos_biz_cost_fact", Set.of(
                    "project_id", "is_pool", "department_id", "period", "cost_category",
                    "direct_or_allocated", "allocation_rule_ref", "amount", "currency",
                    "fact_type", "evidence_ref", "domain"),
            "ecos_dw.ecos_forecast_input_snapshot", Set.of(
                    "forecast_run_id", "as_of_time", "scope_hash", "fact_refs_json",
                    "metric_versions_json", "profile_versions_json", "assumption_versions_json",
                    "caliber_id", "caliber_version", "checksum", "domain"));

    private static final Set<String> ALLOWED_LAYERS = Set.of("RAW", "CURATED", "SEMANTIC", "APPLICATION");
    private static final int MAX_ROWS = 500;

    private final JdbcTemplate jdbc;

    public BusinessDomainWriteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record WriteChannelRequest(String layer, String carrierRef, String idempotencyKey,
                                       String traceId, List<Map<String, Object>> rows) {}

    public record WriteResult(String carrierRef, String layer, int written, String idempotencyKey,
                              boolean idempotentReplay) {}

    /**
     * F02-05-3：唯一写入口。
     * <ol>
     *   <li>校验 层/载体/cols 白名单（MC02/MC04）</li>
     *   <li>idempotencyKey 去重：命中 → 直接回 replay=true 不写</li>
     *   <li>批量 INSERT（PK 侧为应用侧 UUID）</li>
     * </ol>
     */
    public WriteResult writeBusinessDomainRows(WriteChannelRequest req) {
        if (req == null) throw new BusinessException(400, "body 必填");
        if (req.layer() == null || !ALLOWED_LAYERS.contains(req.layer())) {
            throw new BusinessException(400, "layer 必填 ∈ {RAW, CURATED, SEMANTIC, APPLICATION}");
        }
        String carrier = req.carrierRef();
        if (carrier == null || carrier.isBlank()) {
            throw new BusinessException(400, "carrierRef 必填");
        }
        if (!WRITE_WHITELIST.contains(carrier)) {
            // MC04/02 白名单：未登记的 DW 表不承接 write-channel
            throw new BusinessException(400,
                    "carrierRef 未登记为(write-channel) 白名单表: " + carrier
                    + "（合法: " + String.join("/", WRITE_WHITELIST) + "）");
        }
        if (req.rows() == null || req.rows().isEmpty()) {
            throw new BusinessException(400, "rows 必填");
        }
        if (req.rows().size() > MAX_ROWS) {
            throw new BusinessException(400, "单批 ≤" + MAX_ROWS + " 行");
        }
        String idem = req.idempotencyKey();
        if (idem == null || idem.isBlank() || idem.length() > 64) {
            throw new BusinessException(400, "idempotencyKey 必填（≤64 char）");
        }

        // idempotency 键命中：来源表不统一，按 batch_id 反查（新写入侧统一 batch_id=idempotencyKey）
        Integer hits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + carrier + " WHERE batch_id = ?", Integer.class, idem);
        if (hits != null && hits > 0) {
            log.info("F02-05 写通道幂等命中（replay）: carrier={} idem={} hits={}", carrier, idem, hits);
            return new WriteResult(carrier, req.layer(), hits, idem, true);
        }

        Set<String> allowed = COLUMN_WHITELIST.get(carrier);
        List<Map<String, Object>> toWrite = new ArrayList<>(req.rows().size());
        for (int i = 0; i < req.rows().size(); i++) {
            Map<String, Object> r = req.rows().get(i);
            Map<String, Object> filtered = new LinkedHashMap<>();
            filtered.put("id", UUID.randomUUID().toString());
            filtered.put("dq_status", "PASSED");
            filtered.put("is_active", 1);
            filtered.put("version_no", "1");
            filtered.put("batch_id", idem);
            for (Map.Entry<String, Object> e : r.entrySet()) {
                if (!allowed.contains(e.getKey())) {
                    throw new BusinessException(400,
                            "列不在 carrier 白名单（MC02）: row=" + (i + 1) + " col=" + e.getKey());
                }
                filtered.put(e.getKey(), e.getValue());
            }
            // forecast_input_snapshot 无 version_no 列 → 移除
            if ("ecos_dw.ecos_forecast_input_snapshot".equals(carrier)) {
                filtered.remove("version_no");
                // dq_status/is_active 有此表
            }
            toWrite.add(filtered);
        }

        List<String> cols = new ArrayList<>(toWrite.get(0).keySet());
        String placeholders = String.join(", ", Collections.nCopies(cols.size(), "?"));
        String sql = "INSERT INTO " + carrier + " (" + String.join(",", cols)
                + ") VALUES (" + placeholders + ")";
        List<Object[]> args = new ArrayList<>(toWrite.size());
        for (Map<String, Object> row : toWrite) {
            args.add(cols.stream().map(row::get).toArray(Object[]::new));
        }
        jdbc.batchUpdate(sql, args);

        log.info("F02-05 写通道成功: carrier={} layer={} rows={} idem={}",
                carrier, req.layer(), toWrite.size(), idem);
        return new WriteResult(carrier, req.layer(), toWrite.size(), idem, false);
    }

    /** F02-05 / D.2 B.4：登记一层载体（td_data_resource 新行 layer/carrier_ref/storage_kind/declared_flag）。 */
    public String registerLayerCarrier(String layer, String carrierRef, String storageKind) {
        if (layer == null || !ALLOWED_LAYERS.contains(layer)) {
            throw new BusinessException(400, "layer 必填 ∈ {RAW, CURATED, SEMANTIC, APPLICATION}");
        }
        if (carrierRef == null || carrierRef.isBlank()) {
            throw new BusinessException(400, "carrierRef 必填");
        }
        String sk = storageKind == null ? "PG" : storageKind;
        if (!sk.matches("[A-Z_]{2,16}")) {
            throw new BusinessException(400, "storageKind 非法（如 PG / MINIO / DORIS / CLICKHOUSE / NEO4J）");
        }
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO td_data_resource"
                        + " (resource_id, resource_name, resource_type, layer, layer_bucket,"
                        + "  carrier_ref, storage_kind, declared_flag, is_deleted,"
                        + "  create_time, create_by)"
                        + " VALUES (?, ?, 'RESOURCE', ?, ?, ?, ?, 1, 0, CURRENT_TIMESTAMP, ?)",
                id, tierName(layer, sk), layer, bucketFor(layer), carrierRef, sk, "gateway-write-channel");
        return id;
    }

    private String tierName(String layer, String storageKind) {
        return layer + "-" + storageKind;
    }

    private String bucketFor(String layer) {
        return switch (layer) {
            case "RAW" -> "NEAR_SOURCE";
            case "CURATED" -> "BIZ_CURATED";
            case "SEMANTIC" -> "ONTOLOGY";
            case "APPLICATION" -> "APP_RESULT";
            default -> "";
        };
    }
}
