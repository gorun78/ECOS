package com.chinacreator.gzcm.engine.data.writechannel;

import com.chinacreator.gzcm.common.data.DataDomainErrorCode;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
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
 * <p>守卫规则（违规即抛 {@link IllegalStateException}，由 Service 转 403/业务错误码；
 * 例外见下面第 3 条——「本部署档未启用 OLAP」不是违规而是<b>不可用</b>，抛携带 503 的
 * {@link DataBridgeException}）：
 * <ol>
 *   <li>storageKind ∈ {DORIS, CLICKHOUSE} 但目标 schema 属控制域 → 拒（控制域禁入 OLAP）。</li>
 *   <li>storageKind ∈ {DORIS, CLICKHOUSE} 时 {@code dw.olap.engine} 必须恰好选中该引擎
 *       （禁双 OLAP 并存）；不匹配 → 拒（ULTRM/ST02：OLAP 仅 ultimate 档启用）。</li>
 *   <li><b>#142/B4-3e-43</b>：{@code dw.olap.engine} <b>未配置</b>＝该部署档没开通这条写通道，
 *       语义是「制品在本部署不可用」（§3.2.7），不是「调用方没有权限」（§4.7.2「不可用 ≠ 无权限」）
 *       ⇒ 抛携带 503 的 {@link DataBridgeException}。收口前本腿与第 1/2 条同形抛 ISE，被属主端点
 *       {@code WriteChannelController.write} 的 {@code catch (IllegalStateException)} 一律折成
 *       {@code forbidden}＝403，客户端按「无权限」处置而不会重试；同时棘轮门
 *       {@code DependencyUnreadyIllegalStateRatchetTest} 的 CARRIER 面只认 {@code @ControllerAdvice}、
 *       读不到端点局部 catch，把本腿记成 {@code ENTRY_FALLBACK_500}——账面 500 与真实 403
 *       两个读数都对不上（新登记 D-B4-72）。机读码改取 D.1 单源表内语义本就对应的
 *       {@code ECOS-DATA-053}（503 — Doris/ClickHouse OLAP 降级），不再借用
 *       {@code ECOS-DATA-054}（表内＝Kafka 事件/审计降级；号义漂移另登 D-B4-73）。</li>
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
     * @throws IllegalStateException 违规（控制域写 OLAP / 双 OLAP 并存 / OLAP 层不合法）
     * @throws DataBridgeException 本部署档未启用 OLAP（携带 503 ＋ {@code ECOS-DATA-053}，
     *         #142/B4-3e-43 起脱离 ISE 形态，见类注释第 3 条）
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
                    DataDomainErrorCode.ECOS_DATA_062 + ": MC04 违规 — 控制域写 OLAP 被拒（" + storageKind
                            + "）：OLAP 仅承载业务域 DW/APPLICATION，控制域数据禁入湖/OLAP 载体");
        }

        // ── 层必须是业务域 DW/APPLICATION ────────────────────────────
        String lyr = layer == null ? "" : layer.trim().toUpperCase();
        if (!OLAP_ALLOWED_LAYERS.contains(lyr)) {
            throw new IllegalStateException(
                    DataDomainErrorCode.ECOS_DATA_062 + ": MC04 违规 — 层 " + layer + " 不可落 OLAP（仅 CURATED/APPLICATION）");
        }

        // ── 单 OLAP 引擎二选一（禁双写 / 未启用即拒，ST02）──────────
        String expected = kind.toLowerCase(); // DORIS→doris，CLICKHOUSE→clickhouse
        if (configuredOlapEngine.isEmpty()) {
            // #142/B4-3e-43（D-B4-71 属主侧第四批）：本腿＝「该部署档没开通这条写通道」，
            // 按 §3.2.7 属主端点显式 503，不再与 MC04 违规腿共用 ISE 形态被端点折成 403。
            throw new DataBridgeException(503, 503,
                    DataDomainErrorCode.ECOS_DATA_053 + " — OLAP 引擎未启用（dw.olap.engine 未配置），"
                            + "本部署档不可用，拒绝 " + storageKind + " 写入");
        }
        if (!configuredOlapEngine.equals(expected)) {
            throw new IllegalStateException(
                    DataDomainErrorCode.ECOS_DATA_062 + ": MC04 违规 — 部署选中 dw.olap.engine=" + configuredOlapEngine
                            + "，与请求载体 " + storageKind + " 不一致（禁双 OLAP 并存）");
        }
    }
}
