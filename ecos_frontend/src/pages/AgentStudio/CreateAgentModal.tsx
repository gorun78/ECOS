/**
 * CreateAgentModal — Agent creation modal for AgentStudio.
 * @license Apache-2.0
 */

import { Bot, X } from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { g } from "./helpers";

interface NewAgentForm {
  name: string;
  role: string;
  goal: string;
  systemPrompt: string;
}

interface CreateAgentModalProps {
  form: NewAgentForm;
  onChange: (updater: (prev: NewAgentForm) => NewAgentForm) => void;
  onConfirm: () => void;
  onClose: () => void;
}

export default function CreateAgentModal({ form, onChange, onConfirm, onClose }: CreateAgentModalProps) {
  const { t, locale } = useLanguage();

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-md mx-4 p-6 border border-slate-200">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-bold text-slate-800 flex items-center gap-2">
            <Bot className="w-5 h-5 text-indigo-600" />
            {t("agent.create.title")}
          </h2>
          <button onClick={onClose} className="p-1 hover:bg-slate-100 rounded">
            <X className="w-5 h-5 text-slate-400" />
          </button>
        </div>
        <div className="space-y-3">
          <div>
            <label className="text-xs font-semibold text-slate-600 block mb-1">{t("agent.create.name")}</label>
            <input type="text" value={form.name}
              onChange={e => onChange(prev => ({...prev, name: e.target.value}))}
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs outline-hidden focus:border-indigo-400"
              placeholder={g(locale, "e.g. Data Analyst Agent", "如: 数据分析Agent")} />
          </div>
          <div>
            <label className="text-xs font-semibold text-slate-600 block mb-1">{t("agent.create.role")}</label>
            <input type="text" value={form.role}
              onChange={e => onChange(prev => ({...prev, role: e.target.value}))}
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs outline-hidden focus:border-indigo-400"
              placeholder={g(locale, "e.g. Data Analyst & Visualizer", "如: 数据分析与可视化专家")} />
          </div>
          <div>
            <label className="text-xs font-semibold text-slate-600 block mb-1">{t("agent.create.goal")}</label>
            <textarea value={form.goal} rows={2}
              onChange={e => onChange(prev => ({...prev, goal: e.target.value}))}
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs outline-hidden focus:border-indigo-400 resize-none"
              placeholder={g(locale, "What should this agent accomplish?", "该 Agent 的目标是什么？")} />
          </div>
          <div>
            <label className="text-xs font-semibold text-slate-600 block mb-1">{t("agent.create.prompt")}</label>
            <textarea value={form.systemPrompt} rows={3}
              onChange={e => onChange(prev => ({...prev, systemPrompt: e.target.value}))}
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-xs outline-hidden focus:border-indigo-400 resize-none font-mono"
              placeholder={g(locale, "System prompt for this agent...", "系统提示词...")} />
          </div>
        </div>
        <div className="flex justify-end gap-2 mt-5">
          <button onClick={onClose}
            className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-lg transition">
            {g(locale, "Cancel", "取消")}
          </button>
          <button onClick={onConfirm} disabled={!form.name.trim()}
            className="px-4 py-2 text-xs font-semibold bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white rounded-lg transition">
            {t("agent.create.confirm")}
          </button>
        </div>
      </div>
    </div>
  );
}
