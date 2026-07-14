/**
 * Agent Test Console — Agent测试控制台
 * 消息输入框 + 发送按钮 → POST /api/v1/agents/{id}/test
 * 回复显示区(支持markdown) + 工具调用trace展开面板
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect, useRef } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Terminal, Send, RefreshCw, ArrowLeft, Bot, User, Wrench,
  ChevronDown, ChevronUp, Clock, Zap, AlertTriangle, Cpu,
} from "lucide-react";
import {
  AgentConfig,
  AgentTestResponse,
  AgentToolCallTrace,
  fetchAgent,
  testAgent,
} from "../services/agentConfig";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import LoadingSkeleton from "../components/common/LoadingSkeleton";

// ── Helpers ─────────────────────────────────────────────────

function g(locale: string, en: string, zh: string): string {
  return locale === "zh" ? zh : en;
}

// ── Types ───────────────────────────────────────────────────

interface ChatEntry {
  role: "user" | "agent" | "error";
  text: string;
  timestamp: string;
  toolCalls?: AgentToolCallTrace[];
  collapsed?: boolean;
}

// ── Component ───────────────────────────────────────────────

export default function AgentTestConsole() {
  const navigate = useNavigate();
  const { agentId } = useParams<{ agentId?: string }>();
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const chatEndRef = useRef<HTMLDivElement>(null);

  // Agent data
  const [agent, setAgent] = useState<AgentConfig | null>(null);
  const [loadingAgent, setLoadingAgent] = useState(true);

  // Chat state
  const [message, setMessage] = useState("");
  const [chatHistory, setChatHistory] = useState<ChatEntry[]>([]);
  const [isSending, setIsSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // ── Load agent ─────────────────────────────────────────────

  useEffect(() => {
    if (!agentId) {
      setError(g(locale, "No agent ID provided", "未提供Agent ID"));
      setLoadingAgent(false);
      return;
    }

    setLoadingAgent(true);
    setError(null);

    fetchAgent(agentId)
      .then((data) => {
        setAgent(data);
      })
      .catch((err) => {
        setError(err.message || g(locale, "Failed to load agent", "加载Agent失败"));
      })
      .finally(() => setLoadingAgent(false));
  }, [agentId, locale]);

  // ── Auto-scroll ────────────────────────────────────────────

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [chatHistory]);

  // ── Send message ───────────────────────────────────────────

  const handleSend = async () => {
    const msg = message.trim();
    if (!msg || !agentId || isSending) return;

    setMessage("");
    setIsSending(true);
    setError(null);

    // Add user entry
    const userEntry: ChatEntry = {
      role: "user",
      text: msg,
      timestamp: new Date().toLocaleTimeString(),
    };
    setChatHistory((prev) => [...prev, userEntry]);

    try {
      const t0 = Date.now();
      const result = await testAgent(agentId, msg);
      const elapsed = Date.now() - t0;

      const agentEntry: ChatEntry = {
        role: "agent",
        text: result.responseText || g(locale, "(No response)", "（无回复）"),
        timestamp: new Date().toLocaleTimeString(),
        toolCalls: result.toolCalls || [],
        collapsed: false,
      };
      setChatHistory((prev) => [...prev, agentEntry]);
    } catch (err: any) {
      const errorEntry: ChatEntry = {
        role: "error",
        text: `${g(locale, "Test failed", "测试失败")}: ${err.message}`,
        timestamp: new Date().toLocaleTimeString(),
      };
      setChatHistory((prev) => [...prev, errorEntry]);
    } finally {
      setIsSending(false);
    }
  };

  // ── Toggle trace ───────────────────────────────────────────

  const toggleTrace = (idx: number) => {
    setChatHistory((prev) =>
      prev.map((entry, i) =>
        i === idx ? { ...entry, collapsed: !entry.collapsed } : entry
      )
    );
  };

  // ── Render trace panel ─────────────────────────────────────

  const renderTracePanel = (toolCalls: AgentToolCallTrace[], collapsed: boolean, idx: number) => {
    if (!toolCalls || toolCalls.length === 0) return null;

    return (
      <div className="mt-2 ml-2 mr-6">
        <button
          onClick={() => toggleTrace(idx)}
          className="flex items-center gap-1.5 text-[10px] text-slate-500 hover:text-slate-300 transition-colors cursor-pointer select-none"
        >
          {collapsed ? (
            <ChevronDown className="w-3 h-3" />
          ) : (
            <ChevronUp className="w-3 h-3" />
          )}
          <Wrench className="w-3 h-3 text-amber-400" />
          <span className="font-bold uppercase tracking-wider">
            {g(locale, "Tool Calls", "工具调用")}
          </span>
          <span className="text-slate-600">({toolCalls.length})</span>
        </button>

        {!collapsed && (
          <div className="mt-2 space-y-2 ml-1 border-l-2 border-slate-700 pl-3">
            {toolCalls.map((tc, i) => (
              <div
                key={i}
                className="p-2.5 rounded-lg border bg-amber-950/40 border-amber-500/50 text-[10px]"
              >
                <div className="flex items-center gap-1.5 mb-1">
                  <span className="font-bold uppercase text-amber-400">
                    [{tc.toolName}]
                  </span>
                  <span className="text-slate-400 flex items-center gap-1">
                    <Clock className="w-2.5 h-2.5" />
                    {tc.durationMs}ms
                  </span>
                </div>

                {/* Input */}
                {tc.input && Object.keys(tc.input).length > 0 && (
                  <div className="mt-1">
                    <span className="text-slate-500 font-bold uppercase text-[9px]">
                      {g(locale, "Input:", "输入:")}
                    </span>
                    <pre className="text-slate-300 font-mono text-[9px] mt-0.5 bg-black/30 rounded p-1.5 overflow-x-auto whitespace-pre-wrap">
                      {JSON.stringify(tc.input, null, 2)}
                    </pre>
                  </div>
                )}

                {/* Output */}
                {tc.output && (
                  <div className="mt-1">
                    <span className="text-slate-500 font-bold uppercase text-[9px]">
                      {g(locale, "Output:", "输出:")}
                    </span>
                    <pre className="text-slate-300 font-mono text-[9px] mt-0.5 bg-black/30 rounded p-1.5 overflow-x-auto whitespace-pre-wrap max-h-32 overflow-y-auto">
                      {tc.output}
                    </pre>
                  </div>
                )}

                {/* Error */}
                {tc.error && (
                  <div className="mt-1">
                    <span className="text-red-400 font-bold uppercase text-[9px]">
                      {g(locale, "Error:", "错误:")}
                    </span>
                    <pre className="text-red-300 font-mono text-[9px] mt-0.5 bg-red-950/30 rounded p-1.5 overflow-x-auto whitespace-pre-wrap">
                      {tc.error}
                    </pre>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    );
  };

  // ── Render ─────────────────────────────────────────────────

  if (loadingAgent) {
    return (
      <div className="flex-1 bg-slate-50 p-6 animate-fade-in">
        <LoadingSkeleton variant="card" rows={2} />
      </div>
    );
  }

  return (
    <div className="flex-1 bg-slate-50 text-slate-800 flex flex-col h-full font-sans overflow-hidden animate-fade-in w-full">
      {/* Header */}
      <div className="bg-white border-b border-slate-200 p-3 sm:p-5 shrink-0 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-slate-800 flex items-center gap-2">
            <Terminal className="text-green-600 w-5 h-5 shrink-0" />
            {g(locale, "Agent Test Console", "Agent测试控制台")}
          </h1>
          <p className="text-xs text-slate-500 mt-1 max-w-2xl leading-relaxed">
            {agent ? (
              <>
                <span className="font-semibold text-indigo-600">{agent.name}</span>
                <span className="text-slate-400 mx-1.5">·</span>
                <span className="font-mono text-[11px]">{agent.model}</span>
                {agent.toolIds?.length ? (
                  <>
                    <span className="text-slate-400 mx-1.5">·</span>
                    <span className="text-slate-400">
                      {agent.toolIds.length} {g(locale, "tools", "个工具")}
                    </span>
                  </>
                ) : null}
              </>
            ) : (
              <span className="text-red-500">{error || g(locale, "Agent not found", "Agent未找到")}</span>
            )}
          </p>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          {agent && (
            <button
              onClick={() => navigate(`/agent-builder/${agent.id}`)}
              className="bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-lg px-3 py-2 text-xs font-semibold flex items-center gap-1.5 cursor-pointer transition"
            >
              <Cpu className="w-3.5 h-3.5" />
              {g(locale, "Edit Agent", "编辑Agent")}
            </button>
          )}
          <button
            onClick={() => navigate("/agent-builder")}
            className="bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-lg px-3 py-2 text-xs font-semibold flex items-center gap-1.5 cursor-pointer transition"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            {g(locale, "Back", "返回")}
          </button>
        </div>
      </div>

      {/* Chat area */}
      <div className="flex-1 flex flex-col min-h-0 bg-slate-950">
        {/* Messages */}
        <div className="flex-1 overflow-y-auto scrollbar-thin p-4 sm:p-6 space-y-4">
          {chatHistory.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-full text-slate-500 select-none">
              <Bot className="w-12 h-12 mb-3 opacity-30" />
              <p className="text-sm italic">
                {agent
                  ? g(locale, `Start a conversation with ${agent.name}`, `开始与 ${agent.name} 对话`)
                  : g(locale, "Agent not available", "Agent不可用")}
              </p>
              <p className="text-[10px] text-slate-600 mt-1">
                {g(locale, "Type a message and press Send to test the agent", "输入消息并点击发送以测试Agent")}
              </p>
            </div>
          ) : (
            chatHistory.map((entry, i) => (
              <div key={i}>
                {/* Message bubble */}
                <div
                  className={`p-3 rounded-xl border ${
                    entry.role === "user"
                      ? "bg-indigo-900/30 border-indigo-500/40 ml-4 sm:ml-12"
                      : entry.role === "error"
                      ? "bg-red-900/30 border-red-500/40"
                      : "bg-slate-800/40 border-slate-700 border-l-2 border-l-green-500 mr-4 sm:mr-12"
                  }`}
                >
                  <div className="flex items-center gap-2 mb-1.5">
                    <span className="text-[10px] font-bold uppercase text-slate-400">
                      {entry.role === "user" ? (
                        <span className="flex items-center gap-1">
                          <User className="w-3 h-3" />
                          {g(locale, "You", "你")}
                        </span>
                      ) : entry.role === "error" ? (
                        <span className="flex items-center gap-1">
                          <AlertTriangle className="w-3 h-3 text-red-400" />
                          {g(locale, "Error", "错误")}
                        </span>
                      ) : (
                        <span className="flex items-center gap-1">
                          <Bot className="w-3 h-3 text-green-400" />
                          {agent?.name || "Agent"}
                        </span>
                      )}
                    </span>
                    <span className="text-[9px] text-slate-600">{entry.timestamp}</span>
                  </div>
                  <p className="text-slate-200 font-sans text-[13px] leading-relaxed whitespace-pre-wrap">
                    {entry.text}
                  </p>
                </div>

                {/* Tool call trace */}
                {entry.role === "agent" &&
                  entry.toolCalls &&
                  entry.toolCalls.length > 0 &&
                  renderTracePanel(entry.toolCalls, !!entry.collapsed, i)}
              </div>
            ))
          )}
          <div ref={chatEndRef} />
        </div>

        {/* Input area */}
        <div className="shrink-0 border-t border-slate-800 p-3 sm:p-4 bg-slate-950">
          <div className="flex items-center gap-2 max-w-3xl mx-auto">
            <input
              type="text"
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey) {
                  e.preventDefault();
                  handleSend();
                }
              }}
              disabled={isSending || !agent}
              placeholder={
                agent
                  ? g(locale, `Message ${agent.name}...`, `向 ${agent.name} 发送消息...`)
                  : g(locale, "Agent not available", "Agent不可用")
              }
              className="flex-1 bg-slate-800 border border-slate-600 rounded-lg px-4 py-2.5 text-sm text-slate-200 placeholder-slate-500 outline-hidden focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500/30 transition font-sans"
            />
            <button
              onClick={handleSend}
              disabled={isSending || !message.trim() || !agent}
              className="bg-indigo-600 hover:bg-indigo-700 disabled:opacity-40 text-white rounded-lg px-4 py-2.5 text-sm font-semibold flex items-center gap-2 cursor-pointer transition shadow-xs shrink-0"
            >
              {isSending ? (
                <RefreshCw className="w-4 h-4 animate-spin" />
              ) : (
                <Send className="w-4 h-4" />
              )}
              {g(locale, "Send", "发送")}
            </button>
          </div>
          <div className="text-[9px] text-slate-600 text-center mt-2 max-w-3xl mx-auto">
            <Zap className="w-2.5 h-2.5 inline mr-0.5 text-amber-500" />
            {g(
              locale,
              `Testing against model: ${agent?.model || "—"} · Temperature: ${agent?.temperature ?? "—"}`,
              `测试模型: ${agent?.model || "—"} · 温度: ${agent?.temperature ?? "—"}`
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
