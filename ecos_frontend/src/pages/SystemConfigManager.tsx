import React, { useState, useEffect, useCallback } from 'react';
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import { Settings, Save, Check, AlertCircle, Loader2, ChevronDown, ChevronRight, Info, Shield } from 'lucide-react';
import { fetchSysConfigs, updateSysConfig, fetchConfigAudit } from '../api';
import type { SysConfigItem, SysConfigGrouped, SysConfigAuditItem } from '../api';
import ErrorBoundary from '../components/common/ErrorBoundary';

const GROUPS: Record<string,{zh:string,en:string}> = {
  global: {zh:'全局', en:'Global'},
  'g1-data-foundation': {zh:'G1-数据底座', en:'G1-Foundation'},
  'g2-biz-semantics': {zh:'G2-业务语义', en:'G2-Semantics'},
  'g3-operations': {zh:'G3-运营', en:'G3-Operations'},
  'g4-agent': {zh:'G4-Agent', en:'G4-Agent'},
  'g5-infrastructure': {zh:'G5-基础设施', en:'G5-Infrastructure'},
};

const EDITION_FILTERS: {key:string; zh:string; en:string}[] = [
  { key: 'all', zh: '全部', en: 'All' },
  { key: 'standard', zh: '标准版', en: 'Standard' },
  { key: 'enterprise', zh: '企业版', en: 'Enterprise' },
  { key: 'ultimate', zh: '旗舰版', en: 'Ultimate' },
];

const EDITION_LABELS: Record<string, string> = {
  all: '全部版本',
  standard: '标准版',
  enterprise: '企业版',
  ultimate: '旗舰版',
};

