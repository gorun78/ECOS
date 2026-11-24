/**
 * 新建场景模态（L1「我的场景」，原型 v2.0 §新建场景；2026-11-24 接真实模板）。
 *
 * <p>模板只作为新建流程的预填包出现（选模板 → 预填目标/成功标准）；
 * 模板本身的增删改在独立「场景模板」页（{@code /workbench/templates}）。</p>
 *
 * <p>模板来源：{@code GET /api/v1/workspace/scenario-templates}（真实端点）。
 * 选中模板后用其 {@code definitionDefaults.goalStatement / successCriteria} 预填表单，
 * 创建走真实 POST（ScenarioSaveDTO，创建即 DRAFT）。模板列表为空时如实提示，
 * 不回落成前端写死的假模板。</p>
 */
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Modal from '../../components/common/Modal';
import { Check, Lightbulb } from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import { apiFetchData } from '../../api';
// #145/B6-2：场景集合基址单源
import { WORKSPACE_SCENARIOS_BASE } from '../../services/scenarioActionClient';
import { fetchScenarioTemplates, type ScenarioTemplateVO } from '../../services/scenarioConfigClient';
import { showToastGlobal } from '../../components/common/Toast';
import logger from '../../services/logger';

interface CreatedScenarioVO {
  id?: string;
}

/** 创建端点完整 URL：基址由 services/scenarioActionClient 的 WORKSPACE_SCENARIOS_BASE 单源持有（#145/B6-2） */
const SCENARIOS_URL = WORKSPACE_SCENARIOS_BASE;

/** 从模板的 definitionDefaults 取预填值（真实字段，取不到即空，不造值）。 */
function presetOf(tpl: ScenarioTemplateVO | null): { goal: string; criteria: string[] } {
  const d = (tpl?.definitionDefaults ?? {}) as Record<string, unknown>;
  const goal = typeof d.goalStatement === 'string' ? d.goalStatement : '';
  const raw = Array.isArray(d.successCriteria) ? d.successCriteria : [];
  const criteria = raw.filter((x): x is string => typeof x === 'string');
  return { goal, criteria };
}

