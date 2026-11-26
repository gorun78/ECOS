/**
 * 场景定义页（场景工作台简化 v0.3，2026-11-23 补全为「单页定义全部要素」）。
 *
 * <p>业务语义四段：基本信息（含成功标准） → 数据/知识/能力/安全绑定 → 版本与发布。
 * 绑定在此直接增删、随保存一并落库（PUT 全量替换语义），不再需要去别处补齐。
 * 页头右侧是「场景上下文构建」——把当前定义的真实绑定作为权威源清单构建运行上下文包。
 * 发布按钮先落盘再发布，避免「改完直接发布」丢改动。</p>
 */
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { ChevronDown, ChevronUp, GitBranch, Plus, RefreshCw, Save, Send, Workflow, X } from 'lucide-react';
import { useLanguage } from '../../../../components/LanguageContext';
import { useTheme } from '../../../../components/ThemeContext';
import { useMediaQuery } from '../../../../hooks/useMediaQuery';
import type { DefinitionVO, DefinitionVersionVO } from '../../../../services/scenarioDefinitionClient';
import { saveScenarioDefinition, publishScenarioDefinition } from '../../../../services/scenarioDefinitionClient';
import { showToastGlobal } from '../../../../components/common/Toast';
import ScenarioContextBuilder from './ScenarioContextBuilder';
import { loadBindingOptions, type BindingGroupKey, type BindingOptionsState } from './bindingOptions';

interface Props {
  scenarioId: string;
  definition: DefinitionVO | null;
  onSaved: (d: DefinitionVO) => void;
  onPublished: (d: DefinitionVO) => void;
}

/**
 * 场景定义页的绑定分组（2026-11-26 批次 H：恢复「智能体」绑定）。
 *
 * <p>执行载体三选一需要场景侧能声明可用的编排与智能体；仍保留 securityPolicies / interfaces
 * 的后端枚举（历史数据可读），但前端不提供入口。规则集与知识资产绑定见批次 E。</p>
 */
const GROUPS: Array<{ key: BindingGroupKey; labelKey: string; hintKey: string }> = [
  { key: 'datasets', labelKey: 'workspace.def.data.datasets', hintKey: 'workspace.def.data.datasetsHint' },
  { key: 'objectTypes', labelKey: 'workspace.def.data.objects', hintKey: 'workspace.def.data.objectsHint' },
  { key: 'knowledgeBases', labelKey: 'workspace.def.knowledge.bases', hintKey: 'workspace.def.knowledge.basesHint' },
  { key: 'knowledgeArticles', labelKey: 'workspace.def.knowledge.articles', hintKey: 'workspace.def.knowledge.articlesHint' },
  { key: 'cognitiveAssets', labelKey: 'workspace.def.knowledge.cognitive', hintKey: 'workspace.def.knowledge.cognitiveHint' },
  { key: 'ruleSets', labelKey: 'workspace.def.knowledge.rules', hintKey: 'workspace.def.knowledge.rulesHint' },
  { key: 'pipelines', labelKey: 'workspace.def.capability.pipelines', hintKey: 'workspace.def.capability.pipelinesHint' },
  { key: 'aiAgents', labelKey: 'workspace.def.capability.agents', hintKey: 'workspace.def.capability.agentsHint' },
];

function safeMap(def: DefinitionVO | null): Record<string, string[]> {
  return (def?.bindings ?? {}) as Record<string, string[]>;
}

/** 分组 key → 后端 BindingType 八值（与 ScenarioService.BINDING_ENDPOINTS 逐字对齐）。 */
const BINDING_TYPE_BY_GROUP: Record<BindingGroupKey, string> = {
  datasets: 'DATASET',
  objectTypes: 'OBJECT_TYPE',
  knowledgeBases: 'KNOWLEDGE_BASE',
  knowledgeArticles: 'KNOWLEDGE_ARTICLE',
  cognitiveAssets: 'COGNITIVE_ASSET',
  ruleSets: 'RULE_SET',
  pipelines: 'PIPELINE',
  aiAgents: 'AI_AGENT',
  securityPolicies: 'SECURITY_POLICY',
  interfaces: 'INTERFACE',
};

/**
 * 后端承认的全部八类绑定（= BINDING_TYPE_BY_GROUP 的键集）。用于保存时全量回传，
 * 保证「UI 不提供入口」不等于「把已存数据清空」。
 */
const ALL_GROUP_KEYS: BindingGroupKey[] = [
  'datasets', 'objectTypes', 'knowledgeBases', 'knowledgeArticles', 'cognitiveAssets', 'ruleSets',
  'pipelines', 'aiAgents', 'securityPolicies', 'interfaces',
];

