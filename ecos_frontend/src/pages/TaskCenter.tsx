/**
 * TaskCenter — 任务管理中心页面
 * 
 * 功能: 任务列表 / 提交 / 执行 / 取消 / 状态追踪 / Doris 健康检查
 * 对齐: Sprint 5.3 — TaskController REST API
 */

import React, { useState, useEffect, useCallback } from "react";
import { apiTaskList, apiTaskSubmit, apiTaskStatus, apiTaskCancel } from "../api";

interface Task {
  taskId: string;
  taskName: string;
  taskType: string;
  description: string;
  status: string;
  priority: number;
  createdAt: string;
  createdBy: string;
  params?: Record<string, string>;
}

interface DorisHealth {
  doris: string;
  jdbcUrl: string;
}

const STATUS_COLORS: Record<string, string> = {
  SUBMITTED: "#6366f1",
  RUNNING: "#f59e0b",
  COMPLETED: "#10b981",
  FAILED: "#ef4444",
  CANCELLED: "#9ca3af",
  PAUSED: "#8b5cf6",
};

// 四类任务类型分类
const TASK_CATEGORIES = [
  { key: "pipeline", label: "管道", types: ["DORIS_SQL","ETL","DATA_SYNC","DATA_INGEST","PIPELINE"], color: "#3b82f6", icon: "🔗" },
  { key: "agent", label: "Agent", types: ["AGENT","AI_AGENT","LLM_TASK","KG_SYNC"], color: "#8b5cf6", icon: "🤖" },
  { key: "realtime", label: "实时", types: ["REALTIME","STREAMING","MONITOR","ALERT","TELEMETRY"], color: "#10b981", icon: "⚡" },
  { key: "management", label: "管理", types: ["DATA_QUALITY","REPORT","MAINTENANCE","BACKUP","CONFIG","ADMIN"], color: "#6b7280", icon: "⚙️" },
];
const TYPE_CATEGORY_MAP: Record<string, string> = {};
TASK_CATEGORIES.forEach(cat => cat.types.forEach(t => { TYPE_CATEGORY_MAP[t] = cat.key; }));

function getTaskCategory(taskType: string) {
  return TASK_CATEGORIES.find(c => c.key === TYPE_CATEGORY_MAP[taskType?.toUpperCase() || ""]) || null;
}

