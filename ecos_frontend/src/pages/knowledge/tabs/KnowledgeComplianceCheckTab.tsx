import React, { useState } from 'react';
import {
  Shield, CheckCircle, XCircle, Search, Download, Plus, Trash2,
  FileText, AlertTriangle, ChevronRight, ChevronDown, Code, List,
  ClipboardCheck,
} from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { apiFetch } from '../../../api';

// ── 业务对象选项 ──────────────────────────────────────────
const BUSINESS_OBJECTS = [
  { value: 'medical_device', labelZh: '医疗器械', labelEn: 'Medical Device' },
  { value: 'pharmaceutical', labelZh: '药品', labelEn: 'Pharmaceutical' },
  { value: 'cosmetics', labelZh: '化妆品', labelEn: 'Cosmetics' },
  { value: 'food', labelZh: '食品', labelEn: 'Food' },
  { value: 'health_supplement', labelZh: '保健食品', labelEn: 'Health Supplement' },
  { value: 'biologics', labelZh: '生物制品', labelEn: 'Biologics' },
  { value: 'in_vitro_diagnostic', labelZh: '体外诊断试剂', labelEn: 'In Vitro Diagnostic' },
];

// ── 类型 ──────────────────────────────────────────────────

interface FactEntry {
  id: string;
  key: string;
  value: string;
}

interface CheckedRule {
  ruleId: string;
  ruleName: string;
  passed: boolean;
  condition: string;
  facts: Record<string, string>;
  conclusion: string;
  source: string;
  reasoningChain?: string;
}

interface ComplianceCheckResponse {
  results: CheckedRule[];
  summary: {
    total: number;
    passed: number;
    failed: number;
  };
}

// ── 工具函数 ──────────────────────────────────────────────

let _factIdCounter = 1;
function nextFactId(): string {
  return `fact_${_factIdCounter++}`;
}

function buildFactsPayload(
  facts: FactEntry[],
  jsonMode: boolean,
  jsonInput: string,
): Record<string, string> {
  if (jsonMode) {
    try {
      return JSON.parse(jsonInput);
    } catch {
      return {};
    }
  }
  const payload: Record<string, string> = {};
  for (const f of facts) {
    if (f.key.trim()) {
      payload[f.key.trim()] = f.value.trim();
    }
  }
  return payload;
}

// ── 组件 ──────────────────────────────────────────────────

