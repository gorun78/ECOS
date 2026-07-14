/**
 * ECOS 项目工作台 — 共享类型定义。
 * 从 ScenarioManagementView.tsx 拆分。
 */

export interface BusinessScenario {
  id: string;
  name: string;
  description: string;
  businessGoal: string;
  department: string;
  priority: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  status: 'ACTIVE' | 'DRAFT' | 'COMPLETED' | 'SUSPENDED';
  budget: string;
  safetyIndexTarget: string;
  actualSafetyIndex: string;
  createdAt: string;
  bindings: {
    datasets: string[];
    objectTypes: string[];
    knowledgeBases: string[];
    aiAgents: string[];
    securityPolicies: string[];
    interfaces: string[];
  };
  metrics: {
    integrityScore: number;
    mappingCompleteness: number;
    threatBlockRate: number;
    slaScore: number;
  };
}

export interface ScenarioManagementViewProps {
  showToast?: (type: 'success' | 'info' | 'error', message: string) => void;
}
