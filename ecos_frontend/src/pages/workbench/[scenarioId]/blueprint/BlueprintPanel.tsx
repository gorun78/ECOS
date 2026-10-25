/**
 * BlueprintPanel — 场景蓝图容器（设计方案 §3.2 L2 中心对象 / §3.4 交互 / §3.9 六态）。
 *
 * <p>唯一消费通道：{@link services/scenarioBlueprintClient}（绑定 workspace
 * `/business/scenarios/{id}/blueprints` 真实端点）。六态全覆盖：
 * 空 / 加载 / 错误（404→未找到、403→无权限、503→引擎横幅+traceId、其余→错误）/
 * 离线 / 权限 / 进行中。所有状态迁移、审批、发布、修订均调后端，按钮态由
 * `allowedTransitions` 决定（唯一权威），409 按 errorCode 呈现，绝不本地合成。</p>
 */
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertOctagon,
  AlertTriangle,
  Map,
  PlugZap,
  Plus,
  RefreshCw,
  Scale,
  TrendingUp
} from 'lucide-react';
import { Icon } from '../../../../components/LucideIcon';
import { useTheme } from '../../../../components/ThemeContext';
import { useLanguage } from '../../../../components/LanguageContext';
import logger from '../../../../services/logger';
import {
  listBlueprints,
  getBlueprint,
  createBlueprint,
  transitionBlueprint,
  enterApprovalGate,
  approveBlueprint,
  releaseBlueprint,
  reviseBlueprint,
  validateBlueprint,
  isBlueprintApiError,
  type ScenarioBlueprintVO,
  type BlueprintValidateResult,
  type BlueprintErrorCode,
} from '../../../../services/scenarioBlueprintClient';
import BlueprintStatusTrack from './BlueprintStatusTrack';
import BlueprintDetail from './BlueprintDetail';
import BlueprintValidatePanel from './BlueprintValidatePanel';
import CapabilityPanel from './CapabilityPanel';
import ContextBuildPanel from './ContextBuildPanel';
import WizardShell, { type WizardStep } from './WizardShell';

interface Props {
  scenarioId: string;
}

type Phase = 'loading' | 'ready' | 'empty' | 'not_found' | 'denied' | 'engine' | 'error';

function phaseOfError(e: unknown): { phase: Phase; msg: string; code?: BlueprintErrorCode; traceId?: string } {
  if (isBlueprintApiError(e)) {
    if (e.status === 404) return { phase: 'not_found', msg: e.message, code: e.code, traceId: e.traceId };
    if (e.status === 403 || e.status === 401) return { phase: 'denied', msg: e.message, code: e.code, traceId: e.traceId };
    if (e.status === 503) return { phase: 'engine', msg: e.message, code: e.code, traceId: e.traceId };
    if (e.code === 'NETWORK') return { phase: 'error', msg: e.message, code: e.code };
    return { phase: 'error', msg: e.message, code: e.code, traceId: e.traceId };
  }
  return { phase: 'error', msg: e instanceof Error ? e.message : String(e) };
}