export default function KnowledgeComplianceCheckTab() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();

  const [businessObject, setBusinessObject] = useState('');
  const [facts, setFacts] = useState<FactEntry[]>([{ id: nextFactId(), key: '', value: 'true' }]);
  const [jsonMode, setJsonMode] = useState(false);
  const [jsonInput, setJsonInput] = useState('');
  const [isChecking, setIsChecking] = useState(false);
  const [results, setResults] = useState<ComplianceCheckResponse | null>(null);
  const [error, setError] = useState('');
  const [expandedRules, setExpandedRules] = useState<Set<string>>(new Set());

  // ── Fact 操作 ──────────────────────────────────────────

  const addFactRow = () => {
    setFacts(prev => [...prev, { id: nextFactId(), key: '', value: 'true' }]);
  };

  const removeFactRow = (id: string) => {
    setFacts(prev => (prev.length <= 1 ? prev : prev.filter(f => f.id !== id)));
  };

  const updateFact = (id: string, field: 'key' | 'value', val: string) => {
    setFacts(prev => prev.map(f => (f.id === id ? { ...f, [field]: val } : f)));
  };

  const toggleRuleExpand = (ruleId: string) => {
    setExpandedRules(prev => {
      const next = new Set(prev);
      if (next.has(ruleId)) next.delete(ruleId);
      else next.add(ruleId);
      return next;
    });
  };

  // ── 合规检查 ────────────────────────────────────────────

  const handleCheck = async () => {
    if (!businessObject) {
      setError(t("knowledge.knowledgecompliancechecktab.请选择业务对象"));
      return;
    }
    const factsPayload = buildFactsPayload(facts, jsonMode, jsonInput);
    if (Object.keys(factsPayload).length === 0) {
      setError(t("knowledge.knowledgecompliancechecktab.请至少输入一条事实"));
      return;
    }
    setError('');
    setIsChecking(true);
    setResults(null);
    try {
      const data = await apiFetch<ComplianceCheckResponse>('/v1/rules/check', {
        method: 'POST',
        body: JSON.stringify({
          businessObject,
          facts: factsPayload,
        }),
      });
      setResults(data);
    } catch (e: any) {
      setError(e?.message || (t("knowledge.knowledgecompliancechecktab.合规检查请求失败")));
    } finally {
      setIsChecking(false);
    }
  };

  // ── 报告导出 ────────────────────────────────────────────

  const handleExportReport = () => {
    if (!results) return;
    const zh = locale === 'zh';
    const lines: string[] = [
      '═══════════════════════════════════════',
      zh ? '  合规检查报告' : '  Compliance Check Report',
      '═══════════════════════════════════════',
      '',
      `${zh ? '业务对象' : 'Business Object'}: ${businessObject}`,
      `${zh ? '检查时间' : 'Check Time'}: ${new Date().toLocaleString()}`,
      '',
      `${zh ? '总结' : 'Summary'}:`,
      `  ${zh ? '总计' : 'Total'}: ${results.summary.total}`,
      `  ${zh ? '通过' : 'Passed'}: ${results.summary.passed} ✓`,
      `  ${zh ? '未通过' : 'Failed'}: ${results.summary.failed} ✗`,
      '',
      '───────────────────────────────────────',
    ];

    for (const r of results.results) {
      lines.push(
        '',
        `${r.passed ? '✓' : '✗'} [${r.ruleId}] ${r.ruleName}`,
        `  ${zh ? '条件' : 'Condition'}: ${r.condition}`,
        `  ${zh ? '结论' : 'Conclusion'}: ${r.conclusion}`,
        `  ${zh ? '来源' : 'Source'}: ${r.source}`,
      );
    }
    lines.push('', '═══════════════════════════════════════');

    const blob = new Blob([lines.join('\n')], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `compliance-report-${businessObject}-${Date.now()}.txt`;
    a.click();
    URL.revokeObjectURL(url);
  };

  // ── 渲染 ────────────────────────────────────────────────

  const zh = locale === 'zh';

  return (
    <div className="space-y-6 max-w-5xl">
      {/* 标题 */}
      <div className="border-b border-slate-200 pb-3 space-y-1">
        <h2 className="text-sm font-black text-slate-800 flex items-center gap-2">
          <Shield size={16} className="text-indigo-600" />
          {zh ? '合规检查 (Compliance Check)' : 'Compliance Check'}
        </h2>
        <p className="text-xs text-slate-500">
          {zh
            ? '选择业务对象并输入事实，系统将自动匹配规则库进行合规性校验，输出每个规则的判定结果与推理链路。'
            : 'Select a business object and input facts. The system matches rules automatically, validating compliance and outputting per-rule verdicts with reasoning chains.'}
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* ── 左侧：输入区 ───────────────────────────────── */}
        <div className="lg:col-span-5 space-y-4">
          {/* 业务对象选择 */}
          <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-xs space-y-3">
            <div className="flex items-center gap-2 border-b border-slate-100 pb-2">
              <span className="p-1.5 rounded bg-indigo-50 text-indigo-600">
                <Search size={13} />
              </span>
              <h3 className="font-bold text-slate-800 text-xs">
                {zh ? '选择业务对象' : 'Business Object'}
              </h3>
            </div>
            <select
              value={businessObject}
              onChange={e => setBusinessObject(e.target.value)}
              className="w-full px-3 py-2 border border-slate-200 rounded-lg text-xs font-sans text-slate-700 bg-white focus:outline-hidden focus:border-indigo-500 cursor-pointer"
            >
              <option value="">
                {zh ? '-- 请选择业务对象 --' : '-- Select Business Object --'}
              </option>
              {BUSINESS_OBJECTS.map(obj => (
                <option key={obj.value} value={obj.value}>
                  {zh ? obj.labelZh : obj.labelEn}
                </option>
              ))}
            </select>
          </div>

          {/* 事实输入 */}
          <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-xs space-y-3">
            <div className="flex items-center justify-between border-b border-slate-100 pb-2">
              <div className="flex items-center gap-2">
                <span className="p-1.5 rounded bg-violet-50 text-violet-600">
                  <FileText size={13} />
                </span>
                <h3 className="font-bold text-slate-800 text-xs">
                  {zh ? '事实输入' : 'Facts Input'}
                </h3>
              </div>
              {/* 模式切换 */}
              <div className="flex rounded-lg border border-slate-200 overflow-hidden">
                <button
                  onClick={() => setJsonMode(false)}
                  className={`px-2.5 py-1 text-[10px] font-bold cursor-pointer transition-colors ${
                    !jsonMode
                      ? 'bg-indigo-600 text-white'
                      : 'bg-slate-50 text-slate-500 hover:bg-slate-100'
                  }`}
                >
                  <List size={11} className="inline mr-1" />
                  KV
                </button>
                <button
                  onClick={() => setJsonMode(true)}
                  className={`px-2.5 py-1 text-[10px] font-bold cursor-pointer transition-colors ${
                    jsonMode
                      ? 'bg-indigo-600 text-white'
                      : 'bg-slate-50 text-slate-500 hover:bg-slate-100'
                  }`}
                >
                  <Code size={11} className="inline mr-1" />
                  JSON
                </button>
              </div>
            </div>

            {jsonMode ? (
              <textarea
                value={jsonInput}
                onChange={e => setJsonInput(e.target.value)}
                rows={6}
                placeholder={JSON.stringify({ '灭菌工艺变更': 'true', '安全性数据': 'false' }, null, 2)}
                className="w-full px-3 py-2 border border-slate-200 rounded-lg text-xs font-mono text-slate-700 leading-relaxed focus:outline-hidden focus:border-violet-500"
              />
            ) : (
              <div className="space-y-2">
                {facts.map((f) => (
                  <div key={f.id} className="flex items-center gap-2">
                    <input
                      value={f.key}
                      onChange={e => updateFact(f.id, 'key', e.target.value)}
                      placeholder={zh ? '事实名 (例: 灭菌工艺变更)' : 'Fact key (e.g. process_change)'}
                      className="flex-1 px-2.5 py-1.5 border border-slate-200 rounded-lg text-xs font-mono text-slate-700 focus:outline-hidden focus:border-violet-500"
                    />
                    <select
                      value={f.value}
                      onChange={e => updateFact(f.id, 'value', e.target.value)}
                      className="w-24 px-2 py-1.5 border border-slate-200 rounded-lg text-xs font-mono text-slate-700 bg-white focus:outline-hidden focus:border-violet-500 cursor-pointer"
                    >
                      <option value="true">true</option>
                      <option value="false">false</option>
                      <option value="unknown">unknown</option>
                    </select>
                    <button
                      onClick={() => removeFactRow(f.id)}
                      disabled={facts.length <= 1}
                      className="p-1.5 rounded-lg text-slate-400 hover:text-red-500 hover:bg-red-50 cursor-pointer disabled:opacity-30 disabled:cursor-not-allowed"
                    >
                      <Trash2 size={13} />
                    </button>
                  </div>
                ))}
                <button
                  onClick={addFactRow}
                  className="w-full py-1.5 border border-dashed border-slate-300 rounded-lg text-[10px] text-slate-500 hover:text-indigo-600 hover:border-indigo-300 hover:bg-indigo-50 font-bold flex items-center justify-center gap-1 cursor-pointer transition-colors"
                >
                  <Plus size={11} />
                  {zh ? '添加事实' : 'Add Fact'}
                </button>
              </div>
            )}
          </div>

          {/* 检查按钮 */}
          <button
            onClick={handleCheck}
            disabled={isChecking || !businessObject}
            className={`w-full py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-xl flex items-center justify-center gap-2 shadow-sm transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed`}
          >
            {isChecking ? (
              <>
                <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                {zh ? '合规检查中...' : 'Checking...'}
              </>
            ) : (
              <>
                <ClipboardCheck size={14} />
                {zh ? '执行合规检查' : 'Run Compliance Check'}
              </>
            )}
          </button>

          {error && (
            <div className="flex items-center gap-2 px-3 py-2 bg-red-50 border border-red-200 rounded-lg text-xs text-red-700">
              <AlertTriangle size={13} />
              {error}
            </div>
          )}
        </div>

        {/* ── 右侧：结果区 ───────────────────────────────── */}
        <div className="lg:col-span-7 space-y-4">
          {/* 结果占位 */}
          {!results && !isChecking && (
            <div className="bg-white border border-slate-200 rounded-xl p-8 shadow-xs text-center space-y-2">
              <Shield size={32} className="text-slate-300 mx-auto" />
              <p className="text-xs text-slate-400 font-medium">
                {zh
                  ? '选择业务对象并输入事实后，点击"执行合规检查"开始'
                  : 'Select a business object, enter facts, then click "Run Compliance Check"'}
              </p>
            </div>
          )}

          {/* 检查中 */}
          {isChecking && (
            <div className="bg-white border border-slate-200 rounded-xl p-8 shadow-xs text-center space-y-3">
              <span className="w-8 h-8 border-3 border-indigo-200 border-t-indigo-600 rounded-full animate-spin mx-auto block" />
              <p className="text-xs text-slate-500 font-medium">
                {zh ? '正在调用规则引擎进行合规校验...' : 'Invoking rule engine for compliance validation...'}
              </p>
            </div>
          )}

          {/* 结果展示 */}
          {results && (
            <>
              {/* 总结卡片 */}
              <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-xs">
                <div className="flex items-center justify-between border-b border-slate-100 pb-3 mb-3">
                  <h3 className="font-bold text-slate-800 text-xs flex items-center gap-2">
                    <ClipboardCheck size={13} className="text-indigo-600" />
                    {zh ? '检查结果' : 'Check Results'}
                  </h3>
                  <button
                    onClick={handleExportReport}
                    className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white font-bold rounded-lg flex items-center gap-1.5 cursor-pointer text-[10px] transition-colors shadow-sm"
                  >
                    <Download size={12} />
                    {zh ? '导出报告' : 'Export Report'}
                  </button>
                </div>

                <div className="flex gap-4">
                  <div className="flex-1 bg-slate-50 rounded-lg p-3 text-center">
                    <div className="text-lg font-black text-slate-800">{results.summary.total}</div>
                    <div className="text-[9px] text-slate-500 font-bold uppercase">{zh ? '总计' : 'Total'}</div>
                  </div>
                  <div className="flex-1 bg-emerald-50 rounded-lg p-3 text-center">
                    <div className="text-lg font-black text-emerald-700">{results.summary.passed}</div>
                    <div className="text-[9px] text-emerald-600 font-bold uppercase">{zh ? '通过' : 'Passed'}</div>
                  </div>
                  <div className="flex-1 bg-red-50 rounded-lg p-3 text-center">
                    <div className="text-lg font-black text-red-700">{results.summary.failed}</div>
                    <div className="text-[9px] text-red-600 font-bold uppercase">{zh ? '未通过' : 'Failed'}</div>
                  </div>
                </div>
              </div>

              {/* 逐条规则详情 */}
              <div className="space-y-2 max-h-[60vh] overflow-y-auto">
                {results.results.map((r) => (
                  <div
                    key={r.ruleId}
                    className={`bg-white border rounded-xl shadow-xs overflow-hidden ${
                      r.passed ? 'border-emerald-200' : 'border-red-200'
                    }`}
                  >
                    {/* 规则头部 */}
                    <button
                      onClick={() => toggleRuleExpand(r.ruleId)}
                      className="w-full flex items-center gap-3 px-4 py-3 text-left hover:bg-slate-50 transition-colors cursor-pointer"
                    >
                      {r.passed ? (
                        <CheckCircle size={16} className="text-emerald-500 shrink-0" />
                      ) : (
                        <XCircle size={16} className="text-red-500 shrink-0" />
                      )}
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="font-bold text-slate-800 text-xs truncate">{r.ruleName}</span>
                          <span className="text-[9px] font-mono text-slate-400 bg-slate-100 px-1.5 py-0.5 rounded shrink-0">
                            {r.ruleId}
                          </span>
                        </div>
                        <div className="text-[10px] text-slate-500 truncate mt-0.5">
                          {r.conclusion}
                        </div>
                      </div>
                      <span
                        className={`text-[10px] font-bold px-2 py-0.5 rounded-full shrink-0 ${
                          r.passed
                            ? 'bg-emerald-100 text-emerald-700'
                            : 'bg-red-100 text-red-700'
                        }`}
                      >
                        {r.passed ? (zh ? '通过' : 'PASS') : (zh ? '未通过' : 'FAIL')}
                      </span>
                      <ChevronDown
                        size={14}
                        className={`text-slate-400 transition-transform shrink-0 ${
                          expandedRules.has(r.ruleId) ? 'rotate-180' : ''
                        }`}
                      />
                    </button>

                    {/* 推理链 (展开详情) */}
                    {expandedRules.has(r.ruleId) && (
                      <div className="border-t border-slate-100 bg-slate-50 px-4 py-3 space-y-2">
                        {/* 推理链路图 */}
                        <div className="text-[9px] font-extrabold text-slate-500 uppercase tracking-wider">
                          {zh ? '推理链' : 'Reasoning Chain'}:
                        </div>
                        <div className="flex items-center gap-1.5 flex-wrap text-[10px]">
                          <span className="px-2 py-0.5 bg-indigo-100 text-indigo-700 font-mono font-bold rounded">
                            {r.ruleId}
                          </span>
                          <ChevronRight size={10} className="text-slate-400" />
                          <span className="px-2 py-0.5 bg-violet-100 text-violet-700 font-mono rounded">
                            {zh ? '条件' : 'Condition'}
                          </span>
                          <ChevronRight size={10} className="text-slate-400" />
                          <span className="px-2 py-0.5 bg-amber-100 text-amber-700 font-mono rounded">
                            {zh ? '事实' : 'Facts'}
                          </span>
                          <ChevronRight size={10} className="text-slate-400" />
                          <span
                            className={`px-2 py-0.5 font-bold rounded ${
                              r.passed
                                ? 'bg-emerald-100 text-emerald-700'
                                : 'bg-red-100 text-red-700'
                            }`}
                          >
                            {zh ? '结论' : 'Conclusion'}
                          </span>
                          <ChevronRight size={10} className="text-slate-400" />
                          <span className="px-2 py-0.5 bg-slate-200 text-slate-600 font-mono rounded">
                            {zh ? '来源' : 'Source'}
                          </span>
                        </div>

                        {/* 详细信息 */}
                        <div className="grid grid-cols-1 gap-1.5 mt-1">
                          <div className="flex gap-2 text-[10px]">
                            <span className="text-slate-400 font-bold w-12 shrink-0">{zh ? '规则ID' : 'Rule ID'}:</span>
                            <span className="text-slate-700 font-mono">{r.ruleId}</span>
                          </div>
                          <div className="flex gap-2 text-[10px]">
                            <span className="text-slate-400 font-bold w-12 shrink-0">{zh ? '条件' : 'Condition'}:</span>
                            <span className="text-slate-700">{r.condition}</span>
                          </div>
                          <div className="flex gap-2 text-[10px]">
                            <span className="text-slate-400 font-bold w-12 shrink-0">{zh ? '事实' : 'Facts'}:</span>
                            <div className="flex flex-wrap gap-1">
                              {Object.entries(r.facts).map(([k, v]) => (
                                <span
                                  key={k}
                                  className={`px-1.5 py-0.5 rounded text-[9px] font-mono ${
                                    v === 'true'
                                      ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                                      : v === 'false'
                                        ? 'bg-red-50 text-red-700 border border-red-200'
                                        : 'bg-slate-100 text-slate-600 border border-slate-200'
                                  }`}
                                >
                                  {k}: {v}
                                </span>
                              ))}
                            </div>
                          </div>
                          <div className="flex gap-2 text-[10px]">
                            <span className="text-slate-400 font-bold w-12 shrink-0">{zh ? '结论' : 'Conclusion'}:</span>
                            <span className={`font-bold ${r.passed ? 'text-emerald-700' : 'text-red-700'}`}>
                              {r.conclusion}
                            </span>
                          </div>
                          <div className="flex gap-2 text-[10px]">
                            <span className="text-slate-400 font-bold w-12 shrink-0">{zh ? '来源' : 'Source'}:</span>
                            <span className="text-slate-600 font-mono">{r.source}</span>
                          </div>
                          {r.reasoningChain && (
                            <div className="flex gap-2 text-[10px]">
                              <span className="text-slate-400 font-bold w-12 shrink-0">{zh ? '推理' : 'Reasoning'}:</span>
                              <span className="text-slate-600 whitespace-pre-wrap">{r.reasoningChain}</span>
                            </div>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
