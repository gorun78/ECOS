import { create } from 'zustand';
import { devtools } from 'zustand/middleware';

export interface WorldState {
  id: string;
  timestamp: string;
  stateData: Record<string, unknown>;
}

export interface Scenario {
  id: string;
  name: string;
  type: string;
  assumptions: Record<string, unknown>;
  description: string;
}

export interface SimulationResult {
  id: string;
  scenarioId: string;
  status: string;
  confidence: number;
  summary: string;
}

export interface StrategyRecommendation {
  id: string;
  goal: string;
  actions: string[];
  expectedImpact: number;
  riskLevel: number;
  reasoning: string;
}

export interface CausalEdge {
  id: string;
  sourceNode: string;
  targetNode: string;
  weight: number;
}

export interface CognitiveState {
  worldState: WorldState | null;
  scenarios: Scenario[];
  currentScenarioId: string | null;
  simulationResult: SimulationResult | null;
  strategyRecommendation: StrategyRecommendation | null;
  causalGraph: CausalEdge[];
  loading: boolean;
  error: string | null;

  fetchWorldState: () => Promise<void>;
  fetchScenarios: () => Promise<void>;
  selectScenario: (id: string | null) => void;
  runSimulation: (scenario: Partial<Scenario>) => Promise<void>;
  getStrategyRecommendation: (goal: string) => Promise<void>;
  fetchCausalGraph: () => Promise<void>;
  setError: (error: string | null) => void;
}

export const useCognitiveStore = create<CognitiveState>()(
  devtools(
    (set, get) => ({
      worldState: null,
      scenarios: [],
      currentScenarioId: null,
      simulationResult: null,
      strategyRecommendation: null,
      causalGraph: [],
      loading: false,
      error: null,

      fetchWorldState: async () => {
        set({ loading: true, error: null });
        try {
          const resp = await fetch('/api/v1/world-model/state');
          if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
          const json = await resp.json();
          set({ worldState: json.data || json, loading: false });
        } catch (err: unknown) {
          const message = err instanceof Error ? err.message : 'Failed to fetch world state';
          set({ loading: false, error: message });
        }
      },

      fetchScenarios: async () => {
        set({ loading: true, error: null });
        try {
          const resp = await fetch('/api/v1/world-model/scenarios');
          if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
          const json = await resp.json();
          set({ scenarios: json.data || json || [], loading: false });
        } catch (err: unknown) {
          const message = err instanceof Error ? err.message : 'Failed to fetch scenarios';
          set({ loading: false, error: message });
        }
      },

      selectScenario: (id) => set({ currentScenarioId: id }),

      runSimulation: async (scenario) => {
        set({ loading: true, error: null });
        try {
          const resp = await fetch('/api/v1/world-model/scenarios', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(scenario),
          });
          if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
          const json = await resp.json();
          set({ simulationResult: json.data || json, loading: false });
        } catch (err: unknown) {
          const message = err instanceof Error ? err.message : 'Simulation failed';
          set({ loading: false, error: message });
        }
      },

      getStrategyRecommendation: async (goal) => {
        set({ loading: true, error: null });
        try {
          const resp = await fetch(`/api/v1/world-model/strategy/recommend?goal=${encodeURIComponent(goal)}`, {
            method: 'POST',
          });
          if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
          const json = await resp.json();
          set({ strategyRecommendation: json.data || json, loading: false });
        } catch (err: unknown) {
          const message = err instanceof Error ? err.message : 'Strategy recommendation failed';
          set({ loading: false, error: message });
        }
      },

      fetchCausalGraph: async () => {
        set({ loading: true, error: null });
        try {
          const resp = await fetch('/api/v1/world-model/causal-graph');
          if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
          const json = await resp.json();
          set({ causalGraph: json.data || json || [], loading: false });
        } catch (err: unknown) {
          const message = err instanceof Error ? err.message : 'Failed to fetch causal graph';
          set({ loading: false, error: message });
        }
      },

      setError: (error) => set({ error }),
    }),
    { name: 'cognitive-store' }
  )
);
