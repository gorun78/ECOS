import React, { useState, useEffect, useCallback } from 'react';
import {
  Shield, Search, Plus, Edit3, Trash2, History, X, Save, Loader2,
  ChevronRight, Clock, User, Tag, AlertTriangle,
} from 'lucide-react';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import { knowledgeApi } from './services/knowledgeApi';
import type { RuleRepository, RuleVersion } from './typesAndConstants';
import { RULE_STATUS_OPTIONS } from './typesAndConstants';

const STATUS_COLORS: Record<string, string> = {
  DRAFT: 'bg-slate-100 text-slate-700 border-slate-300',
  IN_REVIEW: 'bg-amber-100 text-amber-700 border-amber-300',
  ACTIVE: 'bg-emerald-100 text-emerald-700 border-emerald-300',
  DEPRECATED: 'bg-rose-100 text-rose-700 border-rose-300',
};

const STATUS_LABELS_ZH: Record<string, string> = {
  DRAFT: '草稿',
  IN_REVIEW: '审核中',
  ACTIVE: '已激活',
  DEPRECATED: '已废弃',
};

export default function KnowledgeRuleRepositoryTab() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const [rules, setRules] = useState<RuleRepository[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('');

  // Drawer states
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerMode, setDrawerMode] = useState<'create' | 'edit'>('create');
  const [editingRule, setEditingRule] = useState<RuleRepository | null>(null);

  // Version history states
  const [versionDrawerOpen, setVersionDrawerOpen] = useState(false);
  const [versions, setVersions] = useState<RuleVersion[]>([]);
  const [versionRuleName, setVersionRuleName] = useState('');
  const [isLoadingVersions, setIsLoadingVersions] = useState(false);

  // Form state
  const [form, setForm] = useState({
    name: '',
    domain: '',
    status: 'DRAFT' as RuleRepository['status'],
    priority: 1,
    description: '',
    content: '',
  });

  const [toast, setToast] = useState<{ type: string; msg: string } | null>(null);
  const showToast = useCallback((type: string, msg: string) => {
    setToast({ type, msg });
    setTimeout(() => setToast(null), 3000);
  }, []);

  const loadRules = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await knowledgeApi.fetchRules({
        status: statusFilter || undefined,
        keyword: searchQuery || undefined,
      });
      setRules(Array.isArray(data) ? data : []);
    } catch {
      setRules([]);
    } finally {
      setIsLoading(false);
    }
  }, [statusFilter, searchQuery]);

  useEffect(() => {
    const timer = setTimeout(loadRules, 300);
    return () => clearTimeout(timer);
  }, [loadRules]);

  const resetForm = () => {
    setForm({ name: '', domain: '', status: 'DRAFT', priority: 1, description: '', content: '' });
  };

  const openCreateDrawer = () => {
    resetForm();
    setEditingRule(null);
    setDrawerMode('create');
    setDrawerOpen(true);
  };

  const openEditDrawer = (rule: RuleRepository) => {
    setEditingRule(rule);
    setForm({
      name: rule.name || '',
      domain: rule.domain || '',
      status: rule.status || 'DRAFT',
      priority: rule.priority ?? 1,
      description: rule.description || '',
      content: rule.content || '',
    });
    setDrawerMode('edit');
    setDrawerOpen(true);
  };

  const closeDrawer = () => {
    setDrawerOpen(false);
    setEditingRule(null);
  };

  const handleCreate = async () => {
    if (!form.name.trim()) {
      showToast('error', t("knowledge.knowledgerulerepository.规则名称不能为空"));
      return;
    }
    try {
      await knowledgeApi.createRule(form);
      showToast('success', t("knowledge.knowledgerulerepository.规则创建成功"));
      closeDrawer();
      loadRules();
    } catch {
      showToast('error', t("knowledge.glossarytab.创建失败"));
    }
  };

  const handleUpdate = async () => {
    if (!editingRule) return;
    if (!form.name.trim()) {
      showToast('error', t("knowledge.knowledgerulerepository.规则名称不能为空"));
      return;
    }
    try {
      await knowledgeApi.updateRule(editingRule.id, form);
      showToast('success', t("knowledge.knowledgerulerepository.规则更新成功"));
      closeDrawer();
      loadRules();
    } catch {
      showToast('error', t("knowledge.glossarytab.更新失败"));
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm(t("knowledge.knowledgerulerepository.确定删除此规则"))) return;
    try {
      await knowledgeApi.deleteRule(id);
      showToast('success', t("knowledge.glossarytab.已删除"));
      loadRules();
    } catch {
      showToast('error', t("knowledge.glossarytab.删除失败"));
    }
  };

  const openVersionHistory = async (rule: RuleRepository) => {
    setVersionRuleName(rule.name);
    setVersionDrawerOpen(true);
    setIsLoadingVersions(true);
    try {
      const data = await knowledgeApi.fetchRuleVersions(rule.id);
      setVersions(Array.isArray(data) ? data : []);
    } catch {
      setVersions([]);
    } finally {
      setIsLoadingVersions(false);
    }
  };

  const formatDate = (dateStr?: string) => {
    if (!dateStr) return '—';
    try {
      return new Date(dateStr).toLocaleString(t("knowledge.knowledgerulerepository.zh_cn"));
    } catch {
      return dateStr;
    }
  };

  const getStatusBadge = (status: string) => {
    const zhLabel = STATUS_LABELS_ZH[status] || status;
    const label = locale === 'zh' ? zhLabel : status.replace('_', ' ');
    return (
      <span className={`px-2 py-0.5 text-[10px] font-bold rounded border ${STATUS_COLORS[status] || 'bg-slate-100 text-slate-700 border-slate-300'}`}>
        {label}
      </span>
    );
  };

  const filteredRules = rules;

  return (
    <div className="space-y-4 h-full flex flex-col">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-slate-200 pb-3">
        <div className="space-y-1">
          <h2 className="text-sm font-black text-slate-800 flex items-center gap-2">
            <Shield size={16} className="text-indigo-600" />
            {t("knowledge.knowledgerulerepository.规则库_rule_repository")}
          </h2>
          <p className="text-xs text-slate-500">
            {t("knowledge.knowledgerulerepository.管理业务规则定义_支持版本追踪与状态生命周期管理")}
          </p>
        </div>
        <button
          onClick={openCreateDrawer}
          className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-lg flex items-center gap-1.5 cursor-pointer text-xs"
        >
          <Plus size={12} />
          {t("knowledge.knowledgerulerepository.新建规则")}
        </button>
      </div>

      {/* Filters */}
      <div className="flex gap-2 items-center">
        <div className="relative flex-1">
          <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            placeholder={t("knowledge.knowledgerulerepository.搜索规则名称")}
            className="w-full pl-8 pr-3 py-1.5 border border-slate-200 rounded-lg text-xs"
          />
        </div>
        <select
          value={statusFilter}
          onChange={e => setStatusFilter(e.target.value)}
          className="px-2.5 py-1.5 border border-slate-200 rounded-lg text-xs bg-white"
        >
          <option value="">{t("knowledge.glossarytab.全部状态")}</option>
          {RULE_STATUS_OPTIONS.map(s => (
            <option key={s} value={s}>{STATUS_LABELS_ZH[s] || s}</option>
          ))}
        </select>
      </div>

      {/* Rule Table */}
      <div className="flex-1 overflow-y-auto">
        {isLoading ? (
          <div className="py-12 text-center text-slate-400 flex items-center justify-center gap-2">
            <Loader2 size={14} className="animate-spin" />
            {t("knowledge.glossarytab.加载中")}
          </div>
        ) : filteredRules.length === 0 ? (
          <div className="py-12 text-center text-slate-400">
            <Shield size={28} className="mx-auto text-slate-300 mb-2" />
            <p className="text-xs">{t("knowledge.knowledgerulerepository.暂无规则数据")}</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b border-slate-200 text-left">
                  <th className="py-2 px-3 font-black text-slate-600">{t("knowledge.knowledgerulerepository.名称")}</th>
                  <th className="py-2 px-3 font-black text-slate-600">{t("knowledge.knowledgerulerepository.域")}</th>
                  <th className="py-2 px-3 font-black text-slate-600">{t("knowledge.knowledgerulerepository.状态")}</th>
                  <th className="py-2 px-3 font-black text-slate-600 text-center">{t("knowledge.knowledgerulerepository.优先级")}</th>
                  <th className="py-2 px-3 font-black text-slate-600 text-center">{t("knowledge.knowledgerulerepository.版本")}</th>
                  <th className="py-2 px-3 font-black text-slate-600">{t("knowledge.knowledgerulerepository.更新时间")}</th>
                  <th className="py-2 px-3 font-black text-slate-600 text-right">{t("knowledge.ontologytab.操作")}</th>
                </tr>
              </thead>
              <tbody>
                {filteredRules.map(rule => (
                  <tr
                    key={rule.id}
                    className="border-b border-slate-100 hover:bg-slate-50 transition-colors"
                  >
                    <td className="py-2 px-3">
                      <div className="font-bold text-slate-800">{rule.name}</div>
                      {rule.description && (
                        <div className="text-[10px] text-slate-400 truncate max-w-[200px]">{rule.description}</div>
                      )}
                    </td>
                    <td className="py-2 px-3">
                      <span className="px-1.5 py-0.5 bg-indigo-50 text-indigo-700 text-[10px] font-bold rounded">
                        {rule.domain || '—'}
                      </span>
                    </td>
                    <td className="py-2 px-3">{getStatusBadge(rule.status)}</td>
                    <td className="py-2 px-3 text-center">
                      <span className="font-mono font-bold text-slate-700">{rule.priority ?? '—'}</span>
                    </td>
                    <td className="py-2 px-3 text-center">
                      <span className="font-mono text-slate-600">v{rule.version ?? 1}</span>
                    </td>
                    <td className="py-2 px-3 text-slate-500">{formatDate(rule.updatedAt)}</td>
                    <td className="py-2 px-3">
                      <div className="flex gap-1 justify-end">
                        <button
                          onClick={() => openVersionHistory(rule)}
                          className="p-1.5 text-slate-400 hover:text-indigo-600 cursor-pointer rounded hover:bg-slate-100"
                          title={t("knowledge.knowledgerulerepository.版本历史")}
                        >
                          <History size={12} />
                        </button>
                        <button
                          onClick={() => openEditDrawer(rule)}
                          className="p-1.5 text-slate-400 hover:text-blue-600 cursor-pointer rounded hover:bg-slate-100"
                          title={t("knowledge.knowledgerulerepository.编辑")}
                        >
                          <Edit3 size={12} />
                        </button>
                        <button
                          onClick={() => handleDelete(rule.id)}
                          className="p-1.5 text-slate-400 hover:text-rose-600 cursor-pointer rounded hover:bg-slate-100"
                          title={t("knowledge.ontologytab.删除")}
                        >
                          <Trash2 size={12} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* ── Create/Edit Drawer ── */}
      {drawerOpen && (
        <div className="fixed inset-0 z-50 flex justify-end">
          <div className="absolute inset-0 bg-black/30" onClick={closeDrawer} />
          <div className={`relative w-full max-w-lg ${styles.cardBg} shadow-2xl h-full overflow-y-auto`}>
            <div className="sticky top-0 z-10 flex items-center justify-between p-4 border-b border-slate-200 bg-inherit">
              <h3 className="text-sm font-black text-slate-800">
                {drawerMode === 'create'
                  ? (t("knowledge.knowledgerulerepository.新建规则"))
                  : (t("knowledge.knowledgerulerepository.编辑规则"))}
              </h3>
              <button onClick={closeDrawer} className="p-1 text-slate-400 hover:text-slate-600 cursor-pointer">
                <X size={18} />
              </button>
            </div>

            <div className="p-4 space-y-4">
              <div>
                <label className="block text-[11px] font-bold text-slate-600 mb-1">
                  {t("knowledge.knowledgerulerepository.规则名称")} <span className="text-rose-500">*</span>
                </label>
                <input
                  value={form.name}
                  onChange={e => setForm(prev => ({ ...prev, name: e.target.value }))}
                  placeholder={t("knowledge.knowledgerulerepository.请输入规则名称")}
                  className="w-full px-3 py-2 border border-slate-200 rounded-lg text-xs focus:border-indigo-400 focus:ring-1 focus:ring-indigo-100 outline-none"
                />
              </div>

              <div>
                <label className="block text-[11px] font-bold text-slate-600 mb-1">
                  {t("knowledge.knowledgerulerepository.所属域")}
                </label>
                <input
                  value={form.domain}
                  onChange={e => setForm(prev => ({ ...prev, domain: e.target.value }))}
                  placeholder={t("knowledge.knowledgerulerepository.如_security_business_data")}
                  className="w-full px-3 py-2 border border-slate-200 rounded-lg text-xs focus:border-indigo-400 focus:ring-1 focus:ring-indigo-100 outline-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-[11px] font-bold text-slate-600 mb-1">
                    {t("knowledge.knowledgerulerepository.状态")}
                  </label>
                  <select
                    value={form.status}
                    onChange={e => setForm(prev => ({ ...prev, status: e.target.value as RuleRepository['status'] }))}
                    className="w-full px-3 py-2 border border-slate-200 rounded-lg text-xs bg-white"
                  >
                    {RULE_STATUS_OPTIONS.map(s => (
                      <option key={s} value={s}>{STATUS_LABELS_ZH[s] || s}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-[11px] font-bold text-slate-600 mb-1">
                    {t("knowledge.knowledgerulerepository.优先级")}
                  </label>
                  <input
                    type="number"
                    min={1}
                    max={100}
                    value={form.priority}
                    onChange={e => setForm(prev => ({ ...prev, priority: parseInt(e.target.value) || 1 }))}
                    className="w-full px-3 py-2 border border-slate-200 rounded-lg text-xs focus:border-indigo-400 focus:ring-1 focus:ring-indigo-100 outline-none"
                  />
                </div>
              </div>

              <div>
                <label className="block text-[11px] font-bold text-slate-600 mb-1">
                  {t("knowledge.knowledgerulerepository.描述")}
                </label>
                <textarea
                  value={form.description}
                  onChange={e => setForm(prev => ({ ...prev, description: e.target.value }))}
                  placeholder={t("knowledge.knowledgerulerepository.规则描述说明")}
                  rows={3}
                  className="w-full px-3 py-2 border border-slate-200 rounded-lg text-xs focus:border-indigo-400 focus:ring-1 focus:ring-indigo-100 outline-none resize-none"
                />
              </div>

              <div>
                <label className="block text-[11px] font-bold text-slate-600 mb-1">
                  {t("knowledge.knowledgerulerepository.规则内容")}
                </label>
                <textarea
                  value={form.content}
                  onChange={e => setForm(prev => ({ ...prev, content: e.target.value }))}
                  placeholder={t("knowledge.knowledgerulerepository.规则定义内容_如_drl_dsl_表达式")}
                  rows={5}
                  className="w-full px-3 py-2 border border-slate-200 rounded-lg text-xs font-mono focus:border-indigo-400 focus:ring-1 focus:ring-indigo-100 outline-none resize-none"
                />
              </div>
            </div>

            <div className="sticky bottom-0 p-4 border-t border-slate-200 flex gap-2 justify-end bg-inherit">
              <button
                onClick={closeDrawer}
                className="px-4 py-2 bg-slate-200 hover:bg-slate-300 text-slate-700 font-bold rounded-lg text-xs cursor-pointer"
              >
                {t("knowledge.glossarytab.取消")}
              </button>
              <button
                onClick={drawerMode === 'create' ? handleCreate : handleUpdate}
                className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-lg text-xs cursor-pointer flex items-center gap-1.5"
              >
                <Save size={11} />
                {drawerMode === 'create'
                  ? (t("knowledge.glossarytab.创建"))
                  : (t("knowledge.knowledgerulerepository.保存"))}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Version History Drawer ── */}
      {versionDrawerOpen && (
        <div className="fixed inset-0 z-50 flex justify-end">
          <div className="absolute inset-0 bg-black/30" onClick={() => setVersionDrawerOpen(false)} />
          <div className={`relative w-full max-w-md ${styles.cardBg} shadow-2xl h-full overflow-y-auto`}>
            <div className="sticky top-0 z-10 flex items-center justify-between p-4 border-b border-slate-200 bg-inherit">
              <div>
                <h3 className="text-sm font-black text-slate-800">
                  {t("knowledge.knowledgerulerepository.版本历史")}
                </h3>
                <p className="text-[10px] text-slate-500 mt-0.5 font-bold">{versionRuleName}</p>
              </div>
              <button
                onClick={() => setVersionDrawerOpen(false)}
                className="p-1 text-slate-400 hover:text-slate-600 cursor-pointer"
              >
                <X size={18} />
              </button>
            </div>

            <div className="p-4">
              {isLoadingVersions ? (
                <div className="py-8 text-center text-slate-400 flex items-center justify-center gap-2">
                  <Loader2 size={14} className="animate-spin" />
                  {t("knowledge.glossarytab.加载中")}
                </div>
              ) : versions.length === 0 ? (
                <div className="py-8 text-center text-slate-400">
                  <History size={24} className="mx-auto text-slate-300 mb-2" />
                  <p className="text-xs">{t("knowledge.knowledgerulerepository.暂无版本记录")}</p>
                </div>
              ) : (
                <div className="relative">
                  {/* Timeline */}
                  <div className="absolute left-[15px] top-2 bottom-2 w-0.5 bg-slate-200" />
                  <div className="space-y-4">
                    {versions.map((v, idx) => (
                      <div key={v.id || idx} className="flex gap-3 relative">
                        {/* Node */}
                        <div className={`shrink-0 w-8 h-8 rounded-full flex items-center justify-center z-10 ${
                          idx === 0 ? 'bg-indigo-100 text-indigo-600' : 'bg-slate-100 text-slate-500'
                        }`}>
                          {idx === 0 ? (
                            <Tag size={14} />
                          ) : (
                            <div className="text-[10px] font-black">v{v.version}</div>
                          )}
                        </div>

                        {/* Content */}
                        <div className="flex-1 pb-2">
                          <div className="bg-white border border-slate-200 rounded-lg p-3 shadow-xs">
                            <div className="flex items-center justify-between mb-1">
                              <span className="font-black text-xs text-slate-800">
                                {t("knowledge.knowledgerulerepository.版本")} {v.version}
                              </span>
                              {idx === 0 && (
                                <span className="px-1.5 py-0.5 bg-indigo-50 text-indigo-700 text-[9px] font-bold rounded">
                                  {t("knowledge.knowledgerulerepository.当前")}
                                </span>
                              )}
                            </div>
                            <p className="text-[11px] text-slate-600 leading-relaxed mb-2">
                              {v.changeLog || (t("knowledge.knowledgerulerepository.无变更说明"))}
                            </p>
                            <div className="flex items-center gap-4 text-[10px] text-slate-400">
                              <span className="flex items-center gap-1">
                                <Clock size={10} />
                                {formatDate(v.createdAt)}
                              </span>
                              {v.createdBy && (
                                <span className="flex items-center gap-1">
                                  <User size={10} />
                                  {v.createdBy}
                                </span>
                              )}
                            </div>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>

            <div className="sticky bottom-0 p-4 border-t border-slate-200 flex justify-end bg-inherit">
              <button
                onClick={() => setVersionDrawerOpen(false)}
                className="px-4 py-2 bg-slate-200 hover:bg-slate-300 text-slate-700 font-bold rounded-lg text-xs cursor-pointer"
              >
                {t("knowledge.ontologytab.关闭")}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Toast */}
      {toast && (
        <div className={`fixed bottom-6 right-6 z-[60] px-4 py-2.5 rounded-lg shadow-lg text-sm font-medium ${
          toast.type === 'success' ? 'bg-emerald-600 text-white' : 'bg-rose-600 text-white'
        }`}>
          {toast.msg}
        </div>
      )}
    </div>
  );
}
