import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

const API_BASE = '/api/v1';

export function useAgentRuntime() {
  return useQuery({
    queryKey: ['agent-runtime', 'metrics'],
    queryFn: async () => {
      const resp = await fetch(`${API_BASE}/agent-runtime/telemetry/default`);
      if (!resp.ok) throw new Error('Failed to fetch agent metrics');
      const json = await resp.json();
      return json.data || json;
    },
  });
}

export function useCreatePlan() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (goal: { id: string; description: string; priority: number }) => {
      const resp = await fetch(`${API_BASE}/agent-runtime/plans`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(goal),
      });
      if (!resp.ok) throw new Error('Failed to create plan');
      const json = await resp.json();
      return json.data || json;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['agent-runtime'] });
    },
  });
}

export function useWorldModelState() {
  return useQuery({
    queryKey: ['world-model', 'state'],
    queryFn: async () => {
      const resp = await fetch(`${API_BASE}/world-model/state`);
      if (!resp.ok) throw new Error('Failed to fetch world state');
      const json = await resp.json();
      return json.data || json;
    },
  });
}

export function useCausalGraph() {
  return useQuery({
    queryKey: ['world-model', 'causal-graph'],
    queryFn: async () => {
      const resp = await fetch(`${API_BASE}/world-model/causal-graph`);
      if (!resp.ok) throw new Error('Failed to fetch causal graph');
      const json = await resp.json();
      return json.data || json || [];
    },
  });
}

export function useRunSimulation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (scenario: Record<string, unknown>) => {
      const resp = await fetch(`${API_BASE}/world-model/scenarios`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(scenario),
      });
      if (!resp.ok) throw new Error('Simulation failed');
      const json = await resp.json();
      return json.data || json;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['world-model'] });
    },
  });
}

export function useStrategyRecommendation(goal: string | null) {
  return useQuery({
    queryKey: ['world-model', 'strategy', goal],
    queryFn: async () => {
      const resp = await fetch(`${API_BASE}/world-model/strategy/recommend?goal=${encodeURIComponent(goal!)}`, {
        method: 'POST',
      });
      if (!resp.ok) throw new Error('Strategy recommendation failed');
      const json = await resp.json();
      return json.data || json;
    },
    enabled: !!goal,
  });
}

export function useRagQuery() {
  return useMutation({
    mutationFn: async (request: { query: string; topK?: number; useGraph?: boolean; useVector?: boolean }) => {
      const resp = await fetch(`${API_BASE}/knowledge/rag`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
      });
      if (!resp.ok) throw new Error('RAG query failed');
      const json = await resp.json();
      return json.data || json;
    },
  });
}

export function useCompileOntology() {
  return useMutation({
    mutationFn: async (request: Record<string, unknown>) => {
      const resp = await fetch(`${API_BASE}/ontology/compiler/compile`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
      });
      if (!resp.ok) throw new Error('Compilation failed');
      const json = await resp.json();
      return json.data || json;
    },
  });
}
