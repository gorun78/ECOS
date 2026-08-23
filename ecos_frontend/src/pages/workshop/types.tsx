import React from 'react';
import * as Icons from 'lucide-react';

// ── Local types (self-contained workshop) ──
export interface ObjectType { id: string; name: string; }
export interface ActionType { id: string; name: string; displayName: string; description: string; parameters?: any[]; }
export interface Dataset { id: string; sampleData: any[]; }

// Dynamic icon component for data-driven icon names
export const DynamicIcon = ({ name, size = 16, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || Icons.HelpCircle;
  return <Comp size={size} className={className} />;
};

// ── Local action types (for action_button widgets) ──
export const mockActionTypes: ActionType[] = [
  { id: 'update_flight_status', name: 'Update Flight Status', displayName: '更新航班状态', description: '更新指定航班的运行状态' },
  { id: 'schedule_maintenance_check', name: 'Schedule Maintenance', displayName: '安排适航维护检修', description: '为指定飞机安排维护检查' },
  { id: 'assign_pilot', name: 'Assign Pilot', displayName: '分派飞行员', description: '为航班分派飞行员' },
  { id: 'cancel_flight', name: 'Cancel Flight', displayName: '取消航班', description: '取消指定航班' },
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
