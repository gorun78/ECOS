/**
 * AIP Copilot — one-click agent quick actions (bottom of drawer)
 * Pure presentation component. String display via i18n.
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import LucideIcon from '../LucideIcon';
import { useLanguage } from '../LanguageContext';
import { useAgentScenarios, AgentScenarioView, AgentScenarioType } from './AgentScenarioData';

interface AgentQuickActionsProps {
  viewMode: 'ontology' | 'explorer' | 'integration' | 'knowledge' | 'aip' | 'security' | 'workshop';
  isExecuting: boolean;
  onRun: (type: AgentScenarioType) => void;
}

/**
 * Map scenario type → lucide icon name.
 * Static map, no switch-case, complies with ECOS rules 4.6 (Record).
 */
const ICON_BY_SCENARIO: Record<AgentScenarioType, string> = {
  pipeline: 'Workflow',
  ontology: 'Boxes',
  health: 'ShieldAlert',
  lineage: 'Share2',
  sec_gdpr: 'ShieldCheck',
  sec_row_col: 'EyeOff',
  sec_finance: 'KeyRound',
  sec_audit: 'Activity',
  ws_generate_dashboard: 'LayoutDashboard',
  ws_auto_bind: 'Link',
  ws_inject_copilot: 'Bot',
  ws_transform_theme: 'Palette',
};

export default function AgentQuickActions({ viewMode, isExecuting, onRun }: AgentQuickActionsProps) {
  const { t } = useLanguage();
  const { data, security, workshop } = useAgentScenarios();

  // Pick the visible scenario group based on the current drawer view mode.
  const visible: AgentScenarioView[] =
    viewMode === 'security' ? security
    : viewMode === 'workshop' ? workshop
    : data;

  return (
    <div className="border-t border-slate-200 p-3 bg-slate-50 shrink-0 space-y-2 select-none">
      <span className="text-[10px] font-black uppercase text-slate-400 block tracking-wider font-mono">
        {t('copilot.chat.quickHeader')}
      </span>
      <div className="grid grid-cols-1 gap-2">
        {visible.map((s) => (
          <button
            key={s.type}
            onClick={() => { if (!isExecuting) onRun(s.type); }}
            disabled={isExecuting}
            className="w-full text-left p-2.5 bg-white border border-slate-200 rounded-xl hover:border-indigo-400 hover:bg-indigo-50/20 active:bg-indigo-50 transition-all text-xs flex flex-col gap-1 cursor-pointer group disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <div className="flex items-center justify-between w-full">
              <span className="font-extrabold text-slate-800 flex items-center gap-1.5">
                <LucideIcon
                  name={ICON_BY_SCENARIO[s.type]}
                  size={13}
                  className="text-indigo-500 group-hover:scale-110 transition-transform"
                />
                {s.title}
              </span>
              <span className="text-[9px] text-indigo-600 bg-indigo-50 px-1.5 py-0.2 rounded font-mono font-bold opacity-0 group-hover:opacity-100 transition-opacity">
                {t('copilot.chat.runAgent')}
              </span>
            </div>
            <p className="text-[10px] text-slate-400 font-sans group-hover:text-slate-500">
              {s.desc}
            </p>
          </button>
        ))}
      </div>
    </div>
  );
}
