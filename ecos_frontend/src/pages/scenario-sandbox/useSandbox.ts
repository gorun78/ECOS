/**
 * useSandbox.ts — 场景沙盘 (Scenario Sandbox) Hook
 *
 * 提供：
 *   - 拉布局（fetchSandboxLayout）→ 初始化 nodes/edges/viewport
 *   - 默认布局：1 中央 hub + 6 资源按对侧散点（按 category swatch）
 *   - addResourceNode / removeResourceNode（自动建 bind edge）
 *   - updateHubData / addMindVariant / removeMindVariant（PATCH active 切换）
 *   - runInsight（stub 占位，P3b T25 接真端点）
 *   - dirtyLayout + saveLayout（2s debounce on nodes/edges 变化）
 *   - saveError（409 版本号冲突 banner）
 *
 * @license Apache-2.0
 */

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type {
  SdBEdge,
  SdBHubData,
  SdBInsightData,
  SdLayout,
  SdBNode,
  SdBResourceData,
  SdViewport,
  ResourceCategory,
  CognitiveEp,
} from "./types";
import type { SdLayoutSaveRequest, SdMindVariant, SdResourceItem } from "./types";
import type { SandboxAvailableCategory } from "./api";
import {
  fetchSandboxLayout,
  saveSandboxLayout,
  listScenarioMinds,
  saveScenarioMind,
  patchScenarioMind,
  deleteScenarioMind,
  runInsight,
  fetchSandboxAvailable,
  CATEGORY_TO_ENDPOINT,
} from "./api";
import type { SdMindVariant as ApiMindVariant } from "./types";

// ── 常量 ──────────────────────────────────────────────────

/** 默认视口（与 ReactFlow 默认一致） */
const DEFAULT_VIEWPORT: SdViewport = { x: 0, y: 0, zoom: 0.8 };

/** 默认 hub 位置（画布中央） */
const HUB_POSITION = { x: 480, y: 280 };

/** 6 类资源默认位置（围绕 hub，左右各 3，等距 y 分布） */
const RESOURCE_DEFAULT_POSITIONS: Record<ResourceCategory, { x: number; y: number }> = {
  DATASOURCE: { x: 80, y: 60 },
  ONTOLOGY_ENTITY: { x: 80, y: 220 },
  KNOWLEDGE_ARTICLE: { x: 80, y: 380 },
  AGENT_PROFILE: { x: 880, y: 60 },
  SECURITY_POLICY: { x: 880, y: 220 },
  INTERFACE_REF: { x: 880, y: 380 },
};

/** 6 类资源默认 label（前端展示） */
const RESOURCE_DEFAULT_LABEL: Record<ResourceCategory, string> = {
  DATASOURCE: "DATASOURCE_PLACEHOLDER",
  ONTOLOGY_ENTITY: "ONT_ENTITY_PLACEHOLDER",
  KNOWLEDGE_ARTICLE: "KNOWLEDGE_ARTICLE_PLACEHOLDER",
  AGENT_PROFILE: "AGENT_PROFILE_PLACEHOLDER",
  SECURITY_POLICY: "SECURITY_POLICY_PLACEHOLDER",
  INTERFACE_REF: "INTERFACE_REF_PLACEHOLDER",
};

/** 默认 base 心智（首次建档时落库） */
const DEFAULT_MIND_VARIANT: SdMindVariant = {
  mindId: "mind-base",
  label: "BASE_MIND_PLACEHOLDER",
  active: true,
  inheritedFrom: undefined,
};

// ── 节点 id 前缀 ──────────────────────────────────────────
const NODE_ID_HUB = "hub-root";
const NODE_ID_MIND = (id: string) => `mind-${id}`;
const NODE_ID_INSIGHT = (ep: CognitiveEp) => `insight-${ep}`;

let nodeCounter = 0;
function nextNodeId(prefix: string): string {
  nodeCounter += 1;
  return `${prefix}-${Date.now().toString(36).slice(-4)}-${nodeCounter}`;
}

// ── 节点构造 ──────────────────────────────────────────────

/** 构造默认 hub 节点 */
function buildDefaultHubNode(scenarioId: string): SdBNode {
  const hubData: SdBHubData = {
    scenarioId,
    status: "DRAFT",
    beliefProb: [0.25, 0.35, 0.25, 0.15],
    beliefsVariable: "default_variable",
    epEnabled: {
      diagnose: true,
      forecast: true,
      simulate: false,
      policy: false,
    },
  };
  return {
    id: NODE_ID_HUB,
    position: { ...HUB_POSITION },
    type: "hub",
    data: hubData,
  };
}

