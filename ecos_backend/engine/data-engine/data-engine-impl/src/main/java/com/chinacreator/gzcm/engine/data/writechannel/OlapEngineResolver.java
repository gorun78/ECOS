package com.chinacreator.gzcm.engine.data.writechannel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * F02-05 第 4 条 / MC04 · OLAP 双例外守卫（Doris ∨ ClickHouse）。
 *
 * <p>MC04：OLAP（Doris / ClickHouse）<b>只准承载业务域 DW（CURATED）/ 应用（APPLICATION）层</b>，
 * <b>控制域禁用</b>；同一部署 {@code dw.olap.engine=doris|clickhouse} 二选一，禁双 OLAP 并存双写；
 * 统一经 {@code runtime-access} 收口（IR06 / 工程调整清单 #12）。
 *
 * <p>本组件只做<b>裁决</b>（引擎编排层职责，铁律 §2.5「runtime 出 Driver、引擎做编排」），
 * 不持有 Driver：实际 OLAP 写仍走 runtime-access，本类负责"域 × 载体"合法性闸门。
 *
 * <p>守卫规则（违规即抛 {@link IllegalStateException}，由 Service 转 403/业务错误码）：
 * <ol>
 *   <li>storageKind ∈ {DORIS, CLICKHOUSE} 但目标 schema 属控制域 → 拒（控制域禁入 OLAP）。</li>
 *   <li>storageKind ∈ {DORIS, CLICKHOUSE} 时 {@code dw.olap.engine} 必须恰好选中该引擎
 *       （禁双 OLAP 并存）；未配置/不匹配 → 拒（ULTRM/ST02：OLAP 仅 ultimate 档启用）。</li>
 * </ol>
 */
@Component
public class OlapEngineResolver {

    /** OLAP 载体存储类型（湖规范 §七A）。 */
    public static final Set<String> OLAP_KINDS = Set.of("DORIS", "CLICKHOUSE");

    /** 允许落 OLAP 的业务层（MC04：DW/应用）。 */
    public static final Set<String> OLAP_ALLOWED_LAYERS = Set.of("CURATED", "APPLICATION");

    private final String configuredOlapEngine;

    public OlapEngineResolver(@Value("${dw.olap.engine:}") String configuredOlapEngine) {
        this.configuredOlapEngine = configuredOlapEngine == null ? "" : configuredOlapEngine.trim().toLowerCase();
    }

    /** 供测试/非容器装配（无 @Value）。 */
    OlapEngineResolver() {
        this.configuredOlapEngine = "";
    }

    /**
     * 当前部署选中的 OLAP 引擎（{@code doris} / {@code clickhouse} / 空串=未启用）。
     */
    public String resolveEngine() {
        return configuredOlapEngine;
    }

    /** OLAP 载体是否在当前部署启用（dw.olap.engine 已配置为其中之一）。 */
    public boolean isOlapCarrierEnabled(String storageKind) {
        String kind = storageKind == null ? "" : storageKind.trim().toUpperCase();
        if (!OLAP_KINDS.contains(kind)) return false;
        return !configuredOlapEngine.isEmpty();
    }

    /**
     * MC04 守卫：校验给定（层 / 目标 schema 域 / 存储载体）组合是否合法。
     *
     * @throws IllegalStateException 违规（控制域写 OLAP / 双 OLAP 并存 / OLAP 层不合法 / OLAP 未启用）
     */
    public void guard(String layer, DwSchemaQualifier.Domain domain, String storageKind) {
        String kind = storageKind == null ? "" : storageKind.trim().toUpperCase();
        boolean isOlap = OLAP_KINDS.contains(kind);

        if (!isOlap) {
            // 非 OLAP 载体：控制域可落 PG（主控制/引擎 schema），业务域可落 PG 业务 schema，均不在此拦截。
            return;
        }

        // ── 控制域禁入 OLAP（MC04 铁律）─────────────────────────────
        if (domain == DwSchemaQualifier.Domain.CONTROL) {
            throw new IllegalStateException(
                    "ECOS-DATA-054: MC04 违规 — 控制域写 OLAP 被拒（" + storageKind
                            + "）：OLAP 仅承载业务域 DW/APPLICATION，控制域数据禁入湖/OLAP 载体");
        }

        // ── 层必须是业务域 DW/APPLICATION ────────────────────────────
        String lyr = layer == null ? "" : layer.trim().toUpperCase();
        if (!OLAP_ALLOWED_LAYERS.contains(lyr)) {
            throw new IllegalStateException(
                    "ECOS-DATA-054: MC04 违规 — 层 " + layer + " 不可落 OLAP（仅 CURATED/APPLICATION）");
        }

        // ── 单 OLAP 引擎二选一（禁双写 / 未启用即拒，ST02）──────────
        String expected = kind.toLowerCase(); // DORIS→doris，CLICKHOUSE→clickhouse
        if (configuredOlapEngine.isEmpty()) {
            throw new IllegalStateException(
                    "ECOS-DATA-054: MC04 违规 — OLAP 引擎未启用（dw.olap.engine 未配置），"
                            + "拒绝 " + storageKind + " 写入");
        }
        if (!configuredOlapEngine.equals(expected)) {
            throw new IllegalStateException(
                    "ECOS-DATA-054: MC04 违规 — 部署选中 dw.olap.engine=" + configuredOlapEngine
                            + "，与请求载体 " + storageKind + " 不一致（禁双 OLAP 并存）");
        }
    }
}
