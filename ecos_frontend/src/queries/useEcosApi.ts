import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  fetchWorldStateJson,
  fetchCausalGraphJson,
  postSimulationJson,
  postStrategyRecommendJson,
} from '../services/worldModelGraphApi';
import { fetchAgentTelemetryJson, createRuntimePlanJson } from '../services/agentMeshApi';
import { ragQueryRaw, compileOntologyRaw } from '../services/knowledgeQueryApi';

export function useAgentRuntime() {
  return useQuery({
    queryKey: ['agent-runtime', 'metrics'],
    queryFn: async () => {
      const json = await fetchAgentTelemetryJson('Failed to fetch agent metrics');
      return json.data || json;
    },
  });
}

export function useCreatePlan() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (goal: { id: string; description: string; priority: number }) => {
      const json = await createRuntimePlanJson(goal, 'Failed to create plan');
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
      const json = await fetchWorldStateJson('Failed to fetch world state');
      return json.data || json;
    },
  });
}

export function useCausalGraph() {
  return useQuery({
    queryKey: ['world-model', 'causal-graph'],
    queryFn: async () => {
      const json = await fetchCausalGraphJson('Failed to fetch causal graph');
      return json.data || json || [];
    },
  });
}

export function useRunSimulation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (scenario: Record<string, unknown>) => {
      const json = await postSimulationJson(scenario, 'Simulation failed');
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
      const json = await postStrategyRecommendJson(goal!, 'Strategy recommendation failed');
      return json.data || json;
    },
    enabled: !!goal,
  });
}

export function useRagQuery() {
  return useMutation({
    mutationFn: async (request: { query: string; topK?: number; useGraph?: boolean; useVector?: boolean }) => {
      const json = await ragQueryRaw(request, 'RAG query failed');
      return json.data || json;
    },
  });
}

export function useCompileOntology() {
  return useMutation({
    mutationFn: async (request: Record<string, unknown>) => {
      const json = await compileOntologyRaw(request, 'Compilation failed');
      return json.data || json;
    },
  });
}
