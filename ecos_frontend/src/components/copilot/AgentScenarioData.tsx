/**
 * AIP Copilot — agent scenario catalog (data layer).
 * Pure i18n-driven data: all display text resolved via LanguageContext t().
 *
 * 2026-11-14 验证批（V-COP-022 实测）：原 12 条场景里有 8 条（pipeline/ontology/health/lineage +
 * 4 条 ws_*）只有 `await sleep()` 与成功 toast，没有任何后端调用 —— 其中「本体建对象」
 * 连内存都没变（App.tsx 未向抽屉传 setObjectTypes，落到默认空实现）。经用户裁决
 * **删除这 8 条伪场景**，目录只保留 4 条有真实后端调用的安全场景：
 *   - sec_gdpr / sec_finance → POST /api/v1/security/policy/evaluate（真实 ABAC 裁决）
 *   - sec_audit → GET /api/v1/security/audit-logs（真实审计流）
 *   - sec_row_col → 同 ABAC 通道的行列策略评估
 *
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { useLanguage } from '../LanguageContext';

/** 支持的真实场景类型（每条都对应一次真实后端调用）。 */
export type AgentScenarioType =
  | 'sec_gdpr'
  | 'sec_row_col'
  | 'sec_finance'
  | 'sec_audit';

/** One scenario entry rendered as a quick-action card. */
export interface AgentScenarioView {
  type: AgentScenarioType;
  title: string;
  desc: string;
}

/** Scenario group surfaced by drawer view mode. */
export interface AgentScenarioGroups {
  security: AgentScenarioView[];
}

const SECURITY_SCENARIOS: AgentScenarioType[] = ['sec_gdpr', 'sec_row_col', 'sec_finance', 'sec_audit'];

/**
 * Resolve one scenario entry from i18n keys.
 * @param t translation function from useLanguage
 * @param type scenario discriminator
 */
function toView(t: (k: string) => string, type: AgentScenarioType): AgentScenarioView {
  return {
    type,
    title: t(`copilot.chat.scenario.${type}.title`),
    desc: t(`copilot.chat.scenario.${type}.desc`),
  };
}

/**
 * Hook returning the scenario group with i18n-resolved labels.
 * Re-resolves on locale switch (t identity changes with language).
 */
export function useAgentScenarios(): AgentScenarioGroups {
  const { t } = useLanguage();
  return {
    security: SECURITY_SCENARIOS.map((type) => toView(t, type)),
  };
}

/**
 * Resolve the trigger prompt for a scenario (used for query routing + history match).
 * @param t translation function from useLanguage
 * @param type scenario discriminator
 */
export function scenarioPrompt(t: (k: string) => string, type: AgentScenarioType): string {
  return t(`copilot.chat.scenario.${type}.prompt`);
}
