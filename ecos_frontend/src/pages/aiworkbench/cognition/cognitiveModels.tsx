import React, { useState, useCallback, useEffect } from 'react';
import { Plus, X, Eye } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme, type ThemeStyles } from '../../../components/ThemeContext';
import { cognitiveEngineApi } from '../../../services/cognitiveEngineApi';
import { showToastGlobal } from '../../../components/common/Toast';
import Button from '../../../components/common/Button';
import Badge, { type BadgeVariant } from '../../../components/common/Badge';

interface Model { id: string; code: string; name: string; modelType: string; status: string; version?: string; description?: string; [key: string]: unknown; }

// 主题合规（§七.1 / design-05 B.9-4）：状态色 → Badge 语义变体；生命周期色 → useTheme() token 键。
const STATUS_BADGE: Record<string, BadgeVariant> = {
  PUBLISHED: 'success',
  TESTING: 'warning',
  DRAFT: 'neutral',
  VALIDATED: 'info',
  DEPRECATED: 'danger',
};

const STATUS_LABELS: Record<string, string> = {
  PUBLISHED: '已发布', TESTING: '测试中', DRAFT: '草稿', VALIDATED: '已验证', DEPRECATED: '已弃用',
};

const LIFECYCLE_STEPS = ['DRAFT', 'TESTING', 'VALIDATED', 'PUBLISHED', 'DEPRECATED'];
const LIFECYCLE_LABELS: Record<string, string> = { DRAFT: 'Draft', TESTING: 'Testing', VALIDATED: 'Validated', PUBLISHED: 'Published', DEPRECATED: 'Deprecated' };
type TokenKey = keyof ThemeStyles & string;
const LIFECYCLE_TOKENS: Record<string, { borderKey: TokenKey; textKey: TokenKey }> = {
  DRAFT: { borderKey: 'appBorder', textKey: 'muted' },
  TESTING: { borderKey: 'warningBorder', textKey: 'warningText' },
  VALIDATED: { borderKey: 'infoBorder', textKey: 'infoText' },
  PUBLISHED: { borderKey: 'successBorder', textKey: 'successText' },
  DEPRECATED: { borderKey: 'dangerBorder', textKey: 'dangerText' },
};

const MODEL_TYPES = ['CAUSAL', 'SCENARIO', 'HYBRID', 'FORECAST', 'DECISION'];