/** 构造默认 resource 节点（占位 label，等待 fetchSandboxAvailable 后从抽屉 select 真 id） */
function buildResourceNode(category: ResourceCategory): SdBNode {
  const data: SdBResourceData = {
    category,
    targetId: `resource-${Date.now().toString(36)}`,
    targetName: RESOURCE_DEFAULT_LABEL[category],
    status: "ok",
  };
  return {
    id: nextNodeId("res"),
    position: { ...RESOURCE_DEFAULT_POSITIONS[category] },
    type: "resource",
    data,
  };
}

/** 构造 bind edge（resource → hub） */
function buildBindEdge(resourceNodeId: string, hubNodeId: string): SdBEdge {
  return {
    id: `bind-${resourceNodeId}`,
    source: resourceNodeId,
    target: hubNodeId,
    kind: "bind",
    animated: true,
  };
}

/** 构造 6 类 resource 默认散点布局：1 hub + 6 资源分散在 hub 两侧 */
function buildDefaultLayout(scenarioId: string): SdLayout {
  const hub = buildDefaultHubNode(scenarioId);
  const resources: SdBNode[] = (Object.keys(RESOURCE_DEFAULT_POSITIONS) as ResourceCategory[]).map(
    (cat) => buildResourceNode(cat)
  );
  const edges: SdBEdge[] = resources.map((n) => buildBindEdge(n.id, hub.id));
  return {
    nodes: [hub, ...resources],
    edges,
    viewport: { ...DEFAULT_VIEWPORT },
  };
}

// ── 类型 ──────────────────────────────────────────────────

export interface SdLayoutSaveError {
  kind: "VERSION_CONFLICT" | "ERROR";
  message: string;
  /** VERSION_CONFLICT 时含服务端最新版本号 */
  currentVersion?: number;
}

export interface UseSandboxReturn {
  /** ReactFlow Nodes（外层 useNodesState 包装） */
  nodes: SdBNode[];
  edges: SdBEdge[];
  viewport: SdViewport;
  layoutVersion: number;
  hasLoaded: boolean;
  /** 布局变更标记 */
  dirtyLayout: boolean;
  saveError: SdLayoutSaveError | null;

  /** 拉 6 类资源的可用项 */
  fetchAvailable(cat: SandboxAvailableCategory): Promise<SdResourceItem[]>;
  /** 新增 resource 节点 + 自动 bind edge */
  addResourceNode(category: ResourceCategory, targetId: string, targetName: string): void;
  /** 移除 resource 节点（同时移除相关 bind edge） */
  removeResourceNode(nodeId: string): void;
  /** 给一个未 bind 的 resource 建 bind edge */
  linkResourceToHub(nodeId: string): void;
  /** 修改 hub data */
  updateHubData(partial: Partial<SdBHubData>): void;

  /** 新增心智 variant（可选 inheritFrom 复制当前 active） */
  addMindVariant(label: string, opts?: { inheritFrom?: string }): Promise<boolean>;
  /** 删除心智 variant */
  removeMindVariant(mindId: string): Promise<boolean>;
  /** 局部更新某个 mind variant（active toggle / label 改） */
  patchMindVariant(mindId: string, partial: Partial<SdMindVariant>): Promise<boolean>;

  /** 触发认知四件套（P3b stub） */
  runInsight(ep: CognitiveEp, mindId?: string): Promise<{ summary: string; payloadHash: string; ok: boolean }>;
  /** 更新 inspect 卡片数据 */
  setInsightCard(ep: CognitiveEp, data: Partial<SdBInsightData>): void;

  /** 保存（user 按 save） */
  saveLayout(): Promise<boolean>;
  /** 手动标记 dirty（onNodesChange/onEdgesChange 调用） */
  markDirty(): void;
}

// ── Hook 主体 ─────────────────────────────────────────────

