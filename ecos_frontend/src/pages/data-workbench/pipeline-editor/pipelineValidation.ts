/**
 * pipelineValidation — pre-save / pre-execute local pre-checks for the
 * Pipeline canvas (Wave 3 lower T2).
 *
 * Extracted as a shared utility so the same checks are not duplicated in
 * PipelineFlowEditor and other flows.
 *
 * The basic rules are defined per nodeType (P2-01 enumeration):
 *   - SOURCE_JDBC : datasourceId + sql
 *   - SOURCE_CSV  : filePath
 *   - SOURCE_REST : url
 *   - SOURCE_CDC  : (no required fields)
 *   - TRANSFORM_SQL: transformSql
 *   - OUTPUT_OBJECT: targetTable
 *
 * Plus structural checks on the whole DAG:
 *   - Kahn's-algorithm cycle detection
 *   - every node must be reachable from at least one SOURCE_*
 *     AND reach at least one OUTPUT_OBJECT (or be output/source itself)
 * @license Apache-2.0
 */

import type { Node } from '@xyflow/react';
import type { NodeConfig, PipelineNodeType } from './types';

export type ValidationKey = 'missingConfig' | 'cycle' | 'reachability';

/** A single validation failure — typed so callers can compose messages. */
export interface ValidationIssue {
  key: ValidationKey;
  nodeId: string;
  reason: string; // i18n-rendered by caller; here it carries the field name
}

/** Required fields per P2-01 node type (NodeConfig.config keys). */
const REQUIRED_CONFIG_FIELDS: Record<PipelineNodeType, readonly string[]> = {
  SOURCE_JDBC: ['datasourceId', 'sql'],
  SOURCE_CSV: ['filePath'],
  SOURCE_REST: ['url'],
  SOURCE_CDC: [],
  // SOURCE_MINIO 必填字段随 zone 分支（STRUCTURED 需 table；UNSTRUCTURED 需 docId+originalFileName；
  // 显式 objectName 时均免），由后端执行器强制校验，故此处不设静态必填项。
  SOURCE_MINIO: [],
  TRANSFORM_SQL: ['transformSql'],
  TRANSFORM_UDF: ['udfId'],
  // TRANSFORM_DOC_PARSE（B6-2）：docId 为必填；chunkSize/chunkOverlap 有后端默认值与合法性校验
  TRANSFORM_DOC_PARSE: ['docId'],
  JOIN: ['joinType'],
  SINK: ['targetTable'],
  SINK_MINIO: ['table'],
  OUTPUT_OBJECT: ['targetTable'],
};

const SOURCE_TYPES = new Set<PipelineNodeType>(
  ['SOURCE_JDBC', 'SOURCE_CSV', 'SOURCE_REST', 'SOURCE_CDC', 'SOURCE_MINIO']);
const OUTPUT_TYPES = new Set<PipelineNodeType>(['OUTPUT_OBJECT', 'SINK', 'SINK_MINIO']);

/** ReadNodeConfig helper — stable across node id changes. */
function readNodeConfig(node: Node): NodeConfig {
  return (node.data ?? {}) as unknown as NodeConfig;
}

function nodeTypeOf(node: Node): PipelineNodeType {
  const cfg = readNodeConfig(node);
  return cfg.nodeType || (node.type as PipelineNodeType) || 'TRANSFORM_SQL';
}

/**
 * Rule 1 — required config check.
 * Returns one issue per missing field on the right node(s). `reason` is the
 * missing field name (caller turns it into a localized message).
 */
export function checkRequiredConfigs(nodes: Node[]): ValidationIssue[] {
  const issues: ValidationIssue[] = [];
  for (const node of nodes) {
    const nodeType = nodeTypeOf(node);
    const cfg = readNodeConfig(node);
    const cfgObj = cfg.config || {};
    const required = REQUIRED_CONFIG_FIELDS[nodeType] || [];
    for (const field of required) {
      const value = (cfgObj as Record<string, unknown>)[field];
      const missing = value === undefined || value === null || value === '' ||
        (typeof value === 'string' && value.trim() === '');
      if (missing) {
        issues.push({ key: 'missingConfig', nodeId: node.id, reason: field });
      }
    }
  }
  return issues;
}

/**
 * Rule 2 — cycle detection via Kahn's algorithm.
 * Nodes that still have a positive in-degree after full topological
 * processing all belong to (or downstream of) the cycle.
 */
