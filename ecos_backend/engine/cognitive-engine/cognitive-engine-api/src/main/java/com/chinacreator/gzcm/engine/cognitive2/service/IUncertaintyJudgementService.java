package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.common.cognitive.BeliefDistributionVO;
import com.chinacreator.gzcm.common.cognitive.EvidenceRecordVO;

import java.util.List;

/**
 * 不确定性判断服务接口 — 心智层 P 库契约（PMO-59 Phase 1 / ADR-9，只增不删）。
 *
 * <p>定位：认知引擎"心智层"核心——对不可观测经营变量（竞品降价概率、原材料涨跌概率等）
 * 维护有限离散概率分布：查询 / 注册 / 新证据加权更新 / 人工覆写。
 * 后端表 {@code ecos_cognitive_belief}（V129，ADR-9 心智状态落盘）。
 * 事件冲击走 Kafka topic {@code ecos.cognitive} + runtime-task 定时补算（不做流式）。</p>
 *
 * <p><b>本 Phase（P0）仅契约不实现</b>：impl 与 REST 端点留待 Phase 2 落地；
 * 消费方（workspace 场景层 / aiming）只能 import 本接口，禁止 import cognitive-engine-impl（架构铁律 2.1）。</p>
 *
 * <p>术语：业务方口径统一称"不确定性判断"（外部方案原"信念"已改称，避心理学歧义）；
 * LLM 零参与认知计算（ADR-5 口径）。</p>
 */
public interface IUncertaintyJudgementService {

    /**
     * 查询某变量当前生效的不确定性判断（最新版本）。
     *
     * @param variableName 不可观测经营变量名（必填）
     * @param domain       业务域（必填，如 pricing / supply-chain / demand）
     * @return 当前生效版本 VO；无记录返回 null（不抛异常，由调用方判空）
     */
    BeliefDistributionVO getLatest(String variableName, String domain);

    /**
     * 列出某业务域下全部生效的不确定性判断（认知状态盘点 / 盲区检测输入）。
     *
     * @param domain 业务域（必填）
     * @return 判断列表（可能为空列表）
     */
    List<BeliefDistributionVO> listActiveByDomain(String domain);

    /**
     * 注册/初始化一个不确定性判断（首版分布）。
     *
     * @param belief 待注册判断（variableName/domain/distribution 必填，prob 和=1；version 由服务端置 1）
     * @return 落库后的判断 VO（含服务端生成的 id）
     */
    BeliefDistributionVO register(BeliefDistributionVO belief);

    /**
     * 新证据加权更新：按证据可信度对既有分布做加权贝叶斯式更新，生成 version+1。
     *
     * @param variableName 变量名（必填）
     * @param domain       业务域（必填）
     * @param evidence     触发本次更新的证据（必填，最后一跳溯源）
     * @return 新版本判断 VO
     */
    BeliefDistributionVO updateByEvidence(String variableName, String domain, EvidenceRecordVO evidence);

    /**
     * 人工覆写（专家干预）：覆写分布并置 is_manual_override=true，覆写后模型更新让位于专家意见直至再次覆写。
     *
     * @param belief 覆写请求（variableName/domain/distribution/overrideReason 必填）
     * @return 落库后的判断 VO
     */
    BeliefDistributionVO override(BeliefDistributionVO belief);
}
