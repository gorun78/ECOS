import React from 'react';
import * as Icons from 'lucide-react';

// ── Local types (self-contained workshop) ──
export interface ObjectType { id: string; name: string; }
/** 动作目录：文案一律经 t('aiworkbench.ws.actionType.{id}.name/.desc') 解析（§六），数据面不存裸中文。 */
export interface ActionType { id: string; name: string; parameters?: unknown[]; }
export interface Dataset { id: string; sampleData: unknown[]; }

/** 预览运行时的本体对象行：来自 GET /api/v1/ontology/data，id 为真实写回（PUT）凭据。 */
export interface WorkshopObjectRow {
  id?: string;
  flightNumber?: string;
  tailNumber?: string;
  depAirport?: string;
  arrAirport?: string;
  status?: string;
  model?: string;
  manufacturer?: string;
  lastMaintenance?: string;
  assignedPilotId?: string;
  scheduledDep?: string;
}

// Dynamic icon component for data-driven icon names
export const DynamicIcon = ({ name, size = 16, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || Icons.HelpCircle;
  return <Comp size={size} className={className} />;
};

// ── Local action types (for action_button widgets) ──
export const mockActionTypes: ActionType[] = [
  { id: 'update_flight_status', name: 'Update Flight Status' },
  { id: 'schedule_maintenance_check', name: 'Schedule Maintenance' },
  { id: 'assign_pilot', name: 'Assign Pilot' },
  { id: 'cancel_flight', name: 'Cancel Flight' },
];

export interface WorkshopVariable {
  id: string;
  name: string;
  type: 'object_set' | 'object' | 'string' | 'number' | 'boolean';
  objectTypeId?: string;
  value: any;
  initialValue: any;
  description: string;
}

export interface WorkshopWidget {
  id: string;
  type: 'table' | 'chart' | 'metric' | 'object_view' | 'action_button' | 'filter_bar' | 'rich_text';
  title: string;
  slot: string;
  config: {
    dataSourceVarId?: string;
    targetVarId?: string;
    filterProperty?: string;
    columns?: string[];
    chartType?: 'bar' | 'line' | 'pie';
    groupByProperty?: string;
    metricType?: 'count' | 'sum' | 'avg';
    metricProperty?: string;
    actionTypeId?: string;
    content?: string;
    width?: string;
    height?: string;
  };
}

export interface WorkshopPage {
  id: string;
  title: string;
  icon: string;
  widgets: WorkshopWidget[];
}

export interface WorkshopApp {
  id: string;
  name: string;
  description: string;
  lastModified: string;
  isPublished: boolean;
  theme: {
    primaryColor: string;
    isDark: boolean;
    title: string;
    logo: string;
  };
  pages: WorkshopPage[];
  variables: WorkshopVariable[];
}