export default function BlueprintPanel({ scenarioId }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [versions, setVersions] = useState<ScenarioBlueprintVO[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [phase, setPhase] = useState<Phase>('loading');
  const [loadMsg, setLoadMsg] = useState<string | null>(null);
  const [loadCode, setLoadCode] = useState<BlueprintErrorCode | undefined>();
  const [loadTraceId, setLoadTraceId] = useState<string | undefined>();

  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<{ code?: BlueprintErrorCode; msg: string; traceId?: string } | null>(null);

  const [validating, setValidating] = useState(false);
  const [validateResult, setValidateResult] = useState<BlueprintValidateResult | null>(null);
  const [validateError, setValidateError] = useState<string | null>(null);

  const [showCreate, setShowCreate] = useState(false);
  const [goal, setGoal] = useState('');
  const [criteria, setCriteria] = useState('');
  const [needApproval, setNeedApproval] = useState(true);
  const [note, setNote] = useState('');
  const [creating, setCreating] = useState(false);

  const [approvalRef, setApprovalRef] = useState('');

  const selected = useMemo(
    () => versions.find((v) => v.id === selectedId) ?? null,
    [versions, selectedId],
  );

  const load = useCallback(async () => {
    if (!scenarioId) return;
    setPhase('loading');
    setLoadMsg(null);
    setLoadCode(undefined);
    setLoadTraceId(undefined);
    try {
      const list = await listBlueprints(scenarioId);
      if (list.length === 0) {
        setVersions([]);
        setSelectedId(null);
        setPhase('empty');
        return;
      }
      // 后端升序：默认选最新版本；保留选择若仍存在
      setVersions(list);
      setSelectedId((prev) => (prev && list.some((x) => x.id === prev) ? prev : list[list.length - 1].id));
      setPhase('ready');
    } catch (e) {
      logger.error('blueprint-load', e);
      const { phase: p, msg, code, traceId } = phaseOfError(e);
      setPhase(p);
      setLoadMsg(msg);
      setLoadCode(code);
      setLoadTraceId(traceId);
    }
  }, [scenarioId]);

  useEffect(() => {
    void load();
  }, [load]);

  const resetValidate = (): void => {
    setValidateResult(null);
    setValidateError(null);
  };

  const pickVersion = async (id: string): Promise<void> => {
    setSelectedId(id);
    resetValidate();
    setActionError(null);
    try {
      const fresh = await getBlueprint(scenarioId, id);
      setVersions((prev) => prev.map((v) => (v.id === id ? fresh : v)));
    } catch (e) {
      logger.error('blueprint-get', e);
      // 详情刷新失败不阻断（列表已有快照），仅提示
      if (isBlueprintApiError(e)) setActionError({ code: e.code, msg: e.message, traceId: e.traceId });
    }
  };

  const runAction = async (fn: () => Promise<ScenarioBlueprintVO>): Promise<void> => {
    if (!selected) return;
    setBusy(true);
    setActionError(null);
    try {
      const updated = await fn();
      setVersions((prev) => prev.map((v) => (v.id === updated.id ? updated : v)));
      resetValidate();
      // 迁移后 allowedTransitions 可能变，拉一次最新快照
      try {
        const fresh = await getBlueprint(scenarioId, updated.id);
        setVersions((prev) => prev.map((v) => (v.id === fresh.id ? fresh : v)));
      } catch {
        /* 已用 updated 兜底 */
      }
    } catch (e) {
      logger.error('blueprint-action', e);
      if (isBlueprintApiError(e)) setActionError({ code: e.code, msg: e.message, traceId: e.traceId });
      else setActionError({ msg: e instanceof Error ? e.message : String(e) });
    } finally {
      setBusy(false);
    }
  };

  const allowed = new Set((selected?.allowedTransitions ?? []).map(String));

  const onTransition = (target: string): void => {
    if (!selected) return;
    void runAction(() => transitionBlueprint(scenarioId, selected.id, target));
  };

  const errKey = (code?: BlueprintErrorCode): string =>
    code && code !== 'NETWORK' && code !== 'HTTP_ERROR' && code !== 'BUSINESS_ERROR' && code !== 'UNKNOWN'
      ? `workspace.bp.err.${code}`
      : 'workspace.bp.err.unknown';

  const doValidate = async (): Promise<void> => {
    if (!selected) return;
    setValidating(true);
    setValidateError(null);
    try {
      const r = await validateBlueprint(scenarioId, selected.id);
      setValidateResult(r);
    } catch (e) {
      logger.error('blueprint-validate', e);
      setValidateResult(null);
      setValidateError(e instanceof Error ? e.message : String(e));
    } finally {
      setValidating(false);
    }
  };

  const doCreate = async (): Promise<void> => {
    if (!goal.trim()) return;
    setCreating(true);
    setActionError(null);
    try {
      const created = await createBlueprint(scenarioId, {
        scenarioId,
        goalStatement: goal.trim(),
        successCriteria: criteria
          .split('\n')
          .map((s) => s.trim())
          .filter(Boolean),
        approvalRequired: needApproval,
        note: note.trim() || undefined,
      });
      setShowCreate(false);
      setGoal('');
      setCriteria('');
      setNote('');
      setVersions((prev) => (prev.some((v) => v.id === created.id) ? prev : [...prev, created]));
      setSelectedId(created.id);
      setPhase('ready');
    } catch (e) {
      logger.error('blueprint-create', e);
      if (isBlueprintApiError(e)) setActionError({ code: e.code, msg: e.message, traceId: e.traceId });
      else setActionError({ msg: e instanceof Error ? e.message : String(e) });
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
      <div className="flex items-center gap-2 flex-wrap mb-3">
        <Map size={15} className={styles.accentText} />
        <h2 className={`text-sm font-extrabold ${styles.cardText}`}>{t('workspace.bp.title')}</h2>
        <span className={`text-[11px] ${styles.cardTextMuted}`}>{t('workspace.bp.subtitle')}</span>
        <button
          type="button"
          onClick={load}
          className={`ml-auto inline-flex items-center gap-1 text-xs font-bold px-2 py-1 rounded border cursor-pointer ${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`}
        >
          <RefreshCw size={12} />
          {t('workspace.bp.retry')}
        </button>
      </div>

      {/* 版本选择（最新默认） */}
      {phase === 'ready' && versions.length > 0 && (
        <div className="flex items-center gap-1.5 flex-wrap mb-3">
          <span className={`text-[10px] font-bold uppercase ${styles.cardTextMuted}`}>{t('workspace.bp.versions_title')}</span>
          {[...versions].reverse().map((v) => (
            <button
              key={v.id}
              type="button"
              onClick={() => void pickVersion(v.id)}
              className={`text-[11px] font-mono px-2 py-0.5 rounded border cursor-pointer ${
                v.id === selectedId ? `${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}` : `${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`
              }`}
            >
              {t('workspace.bp.version', { version: v.version })}
            </button>
          ))}
        </div>
      )}

      {/* 六态：加载 */}
      {phase === 'loading' && (
        <div className={`flex items-center gap-2 text-xs ${styles.cardTextMuted} py-6 justify-center`}>
          <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent opacity-60" />
          {t('workspace.bp.loading')}
        </div>
      )}

      {/* 空 */}
      {phase === 'empty' && (
        <div className={`p-6 rounded border text-center ${styles.cardBorder} ${styles.cardBg}`}>
          <Map size={22} className={`${styles.cardTextMuted}`} />
          <p className={`text-sm font-semibold ${styles.cardText}`}>{t('workspace.bp.empty')}</p>
          <p className={`text-xs ${styles.cardTextMuted} mt-1 mb-3 max-w-md mx-auto`}>{t('workspace.bp.empty_hint')}</p>
          <button
            type="button"
            onClick={() => setShowCreate((s) => !s)}
            className={`inline-flex items-center gap-1 text-xs font-bold px-3 py-1.5 rounded border cursor-pointer ${styles.accentBorder} ${styles.accentText} ${styles.cardBg}`}
          >
            <Plus size={12} />
            {t('workspace.bp.new')}
          </button>
        </div>
      )}

      {/* 未找到 / 无权限 */}
      {(phase === 'not_found' || phase === 'denied') && (
        <div className={`p-4 rounded border text-sm ${phase === 'denied' ? `${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}` : `${styles.cardBorder} ${styles.cardBg} ${styles.cardText}`}`}>
          <Icon name={phase === 'denied' ? 'ShieldAlert' : 'FileQuestion'} size={14} className="inline mr-1" />
          {phase === 'denied' ? t('workspace.bp.no_permission') : t('workspace.bp.not_found')}
        </div>
      )}

      {/* 引擎不可用 503：横幅 + traceId，不伪造 */}
      {phase === 'engine' && (
        <div className={`p-4 rounded border text-xs ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>
          <PlugZap size={14} className="inline mr-1" />
          {t('workspace.bp.engine_unavailable', { code: loadCode ?? 'ENGINE_DISABLED', traceId: loadTraceId ?? '—' })}
          <button onClick={load} className={`ml-2 underline cursor-pointer`}>{t('workspace.bp.retry')}</button>
        </div>
      )}

      {/* 通用错误 */}
      {phase === 'error' && (
        <div className={`p-4 rounded border text-xs ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
          <AlertTriangle size={12} className="inline mr-1" />
          {t('workspace.bp.error', { msg: loadMsg ?? '' })}
          <button onClick={load} className={`ml-2 underline cursor-pointer`}>{t('workspace.bp.retry')}</button>
        </div>
      )}

      {/* 建稿表单 */}
      {showCreate && phase !== 'ready' && (
        <CreateForm
          goal={goal}
          criteria={criteria}
          note={note}
          needApproval={needApproval}
          creating={creating}
          onGoal={setGoal}
          onCriteria={setCriteria}
          onNote={setNote}
          onNeedApproval={setNeedApproval}
          onCancel={() => setShowCreate(false)}
          onSubmit={() => void doCreate()}
        />
      )}

      {/* 主体：选中蓝图 */}
      {phase === 'ready' && selected && (
        <div className="space-y-3">
          {/* 顶层建稿入口（有版本时也可新建） */}
          {!showCreate && (
            <div className="flex justify-end">
              <button
                type="button"
                onClick={() => setShowCreate(true)}
                className={`inline-flex items-center gap-1 text-[11px] font-bold px-2 py-1 rounded border cursor-pointer ${styles.cardBg} ${styles.accentText} ${styles.accentBorder}`}
              >
                <Plus size={11} />
                {t('workspace.bp.new')}
              </button>
            </div>
          )}
          {showCreate && (
            <CreateForm
              goal={goal}
              criteria={criteria}
              note={note}
              needApproval={needApproval}
              creating={creating}
              onGoal={setGoal}
              onCriteria={setCriteria}
              onNote={setNote}
              onNeedApproval={setNeedApproval}
              onCancel={() => setShowCreate(false)}
              onSubmit={() => void doCreate()}
            />
          )}

          <WizardShell
            status={selected.status}
            steps={buildWizardSteps({
              scenarioId,
              selected,
              busy,
              allowed,
              actionError,
              errKey,
              onTransition,
              runAction,
              enterApprovalGate,
              approveBlueprint,
              releaseBlueprint,
              reviseBlueprint,
              approvalRef,
              setApprovalRef,
              validateResult,
              validating,
              validateError,
              doValidate,
              t,
              styles,
            })}
          />
        </div>
      )}
    </div>
  );
}

/** 向导五步内容装配（复用既有面板组件，复盘 S1：面板堆叠 → 步骤编排）。 */
function buildWizardSteps(ctx: {
  scenarioId: string;
  selected: ScenarioBlueprintVO;
  busy: boolean;
  allowed: Set<string>;
  actionError: { code?: BlueprintErrorCode; msg: string; traceId?: string } | null;
  errKey: (code?: BlueprintErrorCode) => string;
  onTransition: (target: string) => void;
  runAction: (fn: () => Promise<ScenarioBlueprintVO>) => Promise<void>;
  enterApprovalGate: (sid: string, id: string) => Promise<ScenarioBlueprintVO>;
  approveBlueprint: (sid: string, id: string, ref: string) => Promise<ScenarioBlueprintVO>;
  releaseBlueprint: (sid: string, id: string) => Promise<ScenarioBlueprintVO>;
  reviseBlueprint: (sid: string, id: string) => Promise<ScenarioBlueprintVO>;
  approvalRef: string;
  setApprovalRef: (v: string) => void;
  validateResult: BlueprintValidateResult | null;
  validating: boolean;
  validateError: string | null;
  doValidate: () => Promise<void>;
  t: (k: string, p?: Record<string, unknown>) => string;
  styles: ReturnType<typeof useTheme>['styles'];
}): WizardStep[] {
  const {
    scenarioId, selected, busy, allowed, actionError, errKey, onTransition, runAction,
    approvalRef, setApprovalRef, validateResult, validating, validateError, doValidate, t, styles,
  } = ctx;

  const goalStep = (
    <div className="space-y-3">
      <BlueprintStatusTrack blueprint={selected} onTransition={onTransition} busy={busy} />
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 space-y-2`}>
        <div className="flex items-center gap-2 flex-wrap">
          {allowed.has('READY_FOR_APPROVAL') && (
            <ActionButton icon="ClipboardCheck" label={t('workspace.bp.action.enter_approval')} busy={busy} onClick={() => void runAction(() => ctx.enterApprovalGate(scenarioId, selected.id))} />
          )}
          {allowed.has('APPROVED') && (
            <div className="flex items-center gap-1 flex-wrap">
              <input
                value={approvalRef}
                onChange={(e) => setApprovalRef(e.target.value)}
                placeholder={t('workspace.bp.approval_ref_ph')}
                className={`text-xs px-2 py-1 rounded border ${styles.cardBg} ${styles.cardText} ${styles.cardBorder}`}
              />
              <ActionButton icon="Stamp" label={t('workspace.bp.action.approve')} busy={busy} disabled={!approvalRef.trim()} onClick={() => void runAction(() => ctx.approveBlueprint(scenarioId, selected.id, approvalRef.trim()))} />
            </div>
          )}
          {allowed.has('RELEASED') && (
            <ActionButton icon="Rocket" label={t('workspace.bp.action.release')} busy={busy} onClick={() => void runAction(() => ctx.releaseBlueprint(scenarioId, selected.id))} />
          )}
          <ActionButton icon="GitCompareArrows" label={t('workspace.bp.action.revise')} busy={busy} variant="muted" onClick={() => void runAction(() => ctx.reviseBlueprint(scenarioId, selected.id))} />
        </div>

        {actionError && (
          <div className={`text-[11px] rounded border px-2 py-1.5 flex items-start gap-1.5 ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
            <AlertOctagon size={12} className="mt-0.5 shrink-0" />
            <span>
              {errKey(actionError.code) === 'workspace.bp.err.unknown'
                ? t('workspace.bp.err.unknown', { msg: actionError.msg })
                : t(errKey(actionError.code))}
              {actionError.traceId ? <span className={`ml-1 font-mono ${styles.cardTextMuted}`}>traceId={actionError.traceId}</span> : null}
            </span>
          </div>
        )}
      </div>
    </div>
  );

  const releaseStep = (
    <div className="space-y-2">
      <BlueprintStatusTrack blueprint={selected} busy />
      <p className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.wiz.release_hint')}</p>
      <div className="flex items-center gap-2 flex-wrap">
        <Link
          to={`/workbench/${scenarioId}/forecast`}
          className={`inline-flex items-center gap-1 text-xs font-bold px-2.5 py-1.5 rounded border cursor-pointer ${styles.accentBg} ${styles.cardText} ${styles.accentBorder}`}
        >
          <TrendingUp size={12} />
          {t('workspace.wiz.go_forecast')}
        </Link>
        <Link
          to={`/workbench/${scenarioId}/decisions`}
          className={`inline-flex items-center gap-1 text-xs font-bold px-2.5 py-1.5 rounded border cursor-pointer ${styles.cardBg} ${styles.cardText} ${styles.cardBorder}`}
        >
          <Scale size={12} />
          {t('workspace.wiz.go_decisions')}
        </Link>
      </div>
    </div>
  );

  return [
    { key: 'goal', icon: 'Target', content: goalStep },
    { key: 'context', icon: 'Layers', content: <ContextBuildPanel scenarioId={scenarioId} blueprint={selected} /> },
    {
      key: 'model',
      icon: 'Wrench',
      content: (
        <div className="space-y-3">
          <CapabilityPanel declaredCapabilities={selected.capabilities} />
          <BlueprintDetail bp={selected} />
        </div>
      ),
    },
    {
      key: 'validate',
      icon: 'ShieldCheck',
      content: <BlueprintValidatePanel result={validateResult} loading={validating} error={validateError} onRun={() => void doValidate()} disabled={!selected} />,
    },
    { key: 'release', icon: 'Rocket', content: releaseStep },
  ];
}

function ActionButton({ icon, label, busy, disabled, onClick, variant }: { icon: string; label: string; busy: boolean; disabled?: boolean; onClick: () => void; variant?: 'muted' }) {
  const { styles } = useTheme();
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={busy || disabled}
      className={[
        'inline-flex items-center gap-1 text-xs font-bold px-2.5 py-1.5 rounded border cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed transition-colors',
        variant === 'muted'
          ? `${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`
          : `${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`,
      ].join(' ')}
    >
      <Icon name={icon} size={12} />
      {label}
    </button>
  );
}

function CreateForm(props: {
  goal: string;
  criteria: string;
  note: string;
  needApproval: boolean;
  creating: boolean;
  onGoal: (v: string) => void;
  onCriteria: (v: string) => void;
  onNote: (v: string) => void;
  onNeedApproval: (v: boolean) => void;
  onCancel: () => void;
  onSubmit: () => void;
}) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const inputCls = `w-full text-xs px-2 py-1.5 rounded border ${styles.cardBg} ${styles.cardText} ${styles.cardBorder}`;
  return (
    <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 space-y-2`}>
      <h4 className={`text-[11px] font-bold uppercase ${styles.cardTextMuted}`}>{t('workspace.bp.create_dialog')}</h4>
      <div>
        <label className={`block text-[10px] ${styles.cardTextMuted} mb-0.5`}>{t('workspace.bp.goal')}</label>
        <input className={inputCls} value={props.goal} onChange={(e) => props.onGoal(e.target.value)} placeholder={t('workspace.bp.goal_ph')} />
      </div>
      <div>
        <label className={`block text-[10px] ${styles.cardTextMuted} mb-0.5`}>{t('workspace.bp.success_criteria')}</label>
        <textarea className={inputCls} rows={2} value={props.criteria} onChange={(e) => props.onCriteria(e.target.value)} placeholder={t('workspace.bp.success_criteria_ph')} />
      </div>
      <div>
        <label className={`block text-[10px] ${styles.cardTextMuted} mb-0.5`}>{t('workspace.bp.note')}</label>
        <input className={inputCls} value={props.note} onChange={(e) => props.onNote(e.target.value)} />
      </div>
      <label className={`flex items-center gap-1.5 text-xs ${styles.cardText} cursor-pointer`}>
        <input type="checkbox" checked={props.needApproval} onChange={(e) => props.onNeedApproval(e.target.checked)} />
        {t('workspace.bp.approval_required')}
      </label>
      <div className="flex items-center gap-2">
        <button type="button" onClick={props.onSubmit} disabled={props.creating || !props.goal.trim()} className={`inline-flex items-center gap-1 text-xs font-bold px-3 py-1.5 rounded border cursor-pointer ${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder} disabled:opacity-50`}>
          {props.creating ? t('workspace.bp.creating') : t('workspace.bp.create')}
        </button>
        <button type="button" onClick={props.onCancel} className={`text-xs font-bold px-3 py-1.5 rounded border cursor-pointer ${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`}>
          {t('workspace.bp.cancel')}
        </button>
      </div>
    </div>
  );
}
