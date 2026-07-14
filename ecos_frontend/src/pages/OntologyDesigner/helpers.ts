/**
 * OntologyDesigner helpers — 类型常量
 */
import type { OntologyEntity, OntologyProperty, OntologyRelationship } from "../../api";

export const ONTOLOGY_ID = "ont001";

export const PROP_TYPES = ["STRING", "INTEGER", "DECIMAL", "DATE", "DATETIME", "BOOLEAN", "TEXT", "FUNCTION"] as const;
export const FUNCTION_TYPES = ["EXPRESSION", "AGGREGATION", "LOOKUP"] as const;
export const REL_TYPES = ["ONE_TO_ONE", "ONE_TO_MANY", "MANY_TO_ONE", "MANY_TO_MANY"] as const;

export const entTypeLabel = (t: string) => t === "MASTER" ? "主数据" : t === "TRANSACTION" ? "事务" : t;

export type { OntologyEntity, OntologyProperty, OntologyRelationship };

// Graph data builders
export function buildGraphNodes(entities: OntologyEntity[], properties: Record<string, OntologyProperty[]>) {
  return entities.map(e => ({
    id: e.id,
    type: e.entityType === "MASTER" ? "source" : "dataset",
    label: `${e.code} (${e.name})`,
    status: "Ready",
    properties: { entityCode: e.code, entityType: e.entityType },
  }));
}

export function buildGraphLinks(relationships: OntologyRelationship[]) {
  return relationships.map(r => ({
    id: r.id,
    source: r.sourceEntityId,
    target: r.targetEntityId,
  }));
}
