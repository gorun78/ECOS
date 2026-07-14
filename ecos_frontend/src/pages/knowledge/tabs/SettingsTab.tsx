import React, { useState, useEffect } from 'react';
import { Settings, Save, RefreshCw, Loader2, Check } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { knowledgeApi } from '../services/knowledgeApi';
import type { KnowledgeSettings } from '../typesAndConstants';
import { DEFAULT_SETTINGS, CHUNK_SIZE_OPTIONS, VECTOR_MODELS } from '../typesAndConstants';

export default function SettingsTab() {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const [settings, setSettings] = useState<KnowledgeSettings>(DEFAULT_SETTINGS);
  const [isLoading, setIsLoading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [toast, setToast] = useState<{ type: string; msg: string } | null>(null);

  const loadSettings = async () => {
    setIsLoading(true);
    try {
      const data = await knowledgeApi.getSettings();
      setSettings(data);
    } catch {
      setSettings(DEFAULT_SETTINGS);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => { loadSettings(); }, []);

  const handleSave = async () => {
    setIsSaving(true);
    try {
      await knowledgeApi.updateSettings(settings);
      setToast({ type: 'success', msg: locale === 'zh' ? '配置已保存' : 'Settings saved' });
    } catch {
      setToast({ type: 'error', msg: locale === 'zh' ? '保存失败' : 'Save failed' });
    } finally {
      setIsSaving(false);
      setTimeout(() => setToast(null), 3000);
    }
  };

  const handleChange = (key: keyof KnowledgeSettings, value: any) => {
    setSettings(prev => ({ ...prev, [key]: value }));
  };

  return (
    <div className="space-y-6 max-w-3xl">
      <div className="flex items-center justify-between border-b border-slate-200 pb-3">
        <div className="space-y-1">
          <h2 className="text-sm font-black text-slate-800 flex items-center gap-2">
            <Settings size={16} className="text-slate-600" />
            {locale === 'zh' ? '工作台配置 (Knowledge Settings)' : 'Knowledge Settings'}
          </h2>
          <p className="text-xs text-slate-500">
            {locale === 'zh' ? '配置知识工作台的全局参数，包括向量模型、切块策略、Neo4j连接等' : 'Configure global knowledge workbench parameters'}
          </p>
        </div>
        <div className="flex gap-2">
          <button onClick={loadSettings} disabled={isLoading} className="px-3 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold rounded-lg flex items-center gap-1.5 cursor-pointer text-xs">
            {isLoading ? <Loader2 size={12} className="animate-spin" /> : <RefreshCw size={12} />}
            {locale === 'zh' ? '刷新' : 'Refresh'}
          </button>
          <button onClick={handleSave} disabled={isSaving} className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-lg flex items-center gap-1.5 cursor-pointer text-xs disabled:opacity-60">
            {isSaving ? <Loader2 size={12} className="animate-spin" /> : <Save size={12} />}
            {locale === 'zh' ? '保存配置' : 'Save'}
          </button>
        </div>
      </div>

      <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-5">
        <h3 className="font-extrabold text-slate-800 text-xs border-b border-slate-100 pb-2">
          {locale === 'zh' ? '向量索引配置' : 'Vector Index Config'}
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="space-y-1">
            <label className="block text-slate-600 font-bold text-[10px] uppercase">{locale === 'zh' ? '默认向量模型' : 'Default Vector Model'}</label>
            <select value={settings.defaultVectorModel} onChange={e => handleChange('defaultVectorModel', e.target.value)} className="w-full px-2.5 py-1.5 border border-slate-200 rounded-lg text-xs font-mono bg-white">
              {VECTOR_MODELS.map(m => <option key={m.id} value={m.id}>{m.label} ({m.dim}d)</option>)}
            </select>
          </div>
          <div className="space-y-1">
            <label className="block text-slate-600 font-bold text-[10px] uppercase">{locale === 'zh' ? '默认切块大小' : 'Default Chunk Size'}</label>
            <select value={settings.defaultChunkSize} onChange={e => handleChange('defaultChunkSize', Number(e.target.value))} className="w-full px-2.5 py-1.5 border border-slate-200 rounded-lg text-xs font-mono bg-white">
              {CHUNK_SIZE_OPTIONS.map(v => <option key={v} value={v}>{v}</option>)}
            </select>
          </div>
          <div className="space-y-1">
            <label className="block text-slate-600 font-bold text-[10px] uppercase">{locale === 'zh' ? '默认重叠度' : 'Default Overlap'}</label>
            <input type="number" value={settings.defaultOverlap} onChange={e => handleChange('defaultOverlap', Number(e.target.value))} min={0} max={500} className="w-full px-2.5 py-1.5 border border-slate-200 rounded-lg text-xs font-mono bg-white" />
          </div>
        </div>

        <h3 className="font-extrabold text-slate-800 text-xs border-b border-slate-100 pb-2 pt-2">
          {locale === 'zh' ? '检索与同步配置' : 'Retrieval & Sync Config'}
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="space-y-1">
            <label className="block text-slate-600 font-bold text-[10px] uppercase">{locale === 'zh' ? '最大检索结果数' : 'Max Retrieval Results'}</label>
            <input type="number" value={settings.maxRetrievalResults} onChange={e => handleChange('maxRetrievalResults', Number(e.target.value))} min={1} max={50} className="w-full px-2.5 py-1.5 border border-slate-200 rounded-lg text-xs font-mono bg-white" />
          </div>
          <div className="flex items-center gap-3 pt-4">
            <label className="text-slate-600 font-bold text-[10px] uppercase">{locale === 'zh' ? '启用Neo4j' : 'Enable Neo4j'}</label>
            <button onClick={() => handleChange('neo4jEnabled', !settings.neo4jEnabled)} className={`w-10 h-5 rounded-full transition-all cursor-pointer ${settings.neo4jEnabled ? 'bg-emerald-500' : 'bg-slate-300'}`}>
              <span className={`block w-4 h-4 rounded-full bg-white shadow transition-transform ${settings.neo4jEnabled ? 'translate-x-5' : 'translate-x-0.5'}`} />
            </button>
          </div>
          <div className="flex items-center gap-3 pt-4">
            <label className="text-slate-600 font-bold text-[10px] uppercase">{locale === 'zh' ? '自动同步' : 'Auto Sync'}</label>
            <button onClick={() => handleChange('autoSyncEnabled', !settings.autoSyncEnabled)} className={`w-10 h-5 rounded-full transition-all cursor-pointer ${settings.autoSyncEnabled ? 'bg-emerald-500' : 'bg-slate-300'}`}>
              <span className={`block w-4 h-4 rounded-full bg-white shadow transition-transform ${settings.autoSyncEnabled ? 'translate-x-5' : 'translate-x-0.5'}`} />
            </button>
          </div>
        </div>
      </div>

      {toast && (
        <div className={`fixed bottom-6 right-6 z-50 px-4 py-2.5 rounded-lg shadow-lg text-sm font-medium ${
          toast.type === 'success' ? 'bg-emerald-600 text-white' : 'bg-rose-600 text-white'
        }`}>{toast.msg}</div>
      )}
    </div>
  );
}
