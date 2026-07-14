/**
 * ECOS Policy Engine — Rego Editor + Policy Test Panel
 * OPA 策略管理前端
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback } from "react";
import {
  Shield, Save, Play, RefreshCw, CheckCircle, XCircle,
  Loader2, AlertTriangle, Zap, FileCode, Terminal
} from "lucide-react";
import {
  fetchPolicyEnginePolicies, fetchPolicyEnginePolicy, updatePolicyEnginePolicy,
  evaluatePolicyEngine, fetchPolicyEngineStatus,
  type PolicyEngineStatus, type PolicyEvalResult
} from "../api";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";

const DEFAULT_INPUT = `{
  "user": "alice",
  "action": "read",
  "resource": "documents/report-001",
  "role": "viewer"
}`;

export default function PolicyEngine() {
  const { locale } = useLanguage();
  const { styles } = useTheme();

  // ── State ──
  const [policies, setPolicies] = useState<string[]>([]);
  const [selectedPolicy, setSelectedPolicy] = useState<string | null>(null);
  const [regoContent, setRegoContent] = useState<string>("");
  const [originalContent, setOriginalContent] = useState<string>("");
  const [dirty, setDirty] = useState(false);
  const [jsonInput, setJsonInput] = useState(DEFAULT_INPUT);
  const [evalResult, setEvalResult] = useState<PolicyEvalResult | null>(null);
  const [evalError, setEvalError] = useState<string | null>(null);

  // Loading / status
  const [loadingPolicies, setLoadingPolicies] = useState(false);
  const [loadingContent, setLoadingContent] = useState(false);
  const [saving, setSaving] = useState(false);
  const [evaluating, setEvaluating] = useState(false);
  const [status, setStatus] = useState<PolicyEngineStatus | null>(null);
  const [statusLoading, setStatusLoading] = useState(false);

  // Feedback
  const [saveMessage, setSaveMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);

  // ── Load policy list ──
  const loadPolicies = useCallback(async () => {
    setLoadingPolicies(true);
    try {
      const list = await fetchPolicyEnginePolicies();
      setPolicies(list);
    } catch (e: any) {
      console.warn("loadPolicies failed", e);
      setPolicies([]);
    } finally {
      setLoadingPolicies(false);
    }
  }, []);

  // ── Load policy content ──
  const loadPolicyContent = useCallback(async (name: string) => {
    setLoadingContent(true);
    setEvalResult(null);
    setEvalError(null);
    try {
      const content = await fetchPolicyEnginePolicy(name);
      setRegoContent(content);
      setOriginalContent(content);
      setDirty(false);
    } catch (e: any) {
      console.warn("loadPolicyContent failed", e);
      setRegoContent(`# Failed to load: ${name}\n# ${e.message}`);
      setOriginalContent("");
    } finally {
      setLoadingContent(false);
    }
  }, []);

  // ── Load OPA status ──
  const loadStatus = useCallback(async () => {
    setStatusLoading(true);
    try {
      const s = await fetchPolicyEngineStatus();
      setStatus(s);
    } catch (e: any) {
      console.warn("loadStatus failed", e);
      setStatus(null);
    } finally {
      setStatusLoading(false);
    }
  }, []);

  // ── Initial load ──
  useEffect(() => {
    loadPolicies();
    loadStatus();
  }, [loadPolicies, loadStatus]);

  // ── Select policy ──
  const handleSelectPolicy = (name: string) => {
    if (dirty && !window.confirm(locale === "zh" ? "未保存的更改将丢失，是否继续？" : "Unsaved changes will be lost. Continue?")) {
      return;
    }
    setSelectedPolicy(name);
    setSaveMessage(null);
    loadPolicyContent(name);
  };

  // ── Track dirty ──
  const handleRegoChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const val = e.target.value;
    setRegoContent(val);
    setDirty(val !== originalContent);
    setSaveMessage(null);
  };

  // ── Save ──
  const handleSave = async () => {
    if (!selectedPolicy) return;
    setSaving(true);
    setSaveMessage(null);
    try {
      await updatePolicyEnginePolicy(selectedPolicy, { content: regoContent });
      setOriginalContent(regoContent);
      setDirty(false);
      setSaveMessage({ type: "success", text: locale === "zh" ? "策略已保存并热加载到 OPA" : "Policy saved & hot-reloaded to OPA" });
    } catch (e: any) {
      setSaveMessage({ type: "error", text: e.message || "Save failed" });
    } finally {
      setSaving(false);
    }
  };

  // ── Evaluate ──
  const handleEvaluate = async () => {
    if (!selectedPolicy) return;
    // Validate JSON input
    let input: any;
    try {
      input = JSON.parse(jsonInput);
    } catch {
      setEvalError(locale === "zh" ? "JSON 格式错误" : "Invalid JSON input");
      setEvalResult(null);
      return;
    }

    setEvaluating(true);
    setEvalError(null);
    setEvalResult(null);
    try {
      const result = await evaluatePolicyEngine({
        policy: selectedPolicy,
        input,
      });
      setEvalResult(result);
    } catch (e: any) {
      setEvalError(e.message || "Evaluation failed");
    } finally {
      setEvaluating(false);
    }
  };

  // ── Refresh all ──
  const handleRefresh = () => {
    loadPolicies();
    loadStatus();
    if (selectedPolicy) {
      loadPolicyContent(selectedPolicy);
    }
  };

  // ── Render helpers ──
  const t = (zh: string, en: string) => locale === "zh" ? zh : en;

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {/* ── Status Bar ── */}
      <div className={`flex items-center gap-4 px-6 py-3 border-b ${styles.appBorder} shrink-0`}>
        {/* OPA Connection Status */}
        <div className="flex items-center gap-2">
          <span className="text-xs font-mono uppercase tracking-wider opacity-60">
            {t("OPA 状态", "OPA Status")}
          </span>
          {statusLoading ? (
            <Loader2 className="w-4 h-4 animate-spin opacity-50" />
          ) : status ? (
            <span className="flex items-center gap-1.5 text-xs font-semibold">
              <span className={`w-2 h-2 rounded-full ${status.connected ? "bg-emerald-500" : "bg-red-500"}`} />
              <span className={status.connected ? "text-emerald-600" : "text-red-500"}>
                {status.connected ? t("已连接", "Connected") : t("未连接", "Disconnected")}
              </span>
            </span>
          ) : (
            <span className="flex items-center gap-1.5 text-xs font-semibold text-red-500">
              <span className="w-2 h-2 rounded-full bg-red-500" />
              {t("未连接", "Unreachable")}
            </span>
          )}
        </div>

        <span className="opacity-30">|</span>

        {/* Policy Count */}
        <div className="flex items-center gap-2">
          <FileCode className="w-3.5 h-3.5 opacity-50" />
          <span className="text-xs opacity-70">
            {t("策略数", "Policies")}: <span className="font-semibold">{policies.length}</span>
          </span>
        </div>

        <span className="opacity-30">|</span>

        {/* Last Update */}
        <div className="flex items-center gap-2">
          <RefreshCw className="w-3.5 h-3.5 opacity-50" />
          <span className="text-xs opacity-70">
            {t("最近更新", "Last refresh")}: <span className="font-mono">{new Date().toLocaleTimeString()}</span>
          </span>
        </div>

        {/* Refresh button */}
        <button
          onClick={handleRefresh}
          className="ml-auto p-1.5 rounded hover:bg-slate-200/50 dark:hover:bg-slate-700/50 transition-colors"
          title={t("刷新", "Refresh")}
        >
          <RefreshCw className={`w-4 h-4 opacity-50 ${(loadingPolicies || statusLoading) ? "animate-spin" : ""}`} />
        </button>
      </div>

      {/* ── Main 3-panel layout ── */}
      <div className="flex-1 flex min-h-0 overflow-hidden">
        {/* ── Left: Policy List ── */}
        <div className={`w-56 shrink-0 border-r ${styles.appBorder} flex flex-col overflow-hidden`}>
          <div className={`px-4 py-3 border-b ${styles.appBorder} shrink-0`}>
            <span className="text-xs font-semibold uppercase tracking-wider opacity-60">
              {t("策略列表", "Policy List")}
            </span>
          </div>
          <div className="flex-1 overflow-y-auto p-2 space-y-1">
            {loadingPolicies ? (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="w-5 h-5 animate-spin opacity-40" />
              </div>
            ) : policies.length === 0 ? (
              <div className="text-xs opacity-40 text-center py-8">
                {t("暂无策略", "No policies found")}
              </div>
            ) : (
              policies.map((name) => (
                <button
                  key={name}
                  onClick={() => handleSelectPolicy(name)}
                  className={`w-full text-left px-3 py-2 rounded text-xs transition-colors ${
                    selectedPolicy === name
                      ? "bg-indigo-500/15 text-indigo-600 dark:text-indigo-400 font-semibold"
                      : "hover:bg-slate-100 dark:hover:bg-slate-800 opacity-75 hover:opacity-100"
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <Shield className={`w-3.5 h-3.5 shrink-0 ${selectedPolicy === name ? "text-indigo-500" : "opacity-40"}`} />
                    <span className="truncate">{name}</span>
                  </div>
                </button>
              ))
            )}
          </div>
        </div>

        {/* ── Center: Rego Editor ── */}
        <div className="flex-1 flex flex-col min-w-0 overflow-hidden border-r border-slate-200/50 dark:border-slate-700/50">
          {/* Editor Header */}
          <div className="flex items-center justify-between px-4 py-2 shrink-0 border-b border-slate-200/50 dark:border-slate-700/50 bg-slate-50/50 dark:bg-slate-900/30">
            <div className="flex items-center gap-2">
              <Zap className="w-4 h-4 text-amber-500" />
              <span className="text-xs font-semibold uppercase tracking-wider opacity-70">
                {t("Rego 编辑器", "Rego Editor")}
              </span>
              {selectedPolicy && (
                <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-slate-200/70 dark:bg-slate-700/70 opacity-60">
                  {selectedPolicy}
                </span>
              )}
              {dirty && (
                <span className="text-[10px] text-amber-600 dark:text-amber-400">● {t("已修改", "Modified")}</span>
              )}
            </div>
            <div className="flex items-center gap-2">
              {/* Save button */}
              <button
                onClick={handleSave}
                disabled={!selectedPolicy || !dirty || saving}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded text-xs font-semibold transition-all ${
                  !selectedPolicy || !dirty
                    ? "opacity-30 cursor-not-allowed bg-slate-200 dark:bg-slate-700 text-slate-500"
                    : "bg-indigo-500 hover:bg-indigo-600 text-white shadow-sm"
                }`}
              >
                {saving ? (
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  <Save className="w-3.5 h-3.5" />
                )}
                {t("保存", "Save")}
              </button>
            </div>
          </div>

          {/* Save feedback */}
          {saveMessage && (
            <div className={`px-4 py-1.5 text-xs flex items-center gap-1.5 ${
              saveMessage.type === "success"
                ? "bg-emerald-50 dark:bg-emerald-900/20 text-emerald-700 dark:text-emerald-400"
                : "bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-400"
            }`}>
              {saveMessage.type === "success" ? (
                <CheckCircle className="w-3.5 h-3.5" />
              ) : (
                <AlertTriangle className="w-3.5 h-3.5" />
              )}
              {saveMessage.text}
            </div>
          )}

          {/* Editor area */}
          <div className="flex-1 min-h-0 overflow-hidden">
            {loadingContent ? (
              <div className="flex items-center justify-center h-full">
                <Loader2 className="w-6 h-6 animate-spin opacity-40" />
              </div>
            ) : !selectedPolicy ? (
              <div className="flex items-center justify-center h-full">
                <div className="text-center opacity-40">
                  <FileCode className="w-10 h-10 mx-auto mb-2" />
                  <p className="text-sm">{t("请从左侧选择一个策略", "Select a policy from the left")}</p>
                </div>
              </div>
            ) : (
              <textarea
                value={regoContent}
                onChange={handleRegoChange}
                className="w-full h-full resize-none p-4 font-mono text-sm leading-relaxed bg-slate-900 text-slate-300 outline-none border-none"
                spellCheck={false}
                placeholder={`package ecos.rbac\n\n# Rego policy content...`}
              />
            )}
          </div>
        </div>

        {/* ── Right: Test Panel ── */}
        <div className="w-80 shrink-0 flex flex-col overflow-hidden">
          {/* Test Header */}
          <div className="flex items-center gap-2 px-4 py-2 shrink-0 border-b border-slate-200/50 dark:border-slate-700/50 bg-slate-50/50 dark:bg-slate-900/30">
            <Terminal className="w-4 h-4 text-emerald-500" />
            <span className="text-xs font-semibold uppercase tracking-wider opacity-70">
              {t("策略测试", "Policy Test")}
            </span>
          </div>

          {/* JSON Input */}
          <div className="px-3 pt-3 shrink-0">
            <label className="text-[10px] font-semibold uppercase tracking-wider opacity-50 block mb-1.5">
              {t("输入 (JSON)", "Input (JSON)")}
            </label>
            <textarea
              value={jsonInput}
              onChange={(e) => { setJsonInput(e.target.value); setEvalResult(null); setEvalError(null); }}
              className="w-full h-32 resize-none p-3 font-mono text-xs leading-relaxed rounded border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-900 text-slate-800 dark:text-slate-200 outline-none focus:ring-2 focus:ring-indigo-500/30 transition-shadow"
              spellCheck={false}
            />
          </div>

          {/* Evaluate button */}
          <div className="px-3 pt-2 shrink-0">
            <button
              onClick={handleEvaluate}
              disabled={!selectedPolicy || evaluating}
              className={`w-full flex items-center justify-center gap-2 px-4 py-2 rounded text-sm font-semibold transition-all ${
                !selectedPolicy
                  ? "opacity-30 cursor-not-allowed bg-slate-200 dark:bg-slate-700 text-slate-500"
                  : "bg-emerald-500 hover:bg-emerald-600 text-white shadow-sm"
              }`}
            >
              {evaluating ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <Play className="w-4 h-4" />
              )}
              {t("执行评估", "Evaluate")}
            </button>
          </div>

          {/* Result area */}
          <div className="flex-1 overflow-y-auto px-3 py-3 min-h-0">
            {evalError ? (
              <div className="p-3 rounded bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800">
                <div className="flex items-start gap-2">
                  <AlertTriangle className="w-4 h-4 text-red-500 shrink-0 mt-0.5" />
                  <div>
                    <p className="text-xs font-semibold text-red-700 dark:text-red-400 mb-1">
                      {t("评估错误", "Evaluation Error")}
                    </p>
                    <p className="text-xs text-red-600 dark:text-red-300 font-mono break-all">{evalError}</p>
                  </div>
                </div>
              </div>
            ) : evalResult ? (
              <div className="space-y-3">
                {/* Result badge */}
                <div className={`p-4 rounded-lg border-2 flex items-center gap-3 ${
                  evalResult.result === true
                    ? "bg-emerald-50 dark:bg-emerald-900/20 border-emerald-300 dark:border-emerald-700"
                    : "bg-red-50 dark:bg-red-900/20 border-red-300 dark:border-red-700"
                }`}>
                  {evalResult.result === true ? (
                    <CheckCircle className="w-7 h-7 text-emerald-500 shrink-0" />
                  ) : (
                    <XCircle className="w-7 h-7 text-red-500 shrink-0" />
                  )}
                  <div>
                    <p className={`text-sm font-bold ${
                      evalResult.result === true
                        ? "text-emerald-700 dark:text-emerald-400"
                        : "text-red-700 dark:text-red-400"
                    }`}>
                      {evalResult.result === true ? "ALLOW ✓" : "DENY ✗"}
                    </p>
                    <p className="text-[10px] opacity-60 mt-0.5">
                      {t("策略", "Policy")}: {selectedPolicy}
                    </p>
                  </div>
                </div>

                {/* Raw response */}
                <details className="text-xs">
                  <summary className="cursor-pointer opacity-50 hover:opacity-80 font-mono">
                    {t("查看原始响应", "View raw response")}
                  </summary>
                  <pre className="mt-2 p-2 rounded bg-slate-100 dark:bg-slate-800 font-mono text-[11px] overflow-x-auto">
                    {JSON.stringify(evalResult, null, 2)}
                  </pre>
                </details>
              </div>
            ) : (
              <div className="flex items-center justify-center h-full">
                <div className="text-center opacity-30">
                  <Terminal className="w-8 h-8 mx-auto mb-2" />
                  <p className="text-xs">{t("点击「执行评估」测试策略", "Click Evaluate to test the policy")}</p>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