function emptyBindingState(): Record<BindingGroupKey, string[]> {
  return {
    datasets: [],
    objectTypes: [],
    knowledgeBases: [],
    knowledgeArticles: [],
    cognitiveAssets: [],
    ruleSets: [],
    pipelines: [],
    aiAgents: [],
    securityPolicies: [],
    interfaces: [],
  };
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
  /** 成功标准（一行一条）：发布校验与上下文 gap 生成的输入。 */
  const [criteriaText, setCriteriaText] = useState('');
  const [saving, setSaving] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [showVersions, setShowVersions] = useState(false);
  /** 绑定编辑态：与 definition 同步，保存时随定义一并提交（PUT 全量替换语义）。 */
  const [bindingState, setBindingState] = useState<Record<BindingGroupKey, string[]>>(emptyBindingState);
  /** 每个分组的新增输入框草稿（受控，回车/按钮提交到 bindingState）。 */
  const [bindingDraft, setBindingDraft] = useState<Record<BindingGroupKey, string>>(() => {
    const init = {} as Record<BindingGroupKey, string>;
    GROUPS.forEach((g) => { init[g.key] = ''; });
    return init;
  });

  /**
   * 八类绑定候选清单（方案 A：下拉选择取代「凭记忆手输 ID」）。
   * 全部取真实端点；某类无数据/无端点时如实给 emptyReason，不造演示项。
   */
  const [options, setOptions] = useState<Record<BindingGroupKey, BindingOptionsState> | null>(null);
  const [optionsLoading, setOptionsLoading] = useState(false);

  const reloadOptions = useCallback(() => {
    let cancelled = false;
    setOptionsLoading(true);
    loadBindingOptions(true)
      .then((next) => { if (!cancelled) setOptions(next); })
      .finally(() => { if (!cancelled) setOptionsLoading(false); });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => reloadOptions(), [reloadOptions]);

  useEffect(() => {
    setName(scenarioField(definition, 'name'));
    setDescription(scenarioField(definition, 'description'));
    setBusinessGoal(scenarioField(definition, 'businessGoal'));
    setDepartment(scenarioField(definition, 'department'));
    setPriority(scenarioField(definition, 'priority') || 'MEDIUM');
    setBudget(scenarioField(definition, 'budget'));
    // 成功标准来自当前定义版本（无版本时为空）
    const versions = definition?.versions ?? [];
    const draft = versions.filter((v) => String(v.status).toUpperCase() === 'DRAFT');
    const pool = draft.length > 0 ? draft : versions;
    const latest = pool.length > 0 ? pool.reduce((a, b) => (b.version > a.version ? b : a)) : null;
    const crit = latest?.successCriteria;
    setCriteriaText(Array.isArray(crit) ? crit.join('\n') : '');
  }, [definition]);

  // 绑定回填：definition 变化（首次加载 / 保存回读 / 发布回读）时重置编辑态。
  // 保存回读是服务端真实结果，重置不会丢失用户输入（保存时已提交）。
  useEffect(() => {
    const map = safeMap(definition);
    const next = emptyBindingState();
    GROUPS.forEach((g) => {
      next[g.key] = Array.isArray(map[g.key]) ? [...map[g.key]] : [];
    });
    setBindingState(next);
  }, [definition]);

  const versions = useMemo(() => definition?.versions ?? [], [definition]);
  const currentVersion = definition?.currentVersion ?? null;
  const upstream = definition?.upstream;
  const missing = upstream?.missingEdges ?? [];

  const addBinding = (key: BindingGroupKey) => {
    const raw = (bindingDraft[key] ?? '').trim();
    if (!raw) return;
    setBindingState((prev) => {
      if (prev[key].includes(raw)) return prev;
      return { ...prev, [key]: [...prev[key], raw] };
    });
    setBindingDraft((prev) => ({ ...prev, [key]: '' }));
  };

  const removeBinding = (key: BindingGroupKey, target: string) => {
    setBindingState((prev) => ({ ...prev, [key]: prev[key].filter((x) => x !== target) }));
  };

  /**
   * 定义保存体（Publish 前也复用：避免「改完绑定直接发布」丢失改动）。
   *
   * <p><b>必须提交全部八类绑定</b>（不只是可编辑的五类）：后端 `bindings` 是全量替换语义，
   * 只提交 GROUPS 会把 aiAgents/securityPolicies/interfaces 三类历史绑定静默清空——
   * 那是数据丢失而非「移除入口」。UI 不再提供这三类的编辑入口，但它们已存的值
   * 原样带回，保证「移除入口」不等于「删数据」。</p>
   */
  const buildPayload = () => ({
    name: name.trim(),
    description: description.trim() || undefined,
    businessGoal: businessGoal.trim() || undefined,
    department: department.trim() || undefined,
    priority,
    budget: budget.trim() || undefined,
    successCriteria: criteriaText.split('\n').map((s) => s.trim()).filter(Boolean),
    bindings: ALL_GROUP_KEYS.flatMap((key) =>
      (bindingState[key] ?? []).map((target) => ({
        bindingType: BINDING_TYPE_BY_GROUP[key],
        targetRef: target,
        targetId: target,
      })),
    ),
  });

  const save = async () => {
    setSaving(true);
    try {
      const result = await saveScenarioDefinition(scenarioId, buildPayload());
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
      // 先落盘再发布：发布端点只推进蓝图状态机，不写场景字段与绑定
      const saved = await saveScenarioDefinition(scenarioId, buildPayload());
      onSaved(saved);
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
      {/* 页头：左侧标题，右侧「场景上下文构建」（构建场景运行所需上下文包） */}
      <div className="flex items-center justify-between gap-2 flex-wrap">
        <h2 className={`text-sm font-extrabold ${styles.cardText}`}>{t('workspace.def.title')}</h2>
        <ScenarioContextBuilder scenarioId={scenarioId} definition={definition} />
      </div>

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
          <label className="space-y-1 col-span-full">
            <span className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.def.basic.criteria')}</span>
            <textarea
              value={criteriaText}
              onChange={(e) => setCriteriaText(e.target.value)}
              rows={3}
              placeholder={t('workspace.def.basic.criteria_ph')}
              className={`w-full text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
            />
          </label>
        </div>
      </section>

      {/* 数据 / 知识 / 能力 / 安全 —— 绑定在此直接增删，保存即落库 */}
      <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
        <h3 className={`text-sm font-bold ${styles.cardText} mb-3`}>{t('workspace.def.data.title')}</h3>
        <div className="space-y-4">
          {GROUPS.map((g) => {
            const items = bindingState[g.key] ?? [];
            const opt = options?.[g.key];
            const loading = optionsLoading || (opt?.loading ?? false);
            const candidates = (opt?.items ?? []).filter((o) => !items.includes(o.value));
            const selectable = candidates.length > 0;
            return (
              <div key={g.key}>
                <div className="flex items-center justify-between mb-1">
                  <span className={`text-xs font-semibold ${styles.cardText}`}>{t(g.labelKey)}</span>
                  <span className={`text-[10px] ${styles.cardTextMuted}`}>{items.length}</span>
                </div>
                <p className={`text-[11px] ${styles.cardTextMuted} mb-2`}>{t(g.hintKey)}</p>
                <div className="flex items-center gap-1.5 mb-2">
                  {selectable ? (
                    <select
                      value={bindingDraft[g.key] ?? ''}
                      onChange={(e) => setBindingDraft((prev) => ({ ...prev, [g.key]: e.target.value }))}
                      disabled={loading}
                      aria-label={t(g.labelKey)}
                      className={`flex-1 min-w-0 text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
                    >
                      <option value="">{t('workspace.def.binding_select_ph')}</option>
                      {candidates.map((o) => (
                        <option key={o.value} value={o.value}>
                          {o.hint ? `${o.label} · ${o.hint}` : o.label}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <div
                      className={`flex-1 min-w-0 text-[11px] rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.cardTextMuted}`}
                      role="status"
                    >
                      {loading
                        ? t('workspace.def.binding_loading')
                        : opt?.error
                          ? t('workspace.def.binding_load_failed', { msg: opt.error })
                          : (opt?.emptyReason ?? t('workspace.def.binding_none'))}
                    </div>
                  )}
                  {/* 候选为空/出错时给重试（不把失败伪装成「没有数据」） */}
                  {!loading && !selectable && (
                    <button
                      type="button"
                      onClick={() => reloadOptions()}
                      aria-label={t('workspace.def.binding_reload')}
                      className={`shrink-0 p-1.5 rounded border cursor-pointer ${styles.cardBg} ${styles.accentText} ${styles.accentBorder}`}
                    >
                      <RefreshCw size={11} />
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={() => addBinding(g.key)}
                    disabled={!(bindingDraft[g.key] ?? '').trim()}
                    className={`shrink-0 inline-flex items-center gap-1 px-2 py-1.5 rounded border text-[11px] font-bold cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed ${styles.cardBg} ${styles.accentText} ${styles.accentBorder}`}
                  >
                    <Plus size={11} />
                    {t('workspace.def.binding_add')}
                  </button>
                </div>
                {items.length === 0 ? (
                  <div className={`text-xs ${styles.cardTextMuted} border border-dashed ${styles.cardBorder} rounded p-3`}>
                    {t('workspace.def.empty')}
                  </div>
                ) : (
                  <ul className="space-y-1">
                    {items.map((id) => (
                      <li key={id} className={`flex items-center justify-between gap-2 text-xs ${styles.cardText} font-mono border ${styles.cardBorder} rounded px-2 py-1`}>
                        <span className="break-all">{id}</span>
                        <button
                          type="button"
                          onClick={() => removeBinding(g.key, id)}
                          aria-label={t('workspace.def.binding_remove')}
                          className={`shrink-0 p-0.5 rounded cursor-pointer ${styles.cardTextMuted} hover:opacity-70`}
                        >
                          <X size={12} />
                        </button>
                      </li>
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
