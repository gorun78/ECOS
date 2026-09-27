/**
 * ECOS 场景工作台 — 共享类型定义。
 * 从 ScenarioManagementView.tsx 拆分。
 */

/** 场景工作台的真实连边覆盖率（后端 /graph 端点返回；PMO-66 B2 引入，§0.6.2.3）。 */
export interface Coverage {
  /** 数据集→本体实体 真实连接覆盖率（0~1，无 OBJECT_TYPE = 1） */
  d2iCoverage: number;
  /** 知识→Agent 真实连线覆盖率（0~1，无 AI_AGENT = 1） */
  k2wCoverage: number;
  totalNodes: number;
  totalLinks: number;
}

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
  /** 可选：后端 /graph 返回的真实连边覆盖率（PMO-66 新增，未绑定联动时为 0 / 空） */
  coverage?: Coverage;
}

export interface ScenarioManagementViewProps {
  showToast?: (type: 'success' | 'info' | 'error', message: string) => void;
}
