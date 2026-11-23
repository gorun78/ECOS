/**
 * 场景定义页（场景工作台简化 v0.3）。
 *
 * <p>业务语义四段：基本信息 → 数据 → 知识 → 版本与发布。蓝图明细收进高级区；
 * 用户只面对「定义版本」与「待补齐项」，不出现 Mind/Goal Tree/Causal 等词。</p>
 */
import React, { useEffect, useMemo, useState } from 'react';
import { ChevronDown, ChevronUp, GitBranch, Save, Send, Workflow } from 'lucide-react';
import { useLanguage } from '../../../../components/LanguageContext';
import { useTheme } from '../../../../components/ThemeContext';
import { useMediaQuery } from '../../../../hooks/useMediaQuery';
import type { DefinitionVO, DefinitionVersionVO } from '../../../../services/scenarioDefinitionClient';
import { saveScenarioDefinition, publishScenarioDefinition } from '../../../../services/scenarioDefinitionClient';
import { showToastGlobal } from '../../../../components/common/Toast';

interface Props {
  scenarioId: string;
  definition: DefinitionVO | null;
  onSaved: (d: DefinitionVO) => void;
  onPublished: (d: DefinitionVO) => void;
}

type BindingGroupKey = 'datasets' | 'objectTypes' | 'knowledgeBases' | 'ruleSets' | 'aiAgents' | 'securityPolicies' | 'interfaces';

const GROUPS: Array<{ key: BindingGroupKey; labelKey: string; hintKey: string }> = [
  { key: 'datasets', labelKey: 'workspace.def.data.datasets', hintKey: 'workspace.def.data.datasetsHint' },
  { key: 'objectTypes', labelKey: 'workspace.def.data.objects', hintKey: 'workspace.def.data.objectsHint' },
  { key: 'knowledgeBases', labelKey: 'workspace.def.knowledge.bases', hintKey: 'workspace.def.knowledge.basesHint' },
  { key: 'ruleSets', labelKey: 'workspace.def.knowledge.rules', hintKey: 'workspace.def.knowledge.rulesHint' },
  { key: 'aiAgents', labelKey: 'workspace.def.capability.agents', hintKey: 'workspace.def.capability.agentsHint' },
  { key: 'securityPolicies', labelKey: 'workspace.def.security.policies', hintKey: 'workspace.def.security.policiesHint' },
  { key: 'interfaces', labelKey: 'workspace.def.security.interfaces', hintKey: 'workspace.def.security.interfacesHint' },
];

function safeMap(def: DefinitionVO | null): Record<string, string[]> {
  return (def?.bindings ?? {}) as Record<string, string[]>;
}

function scenarioField(def: DefinitionVO | null, key: string): string {
  const v = def?.scenario?.[key];
  return v == null ? '' : String(v);
}