export function useSandbox(scenarioId: string | null): UseSandboxReturn {
  const [nodes, setNodes] = useState<SdBNode[]>([]);
  const [edges, setEdges] = useState<SdBEdge[]>([]);
  const [viewport, setViewport] = useState<SdViewport>({ ...DEFAULT_VIEWPORT });
  const [layoutVersion, setLayoutVersion] = useState<number>(0);
  const [hasLoaded, setHasLoaded] = useState<boolean>(false);
  const [dirtyLayout, setDirtyLayout] = useState<boolean>(false);
  const [saveError, setSaveError] = useState<SdLayoutSaveError | null>(null);

  // 资源可用缓存（避免每个 palette 按钮 fetch 重抓）
  const [availableCache, setAvailableCache] = useState<
    Partial<Record<SandboxAvailableCategory, SdResourceItem[]>>
  >({});
  // 当前场景的 mind variants（镜像后端）
  const [minds, setMinds] = useState<SdMindVariant[]>([]);

  const scenarioIdRef = useRef<string | null>(null);
  scenarioIdRef.current = scenarioId;

  // ── 初始化：拉布局 + 拉 minds ──
  useEffect(() => {
    if (!scenarioId) {
      setNodes([]);
      setEdges([]);
      setMinds([]);
      setLayoutVersion(0);
      setHasLoaded(false);
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const layout = await fetchSandboxLayout(scenarioId);
        if (cancelled) return;
        const list = await listScenarioMinds(scenarioId);
        if (cancelled) return;
        setMinds(list);
        if (layout && Array.isArray(layout.nodes) && layout.nodes.length > 0) {
          setNodes(layout.nodes);
          setEdges(layout.edges ?? []);
          setViewport(layout.viewport ?? { ...DEFAULT_VIEWPORT });
        } else {
          const fallback = buildDefaultLayout(scenarioId);
          setNodes(fallback.nodes);
          setEdges(fallback.edges);
          setViewport({ ...DEFAULT_VIEWPORT });
        }
        setHasLoaded(true);
        setDirtyLayout(false);
      } catch (e) {
        if (cancelled) return;
        const msg = e instanceof Error ? e.message : String(e);
        setSaveError({ kind: "ERROR", message: msg });
        setHasLoaded(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [scenarioId]);

  // ── 拉某类资源（带 cache）────
  const fetchAvailable = useCallback(
    async (cat: SandboxAvailableCategory): Promise<SdResourceItem[]> => {
      if (availableCache[cat]) return availableCache[cat] ?? [];
      const items = await fetchSandboxAvailable(cat);
      if (availableCache !== null) {
        setAvailableCache((prev) => ({ ...prev, [cat]: items }));
      }
      return items;
    },
    [availableCache]
  );

  // ── 心智 variants API ──────────────────────────────────
  const refreshMinds = useCallback(async () => {
    const sid = scenarioIdRef.current;
    if (!sid) return;
    try {
      const list = await listScenarioMinds(sid);
      setMinds(list);
    } catch {
      // best-effort — UI 仍可显示本地镜像
    }
  }, []);

  const addMindVariant = useCallback(
    async (label: string, opts?: { inheritFrom?: string }): Promise<boolean> => {
      const sid = scenarioIdRef.current;
      if (!sid) return false;
      // 复制到 active mind 的变体
      const sourceActive = minds.find((m) => m.active) ?? null;
      const mindId = nextNodeId("mind");
      const variant: ApiMindVariant = {
        mindId,
        label,
        active: false,
        inheritedFrom: opts?.inheritFrom ?? (sourceActive ? sourceActive.mindId : undefined),
      };
      try {
        const saved = await saveScenarioMind(sid, variant as unknown as SdMindVariant);
        setMinds((prev) => {
          const exists = prev.find((m) => m.mindId === saved.mindId);
          if (exists) return prev.map((m) => (m.mindId === saved.mindId ? saved : m));
          return [...prev, saved];
        });
        // 新增 mind 节点 + inherit edge（存在源 mind 时）
        const mindNode: SdBNode = {
          id: NODE_ID_MIND(saved.mindId),
          position: { x: HUB_POSITION.x - 80, y: HUB_POSITION.y + 200 + (minds.length - 1) * 80 },
          type: "mind",
          data: {
            mindId: saved.mindId,
            label: saved.label,
            active: saved.active,
            threeFactorSummary: "",
          },
        };
        setNodes((prev) => [...prev, mindNode]);
        if (variant.inheritedFrom) {
          const sourceNic = `mind-${variant.inheritedFrom}`;
          const edge: SdBEdge = {
            id: `inherit-${mindId}`,
            source: sourceNic,
            target: mindNode.id,
            kind: "inherit",
          };
          setEdges((prev) => [...prev, edge]);
        }
        setDirtyLayout(true);
        return true;
      } catch (e) {
        setSaveError({ kind: "ERROR", message: e instanceof Error ? e.message : String(e) });
        return false;
      }
    },
    [minds]
  );

  const removeMindVariant = useCallback(
    async (mindId: string): Promise<boolean> => {
      const sid = scenarioIdRef.current;
      if (!sid) return false;
      try {
        await deleteScenarioMind(sid, mindId);
        setMinds((prev) => prev.filter((m) => m.mindId !== mindId));
        // 移除对应 mind 节点 + 关联 inherit edge
        const nodeKey = NODE_ID_MIND(mindId);
        setNodes((prev) => prev.filter((n) => n.id !== nodeKey));
        setEdges((prev) => prev.filter((e) => e.source !== nodeKey && e.target !== nodeKey));
        setDirtyLayout(true);
        return true;
      } catch (e) {
        setSaveError({ kind: "ERROR", message: e instanceof Error ? e.message : String(e) });
        return false;
      }
    },
    []
  );

  const patchMindVariant = useCallback(
    async (mindId: string, partial: Partial<SdMindVariant>): Promise<boolean> => {
      const sid = scenarioIdRef.current;
      if (!sid) return false;
      try {
        const updated = await patchScenarioMind(sid, mindId, partial);
        setMinds((prev) => prev.map((m) => (m.mindId === mindId ? updated : m)));
        // 同步 mind 节点 data.active / label
        const nodeKey = NODE_ID_MIND(mindId);
        setNodes((prev) =>
          prev.map((n) => {
            if (n.id !== nodeKey || n.type !== "mind") return n;
            const prevData = n.data as { mindId: string; label: string; active: boolean; threeFactorSummary: string };
            return {
              ...n,
              data: {
                mindId: updated.mindId,
                label: updated.label ?? prevData.label,
                active: partial.active !== undefined ? partial.active : prevData.active,
                threeFactorSummary: prevData.threeFactorSummary,
              },
            };
          })
        );
        if (partial.active !== undefined) {
          // 单选切换：仅允许一个 active，patch 其他 mind
          if (partial.active) {
            setMinds((prev) =>
              prev.map((m) =>
                m.mindId !== mindId && m.active ? { ...m, active: false } : m
              )
            );
            setNodes((prev) =>
              prev.map((n) => {
                if (n.type !== "mind" || n.id === nodeKey) return n;
                const prevData = n.data as { mindId: string; label: string; active: boolean; threeFactorSummary: string };
                return {
                  ...n,
                  data: { ...prevData, active: false },
                };
              })
            );
          }
        }
        return true;
      } catch (e) {
        setSaveError({ kind: "ERROR", message: e instanceof Error ? e.message : String(e) });
        return false;
      }
    },
    []
  );

  // ── Resource node 操作 ──────────────────────────────────

  const addResourceNode = useCallback(
    (category: ResourceCategory, targetId: string, targetName: string) => {
      const node = buildResourceNode(category);
      const resData = node.data as SdBResourceData;
      resData.targetId = targetId;
      resData.targetName = targetName;
      setNodes((prevNodes) => [...prevNodes, node]);
      // 资源永远 bind 到中央 hub（hub 一定存在）
      setEdges((prevEdges) => [
        ...prevEdges,
        buildBindEdge(node.id, NODE_ID_HUB),
      ]);
      setDirtyLayout(true);
    },
    []
  );

  const removeResourceNode = useCallback((nodeId: string) => {
    setNodes((prev) => prev.filter((n) => n.id !== nodeId));
    setEdges((prev) => prev.filter((e) => e.source !== nodeId && e.target !== nodeId));
    setDirtyLayout(true);
  }, []);

  const linkResourceToHub = useCallback((nodeId: string) => {
    setEdges((prev) => {
      if (prev.some((e) => e.source === nodeId && e.kind === "bind")) return prev;
      const hubNode = NODE_ID_HUB;
      return [
        ...prev,
        {
          id: `bind-${nodeId}`,
          source: nodeId,
          target: hubNode,
          kind: "bind",
          animated: true,
        },
      ];
    });
    setDirtyLayout(true);
  }, []);

  const updateHubData = useCallback((partial: Partial<SdBHubData>) => {
    setNodes((prev) =>
      prev.map((n) => {
        if (n.type !== "hub") return n;
        const prevData = n.data as SdBHubData;
        const merged: SdBHubData = {
          ...prevData,
          ...partial,
          epEnabled: { ...prevData.epEnabled, ...(partial.epEnabled ?? {}) },
        };
        return { ...n, data: merged };
      })
    );
    setDirtyLayout(true);
  }, []);

  const setInsightCard = useCallback(
    (ep: CognitiveEp, data: Partial<SdBInsightData>) => {
      setNodes((prev) => {
        const nodeKey = NODE_ID_INSIGHT(ep);
        const exists = prev.find((n) => n.id === nodeKey);
        if (!exists) {
          // 首条调用时新建占位节点（P3b 落地后删除此分支）
          const newNode: SdBNode = {
            id: nodeKey,
            position: { x: HUB_POSITION.x + 260, y: HUB_POSITION.y + 320 },
            type: "insight",
            data: {
              ep,
              summary: "",
              payloadHash: "",
              ok: false,
            },
          };
          return [...prev, { ...newNode }];
        }
        return prev.map((n) => {
          if (n.type !== "insight" || n.id !== nodeKey) return n;
          return {
            ...n,
            data: { ...((n.data as SdBInsightData) ?? ({} as SdBInsightData)), ...data },
          };
        });
      });
    },
    []
  );

  // ── 保存 ────────────────────────────────────────────────

  const markDirty = useCallback(() => {
    setDirtyLayout(true);
  }, []);

  const saveLayout = useCallback(async (): Promise<boolean> => {
    const sid = scenarioIdRef.current;
    if (!sid) return false;
    const body: SdLayoutSaveRequest = {
      nodes,
      edges,
      viewport,
      expectedVersion: layoutVersion,
    };
    try {
      const res = await saveSandboxLayout(sid, body);
      setLayoutVersion(res.layoutVersion);
      setDirtyLayout(false);
      setSaveError(null);
      return true;
    } catch (e) {
      // 409 版本号冲突（explosive）
      const err = e as { name?: string; status?: number; code?: number; message?: string; currentVersion?: number; serverVersion?: number };
      const isConflict = err?.status === 409 || err?.name === "SaveConflict" || err?.code === 409;
      if (isConflict) {
        setSaveError({
          kind: "VERSION_CONFLICT",
          message: err.message ?? "",
          currentVersion: err.currentVersion ?? err.serverVersion,
        });
      } else {
        setSaveError({
          kind: "ERROR",
          message: err?.message ?? String(e),
        });
      }
      return false;
    }
  }, [nodes, edges, viewport, layoutVersion]);

  // ── 离场清理：避免 stale save ────────────────────────────
  const debouncedSaveTimer = useRef<number | null>(null);
  // nodes / edges 变化 2s debounce（user 拖拽完成手势）— 但仅标记 dirty，不自动 save
  const debouncesRef = useRef<{ startedAt: number }>({ startedAt: 0 });
  useEffect(() => {
    if (!dirtyLayout) return;
    if (debouncedSaveTimer.current !== null) {
      window.clearTimeout(debouncedSaveTimer.current);
    }
    debouncedSaveTimer.current = window.setTimeout(() => {
      debouncedSaveTimer.current = null;
    }, 2000);
    return () => {
      if (debouncedSaveTimer.current !== null) {
        window.clearTimeout(debouncedSaveTimer.current);
        debouncedSaveTimer.current = null;
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dirtyLayout]);

  // ── runInsight（P3b stub）───────────────────────────────

  // 4 类 insight 节点的懒创建：首次调用时建
  const runInsightFn = useCallback(
    async (ep: CognitiveEp, mindId?: string) => {
      const sid = scenarioIdRef.current;
      if (!sid) return { summary: "", payloadHash: "", ok: false };
      const nodeKey = NODE_ID_INSIGHT(ep);
      // 确保 insight 节点存在
      setNodes((prev) => {
        if (prev.some((n) => n.id === nodeKey)) return prev;
        const newNode: SdBNode = {
          id: nodeKey,
          position: { x: HUB_POSITION.x + 260, y: HUB_POSITION.y + 320 },
          type: "insight",
          data: { ep, summary: "", payloadHash: "", ok: false },
        };
        return [...prev, newNode];
      });
      const res = await runInsight(sid, ep, mindId);
      setNodes((prev) =>
        prev.map((n) => {
          if (n.type !== "insight" || n.id !== nodeKey) return n;
          const prevData = (n.data as SdBInsightData) ?? ({} as SdBInsightData);
          return {
            ...n,
            data: { ...prevData, summary: res.summary, payloadHash: res.payloadHash, ok: res.ok },
          };
        })
      );
      // 建 feed edge（hub → insight）
      setEdges((prev) =>
        prev.some((e) => e.target === nodeKey && e.kind === "feed")
          ? prev
          : [...prev, { id: `feed-${nodeKey}`, source: NODE_ID_HUB, target: nodeKey, kind: "feed", animated: true }]
      );
      setDirtyLayout(true);
      return res;
    },
    []
  );

  // ── 暴露 ────────────────────────────────────────────────

  return {
    nodes,
    edges,
    viewport,
    layoutVersion,
    hasLoaded,
    dirtyLayout,
    saveError,
    fetchAvailable,
    addResourceNode,
    removeResourceNode,
    linkResourceToHub,
    updateHubData,
    addMindVariant,
    removeMindVariant,
    patchMindVariant,
    runInsight: runInsightFn,
    setInsightCard,
    saveLayout,
    markDirty,
  };
}
