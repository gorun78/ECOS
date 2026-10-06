/**
 * Data Masking Demo — ECOS P1-5
 * @license Apache-2.0
 */

import React, { useState, useEffect } from "react";
import {
  EyeOff, Shield, Play, Copy, RefreshCw, Loader2, AlertTriangle, CheckCircle2
} from "lucide-react";
import { fetchDataMaskingDemo, applyDataMasking, type DataMaskingDemoData, type DataMaskingApplyResult } from "../api";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";

export default function DataMaskingDemo() {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const [demoData, setDemoData] = useState<DataMaskingDemoData | null>(null);
  const [demoLoading, setDemoLoading] = useState(false);
  const [demoError, setDemoError] = useState<string | null>(null);

  const [inputText, setInputText] = useState("");
  const [selectedRules, setSelectedRules] = useState<string[]>(["phone", "email", "id_card"]);
  const [applyResult, setApplyResult] = useState<DataMaskingApplyResult | null>(null);
  const [applyLoading, setApplyLoading] = useState(false);
  const [applyError, setApplyError] = useState<string | null>(null);

  const availableRules = [
    { value: "phone", labelZh: "手机号", labelEn: "Phone" },
    { value: "email", labelZh: "邮箱", labelEn: "Email" },
    { value: "id_card", labelZh: "身份证", labelEn: "ID Card" },
    { value: "bank_card", labelZh: "银行卡", labelEn: "Bank Card" },
    { value: "name", labelZh: "姓名", labelEn: "Name" },
    { value: "address", labelZh: "地址", labelEn: "Address" },
  ];

  const loadDemo = async () => {
    setDemoLoading(true);
    setDemoError(null);
    try {
      const data = await fetchDataMaskingDemo();
      setDemoData(data);
    } catch (e: any) {
      setDemoError(e.message || "Failed to load demo data");
    } finally {
      setDemoLoading(false);
    }
  };

  useEffect(() => {
    loadDemo();
  }, []);

  const handleApplyMasking = async () => {
    if (!inputText.trim()) return;
    setApplyLoading(true);
    setApplyError(null);
    try {
      const result = await applyDataMasking({ text: inputText, rules: selectedRules });
      setApplyResult(result);
    } catch (e: any) {
      setApplyError(e.message || "Masking failed");
    } finally {
      setApplyLoading(false);
    }
  };

  const toggleRule = (rule: string) => {
    setSelectedRules((prev) =>
      prev.includes(rule) ? prev.filter((r) => r !== rule) : [...prev, rule]
    );
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text).catch(() => {});
  };

  const L = (zh: string, en: string) => locale === "zh" ? zh : en;

  return (
    <div className={`flex-1 overflow-y-auto p-5 flex flex-col h-full font-sans ${styles.appBg} ${styles.appText}`}>
      {/* Header */}
      <div className="flex justify-between items-center mb-5 shrink-0">
        <div>
          <h1 className={`text-xl font-bold tracking-tight flex items-center gap-2 ${styles.cardText}`}>
            <EyeOff className="text-purple-500 w-5 h-5 shrink-0" />
            {L("数据脱敏演示", "Data Masking Demo")}
          </h1>
          <p className={`text-xs mt-0.5 ${styles.cardTextMuted}`}>
            {L("敏感数据自动识别与脱敏处理", "Automatic sensitive data detection & masking")}
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5 flex-1 min-h-0">
        {/* Left Column: Demo Cards */}
        <div className="space-y-5 overflow-y-auto pr-1">
          {/* Demo Data Cards */}
          <div className={`rounded-md p-4 border ${styles.cardBg} ${styles.cardBorder}`}>
            <div className="flex items-center justify-between mb-3">
              <h3 className={`text-xs font-bold uppercase tracking-wider flex items-center gap-1.5 ${styles.cardText}`}>
                <Shield className="w-3.5 h-3.5 text-blue-400" />
                {L("脱敏对比示例", "Masking Comparison Examples")}
              </h3>
              <button
                onClick={loadDemo}
                className={`p-1.5 rounded transition-colors ${styles.cardTextMuted} hover:${styles.cardText} hover:${styles.inputBg}`}
                title={L("刷新", "Refresh")}
              >
                <RefreshCw className={`w-3.5 h-3.5 ${demoLoading ? "animate-spin" : ""}`} />
              </button>
            </div>

            {demoError && (
              <div className="flex items-center gap-2 mb-3 p-2.5 bg-red-900/30 border border-red-800/50 rounded-md text-xs text-red-300">
                <AlertTriangle className="w-3.5 h-3.5 shrink-0" />
                {demoError}
              </div>
            )}

            {demoLoading ? (
              <div className={`flex items-center justify-center h-32 ${styles.cardTextMuted}`}>
                <Loader2 className="w-4 h-4 animate-spin mr-2" />
                {L("加载中...", "Loading...")}
              </div>
            ) : demoData && demoData.comparisons && demoData.comparisons.length > 0 ? (
              <div className="space-y-3">
                {demoData.comparisons.map((item: any, i: number) => (
                  <div key={i} className={`rounded-md p-3 border bg-black/20 ${styles.cardBorder}`}>
                    <div className="flex items-center justify-between mb-2">
                      <span className={`text-[10px] uppercase tracking-wider font-medium ${styles.cardTextMuted}`}>
                        {item.type || item.rule || `#${i + 1}`}
                      </span>
                      <span className={`text-[9px] font-mono ${styles.cardTextMuted}`}>
                        {item.rule || ""}
                      </span>
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                      {/* Original */}
                      <div>
                        <span className={`text-[9px] block mb-1 ${styles.cardTextMuted}`}>
                          {L("原始数据", "Original")}
                        </span>
                        <div className={`border rounded px-2.5 py-2 text-xs font-mono break-all ${styles.inputBg} ${styles.cardBorder} ${styles.cardText}`}>
                          {item.original || item.raw || "—"}
                        </div>
                      </div>
                      {/* Masked */}
                      <div>
                        <span className="text-[9px] text-purple-400 block mb-1">
                          {L("脱敏后", "Masked")}
                        </span>
                        <div className="bg-purple-900/15 border border-purple-800/30 rounded px-2.5 py-2 text-xs text-purple-300 font-mono break-all">
                          {item.masked || "—"}
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className={`flex flex-col items-center justify-center h-32 text-xs gap-2 ${styles.cardTextMuted}`}>
                <EyeOff className="w-8 h-8 opacity-30" />
                {L("暂无脱敏示例数据", "No masking demo data available")}
              </div>
            )}
          </div>
        </div>

        {/* Right Column: Apply Masking */}
        <div className="space-y-5 overflow-y-auto">
          <div className={`rounded-md p-4 border ${styles.cardBg} ${styles.cardBorder}`}>
            <h3 className={`text-xs font-bold uppercase tracking-wider flex items-center gap-1.5 mb-3 ${styles.cardText}`}>
              <EyeOff className="w-3.5 h-3.5 text-purple-400" />
              {L("应用脱敏", "Apply Data Masking")}
            </h3>

            {/* Text Input */}
            <div className="mb-4">
              <label className={`block text-[10px] mb-1.5 font-medium ${styles.cardTextMuted}`}>
                {L("输入文本", "Input Text")} *
              </label>
              <textarea
                value={inputText}
                onChange={(e) => setInputText(e.target.value)}
                rows={5}
                className={`w-full px-3 py-2.5 rounded-md text-xs font-mono focus:outline-none focus:border-purple-500/60 resize-none ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
                placeholder={L(
                  "例如: 张三的手机号是 13812345678，邮箱是 zhangsan@example.com",
                  "e.g. John's phone is 13812345678 and email is john@example.com"
                )}
              />
            </div>

            {/* Rules Selector */}
            <div className="mb-4">
              <label className={`block text-[10px] mb-2 font-medium ${styles.cardTextMuted}`}>
                {L("脱敏规则", "Masking Rules")}
              </label>
              <div className="flex flex-wrap gap-1.5">
                {availableRules.map((rule) => {
                  const isSelected = selectedRules.includes(rule.value);
                  return (
                    <button
                      key={rule.value}
                      onClick={() => toggleRule(rule.value)}
                      className={`px-2.5 py-1 rounded-full text-[10px] font-medium border transition-colors ${
                        isSelected
                          ? "bg-purple-600/20 text-purple-300 border-purple-600/50"
                          : `${styles.inputBg} ${styles.cardTextMuted} ${styles.inputBorder} hover:border-purple-500/30`
                      }`}
                    >
                      {locale === "zh" ? rule.labelZh : rule.labelEn}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Apply Button */}
            <button
              onClick={handleApplyMasking}
              disabled={applyLoading || !inputText.trim() || selectedRules.length === 0}
              className="w-full flex items-center justify-center gap-1.5 px-4 py-2.5 bg-purple-600 hover:bg-purple-700 disabled:opacity-40 disabled:cursor-not-allowed text-white text-xs font-medium rounded-md transition-colors"
            >
              {applyLoading ? (
                <>
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  {L("处理中...", "Processing...")}
                </>
              ) : (
                <>
                  <Play className="w-3.5 h-3.5" />
                  {L("应用脱敏", "Apply Masking")}
                </>
              )}
            </button>

            {/* Error */}
            {applyError && (
              <div className="flex items-center gap-2 mt-3 p-2.5 bg-red-900/30 border border-red-800/50 rounded-md text-xs text-red-300">
                <AlertTriangle className="w-3.5 h-3.5 shrink-0" />
                {applyError}
              </div>
            )}

            {/* Result */}
            {applyResult && (
              <div className="mt-4 space-y-3">
                <div className="flex items-center gap-2">
                  <CheckCircle2 className="w-3.5 h-3.5 text-green-400" />
                  <span className="text-xs text-green-400 font-medium">
                    {L("脱敏完成", "Masking Complete")}
                  </span>
                </div>

                {/* Result Cards: Original vs Masked */}
                <div className="grid grid-cols-1 gap-3">
                  <div>
                    <div className="flex items-center justify-between mb-1">
                      <span className={`text-[9px] ${styles.cardTextMuted}`}>
                        {L("原始文本", "Original Text")}
                      </span>
                      <button
                        onClick={() => copyToClipboard(applyResult.original || inputText)}
                        className={`text-[9px] flex items-center gap-1 ${styles.cardTextMuted} hover:${styles.cardText}`}
                      >
                        <Copy className="w-3 h-3" />
                        {L("复制", "Copy")}
                      </button>
                    </div>
                    <div className={`rounded-md px-3 py-2.5 text-xs font-mono whitespace-pre-wrap break-all border bg-black/20 ${styles.cardBorder} ${styles.cardText}`}>
                      {applyResult.original || inputText}
                    </div>
                  </div>

                  <div>
                    <div className="flex items-center justify-between mb-1">
                      <span className="text-[9px] text-purple-400">
                        {L("脱敏结果", "Masked Result")}
                      </span>
                      <button
                        onClick={() => copyToClipboard(applyResult.masked || "")}
                        className={`text-[9px] flex items-center gap-1 ${styles.cardTextMuted} hover:${styles.cardText}`}
                      >
                        <Copy className="w-3 h-3" />
                        {L("复制", "Copy")}
                      </button>
                    </div>
                    <div className="bg-purple-900/10 border border-purple-800/30 rounded-md px-3 py-2.5 text-xs text-purple-300 font-mono whitespace-pre-wrap break-all">
                      {applyResult.masked || "—"}
                    </div>
                  </div>
                </div>

                {/* Detection Details */}
                {applyResult.detections && applyResult.detections.length > 0 && (
                  <div>
                    <span className={`text-[9px] block mb-1.5 ${styles.cardTextMuted}`}>
                      {L("检测详情", "Detection Details")}
                    </span>
                    <div className="space-y-1">
                      {applyResult.detections.map((det: any, i: number) => (
                        <div key={i} className={`flex items-center justify-between rounded px-2.5 py-1.5 text-[10px] border bg-black/20 ${styles.cardBorder}`}>
                          <span className={`font-mono ${styles.cardTextMuted}`}>{det.type || det.rule}</span>
                          <span className={styles.cardTextMuted}>{det.count !== undefined ? `${det.count} ${L("处", "found")}` : ""}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
