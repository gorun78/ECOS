package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.common.cognitive.EvidenceRecordVO;
import com.chinacreator.gzcm.common.cognitive.HypothesisVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 假设生命周期服务接口 — 心智层 H 库契约（PMO-59 Phase 1 / ADR-9，只增不删）。
 *
 * <p>定位：认知引擎"心智层"假设管理（外部方案层3.1）——每条假设结构化存储
 * （证据引用/valid/失效时间），核心能力为<b>失效检测</b>：监测新证据是否触发假设失效，
 * 失效后自动下游推演作废 + 告警（失效后的推演作废链路 Phase 2 落地，本 Phase 仅契约）。</p>
 *
 * <p>后端表 {@code ecos_cognitive_hypothesis}（V128，ADR-9 心智状态落盘）。
 * <b>本 Phase（P0）仅契约不实现</b>：impl 与 REST 端点留待 Phase 2 落地；
 * 消费方只能 import 本接口，禁止 import cognitive-engine-impl（架构铁律 2.1）。</p>
 */
public interface IHypothesisLifecycleService {

    /**
     * 查询单条假设（含证据引用与失效状态）。
     *
     * @param hypothesisId 假设主键（必填）
     * @return 假设 VO；无记录返回 null（不抛异常，由调用方判空）
     */
    HypothesisVO getDetail(String hypothesisId);

    /**
     * 列出指定业务域下全部有效假设（推演前"事实 + 有效假设"装配输入）。
     *
     * @param domain 业务域（必填）
     * @return 有效假设列表（status=VALID 且 is_valid=true，可能为空列表）
     */
    List<HypothesisVO> listActiveByDomain(String domain);

    /**
     * 注册一条假设（绑定支撑证据）。
     *
     * @param hypothesis 待注册假设（statement 必填，evidenceIds 建议非空）
     * @return 落库后的假设 VO（含服务端生成的 id，初始 status=VALID）
     */
    HypothesisVO register(HypothesisVO hypothesis);

    /**
     * 失效检测：用给定证据检验假设是否被推翻（抵消/冲突证据超过阈值 → 失效）。
     *
     * @param hypothesisId 假设主键（必填）
     * @param evidence     触发检验的证据（必填，留痕失效原因）
     * @param detectedAt   检测时刻（必填，写入 invalid_at）
     * @return 检测后的假设 VO（若命中失效，status=INVALIDATED 且 is_valid=false）
     */
    HypothesisVO detectInvalidation(String hypothesisId, EvidenceRecordVO evidence, LocalDateTime detectedAt);

    /**
     * 人工标记假设失效（专家干预兜底，监测漏报时手动作废）。
     *
     * @param hypothesisId 假设主键（必填）
     * @param reason       失效原因（必填，留痕审计）
     * @return 更新后的假设 VO（status=INVALIDATED）
     */
    HypothesisVO invalidate(String hypothesisId, String reason);

    /**
     * 归档假设（退出有效集合，保留历史可查）。
     *
     * @param hypothesisId 假设主键（必填）
     * @return 更新后的假设 VO（status=ARCHIVED）
     */
    HypothesisVO archive(String hypothesisId);
}
