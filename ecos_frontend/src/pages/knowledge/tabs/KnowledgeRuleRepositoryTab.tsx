import React, { useState, useEffect } from 'react';
import { FileText, Plus, Search, Edit3, Trash2, ChevronDown, History } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { apiFetchData } from '../../../api';

interface Rule {
  id: string;
  name: string;
  domain: string;
  condition: string;
  action: string;
  status: string;
  version: number;
}

export default function KnowledgeRuleRepositoryTab() {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const tl = (zh: string, en: string) => locale === 'zh' ? zh : en;

  const [rules, setRules] = useState<Rule[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  useEffect(() => {
    loadRules();
  }, []);

  const loadRules = () => {
    setLoading(true);
    apiFetchData('/api/v1/kb/rules')
      .then((data: any) => setRules(data?.list || data || []))
      .catch(() => {
        setRules([
          { id: 'r1', name: '市场准入规则', domain: 'compliance', condition: 'market == "CN"', action: 'require CN license', status: 'ACTIVE', version: 3 },
          { id: 'r2', name: '数据分类规则', domain: 'governance', condition: 'data_type == "PII"', action: 'apply AES-256', status: 'ACTIVE', version: 2 },
          { id: 'r3', name: '财务报表规则', domain: 'finance', condition: 'fiscal_year == current', action: 'GAAP compliance', status: 'DRAFT', version: 1 },
        ]);
      })
      .finally(() => setLoading(false));
  };

  const filtered = rules.filter(r =>
    r.name.toLowerCase().includes(search.toLowerCase()) ||
    r.domain.toLowerCase().includes(search.toLowerCase())
  );

  const statusColor = (s: string) => {
    switch (s) {
      case 'ACTIVE': return 'bg-green-50 text-green-700';
      case 'DRAFT': return 'bg-yellow-50 text-yellow-700';
      case 'DEPRECATED': return 'bg-red-50 text-red-700';
      default: return 'bg-slate-50 text-slate-600';
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <FileText size={20} className="text-amber-400" />
          <div>
            <h2 className={`text-base font-bold ${styles.cardText}`}>
              {tl('规则库', 'Rule Repository')}
            </h2>
            <p className={`text-[11px] ${styles.cardTextMuted}`}>
              {tl('管理和版本化知识治理规则', 'Manage and version knowledge governance rules')}
            </p>
          </div>
        </div>
        <button className="flex items-center gap-1 px-3 py-1.5 bg-amber-500 hover:bg-amber-600 text-white text-xs rounded-lg transition">
          <Plus size={14} /> {tl('新建规则', 'New Rule')}
        </button>
      </div>

      <div className={`flex items-center gap-2 rounded-lg px-3 py-1.5 border ${styles.inputBg} ${styles.inputBorder}`}>
        <Search size={14} className={styles.muted} />
        <input
          type="text"
          className={`bg-transparent outline-none text-xs ${styles.inputText} placeholder-slate-400 w-full`}
          placeholder={tl('搜索规则名或领域...', 'Search by name or domain...')}
          value={search} onChange={e => setSearch(e.target.value)}
        />
      </div>

      <div className="space-y-1">
        {loading ? (
          <div className="text-center py-8 text-xs text-slate-400">{tl('加载中...', 'Loading...')}</div>
        ) : filtered.length === 0 ? (
          <div className="text-center py-8 text-xs text-slate-400">{tl('无匹配规则', 'No matching rules')}</div>
        ) : (
          filtered.map(rule => (
            <div key={rule.id} className={`rounded-lg border ${styles.cardBorder} ${styles.cardBg} overflow-hidden`}>
              <div
                className="flex items-center justify-between px-3 py-2 cursor-pointer hover:bg-slate-50/50"
                onClick={() => setExpandedId(expandedId === rule.id ? null : rule.id)}
              >
                <div className="flex items-center gap-3">
                  <ChevronDown size={14} className={`transition ${expandedId === rule.id ? 'rotate-180' : ''} text-slate-400`} />
                  <span className={`text-sm font-medium ${styles.cardText}`}>{rule.name}</span>
                  <span className={`px-1.5 py-0.5 rounded text-[10px] font-medium ${statusColor(rule.status)}`}>
                    {rule.status}
                  </span>
                </div>
                <div className="flex items-center gap-1.5">
                  <span className={`text-[10px] ${styles.muted}`}>v{rule.version}</span>
                  <Edit3 size={12} className={`${styles.muted} hover:text-amber-500`} onClick={e => e.stopPropagation()} />
                  <Trash2 size={12} className={`${styles.muted} hover:text-red-500`} onClick={e => e.stopPropagation()} />
                </div>
              </div>
              {expandedId === rule.id && (
                <div className="px-4 py-3 border-t border-slate-100 space-y-2 text-xs">
                  <div className="flex gap-4">
                    <span className={`${styles.muted} w-16`}>{tl('领域', 'Domain')}</span>
                    <span className="font-mono text-slate-600">{rule.domain}</span>
                  </div>
                  <div className="flex gap-4">
                    <span className={`${styles.muted} w-16`}>IF</span>
                    <span className="font-mono text-slate-600 bg-slate-50 rounded px-2 py-0.5">{rule.condition}</span>
                  </div>
                  <div className="flex gap-4">
                    <span className={`${styles.muted} w-16`}>THEN</span>
                    <span className="font-mono text-green-700 bg-green-50 rounded px-2 py-0.5">{rule.action}</span>
                  </div>
                  <div className="pt-1 border-t border-slate-50 flex gap-2">
                    <button className="flex items-center gap-1 text-[10px] text-indigo-500 hover:underline">
                      <History size={12} /> {tl('版本历史', 'History')}
                    </button>
                  </div>
                </div>
              )}
            </div>
          ))
        )}
      </div>
    </div>
  );
}
