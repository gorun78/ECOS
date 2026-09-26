import React, { useState, useCallback, useEffect } from 'react';
import { Plus, X, Eye } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { cognitiveEngineApi } from '../../../services/cognitiveEngineApi';
import { showToastGlobal } from '../../../components/common/Toast';

interface Model { id: string; code: string; name: string; modelType: string; status: string; version?: string; description?: string; [key: string]: unknown; }

const STATUS_STYLES: Record<string, string> = {
  PUBLISHED: 'bg-green-100 dark:bg-green-950 text-green-700 dark:text-green-300',
  TESTING: 'bg-orange-100 dark:bg-orange-950 text-orange-700 dark:text-orange-300',
  DRAFT: 'bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400',
  VALIDATED: 'bg-blue-100 dark:bg-blue-950 text-blue-700 dark:text-blue-300',
  DEPRECATED: 'bg-red-100 dark:bg-red-950 text-red-700 dark:text-red-300',
};

const STATUS_LABELS: Record<string, string> = {
  PUBLISHED: '已发布', TESTING: '测试中', DRAFT: '草稿', VALIDATED: '已验证', DEPRECATED: '已弃用',
};

const LIFECYCLE_STEPS = ['DRAFT', 'TESTING', 'VALIDATED', 'PUBLISHED', 'DEPRECATED'];
const LIFECYCLE_LABELS: Record<string, string> = { DRAFT: 'Draft', TESTING: 'Testing', VALIDATED: 'Validated', PUBLISHED: 'Published', DEPRECATED: 'Deprecated' };
const LIFECYCLE_COLORS: Record<string, string> = {
  DRAFT: 'border-slate-300 dark:border-slate-600 text-slate-500 dark:text-slate-400',
  TESTING: 'border-orange-300 dark:border-orange-700 text-orange-600 dark:text-orange-400',
  VALIDATED: 'border-blue-300 dark:border-blue-700 text-blue-600 dark:text-blue-400',
  PUBLISHED: 'border-green-300 dark:border-green-700 text-green-600 dark:text-green-400',
  DEPRECATED: 'border-red-300 dark:border-red-700 text-red-600 dark:text-red-400',
};

const MODEL_TYPES = ['CAUSAL', 'SCENARIO', 'HYBRID', 'FORECAST', 'DECISION'];

