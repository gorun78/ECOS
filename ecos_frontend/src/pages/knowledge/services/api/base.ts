// B.9 / K-55 — 知识域 API base 常量单源。
// 原先 knowledgeApi.ts 内 KNOWLEDGE_BASE / GRAPH_BASE / KB_V1 等三套 base 别名分散，
// 现收敛到本文件唯一来源，各 Tab 模块统一从 './base' 取用。
export const KNOWLEDGE_BASE = '/api/v1/knowledge';
export const GRAPH_BASE = '/api/v1/knowledge';
export const GLOSSARY_BASE = '/api/v1/ontology/glossary';
export const CATALOG_BASE = '/api/v1/catalog';
export const COGNITIVE_BASE = '/api/v1/cognitive';
export const RULES_BASE = '/api/v1/knowledge/compliance-rules';
export const KB_V1 = '/api/v1/knowledge';
