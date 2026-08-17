/**
 * KnowledgeRuleRepositoryTab — 规则库树形导航
 *
 * Features:
 * - 左侧法规层级树: 法规→章节→条款三级
 * - GET /api/v1/knowledge/rules?groupBy=regulation → 前端groupBy构建树
 * - 点击章节→展开条款→右侧规则列表联动过滤
 * - 关联本体对象可点击跳转(navigate到本体工作台)
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect, useMemo, useCallback } from 'react';
import {
  FileText, Plus, Search, Edit3, Trash2, ChevronDown, ChevronRight,
  History, BookOpen, Bookmark, ScrollText, ExternalLink, Scale,
} from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { apiFetchData } from '../../../api';

// ── Types ──────────────────────────────────────────────────────

interface RuleItem {
  id: string;
  name: string;
  regulation: string;
  chapter?: string;
  clause?: string;
  domain?: string;
  condition?: string;
  action?: string;
  status: string;
  version: number;
  ontologyType?: string;
  ontologyTypeId?: string;
}

interface ChapterNode { name: string; clauses: ClauseNode[]; }
interface ClauseNode { name: string; ruleIds: string[]; }
interface RegulationNode { name: string; chapters: ChapterNode[]; ruleIds: string[]; }

// ── Demo data ──────────────────────────────────────────────────

const DEMO_RULES: RuleItem[] = [
  { id:'r1', name:'SSN脱敏规则', regulation:'GDPR', chapter:'Chapter II — Principles', clause:'Article 5(1)(c)', domain:'compliance', condition:'field.type=="SSN"', action:'mask(REDACT)', status:'ACTIVE', version:3, ontologyType:'PersonalData', ontologyTypeId:'ot-pii' },
  { id:'r2', name:'数据最小化规则', regulation:'GDPR', chapter:'Chapter II — Principles', clause:'Article 5(1)(c)', domain:'governance', condition:'collect.purpose=="unnecessary"', action:'reject_collection()', status:'ACTIVE', version:2, ontologyType:'DataCollection', ontologyTypeId:'ot-dc' },
  { id:'r3', name:'跨境传输限制', regulation:'GDPR', chapter:'Chapter V — Transfers', clause:'Article 44', domain:'compliance', condition:'transfer.target.outsideEU', action:'require_adequacy_decision()', status:'ACTIVE', version:5, ontologyType:'CrossBorderTransfer', ontologyTypeId:'ot-cbt' },
  { id:'r4', name:'被遗忘权处理', regulation:'GDPR', chapter:'Chapter III — Rights', clause:'Article 17', domain:'compliance', condition:'request.type=="erasure"', action:'delete_all_pii(subject)', status:'ACTIVE', version:4, ontologyType:'PersonalData', ontologyTypeId:'ot-pii' },
  { id:'r5', name:'消费者数据销售选择退出', regulation:'CCPA', chapter:'Right to Opt-Out', clause:'Section 1798.120', domain:'privacy', condition:'sale.opt_out==true', action:'stop_sale(subject)', status:'ACTIVE', version:2, ontologyType:'ConsumerProfile', ontologyTypeId:'ot-cp' },
  { id:'r6', name:'数据访问请求响应', regulation:'CCPA', chapter:'Right to Know', clause:'Section 1798.110', domain:'privacy', condition:'request.type=="access"', action:'provide_report(subject,12mo)', status:'DRAFT', version:1, ontologyType:'DataAccessRequest', ontologyTypeId:'ot-dar' },
  { id:'r7', name:'敏感数据分类保护', regulation:'CCPA', chapter:'Right to Know', clause:'Section 1798.110', domain:'governance', condition:'data.category=="sensitive"', action:'apply_enhanced_protection()', status:'ACTIVE', version:1, ontologyType:'SensitiveData', ontologyTypeId:'ot-sd' },
];

// ── Component ──────────────────────────────────────────────────

export default function KnowledgeRuleRepositoryTab() {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const tl = (zh: string, en: string) => locale === 'zh' ? zh : en;

  const [rules, setRules] = useState<RuleItem[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [expandedRegs, setExpandedRegs] = useState<Set<string>>(new Set());
  const [expandedChapters, setExpandedChapters] = useState<Set<string>>(new Set());
  const [selectedClause, setSelectedClause] = useState<string | null>(null);
  const [expandedRuleId, setExpandedRuleId] = useState<string | null>(null);

  useEffect(() => { loadRules(); }, []);

  const loadRules = () => {
    setLoading(true);
    apiFetchData('/api/v1/knowledge/rules?groupBy=regulation')
      .then((data: any) => {
        const list = data?.list || data?.data || data || [];
        setRules(Array.isArray(list) ? list : []);
      })
      .catch(() => { setRules(DEMO_RULES); })
      .finally(() => setLoading(false));
  };

  // Build regulation tree from rules
  const regTree = useMemo((): RegulationNode[] => {
    const map = new Map<string, RegulationNode>();
    for (const rule of rules) {
      const regName = rule.regulation || tl('未分类', 'Uncategorized');
      if (!map.has(regName)) map.set(regName, { name:regName, chapters:[], ruleIds:[] });
      const reg = map.get(regName)!;
      reg.ruleIds.push(rule.id);
      if (rule.chapter) {
        let chap = reg.chapters.find(c => c.name === rule.chapter);
        if (!chap) { chap = { name:rule.chapter, clauses:[] }; reg.chapters.push(chap); }
        const clauseName = rule.clause || tl('通用条款', 'General');
        let clause = chap.clauses.find(c => c.name === clauseName);
        if (!clause) { clause = { name:clauseName, ruleIds:[] }; chap.clauses.push(clause); }
        clause.ruleIds.push(rule.id);
      }
    }
    return Array.from(map.values());
  }, [rules, locale]);

  // Filter rules based on selected clause + search
  const filteredRules = useMemo(() => {
    let result = rules;
    if (selectedClause) result = result.filter(r => (r.clause || tl('通用条款', 'General')) === selectedClause);
    if (search) {
      const q = search.toLowerCase();
      result = result.filter(r => r.name.toLowerCase().includes(q) || (r.domain||'').toLowerCase().includes(q));
    }
    return result;
  }, [rules, selectedClause, search]);

  const toggleReg = useCallback((name: string) => {
    setExpandedRegs(prev => { const next = new Set(prev); next.has(name) ? next.delete(name) : next.add(name); return next; });
  }, []);
  const toggleChapter = useCallback((key: string) => {
    setExpandedChapters(prev => { const next = new Set(prev); next.has(key) ? next.delete(key) : next.add(key); return next; });
  }, []);
  const selectClause = useCallback((name: string) => {
    setSelectedClause(prev => prev === name ? null : name);
  }, []);

  const navigateToOntology = useCallback((ontologyTypeId?: string) => {
    if (ontologyTypeId) window.location.hash = `#/ontology-workbench?type=${encodeURIComponent(ontologyTypeId)}`;
  }, []);

  const statusColor = (s: string) => {
    switch (s) {
      case 'ACTIVE': return 'bg-emerald-50 text-emerald-700';
      case 'DRAFT': return 'bg-amber-50 text-amber-700';
      case 'DEPRECATED': return 'bg-rose-50 text-rose-700';
      default: return 'bg-slate-50 text-slate-600';
    }
  };

  const ruleCountForClause = (clauseName: string) => filteredRules.filter(r => (r.clause || tl('通用条款', 'General')) === clauseName).length;

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Scale size={20} className="text-amber-400" />
          <div>
            <h2 className={`text-base font-bold ${styles.cardText}`}>{tl('规则库', 'Rule Repository')}</h2>
            <p className={`text-[11px] ${styles.cardTextMuted}`}>
              {tl('按法规层级浏览和管理知识治理规则', 'Browse and manage governance rules by regulation hierarchy')}
            </p>
          </div>
        </div>
        <button className="flex items-center gap-1 px-3 py-1.5 bg-amber-500 hover:bg-amber-600 text-white text-xs rounded-lg transition cursor-pointer">
          <Plus size={14} /> {tl('新建规则', 'New Rule')}
        </button>
      </div>

      {/* Search bar */}
      <div className={`flex items-center gap-2 rounded-lg px-3 py-1.5 border ${styles.inputBg} ${styles.inputBorder}`}>
        <Search size={14} className={styles.cardTextMuted} />
        <input type="text"
          className={`bg-transparent outline-none text-xs ${styles.inputText} placeholder-slate-400 w-full`}
          placeholder={tl('搜索规则名或领域...', 'Search by name or domain...')}
          value={search} onChange={e => setSearch(e.target.value)}
        />
      </div>

      {/* Main content: Tree + Rule list */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
        {/* Left: Regulation tree */}
        <div className={`lg:col-span-4 ${styles.cardBg} border ${styles.cardBorder} rounded-xl p-3 max-h-[600px] overflow-y-auto`}>
          <h3 className={`text-[10px] font-extrabold ${styles.cardTextMuted} uppercase mb-2 flex items-center gap-1.5`}>
            <BookOpen size={11} /> {tl('法规层级', 'Regulation Hierarchy')}
          </h3>
          {loading ? (
            <div className="text-center py-8 text-[10px] text-slate-400">{tl('加载中...', 'Loading...')}</div>
          ) : regTree.length === 0 ? (
            <div className="text-center py-8 text-[10px] text-slate-400">{tl('无数据', 'No data')}</div>
          ) : (
            <div className="space-y-0.5">
              {regTree.map(reg => {
                const isRegOpen = expandedRegs.has(reg.name);
                return (
                  <div key={reg.name}>
                    {/* Regulation level */}
                    <button onClick={() => toggleReg(reg.name)}
                      className={`w-full text-left px-2 py-1.5 rounded-lg flex items-center gap-1.5 text-[11px] font-bold transition cursor-pointer ${
                        isRegOpen ? `${styles.accentBg} text-white` : `${styles.cardText} hover:bg-slate-50`
                      }`}>
                      {isRegOpen ? <ChevronDown size={11} /> : <ChevronRight size={11} />}
                      <Bookmark size={11} /> {reg.name}
                      <span className="ml-auto text-[9px] opacity-60">{reg.ruleIds.length}</span>
                    </button>
                    {isRegOpen && reg.chapters.map(chapter => {
                      const chapKey = `${reg.name}::${chapter.name}`;
                      const isChapOpen = expandedChapters.has(chapKey);
                      return (
                        <div key={chapKey} className="ml-4">
                          {/* Chapter level */}
                          <button onClick={() => toggleChapter(chapKey)}
                            className={`w-full text-left px-2 py-1.5 rounded-lg flex items-center gap-1.5 text-[10px] font-semibold transition cursor-pointer ${
                              isChapOpen ? 'text-blue-600 bg-blue-50' : `${styles.cardTextMuted} hover:bg-slate-50`
                            }`}>
                            {isChapOpen ? <ChevronDown size={10} /> : <ChevronRight size={10} />}
                            <ScrollText size={10} /> {chapter.name}
                          </button>
                          {isChapOpen && (
                            <div className="ml-4 space-y-0.5 border-l-2 border-slate-100 pl-2">
                              {chapter.clauses.map(clause => {
                                const isActive = selectedClause === clause.name;
                                const count = ruleCountForClause(clause.name);
                                return (
                                  <button key={clause.name} onClick={() => selectClause(clause.name)}
                                    className={`w-full text-left px-2 py-1 rounded text-[9px] font-medium flex items-center gap-1.5 transition cursor-pointer ${
                                      isActive ? 'bg-amber-50 text-amber-700 font-bold' : `${styles.cardTextMuted} hover:bg-slate-50`
                                    }`}>
                                    <FileText size={9} /> {clause.name}
                                    {count > 0 && <span className="ml-auto text-[8px] bg-slate-100 px-1 rounded">{count}</span>}
                                  </button>
                                );
                              })}
                            </div>
                          )}
                        </div>
                      );
                    })}
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Right: Rule list */}
        <div className="lg:col-span-8 space-y-1">
          {selectedClause && (
            <div className="flex items-center gap-2 mb-2">
              <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full bg-amber-50 text-amber-700 flex items-center gap-1`}>
                <FileText size={10} /> {selectedClause}
              </span>
              <button onClick={() => setSelectedClause(null)} className={`text-[9px] ${styles.cardTextMuted} hover:underline`}>
                {tl('清除筛选', 'Clear')}
              </button>
            </div>
          )}
          {loading ? (
            <div className="text-center py-8 text-[10px] text-slate-400">{tl('加载中...', 'Loading...')}</div>
          ) : filteredRules.length === 0 ? (
            <div className={`text-center py-12 text-[10px] ${styles.cardTextMuted}`}>
              {selectedClause ? tl('该条款下无匹配规则', 'No rules under this clause') : tl('无匹配规则', 'No matching rules')}
            </div>
          ) : (
            filteredRules.map(rule => (
              <div key={rule.id} className={`rounded-lg border ${styles.cardBorder} ${styles.cardBg} overflow-hidden`}>
                <div className="flex items-center justify-between px-3 py-2 cursor-pointer hover:bg-slate-50/50"
                  onClick={() => setExpandedRuleId(expandedRuleId === rule.id ? null : rule.id)}>
                  <div className="flex items-center gap-3">
                    <ChevronDown size={14} className={`transition ${expandedRuleId===rule.id?'rotate-180':''} text-slate-400`} />
                    <span className={`text-sm font-medium ${styles.cardText}`}>{rule.name}</span>
                    <span className={`px-1.5 py-0.5 rounded text-[10px] font-medium ${statusColor(rule.status)}`}>{rule.status}</span>
                    {rule.regulation && (
                      <span className="text-[9px] text-slate-400 bg-slate-50 px-1.5 py-0.5 rounded">{rule.regulation}</span>
                    )}
                  </div>
                  <div className="flex items-center gap-1.5">
                    <span className={`text-[10px] ${styles.cardTextMuted}`}>v{rule.version}</span>
                    <Edit3 size={12} className={`${styles.cardTextMuted} hover:text-amber-500`} onClick={e => e.stopPropagation()} />
                    <Trash2 size={12} className={`${styles.cardTextMuted} hover:text-red-500`} onClick={e => e.stopPropagation()} />
                  </div>
                </div>
                {expandedRuleId === rule.id && (
                  <div className="px-4 py-3 border-t border-slate-100 space-y-2 text-xs">
                    {rule.chapter && (
                      <div className="flex gap-4">
                        <span className={`${styles.cardTextMuted} w-16`}>{tl('章节', 'Chapter')}</span>
                        <span className="text-slate-600">{rule.chapter} · {rule.clause || '—'}</span>
                      </div>
                    )}
                    {rule.domain && (
                      <div className="flex gap-4">
                        <span className={`${styles.cardTextMuted} w-16`}>{tl('领域', 'Domain')}</span>
                        <span className="font-mono text-slate-600">{rule.domain}</span>
                      </div>
                    )}
                    {rule.condition && (
                      <div className="flex gap-4">
                        <span className={`${styles.cardTextMuted} w-16`}>IF</span>
                        <span className="font-mono text-slate-600 bg-slate-50 rounded px-2 py-0.5">{rule.condition}</span>
                      </div>
                    )}
                    {rule.action && (
                      <div className="flex gap-4">
                        <span className={`${styles.cardTextMuted} w-16`}>THEN</span>
                        <span className="font-mono text-emerald-700 bg-emerald-50 rounded px-2 py-0.5">{rule.action}</span>
                      </div>
                    )}
                    {rule.ontologyType && (
                      <div className="flex gap-4 items-center">
                        <span className={`${styles.cardTextMuted} w-16`}>{tl('关联本体', 'Ontology')}</span>
                        <button onClick={e => { e.stopPropagation(); navigateToOntology(rule.ontologyTypeId); }}
                          className="flex items-center gap-1 text-[10px] text-indigo-500 hover:underline cursor-pointer">
                          {rule.ontologyType} <ExternalLink size={10} />
                        </button>
                      </div>
                    )}
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
    </div>
  );
}
