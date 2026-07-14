import React, { useState, useEffect } from 'react';
import {
  fetchDqRules, fetchDqIssues, fetchDqDashboard, fetchDqAll,
  createDqItem, updateDqItem, deleteDqItem,
  runDqCheck, resolveDqIssue,
} from "../api";
import { Shield, AlertTriangle, CheckCircle2, Plus, Play, RefreshCw, X, ExternalLink } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import { useDict } from "../hooks/useDict";
import { commit as gitCommit } from '../services/gitService';

const SEV_COLORS: Record<string, string> = {
  CRITICAL: "bg-red-100 text-red-700 border-red-200",
  HIGH: "bg-orange-100 text-orange-700 border-orange-200",
  MEDIUM: "bg-yellow-100 text-yellow-700 border-yellow-200",
  LOW: "bg-green-100 text-green-700 border-green-200",
};

const RULE_TYPES = ['COMPLETENESS','ACCURACY','CONSISTENCY','UNIQUENESS','TIMELINESS','VALIDITY'];
const SEVERITIES = ['CRITICAL','HIGH','MEDIUM','LOW'];

export default function DataQualityDashboard() {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const { getLabel: getStatusLabel, getColor: getStatusColor } = useDict("dq_issue_status", locale);
  const { getLabel: getRuleTypeLabel } = useDict("dq_rule_type", locale);
  const navigate = useNavigate();
  const [tab, setTab] = useState(0);
  const [rules, setRules] = useState<any[]>([]);
  const [issues, setIssues] = useState<any[]>([]);
  const [dashboard, setDashboard] = useState<any>(null);
  const [dlgOpen, setDlgOpen] = useState(false);
  const [dlgType, setDlgType] = useState('');
  const [form, setForm] = useState<Record<string, any>>({});
  const [resolveOpen, setResolveOpen] = useState(false);
  const [resolveIssue, setResolveIssue] = useState<any>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const tl = (zh: string, en: string) => locale === "zh" ? zh : en;

  async function fetchAll() {
    setLoading(true); setError('');
    try {
      const [r, i, d] = await fetchDqAll();
      if (r) setRules(r.data || r || []);
      if (i) setIssues(i.data || i || []);
      if (d) setDashboard(d.data || d);
    } catch (e: any) { setError(e.message || String(e)); }
    setLoading(false);
  }

  useEffect(() => { fetchAll(); }, []);

  async function handleDelete(type: string, id: string) {
    try { await deleteDqItem(type, id); } catch (e: any) { setError(e.message); }
    gitCommit('data-workbench', {
      message: `dq-rule: ${id} deleted`,
      authorName: 'system',
      authorEmail: 'system@ecos.local'
    }).catch(() => {});
    fetchAll();
  }

  function openDlg(type: string, item?: any) {
    setDlgType(type);
    setForm(item || { ruleType: 'COMPLETENESS', severity: 'MEDIUM', status: 'ACTIVE' });
    setDlgOpen(true);
  }

  async function saveDlg() {
    try {
      if (form.id) await updateDqItem(dlgType, form.id, form);
      else await createDqItem(dlgType, form);
    } catch (e: any) { setError(e.message); return; }
    gitCommit('data-workbench', {
      message: `dq-rule: ${form.name || form.id || 'unknown'} ${form.id ? 'updated' : 'created'}`,
      authorName: 'system',
      authorEmail: 'system@ecos.local'
    }).catch(() => {});
    setDlgOpen(false); fetchAll();
  }

  async function runCheck() {
    setLoading(true);
    try {
      const res = await runDqCheck();
      const data = res?.data || res;
      if (data) setError(tl(`检查完成: 扫描了 ${data.checked || '?'} 条规则`, `Check complete: ${data.checked || '?'} rules scanned`));
    } catch (e: any) { setError(e.message); }
    gitCommit('data-workbench', {
      message: `dq-rule: check executed`,
      authorName: 'system',
      authorEmail: 'system@ecos.local'
    }).catch(() => {});
    fetchAll();
  }

  async function resolveIssueAction() {
    if (!resolveIssue) return;
    try { await resolveDqIssue(resolveIssue.id, { status: 'RESOLVED', resolutionNote: resolveIssue.note || '' }); }
    catch (e: any) { setError(e.message); }
    gitCommit('data-workbench', {
      message: `dq-issue: ${resolveIssue.id} resolved`,
      authorName: 'system',
      authorEmail: 'system@ecos.local'
    }).catch(() => {});
    setResolveOpen(false); fetchAll();
  }

  const chip = (severity: string) => (
    <span className={`inline-block px-2 py-0.5 rounded-full text-[10px] font-semibold border ${SEV_COLORS[severity] || "bg-slate-100 text-slate-600 border-slate-200"}`}>
      {severity}
    </span>
  );

  return (
    <div className={`flex-1 ${styles.appBg} flex flex-col h-full font-sans overflow-auto`}>
      <div className="p-5 sm:p-6 max-w-7xl mx-auto w-full space-y-5">

        {/* Header */}
        <div>
          <h1 className={`text-xl font-bold ${styles.cardText} flex items-center gap-2`}>
            <Shield className="w-5 h-5 text-indigo-600" />
            {tl("数据质量管理", "Data Quality Dashboard")}
          </h1>
        </div>

        {/* Stats */}
        {dashboard && (
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
            <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 text-center`}>
              <div className="text-2xl font-bold text-indigo-600">{dashboard.activeRules}/{dashboard.totalRules}</div>
              <div className="text-[11px] text-slate-500 mt-1">{tl("规则 (生效/总数)", "Rules (Active/Total)")}</div>
            </div>
            <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 text-center`}>
              <div className={`text-2xl font-bold ${dashboard.openIssues > 0 ? 'text-red-500' : 'text-green-500'}`}>
                {dashboard.openIssues}
              </div>
              <div className="text-[11px] text-slate-500 mt-1">{tl("待处理问题", "Open Issues")}</div>
            </div>
            <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 text-center`}>
              <div className="text-2xl font-bold text-amber-500">
                {(dashboard.totalIssues || 0) - (dashboard.openIssues || 0)}
              </div>
              <div className="text-[11px] text-slate-500 mt-1">{tl("已解决", "Resolved")}</div>
            </div>
            <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 flex flex-col items-center justify-center gap-1.5`}>
              <div className="flex flex-wrap justify-center gap-1">
                {Object.entries(dashboard.bySeverity || {}).map(([k, v]: [string, any]) => (
                  <span key={k} className={`inline-block px-2 py-0.5 rounded-full text-[10px] font-semibold border ${SEV_COLORS[k] || "bg-slate-100 text-slate-600 border-slate-200"}`}>
                    {k}: {v}
                  </span>
                ))}
              </div>
              <div className="text-[11px] text-slate-500">{tl("按严重程度", "By Severity")}</div>
            </div>
          </div>
        )}

        {error && (
          <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 flex items-start gap-2 text-amber-800 text-xs">
            <AlertTriangle className="w-3.5 h-3.5 shrink-0 mt-0.5" />
            <span>{error}</span>
            <button onClick={() => setError('')} className="ml-auto"><X className="w-3 h-3" /></button>
          </div>
        )}

        {/* Tabs */}
        <div className={`flex items-center gap-1 border-b-2 ${styles.cardBorder} pb-0`}>
          {[
            tl("规则管理", "Rules"),
            tl("问题列表", "Issues"),
            tl("仪表盘", "Dashboard")
          ].map((label, i) => (
            <button key={i}
              onClick={() => setTab(i)}
              className={`px-4 py-2 text-xs font-semibold rounded-t-lg transition border-b-2 -mb-[2px] ${
                tab === i
                  ? `${styles.cardBg} border-indigo-500 text-indigo-700`
                  : "text-slate-500 hover:text-slate-700 border-transparent"
              }`}>
              {label}
            </button>
          ))}
          <button onClick={fetchAll} disabled={loading}
            className="ml-auto px-3 py-1.5 text-xs font-semibold text-slate-500 hover:text-slate-700 hover:bg-slate-100 rounded-lg flex items-center gap-1">
            <RefreshCw className={`w-3 h-3 ${loading ? "animate-spin" : ""}`} />
            {tl("刷新", "Refresh")}
          </button>
        </div>

        {/* RULES TAB */}
        {tab === 0 && (
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <button onClick={() => openDlg('rules', { ruleType: 'COMPLETENESS', severity: 'MEDIUM', status: 'ACTIVE' })}
                className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-xs font-semibold flex items-center gap-1.5">
                <Plus className="w-3 h-3" />
                {tl("新建规则", "New Rule")}
              </button>
              <button onClick={runCheck}
                className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-xs font-semibold flex items-center gap-1.5">
                <Play className="w-3 h-3" />
                {tl("执行检查", "Run Check")}
              </button>
            </div>
            <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl overflow-hidden`}>
              <table className="w-full text-xs">
                <thead>
                  <tr className={`bg-slate-50 border-b ${styles.cardBorder} text-left`}>
                    <th className="px-4 py-2.5 text-slate-500 font-semibold">{tl("规则", "Rule")}</th>
                    <th className="px-4 py-2.5 text-slate-500 font-semibold">{tl("类型", "Type")}</th>
                    <th className="px-4 py-2.5 text-slate-500 font-semibold">{tl("目标", "Target")}</th>
                    <th className="px-4 py-2.5 text-slate-500 font-semibold">{tl("严重度", "Severity")}</th>
                    <th className="px-4 py-2.5 text-slate-500 font-semibold">{tl("状态", "Status")}</th>
                    <th className="px-4 py-2.5 text-slate-500 font-semibold">{tl("操作", "Actions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {rules.map((r: any) => (
                    <tr key={r.id} className="border-b border-slate-100 hover:bg-slate-50">
                      <td className="px-4 py-2.5">
                        <div className="font-semibold text-slate-700">{r.name}</div>
                        <div className="text-[10px] text-slate-400">{r.description}</div>
                      </td>
                      <td className="px-4 py-2.5 text-slate-600">{getRuleTypeLabel(r.ruleType || r.rule_type)}</td>
                      <td className="px-4 py-2.5 font-mono text-[10px] text-slate-500">
                        <button
                          onClick={() => {
                            const target = r.targetEntity || r.target_entity;
                            if (target) navigate(`/dataset_explorer/${target}`);
                          }}
                          className="text-indigo-500 hover:text-indigo-700 hover:underline flex items-center gap-0.5"
                        >
                          {r.targetEntity || r.target_entity}{r.targetField || r.target_field ? '.' + (r.targetField || r.target_field) : ''}
                          <ExternalLink className="w-2.5 h-2.5 opacity-50" />
                        </button>
                      </td>
                      <td className="px-4 py-2.5">{chip(r.severity)}</td>
                      <td className="px-4 py-2.5">
                        <span className="inline-block px-2 py-0.5 rounded-full text-[10px] font-semibold border" style={{ borderColor: getStatusColor(r.status), color: getStatusColor(r.status) }}>
                          {getStatusLabel(r.status)}
                        </span>
                      </td>
                      <td className="px-4 py-2.5">
                        <div className="flex gap-1.5">
                          <button onClick={() => openDlg('rules', r)}
                            className="px-2 py-1 text-[10px] bg-slate-100 hover:bg-slate-200 text-slate-600 rounded">
                            {tl("编辑", "Edit")}
                          </button>
                          <button onClick={() => handleDelete('rules', r.id)}
                            className="px-2 py-1 text-[10px] bg-red-50 hover:bg-red-100 text-red-600 rounded">
                            {tl("删除", "Del")}
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                  {rules.length === 0 && (
                    <tr><td colSpan={6} className="px-4 py-8 text-center text-slate-400 text-xs">
                      {tl("暂无规则", "No rules yet")}
                    </td></tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* ISSUES TAB */}
        {tab === 1 && (
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl overflow-hidden`}>
            <table className="w-full text-xs">
              <thead>
                <tr className={`bg-slate-50 border-b ${styles.cardBorder} text-left`}>
                  <th className="px-4 py-2.5 text-slate-500 font-semibold">{tl("问题", "Issue")}</th>
                  <th className="px-4 py-2.5 text-slate-500 font-semibold">{tl("实体", "Entity")}</th>
                  <th className="px-4 py-2.5 text-slate-500 font-semibold">{tl("字段", "Field")}</th>
                  <th className="px-4 py-2.5 text-slate-500 font-semibold">{tl("严重度", "Severity")}</th>
                  <th className="px-4 py-2.5 text-slate-500 font-semibold">{tl("状态", "Status")}</th>
                  <th className="px-4 py-2.5 text-slate-500 font-semibold">{tl("时间", "Time")}</th>
                  <th className="px-4 py-2.5 text-slate-500 font-semibold">{tl("操作", "Action")}</th>
                </tr>
              </thead>
              <tbody>
                {issues.map((i: any) => (
                  <tr key={i.id} className={`border-b border-slate-100 hover:bg-slate-50 ${i.status === 'OPEN' ? 'bg-amber-50/50' : ''}`}>
                    <td className="px-4 py-2.5">
                      <div className="font-semibold text-slate-700">{i.issueType || i.issue_type}</div>
                      <div className="text-[10px] text-slate-400">{i.description}</div>
                    </td>
                    <td className="px-4 py-2.5 font-mono text-[10px] text-slate-500">{i.entityType || i.entity_type}/{i.entityId || i.entity_id}</td>
                    <td className="px-4 py-2.5 text-slate-600">{i.fieldName || i.field_name || '—'}</td>
                    <td className="px-4 py-2.5">{chip(i.severity)}</td>
                    <td className="px-4 py-2.5">
                      <span className="inline-block px-2 py-0.5 rounded-full text-[10px] font-semibold border" style={{ borderColor: getStatusColor(i.status), color: getStatusColor(i.status) }}>
                        {getStatusLabel(i.status)}
                      </span>
                    </td>
                    <td className="px-4 py-2.5 text-[10px] text-slate-400">{(i.detectedAt || i.detected_at || '').substring(0, 16)}</td>
                    <td className="px-4 py-2.5">
                      {i.status === 'OPEN' && (
                        <button onClick={() => { setResolveIssue({ id: i.id, note: '' }); setResolveOpen(true); }}
                          className="px-2 py-1 text-[10px] bg-emerald-100 hover:bg-emerald-200 text-emerald-700 rounded font-semibold">
                          {tl("解决", "Resolve")}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
                {issues.length === 0 && (
                  <tr><td colSpan={7} className="px-4 py-8 text-center text-slate-400 text-xs">
                    {tl("暂无问题", "No issues")}
                  </td></tr>
                )}
              </tbody>
            </table>
          </div>
        )}

        {/* DASHBOARD TAB */}
        {tab === 2 && dashboard && (
          <div className="space-y-4">
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 text-center`}>
                <div className="text-2xl font-bold text-red-500">
                  {(dashboard.bySeverity?.HIGH || 0) + (dashboard.bySeverity?.CRITICAL || 0)}
                </div>
                <div className="text-[11px] text-slate-500 mt-1">{tl("高危问题", "High-Risk Issues")}</div>
              </div>
              <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4`}>
                <div className="text-xs font-semibold text-slate-500 mb-2">{tl("最近问题", "Recent Issues")}</div>
                <div className="space-y-1.5">
                  {dashboard.recentIssues?.map((i: any) => (
                    <div key={i.id} className="flex items-center gap-2 text-[11px] py-1">
                      {chip(i.severity)}
                      <span className="flex-1 text-slate-600 truncate">{i.description}</span>
                      <span className="inline-block px-2 py-0.5 rounded-full text-[10px] font-semibold border" style={{ borderColor: getStatusColor(i.status), color: getStatusColor(i.status) }}>
                        {getStatusLabel(i.status)}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
            {dashboard.byType && Object.keys(dashboard.byType).length > 0 && (
              <div>
                <div className="text-xs font-semibold text-slate-500 mb-2">{tl("问题类型分布", "Issue Type Distribution")}</div>
                <div className="flex flex-wrap gap-3">
                  {Object.entries(dashboard.byType).map(([k, v]: [string, any]) => (
                    <div key={k} className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl px-5 py-3 text-center`}>
                      <div className="text-lg font-bold text-slate-700">{v}</div>
                      <div className="text-[10px] text-slate-400">{k}</div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        {/* RULE DIALOG */}
        {dlgOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm" onClick={() => setDlgOpen(false)}>
            <div className={`${styles.cardBg} rounded-xl shadow-2xl w-full max-w-lg mx-4 p-6 border ${styles.cardBorder}`} onClick={e => e.stopPropagation()}>
              <div className="flex items-center justify-between mb-4">
                <h2 className={`text-lg font-bold ${styles.cardText}`}>
                  {form.id ? tl("编辑规则", "Edit Rule") : tl("新建规则", "New Rule")}
                </h2>
                <button onClick={() => setDlgOpen(false)} className="p-1 hover:bg-slate-100 rounded">
                  <X className="w-5 h-5 text-slate-400" />
                </button>
              </div>
              <div className="space-y-3">
                <input className={`w-full border ${styles.inputBorder} ${styles.inputBg} rounded-lg px-3 py-2 text-xs outline-none focus:border-indigo-400`}
                  placeholder={tl("编码", "Code")} value={form.code || ''}
                  onChange={e => setForm({...form, code: e.target.value})} />
                <input className={`w-full border ${styles.inputBorder} ${styles.inputBg} rounded-lg px-3 py-2 text-xs outline-none focus:border-indigo-400`}
                  placeholder={tl("名称", "Name")} value={form.name || ''}
                  onChange={e => setForm({...form, name: e.target.value})} />
                <input className={`w-full border ${styles.inputBorder} ${styles.inputBg} rounded-lg px-3 py-2 text-xs outline-none focus:border-indigo-400`}
                  placeholder={tl("描述", "Description")} value={form.description || ''}
                  onChange={e => setForm({...form, description: e.target.value})} />
                <select className={`w-full border ${styles.inputBorder} ${styles.inputBg} rounded-lg px-3 py-2 text-xs outline-none focus:border-indigo-400`}
                  value={form.ruleType || 'COMPLETENESS'}
                  onChange={e => setForm({...form, ruleType: e.target.value})}>
                  {RULE_TYPES.map(t => <option key={t} value={t}>{getRuleTypeLabel(t)}</option>)}
                </select>
                <input className={`w-full border ${styles.inputBorder} ${styles.inputBg} rounded-lg px-3 py-2 text-xs outline-none focus:border-indigo-400`}
                  placeholder={tl("目标实体 (表名)", "Target Entity (table)")} value={form.targetEntity || ''}
                  onChange={e => setForm({...form, targetEntity: e.target.value})} />
                <input className={`w-full border ${styles.inputBorder} ${styles.inputBg} rounded-lg px-3 py-2 text-xs outline-none focus:border-indigo-400`}
                  placeholder={tl("目标字段", "Target Field")} value={form.targetField || ''}
                  onChange={e => setForm({...form, targetField: e.target.value})} />
                <input className={`w-full border ${styles.inputBorder} ${styles.inputBg} rounded-lg px-3 py-2 text-xs outline-none focus:border-indigo-400`}
                  placeholder={tl("规则表达式 (如: name IS NOT NULL)", "Rule Expression (e.g. name IS NOT NULL)")}
                  value={form.ruleExpression || ''}
                  onChange={e => setForm({...form, ruleExpression: e.target.value})} />
                <select className={`w-full border ${styles.inputBorder} ${styles.inputBg} rounded-lg px-3 py-2 text-xs outline-none focus:border-indigo-400`}
                  value={form.severity || 'MEDIUM'}
                  onChange={e => setForm({...form, severity: e.target.value})}>
                  {SEVERITIES.map(v => <option key={v} value={v}>{v}</option>)}
                </select>
              </div>
              <div className="flex justify-end gap-2 mt-5">
                <button onClick={() => setDlgOpen(false)}
                  className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-lg">
                  {tl("取消", "Cancel")}
                </button>
                <button onClick={saveDlg}
                  className="px-4 py-2 text-xs font-semibold bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg">
                  {tl("保存", "Save")}
                </button>
              </div>
            </div>
          </div>
        )}

        {/* RESOLVE DIALOG */}
        {resolveOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm" onClick={() => setResolveOpen(false)}>
            <div className={`${styles.cardBg} rounded-xl shadow-2xl w-full max-w-md mx-4 p-6 border ${styles.cardBorder}`} onClick={e => e.stopPropagation()}>
              <h2 className={`text-lg font-bold ${styles.cardText} mb-4`}>{tl("解决问题", "Resolve Issue")}</h2>
              <textarea className={`w-full border ${styles.inputBorder} ${styles.inputBg} rounded-lg px-3 py-2 text-xs outline-none focus:border-indigo-400 min-h-[80px]`}
                placeholder={tl("处理备注", "Resolution Note")}
                value={resolveIssue?.note || ''}
                onChange={e => setResolveIssue({...resolveIssue, note: e.target.value})} />
              <div className="flex justify-end gap-2 mt-4">
                <button onClick={() => setResolveOpen(false)}
                  className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-lg">
                  {tl("取消", "Cancel")}
                </button>
                <button onClick={resolveIssueAction}
                  className="px-4 py-2 text-xs font-semibold bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg">
                  {tl("确认解决", "Confirm")}
                </button>
              </div>
            </div>
          </div>
        )}

      </div>
    </div>
  );
}