export function findCycleNodes(nodes: Node[], edges: { source: string; target: string }[]): Set<string> {
  const inDegree = new Map<string, number>();
  const outAdjs = new Map<string, string[]>();
  for (const n of nodes) {
    inDegree.set(n.id, 0);
    outAdjs.set(n.id, []);
  }
  for (const e of edges) {
    if (!inDegree.has(e.source) || !inDegree.has(e.target)) continue;
    inDegree.set(e.target, (inDegree.get(e.target) || 0) + 1);
    const arr = outAdjs.get(e.source);
    if (arr) arr.push(e.target);
    else outAdjs.set(e.source, [e.target]);
  }
  // Kahn iteration (BFS over zero in-degree nodes).
  const queue: string[] = [];
  for (const [id, d] of inDegree.entries()) {
    if (d === 0) queue.push(id);
  }
  const visited = new Set<string>();
  while (queue.length > 0) {
    const cur = queue.shift()!;
    visited.add(cur);
    for (const next of outAdjs.get(cur) || []) {
      const d = (inDegree.get(next) || 0) - 1;
      inDegree.set(next, d);
      if (d === 0) queue.push(next);
    }
  }
  // Anything not visited is part of (or reachable from) the cycle.
  const cyclic = new Set<string>();
  for (const id of inDegree.keys()) {
    if (!visited.has(id)) cyclic.add(id);
  }
  return cyclic;
}

/**
 * Rule 3 — reachability.
 * Forward BFS from SOURCE_* → all reachable nodes.
 * Backward BFS from OUTPUT_OBJECT → all nodes that can reach an output.
 * A non-source node not reached forward OR a non-output node not reaching an
 * output is orphaned (e.g. free-floating node or dead-end).
 */
export function findOrphanedNodes(nodes: Node[], edges: { source: string; target: string }[]): Set<string> {
  const forward = new Map<string, string[]>();
  const backward = new Map<string, string[]>();
  for (const n of nodes) {
    forward.set(n.id, []);
    backward.set(n.id, []);
  }
  for (const e of edges) {
    const f = forward.get(e.source);
    if (f) f.push(e.target);
    const b = backward.get(e.target);
    if (b) b.push(e.source);
  }

  const sources: string[] = [];
  const sinks: string[] = [];
  for (const n of nodes) {
    const t = nodeTypeOf(n);
    if (SOURCE_TYPES.has(t)) sources.push(n.id);
    else if (OUTPUT_TYPES.has(t)) sinks.push(n.id);
  }

  const reachForward = new Set<string>();
  bfs(sources, forward, reachForward);
  const reachBackward = new Set<string>();
  bfs(sinks, backward, reachBackward);

  const orphaned = new Set<string>();
  for (const n of nodes) {
    const t = nodeTypeOf(n);
    const isSource = SOURCE_TYPES.has(t);
    const isSink = OUTPUT_TYPES.has(t);
    const fwd = reachForward.has(n.id) || isSource;
    const bwd = reachBackward.has(n.id) || isSink;
    if (!fwd || !bwd) orphaned.add(n.id);
  }
  return orphaned;
}

function bfs(starts: string[], adj: Map<string, string[]>, out: Set<string>): void {
  const queue = [...starts];
  for (const s of queue) out.add(s);
  while (queue.length > 0) {
    const cur = queue.shift()!;
    for (const next of adj.get(cur) || []) {
      if (!out.has(next)) {
        out.add(next);
        queue.push(next);
      }
    }
  }
}

/**
 * Combined pre-check. Returns:
 *   - issue: text keys (caller localizes with i18n);
 *   - invalidNodeIds: nodes to highlight red (union of all failures);
 *   - ok: everything passed.
 */
export function runPreFlightCheck(
  nodes: Node[],
  edges: { source: string; target: string }[]
): { ok: boolean; issues: ValidationIssue[]; invalidNodeIds: Set<string> } {
  const issues: ValidationIssue[] = [];
  const invalid = new Set<string>();
  if (nodes.length === 0) {
    issues.push({ key: 'missingConfig', nodeId: '', reason: 'canvasEmpty' });
    return { ok: false, issues, invalidNodeIds: invalid };
  }
  issues.push(...checkRequiredConfigs(nodes));
  const cycleNodes = findCycleNodes(nodes, edges);
  if (cycleNodes.size > 0) {
    issues.push({ key: 'cycle', nodeId: '', reason: '' });
  }
  const orphans = findOrphanedNodes(nodes, edges);
  if (orphans.size > 0) {
    issues.push({ key: 'reachability', nodeId: '', reason: '' });
  }
  for (const issue of issues) {
    if (issue.key === 'missingConfig' && issue.nodeId) invalid.add(issue.nodeId);
  }
  for (const id of cycleNodes) invalid.add(id);
  for (const id of orphans) invalid.add(id);
  return { ok: issues.length === 0, issues, invalidNodeIds: invalid };
}

// Re-exported to keep the shared surface stable.
export const REQUIRED_FIELDS_BY_TYPE = REQUIRED_CONFIG_FIELDS;