export default function DefinitionTab({ scenarioId, definition, onSaved, onPublished }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const isMobile = useMediaQuery('(max-width: 767px)');

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [businessGoal, setBusinessGoal] = useState('');
  const [department, setDepartment] = useState('');
  const [priority, setPriority] = useState('MEDIUM');
  const [budget, setBudget] = useState('');
  const [saving, setSaving] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [showVersions, setShowVersions] = useState(false);

  useEffect(() => {
    setName(scenarioField(definition, 'name'));
    setDescription(scenarioField(definition, 'description'));
    setBusinessGoal(scenarioField(definition, 'businessGoal'));
    setDepartment(scenarioField(definition, 'department'));
    setPriority(scenarioField(definition, 'priority') || 'MEDIUM');
    setBudget(scenarioField(definition, 'budget'));
  }, [definition]);

  const versions = useMemo(() => definition?.versions ?? [], [definition]);
  const currentVersion = definition?.currentVersion ?? null;
  const upstream = definition?.upstream;
  const missing = upstream?.missingEdges ?? [];

  const save = async () => {
    setSaving(true);
    try {
      const payload = {
        name: name.trim(),
        description: description.trim() || undefined,
        businessGoal: businessGoal.trim() || undefined,
        department: department.trim() || undefined,
        priority,
        budget: budget.trim() || undefined,
      };
      const result = await saveScenarioDefinition(scenarioId, payload);
      onSaved(result);
      showToastGlobal('success', t('workspace.def.save_ok'));
    } catch (e) {
      showToastGlobal('error', e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  };

  const publish = async () => {
    setPublishing(true);
    try {
      const result = await publishScenarioDefinition(scenarioId, { approvalRequired: false });
      onPublished(result);
      showToastGlobal('success', t('workspace.def.publish_ok', { version: result.releasedVersion ?? '' }));
    } catch (e) {
      showToastGlobal('error', e instanceof Error ? e.message : String(e));
    } finally {
      setPublishing(false);
    }
  };

  return (
    <div className="p-4 space-y-4">
      {/* 基本信息 */}
      <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
        <h3 className={`text-sm font-bold ${styles.cardText} mb-3`}>{t('workspace.def.basic.title')}</h3>
        <div className={`grid gap-3 ${isMobile ? 'grid-cols-1' : 'grid-cols-2'}`}>
          <label className="space-y-1">
            <span className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.def.basic.name')}</span>
            <input value={name} onChange={(e) => setName(e.target.value)} className={`w-full text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`} />
          </label>
          <label className="space-y-1">
            <span className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.def.basic.department')}</span>
            <input value={department} onChange={(e) => setDepartment(e.target.value)} className={`w-full text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`} />
          </label>
          <label className="space-y-1">
            <span className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.def.basic.goal')}</span>
            <input value={businessGoal} onChange={(e) => setBusinessGoal(e.target.value)} className={`w-full text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`} />
          </label>
          <label className="space-y-1">
            <span className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.def.basic.priority')}</span>
            <select value={priority} onChange={(e) => setPriority(e.target.value)} className={`w-full text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}>
              {['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((p) => <option key={p} value={p}>{t(`workspace.def.priority.${p}`)}</option>)}
            </select>
          </label>
          <label className="space-y-1 col-span-full">
            <span className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.def.basic.description')}</span>
            <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={2} className={`w-full text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`} />
          </label>
        </div>
      </section>

      {/* 数据 / 知识 / 能力 / 安全 */}
      <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
        <h3 className={`text-sm font-bold ${styles.cardText} mb-3`}>{t('workspace.def.data.title')}</h3>
        <div className="space-y-3">
          {GROUPS.map((g) => {
            const items = safeMap(definition)[g.key] ?? [];
            return (
              <div key={g.key}>
                <div className="flex items-center justify-between mb-1">
                  <span className={`text-xs font-semibold ${styles.cardText}`}>{t(g.labelKey)}</span>
                  <span className={`text-[10px] ${styles.cardTextMuted}`}>{items.length}</span>
                </div>
                <p className={`text-[11px] ${styles.cardTextMuted} mb-2`}>{t(g.hintKey)}</p>
                {items.length === 0 ? (
                  <div className={`text-xs ${styles.cardTextMuted} border border-dashed ${styles.cardBorder} rounded p-3`}>
                    {t('workspace.def.empty')}
                  </div>
                ) : (
                  <ul className="space-y-1">
                    {items.map((id) => (
                      <li key={id} className={`text-xs ${styles.cardText} font-mono`}>{id}</li>
                    ))}
                  </ul>
                )}
              </div>
            );
          })}
        </div>
        {missing.length > 0 && (
          <div className={`mt-3 text-xs ${styles.warningBg} ${styles.warningText} ${styles.warningBorder} rounded p-2`}>
            <div className="font-bold mb-1">{t('workspace.def.upstream.title')}</div>
            <ul className="space-y-1">
              {missing.map((m, i) => (
                <li key={i}>{t('workspace.def.upstream.item', { type: m.type, reason: m.reason ?? '' })}</li>
              ))}
            </ul>
          </div>
        )}
      </section>

      {/* 版本与发布 */}
      <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
        <div className="flex items-center justify-between mb-3">
          <h3 className={`text-sm font-bold ${styles.cardText}`}>{t('workspace.def.version.title')}</h3>
          <button
            type="button"
            onClick={() => setShowVersions((v) => !v)}
            className={`text-xs ${styles.cardTextMuted} inline-flex items-center gap-1`}
          >
            {showVersions ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
            {t('workspace.def.version.toggle')}
          </button>
        </div>
        <div className="flex items-center gap-3 text-xs">
          <GitBranch size={14} className={styles.cardTextMuted} />
          <span className={styles.cardTextMuted}>{t('workspace.def.version.current')}：</span>
          <span className={`font-mono ${styles.cardText}`}>{currentVersion ?? t('workspace.def.version.none')}</span>
        </div>
        {showVersions && (
          <ul className="mt-3 space-y-2">
            {versions.map((v: DefinitionVersionVO) => (
              <li key={v.id} className={`border ${styles.cardBorder} rounded p-2 text-xs ${styles.cardText}`}>
                <div className="flex items-center justify-between">
                  <span className="font-mono">v{v.version}</span>
                  <span className={`text-[10px] px-1.5 py-0.5 rounded border ${styles.cardBorder} ${styles.cardTextMuted}`}>{v.status}</span>
                </div>
                <p className={`mt-1 ${styles.cardTextMuted}`}>{v.goalStatement ?? ''}</p>
                <p className={`mt-1 text-[10px] ${styles.cardTextMuted}`}>{v.releasedTime ?? ''}</p>
              </li>
            ))}
          </ul>
        )}
        <div className="flex gap-2 mt-4">
          <button
            type="button"
            disabled={saving || publishing}
            onClick={save}
            className={`inline-flex items-center gap-1 px-3 py-1.5 rounded border text-xs font-bold cursor-pointer ${styles.cardBg} ${styles.cardText} ${styles.cardBorder}`}
          >
            <Save size={12} />
            {saving ? t('workspace.def.saving') : t('workspace.def.save')}
          </button>
          <button
            type="button"
            disabled={saving || publishing}
            onClick={publish}
            className={`inline-flex items-center gap-1 px-3 py-1.5 rounded border text-xs font-bold cursor-pointer ${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`}
          >
            <Send size={12} />
            {publishing ? t('workspace.def.publishing') : t('workspace.def.publish')}
          </button>
        </div>
        <p className={`mt-2 text-[11px] ${styles.cardTextMuted}`}>
          <Workflow size={12} className="inline mr-1" />
          {t('workspace.def.publish_hint')}
        </p>
      </section>
    </div>
  );
}