export default function cognitiveModels() {
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
      showToastGlobal('error', `${t('model.loadFail', '加载模型列表失败')}: ${(e as Error)?.message ?? e}`);
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => { loadData(); }, [loadData]);

  const handleCreate = async () => {
    if (creating) return;
    if (!newName.trim()) {
      showToastGlobal('error', t('model.nameRequired', '请填写模型名称'));
      return;
    }
    setCreating(true);
    try {
      const modelId = `m_${Date.now()}`;
      await cognitiveEngineApi.createModel({ modelId, modelType: newType, features: { name: newName.trim(), description: newDesc.trim() } });
      showToastGlobal('success', t('model.created', '模型已创建'));
      setShowModal(false);
      setNewName('');
      setNewType('CAUSAL');
      setNewDesc('');
      loadData();
    } catch (e) {
      showToastGlobal('error', `${t('model.createFail', '创建失败')}: ${(e as Error)?.message ?? e}`);
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-lg font-semibold text-slate-900 dark:text-slate-100">{t('model.title', '认知模型')}</h1>
        <button onClick={() => setShowModal(true)}
          className="flex items-center gap-1 px-3 py-1.5 rounded-md text-sm font-medium bg-blue-600 text-white hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600">
          <Plus className="w-4 h-4" />
          {t('model.new', '新建认知模型')}
        </button>
      </div>

      {/* Model cards */}
      {loading ? (
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-8 text-center text-slate-400 text-sm">Loading…</div>
      ) : models.length === 0 ? (
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-8 text-center text-slate-400 text-sm">{t('model.empty', '暂无认知模型')}</div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {models.map(m => (
            <div key={m.id} className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-4 flex flex-col gap-3">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <p className="text-sm font-semibold text-slate-900 dark:text-slate-100">{m.code ?? m.id}</p>
                  <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">{m.name ?? ''}</p>
                </div>
                <span className={`text-xs px-2 py-0.5 rounded-full flex-shrink-0 ${STATUS_STYLES[m.status] ?? 'bg-slate-100 dark:bg-slate-800 text-slate-500'}`}>
                  {STATUS_LABELS[m.status] ?? m.status}
                </span>
              </div>
              <div className="flex items-center gap-3 text-xs text-slate-500 dark:text-slate-400">
                <span>{m.modelType}</span>
                {m.version && <span>v{m.version}</span>}
              </div>
              {m.description && <p className="text-xs text-slate-500 dark:text-slate-400 line-clamp-2">{m.description}</p>}
              <div className="mt-auto">
                <button className="flex items-center gap-1 text-xs text-blue-600 dark:text-blue-400 hover:underline font-medium">
                  <Eye className="w-3.5 h-3.5" />
                  {t('model.view', '查看模型')}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Lifecycle */}
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-4">
        <h2 className="text-sm font-medium text-slate-900 dark:text-slate-100 mb-3">{t('model.lifecycle', '模型生命周期')}</h2>
        <div className="flex items-center gap-2 overflow-x-auto pb-1">
          {LIFECYCLE_STEPS.map((s, i) => (
            <React.Fragment key={s}>
              <div className={`px-3 py-1.5 rounded-md border text-xs font-medium whitespace-nowrap ${LIFECYCLE_COLORS[s]}`}>
                {LIFECYCLE_LABELS[s]}
              </div>
              {i < LIFECYCLE_STEPS.length - 1 && <button className="w-3 h-3 text-slate-300 dark:text-slate-600 flex-shrink-0">→</button>}
            </React.Fragment>
          ))}
        </div>
      </div>

      {/* Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4" onClick={() => setShowModal(false)}>
          <div className="bg-white dark:bg-slate-900 rounded-lg shadow-xl w-full max-w-md p-6 space-y-4" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between">
              <h2 className="text-base font-semibold text-slate-900 dark:text-slate-100">{t('model.new', '新建认知模型')}</h2>
              <button onClick={() => setShowModal(false)} className="text-slate-400 hover:text-slate-600"><X className="w-4 h-4" /></button>
            </div>
            <div className="space-y-3">
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">
                {t('model.name', '模型名称')}
                <input className="mt-1 w-full px-3 py-2 text-sm border border-slate-200 dark:border-slate-700 rounded-md bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100"
                  value={newName} onChange={e => setNewName(e.target.value)} />
              </label>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">
                {t('model.type', '模型类型')}
                <select className="mt-1 w-full px-3 py-2 text-sm border border-slate-200 dark:border-slate-700 rounded-md bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100"
                  value={newType} onChange={e => setNewType(e.target.value)}>
                  {MODEL_TYPES.map(mt => <option key={mt} value={mt}>{mt}</option>)}
                </select>
              </label>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">
                {t('model.description', '模型描述')}
                <textarea className="mt-1 w-full px-3 py-2 text-sm border border-slate-200 dark:border-slate-700 rounded-md bg-white dark:bg-slate-800 text-slate-900 dark:text-slate-100 min-h-[80px]"
                  value={newDesc} onChange={e => setNewDesc(e.target.value)} />
              </label>
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <button onClick={() => setShowModal(false)}
                className="px-3 py-1.5 rounded-md text-sm font-medium bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700">
                {t('common.cancel', '取消')}
              </button>
              <button onClick={handleCreate} disabled={creating}
                className="px-3 py-1.5 rounded-md text-sm font-medium bg-blue-600 text-white hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600 disabled:opacity-50">
                {creating ? <span className="inline-block w-3.5 h-3.5 border-2 border-white/40 border-t-white rounded-full animate-spin" /> : null}
                {t('common.save', '保存')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
