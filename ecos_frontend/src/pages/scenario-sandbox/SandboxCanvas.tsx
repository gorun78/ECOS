/**
 * SandboxCanvas.tsx — 场景沙盘 (Scenario Sandbox) 画布主组件
 *
 * 由 ScenarioList「进沙盘」按钮打开（react-router link 到 /scenario-sandbox/:scenarioId）。
 *
 * 布局：
 *   ┌────────────────────────────────────────────┐
 *   │ toolbar (title/save/version/conflict)      │
 *   ├─┬─────────────────────────────────────────┤
 *   │P│ React Flow canvas                       │
 *   │a│  - 4 自定义 node 类型 (resource/hub/)    │
 *   │l│  - Background + MiniMap + Controls      │
 *   │e│  - onConnect → 按 from/to type 推断 edge │
 *   │t│  - onClick node → drawer open           │
 *   ├─┴─────────────────────────────────────────┤
 *   │ version badge (bottom-left overlay)         │
 *   └────────────────────────────────────────────┘
 *
 * @license Apache-2.0
 */

import {
  useCallback,
  useEffect,
  useRef,
  useState,
} from "react";
import React from "react";
import {
  ReactFlow,
  Background,
  BackgroundVariant,
  Controls,
  MiniMap,
  type Connection,
  type Edge as RfEdge,
  type Node as RfNode,
  type NodeTypes,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import {
  Save,
  AlertTriangle,
  Database,
  Network,
  BookOpen,
  Cpu,
  ShieldCheck,
  Plug,
  Sparkles,
  Loader2,
  GitBranch,
  Box,
} from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { useSandbox } from "./useSandbox";

import type {
  SdBEdge,
  SdBHubData,
  SdBNode,
  SdBMindData,
  ResourceCategory,
  CognitiveEp,
} from "./types";
import type { SandboxAvailableCategory } from "./api";
import SdResourceNode from "./SdResourceNode";
import SdHubNode from "./SdHubNode";
import SdMindCard from "./SdMindCard";
import SdInsightCard from "./SdInsightCard";
import SdNodeDrawer from "./SdNodeDrawer";
import type { SdNodeDrawerProps } from "./SdNodeDrawer";
import SdResourcePickerModal from "./SdResourcePickerModal";
import type { SdResourcePickerState } from "./SdResourcePickerModal";

// ── reactflow 类型别名 ─────────────────────────────────────

/** SdBNode → RfNode 包装（data 同 SdBNode["data"] 联合） */
type RFNode = RfNode<SdBNode["data"]>;
type RFFEdge = RfEdge;

// ── 6 类资源 palette 配置 ──────────────────────────────────

const CATEGORIES: Array<{
  category: ResourceCategory;
  endpoint: SandboxAvailableCategory;
  icon: React.ComponentType<{ size?: number; className?: string }>;
  i18nKey: `scenario.sandbox.palette.${string}`;
}> = [
  { category: "DATASOURCE", endpoint: "datasets", icon: Database, i18nKey: "scenario.sandbox.palette.dssrc" },
  { category: "ONTOLOGY_ENTITY", endpoint: "objects", icon: Network, i18nKey: "scenario.sandbox.palette.ont" },
  { category: "KNOWLEDGE_ARTICLE", endpoint: "knowledge", icon: BookOpen, i18nKey: "scenario.sandbox.palette.kno" },
  { category: "AGENT_PROFILE", endpoint: "agents", icon: Cpu, i18nKey: "scenario.sandbox.palette.agent" },
  { category: "SECURITY_POLICY", endpoint: "security", icon: ShieldCheck, i18nKey: "scenario.sandbox.palette.sec" },
  { category: "INTERFACE_REF", endpoint: "interfaces", icon: Plug, i18nKey: "scenario.sandbox.palette.iface" },
];

// ── nodeTypes：按 useSandbox 注册的 4 类节点 ─────────────────

function buildNodeTypeRegistry(
  onPatch: (mindId: string, p: { active: boolean }) => void,
  onRemove: (mindId: string) => void,
  onCopy: (mindId: string) => void
): NodeTypes {
  const nodeTypes: NodeTypes = {
    resource: SdResourceNode as unknown as NodeTypes["resource"],
    hub: SdHubNode as unknown as NodeTypes["hub"],
    mind: ((props: unknown) => {
      const r = props as {
        data?: SdBMindData;
        selected?: boolean;
        id?: string;
        type?: string;
      };
      const data: SdBMindData = (r.data as SdBMindData | undefined) ?? {
        mindId: "",
        label: "",
        active: false,
        threeFactorSummary: "",
      };
      return (
        <SdMindCard
          id={typeof r.id === "string" ? r.id : ""}
          type={typeof r.type === "string" ? r.type : "mind"}
          data={data}
          selected={r.selected}
          onPatch={onPatch}
          onRemove={onRemove}
          onCopy={onCopy}
        />
      );
    }) as unknown as NodeTypes["mind"],
    insight: SdInsightCard as unknown as NodeTypes["insight"],
  };
  return nodeTypes;
}

// ── SdBEdge kind → 颜色 token 映射（参考 WorkflowDesigner 颜色策略）──

// ── SdBEdge kind → CSS var() token（B8: 无硬编码 hex，4 主题在 index.css 定义）──
// 需确保 index.css 4 主题(palette/deep/cyber/royal)下定义了 --ecos-edge-bind/inherit/feed/insight

function sdEdgeStyle(kind: SdBEdge["kind"]): React.CSSProperties {
  switch (kind) {
    case "bind":
      return { stroke: "var(--ecos-edge-bind, #64748B)", strokeWidth: 1.5 };
    case "inherit":
      return { stroke: "var(--ecos-edge-inherit, #A855F7)", strokeWidth: 1.5, strokeDasharray: "4 3" };
    case "feed":
      return { stroke: "var(--ecos-edge-feed, #10B981)", strokeWidth: 1.5 };
    case "insight":
      return { stroke: "var(--ecos-edge-insight, #F59E0B)", strokeWidth: 1.5, strokeDasharray: "6 3" };
    default:
      return { stroke: "var(--ecos-edge-bind, #64748B)", strokeWidth: 1 };
  }
}

function sdEdgeLabelStyle(): React.CSSProperties {
  return { fontSize: 10, fill: "var(--ecos-text-muted, #CBD5E1)", background: "var(--ecos-surface, rgba(15, 23, 42, 0.92))" };
}

// ── Palette state / Drawer state ───────────────────────────

interface PaletteState extends SdResourcePickerState {
  category: ResourceCategory;
  endpoint: SandboxAvailableCategory;
}

interface DrawerState {
  open: boolean;
  node: RFNode | null;
}

// ── 主 Component ────────────────────────────────────────────

interface SandboxCanvasProps {
  /** 当前场景 ID（route param） */
  scenarioId: string;
}

export default function SandboxCanvas({ scenarioId }: SandboxCanvasProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const sandbox = useSandbox(scenarioId);
  const {
    nodes, edges, viewport,
    layoutVersion, dirtyLayout, saveError,
    fetchAvailable,
    addResourceNode, removeResourceNode, linkResourceToHub,
    addMindVariant, removeMindVariant, patchMindVariant,
    runInsight,
    saveLayout, markDirty,
  } = sandbox;

  // ── ReactFlow state ────────────────────────────────────
  const [rfNodes, setRfNodes] = useState<RFNode[]>([]);
  const [rfEdges, setRfEdges] = useState<RFFEdge[]>([]);
  void viewport; // Sd 视口来自 useSandbox；只读

  // ── Drawer & Modal state ───────────────────────────────
  const [drawer, setDrawer] = useState<DrawerState>({ open: false, node: null });
  const [palette, setPalette] = useState<PaletteState | null>(null);

  // ── nodes/edges 桥：仅在 id 集合变化时整体重置 ──────────
  const prevNodesRef = useRef<string[]>([]);
  useEffect(() => {
    const ids = nodes.map((n) => n.id);
    const sameSize = prevNodesRef.current.length === ids.length;
    const sameOrder = sameSize && ids.every((id, i) => id === prevNodesRef.current[i]);
    if (sameOrder) return;
    prevNodesRef.current = ids;
    setRfNodes((prev) => {
      const byId = new Map(prev.map((n) => [n.id, n] as const));
      return nodes.map((n) => {
        const existing = byId.get(n.id);
        return {
          ...existing,
          id: n.id,
          position: n.position,
          data: n.data,
          type: n.type,
        } as unknown as RFNode;
      });
    });
  }, [nodes]);

  const prevEdgesRef = useRef<string[]>([]);
  useEffect(() => {
    const ids = edges.map((e) => e.id);
    const sameSize = prevEdgesRef.current.length === ids.length;
    const sameOrder = sameSize && ids.every((id, i) => id === prevEdgesRef.current[i]);
    if (sameOrder) return;
    prevEdgesRef.current = ids;
    setRfEdges((prev) => {
      const byId = new Map(prev.map((e) => [e.id, e] as const));
      return edges.map((e) => {
        const existing = byId.get(e.id);
        return {
          ...(existing ?? {}),
          id: e.id,
          source: e.source,
          target: e.target,
          kind: e.kind,
          label: e.label,
          labelStyle: sdEdgeLabelStyle(),
          labelBgPadding: [4, 2] as [number, number],
          labelBgBorderRadius: 3,
          animated: e.animated,
          style: sdEdgeStyle(e.kind),
        } as unknown as RFFEdge;
      });
    });
  }, [edges]);

  // ── RF 内部 change：仅上报 markDirty ────────────────────
  const onNodesChange = useCallback(
    (changes: unknown[]) => {
      void changes;
      markDirty();
    },
    [markDirty]
  );

  const onEdgesChange = useCallback(
    (changes: unknown[]) => {
      void changes;
      markDirty();
    },
    [markDirty]
  );

  // ── onConnect：推断 edge kind ───────────────────────────
  const onConnect = useCallback(
    (conn: Connection) => {
      const { source, target } = conn;
      const from = rfNodes.find((n) => n.id === source);
      const to = rfNodes.find((n) => n.id === target);
      const ft = (from?.type as string | undefined) ?? "resource";
      const tt = (to?.type as string | undefined) ?? "hub";
      let kind: SdBEdge["kind"];
      if (ft === "resource" && tt === "hub") kind = "bind";
      else if (ft === "mind" && tt === "mind") kind = "inherit";
      else if (ft === "hub" && tt === "insight") kind = "feed";
      else if (ft === "mind" && tt === "insight") kind = "insight";
      else if (ft === "hub" && tt === "mind") kind = "feed";
      else kind = "feed";

      const cpu: SdBEdge = {
        id: `e-${source}-${target}-${Date.now().toString(36)}`,
        source,
        target,
        kind,
      };
      setRfEdges((prev) => [
        ...prev,
        {
          id: cpu.id,
          source,
          target,
          kind,
          style: sdEdgeStyle(kind),
          animated: kind !== "inherit",
        } as unknown as RFFEdge,
      ]);
      markDirty();
    },
    [rfNodes, markDirty]
  );

  // ── 节点点击 → drawer ──────────────────────────────────
  const onNodeClick = useCallback((_e: React.MouseEvent, n: RfNode) => {
    setDrawer({ open: true, node: n as unknown as RFNode });
  }, []);

  const closeDrawer = useCallback(
    () => setDrawer({ open: false, node: null }),
    []
  );

  // ── Palette ────────────────────────────────────────────
  const openPaletteModal = useCallback(
    async (cfg: (typeof CATEGORIES)[number]) => {
      setPalette({
        category: cfg.category,
        endpoint: cfg.endpoint,
        i18nP: cfg.i18nKey,
        items: [],
        loading: true,
      });
      try {
        const items = await fetchAvailable(cfg.endpoint);
        setPalette((st) =>
          st
            ? {
                ...st,
                items: items as SdResourcePickerState["items"],
                loading: false,
              }
            : st
        );
      } catch (e) {
        setPalette((st) =>
          st
            ? {
                ...st,
                loading: false,
                error: e instanceof Error ? e.message : String(e),
              }
            : st
        );
      }
    },
    [fetchAvailable]
  );

  const handlePalettePick = useCallback(
    (item: SdResourcePickerState["items"][number]) => {
      if (!palette) return;
      addResourceNode(palette.category, String(item.id), item.name);
      setPalette(null);
    },
    [palette, addResourceNode]
  );

  // ── 节点操作：drawer 内的 cmd ──────────────────────────
  const handleDeleteResource = useCallback(() => {
    if (!drawer.node || drawer.node.type !== "resource") return;
    removeResourceNode(drawer.node.id);
    closeDrawer();
  }, [drawer, removeResourceNode, closeDrawer]);

  const handleLinkToHub = useCallback(() => {
    if (!drawer.node || drawer.node.type !== "resource") return;
    linkResourceToHub(drawer.node.id);
  }, [drawer, linkResourceToHub]);

  const handleSetMindActive = useCallback(
    (mindId: string, p: { active: boolean }) => {
      void patchMindVariant(mindId, p);
    },
    [patchMindVariant]
  );

  const handleCopyMind = useCallback(
    (mindId: string) => {
      void addMindVariant(`mind-${Date.now().toString(36)}`, { inheritFrom: mindId });
    },
    [addMindVariant]
  );

  const handleDeleteMind = useCallback(
    (mindId: string) => {
      void removeMindVariant(mindId);
      closeDrawer();
    },
    [removeMindVariant, closeDrawer]
  );

  const handleRunInsight = useCallback(
    (ep: CognitiveEp) => {
      const h = nodes.find((n) => n.type === "hub");
      const activeMind = (h?.data as SdBHubData | undefined)?.activeMindId;
      void runInsight(ep, activeMind);
    },
    [nodes, runInsight]
  );

  const drawerProps: Pick<
    SdNodeDrawerProps,
    "onClose" | "onDeleteRes" | "onLinkHub" | "onRunInsight"
  > = {
    onClose: closeDrawer,
    onDeleteRes: handleDeleteResource,
    onLinkHub: handleLinkToHub,
    onRunInsight: handleRunInsight,
  };

  // ── 保存 ────────────────────────────────────────────────
  const savingRef = useRef(false);
  const onSave = useCallback(async () => {
    if (savingRef.current) return;
    savingRef.current = true;
    try {
      void rfNodes;
      void rfEdges;
      markDirty();
      await saveLayout();
    } finally {
      savingRef.current = false;
    }
  }, [rfNodes, rfEdges, saveLayout, markDirty]);

  // ── 空场景 fallback ────────────────────────────────────
  if (!scenarioId) {
    return (
      <div
        className={`flex h-full items-center justify-center ${styles.appBg} ${styles.appText}`}
      >
        <div className="text-center">
          <Sparkles size={48} className={`${styles.muted} mx-auto mb-3`} />
          <h2 className={`text-lg font-bold ${styles.cardText}`}>
            {t("scenario.sandbox.empty.title")}
          </h2>
          <p className={`text-xs mt-1 ${styles.cardTextMuted}`}>
            {t("scenario.sandbox.empty.body")}
          </p>
        </div>
      </div>
    );
  }

  const saveConflict = saveError?.kind === "VERSION_CONFLICT";

  return (
    <div className={`relative flex h-full flex-col ${styles.appBg} ${styles.appText}`}>
      {/* ── toolbar ─────────────────────────────────── */}
      <div className={`shrink-0 px-4 py-2 border-b ${styles.cardBorder}`}>
        <div className="flex items-center justify-between gap-4 max-w-7xl mx-auto">
          <div className="flex items-center gap-2 min-w-0">
            <Box size={18} className={`${styles.accentText} shrink-0`} />
            <h1 className="text-base font-bold truncate">
              {t("scenario.sandbox.title")}
            </h1>
            <span
              className={`text-[11px] font-mono truncate ${styles.muted}`}
              title={scenarioId}
            >
              {scenarioId}
            </span>
            {dirtyLayout && (
              <span
                className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${styles.warningBg} ${styles.warningText}`}
              >
                {t("scenario.sandbox.dirty")}
              </span>
            )}
          </div>
          <div className="flex items-center gap-2 shrink-0">
            {layoutVersion > 0 && (
              <span
                className={`inline-flex items-center gap-1 text-[10px] px-1.5 py-0.5 rounded ${styles.badgeBg} ${styles.badgeText}`}
                title={t("scenario.sandbox.version")}
              >
                <GitBranch size={10} />
                v{layoutVersion}
              </span>
            )}
            <button
              type="button"
              onClick={() => void onSave()}
              disabled={!dirtyLayout || savingRef.current}
              className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded font-semibold text-xs ${styles.accentBg} ${styles.inputText} ${
                !dirtyLayout || savingRef.current
                  ? "opacity-40 cursor-not-allowed"
                  : "cursor-pointer"
              }`}
            >
              {savingRef.current ? (
                <Loader2 size={13} className="animate-spin" />
              ) : (
                <Save size={13} />
              )}
              {savingRef.current
                ? t("scenario.sandbox.saving")
                : t("scenario.sandbox.save")}
            </button>
          </div>
        </div>

        {/* 保存错误横幅 */}
        {saveError && (
          <div
            className={`mt-2 px-2 py-1.5 rounded border text-[11px] font-medium flex items-center gap-1.5 ${
              saveConflict
                ? `${styles.warningBg} ${styles.warningBorder} ${styles.warningText}`
                : `${styles.dangerBg} ${styles.dangerBorder} ${styles.dangerText}`
            }`}
          >
            <AlertTriangle size={12} />
            <span>
              {saveConflict && saveError.currentVersion !== undefined
                ? t("scenario.sandbox.saveConflict", {
                    cur: String(saveError.currentVersion),
                  })
                : t("scenario.sandbox.saveError", {
                    msg: saveError.message || "?",
                  })}
            </span>
          </div>
        )}
      </div>

      {/* ── palette + canvas ──────────────────────────── */}
      <div className="flex-1 flex min-h-0">
        <aside
          className={`shrink-0 w-44 border-r ${styles.sidebarBg} ${styles.sidebarBorder} p-3 flex flex-col gap-1.5 overflow-y-auto`}
        >
          <div
            className={`text-[11px] font-semibold tracking-wide uppercase ${styles.sidebarText}`}
          >
            {t("scenario.sandbox.palette")}
          </div>
          {CATEGORIES.map((cfg) => {
            const Icon = cfg.icon;
            return (
              <button
                key={cfg.category}
                type="button"
                onClick={() => void openPaletteModal(cfg)}
                className={`flex items-center gap-2 px-2 py-1.5 rounded font-medium text-xs ${styles.sidebarHoverBg} ${styles.sidebarText} text-left cursor-pointer`}
                title={t(cfg.i18nKey)}
              >
                <Icon size={14} className={styles.sidebarActiveText} />
                <span>{t(cfg.i18nKey)}</span>
              </button>
            );
          })}
          <div
            className={`mt-2 pt-2 border-t ${styles.sidebarBorder} text-[10px] leading-normal ${styles.sidebarText}`}
          >
            {t("scenario.sandbox.hint")}
          </div>
        </aside>

        <main className="relative flex-1 min-w-0">
          <ReactFlow
            nodes={rfNodes as unknown as RfNode[]}
            edges={rfEdges as unknown as RfEdge[]}
            nodeTypes={buildNodeTypeRegistry(handleSetMindActive, handleDeleteMind, handleCopyMind)}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onNodeClick={onNodeClick}
            fitView
            nodeDragThreshold={0}
            proOptions={{ hideAttribution: true }}
            defaultEdgeOptions={{
              type: "default",
              labelBgPadding: [4, 2],
              labelBgBorderRadius: 3,
              style: { strokeWidth: 1.5 },
            }}
          >
            <Background variant={BackgroundVariant.Dots} gap={16} size={1} />
            <Controls
              showInteractive={false}
              position="bottom-right"
              className={`${styles.cardBg} ${styles.cardBorder}`}
            />
            <MiniMap
              position="bottom-left"
              className={`${styles.cardBg} ${styles.cardBorder}`}
              maskColor="rgba(0,0,0,0.3)"
              nodeColor={(nd: { type?: string }) => {
                const tt = nd.type;
                if (tt === "hub") return "var(--ecos-node-hub, #6366F1)";
                if (tt === "resource") return "var(--ecos-node-resource, #0EA5E9)";
                if (tt === "mind") return "var(--ecos-node-mind, #F59E0B)";
                if (tt === "insight") return "var(--ecos-node-insight, #10B981)";
                return "var(--ecos-node-default, #94A3B8)";
              }}
            />
          </ReactFlow>

          {/* 底部版本徽章 */}
          {layoutVersion > 0 && (
            <div
              className={`absolute left-3 bottom-3 z-10 inline-flex items-center gap-1 px-2 py-1 rounded text-[10px] ${styles.badgeBg} ${styles.badgeText}`}
              title={t("scenario.sandbox.version")}
            >
              <GitBranch size={10} />
              {t("scenario.sandbox.version")} v{layoutVersion}
            </div>
          )}
        </main>
      </div>

      {/* ── 资源 picker modal ─────────────────────────── */}
      {palette && (
        <SdResourcePickerModal
          state={palette}
          onClose={() => setPalette(null)}
          onPick={handlePalettePick}
        />
      )}

      {/* ── 节点详情 drawer ─────────────────────────────── */}
      {drawer.open && drawer.node && (
        <SdNodeDrawer
          node={drawer.node as unknown as Parameters<typeof SdNodeDrawer>[0]["node"]}
          {...drawerProps}
        />
      )}
    </div>
  );
}
