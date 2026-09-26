export interface DiagContext { d: number; i: number; k: number; }
export interface Belief { variableName: string; distribution: { outcome: string; prob: number }[]; version: number; status: string; }
export interface Hypothesis { id: string; code: string; statement: string; domain?: string; status: string; isValid: boolean; }
export interface Evidence { id: string; code: string; sourceType: string; confidence: number; isConflict: boolean; }
export type ReasoningMode = 'HYBRID' | 'CAUSAL' | 'SCENARIO' | 'VECTOR_RAG';
