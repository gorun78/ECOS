/**
 * AIP Copilot — agent scenario catalog (data layer).
 * Pure i18n-driven data: all display text resolved via LanguageContext t().
 * Split from AIPCopilotDrawer (Wave-2A) — shared by AIPCopilotDrawer + AgentQuickActions.
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { useLanguage } from '../LanguageContext';

/** All supported agent scenario types (matches ICON_BY_SCENARIO + detectScenarioType routing). */
export type AgentScenarioType =
  | 'pipeline'
  | 'ontology'
  | 'health'
  | 'lineage'
  | 'sec_gdpr'
  | 'sec_row_col'
  | 'sec_finance'
  | 'sec_audit'
  | 'ws_generate_dashboard'
  | 'ws_auto_bind'
  | 'ws_inject_copilot'
  | 'ws_transform_theme';

/** One scenario entry rendered as a quick-action card. */
export interface AgentScenarioView {
  type: AgentScenarioType;
  title: string;
  desc: string;
}

/** Three scenario groups surfaced by drawer view mode. */
export interface AgentScenarioGroups {
  data: AgentScenarioView[];
  security: AgentScenarioView[];
  workshop: AgentScenarioView[];
}

const DATA_SCENARIOS: AgentScenarioType[] = ['pipeline', 'ontology', 'health', 'lineage'];
const SECURITY_SCENARIOS: AgentScenarioType[] = ['sec_gdpr', 'sec_row_col', 'sec_finance', 'sec_audit'];
const WORKSHOP_SCENARIOS: AgentScenarioType[] = [
  'ws_generate_dashboard',
  'ws_auto_bind',
  'ws_inject_copilot',
  'ws_transform_theme',
];

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
 * Hook returning the three scenario groups with i18n-resolved labels.
 * Re-resolves on locale switch (t identity changes with language).
 */
export function useAgentScenarios(): AgentScenarioGroups {
  const { t } = useLanguage();
  return {
    data: DATA_SCENARIOS.map((type) => toView(t, type)),
    security: SECURITY_SCENARIOS.map((type) => toView(t, type)),
    workshop: WORKSHOP_SCENARIOS.map((type) => toView(t, type)),
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
