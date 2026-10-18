import { create } from 'zustand';
import { devtools } from 'zustand/middleware';
import {
  fetchMeshAgentsJson,
  fetchMeshMissionsJson,
  createMeshMissionJson,
  executeMeshMissionJson,
} from '../../services/agentMeshApi';

export interface AgentDefinition {
  id: string;
  name: string;
  type: string;
  role: string;
  capability: Record<string, unknown>;
  status: string;
}

export interface ExecutionPlan {
  id: string;
  goalId: string;
  tasks: ExecutionTask[];
  status: string;
}

export interface ExecutionTask {
  id: string;
  planId: string;
  agentId: string;
  instruction: string;
  toolType: string;
  status: string;
}

export interface Mission {
  id: string;
  title: string;
  goal: string;
  mode: 'SUPERVISOR' | 'SWARM' | 'PIPELINE' | 'DEBATE';
  status: string;
  result: string | null;
}

/**
 * Run 上下文（篇13 §13.4.5：`meta` 帧建立 Run 上下文并写入 useAgentStore）。
 * 单源在此，面板读同一份；值逐字来自后端 meta 帧，缺失即缺失标记（-1 / "-"），禁补零补串。
 */
export interface AgentRunContext {
  traceId: string;
  sessionId: string;
  model: string;
  contextLimit: number;
  estimatedTokens: number;
  /** 承流通道：sse=实时七帧流；poll=断线达阈值后的非流式兜底（§4.4，不得声称实时） */
  channel: 'sse' | 'poll';
}

export interface AgentState {
  agents: AgentDefinition[];
  selectedAgentId: string | null;
  missions: Mission[];
  currentMissionId: string | null;
  executionPlans: ExecutionPlan[];
  loading: boolean;
  error: string | null;
  runContext: AgentRunContext | null;

  fetchAgents: () => Promise<void>;
  selectAgent: (id: string | null) => void;
  fetchMissions: () => Promise<void>;
  selectMission: (id: string | null) => void;
  createMission: (mission: Partial<Mission>) => Promise<void>;
  executeMission: (id: string) => Promise<void>;
  setError: (error: string | null) => void;
  setRunContext: (context: AgentRunContext | null) => void;
}

export const useAgentStore = create<AgentState>()(
  devtools(
    (set, get) => ({
      agents: [] as AgentDefinition[],
      selectedAgentId: null as string | null,
      missions: [] as Mission[],
      currentMissionId: null as string | null,
      executionPlans: [] as ExecutionPlan[],
      loading: false,
      error: null as string | null,
      runContext: null as AgentRunContext | null,

      fetchAgents: async () => {
        set({ loading: true, error: null });
        try {
          const json = await fetchMeshAgentsJson();
          set({ agents: json.data || json || [], loading: false });
        } catch (err: unknown) {
          const message = err instanceof Error ? err.message : 'Failed to fetch agents';
          set({ loading: false, error: message });
        }
      },

      selectAgent: (id) => set({ selectedAgentId: id }),

      fetchMissions: async () => {
        set({ loading: true, error: null });
        try {
          const json = await fetchMeshMissionsJson();
          set({ missions: json.data || json || [], loading: false });
        } catch (err: unknown) {
          const message = err instanceof Error ? err.message : 'Failed to fetch missions';
          set({ loading: false, error: message });
        }
      },

      selectMission: (id) => set({ currentMissionId: id }),

      createMission: async (mission) => {
        set({ error: null });
        try {
          await createMeshMissionJson(mission);
          await get().fetchMissions();
        } catch (err: unknown) {
          const message = err instanceof Error ? err.message : 'Failed to create mission';
          set({ error: message });
        }
      },

      executeMission: async (id) => {
        set({ error: null });
        try {
          await executeMeshMissionJson(id);
          await get().fetchMissions();
        } catch (err: unknown) {
          const message = err instanceof Error ? err.message : 'Failed to execute mission';
          set({ error: message });
        }
      },

      setError: (error) => set({ error }),

      setRunContext: (context) => set({ runContext: context }),
    }),
    { name: 'agent-store' }
  )
);