export default function NewScenarioModal({
  isOpen,
  onClose,
  onCreated,
}: {
  isOpen: boolean;
  onClose: () => void;
  /** 创建成功（真实 POST 返回后）触发，供 L1 刷新场景列表 */
  onCreated?: (created: CreatedScenarioVO) => void;
}) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const navigate = useNavigate();

  const [name, setName] = useState('');
  const [goal, setGoal] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const [templates, setTemplates] = useState<ScenarioTemplateVO[]>([]);
  const [tplLoading, setTplLoading] = useState(false);
  const [tplError, setTplError] = useState<string | null>(null);
  const [selectedTplId, setSelectedTplId] = useState<string>('');

  const selectedTpl = useMemo(
    () => templates.find((x) => x.id === selectedTplId) ?? null,
    [templates, selectedTplId],
  );

  const reset = () => {
    setName('');
    setGoal('');
    setSelectedTplId('');
    setSubmitting(false);
  };

  const close = () => {
    reset();
    onClose();
  };

  /** 打开时拉真实模板清单（空列表也如实呈现，不造演示模板）。 */
  useEffect(() => {
    if (!isOpen) return;
    let cancelled = false;
    setTplLoading(true);
    setTplError(null);
    fetchScenarioTemplates()
      .then((rows) => { if (!cancelled) setTemplates(Array.isArray(rows) ? rows : []); })
      .catch((e: unknown) => {
        if (!cancelled) setTplError(e instanceof Error ? e.message : String(e));
      })
      .finally(() => { if (!cancelled) setTplLoading(false); });
    return () => { cancelled = true; };
  }, [isOpen]);

  /** 选模板：用模板真实 defaults 预填目标；再点一次取消（回到空白）。 */
  const pickTpl = (tpl: ScenarioTemplateVO) => {
    if (tpl.id === selectedTplId) {
      setSelectedTplId('');
      setGoal('');
      return;
    }
    setSelectedTplId(tpl.id ?? '');
    const p = presetOf(tpl);
    if (p.goal) setGoal(p.goal);
    if (!name.trim() && tpl.name) setName(tpl.name);
  };

  const submit = async () => {
    const trimmed = name.trim();
    if (!trimmed) {
      showToastGlobal('info', t('workspace.create.name_required'));
      return;
    }
    setSubmitting(true);
    try {
      // DTO 口径 = 后端 ScenarioSaveDTO：name 必填；businessGoal 缺省回落 description；
      // status 缺省后端即 DRAFT，不假声明。
      const created = await apiFetchData<CreatedScenarioVO>(SCENARIOS_URL, {
        method: 'POST',
        body: JSON.stringify({ name: trimmed, businessGoal: goal.trim() || undefined }),
      });
      close();
      showToastGlobal('success', t('workspace.create.created', { name: trimmed }));
      if (created && created.id) {
        onCreated?.(created);
        navigate(`/workbench/${created.id}`);
      } else {
        // 回显缺 id 属契约异常：不做本地兜底 id，只提示用户手动进入
        showToastGlobal('info', t('workspace.create.no_id'));
      }
    } catch (e) {
      logger.error('workbench-scenario-create', e);
      const err = e as { message?: string; traceId?: string };
      const base = err.message || t('workspace.create.failed_unknown');
      if (err.traceId) {
        showToastGlobal('error', `${base} (traceId: ${err.traceId})`);
      } else {
        showToastGlobal('error', t('workspace.create.failed', { msg: base }));
      }
      setSubmitting(false);
    }
  };

  const labelCls = `text-xs font-semibold ${styles.cardText} mb-1 block`;
  const inputCls = `w-full text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`;
  const tipCls = `text-[10px] flex items-center gap-1 ${styles.cardTextMuted}`;

  const preset = presetOf(selectedTpl);

  return (
    <Modal
      isOpen={isOpen}
      onClose={close}
      title={t('workspace.create.title')}
      size="sm"
      closeOnOverlay={!submitting}
      closeOnEscape={!submitting}
      footer={
        <div className="flex justify-end">
          <button
            type="button"
            disabled={submitting}
            onClick={() => void submit()}
            className={`inline-flex items-center gap-1.5 px-4 py-1.5 text-xs font-bold rounded-lg cursor-pointer transition ${
              submitting ? `${styles.inputBg} ${styles.cardTextMuted} cursor-not-allowed border ${styles.cardBorder}` : `${styles.accentBg} ${styles.accentSolidText} hover:opacity-90`
            }`}
          >
            {submitting ? (
              <span className="inline-block w-3 h-3 border-2 border-current border-t-transparent rounded-full animate-spin" />
            ) : (
              <Check size={12} />
            )}
            {t('common.confirm')}
          </button>
        </div>
      }
    >
      <div className="flex flex-col gap-3">
        {/* 场景模板（真实端点；选中即预填，再点取消） */}
        <div>
          <label className={labelCls}>{t('workspace.create.tpl')}</label>
          {tplLoading && <p className={`${tipCls} py-1`}>{t('workspace.create.tpl_loading')}</p>}
          {!tplLoading && tplError && (
            <p className={`text-[10px] ${styles.dangerText} py-1`}>{t('workspace.create.tpl_failed', { msg: tplError })}</p>
          )}
          {!tplLoading && !tplError && templates.length === 0 && (
            <p className={`${tipCls} py-1`}>{t('workspace.create.tpl_empty')}</p>
          )}
          {!tplLoading && !tplError && templates.length > 0 && (
            <div className="flex flex-wrap gap-1.5 max-h-32 overflow-y-auto">
              {templates.map((tp) => (
                <button
                  key={tp.id ?? tp.name}
                  type="button"
                  onClick={() => pickTpl(tp)}
                  title={tp.description ?? ''}
                  className={`px-2.5 py-1 text-[11px] font-semibold rounded border transition cursor-pointer ${
                    selectedTplId === tp.id
                      ? `${styles.accentBg} ${styles.accentSolidText}`
                      : `${styles.cardBorder} ${styles.cardText} hover:opacity-80`
                  }`}
                >
                  {tp.name ?? tp.id}
                </button>
              ))}
            </div>
          )}
          <p className={`mt-1.5 ${styles.cardTextMuted} text-[10px]`}>{t('workspace.create.tpl_hint')}</p>
        </div>

        <div>
          <label className={labelCls} htmlFor="new-scenario-name">
            {t('workspace.create.name')} *
          </label>
          <input
            id="new-scenario-name"
            className={inputCls}
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder={t('workspace.create.name_ph')}
            maxLength={255}
          />
        </div>
        <div>
          <label className={labelCls} htmlFor="new-scenario-goal">
            {t('workspace.create.goal')}
          </label>
          <input
            id="new-scenario-goal"
            className={inputCls}
            value={goal}
            onChange={(e) => setGoal(e.target.value)}
            placeholder={t('workspace.create.goal_ph')}
          />
        </div>

        {/* 模板带出的成功标准（只读回显真实值，便于创建前确认） */}
        {preset.criteria.length > 0 && (
          <div>
            <label className={labelCls}>{t('workspace.create.tpl_criteria')}</label>
            <ul className={`text-[11px] ${styles.cardTextMuted} list-disc pl-4 space-y-0.5`}>
              {preset.criteria.map((c) => <li key={c}>{c}</li>)}
            </ul>
          </div>
        )}

        <p className={tipCls}>
          <Lightbulb size={12} />
          {t('workspace.create.after_hint')}
        </p>
      </div>
    </Modal>
  );
}