export default function SystemConfigManager() {
  const { locale } = useLanguage() as any;
  const { styles } = useTheme() as any;

  const [groupedConfigs, setGroupedConfigs] = useState<SysConfigGrouped[]>([]);
  const [activeGroup, setActiveGroup] = useState('global');
  const [editionFilter, setEditionFilter] = useState('all');
  const [editing, setEditing] = useState<Record<string,string>>({});
  const [saving, setSaving] = useState<Record<string,boolean>>({});
  const [savedKeys, setSavedKeys] = useState<Set<string>>(new Set());
  const [errors, setErrors] = useState<Record<string,string>>({});
  const [loading, setLoading] = useState(true);
  const [expandedKey, setExpandedKey] = useState<string | null>(null);
  
  // Audit state
  const [auditOpen, setAuditOpen] = useState(false);
  const [auditData, setAuditData] = useState<SysConfigAuditItem[]>([]);
  const [auditLoading, setAuditLoading] = useState(false);

  const loadConfigs = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchSysConfigs();
      setGroupedConfigs(data || []);
    } catch(e) { console.error(e); }
    setLoading(false);
  }, []);

  useEffect(() => { loadConfigs(); }, [loadConfigs]);

  // Detect current edition for default filter tab
  useEffect(() => {
    const detectEdition = async () => {
      try {
        // Try window global first
        const winEdition = (window as any).__ECOS_EDITION__;
        if (winEdition && EDITION_FILTERS.some(ef => ef.key === winEdition)) {
          setEditionFilter(winEdition);
          return;
        }
        // Fallback: fetch from health endpoint
        const r = await fetch('/api/health');
        const d = await r.json();
        const edition = d.data?.edition || d.edition;
        if (edition && EDITION_FILTERS.some(ef => ef.key === edition)) {
          setEditionFilter(edition);
        }
      } catch { /* ignore */ }
    };
    detectEdition();
  }, []);

  const activeItems = groupedConfigs.find(g => g.group === activeGroup)?.items || [];

  // Filter by edition
  const filteredItems = editionFilter === 'all'
    ? activeItems
    : activeItems.filter(item => {
        const ed = (item.edition || 'all').toLowerCase();
        return ed === 'all' || ed === editionFilter;
      });

  const handleSave = async (key: string) => {
    const val = editing[key] ?? '';
    setSaving(prev => ({...prev, [key]: true}));
    try {
      await updateSysConfig(key, val);
      const s = new Set(savedKeys); s.add(key); setSavedKeys(s);
      setTimeout(() => { const ns = new Set(savedKeys); ns.delete(key); setSavedKeys(ns); }, 2000);
      // Update local state
      setGroupedConfigs(prev => prev.map(g =>
        g.group === activeGroup ? {
          ...g,
          items: g.items.map(item =>
            item.key === key ? { ...item, value: val } : item
          )
        } : g
      ));
    } catch(e: any) {
      setErrors(prev => ({...prev, [key]: e.message}));
    }
    setSaving(prev => { const n={...prev}; delete n[key]; return n; });
  };

  const handleAudit = async () => {
    setAuditLoading(true);
    setAuditOpen(true);
    try {
      const data = await fetchConfigAudit();
      setAuditData(data);
    } catch (e) {
      console.error('Audit fetch failed', e);
    }
    setAuditLoading(false);
  };

  const isZh = locale !== 'en';

  return (
    <ErrorBoundary>
    <div className={`flex h-full ${styles.cardBg}`}>
      {/* Left sidebar — group nav */}
      <div className={`w-48 border-r ${styles.sidebarBorder} ${styles.cardBg} p-3 flex flex-col gap-1`}>
        <h3 className={`text-xs font-semibold ${styles.muted} uppercase mb-2 px-1`}>{isZh?'配置分组':'Config Groups'}</h3>
        {Object.entries(GROUPS).map(([g,label]) => (
          <button key={g} onClick={() => { setActiveGroup(g); setExpandedKey(null); }}
            className={`text-left px-3 py-1.5 rounded text-sm transition-colors ${activeGroup===g?'bg-indigo-600 text-white':styles.muted+' hover:bg-gray-700'}`}>
            {isZh?label.zh:label.en}
          </button>
        ))}
      </div>

      {/* Main content */}
      <div className="flex-1 p-4 overflow-auto">
        {/* Header */}
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <Settings size={18} className={styles.muted}/>
            <h2 className={`text-lg font-semibold ${styles.text}`}>{isZh?'系统配置':'System Config'} — {GROUPS[activeGroup]?(isZh?GROUPS[activeGroup].zh:GROUPS[activeGroup].en):activeGroup}</h2>
          </div>
          <button onClick={handleAudit}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded bg-indigo-600 hover:bg-indigo-700 text-white text-sm transition-colors">
            <Shield size={14} />
            {isZh ? '参数审计' : 'Audit'}
          </button>
        </div>

        {/* Edition filter tabs */}
        <div className="flex gap-1 mb-4 flex-wrap">
          {EDITION_FILTERS.map(ef => (
            <button key={ef.key} onClick={() => setEditionFilter(ef.key)}
              className={`px-3 py-1 rounded text-xs transition-colors ${
                editionFilter === ef.key
                  ? 'bg-indigo-600 text-white'
                  : `${styles.cardBg} border ${styles.sidebarBorder} ${styles.muted} hover:bg-gray-700`
              }`}>
              {isZh ? ef.zh : ef.en}
            </button>
          ))}
        </div>

        {/* Loading state */}
        {loading ? (
          <div className="flex items-center gap-2 p-8"><Loader2 size={24} className="animate-spin text-indigo-400"/><span className={styles.muted}>{isZh?'加载中...':'Loading...'}</span></div>
        ) : (
          <div className="grid gap-3">
            {filteredItems.length === 0 && (
              <p className={`text-center py-8 ${styles.muted} text-sm`}>{isZh ? '当前筛选下无配置项' : 'No configs match the current filter'}</p>
            )}
            {filteredItems.map(cfg => {
              const curVal = editing[cfg.key] ?? cfg.value;
              const isSaving = saving[cfg.key];
              const isSaved = savedKeys.has(cfg.key);
              const error = errors[cfg.key];
              const isExpanded = expandedKey === cfg.key;
              const hasOptions = cfg.options && cfg.options.length > 0;
              const editionLabel = EDITION_LABELS[cfg.edition || 'all'] || (cfg.edition || '全部版本');

              return (
                <div key={cfg.key} className={`${styles.cardBg} border ${styles.sidebarBorder} rounded-lg overflow-hidden`}>
                  {/* Main row */}
                  <div className="p-4">
                    <div className="flex items-start justify-between">
                      <div className="flex-1">
                        <div className="flex items-center gap-2 mb-1">
                          <code className="text-xs px-1.5 py-0.5 rounded bg-gray-700 text-gray-300">{cfg.key}</code>
                          <span className={`text-sm font-medium ${styles.text}`}>{isZh ? (cfg.labelZh || cfg.label) : cfg.label}</span>
                          <span className={`text-[10px] px-1 py-0.5 rounded ${cfg.type==='boolean'?'bg-amber-700 text-amber-200':cfg.type==='number'?'bg-blue-700 text-blue-200':'bg-gray-600 text-gray-300'}`}>{cfg.type}</span>
                          {cfg.edition && cfg.edition !== 'all' && (
                            <span className="text-[10px] px-1 py-0.5 rounded bg-purple-700 text-purple-200">{editionLabel}</span>
                          )}
                        </div>
                        {cfg.description && <p className={`text-xs ${styles.muted} mb-2`}>{isZh && cfg.descriptionZh ? cfg.descriptionZh : cfg.description}</p>}
                      </div>
                      <div className="flex items-center gap-2">
                        {cfg.type === 'boolean' ? (
                          <button onClick={() => {
                            const nv = curVal==='true'?'false':'true';
                            setEditing(prev => ({...prev, [cfg.key]: nv}));
                            handleSave(cfg.key);
                            // update editing so toggle shows correctly
                            setGroupedConfigs(prev => prev.map(g =>
                              g.group === activeGroup ? {
                                ...g,
                                items: g.items.map(item =>
                                  item.key === cfg.key ? { ...item, value: nv } : item
                                )
                              } : g
                            ));
                          }} className={`w-12 h-6 rounded-full transition-colors ${curVal==='true'?'bg-indigo-600':'bg-gray-600'} relative`}>
                            <div className={`absolute top-0.5 w-5 h-5 rounded-full bg-white shadow transition-transform ${curVal==='true'?'translate-x-6':'translate-x-0.5'}`}/>
                          </button>
                        ) : hasOptions ? (
                          /* Dropdown select for string type with options */
                          <div className="flex items-center gap-2">
                            <select value={curVal}
                              onChange={e => setEditing(prev => ({...prev, [cfg.key]: e.target.value}))}
                              className="px-2 py-1 rounded text-sm w-48" style={{background:'#0f172a',color:'#e2e8f0',border:'1px solid #334155'}}>
                              {cfg.options!.map(opt => (
                                <option key={opt} value={opt}>{opt}</option>
                              ))}
                            </select>
                            <button onClick={() => handleSave(cfg.key)} disabled={isSaving}
                              className="p-1 rounded bg-indigo-600 hover:bg-indigo-700 text-white">{isSaving?<Loader2 size={14} className="animate-spin"/>:<Save size={14}/>}</button>
                          </div>
                        ) : (
                          /* Text/number input */
                          <div className="flex items-center gap-2">
                            <input type={cfg.type==='number'?'number':'text'} value={curVal}
                              onChange={e => setEditing(prev => ({...prev, [cfg.key]: e.target.value}))}
                              className="px-2 py-1 rounded text-sm w-48" style={{background:'#0f172a',color:'#e2e8f0',border:'1px solid #334155'}}/>
                            <button onClick={() => handleSave(cfg.key)} disabled={isSaving}
                              className="p-1 rounded bg-indigo-600 hover:bg-indigo-700 text-white">{isSaving?<Loader2 size={14} className="animate-spin"/>:<Save size={14}/>}</button>
                          </div>
                        )}
                        {isSaved && <Check size={16} className="text-emerald-400"/>}
                        {error && <span className="text-xs text-red-400"><AlertCircle size={12} className="inline mr-1"/>{error}</span>}
                        {/* Expand toggle */}
                        <button onClick={() => setExpandedKey(isExpanded ? null : cfg.key)}
                          className="p-1 rounded hover:bg-gray-600/30 transition-colors" title={isZh?'查看详情':'View details'}>
                          {isExpanded ? <ChevronDown size={14} className={styles.muted}/> : <ChevronRight size={14} className={styles.muted}/>}
                        </button>
                      </div>
                    </div>
                  </div>

                  {/* Expanded detail panel */}
                  {isExpanded && (
                    <div className={`border-t ${styles.sidebarBorder} px-4 py-3`}>
                      <div className="grid grid-cols-2 gap-3 text-xs">
                        <div>
                          <span className={`font-semibold ${styles.muted} uppercase tracking-wider`}>{isZh?'定义':'Definition'}</span>
                          <p className={styles.text + ' mt-1'}>{(isZh && cfg.descriptionZh) || cfg.description || (isZh?'无描述':'No description')}</p>
                        </div>
                        <div>
                          <span className={`font-semibold ${styles.muted} uppercase tracking-wider`}>{isZh?'默认值':'Default Value'}</span>
                          <p className={styles.text + ' mt-1 font-mono'}>{cfg.default_value || '—'}</p>
                        </div>
                        <div>
                          <span className={`font-semibold ${styles.muted} uppercase tracking-wider`}>{isZh?'影响范围':'Impact Scope'}</span>
                          <p className={styles.text + ' mt-1'}>{cfg.impactScope || (isZh?'未指定':'Unspecified')}</p>
                        </div>
                        <div>
                          <span className={`font-semibold ${styles.muted} uppercase tracking-wider`}>{isZh?'适用范围':'Edition'}</span>
                          <p className={styles.text + ' mt-1'}>{editionLabel}</p>
                        </div>
                        <div>
                          <span className={`font-semibold ${styles.muted} uppercase tracking-wider`}>{isZh?'消耗状态':'Consumed'}</span>
                          <p className={'mt-1 ' + (cfg.isConsumed ? 'text-amber-400' : 'text-emerald-400')}>
                            {cfg.isConsumed
                              ? `${isZh?'已消耗':'Consumed'}${cfg.consumedBy ? ` (${cfg.consumedBy})` : ''}`
                              : (isZh?'未消耗':'Not consumed')}
                          </p>
                        </div>
                        <div>
                          <span className={`font-semibold ${styles.muted} uppercase tracking-wider`}>{isZh?'当前值':'Current Value'}</span>
                          <p className={styles.text + ' mt-1 font-mono'}>{cfg.value || '—'}</p>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Audit modal / panel */}
      {auditOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60" onClick={() => setAuditOpen(false)}>
          <div className={`${styles.cardBg} border ${styles.sidebarBorder} rounded-xl w-[700px] max-h-[80vh] flex flex-col shadow-2xl`} onClick={e => e.stopPropagation()}>
            <div className={`flex items-center justify-between p-4 border-b ${styles.sidebarBorder}`}>
              <div className="flex items-center gap-2">
                <Shield size={18} className="text-indigo-400" />
                <h3 className={`font-semibold ${styles.text}`}>{isZh ? '参数消耗审计' : 'Config Consumption Audit'}</h3>
              </div>
              <button onClick={() => setAuditOpen(false)} className={`p-1 rounded hover:bg-gray-600/30 ${styles.muted}`}>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M18 6L6 18M6 6l12 12"/></svg>
              </button>
            </div>
            <div className="overflow-auto p-4 flex-1">
              {auditLoading ? (
                <div className="flex items-center gap-2 p-8"><Loader2 size={24} className="animate-spin text-indigo-400"/><span className={styles.muted}>{isZh?'加载中...':'Loading...'}</span></div>
              ) : auditData.length === 0 ? (
                <p className={`text-center py-8 ${styles.muted}`}>{isZh ? '暂无审计数据' : 'No audit data available'}</p>
              ) : (
                <table className="w-full text-sm">
                  <thead>
                    <tr className={`border-b ${styles.sidebarBorder} ${styles.muted} text-left text-xs uppercase`}>
                      <th className="py-2 px-3">{isZh?'参数键':'Key'}</th>
                      <th className="py-2 px-3">{isZh?'标签':'Label'}</th>
                      <th className="py-2 px-3">{isZh?'当前值':'Value'}</th>
                      <th className="py-2 px-3">{isZh?'消耗状态':'Consumed'}</th>
                      <th className="py-2 px-3">{isZh?'消耗者':'Consumed By'}</th>
                      <th className="py-2 px-3">{isZh?'消耗时间':'Consumed At'}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {auditData.map((item, idx) => (
                      <tr key={idx} className={`border-b ${styles.sidebarBorder} ${styles.text}`}>
                        <td className="py-2 px-3 font-mono text-xs">{item.key}</td>
                        <td className="py-2 px-3">{item.label}</td>
                        <td className="py-2 px-3 font-mono text-xs max-w-[120px] truncate">{item.value}</td>
                        <td className="py-2 px-3">
                          <span className={`px-1.5 py-0.5 rounded text-xs ${item.consumed ? 'bg-amber-700 text-amber-200' : 'bg-emerald-700 text-emerald-200'}`}>
                            {item.consumed ? (isZh?'已消耗':'Yes') : (isZh?'未消耗':'No')}
                          </span>
                        </td>
                        <td className="py-2 px-3 text-xs">{item.consumedBy || '—'}</td>
                        <td className="py-2 px-3 text-xs">{item.consumedAt || '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
    </ErrorBoundary>
  );
}
