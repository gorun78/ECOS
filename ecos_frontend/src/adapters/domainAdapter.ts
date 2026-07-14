/**
 * 域适配器 (Domain Adapter)
 *
 * 由于现有后端没有 Domain API，需要在前端通过 Adapter 层从现有数据中提取域信息。
 *
 * Domain 数据来源策略（优先级从高到低）：
 *   1. 从 knowledge-graph API 的 nodes 中提取 entity.domain 字段
 *   2. 如果没有 domain 字段，根据 entity.entityType 或命名前缀分组
 *   3. localStorage 缓存用户手动创建的域（仅前端存储）
 *   4. 【v1.1】从术语库 GlossaryTerm.domain 字段中提取域名称
 *
 * 未来后端加入 Domain API 后（GET /api/v1/ecos/domains），
 * 只需替换此文件的实现即可平滑切换。
 */

import type { KnowledgeGraphResponse, Domain, KGNode, KGEdge } from "../types/workbench";

/**
 * 从知识图谱数据中提取域信息
 *
 * 策略：按 node.domain 字段分组，统计每个域下的实体数量和关系数量。
 *
 * @param kgData - 知识图谱 API 响应数据
 * @returns 域列表（按实体数降序排列）
 */
export function extractDomainsFromKG(kgData: KnowledgeGraphResponse): Domain[] {
  const domainMap = new Map<string, Domain>();

  // ── 第一遍：遍历 nodes，按 domain 字段分组 ──
  kgData.nodes.forEach((node: KGNode) => {
    // 获取域编码：优先使用 node.domain，否则 fallback 为 'default'
    const domainCode = node.domain || "default";

    if (!domainMap.has(domainCode)) {
      domainMap.set(domainCode, {
        code: domainCode,
        name: node.domainName || formatDomainName(domainCode),
        description: undefined,
        entities: [],
        entityCount: 0,
        relationshipCount: 0,
        status: "active",
      });
    }

    const domain = domainMap.get(domainCode)!;

    // 使用 entityCode 或 node.id 作为实体标识
    const entityRef = node.entityCode || node.id;
    if (!domain.entities.includes(entityRef)) {
      domain.entities.push(entityRef);
    }
    domain.entityCount = domain.entities.length;
  });

  // ── 第二遍：遍历 edges，统计每个域的关系数 ──
  kgData.edges.forEach((edge: KGEdge) => {
    const sourceNode = kgData.nodes.find(
      (n: KGNode) => n.id === edge.source
    );
    const targetNode = kgData.nodes.find(
      (n: KGNode) => n.id === edge.target
    );

    const sourceDomain = sourceNode?.domain;
    const targetDomain = targetNode?.domain;

    // 给源域计数
    if (sourceDomain && domainMap.has(sourceDomain)) {
      domainMap.get(sourceDomain)!.relationshipCount++;
    }

    // 给目标域计数（跨域关系也计入目标域）
    if (
      targetDomain &&
      targetDomain !== sourceDomain &&
      domainMap.has(targetDomain)
    ) {
      domainMap.get(targetDomain)!.relationshipCount++;
    }
  });

  // ── 按实体数量降序排列返回 ──
  return Array.from(domainMap.values()).sort(
    (a, b) => b.entityCount - a.entityCount
  );
}

/**
 * 格式化域编码为可读的域名称
 *
 * 例如: "supply_chain" → "Supply Chain", "hr" → "HR"
 *
 * @param code - 域编码
 * @returns 格式化后的域名称
 */
function formatDomainName(code: string): string {
  if (code === "default") return "默认域";

  // 下划线/连字符分割 → 首字母大写
  return code
    .split(/[_-]/)
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(" ");
}

/**
 * 根据域编码查找域内实体 ID 列表
 *
 * @param domainCode - 域编码
 * @param kgData - 知识图谱数据
 * @returns 域内实体 ID 列表
 */
export function getDomainEntityIds(
  domainCode: string,
  kgData: KnowledgeGraphResponse
): string[] {
  return kgData.nodes
    .filter((n: KGNode) => (n.domain || "default") === domainCode)
    .map((n: KGNode) => n.entityCode || n.id);
}

/**
 * 判断两个节点是否属于同一个域
 *
 * @param sourceNodeId - 源节点 ID
 * @param targetNodeId - 目标节点 ID
 * @param kgData - 知识图谱数据
 * @returns 是否同域
 */
export function isSameDomain(
  sourceNodeId: string,
  targetNodeId: string,
  kgData: KnowledgeGraphResponse
): boolean {
  const sourceNode = kgData.nodes.find((n: KGNode) => n.id === sourceNodeId);
  const targetNode = kgData.nodes.find((n: KGNode) => n.id === targetNodeId);

  if (!sourceNode || !targetNode) return false;
  return (sourceNode.domain || "default") === (targetNode.domain || "default");
}