export default function TaskCenter() {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [filter, setFilter] = useState<string>("");
  const [dorisHealth, setDorisHealth] = useState<DorisHealth | null>(null);
  const [showModal, setShowModal] = useState(false);
  const [submitForm, setSubmitForm] = useState({
    taskName: "",
    taskType: "DORIS_SQL",
    description: "",
    priority: 0,
    sql: "",
  });

  const loadTasks = useCallback(async () => {
    try {
      setLoading(true);
      const res = await apiTaskList({});
      const data = res?.data ?? res ?? [];
      setTasks(Array.isArray(data) ? data : []);
    } catch (e: any) {
      setError(e?.message ?? "加载失败");
    } finally {
      setLoading(false);
    }
  }, []);

  const checkDoris = useCallback(async () => {
    try {
      const r = await fetch("/api/v1/task/doris/health");
      if (r.ok) {
        const json = await r.json();
        setDorisHealth(json.data ?? json);
      }
    } catch {
      // Doris 未就绪
    }
  }, []);

  useEffect(() => {
    loadTasks();
    checkDoris();
  }, [loadTasks, checkDoris]);

  const handleSubmit = async () => {
    try {
      await apiTaskSubmit({
        taskName: submitForm.taskName,
        taskType: submitForm.taskType,
        description: submitForm.description,
        priority: submitForm.priority,
        params: submitForm.sql ? { sql: submitForm.sql } : undefined,
      });
      setShowModal(false);
      setSubmitForm({ taskName: "", taskType: "DORIS_SQL", description: "", priority: 0, sql: "" });
      loadTasks();
    } catch (e: any) {
      alert("提交失败: " + (e?.message ?? "未知错误"));
    }
  };

  const handleExecute = async (taskId: string) => {
    try {
      const r = await fetch(`/api/v1/task/${taskId}/execute`, { method: "POST" });
      if (r.ok) loadTasks();
    } catch (e: any) {
      alert("执行失败: " + (e?.message ?? "未知错误"));
    }
  };

  const handleCancel = async (taskId: string) => {
    try {
      await apiTaskCancel(taskId);
      loadTasks();
    } catch (e: any) {
      alert("取消失败: " + (e?.message ?? "未知错误"));
    }
  };

  const handleRefreshStatus = async (taskId: string) => {
    try {
      const s = await apiTaskStatus(taskId);
      alert(`状态: ${s?.data?.status ?? s?.status ?? "?"}\n进度: ${s?.data?.progress ?? s?.progress ?? "?"}%`);
    } catch (e: any) {
      alert("查询失败");
    }
  };

  const filteredTasks = filter
    ? tasks.filter((t) => {
        // 分类筛选
        if (["pipeline","agent","realtime","management"].includes(filter)) {
          return TYPE_CATEGORY_MAP[t.taskType?.toUpperCase() || ""] === filter;
        }
        // 状态筛选
        return t.status === filter;
      })
    : tasks;

  return (
    <div style={{ padding: 24, maxWidth: 1400, margin: "0 auto" }}>
      {/* 标题 */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <h1 style={{ fontSize: 28, fontWeight: 700, margin: 0 }}>📋 任务管理中心</h1>
        <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
          {/* Doris 状态 */}
          <div style={{
            padding: "6px 14px",
            borderRadius: 8,
            background: dorisHealth?.doris === "UP" ? "#065f46" : "#451a03",
            color: dorisHealth?.doris === "UP" ? "#6ee7b7" : "#fbbf24",
            fontSize: 13,
            fontWeight: 600,
          }}>
            🗄️ Doris: {dorisHealth?.doris ?? "检测中..."}
          </div>
          <button
            onClick={() => setShowModal(true)}
            style={{
              padding: "10px 20px",
              borderRadius: 8,
              background: "#7c3aed",
              color: "#fff",
              border: "none",
              fontSize: 14,
              fontWeight: 600,
              cursor: "pointer",
            }}
          >
            ＋ 提交任务
          </button>
        </div>
      </div>

      {/* 筛选栏 — 四类任务分类 */}
      <div style={{ display: "flex", gap: 8, marginBottom: 12, flexWrap: "wrap" }}>
        {[{ key: "", label: "全部", color: "#64748b", icon: "📋" }, ...TASK_CATEGORIES].map((cat) => (
          <button
            key={cat.key}
            onClick={() => setFilter(cat.key || "")}
            style={{
              padding: "8px 16px",
              borderRadius: 8,
              border: `1.5px solid ${filter === cat.key ? cat.color : "#334155"}`,
              background: filter === cat.key ? `${cat.color}18` : "transparent",
              color: filter === cat.key ? cat.color : "#94a3b8",
              fontSize: 13,
              fontWeight: 600,
              cursor: "pointer",
              transition: "all .15s",
            }}
          >
            {cat.icon} {cat.label}
            {cat.key && (
              <span style={{ marginLeft: 6, fontSize: 11, opacity: 0.7 }}>
                ({tasks.filter(t => TYPE_CATEGORY_MAP[t.taskType?.toUpperCase() || ""] === cat.key).length})
              </span>
            )}
          </button>
        ))}
      </div>

      {/* 状态筛选 */}
      <div style={{ display: "flex", gap: 8, marginBottom: 16 }}>
        {["", "SUBMITTED", "RUNNING", "COMPLETED", "FAILED"].map((s) => (
          <button
            key={s}
            onClick={() => setFilter(s)}
            style={{
              padding: "6px 14px",
              borderRadius: 6,
              border: "1px solid #334155",
              background: filter === s ? "#1e293b" : "transparent",
              color: filter === s ? "#e2e8f0" : "#94a3b8",
              fontSize: 12,
              fontWeight: 600,
              cursor: "pointer",
            }}
          >
            {s || "全部"}
          </button>
        ))}
      </div>

      {/* 错误提示 */}
      {error && (
        <div style={{ padding: 12, borderRadius: 8, background: "#451a03", color: "#fca5a5", marginBottom: 16 }}>
          {error}
          <button onClick={loadTasks} style={{ marginLeft: 12, background: "none", border: "none", color: "#fbbf24", cursor: "pointer" }}>
            重试
          </button>
        </div>
      )}

      {/* 任务列表 */}
      {loading ? (
        <div style={{ textAlign: "center", padding: 60, color: "#64748b" }}>加载中...</div>
      ) : filteredTasks.length === 0 ? (
        <div style={{ textAlign: "center", padding: 60, color: "#64748b" }}>
          暂无任务，点击"提交任务"创建第一个
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {filteredTasks.map((task) => (
            <div
              key={task.taskId}
              style={{
                padding: "16px 20px",
                borderRadius: 10,
                background: "#0f172a",
                border: "1px solid #1e293b",
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                transition: "border-color .2s",
              }}
              onMouseEnter={(e) => (e.currentTarget.style.borderColor = "#334155")}
              onMouseLeave={(e) => (e.currentTarget.style.borderColor = "#1e293b")}
            >
              {/* 左侧信息 */}
              <div style={{ flex: 1 }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
                  <span style={{ fontSize: 15, fontWeight: 700, color: "#e2e8f0" }}>
                    {task.taskName}
                  </span>
                  <span style={{
                    padding: "2px 10px",
                    borderRadius: 12,
                    fontSize: 11,
                    fontWeight: 700,
                    background: `${STATUS_COLORS[task.status] ?? "#64748b"}22`,
                    color: STATUS_COLORS[task.status] ?? "#64748b",
                    border: `1px solid ${STATUS_COLORS[task.status] ?? "#64748b"}44`,
                  }}>
                    {task.status ?? "UNKNOWN"}
                  </span>
                  {(() => { const cat = getTaskCategory(task.taskType); return cat && (
                    <span style={{
                      padding: "2px 8px",
                      borderRadius: 4,
                      fontSize: 11,
                      color: cat.color,
                      background: `${cat.color}18`,
                      border: `1px solid ${cat.color}44`,
                    }}>
                      {cat.icon} {cat.label}
                    </span>
                  );})()}
                  <span style={{
                    padding: "2px 8px",
                    borderRadius: 4,
                    fontSize: 11,
                    color: "#64748b",
                    background: "#1e293b",
                  }}>
                    {task.taskType}
                  </span>
                </div>
                <div style={{ fontSize: 12, color: "#94a3b8", marginBottom: 2 }}>
                  {task.description || "无描述"}
                </div>
                <div style={{ fontSize: 11, color: "#475569" }}>
                  ID: {task.taskId?.slice(-8) ?? "?"} · {task.createdAt ?? "?"} · 优先级: {task.priority ?? 0}
                </div>
              </div>

              {/* 右侧操作 */}
              <div style={{ display: "flex", gap: 8, flexShrink: 0 }}>
                <button
                  onClick={() => handleRefreshStatus(task.taskId)}
                  style={{
                    padding: "6px 14px",
                    borderRadius: 6,
                    border: "1px solid #334155",
                    background: "transparent",
                    color: "#64748b",
                    fontSize: 12,
                    cursor: "pointer",
                  }}
                >
                  状态
                </button>
                {(task.status === "SUBMITTED" || !task.status) && (
                  <button
                    onClick={() => handleExecute(task.taskId)}
                    style={{
                      padding: "6px 14px",
                      borderRadius: 6,
                      border: "none",
                      background: "#10b981",
                      color: "#fff",
                      fontSize: 12,
                      fontWeight: 600,
                      cursor: "pointer",
                    }}
                  >
                    执行
                  </button>
                )}
                {(task.status === "RUNNING" || task.status === "SUBMITTED") && (
                  <button
                    onClick={() => handleCancel(task.taskId)}
                    style={{
                      padding: "6px 14px",
                      borderRadius: 6,
                      border: "1px solid #ef4444",
                      background: "transparent",
                      color: "#ef4444",
                      fontSize: 12,
                      cursor: "pointer",
                    }}
                  >
                    取消
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* 提交任务模态框 */}
      {showModal && (
        <div
          style={{
            position: "fixed",
            inset: 0,
            background: "rgba(0,0,0,0.6)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            zIndex: 1000,
          }}
          onClick={() => setShowModal(false)}
        >
          <div
            style={{
              background: "#1e293b",
              borderRadius: 12,
              padding: 28,
              width: 480,
              maxHeight: "80vh",
              overflow: "auto",
              border: "1px solid #334155",
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <h2 style={{ margin: "0 0 20px", fontSize: 20, fontWeight: 700, color: "#e2e8f0" }}>
              📝 提交新任务
            </h2>
            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <label style={{ color: "#94a3b8", fontSize: 13 }}>
                任务名称
                <input
                  value={submitForm.taskName}
                  onChange={(e) => setSubmitForm({ ...submitForm, taskName: e.target.value })}
                  placeholder="如: 日销量汇总"
                  style={inputStyle}
                />
              </label>
              <label style={{ color: "#94a3b8", fontSize: 13 }}>
                任务类型
                <select
                  value={submitForm.taskType}
                  onChange={(e) => setSubmitForm({ ...submitForm, taskType: e.target.value })}
                  style={inputStyle}
                >
                  {TASK_CATEGORIES.map((cat) => (
                    <optgroup key={cat.key} label={`${cat.icon} ${cat.label}`}>
                      {cat.types.map((t) => (
                        <option key={t} value={t}>{t}</option>
                      ))}
                    </optgroup>
                  ))}
                </select>
              </label>
              <label style={{ color: "#94a3b8", fontSize: 13 }}>
                描述
                <input
                  value={submitForm.description}
                  onChange={(e) => setSubmitForm({ ...submitForm, description: e.target.value })}
                  placeholder="任务描述"
                  style={inputStyle}
                />
              </label>
              <label style={{ color: "#94a3b8", fontSize: 13 }}>
                优先级 (0-10)
                <input
                  type="number"
                  min={0}
                  max={10}
                  value={submitForm.priority}
                  onChange={(e) => setSubmitForm({ ...submitForm, priority: Number(e.target.value) })}
                  style={inputStyle}
                />
              </label>
              <label style={{ color: "#94a3b8", fontSize: 13 }}>
                SQL 语句
                <textarea
                  value={submitForm.sql}
                  onChange={(e) => setSubmitForm({ ...submitForm, sql: e.target.value })}
                  placeholder="SELECT * FROM ..."
                  rows={4}
                  style={{ ...inputStyle, fontFamily: "monospace", resize: "vertical" }}
                />
              </label>
            </div>
            <div style={{ display: "flex", gap: 10, marginTop: 20, justifyContent: "flex-end" }}>
              <button
                onClick={() => setShowModal(false)}
                style={{
                  padding: "10px 20px",
                  borderRadius: 8,
                  border: "1px solid #334155",
                  background: "transparent",
                  color: "#94a3b8",
                  fontSize: 14,
                  cursor: "pointer",
                }}
              >
                取消
              </button>
              <button
                onClick={handleSubmit}
                disabled={!submitForm.taskName}
                style={{
                  padding: "10px 20px",
                  borderRadius: 8,
                  border: "none",
                  background: submitForm.taskName ? "#7c3aed" : "#334155",
                  color: submitForm.taskName ? "#fff" : "#64748b",
                  fontSize: 14,
                  fontWeight: 600,
                  cursor: submitForm.taskName ? "pointer" : "not-allowed",
                }}
              >
                提交任务
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

const inputStyle: React.CSSProperties = {
  display: "block",
  marginTop: 4,
  width: "100%",
  padding: "10px 12px",
  borderRadius: 6,
  border: "1px solid #334155",
  background: "#0f172a",
  color: "#e2e8f0",
  fontSize: 14,
  boxSizing: "border-box",
};
