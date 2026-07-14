/**
 * Agent Builder — Agent配置表单页
 * 表单字段: Agent名称 + Model选择 + SystemPrompt + Tool多选 + Knowledge绑定 + Temperature滑块
 * 支持新建(POST)和编辑(PUT)两种模式
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Cpu, Save, Trash2, Play, ArrowLeft, Plus, AlertTriangle,
  CheckCircle2, Loader2, Wrench, Brain, Thermometer, BookOpen, Bot,
} from "lucide-react";
import {
  AgentConfig, ToolInfo, KnowledgeBase,
  fetchAgents, fetchAgent, createAgent, updateAgent, deleteAgent,
  fetchTools, fetchAgentTools, bindTool,
  fetchModels, fetchKnowledgeBases,
} from "../services/agentConfig";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import LoadingSkeleton from "../components/common/LoadingSkeleton";

// ── Helpers ─────────────────────────────────────────────────

function g(locale: string, en: string, zh: string): string {
  return locale === "zh" ? zh : en;
}

// ── Component ───────────────────────────────────────────────

export default function AgentBuilder() {
  const navigate = useNavigate();
  const { agentId } = useParams<{ agentId?: string }>();
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const isEdit = !!agentId;

  // Form state
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [model, setModel] = useState("");
  const [systemPrompt, setSystemPrompt] = useState("");
  const [temperature, setTemperature] = useState(0.7);
  const [selectedToolIds, setSelectedToolIds] = useState<string[]>([]);
  const [knowledgeBaseId, setKnowledgeBaseId] = useState("");

  // Data
  const [models, setModels] = useState<string[]>([]);
  const [tools, setTools] = useState<ToolInfo[]>([]);
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);

  // UI state
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // ── Load data ──────────────────────────────────────────────

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const [modelsData, toolsData, kbsData] = await Promise.all([
        fetchModels(),
        fetchTools(),
        fetchKnowledgeBases(),
      ]);
      setModels(modelsData);
      setTools(toolsData);
      setKnowledgeBases(kbsData);

      // If editing, load agent data
      if (isEdit && agentId) {
        const [agent, agentTools] = await Promise.all([
          fetchAgent(agentId),
          fetchAgentTools(agentId).catch(() => [] as ToolInfo[]),
        ]);
        setName(agent.name || "");
        setDescription(agent.description || "");
        setModel(agent.model || "");
        setSystemPrompt(agent.systemPrompt || "");
        setTemperature(agent.temperature ?? 0.7);
        setKnowledgeBaseId(agent.knowledgeBaseId || "");
        setSelectedToolIds(
          agent.toolIds?.length
            ? agent.toolIds
            : agentTools.map((t) => t.id)
        );
      }
    } catch (err: any) {
      setError(err.message || "Failed to load data");
    } finally {
      setLoading(false);
    }
  }, [isEdit, agentId]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // ── Tool checkbox toggle ───────────────────────────────────

  const toggleTool = (toolId: string) => {
    setSelectedToolIds((prev) =>
      prev.includes(toolId)
        ? prev.filter((id) => id !== toolId)
        : [...prev, toolId]
    );
  };

  // ── Save / Create ──────────────────────────────────────────

  const handleSave = async () => {
    if (!name.trim()) {
      setError(g(locale, "Agent name is required", "Agent名称不能为空"));
      return;
    }

    setSaving(true);
    setError(null);
    setSuccessMsg(null);

    const payload = {
      name: name.trim(),
      description: description.trim(),
      model,
      systemPrompt,
      temperature,
      toolIds: selectedToolIds,
      knowledgeBaseId: knowledgeBaseId || undefined,
      maxTokens: 4096,
    };

    try {
      let saved: AgentConfig;
      if (isEdit && agentId) {
        saved = await updateAgent(agentId, payload);
        // Re-bind tools
        await bindTool(agentId, selectedToolIds).catch(() => {});
      } else {
        saved = await createAgent(payload as any);
        // Bind tools to newly created agent
        if (selectedToolIds.length > 0) {
          await bindTool(saved.id, selectedToolIds).catch(() => {});
        }
      }

      setSuccessMsg(
        isEdit
          ? g(locale, "Agent updated successfully!", "Agent更新成功！")
          : g(locale, "Agent created successfully!", "Agent创建成功！")
      );

      // Navigate to edit mode if newly created
      if (!isEdit) {
        setTimeout(() => navigate(`/agent-builder/${saved.id}`, { replace: true }), 800);
      }
    } catch (err: any) {
      setError(err.message || g(locale, "Save failed", "保存失败"));
    } finally {
      setSaving(false);
    }
  };

  // ── Delete ─────────────────────────────────────────────────

  const handleDelete = async () => {
    if (!isEdit || !agentId) return;
    if (!confirm(g(locale, "Delete this agent?", "确定删除此Agent？"))) return;

    setDeleting(true);
    setError(null);
    try {
      await deleteAgent(agentId);
      navigate("/agent-builder", { replace: true });
    } catch (err: any) {
      setError(err.message || g(locale, "Delete failed", "删除失败"));
      setDeleting(false);
    }
  };

  // ── Test navigation ────────────────────────────────────────

  const handleTest = () => {
    const targetId = isEdit ? agentId : undefined;
    if (targetId) {
      navigate(`/agent-test/${targetId}`);
    }
  };

  // ── Render ─────────────────────────────────────────────────

  if (loading) {
    return (
      <div className="flex-1 bg-slate-50 p-6 animate-fade-in">
        <LoadingSkeleton variant="card" rows={3} />
      </div>
    );
  }

  return (
    <div className="flex-1 bg-slate-50 text-slate-800 flex flex-col h-full font-sans overflow-hidden animate-fade-in w-full">
      {/* Page header */}
      <div className="bg-white border-b border-slate-200 p-3 sm:p-5 shrink-0 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-slate-800 flex items-center gap-2">
            <Cpu className="text-indigo-600 w-5 h-5 shrink-0" />
            {isEdit
              ? g(locale, "Edit Agent", "编辑Agent")
              : g(locale, "Create Agent", "创建Agent")}
          </h1>
          <p className="text-xs text-slate-500 mt-1 max-w-2xl leading-relaxed">
            {isEdit
              ? g(locale, "Modify agent configuration and tools", "修改Agent配置与工具绑定")
              : g(locale, "Configure a new AI agent with model, tools, and knowledge", "配置新的AI Agent：选择模型、绑定工具与知识库")}
          </p>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <button
            onClick={() => navigate("/agent_studio")}
            className="bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-lg px-3 py-2 text-xs font-semibold flex items-center gap-1.5 cursor-pointer transition"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            {g(locale, "Back to Studio", "返回工坊")}
          </button>

          {isEdit && (
            <>
              <button
                onClick={handleTest}
                disabled={!agentId}
                className="bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white rounded-lg px-3 py-2 text-xs font-semibold flex items-center gap-1.5 cursor-pointer transition shadow-xs"
              >
                <Play className="w-3.5 h-3.5" />
                {g(locale, "Test Agent", "测试Agent")}
              </button>
              <button
                onClick={handleDelete}
                disabled={deleting}
                className="bg-red-50 hover:bg-red-100 text-red-600 border border-red-200 rounded-lg px-3 py-2 text-xs font-semibold flex items-center gap-1.5 cursor-pointer transition"
              >
                {deleting ? (
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  <Trash2 className="w-3.5 h-3.5" />
                )}
                {g(locale, "Delete", "删除")}
              </button>
            </>
          )}
        </div>
      </div>

      {/* Main form area */}
      <div className="flex-1 overflow-y-auto p-4 sm:p-6">
        <div className="max-w-3xl mx-auto space-y-5">
          {/* Success / Error messages */}
          {successMsg && (
            <div className="flex items-center gap-2 bg-emerald-50 border border-emerald-200 text-emerald-700 rounded-lg px-4 py-3 text-xs font-semibold animate-fade-in">
              <CheckCircle2 className="w-4 h-4 text-emerald-500" />
              {successMsg}
            </div>
          )}
          {error && (
            <div className="flex items-center gap-2 bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 text-xs font-semibold animate-fade-in">
              <AlertTriangle className="w-4 h-4 text-red-500" />
              {error}
            </div>
          )}

          {/* ── Basic Info Card ───────────────────────────────── */}
          <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-4">
            <h2 className="text-sm font-bold text-slate-800 flex items-center gap-2">
              <Bot className="w-4 h-4 text-indigo-600" />
              {g(locale, "Basic Information", "基本信息")}
            </h2>

            {/* Agent Name */}
            <div>
              <label className="text-xs font-semibold text-slate-600 block mb-1.5">
                {g(locale, "Agent Name", "Agent名称")}
                <span className="text-red-500 ml-0.5">*</span>
              </label>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="w-full border border-slate-200 rounded-lg px-3.5 py-2.5 text-sm text-slate-700 placeholder-slate-400 outline-hidden focus:border-indigo-400 focus:ring-1 focus:ring-indigo-100 transition"
                placeholder={g(locale, "e.g. Data Analyst Agent", "如: 数据分析Agent")}
              />
            </div>

            {/* Description */}
            <div>
              <label className="text-xs font-semibold text-slate-600 block mb-1.5">
                {g(locale, "Description", "描述")}
              </label>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={2}
                className="w-full border border-slate-200 rounded-lg px-3.5 py-2.5 text-sm text-slate-700 placeholder-slate-400 outline-hidden focus:border-indigo-400 focus:ring-1 focus:ring-indigo-100 transition resize-none"
                placeholder={g(locale, "What does this agent do?", "描述此Agent的用途")}
              />
            </div>

            {/* Model Selection */}
            <div>
              <label className="text-xs font-semibold text-slate-600 block mb-1.5">
                {g(locale, "Model", "模型选择")}
                <span className="text-red-500 ml-0.5">*</span>
              </label>
              <select
                value={model}
                onChange={(e) => setModel(e.target.value)}
                className="w-full border border-slate-200 rounded-lg px-3.5 py-2.5 text-sm text-slate-700 bg-white outline-hidden focus:border-indigo-400 focus:ring-1 focus:ring-indigo-100 transition"
              >
                <option value="">{g(locale, "Select a model...", "请选择模型...")}</option>
                {models.map((m) => (
                  <option key={m} value={m}>
                    {m}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* ── System Prompt Card ────────────────────────────── */}
          <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-4">
            <h2 className="text-sm font-bold text-slate-800 flex items-center gap-2">
              <Brain className="w-4 h-4 text-purple-600" />
              {g(locale, "System Prompt", "系统提示词")}
            </h2>
            <textarea
              value={systemPrompt}
              onChange={(e) => setSystemPrompt(e.target.value)}
              rows={8}
              className="w-full border border-slate-200 rounded-lg px-3.5 py-2.5 text-sm text-slate-700 placeholder-slate-400 outline-hidden focus:border-indigo-400 focus:ring-1 focus:ring-indigo-100 transition font-mono resize-y"
              placeholder={g(
                locale,
                "You are a helpful AI assistant...",
                "你是一个专业的AI助手..."
              )}
            />
          </div>

          {/* ── Tools Card ────────────────────────────────────── */}
          <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-4">
            <h2 className="text-sm font-bold text-slate-800 flex items-center gap-2">
              <Wrench className="w-4 h-4 text-amber-600" />
              {g(locale, "Tools", "工具绑定")}
              <span className="text-[10px] font-normal text-slate-400 ml-1">
                ({selectedToolIds.length} {g(locale, "selected", "已选")})
              </span>
            </h2>

            {tools.length === 0 ? (
              <p className="text-xs text-slate-400 italic py-3">
                {g(locale, "No tools available. Add tools from the backend first.", "暂无可用工具，请先在后台注册工具。")}
              </p>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 max-h-64 overflow-y-auto scrollbar-thin pr-1">
                {tools.map((tool) => {
                  const isSelected = selectedToolIds.includes(tool.id);
                  return (
                    <label
                      key={tool.id}
                      className={`flex items-start gap-2.5 p-3 rounded-lg border cursor-pointer transition-all ${
                        isSelected
                          ? "bg-indigo-50 border-indigo-300 shadow-sm"
                          : "bg-slate-50 border-slate-200 hover:border-slate-300 hover:bg-white"
                      }`}
                    >
                      <input
                        type="checkbox"
                        checked={isSelected}
                        onChange={() => toggleTool(tool.id)}
                        className="mt-0.5 shrink-0 accent-indigo-600"
                      />
                      <div className="flex-1 min-w-0">
                        <span className="text-xs font-bold text-slate-700 block leading-tight">
                          {tool.name}
                        </span>
                        {tool.description && (
                          <p className="text-[10px] text-slate-450 leading-relaxed mt-0.5 line-clamp-2">
                            {tool.description}
                          </p>
                        )}
                        {tool.category && (
                          <span className="inline-block text-[9px] font-mono text-slate-400 bg-white/70 border border-slate-200 rounded px-1.5 py-0.5 mt-1">
                            {tool.category}
                          </span>
                        )}
                      </div>
                    </label>
                  );
                })}
              </div>
            )}
          </div>

          {/* ── Knowledge Base + Temperature Card ─────────────── */}
          <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-5">
            {/* Knowledge Base */}
            <div>
              <h2 className="text-sm font-bold text-slate-800 flex items-center gap-2 mb-3">
                <BookOpen className="w-4 h-4 text-cyan-600" />
                {g(locale, "Knowledge Base", "知识库绑定")}
              </h2>
              {knowledgeBases.length === 0 ? (
                <p className="text-xs text-slate-400 italic">
                  {g(locale, "No knowledge bases available.", "暂无可用知识库。")}
                </p>
              ) : (
                <select
                  value={knowledgeBaseId}
                  onChange={(e) => setKnowledgeBaseId(e.target.value)}
                  className="w-full border border-slate-200 rounded-lg px-3.5 py-2.5 text-sm text-slate-700 bg-white outline-hidden focus:border-indigo-400 focus:ring-1 focus:ring-indigo-100 transition"
                >
                  <option value="">
                    {g(locale, "None (no knowledge base)", "无（不绑定知识库）")}
                  </option>
                  {knowledgeBases.map((kb) => (
                    <option key={kb.id} value={kb.id}>
                      {kb.name}
                      {kb.documentCount !== undefined ? ` (${kb.documentCount} docs)` : ""}
                    </option>
                  ))}
                </select>
              )}
            </div>

            {/* Temperature Slider */}
            <div>
              <h2 className="text-sm font-bold text-slate-800 flex items-center gap-2 mb-3">
                <Thermometer className="w-4 h-4 text-orange-500" />
                {g(locale, "Temperature", "温度参数")}
              </h2>
              <div className="flex items-center gap-4">
                <input
                  type="range"
                  min="0"
                  max="2"
                  step="0.05"
                  value={temperature}
                  onChange={(e) => setTemperature(parseFloat(e.target.value))}
                  className="flex-1 accent-indigo-600 h-2 rounded-lg appearance-none bg-slate-200 cursor-pointer"
                />
                <span className="text-sm font-bold font-mono text-indigo-600 bg-indigo-50 border border-indigo-200 rounded-lg px-3 py-1.5 min-w-[4rem] text-center">
                  {temperature.toFixed(2)}
                </span>
              </div>
              <div className="flex justify-between text-[10px] text-slate-400 mt-1.5 font-mono">
                <span>{g(locale, "Precise", "精确")} (0)</span>
                <span>{g(locale, "Balanced", "平衡")} (1.0)</span>
                <span>{g(locale, "Creative", "创意")} (2.0)</span>
              </div>
            </div>
          </div>

          {/* ── Save Button ───────────────────────────────────── */}
          <div className="flex justify-end pt-2 pb-6">
            <button
              onClick={handleSave}
              disabled={saving || !name.trim()}
              className="bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white rounded-lg px-6 py-2.5 text-sm font-semibold flex items-center gap-2 cursor-pointer transition shadow-xs"
            >
              {saving ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <Save className="w-4 h-4" />
              )}
              {isEdit
                ? g(locale, "Save Changes", "保存修改")
                : g(locale, "Create Agent", "创建Agent")}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