export default function cognitiveModels() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [models, setModels] = useState<Model[]>([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [newName, setNewName] = useState('');
  const [newType, setNewType] = useState('CAUSAL');
  const [newDesc, setNewDesc] = useState('');
  const [creating, setCreating] = useState(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const data = await cognitiveEngineApi.fetchModels();
      const list: Model[] = Array.isArray(data) ? data : ((data as any)?.data ?? (data as any)?.records ?? []);
      setModels(list);
    } catch (e) {
      showToastGlobal('error', `${t('model.loadFail')}: ${(e as Error)?.message ?? e}`);
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => { loadData(); }, [loadData]);

  const handleCreate = async () => {
    if (creating) return;
    if (!newName.trim()) {
      showToastGlobal('error', t('model.nameRequired'));
      return;
    }
    setCreating(true);
    try {
      const modelId = `m_${Date.now()}`;
      await cognitiveEngineApi.createModel({ modelId, modelType: newType, features: { name: newName.trim(), description: newDesc.trim() } });
      showToastGlobal('success', t('model.created'));
      setShowModal(false);
      setNewName('');
      setNewType('CAUSAL');
      setNewDesc('');
      loadData();
    } catch (e) {
      showToastGlobal('error', `${t('model.createFail')}: ${(e as Error)?.message ?? e}`);
    } finally {
      setCreating(false);
    }
  };

  const modalField = `mt-1 w-full px-3 py-2 text-sm border ${styles.cardBorder} rounded-md ${styles.inputBg} ${styles.inputText}`;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className={`text-lg font-semibold ${styles.cardText}`}>{t('model.title')}</h1>
        <Button variant="primary" size="md" onClick={() => setShowModal(true)} aria-label={t('model.new')}>
          <Plus className="w-4 h-4" />
          {t('model.new')}
        </Button>
      </div>

      {/* Model cards */}
      {loading ? (
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-8 text-center ${styles.muted} text-sm`} role="status">{t('common.loading')}</div>
      ) : models.length === 0 ? (
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-8 text-center ${styles.muted} text-sm`}>{t('model.empty')}</div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4" role="list" aria-label={t('model.title')}>
          {models.map(m => (
            <div key={m.id} role="listitem" className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4 flex flex-col gap-3`}>
              <div className="flex items-start justify-between gap-2">
                <div>
                  <p className={`text-sm font-semibold ${styles.cardText}`}>{m.code ?? m.id}</p>
                  <p className={`text-xs ${styles.muted} mt-0.5`}>{m.name ?? ''}</p>
                </div>
                <Badge variant={STATUS_BADGE[m.status] ?? 'neutral'} className="flex-shrink-0">
                  {STATUS_LABELS[m.status] ?? m.status}
                </Badge>
              </div>
              <div className={`flex items-center gap-3 text-xs ${styles.muted}`}>
                <span>{m.modelType}</span>
                {m.version && <span>v{m.version}</span>}
              </div>
              {m.description && <p className={`text-xs ${styles.muted} line-clamp-2`}>{m.description}</p>}
              <div className="mt-auto">
                <button type="button" aria-label={t('model.view')}
                  className={`flex items-center gap-1 text-xs ${styles.accentText} hover:underline font-medium cursor-pointer`}>
                  <Eye className="w-3.5 h-3.5" aria-hidden />
                  {t('model.view')}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Lifecycle */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
        <h2 className={`text-sm font-medium ${styles.cardText} mb-3`}>{t('model.lifecycle')}</h2>
        <div className="flex items-center gap-2 overflow-x-auto pb-1">
          {LIFECYCLE_STEPS.map((s, i) => {
            const tk = LIFECYCLE_TOKENS[s];
            return (
              <React.Fragment key={s}>
                <div className={`px-3 py-1.5 rounded-md border text-xs font-medium whitespace-nowrap ${styles[tk.borderKey]} ${styles[tk.textKey]}`}>
                  {LIFECYCLE_LABELS[s]}
                </div>
                {i < LIFECYCLE_STEPS.length - 1 && <span className={`w-3 h-3 text-xs ${styles.muted} flex-shrink-0`}>→</span>}
              </React.Fragment>
            );
          })}
        </div>
      </div>

      {/* Modal */}
      {showModal && (
        <div className={`fixed inset-0 ${styles.overlayBg} flex items-center justify-center z-50 p-4`} onClick={() => setShowModal(false)}>
          <div className={`${styles.cardBg} rounded-lg shadow-xl w-full max-w-md p-6 space-y-4`} onClick={e => e.stopPropagation()}
            role="dialog" aria-label={t('model.new')} aria-modal="true">
            <div className="flex items-center justify-between">
              <h2 className={`text-base font-semibold ${styles.cardText}`}>{t('model.new')}</h2>
              <button type="button" onClick={() => setShowModal(false)} aria-label={t('common.close')}
                className={`p-1 rounded ${styles.muted} hover:opacity-70 cursor-pointer`}><X className="w-4 h-4" /></button>
            </div>
            <div className="space-y-3">
              <label className={`block text-sm font-medium ${styles.cardText}`}>
                {t('model.name')}
                <input className={modalField}
                  value={newName} onChange={e => setNewName(e.target.value)} />
              </label>
              <label className={`block text-sm font-medium ${styles.cardText}`}>
                {t('model.type')}
                <select className={modalField}
                  value={newType} onChange={e => setNewType(e.target.value)}>
                  {MODEL_TYPES.map(mt => <option key={mt} value={mt}>{mt}</option>)}
                </select>
              </label>
              <label className={`block text-sm font-medium ${styles.cardText}`}>
                {t('model.description')}
                <textarea className={`${modalField} min-h-[80px]`}
                  value={newDesc} onChange={e => setNewDesc(e.target.value)} />
              </label>
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button variant="secondary" onClick={() => setShowModal(false)} aria-label={t('common.cancel')}>
                {t('common.cancel')}
              </Button>
              <Button variant="primary" onClick={handleCreate} disabled={creating} aria-label={t('common.save')}>
                {creating ? <span className="inline-block w-3.5 h-3.5 border-2 border-current/30 border-t-current rounded-full animate-spin" aria-hidden /> : null}
                {t('common.save')}
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
