import { create } from 'zustand';
import { devtools } from 'zustand/middleware';

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

export interface AgentState {
  agents: AgentDefinition[];
  selectedAgentId: string | null;
  missions: Mission[];
  currentMissionId: string | null;
  executionPlans: ExecutionPlan[];
  loading: boolean;
  error: string | null;

  fetchAgents: () => Promise<void>;
  selectAgent: (id: string | null) => void;
  fetchMissions: () => Promise<void>;
  selectMission: (id: string | null) => void;
  createMission: (mission: Partial<Mission>) => Promise<void>;
  executeMission: (id: string) => Promise<void>;
  setError: (error: string | null) => void;
}

export const useAgentStore = create<AgentState>()(
  devtools(
    (set, get) => ({
      agents: [],
      selectedAgentId: null,
      missions: [],
      currentMissionId: null,
      executionPlans: [],
      loading: false,
      error: null,

      fetchAgents: async () => {
        set({ loading: true, error: null });
        try {
          const resp = await fetch('/api/ecos/agent-mesh/agents');
          if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
          const json = await resp.json();
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
          const resp = await fetch('/api/ecos/agent-mesh/missions');
          if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
          const json = await resp.json();
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
          const resp = await fetch('/api/ecos/agent-mesh/missions', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(mission),
          });
          if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
          await get().fetchMissions();
        } catch (err: unknown) {
          const message = err instanceof Error ? err.message : 'Failed to create mission';
          set({ error: message });
        }
      },

      executeMission: async (id) => {
        set({ error: null });
        try {
          const resp = await fetch(`/api/ecos/agent-mesh/missions/${id}/execute`, {
            method: 'POST',
          });
          if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
          await get().fetchMissions();
        } catch (err: unknown) {
          const message = err instanceof Error ? err.message : 'Failed to execute mission';
          set({ error: message });
        }
      },

      setError: (error) => set({ error }),
    }),
    { name: 'agent-store' }
  )
);
