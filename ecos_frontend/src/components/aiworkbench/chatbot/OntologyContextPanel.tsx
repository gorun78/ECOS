/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * OntologyContextPanel — Ontology Context Panel (PMO-15 T7-3)
 * Displays bound ontology objects, actions, functions, and guardrails
 */

import React, { useState } from 'react';
import { AIPAgent } from '../../../types/aiworkbench';
import * as Icons from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

// ── Resolve name from ID using known lists ─────────────────────────
function resolveName<T extends { id: string; name: string }>(list: T[], id: string): string {
  return list.find(item => item.id === id)?.name || id;
}

function resolveIcon(list: { id: string; icon: string }[], id: string): string {
  return list.find(item => item.id === id)?.icon || 'HelpCircle';
}

interface OntologyContextPanelProps {
  activeChatbot: AIPAgent;
  styles: Record<string, string>;
  compact?: boolean;
}

export default function OntologyContextPanel({
  activeChatbot,
  styles,
  compact = false,
}: OntologyContextPanelProps) {
  const { t } = useLanguage();
  const [collapsed, setCollapsed] = useState(false);

  // ── Known ontology data ───────────────────────────────────────
  const KNOWN_OBJECTS: { id: string; name: string; icon: string; category: string }[] = [
    { id: 'AviationFlight', name: 'AviationFlight', icon: 'Plane', category: t('aiworkbench.chatbot.ocpCategoryCore') },
    { id: 'AviationPilot', name: 'AviationPilot', icon: 'User', category: t('aiworkbench.chatbot.ocpCategoryCore') },
    { id: 'AviationCrew', name: 'AviationCrew', icon: 'Users', category: t('aiworkbench.chatbot.ocpCategoryCore') },
    { id: 'AviationAircraft', name: 'AviationAircraft', icon: 'PlaneLanding', category: t('aiworkbench.chatbot.ocpCategoryCore') },
    { id: 'FlightSchedule', name: 'FlightSchedule', icon: 'Calendar', category: t('aiworkbench.chatbot.ocpCategoryRelated') },
    { id: 'WeatherReport', name: 'WeatherReport', icon: 'Cloud', category: t('aiworkbench.chatbot.ocpCategoryRelated') },
  ];

  const KNOWN_ACTIONS: { id: string; name: string; icon: string }[] = [
    { id: 'act_reschedule_flight', name: t('aiworkbench.chatbot.ocpActionReschedule'), icon: 'RefreshCw' },
    { id: 'act_cancel_flight', name: t('aiworkbench.chatbot.ocpActionCancel'), icon: 'XCircle' },
    { id: 'act_assign_crew', name: t('aiworkbench.chatbot.ocpActionAssignCrew'), icon: 'UserPlus' },
    { id: 'act_update_status', name: t('aiworkbench.chatbot.ocpActionUpdateStatus'), icon: 'Edit' },
  ];

  const KNOWN_FUNCTIONS: { id: string; name: string; icon: string }[] = [
    { id: 'func_get_flight_weather', name: t('aiworkbench.chatbot.ocpFuncWeather'), icon: 'CloudRain' },
    { id: 'func_get_pilot_status', name: t('aiworkbench.chatbot.ocpFuncPilotStatus'), icon: 'UserCheck' },
    { id: 'func_calculate_fuel', name: t('aiworkbench.chatbot.ocpFuncFuel'), icon: 'Fuel' },
  ];

  const actionCount = activeChatbot.assignedTools?.actionIds?.length || 0;
  const functionCount = activeChatbot.assignedTools?.functionIds?.length || 0;
  const guardrailCount = activeChatbot.guardrailIds?.length || 0;
  const totalBindings = actionCount + functionCount + guardrailCount;

  if (collapsed && compact) {
    return (
      <button
        onClick={() => setCollapsed(false)}
        className={`px-2 py-0.5 ${styles.cardBg} border ${styles.cardBorder} rounded text-[9px] ${styles.accentText} font-bold flex items-center gap-1`}
      >
        <Icon name="GitBranch" size={9} />
        <span>{t('aiworkbench.chatbot.ocpCollapsedLabel').replace('{count}', String(totalBindings))}</span>
        <Icon name="ChevronDown" size={8} />
      </button>
    );
  }

  if (collapsed && !compact) return null;

  return (
    <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl overflow-hidden`}>
      {/* Header */}
      <div className={`px-3 py-2 border-b ${styles.cardBorder} flex items-center justify-between`}>
        <span className={`font-extrabold text-[10px] ${styles.cardText} flex items-center gap-1.5`}>
          <Icon name="GitBranch" size={12} className={styles.accentText} />
          <span>{t('aiworkbench.chatbot.ocpTitle')}</span>
        </span>
        <div className="flex items-center gap-1.5">
          <span className={`text-[8px] font-mono ${styles.cardTextMuted} px-1.5 py-0.5 ${styles.inputBg} rounded`}>
            {t('aiworkbench.chatbot.ocpBindingCount').replace('{count}', String(totalBindings))}
          </span>
          {compact && (
            <button
              onClick={() => setCollapsed(true)}
              className={`p-0.5 ${styles.cardTextMuted}`}
            >
              <Icon name="ChevronUp" size={9} />
            </button>
          )}
        </div>
      </div>

      <div className="p-3 space-y-3">
        {/* Agent info summary */}
        <div className={`text-[9px] ${styles.cardTextMuted} font-mono space-y-0.5`}>
          <div className="flex items-center gap-1.5">
            <Icon name="Bot" size={10} className={styles.accentText} />
            <span className={`${styles.cardText} font-bold`}>{activeChatbot.name}</span>
          </div>
          <div className="flex items-center gap-1.5">
            <Icon name="Cpu" size={10} className={styles.cardTextMuted} />
            <span>{t('aiworkbench.chatbot.ocpModel')}: {activeChatbot.modelId}</span>
          </div>
          <div className="flex items-center gap-1.5">
            <Icon name="Activity" size={10} className={activeChatbot.status === 'active' ? 'text-emerald-500' : 'text-amber-500'} />
            <span className={activeChatbot.status === 'active' ? 'text-emerald-500' : 'text-amber-500'}>
              {t('aiworkbench.chatbot.ocpStatus')}: {activeChatbot.status === 'active' ? t('aiworkbench.chatbot.ocpStatusActive') : t('aiworkbench.chatbot.ocpStatusDev')}
            </span>
          </div>
        </div>

        {/* Bound Objects (Ontology Object Types) */}
        <div>
          <div className={`flex items-center gap-1.5 mb-1.5 text-[9px] font-extrabold ${styles.cardTextMuted}`}>
            <Icon name="Boxes" size={10} />
            <span>{t('aiworkbench.chatbot.ocpObjectTypes')}</span>
          </div>
          <div className="flex flex-wrap gap-1">
            {KNOWN_OBJECTS.filter(o =>
              actionCount > 0 || functionCount > 0 // Show all when bindings exist
            ).slice(0, 4).map(obj => (
              <span
                key={obj.id}
                className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[8px] font-bold ${
                  obj.category === t('aiworkbench.chatbot.ocpCategoryCore')
                    ? `${styles.accentBg}/10 ${styles.accentText} border ${styles.accentBorder}/30`
                    : `${styles.inputBg} ${styles.cardTextMuted} border ${styles.cardBorder}`
                }`}
              >
                <Icon name={obj.icon} size={8} />
                <span>{obj.name}</span>
              </span>
            ))}
          </div>
        </div>

        {/* Bound Actions */}
        <div>
          <div className={`flex items-center gap-1.5 mb-1.5 text-[9px] font-extrabold ${styles.cardTextMuted}`}>
            <Icon name="Settings" size={10} />
            <span>{t('aiworkbench.chatbot.ocpActionsHeader').replace('{count}', String(actionCount))}</span>
          </div>
          {actionCount > 0 ? (
            <div className="space-y-1">
              {activeChatbot.assignedTools.actionIds.map(actionId => {
                const known = KNOWN_ACTIONS.find(a => a.id === actionId);
                return (
                  <div
                    key={actionId}
                    className={`flex items-center gap-1.5 px-2 py-1 rounded text-[9px] ${styles.inputBg} border ${styles.cardBorder}`}
                  >
                    <Icon name={known?.icon || 'Zap'} size={9} className={styles.accentText} />
                    <span className={`${styles.cardText} font-bold`}>{known?.name || actionId}</span>
                    <span className={`${styles.cardTextMuted} font-mono text-[8px] ml-auto`}>{actionId}</span>
                    <span className="text-emerald-500 text-[7px] font-bold px-1 rounded bg-emerald-500/10">RW</span>
                  </div>
                );
              })}
            </div>
          ) : (
            <div className={`text-[8px] ${styles.cardTextMuted} italic px-2`}>
              {t('aiworkbench.chatbot.ocpNoActions')}
            </div>
          )}
        </div>

        {/* Bound Functions */}
        <div>
          <div className={`flex items-center gap-1.5 mb-1.5 text-[9px] font-extrabold ${styles.cardTextMuted}`}>
            <Icon name="FunctionSquare" size={10} />
            <span>{t('aiworkbench.chatbot.ocpFunctionsHeader').replace('{count}', String(functionCount))}</span>
          </div>
          {functionCount > 0 ? (
            <div className="space-y-1">
              {activeChatbot.assignedTools.functionIds.map(funcId => {
                const known = KNOWN_FUNCTIONS.find(f => f.id === funcId);
                return (
                  <div
                    key={funcId}
                    className={`flex items-center gap-1.5 px-2 py-1 rounded text-[9px] ${styles.inputBg} border ${styles.cardBorder}`}
                  >
                    <Icon name={known?.icon || 'Code'} size={9} className={styles.cardTextMuted} />
                    <span className={`${styles.cardText} font-bold`}>{known?.name || funcId}</span>
                    <span className={`${styles.cardTextMuted} font-mono text-[8px] ml-auto`}>{funcId}</span>
                    <span className="text-blue-500 text-[7px] font-bold px-1 rounded bg-blue-500/10">RO</span>
                  </div>
                );
              })}
            </div>
          ) : (
            <div className={`text-[8px] ${styles.cardTextMuted} italic px-2`}>
              {t('aiworkbench.chatbot.ocpNoFunctions')}
            </div>
          )}
        </div>

        {/* Active Guardrails */}
        <div>
          <div className={`flex items-center gap-1.5 mb-1.5 text-[9px] font-extrabold ${styles.cardTextMuted}`}>
            <Icon name="Shield" size={10} />
            <span>{t('aiworkbench.chatbot.ocpGuardrailsHeader').replace('{count}', String(guardrailCount))}</span>
          </div>
          {guardrailCount > 0 ? (
            <div className="space-y-1">
              {activeChatbot.guardrailIds.map(gId => {
                const displayName = gId.replace('gr-', '').replace(/-/g, ' ');
                const severityMap: Record<string, { color: string; bg: string; label: string }> = {
                  'gr-pii': { color: 'text-rose-500', bg: 'bg-rose-500/10', label: t('aiworkbench.chatbot.ocpSevBlock') },
                  'gr-approval': { color: 'text-amber-500', bg: 'bg-amber-500/10', label: t('aiworkbench.chatbot.ocpSevApproval') },
                  'gr-hallucination': { color: 'text-blue-500', bg: 'bg-blue-500/10', label: t('aiworkbench.chatbot.ocpSevAudit') },
                };
                const sev = severityMap[gId] || { color: 'text-slate-500', bg: 'bg-slate-500/10', label: t('aiworkbench.chatbot.ocpSevAudit') };
                return (
                  <div
                    key={gId}
                    className={`flex items-center gap-1.5 px-2 py-1 rounded text-[9px] ${styles.inputBg} border ${styles.cardBorder}`}
                  >
                    <Icon name="ShieldCheck" size={9} className={sev.color} />
                    <span className={`${styles.cardText} font-bold capitalize`}>{displayName}</span>
                    <span className={`${sev.color} text-[7px] font-bold px-1 rounded ml-auto ${sev.bg}`}>
                      {sev.label}
                    </span>
                  </div>
                );
              })}
            </div>
          ) : (
            <div className={`text-[8px] ${styles.cardTextMuted} italic px-2`}>
              {t('aiworkbench.chatbot.ocpNoGuardrails')}
            </div>
          )}
        </div>

        {/* Legend */}
        <div className={`pt-2 border-t ${styles.cardBorder} flex items-center gap-3 text-[7px] font-mono ${styles.cardTextMuted}`}>
          <span className="flex items-center gap-1">
            <span className="w-1.5 h-1.5 rounded-xs bg-emerald-500" /> {t('aiworkbench.chatbot.ocpLegendRW')}
          </span>
          <span className="flex items-center gap-1">
            <span className="w-1.5 h-1.5 rounded-xs bg-blue-500" /> {t('aiworkbench.chatbot.ocpLegendRO')}
          </span>
        </div>
      </div>
    </div>
  );
}
