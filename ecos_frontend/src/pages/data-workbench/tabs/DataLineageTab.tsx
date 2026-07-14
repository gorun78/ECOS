/* Extracted from DataWorkbenchLayout.tsx */
import React from 'react';
import type { DataConnection, DataPipeline, DataSyncTask, DataHealthCheck, Dataset, ObjectType } from '../types';
import LineageMapView from '../LineageMapView';

interface DataLineageTabProps {
  connections: DataConnection[];
  pipelines: DataPipeline[];
  syncTasks?: DataSyncTask[];
  healthChecks?: DataHealthCheck[];
  objectTypes?: ObjectType[];
  datasets?: Dataset[];
  showToast?: (type: string, message: string) => void;
}

const DataLineageTab: React.FC<DataLineageTabProps> = ({ connections, pipelines, syncTasks = [], healthChecks = [], objectTypes = [], datasets = [], showToast }) => (
<div className="flex-1 flex flex-col min-h-0 overflow-hidden">
  <LineageMapView
    connections={connections}
    syncTasks={syncTasks}
    pipelines={pipelines}
    healthChecks={healthChecks}
    objectTypes={objectTypes}
    datasets={datasets}
  />
</div>
);

export default DataLineageTab;
