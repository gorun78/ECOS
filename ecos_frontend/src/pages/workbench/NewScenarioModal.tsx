/**
 * 新建场景模态（L1「我的场景」，原型 v2.0 §新建场景）。
 *
 * <p>表单 = 原型 decCreateForm 五要素：名称*（必填校验）/ 业务目标 /
 * 模板 chips（空白 / 经营预测 / 成本诊断，选中即预填目标与初始口径）/
 * 初始口径（只读，由模板决定）/ 创建说明。
 * 提交走真实端点 POST /api/v1/workspace/scenarios（ScenarioSaveDTO，
 * 创建即 DRAFT）；成功 toast 后 navigate 到 L2 详情，失败 toast 直排
 * 后端 message（4xx 含 traceId 归因），绝不做本地假创建。</p>
 */
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Modal from '../../components/common/Modal';
import {
  Check,
  Lightbulb
} from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import { apiFetchData } from '../../api';
// #145/B6-2：场景集合基址单源
import { WORKSPACE_SCENARIOS_BASE } from '../../services/scenarioActionClient';
import { showToastGlobal } from '../../components/common/Toast';
import logger from '../../services/logger';

type NewSceneTpl = 'blank' | 'forecast' | 'cost';

const TEMPLATE_PRESETS: Record<NewSceneTpl, { goal: string; caliber: string }> = {
  blank: { goal: '', caliber: '—' },
  forecast: { goal: '2027 年利润达成 1.2 亿', caliber: 'cal-finance-v2.3' },
  cost: { goal: '定位项目成本偏差根因', caliber: 'cal-project-v1.1' },
};

interface CreatedScenarioVO {
  id?: string;
}

/** 创建端点完整 URL：基址由 services/scenarioActionClient 的 WORKSPACE_SCENARIOS_BASE 单源持有（#145/B6-2） */
const SCENARIOS_URL = WORKSPACE_SCENARIOS_BASE;

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

  const [tpl, setTpl] = useState<NewSceneTpl>('forecast');
  const [name, setName] = useState('');
  const [goal, setGoal] = useState(TEMPLATE_PRESETS.forecast.goal);
  const [submitting, setSubmitting] = useState(false);

  const reset = () => {
    setTpl('forecast');
    setName('');
    setGoal(TEMPLATE_PRESETS.forecast.goal);
    setSubmitting(false);
  };

  const close = () => {
    reset();
    onClose();
  };

  const pickTpl = (v: NewSceneTpl) => {
    setTpl(v);
    const p = TEMPLATE_PRESETS[v];
    if (p.goal) setGoal(p.goal);
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
        <div>
          <label className={labelCls}>{t('workspace.create.tpl')}</label>
          <div className="flex flex-wrap gap-1.5">
            {(['blank', 'forecast', 'cost'] as const).map((v) => (
              <button
                key={v}
                type="button"
                onClick={() => pickTpl(v)}
                className={`px-2.5 py-1 text-[11px] font-semibold rounded border transition cursor-pointer ${
                  tpl === v
                    ? `${styles.accentBg} ${styles.accentSolidText}`
                    : `${styles.cardBorder} ${styles.cardText} hover:opacity-80`
                }`}
              >
                {t(`workspace.create.tpl.${v}`)}
              </button>
            ))}
          </div>
          <p className={`mt-1.5 ${styles.cardTextMuted} text-[10px]`}>{t('workspace.create.tpl_hint')}</p>
        </div>
        <div>
          <label className={labelCls}>{t('workspace.create.caliber')}</label>
          <input className={`${inputCls} font-mono opacity-80`} value={TEMPLATE_PRESETS[tpl].caliber} readOnly />
        </div>
        <p className={tipCls}>
          <Lightbulb size={12} />
          {t('workspace.create.after_hint')}
        </p>
      </div>
    </Modal>
  );
}
