import React, { useState, useEffect } from 'react';
import { Brain, Save, RefreshCw, Loader2, Settings } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { knowledgeApi } from '../services/knowledgeApi';

interface CognitiveConfigItem {
  config_key: string;
  config_value: string;
  description?: string;
}

export default function CognitiveConfigTab() {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const [configs, setConfigs] = useState<CognitiveConfigItem[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [toast, setToast] = useState<{ type: string; msg: string } | null>(null);

  const loadConfigs = async () => {
    setIsLoading(true);
    try {
      const data = await knowledgeApi.fetchCognitiveConfig();
      if (Array.isArray(data)) {
        setConfigs(data);
      } else if (data && typeof data === 'object') {
        setConfigs(Object.entries(data).map(([key, value]) => ({
          config_key: key,
          config_value: String(value),
        })));
      }
    } catch {
      setConfigs([
        { config_key: 'cognitive.model.default', config_value: 'deepseek-chat', description: locale === 'zh' ? '默认推理模型' : 'Default reasoning model' },
        { config_key: 'cognitive.max_tokens', config_value: '4096', description: locale === 'zh' ? '最大Token数' : 'Max tokens' },
        { config_key: 'cognitive.temperature', config_value: '0.7', description: locale === 'zh' ? '推理温度' : 'Temperature' },
        { config_key: 'cognitive.guardrails.enabled', config_value: 'true', description: locale === 'zh' ? '启用安全护栏' : 'Enable guardrails' },
        { config_key: 'cognitive.pii_detection.enabled', config_value: 'true', description: locale === 'zh' ? '启用PII检测' : 'Enable PII detection' },
        { config_key: 'cognitive.hallucination_check.enabled', config_value: 'true', description: locale === 'zh' ? '启用幻觉校验' : 'Enable hallucination check' },
        { config_key: 'cognitive.action_bridge.enabled', config_value: 'true', description: locale === 'zh' ? '启用动作桥接' : 'Enable action bridge' },
        { config_key: 'cognitive.agent_mesh.mode', config_value: 'sequential', description: locale === 'zh' ? 'Agent Mesh执行模式' : 'Agent Mesh execution mode' },
      ]);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => { loadConfigs(); }, []);

  const handleSave = async () => {
    setIsSaving(true);
    try {
      await knowledgeApi.updateCognitiveConfig(configs.map(c => ({
        config_key: c.config_key,
        config_value: c.config_value,
      })));
      setToast({ type: 'success', msg: locale === 'zh' ? '认知引擎配置已保存' : 'Cognitive config saved' });
    } catch {
      setToast({ type: 'error', msg: locale === 'zh' ? '保存失败' : 'Save failed' });
    } finally {
      setIsSaving(false);
      setTimeout(() => setToast(null), 3000);
    }
  };

  const handleChange = (idx: number, value: string) => {
    setConfigs(prev => {
      const next = [...prev];
      next[idx] = { ...next[idx], config_value: value };
      return next;
    });
  };

  return (
    <div className="space-y-6 max-w-3xl">
      <div className="flex items-center justify-between border-b border-slate-200 pb-3">
        <div className="space-y-1">
          <h2 className="text-sm font-black text-slate-800 flex items-center gap-2">
            <Brain size={16} className="text-purple-600" />
            {locale === 'zh' ? '认知引擎配置 (Cognitive Engine Config)' : 'Cognitive Engine Config'}
          </h2>
          <p className="text-xs text-slate-500">
            {locale === 'zh' ? '配置认知引擎的模型参数、安全策略和执行模式' : 'Configure cognitive engine model params, guardrails, and execution mode'}
          </p>
        </div>
        <div className="flex gap-2">
          <button onClick={loadConfigs} disabled={isLoading} className="px-3 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold rounded-lg flex items-center gap-1.5 cursor-pointer text-xs">
            {isLoading ? <Loader2 size={12} className="animate-spin" /> : <RefreshCw size={12} />}
            {locale === 'zh' ? '刷新' : 'Refresh'}
          </button>
          <button onClick={handleSave} disabled={isSaving} className="px-3 py-1.5 bg-purple-600 hover:bg-purple-700 text-white font-bold rounded-lg flex items-center gap-1.5 cursor-pointer text-xs disabled:opacity-60">
            {isSaving ? <Loader2 size={12} className="animate-spin" /> : <Save size={12} />}
            {locale === 'zh' ? '保存配置' : 'Save'}
          </button>
        </div>
      </div>

      <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-3">
        {isLoading ? (
          <div className="py-8 text-center text-slate-400 flex items-center justify-center gap-2"><Loader2 size={14} className="animate-spin" />{locale === 'zh' ? '加载中...' : 'Loading...'}</div>
        ) : (
          configs.map((config, idx) => (
            <div key={config.config_key} className="flex items-center gap-4 py-2 border-b border-slate-100 last:border-0">
              <div className="w-64 shrink-0 space-y-0.5">
                <div className="font-mono font-bold text-[10px] text-slate-800 truncate">{config.config_key}</div>
                {config.description && <div className="text-[9px] text-slate-400">{config.description}</div>}
              </div>
              <div className="flex-1">
                {config.config_value === 'true' || config.config_value === 'false' ? (
                  <button
                    onClick={() => handleChange(idx, config.config_value === 'true' ? 'false' : 'true')}
                    className={`w-10 h-5 rounded-full transition-all cursor-pointer ${config.config_value === 'true' ? 'bg-emerald-500' : 'bg-slate-300'}`}
                  >
                    <span className={`block w-4 h-4 rounded-full bg-white shadow transition-transform ${config.config_value === 'true' ? 'translate-x-5' : 'translate-x-0.5'}`} />
                  </button>
                ) : (
                  <input
                    value={config.config_value}
                    onChange={e => handleChange(idx, e.target.value)}
                    className="w-full px-2.5 py-1 border border-slate-200 rounded-md text-xs font-mono"
                  />
                )}
              </div>
            </div>
          ))
        )}
      </div>

      {toast && (
        <div className={`fixed bottom-6 right-6 z-50 px-4 py-2.5 rounded-lg shadow-lg text-sm font-medium ${
          toast.type === 'success' ? 'bg-emerald-600 text-white' : 'bg-rose-600 text-white'
        }`}>{toast.msg}</div>
      )}
    </div>
  );
}
