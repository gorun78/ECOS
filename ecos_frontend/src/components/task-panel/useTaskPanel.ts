/**
 * useTaskPanel — TaskPanel 的数据/筛选/分页/选择/详情/批量操作状态逻辑
 * 自 TaskPanel.tsx 原样迁出（H6-T4）：hook 调用顺序与 useEffect 依赖数组逐字保持不变，
 * 仅把 useLanguage/useTheme/useToast 留在父组件（t / showToast 由参数注入）。
 * @license Apache-2.0
 */

import { useState, useEffect, useCallback, useRef, useMemo } from "react";
import type { ToastType } from "../common/Toast";
import {
  taskPanelTypesJson,
  taskPanelStatsJson,
  taskPanelListJson,
  taskPanelDetailJson,
  taskPanelActionJson,
  taskPanelBatchJson,
} from "../../services/taskCenter";
import {
  getCategory,
  type TaskCategory,
  type TaskItem,
  type TaskStats,
  type TaskStatusInfo,
  type TaskDetail,
} from "./taskPanelModel";

type Translate = (key: string, paramsOrFallback?: Record<string, string | number> | string) => string;

export function useTaskPanel(open: boolean, t: Translate, showToast: (type: ToastType, message: string) => void) {
  // 列表 & 统计
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [statusMap, setStatusMap] = useState<Record<string, TaskStatusInfo>>({});
  const [stats, setStats] = useState<TaskStats>({ total: 0, running: 0, pending: 0, succeeded: 0, failed: 0, cancelled: 0 });
  const [loading, setLoading] = useState(false);

  // 筛选
  const [filterStatus, setFilterStatus] = useState<string>("");
  const [filterType, setFilterType] = useState("");
  const [searchType, setSearchType] = useState(""); // actual applied type filter
  const [filterCategory, setFilterCategory] = useState<TaskCategory | "">("");

  // 后端任务类型配置
  const [taskTypes, setTaskTypes] = useState<string[]>([]);

  // 分页
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const pageSize = 20;

  // 选择 & 批量操作
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [batchActioning, setBatchActioning] = useState(false);

  // 详情抽屉
  const [detailTask, setDetailTask] = useState<TaskDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  // 批量操作确认框
  const [batchConfirm, setBatchConfirm] = useState<null | "cancel" | "pause" | "resume" | "archive">(null);

  // 操作中状态
  const [actingIds, setActingIds] = useState<Set<string>>(new Set());

  const refreshTimer = useRef<ReturnType<typeof setInterval> | null>(null);

  // ── 分类计数（客户端计算） ─────────────────────────────

  const categoryCounts = useMemo(() => {
    const counts: Record<string, number> = { pipeline: 0, agent: 0, realtime: 0, management: 0 };
    for (const t of tasks) {
      const cat = getCategory(t.taskType);
      if (cat) counts[cat.key]++;
    }
    return counts;
  }, [tasks]);

  /** 客户端按分类过滤后的任务列表 */
  const filteredTasks = useMemo(() => {
    if (!filterCategory) return tasks;
    return tasks.filter(t => getCategory(t.taskType)?.key === filterCategory);
  }, [tasks, filterCategory]);

  // ── 数据加载 ─────────────────────────────────────────────

  const fetchTaskTypes = useCallback(async () => {
    try {
      const d = await taskPanelTypesJson();
      if (d.code === 0 && Array.isArray(d.data)) {
        setTaskTypes(d.data);
      }
    } catch { /* silent */ }
  }, []);

  const fetchStats = useCallback(async () => {
    try {
      const d = await taskPanelStatsJson();
      if (d.code === 0 && d.data) setStats(d.data);
    } catch { /* silent */ }
  }, []);

  const fetchTasks = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (searchType) params.set("type", searchType);
      if (filterStatus) params.set("status", filterStatus);
      params.set("page", String(page));
      params.set("size", String(pageSize));

      const d = await taskPanelListJson(params);
      if (d.code === 0) {
        setTasks(d.data || []);
        setTotal(d.total || 0);
        // Fetch status for each task
        fetchStatuses(d.data || []);
      }
    } catch { /* silent */ }
    finally { setLoading(false); }
  }, [filterStatus, searchType, page]);

  const fetchStatuses = async (taskList: TaskItem[]) => {
    const map: Record<string, TaskStatusInfo> = {};
    await Promise.all(
      taskList.map(async (t) => {
        try {
          const d = await taskPanelDetailJson(t.taskId);
          if (d.code === 0 && d.data?.status) {
            map[t.taskId] = d.data.status;
          }
        } catch { /* skip */ }
      })
    );
    setStatusMap(prev => ({ ...prev, ...map }));
  };

  const refresh = useCallback(async () => {
    await Promise.all([fetchStats(), fetchTasks()]);
  }, [fetchStats, fetchTasks]);

  // ── 定时刷新 ─────────────────────────────────────────────

  useEffect(() => {
    if (!open) return;
    refresh();
    fetchTaskTypes();
    refreshTimer.current = setInterval(refresh, 10_000);
    return () => {
      if (refreshTimer.current) clearInterval(refreshTimer.current);
    };
  }, [open, refresh, fetchTaskTypes]);

  // 筛选变化时重新加载
  useEffect(() => {
    if (!open) return;
    setPage(1);
    fetchTasks();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterStatus, searchType]);

  // 分类变化时重置分页（纯客户端过滤，不重新请求）
  useEffect(() => {
    setPage(1);
  }, [filterCategory]);

  // ── 操作方法 ─────────────────────────────────────────────

  const doAction = async (taskId: string, action: "execute" | "cancel" | "pause" | "resume" | "archive") => {
    setActingIds(prev => new Set(prev).add(taskId));
    try {
      const d = await taskPanelActionJson(taskId, action);
      if (d.code !== 0) showToast("error", `${t("taskPanel.actionFailed")}: ${d.message || t("taskPanel.unknownError")}`);
    } catch (e: any) {
      showToast("error", `${t("taskPanel.actionError")}: ${e.message}`);
    } finally {
      setActingIds(prev => { const s = new Set(prev); s.delete(taskId); return s; });
      refresh();
    }
  };

  const doBatchAction = (action: "cancel" | "pause" | "resume" | "archive") => {
    if (selectedIds.size === 0) return;
    setBatchConfirm(action);
  };

  const confirmBatchAction = async () => {
    const action = batchConfirm;
    setBatchConfirm(null);
    if (!action) return;
    setBatchActioning(true);
    try {
      const d = await taskPanelBatchJson([...selectedIds], action);
      if (d.code !== 0) showToast("error", `${t("taskPanel.batchFailed")}: ${d.message || t("taskPanel.unknownError")}`);
      else {
        showToast("success", t("taskPanel.batchDone"));
        setSelectedIds(new Set());
        refresh();
      }
    } catch (e: any) {
      showToast("error", `${t("taskPanel.batchError")}: ${e.message}`);
    } finally {
      setBatchActioning(false);
    }
  };

  // ── 详情加载 ─────────────────────────────────────────────

  const openDetail = async (taskId: string) => {
    setDetailLoading(true);
    setDetailTask(null);
    try {
      const d = await taskPanelDetailJson(taskId);
      if (d.code === 0) setDetailTask(d.data);
    } catch { /* silent */ }
    finally { setDetailLoading(false); }
  };

  // ── 全选 ─────────────────────────────────────────────────

  const toggleAll = () => {
    const displayTasks = filteredTasks;
    if (selectedIds.size === displayTasks.length) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(displayTasks.map(t => t.taskId)));
    }
  };

  const toggleOne = (taskId: string) => {
    setSelectedIds(prev => {
      const s = new Set(prev);
      if (s.has(taskId)) s.delete(taskId); else s.add(taskId);
      return s;
    });
  };

  const totalPages = Math.ceil(total / pageSize);

  return {
    // 数据
    tasks,
    statusMap,
    stats,
    loading,
    categoryCounts,
    filteredTasks,
    totalPages,
    // 筛选
    filterStatus,
    setFilterStatus,
    filterType,
    setFilterType,
    searchType,
    setSearchType,
    filterCategory,
    setFilterCategory,
    taskTypes,
    // 分页
    page,
    setPage,
    total,
    // 选择 & 批量
    selectedIds,
    setSelectedIds,
    batchActioning,
    batchConfirm,
    setBatchConfirm,
    actingIds,
    // 详情
    detailTask,
    setDetailTask,
    detailLoading,
    // 动作
    refresh,
    doAction,
    doBatchAction,
    confirmBatchAction,
    openDetail,
    toggleAll,
    toggleOne,
  };
}
