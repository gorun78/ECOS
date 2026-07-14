import React, { useState, useEffect, useCallback } from 'react';
import { BookOpen, Search, Plus, Edit3, Trash2, Save, X, Check, Loader2 } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { knowledgeApi } from '../services/knowledgeApi';
import type { GlossaryTerm, GlossaryFilter, Domain } from '../typesAndConstants';

export default function GlossaryTab() {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const [terms, setTerms] = useState<GlossaryTerm[]>([]);
  const [filter, setFilter] = useState<GlossaryFilter>({ domain: '', status: '' });
  const [isLoading, setIsLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [editingTerm, setEditingTerm] = useState<GlossaryTerm | null>(null);
  const [isCreating, setIsCreating] = useState(false);
  const [newTerm, setNewTerm] = useState({ name: '', definition: '', domain: '' });
  const [toast, setToast] = useState<{ type: string; msg: string } | null>(null);

  const showToast = useCallback((type: string, msg: string) => {
    setToast({ type, msg });
    setTimeout(() => setToast(null), 3000);
  }, []);

  const loadTerms = async () => {
    setIsLoading(true);
    try {
      const data = await knowledgeApi.fetchGlossaryTerms(filter.domain ? { domain: filter.domain } : undefined);
      setTerms(Array.isArray(data) ? data : []);
    } catch {
      setTerms([]);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => { loadTerms(); }, [filter]);

  const handleCreate = async () => {
    if (!newTerm.name.trim()) { showToast('error', locale === 'zh' ? '术语名称不能为空' : 'Name required'); return; }
    try {
      await knowledgeApi.createGlossaryTerm(newTerm);
      showToast('success', locale === 'zh' ? '术语创建成功' : 'Term created');
      setIsCreating(false);
      setNewTerm({ name: '', definition: '', domain: '' });
      loadTerms();
    } catch {
      showToast('error', locale === 'zh' ? '创建失败' : 'Create failed');
    }
  };

  const handleUpdate = async (id: string, data: Record<string, unknown>) => {
    try {
      await knowledgeApi.updateGlossaryTerm(id, data);
      showToast('success', locale === 'zh' ? '术语更新成功' : 'Term updated');
      setEditingTerm(null);
      loadTerms();
    } catch {
      showToast('error', locale === 'zh' ? '更新失败' : 'Update failed');
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm(locale === 'zh' ? '确定删除此术语？' : 'Delete this term?')) return;
    try {
      await knowledgeApi.deleteGlossaryTerm(id);
      showToast('success', locale === 'zh' ? '已删除' : 'Deleted');
      loadTerms();
    } catch {
      showToast('error', locale === 'zh' ? '删除失败' : 'Delete failed');
    }
  };

  const filteredTerms = terms.filter(t =>
    !searchQuery || t.name?.toLowerCase().includes(searchQuery.toLowerCase()) ||
    t.definition?.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div className="space-y-4 h-full flex flex-col">
      <div className="flex items-center justify-between border-b border-slate-200 pb-3">
        <div className="space-y-1">
          <h2 className="text-sm font-black text-slate-800 flex items-center gap-2">
            <BookOpen size={16} className="text-blue-600" />
            {locale === 'zh' ? '术语库 (Glossary Manager)' : 'Glossary Manager'}
          </h2>
          <p className="text-xs text-slate-500">
            {locale === 'zh' ? '管理业务术语定义，构建智能体可理解的语义词汇表' : 'Manage business term definitions for agent-consumable semantic vocabulary'}
          </p>
        </div>
        <button
          onClick={() => setIsCreating(true)}
          className="px-3 py-1.5 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-lg flex items-center gap-1.5 cursor-pointer text-xs"
        >
          <Plus size={12} />
          {locale === 'zh' ? '新建术语' : 'New Term'}
        </button>
      </div>

      <div className="flex gap-2 items-center">
        <div className="relative flex-1">
          <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            placeholder={locale === 'zh' ? '搜索术语...' : 'Search terms...'}
            className="w-full pl-8 pr-3 py-1.5 border border-slate-200 rounded-lg text-xs"
          />
        </div>
        <select
          value={filter.domain}
          onChange={e => setFilter(prev => ({ ...prev, domain: e.target.value }))}
          className="px-2.5 py-1.5 border border-slate-200 rounded-lg text-xs bg-white"
        >
          <option value="">{locale === 'zh' ? '全部领域' : 'All Domains'}</option>
          <option value="data">{locale === 'zh' ? '数据域' : 'Data'}</option>
          <option value="business">{locale === 'zh' ? '业务域' : 'Business'}</option>
          <option value="technology">{locale === 'zh' ? '技术域' : 'Technology'}</option>
        </select>
        <select
          value={filter.status}
          onChange={e => setFilter(prev => ({ ...prev, status: e.target.value }))}
          className="px-2.5 py-1.5 border border-slate-200 rounded-lg text-xs bg-white"
        >
          <option value="">{locale === 'zh' ? '全部状态' : 'All Status'}</option>
          <option value="draft">{locale === 'zh' ? '草稿' : 'Draft'}</option>
          <option value="published">{locale === 'zh' ? '已发布' : 'Published'}</option>
        </select>
      </div>

      {isCreating && (
        <div className="bg-blue-50 border border-blue-200 rounded-xl p-4 space-y-3">
          <div className="flex items-center justify-between">
            <span className="font-bold text-xs text-blue-800">{locale === 'zh' ? '新建术语' : 'New Term'}</span>
            <button onClick={() => setIsCreating(false)} className="text-slate-400 hover:text-slate-600 cursor-pointer"><X size={14} /></button>
          </div>
          <input
            value={newTerm.name}
            onChange={e => setNewTerm(prev => ({ ...prev, name: e.target.value }))}
            placeholder={locale === 'zh' ? '术语名称' : 'Term name'}
            className="w-full px-3 py-1.5 border border-blue-200 rounded-lg text-xs"
          />
          <textarea
            value={newTerm.definition}
            onChange={e => setNewTerm(prev => ({ ...prev, definition: e.target.value }))}
            placeholder={locale === 'zh' ? '定义说明' : 'Definition'}
            rows={3}
            className="w-full px-3 py-1.5 border border-blue-200 rounded-lg text-xs"
          />
          <input
            value={newTerm.domain}
            onChange={e => setNewTerm(prev => ({ ...prev, domain: e.target.value }))}
            placeholder={locale === 'zh' ? '领域 (如: data, business, technology)' : 'Domain'}
            className="w-full px-3 py-1.5 border border-blue-200 rounded-lg text-xs"
          />
          <div className="flex gap-2 justify-end">
            <button onClick={() => setIsCreating(false)} className="px-3 py-1.5 bg-slate-200 hover:bg-slate-300 text-slate-700 font-bold rounded-lg text-xs cursor-pointer">{locale === 'zh' ? '取消' : 'Cancel'}</button>
            <button onClick={handleCreate} className="px-3 py-1.5 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-lg text-xs cursor-pointer flex items-center gap-1"><Save size={11} />{locale === 'zh' ? '创建' : 'Create'}</button>
          </div>
        </div>
      )}

      <div className="flex-1 overflow-y-auto space-y-2">
        {isLoading ? (
          <div className="py-8 text-center text-slate-400 flex items-center justify-center gap-2"><Loader2 size={14} className="animate-spin" />{locale === 'zh' ? '加载中...' : 'Loading...'}</div>
        ) : filteredTerms.length === 0 ? (
          <div className="py-8 text-center text-slate-400">
            <BookOpen size={24} className="mx-auto text-slate-300 mb-2" />
            <p className="text-xs">{locale === 'zh' ? '暂无术语数据' : 'No terms found'}</p>
          </div>
        ) : (
          filteredTerms.map(term => (
            <div key={term.id || term.name} className="bg-white border border-slate-200 rounded-xl p-3 shadow-xs hover:border-slate-300 transition-all">
              {editingTerm?.id === term.id || editingTerm?.name === term.name ? (
                <div className="space-y-2">
                  <input
                    defaultValue={term.name}
                    onChange={e => setEditingTerm(prev => prev ? { ...prev, name: e.target.value } : prev)}
                    className="w-full px-2 py-1 border border-slate-200 rounded-md text-xs font-bold"
                  />
                  <textarea
                    defaultValue={term.definition}
                    onChange={e => setEditingTerm(prev => prev ? { ...prev, definition: e.target.value } : prev)}
                    rows={2}
                    className="w-full px-2 py-1 border border-slate-200 rounded-md text-xs"
                  />
                  <div className="flex gap-1.5 justify-end">
                    <button onClick={() => setEditingTerm(null)} className="p-1 text-slate-400 hover:text-slate-600 cursor-pointer"><X size={12} /></button>
                    <button onClick={() => handleUpdate(term.id || term.name, editingTerm)} className="p-1 text-emerald-600 hover:text-emerald-700 cursor-pointer"><Check size={12} /></button>
                  </div>
                </div>
              ) : (
                <div className="flex items-start justify-between gap-3">
                  <div className="flex-1 space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-xs text-slate-800">{term.name}</span>
                      {term.domain && <span className="px-1.5 py-0.5 bg-blue-50 text-blue-700 text-[8px] font-bold rounded">{term.domain}</span>}
                      {term.status && <span className={`px-1.5 py-0.5 text-[8px] font-bold rounded ${term.status === 'published' ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'}`}>{term.status}</span>}
                    </div>
                    <p className="text-[11px] text-slate-500 leading-relaxed font-sans">{term.definition}</p>
                  </div>
                  <div className="flex gap-1 shrink-0">
                    <button onClick={() => setEditingTerm({ ...term })} className="p-1 text-slate-400 hover:text-blue-600 cursor-pointer"><Edit3 size={11} /></button>
                    <button onClick={() => handleDelete(term.id || term.name)} className="p-1 text-slate-400 hover:text-rose-600 cursor-pointer"><Trash2 size={11} /></button>
                  </div>
                </div>
              )}
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
