/**
 * Copilot 智能体切换条（6 个内置智能体）。
 *
 * 默认智能体由当前页面路由决定（见 AIPCopilotDrawer 的 resolveAgentForRoute），
 * 用户点击可临时覆盖；路由变化后回到该页面的默认智能体。
 * 纯展示组件，文案经 `copilot.*` i18n。
 *
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { Icon } from '../LucideIcon';
import { useTheme } from '../ThemeContext';
import type { CopilotAgentInfo } from '../../services/copilotApi';

/** 引擎 → 图标（静态表，避免 switch 分支）。 */
const ICON_BY_ENGINE: Record<string, string> = {
  'data-engine': 'Database',
  'ontology-engine': 'Boxes',
  'kb-engine': 'BookOpen',
  'ai-engine': 'Cpu',
  workspace: 'Briefcase',
  'security-engine': 'ShieldCheck',
};

interface CopilotAgentSwitcherProps {
  agents: CopilotAgentInfo[];
  selectedId: string;
  onSelect: (agentId: string) => void;
  disabled?: boolean;
}

export default function CopilotAgentSwitcher({ agents, selectedId, onSelect, disabled }: CopilotAgentSwitcherProps) {
  const { styles } = useTheme();
  if (agents.length === 0) {
    return null;
  }
  return (
    <div className={`px-3 py-2 border-b ${styles.cardBorder} ${styles.cardBg} shrink-0`}>
      <div className="flex gap-1.5 overflow-x-auto pb-0.5">
        {agents.map((agent) => {
          const active = agent.id === selectedId;
          return (
            <button
              type="button"
              key={agent.id}
              title={agent.description}
              onClick={() => { if (!disabled) onSelect(agent.id); }}
              disabled={disabled}
              className={`shrink-0 inline-flex items-center gap-1.5 px-2 py-1 rounded-full border text-[10px] font-bold transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed ${
                active
                  ? `${styles.accentBg} text-white ${styles.accentBorder}`
                  : `${styles.cardTextMuted} ${styles.cardBorder} ${styles.cardBg} hover:opacity-80`
              }`}
            >
              <Icon name={ICON_BY_ENGINE[agent.engine] || 'Bot'} size={11} />
              {agent.name}
            </button>
          );
        })}
      </div>
    </div>
  );
}
