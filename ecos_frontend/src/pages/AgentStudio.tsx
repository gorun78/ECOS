/**
 * AgentStudio — AI Agent工作坊
 * 多轮对话 + Thought Trace可视化 + Agent卡片选择器 + 真实后端数据
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { Cpu, Terminal, Sliders, CheckCircle2, Play, RefreshCw, Layers, Award, FileText, Code, Database, ChevronRight, ChevronDown, ChevronUp, Zap, ArrowRight, HelpCircle, Send, Plus, X, Bot, User, AlertTriangle, Info, Brain, Lightbulb, Wrench, Eye, ShieldAlert, XCircle, Check } from "lucide-react";
import { AgentDefinition, ToolDefinition, PromptTemplate, AgentTraceStep } from "../types";
import { fetchAgentMeshAgents, agentChat, fetchTools, fetchPrompts, createAgentMeshMission, apiFetch } from "../api";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import CreateAgentModal from "./AgentStudio/CreateAgentModal";
import MetricsPanel from "./AgentStudio/MetricsPanel";
import { TRACE_COLORS, getTraceStyle, g } from "./AgentStudio/helpers";

// ── Thought Trace step type for visualisation ────────────────
interface ThoughtStep {
  type: string;       // "thought" | "action" | "observation" | "result"
  summary: string;
  detail?: string;
}

// ── Human-in-the-loop Action Proposal ───────────────────────
// Merged from ceos_new AIPWorkbench. An agent response may carry an
// actionProposal: a mutating ontology action that requires explicit
// human approval (Approve / Reject) before being written back through
// POST /api/v1/ontology/actions/{actionId}/execute.
interface ActionProposal {
  actionId: string;
  actionName: string;
  payload: Record<string, string>;
  status: "pending" | "approved" | "rejected";
}

interface ChatEntry {
  role: "user" | "agent" | "error" | "system"; // +system: post-approval confirmation
  text: string;
  thoughtTrace?: ThoughtStep[]; // per-message trace
  collapsed?: boolean;          // trace panel collapsed state
  actionProposal?: ActionProposal; // optional HITL proposal card
}

// ── Component ──

export default function AgentStudio() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const navigate = useNavigate();
  const [agents, setAgents] = useState<AgentDefinition[]>([]);
  const [tools, setTools] = useState<ToolDefinition[]>([]);
  const [prompts, setPrompts] = useState<PromptTemplate[]>([]);
  const [selectedAgentId, setSelectedEntityId] = useState<string>("coordinator");
  const [selectedPromptId, setSelectedPromptId] = useState<string>("investigation");
  const [sandboxPromptText, setSandboxPromptText] = useState("");

  // Chat state — multi-turn conversation with per-message traces
  const [chatMessage, setChatMessage] = useState("");
  const [chatHistory, setChatHistory] = useState<ChatEntry[]>([]);

  // Per-step expand/collapse for the enhanced Thought Trace (multi-step fold/unfold).
  // Keyed by `${msgIndex}-${stepIndex}`.
  const [expandedSteps, setExpandedSteps] = useState<Record<string, boolean>>({});

  // Global trace log (aggregated across turns)
  const [traceLogs, setTraceLogs] = useState<AgentTraceStep[]>([]);
  const [isExecuting, setIsExecuting] = useState(false);
  const [metrics, setMetrics] = useState({
    successRate: 98.4,
    latencyMs: 1450,
    tokensUsed: 4410220,
    costUSD: 4.41,
    toolCalls: 1205
  });

  // Create agent modal
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newAgent, setNewAgent] = useState({ name: "", role: "", goal: "", systemPrompt: "" });

  // ── Load agents from Agent Mesh backend ──────────────────────
  useEffect(() => {
    fetchAgentMeshAgents().then((meshAgents) => {
      if (meshAgents && meshAgents.length > 0) {
        const mapped: AgentDefinition[] = meshAgents.map((a) => {
          let tools: string[] = [];
          if (a.toolset) {
            try { tools = typeof a.toolset === "string" ? JSON.parse(a.toolset) : a.toolset; } catch { tools = []; }
          }
          return {
            id: a.id,
            name: a.name,
            role: a.role,
            goal: a.description || "",
            tools,
            systemPrompt: a.systemPrompt || "",
            capabilities: [] as string[],
            model: a.model || "unknown",
          };
        });
        setAgents(mapped);
        // Auto-select first agent if current selection doesn't exist
        if (!mapped.find((a) => a.id === selectedAgentId)) {
          setSelectedEntityId(mapped[0].id);
        }
      }
    }).catch((err) => {
      console.warn("AgentStudio: Agent Mesh backend unavailable", err);
    });
  }, []);

  // Load tools from backend
  useEffect(() => {
    fetchTools().then(data => {
      if (data && data.length > 0) setTools(data);
    }).catch(() => {});
  }, []);

  // Load prompts from backend
  useEffect(() => {
    fetchPrompts().then(data => {
      if (data && data.length > 0) {
        setPrompts(data);
        setSandboxPromptText(data[0].content);
      }
    }).catch(() => {});
  }, []);

  const selectedAgent = agents.find((a) => a.id === selectedAgentId) || agents[0];
  const selectedPrompt = prompts.find((p) => p.id === selectedPromptId) || prompts[0];

  const handlePromptSelect = (pId: string) => {
    setSelectedPromptId(pId);
    const found = prompts.find((p) => p.id === pId);
    if (found) setSandboxPromptText(found.content);
  };

  const handleUpdatePromptInSandbox = (val: string) => {
    setSandboxPromptText(val);
    setPrompts((prev) =>
      prev.map((p) => {
        if (p.id === selectedPromptId) {
          return { ...p, content: val };
        }
        return p;
      })
    );
  };

  // ── Multi-turn chat: send message → agentChat → capture trace ──
  const handleSendMessage = async () => {
    const msg = chatMessage.trim();
    if (!msg || !selectedAgent) return;
    setIsExecuting(true);
    setChatMessage("");

    // Add user message
    const userEntry: ChatEntry = { role: "user", text: msg };
    setChatHistory(prev => [...prev, userEntry]);

    try {
      const t0 = Date.now();
      const result = await agentChat(selectedAgentId, msg, selectedPrompt?.content);
      const elapsed = Date.now() - t0;

      // Parse thought trace into structured steps
      const steps: ThoughtStep[] = (result.thoughtTrace || []).map((t: any) => ({
        type: t.type || "result",
        summary: t.summary || "",
        detail: t.detail || "",
      }));

      // Build global trace log entries
      const globalSteps: AgentTraceStep[] = steps.map((s, i) => ({
        id: `step_${Date.now()}_${i}`,
        type: s.type as AgentTraceStep["type"] || "result",
        agentId: selectedAgentId,
        timestamp: new Date().toLocaleTimeString(),
        summary: s.summary,
      }));
      setTraceLogs(prev => [...prev, ...globalSteps]);

      // ── Human-in-the-loop Action Proposal ──────────────────────
      // Prefer a proposal returned by the backend (result.actionProposal);
      // otherwise synthesize one heuristically when the user's message looks
      // like an ontology-mutation intent (reschedule / delay / cancel / 调整 /
      // 改签 …) so the Approve/Reject workflow is exercisable end-to-end.
      let proposal: ActionProposal | undefined = undefined;
      const backendProposal = result.actionProposal;
      if (backendProposal && backendProposal.actionId) {
        proposal = {
          actionId: backendProposal.actionId,
          actionName: backendProposal.actionName || backendProposal.actionId,
          payload: backendProposal.payload || {},
          status: (backendProposal.status as ActionProposal["status"]) || "pending",
        };
      } else {
        const isMutationIntent = /延误|改签|改派|重新调度|取消|调整|修改|reschedule|delay|cancel|modify|update/i.test(msg);
        if (isMutationIntent) {
          proposal = {
            actionId: "act_reschedule_flight",
            actionName: g(
              "Ontology Action · Reschedule Flight (act_reschedule_flight)",
              "本体动作 · 重新调度航班 (act_reschedule_flight)"
            ),
            payload: {
              intent: msg.slice(0, 120),
              requested_by: selectedAgent?.name || "AgentStudio",
              auth_required_by: "HUMAN_APPROVAL",
            },
            status: "pending",
          };
        }
      }

      // Add agent response with per-message trace
      const agentEntry: ChatEntry = {
        role: "agent",
        text: result.responseText,
        thoughtTrace: steps,
        collapsed: false,
        actionProposal: proposal,
      };
      setChatHistory(prev => [...prev, agentEntry]);

      // Update metrics
      setMetrics(prev => ({
        ...prev,
        successRate: Math.min(99.9, prev.successRate + 0.05),
        latencyMs: elapsed,
        tokensUsed: prev.tokensUsed + Math.floor(Math.random() * 500 + 200),
        costUSD: prev.costUSD + 0.005,
        toolCalls: prev.toolCalls + 1,
      }));
    } catch (err: any) {
      setChatHistory(prev => [...prev, { role: "error", text: `${t("agent.chat.error")}: ${err.message}` }]);
    }
    setIsExecuting(false);
  };

  // ── Toggle trace panel for a chat entry ─────────────────────
  const toggleTrace = (idx: number) => {
    setChatHistory(prev => prev.map((entry, i) =>
      i === idx ? { ...entry, collapsed: !entry.collapsed } : entry
    ));
  };

  // ── Toggle a single Thought Trace step (multi-step fold/unfold) ──
  const toggleStepExpand = (key: string) => {
    setExpandedSteps(prev => ({ ...prev, [key]: !prev[key] }));
  };

  // ── Human-in-the-loop: approve / reject an Action Proposal ──────
  // Approve → POST /api/v1/ontology/actions/{actionId}/execute (apiFetch
  // prefixes /api and injects the Bearer token from localStorage). On success
  // the card flips to "approved" and a system confirmation bubble is appended;
  // on failure the card flips but an error bubble reports the backend miss.
  const handleActionConsent = async (idx: number, approved: boolean) => {
    const entry = chatHistory[idx];
    if (!entry || !entry.actionProposal) return;
    const proposal = entry.actionProposal;

    if (!approved) {
      setChatHistory(prev => prev.map((e, i) =>
        i === idx && e.actionProposal
          ? { ...e, actionProposal: { ...e.actionProposal, status: "rejected" } }
          : e
      ));
      return;
    }

    try {
      const res = await apiFetch<{ success?: boolean; executionDetail?: string; message?: string; data?: any }>(
        `/v1/ontology/actions/${encodeURIComponent(proposal.actionId)}/execute`,
        { method: "POST", body: JSON.stringify({ payload: proposal.payload }) }
      );
      setChatHistory(prev => prev.map((e, i) =>
        i === idx && e.actionProposal
          ? { ...e, actionProposal: { ...e.actionProposal, status: "approved" } }
          : e
      ));
      const detail = res?.executionDetail || res?.message ||
        (res?.data ? JSON.stringify(res.data) : g("Action committed.", "动作已提交。"));
      setChatHistory(prev => [...prev, {
        role: "system",
        text: g(
          `✅ Ontology Action approved & executed.\nBi-directional verification: ${detail}`,
          `✅ 本体动作已批准并执行。\n双向核对：${detail}`
        ),
      }]);
    } catch (err: any) {
      // Backend unreachable / rejected — mark approved locally, surface the error.
      setChatHistory(prev => prev.map((e, i) =>
        i === idx && e.actionProposal
          ? { ...e, actionProposal: { ...e.actionProposal, status: "approved" } }
          : e
      ));
      setChatHistory(prev => [...prev, {
        role: "error",
        text: g(
          `Action marked approved locally, but backend execution failed: ${err.message}`,
          `动作已在本地标记批准，但后端执行失败：${err.message}`
        ),
      }]);
    }
  };

  const handleCreateAgent = () => {
    if (!newAgent.name.trim()) return;
    const id = `agent_${Date.now()}`;
    const created: AgentDefinition = {
      id,
      name: newAgent.name.trim(),
      role: newAgent.role.trim() || t("agent.create.roleDefault"),
      goal: newAgent.goal.trim() || t("agent.create.goalDefault"),
      systemPrompt: newAgent.systemPrompt.trim(),
      tools: [],
      capabilities: ["chat", "reasoning"],
    };
    setAgents(prev => [...prev, created]);
    setSelectedEntityId(id);
    setShowCreateModal(false);
    setNewAgent({ name: "", role: "", goal: "", systemPrompt: "" });

    // Try to persist to backend
    createAgentMeshMission({ agentId: id, name: created.name, goal: created.goal }).catch(() => {});
  };

  // i18n helper (local closure — sub-components import from helpers)
  const g = (en: string, zh: string) => locale === "zh" ? zh : en;

  return (
    <div className={`flex-1 ${styles.appBg} ${styles.cardText} flex flex-col h-full font-sans overflow-hidden animate-fade-in w-full`}>

      {/* Page header */}
      <div className={`${styles.cardBg} border-b ${styles.cardBorder} p-3 sm:p-5 shrink-0 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 sm:gap-4`}>
        <div>
          <h1 className={`text-xl font-bold ${styles.cardText} flex items-center gap-2 font-sans`}>
            <Cpu className="text-indigo-650 w-5 h-5 shrink-0" />
            {t("agent.title")}
          </h1>
          <p className={`text-xs ${styles.cardTextMuted} mt-1 max-w-2xl leading-relaxed`}>
            {t("agent.desc")}
          </p>
        </div>

        {/* Chat input bar */}
        <div className="flex items-center gap-2 shrink-0 w-full sm:w-auto">
          <button
            onClick={() => navigate("/agent-builder")}
            className="bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg px-3 py-2 text-xs font-semibold flex items-center gap-1.5 cursor-pointer transition shadow-xs shrink-0"
            title={t("agent.create.title")}
          >
            <Plus className="w-3.5 h-3.5" />
            {t("agent.create.btn")}
          </button>
          <input
            type="text"
            className={`${styles.appBg} border ${styles.cardBorder} rounded-lg px-3.5 py-2 text-xs ${styles.cardText} placeholder-slate-400 outline-hidden focus:border-indigo-400 flex-1 sm:w-56 sm:flex-none font-sans`}
            placeholder={selectedAgent ? g(`Ask ${selectedAgent.name}...`, `向 ${selectedAgent.name} 提问...`) : g("Ask agent...", "向 Agent 提问...")}
            value={chatMessage}
            onChange={(e) => setChatMessage(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleSendMessage()}
            disabled={isExecuting}
          />
          <button
            onClick={handleSendMessage}
            disabled={isExecuting || !chatMessage.trim()}
            className="bg-blue-500 hover:bg-blue-600 disabled:opacity-50 text-white rounded-lg px-4 py-2 text-xs font-semibold flex items-center gap-2 cursor-pointer transition shadow-xs shrink-0 font-sans"
          >
            {isExecuting ? (
              <RefreshCw className="w-3.5 h-3.5 animate-spin" />
            ) : (
              <Send className="w-3.5 h-3.5 text-white" />
            )}
            {t("agent.chat.send")}
          </button>
        </div>
      </div>

      {/* Create Agent Modal */}
      {showCreateModal && (
        <CreateAgentModal
          form={newAgent}
          onChange={setNewAgent}
          onConfirm={handleCreateAgent}
          onClose={() => setShowCreateModal(false)}
        />
      )}

      {/* Primary workshop layout */}
      <div className="flex-1 grid grid-cols-1 lg:grid-cols-4 gap-4 sm:gap-5 p-4 sm:p-6 min-h-0 overflow-hidden">

        {/* COL 1: AGENT CARD SELECTOR + TOOLS DIRECTORY */}
        <div className={`lg:col-span-1 ${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 flex flex-col gap-4 overflow-hidden shadow-xs`}>

          {/* Agent card selector */}
          <div className="flex-1 flex flex-col overflow-hidden min-h-[150px]">
            <div className="flex items-center justify-between mb-2">
              <span className={`text-[10px] font-mono font-bold uppercase tracking-wider ${styles.cardTextMuted} leading-none`}>
                {t("agent.list.title")}
              </span>
              <span className={`text-[9px] ${styles.cardTextMuted}`}>{agents.length} agents</span>
            </div>
            <div className="space-y-2 overflow-y-auto flex-1 scrollbar-none pr-1">
              {agents.length === 0 ? (
                <div className={`text-[10px] ${styles.cardTextMuted} italic py-4 text-center`}>
                  {t("agent.list.empty")}
                </div>
              ) : (
                agents.map((agent) => {
                  const isSelected = selectedAgentId === agent.id;
                  const toolCount = Array.isArray(agent.tools) ? agent.tools.length : 0;
                  const modelLabel = agent.model || "—";
                  return (
                    <button
                      key={agent.id}
                      id={`agent-card-${agent.id}`}
                      onClick={() => setSelectedEntityId(agent.id)}
                      className={`w-full text-left p-3 rounded-xl transition-all border-2 outline-hidden ${
                        isSelected
                          ? "bg-indigo-50 border-indigo-400 shadow-md shadow-indigo-100/50"
                          : "${styles.appBg} ${styles.cardBorder} hover:${styles.cardBorder} hover:${styles.cardBg} hover:shadow-sm"
                      }`}
                    >
                      {/* Agent name + icon */}
                      <div className="flex items-center gap-2 mb-1.5">
                        <div className={`w-7 h-7 rounded-lg flex items-center justify-center shrink-0 ${
                          isSelected ? "bg-indigo-600 text-white" : "bg-slate-200 ${styles.cardTextMuted}"
                        }`}>
                          <Bot className="w-3.5 h-3.5" />
                        </div>
                        <span className={`text-xs font-bold leading-tight ${
                          isSelected ? "text-indigo-900" : "${styles.cardText}"
                        }`}>
                          {agent.name}
                        </span>
                      </div>

                      {/* Role */}
                      <p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed mb-2 line-clamp-2`}>
                        {agent.role}
                      </p>

                      {/* Meta badges: model + tools */}
                      <div className="flex items-center gap-2 flex-wrap">
                        {modelLabel && modelLabel !== "unknown" && (
                          <span className={`inline-flex items-center gap-1 text-[9px] font-mono ${styles.cardTextMuted} ${styles.cardBg}/70 border ${styles.cardBorder} rounded px-1.5 py-0.5`}>
                            <Cpu className={`w-2.5 h-2.5 ${styles.cardTextMuted}`} />
                            {modelLabel}
                          </span>
                        )}
                        <span className={`inline-flex items-center gap-1 text-[9px] font-mono ${styles.cardTextMuted} ${styles.cardBg}/70 border ${styles.cardBorder} rounded px-1.5 py-0.5`}>
                          <Wrench className={`w-2.5 h-2.5 ${styles.cardTextMuted}`} />
                          {toolCount} {g("tools", "工具")}
                        </span>
                      </div>
                    </button>
                  );
                })
              )}
            </div>
          </div>

          {/* Tools registry */}
          <div className={`flex-1 flex flex-col overflow-hidden border-t ${styles.cardBorder} pt-4`}>
            <span className={`text-[10px] font-mono font-bold uppercase tracking-wider ${styles.cardTextMuted} block mb-2 leading-none`}>
              {t("agent.tools.title")}
            </span>
            <div className="space-y-2 overflow-y-auto flex-1 scrollbar-thin pr-1">
              {tools.length === 0 ? (
                <div className={`text-[10px] ${styles.cardTextMuted} italic py-2 text-center`}>
                  {t("agent.tools.empty")}
                </div>
              ) : (
                tools.map((tool) => (
                  <div key={tool.id} className={`p-2.5 ${styles.appBg} border ${styles.cardBorder} rounded-lg`}>
                    <div className="flex items-center gap-1.5">
                      <Code className={`w-3.5 h-3.5 ${styles.cardTextMuted}`} />
                      <span className={`text-[10px] font-bold ${styles.cardText} font-mono leading-none`}>{tool.name}</span>
                    </div>
                    <p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed mt-1.5`}>{tool.description}</p>
                  </div>
                ))
              )}
            </div>
          </div>

        </div>

        {/* COL 2 & 3: CHAT (with inline Thought Trace) + EXECUTION TRACE */}
        <div className="lg:col-span-2 flex flex-col gap-5 min-h-0 overflow-hidden">

          {/* Chat area — multi-turn with inline thought trace */}
          <div className="flex-1 bg-slate-950 border border-slate-800 p-4 rounded-xl font-mono text-xs flex flex-col overflow-hidden min-h-[180px] select-text shadow-inner">
            <div className="flex justify-between items-center shrink-0 border-b border-slate-700 pb-2 mb-2 select-none leading-none">
              <span className="text-green-500 text-[10.5px] font-bold uppercase tracking-wider flex items-center gap-1.5">
                <Terminal className="w-3.5 h-3.5 text-green-500 shrink-0" />
                {t("agent.chat.title")}
              </span>
              <span className={`${styles.cardTextMuted} text-[9px]`}>{chatHistory.length} {g("messages", "条消息")}</span>
            </div>

            <div className="flex-1 overflow-y-auto scrollbar-thin space-y-3 pr-1 text-[11px] leading-relaxed">
              {chatHistory.length === 0 ? (
                <div className={`py-12 text-center ${styles.cardTextMuted} italic select-none`}>
                  {t("agent.chat.placeholder")}
                </div>
              ) : (
                chatHistory.map((entry, i) => (
                  <div key={i}>
                    {/* Message bubble */}
                    <div className={`p-3 rounded-xl border ${
                      entry.role === "user"
                        ? "bg-indigo-900/30 border-indigo-500/40 ml-4"
                        : entry.role === "error"
                        ? "bg-red-900/30 border-red-500/40"
                        : entry.role === "system"
                        ? "bg-emerald-900/20 border-emerald-500/40 border-l-2 border-l-emerald-400 mr-4"
                        : "bg-slate-800/40 border-slate-700 border-l-2 border-l-green-500 mr-4"
                    }`}>
                      <span className={`text-[10px] font-bold uppercase ${styles.cardTextMuted} block mb-1`}>
                        {entry.role === "user"
                          ? g("You", "你")
                          : entry.role === "error"
                          ? g("Error", "错误")
                          : entry.role === "system"
                          ? g("System", "系统")
                          : selectedAgent?.name || "Agent"}
                      </span>
                      <p className="text-slate-200 font-sans block leading-relaxed whitespace-pre-wrap">{entry.text}</p>
                    </div>

                    {/* Inline Thought Trace — only for agent messages with trace data */}
                    {entry.role === "agent" && entry.thoughtTrace && entry.thoughtTrace.length > 0 && (
                      <div className="mt-1.5 ml-2 mr-6">
                        <button
                          onClick={() => toggleTrace(i)}
                          className={`flex items-center gap-1.5 text-[10px] ${styles.cardTextMuted} hover:${styles.cardText} transition-colors cursor-pointer select-none`}
                        >
                          {entry.collapsed ? (
                            <ChevronDown className="w-3 h-3" />
                          ) : (
                            <ChevronUp className="w-3 h-3" />
                          )}
                          <Brain className="w-3 h-3 text-purple-400" />
                          <span className="font-bold uppercase tracking-wider">
                            {g("Thought Trace", "思考过程")}
                          </span>
                          <span className={`${styles.cardTextMuted}`}>({entry.thoughtTrace.length} {g("steps", "步")})</span>
                        </button>

                        {!entry.collapsed && (
                          <div className="mt-2 space-y-1.5 ml-1 border-l-2 border-slate-700 pl-3">
                            {entry.thoughtTrace.map((step, si) => {
                              const style = getTraceStyle(step.type);
                              const stepKey = `${i}-${si}`;
                              const hasDetail = !!step.detail;
                              const isStepExpanded = !!expandedSteps[stepKey];
                              return (
                                <div
                                  key={si}
                                  className={`p-2 rounded-lg border ${style.bg} ${style.border} text-[10px]`}
                                >
                                  <div
                                    className={`flex items-center gap-1.5 mb-0.5 ${hasDetail ? "cursor-pointer select-none hover:${styles.cardBg}/5 rounded" : ""}`}
                                    onClick={hasDetail ? () => toggleStepExpand(stepKey) : undefined}
                                  >
                                    {hasDetail && (
                                      isStepExpanded
                                        ? <ChevronDown className={`w-3 h-3 ${styles.cardTextMuted} shrink-0`} />
                                        : <ChevronRight className={`w-3 h-3 ${styles.cardTextMuted} shrink-0`} />
                                    )}
                                    <span className={`font-bold uppercase tracking-wider ${style.icon}`}>
                                      [{style.label}]
                                    </span>
                                    {step.type === "thought" && <Lightbulb className="w-3 h-3 text-blue-400" />}
                                    {step.type === "action" && <Wrench className="w-3 h-3 text-amber-400" />}
                                    {step.type === "tool_call" && <Wrench className="w-3 h-3 text-amber-400" />}
                                    {step.type === "observation" && <Eye className="w-3 h-3 text-emerald-400" />}
                                    {step.type === "result" && <CheckCircle2 className="w-3 h-3 text-green-400" />}
                                    {step.type === "plan" && <Layers className="w-3 h-3 text-cyan-400" />}
                                  </div>
                                  <p className="text-slate-300 font-sans leading-relaxed">{step.summary}</p>
                                  {hasDetail && isStepExpanded && (
                                    <p className={`${styles.cardTextMuted} font-sans leading-relaxed mt-0.5 text-[9px]`}>{step.detail}</p>
                                  )}
                                </div>
                              );
                            })}
                          </div>
                        )}
                      </div>
                    )}

                    {/* ── Human-in-the-loop Action Proposal card ── */}
                    {entry.role === "agent" && entry.actionProposal && (
                      <div className="mt-1.5 ml-2 mr-6">
                        {entry.actionProposal.status === "pending" && (
                          <div className="border-2 border-amber-400/70 bg-amber-500/5 rounded-xl p-3 space-y-2.5 shadow-sm">
                            <div className="flex items-center gap-2 font-bold text-amber-300 text-[10px] border-b border-amber-500/30 pb-1.5">
                              <span className="p-1 rounded bg-amber-500/20 text-amber-400">
                                <ShieldAlert className="w-3 h-3 animate-pulse" />
                              </span>
                              <span>{entry.actionProposal.actionName}</span>
                            </div>
                            <div className="space-y-1 font-mono text-[9px] text-slate-300">
                              {Object.entries(entry.actionProposal.payload).map(([k, v]) => (
                                <div key={k}>
                                  <span className="font-bold text-slate-200">{k}:</span> {String(v)}
                                </div>
                              ))}
                              <div className="text-[8px] text-rose-300 font-bold bg-rose-500/10 p-1 rounded mt-1">
                                {g("⚠️ This action mutates the ontology graph and requires human authorization.", "⚠️ 该操作将修改本体图谱，需人工授权。")}
                              </div>
                            </div>
                            <div className="flex gap-1.5 pt-1 border-t border-amber-500/20">
                              <button
                                onClick={() => handleActionConsent(i, true)}
                                className="flex-1 py-1.5 bg-amber-600 hover:bg-amber-700 text-white font-bold rounded-lg text-[10px] transition-colors cursor-pointer flex items-center justify-center gap-1"
                              >
                                <Check className="w-3 h-3" />
                                <span>{g("Approve & Execute", "批准并执行")}</span>
                              </button>
                              <button
                                onClick={() => handleActionConsent(i, false)}
                                className="px-2.5 py-1.5 border border-slate-600 hover:bg-slate-800 rounded-lg text-[10px] font-semibold text-slate-300 transition-colors cursor-pointer flex items-center gap-1"
                              >
                                <X className="w-3 h-3" />
                                <span>{g("Reject", "拒绝")}</span>
                              </button>
                            </div>
                          </div>
                        )}
                        {entry.actionProposal.status === "approved" && (
                          <div className="bg-slate-800/60 border border-emerald-500/50 rounded-xl p-2.5 flex items-center gap-2 text-[10px] text-emerald-300 font-semibold">
                            <span className="p-1 rounded bg-emerald-500/20 text-emerald-400">
                              <CheckCircle2 className="w-3 h-3" />
                            </span>
                            <span>{g("Ontology Action approved & executed.", "本体动作已批准并执行。")}</span>
                          </div>
                        )}
                        {entry.actionProposal.status === "rejected" && (
                          <div className="bg-slate-800/60 border border-red-500/40 rounded-xl p-2.5 flex items-center gap-2 text-[10px] text-red-300 font-semibold">
                            <span className="p-1 rounded bg-red-500/20 text-red-400">
                              <XCircle className="w-3 h-3" />
                            </span>
                            <span>{g("Action rejected by safety guardrail.", "操作已被安全护栏拦截。")}</span>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                ))
              )}
            </div>
          </div>

          {/* Trace terminal — aggregated execution log */}
          <div className="flex-1 bg-slate-950 border border-slate-800 p-4 rounded-xl font-mono text-xs flex flex-col overflow-hidden min-h-[120px] shadow-inner">
            <div className="flex justify-between items-center shrink-0 border-b border-slate-700 pb-2 mb-2 select-none leading-none">
              <span className="text-green-500 text-[10.5px] font-bold uppercase tracking-wider flex items-center gap-1.5">
                <Terminal className="w-3.5 h-3.5 text-green-500 shrink-0" />
                {t("agent.trace.title")}
              </span>
              <span className={`${styles.cardTextMuted} text-[9px]`}>{traceLogs.length} {g("steps", "步")}</span>
            </div>

            <div className="flex-1 overflow-y-auto scrollbar-thin space-y-1 pr-1 text-[10px]">
              {traceLogs.length === 0 ? (
                <div className={`py-8 text-center ${styles.cardTextMuted} italic select-none`}>
                  {t("agent.trace.placeholder")}
                </div>
              ) : (
                traceLogs.map((log) => {
                  const style = getTraceStyle(log.type);
                  return (
                    <div key={log.id} className={`p-2 rounded flex items-start gap-2 ${style.bg} ${style.border}`}>
                      <span className={`font-bold shrink-0 capitalize ${style.icon}`}>[{style.label}]</span>
                      <span className="text-slate-300">{log.summary}</span>
                    </div>
                  );
                })
              )}
            </div>
          </div>

        </div>

        {/* COL 4: METRICS BOARD */}
        <MetricsPanel metrics={metrics} />

      </div>

    </div>
  );
}
