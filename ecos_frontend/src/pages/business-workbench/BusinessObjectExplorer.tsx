/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useMemo, useEffect } from 'react';
import { ObjectType, LinkType, ActionType, Dataset } from '../../types/ontology';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
// 鉴权头单源定义见 services/auth.ts (H6-T1)
import { fetchOntologyDataJson } from '../../services/ontologyWorkbenchApi';
import {
  SavedSearch,
  FilterQuery,
  ResolvedRelation,
  normalizeObjectTypes,
} from './businessObjectExplorerTypes';
import { ExplorerSidebar, ExplorerWelcome } from './BusinessObjectExplorerSidebar';
import { ExplorerHeader } from './BusinessObjectExplorerHeader';
import { ExplorerTable } from './BusinessObjectExplorerTable';
import { ExplorerAnalytics } from './BusinessObjectExplorerAnalytics';
import { ExplorerDetailPanel } from './BusinessObjectExplorerDetailPanel';
import { SaveSearchListModal, ExecuteActionModal } from './BusinessObjectExplorerModals';

// ── Backend API helpers ────────────────────────────────────────
// ObjectExplorerView augments its prop/seed data with real ontology instance
// data fetched from the ECOS backend. On any failure it gracefully degrades
// to the seed data supplied via props (mockObjectTypes from seedData.ts).
// (normalizeObjectTypes / SavedSearch / FilterQuery 已抽至 businessObjectExplorerTypes.ts — H6-T4 拆分)

interface ObjectExplorerViewProps {
  objectTypes: ObjectType[];
  linkTypes: LinkType[];
  actionTypes: ActionType[];
  datasets: Dataset[];
  onUpdateDatasets: (updated: Dataset[]) => void;
  showToast: (type: 'success' | 'info' | 'error', message: string) => void;
  initialActiveObjectTypeId?: string | null;
  onActiveObjectTypeIdChange?: (id: string | null) => void;
}

export default function BusinessObjectExplorer({
  objectTypes: propObjectTypes,
  linkTypes,
  actionTypes,
  datasets,
  onUpdateDatasets,
  showToast,
  initialActiveObjectTypeId = null,
  onActiveObjectTypeIdChange
}: ObjectExplorerViewProps) {
  // ── Backend API integration state ─────────────────────────────
  // Real ontology data fetched from GET /api/v1/ontology/data is merged on
  // top of the seed/object types supplied via props. Prop items take
  // precedence for a given id (they carry the rich mapping + sampleData
  // needed for instance instantiation); API only contributes NEW types.
  const [apiObjectTypes, setApiObjectTypes] = useState<ObjectType[]>([]);
  const [apiLoading, setApiLoading] = useState(false);
  const [apiError, setApiError] = useState<string | null>(null);
  const { styles } = useTheme();
  const { t } = useLanguage();

  // Effective object types = props ∪ API extras (deduped by id).
  const objectTypes = useMemo<ObjectType[]>(() => {
    const seen = new Set(propObjectTypes.map(o => o.id));
    const extras = apiObjectTypes.filter(o => !seen.has(o.id));
    return [...propObjectTypes, ...extras];
  }, [propObjectTypes, apiObjectTypes]);

  // Load ontology browser data from backend on mount.
  // On failure we keep using the prop/seed data (graceful degradation).
  useEffect(() => {
    let cancelled = false;
    setApiLoading(true);
    fetchOntologyDataJson()
      .then((resp: any) => {
        if (cancelled) return;
        // Backend signals success with code === 0 (and sometimes code === 200).
        if (resp && (resp.code === 0 || resp.code === 200)) {
          const items = normalizeObjectTypes(resp.data);
          if (items.length > 0) {
            setApiObjectTypes(items);
            showToast('success', t('ow.boe.loaded', { n: items.length }));
          }
        } else if (resp && resp.code && resp.code !== 0 && resp.code !== 200) {
          // Explicit backend error code — surface it but keep seed data.
          setApiError(resp.message || t('ow.boe.backendErr', { code: String(resp.code) }));
          console.error('Failed to load ontology data:', resp.message || resp);
        }
        setApiLoading(false);
      })
      .catch((err: Error) => {
        if (cancelled) return;
        console.error('Failed to load ontology data:', err);
        setApiError(err.message);
        setApiLoading(false);
        // Graceful fallback: apiObjectTypes stays [], so `objectTypes`
        // reduces to the prop/seed data — UI continues to work.
      });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Navigation & View States
  const [activeObjectTypeId, setActiveObjectTypeId] = useState<string | null>(initialActiveObjectTypeId);
  const [selectedInstance, setSelectedInstance] = useState<any | null>(null);
  const [activeTab, setActiveTab] = useState<'table' | 'analytics'>('table');
  const [detailTab, setDetailTab] = useState<'properties' | 'relations' | 'activity'>('properties');

  useEffect(() => {
    // truthy guard: null (default) and undefined both mean "no pre-selection";
    // null !== undefined was the old bug — any parent re-render with null cleared user selection.
    if (initialActiveObjectTypeId) {
      setActiveObjectTypeId(prev =>
        prev === initialActiveObjectTypeId ? prev : initialActiveObjectTypeId
      );
    }
  }, [initialActiveObjectTypeId]);

  useEffect(() => {
    if (onActiveObjectTypeIdChange) {
      onActiveObjectTypeIdChange(activeObjectTypeId);
    }
  }, [activeObjectTypeId, onActiveObjectTypeIdChange]);

  // Query States
  const [localSearch, setLocalSearch] = useState('');
  const [activeFilters, setActiveFilters] = useState<FilterQuery[]>([]);
  const [sortBy, setSortBy] = useState<string>('');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc');

  // Filter creation state
  const [newFilterProp, setNewFilterProp] = useState('');
  const [newFilterOp, setNewFilterOp] = useState<'equals' | 'contains' | 'gt' | 'lt' | 'is_empty' | 'is_not_empty'>('equals');
  const [newFilterVal, setNewFilterVal] = useState('');
  const [showFilterCreator, setShowFilterCreator] = useState(false);

  // Saved searches state
  const [savedSearches, setSavedSearches] = useState<SavedSearch[]>([]);
  const [newSearchName, setNewSearchName] = useState('');
  const [showSaveModal, setShowSaveModal] = useState(false);

  // Action execution state
  const [selectedAction, setSelectedAction] = useState<ActionType | null>(null);
  const [actionParams, setActionParams] = useState<Record<string, string>>({});
  const [actionError, setActionError] = useState<string | null>(null);

  // Active object type metadata
  const activeObjectType = useMemo(() => {
    return objectTypes.find(ot => ot.id === activeObjectTypeId) || null;
  }, [objectTypes, activeObjectTypeId]);

  // Load saved searches from local storage
  useEffect(() => {
    const cached = localStorage.getItem('foundry_saved_searches');
    if (cached) {
      try {
        setSavedSearches(JSON.parse(cached));
      } catch (e) {
        console.error(e);
      }
    }
  }, []);

  // Sync saved searches
  const saveSearches = (updated: SavedSearch[]) => {
    setSavedSearches(updated);
    localStorage.setItem('foundry_saved_searches', JSON.stringify(updated));
  };

  // 1. Dynamic Object Instantiation from Mapping
  const allInstances = useMemo(() => {
    if (!activeObjectType) return [];
    // Guard: API-sourced object types may lack a mapping to a physical
    // dataset. Without one we cannot instantiate rows — return empty.
    const mapping = activeObjectType.mapping;
    if (!mapping || !mapping.datasetId) return [];
    const dsId = mapping.datasetId;
    const dataset = datasets.find(d => d.id === dsId);
    if (!dataset) return [];

    const mappings = mapping.propertyMappings || {};
    return dataset.sampleData.map((row, index) => {
      const instance: Record<string, any> = {
        _index: index,
        _datasetId: dsId,
        _objectTypeId: activeObjectType.id
      };
      activeObjectType.properties.forEach(prop => {
        const colName = mappings[prop.id];
        if (colName && row[colName] !== undefined) {
          instance[prop.id] = row[colName];
        } else {
          instance[prop.id] = null;
        }
      });
      return instance;
    });
  }, [activeObjectType, datasets]);

  // Instantiation helper for arbitrary object type (for relationships)
  const getInstancesOfObjectType = (otId: string) => {
    const ot = objectTypes.find(o => o.id === otId);
    if (!ot) return [];
    // Guard: mapping is optional — API-sourced types may not have one.
    const otMapping = ot.mapping;
    if (!otMapping || !otMapping.datasetId) return [];
    const ds = datasets.find(d => d.id === otMapping.datasetId);
    if (!ds) return [];
    const mappings = otMapping.propertyMappings || {};
    return ds.sampleData.map((row, idx) => {
      const inst: Record<string, any> = {
        _index: idx,
        _datasetId: ds.id,
        _objectTypeId: ot.id
      };
      ot.properties.forEach(prop => {
        const colName = mappings[prop.id];
        if (colName && row[colName] !== undefined) {
          inst[prop.id] = row[colName];
        } else {
          inst[prop.id] = null;
        }
      });
      return inst;
    });
  };

  // 2. Filter, Search and Sort Logic
  const processedInstances = useMemo(() => {
    let result = [...allInstances];

    // Local Search
    if (localSearch.trim()) {
      const q = localSearch.toLowerCase();
      result = result.filter(inst => {
        return Object.values(inst).some(val => 
          val !== null && val !== undefined && String(val).toLowerCase().includes(q)
        );
      });
    }

    // Active Filters
    activeFilters.forEach(f => {
      result = result.filter(inst => {
        const val = inst[f.propertyId];
        const fVal = f.value.toLowerCase();
        
        if (f.operator === 'equals') {
          return String(val ?? '').toLowerCase() === fVal;
        } else if (f.operator === 'contains') {
          return String(val ?? '').toLowerCase().includes(fVal);
        } else if (f.operator === 'gt') {
          return Number(val ?? 0) > Number(f.value);
        } else if (f.operator === 'lt') {
          return Number(val ?? 0) < Number(f.value);
        } else if (f.operator === 'is_empty') {
          return val === null || val === undefined || String(val).trim() === '';
        } else if (f.operator === 'is_not_empty') {
          return val !== null && val !== undefined && String(val).trim() !== '';
        }
        return true;
      });
    });

    // Sorting
    if (sortBy) {
      result.sort((a, b) => {
        const valA = a[sortBy];
        const valB = b[sortBy];
        
        if (valA === valB) return 0;
        if (valA === null || valA === undefined) return 1;
        if (valB === null || valB === undefined) return -1;

        const isNumeric = typeof valA === 'number' || (!isNaN(Number(valA)) && !isNaN(Number(valB)));
        if (isNumeric) {
          return sortOrder === 'asc' 
            ? Number(valA) - Number(valB)
            : Number(valB) - Number(valA);
        }

        return sortOrder === 'asc'
          ? String(valA).localeCompare(String(valB))
          : String(valB).localeCompare(String(valA));
      });
    }

    return result;
  }, [allInstances, localSearch, activeFilters, sortBy, sortOrder]);

  // Default Sort By Primary Key when Object Type changes
  useEffect(() => {
    if (activeObjectType) {
      setSortBy(activeObjectType.primaryKey);
      setSortOrder('asc');
      setActiveFilters([]);
      setLocalSearch('');
      setSelectedInstance(null);
    }
  }, [activeObjectTypeId]);

  // Reset selected instance if it disappears from processed list
  useEffect(() => {
    if (selectedInstance && activeObjectType) {
      const stillExists = processedInstances.some(inst => 
        inst[activeObjectType.primaryKey] === selectedInstance[activeObjectType.primaryKey]
      );
      if (!stillExists) {
        setSelectedInstance(null);
      }
    }
  }, [processedInstances, selectedInstance, activeObjectType]);

  // 3. Analytics Chart Data Construction
  const analyticsData: any = useMemo((): any => {
    if (!activeObjectType || processedInstances.length === 0) return [];

    // Choose the best property for categorical grouping
    // Find status, manufacturer, or model properties
    const groupProp = activeObjectType.properties.find(p => 
      p.id === 'status' || 
      p.id === 'manufacturer' || 
      p.id === 'model' || 
      p.id === 'city' || 
      p.id === 'rank'
    ) || activeObjectType.properties[0];

    const distribution: Record<string, number> = {};
    processedInstances.forEach(inst => {
      const key = String(inst[groupProp.id] || t('ow.boe.unspecified'));
      distribution[key] = (distribution[key] || 0) + 1;
    });

    return {
      property: groupProp,
      data: Object.entries(distribution).map(([name, count]) => ({
        name,
        count,
        percentage: ((count / processedInstances.length) * 100).toFixed(1)
      })).sort((a, b) => b.count - a.count)
    };
  }, [activeObjectType, processedInstances]);

  // 4. Filter Creators
  const handleAddFilter = () => {
    if (!newFilterProp) return;
    
    // Check if filter already exists for this property
    const updated = [...activeFilters, {
      propertyId: newFilterProp,
      operator: newFilterOp,
      value: newFilterVal
    }];
    setActiveFilters(updated);
    
    // Reset creators
    setNewFilterProp('');
    setNewFilterVal('');
    setShowFilterCreator(false);
    showToast('info', t('ow.boe.filterAdded'));
  };

  const handleRemoveFilter = (index: number) => {
    const updated = activeFilters.filter((_, idx) => idx !== index);
    setActiveFilters(updated);
    showToast('info', t('ow.boe.filterRemoved'));
  };

  // 5. Saved Searches Manager
  const handleSaveSearch = () => {
    if (!newSearchName.trim() || !activeObjectTypeId) return;

    const newSearch: SavedSearch = {
      id: `search_${Date.now()}`,
      name: newSearchName.trim(),
      objectTypeId: activeObjectTypeId,
      filters: activeFilters,
      sortBy,
      sortOrder
    };

    saveSearches([...savedSearches, newSearch]);
    setNewSearchName('');
    setShowSaveModal(false);
    showToast('success', t('ow.boe.savedSearchSaved', { name: newSearch.name }));
  };

  const handleLoadSavedSearch = (search: SavedSearch) => {
    setActiveObjectTypeId(search.objectTypeId);
    setActiveFilters(search.filters);
    setSortBy(search.sortBy);
    setSortOrder(search.sortOrder);
    showToast('success', t('ow.boe.savedSearchLoaded', { name: search.name }));
  };

  const handleDeleteSavedSearch = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    const updated = savedSearches.filter(s => s.id !== id);
    saveSearches(updated);
    showToast('info', t('ow.boe.savedSearchRemoved'));
  };

  // 6. Relational Connection Traversal Parser
  const resolvedRelations = useMemo<ResolvedRelation[]>(() => {
    if (!selectedInstance || !activeObjectType) return [];

    const relations: Array<{
      linkType: LinkType;
      direction: 'forward' | 'reverse';
      otherObjectType: ObjectType;
      instances: any[];
    }> = [];

    // Search linkTypes referencing the activeObjectType
    linkTypes.forEach(lt => {
      if (lt.sourceObjectType === activeObjectType.id) {
        // Forward relation (e.g. Flight -> Airport)
        const targetOt = objectTypes.find(o => o.id === lt.targetObjectType);
        if (!targetOt) return;

        const targetInstances = getInstancesOfObjectType(lt.targetObjectType);
        
        let matches: any[] = [];
        if (lt.mapping.type === 'foreign_key' && lt.mapping.foreignKeyMapping) {
          const sourceVal = selectedInstance[lt.mapping.foreignKeyMapping.sourceKey];
          matches = targetInstances.filter(t => 
            String(t[lt.mapping.foreignKeyMapping!.targetKey]) === String(sourceVal)
          );
        }

        relations.push({
          linkType: lt,
          direction: 'forward',
          otherObjectType: targetOt,
          instances: matches
        });
      } else if (lt.targetObjectType === activeObjectType.id) {
        // Reverse relation (e.g. Airport -> Flights, or Aircraft -> Flights)
        const sourceOt = objectTypes.find(o => o.id === lt.sourceObjectType);
        if (!sourceOt) return;

        const sourceInstances = getInstancesOfObjectType(lt.sourceObjectType);
        
        let matches: any[] = [];
        if (lt.mapping.type === 'foreign_key' && lt.mapping.foreignKeyMapping) {
          const targetVal = selectedInstance[lt.mapping.foreignKeyMapping.targetKey];
          matches = sourceInstances.filter(s => 
            String(s[lt.mapping.foreignKeyMapping!.sourceKey]) === String(targetVal)
          );
        } else if (lt.mapping.type === 'join_table' && lt.mapping.joinTableMapping) {
          // Many-to-Many rating relationship (e.g. Pilot -> Ratings -> Aircraft)
          // Look up pilot ratings join dataset
          const joinDs = datasets.find(d => d.id === lt.mapping.datasetId);
          if (joinDs && lt.mapping.joinTableMapping) {
            const m = lt.mapping.joinTableMapping;
            const sourceVal = selectedInstance[m.sourceKey];
            
            // Find ratings for this pilot
            const ratings = joinDs.sampleData.filter(row => 
              String(row[m.joinSourceKey]) === String(sourceVal)
            );
            
            // Get all rated aircraft models
            const models = ratings.map(r => r[m.joinTargetKey]);
            
            // Filter target aircraft instances of these models
            matches = sourceInstances.filter(s => models.includes(s[m.targetKey]));
          }
        }

        relations.push({
          linkType: lt,
          direction: 'reverse',
          otherObjectType: sourceOt,
          instances: matches
        });
      }
    });

    return relations;
  }, [selectedInstance, activeObjectType, linkTypes, objectTypes, datasets]);

  // Helper to jump to a linked object instance
  const handleJumpToInstance = (otId: string, instId: string) => {
    const ot = objectTypes.find(o => o.id === otId);
    if (!ot) return;
    
    // Jump to the type
    setActiveObjectTypeId(otId);
    
    // Find inst
    const instList = getInstancesOfObjectType(otId);
    const targetInst = instList.find(i => String(i[ot.primaryKey]) === String(instId));
    if (targetInst) {
      setSelectedInstance(targetInst);
      setDetailTab('properties');
      showToast('info', t('ow.boe.drillDown', { object: ot.displayName, id: instId }));
    }
  };

  // 7. Actions Executer & Validations with Writeback
  const availableActions = useMemo(() => {
    if (!activeObjectType) return [];
    // Actions where at least one parameter takes an object of current type
    return actionTypes.filter(at => 
      at.parameters.some(p => p.dataType === 'object' && p.objectTypeId === activeObjectType.id)
    );
  }, [actionTypes, activeObjectType]);

  const handleOpenActionModal = (action: ActionType) => {
    setSelectedAction(action);
    setActionError(null);
    
    // Pre-fill target object param
    const objParam = action.parameters.find(p => p.dataType === 'object' && p.objectTypeId === activeObjectType?.id);
    const initialParams: Record<string, string> = {};
    if (objParam && selectedInstance && activeObjectType) {
      initialParams[objParam.id] = selectedInstance[activeObjectType.primaryKey];
    }
    
    // Initialize other params with empty strings
    action.parameters.forEach(p => {
      if (p.id !== objParam?.id) {
        initialParams[p.id] = '';
      }
    });
    
    setActionParams(initialParams);
  };

  const handleExecuteAction = () => {
    if (!selectedAction || !activeObjectType || !selectedInstance) return;

    // A. VALIDATION EXPRESSIONS RUN
    let valid = true;
    let errMessage = '';

    selectedAction.validationRules.forEach(rule => {
      // Simulate validation rule evaluation for mock actions
      if (selectedAction.id === 'update_flight_status') {
        const newVal = actionParams['new_status_param'];
        const allowed = ['ON_TIME', 'DELAYED', 'BOARDING', 'CANCELLED'];
        if (!allowed.includes(newVal)) {
          valid = false;
          errMessage = rule.errorMessage;
        }
      } else if (selectedAction.id === 'schedule_maintenance_check') {
        // Can't schedule if already MAINTENANCE
        if (selectedInstance.status === 'MAINTENANCE') {
          valid = false;
          errMessage = rule.errorMessage;
        }
      }
    });

    if (!valid) {
      setActionError(errMessage);
      showToast('error', t('ow.boe.validationFailed', { msg: errMessage }));
      return;
    }

    // B. APPLY RULES & REWRITE TO RAW DATASET
    const updatedDatasets = datasets.map(dataset => {
      const isTargetDataset = dataset.id === activeObjectType.mapping.datasetId;
      if (!isTargetDataset) return dataset;

      // Locate row inside dataset sampleData using selectedInstance._index
      const sampleDataCopy = [...dataset.sampleData];
      const targetRow = { ...sampleDataCopy[selectedInstance._index] };

      selectedAction.rules.forEach(rule => {
        if (rule.type === 'modify_object') {
          rule.propertyEdits?.forEach(edit => {
            const targetCol = activeObjectType.mapping.propertyMappings[edit.propertyId];
            if (!targetCol) return;

            // Resolve value expression
            let valueToSet = edit.valueExpression;
            if (edit.valueExpression.startsWith('parameter.')) {
              const paramId = edit.valueExpression.replace('parameter.', '');
              valueToSet = actionParams[paramId];
            } else if (edit.valueExpression.startsWith('"') && edit.valueExpression.endsWith('"')) {
              valueToSet = edit.valueExpression.slice(1, -1);
            }

            targetRow[targetCol] = valueToSet;
          });
        }
      });

      sampleDataCopy[selectedInstance._index] = targetRow;
      return {
        ...dataset,
        sampleData: sampleDataCopy
      };
    });

    // Save and re-instantiate
    onUpdateDatasets(updatedDatasets);
    
    // Reload active instance
    const otId = activeObjectType.id;
    const pk = selectedInstance[activeObjectType.primaryKey];
    
    showToast('success', t('ow.boe.actionExecuted', { name: selectedAction.displayName }));
    
    // Close modal
    setSelectedAction(null);

    // Refresh selectedInstance in UI
    setTimeout(() => {
      const freshList = updatedDatasets.find(d => d.id === activeObjectType.mapping.datasetId)?.sampleData;
      if (freshList) {
        // Re-read row
        const mappings = activeObjectType.mapping.propertyMappings;
        const freshRow = freshList[selectedInstance._index];
        const updatedInst: Record<string, any> = {
          _index: selectedInstance._index,
          _datasetId: activeObjectType.mapping.datasetId,
          _objectTypeId: activeObjectType.id
        };
        activeObjectType.properties.forEach(prop => {
          const colName = mappings[prop.id];
          updatedInst[prop.id] = freshRow[colName] !== undefined ? freshRow[colName] : null;
        });
        setSelectedInstance(updatedInst);
      }
    }, 100);
  };

  // 8. Small wiring helpers for the extracted sub-components (H6-T4)
  const handleSidebarSelectObjectType = (id: string) => {
    setActiveObjectTypeId(id);
    setActiveTab('table');
  };

  const handleSortColumn = (propId: string) => {
    setSortBy(propId);
    setSortOrder(sortBy === propId && sortOrder === 'asc' ? 'desc' : 'asc');
  };

  const handleSelectInstance = (inst: any) => {
    setSelectedInstance(inst);
    setDetailTab('properties');
  };

  const handleDrillFilter = (propertyId: string, value: string) => {
    setActiveFilters([...activeFilters, { propertyId, operator: 'equals', value }]);
    setActiveTab('table');
    showToast('info', t('ow.boe.drillFilter', { prop: propertyId, val: value }));
  };

  return (
    <div className={`h-full flex overflow-hidden ${styles.appBg} relative select-none`}>

      {/* LEFT PANEL: Object Selector & Saved Searches */}
      <ExplorerSidebar
        objectTypes={objectTypes}
        datasets={datasets}
        activeObjectTypeId={activeObjectTypeId}
        onSelectObjectType={handleSidebarSelectObjectType}
        savedSearches={savedSearches}
        onLoadSavedSearch={handleLoadSavedSearch}
        onDeleteSavedSearch={handleDeleteSavedSearch}
        apiLoading={apiLoading}
        apiError={apiError}
      />

      {/* CENTER & MAIN WORKSPACE */}
      <div className="flex-1 flex flex-col overflow-hidden">

        {/* Active Stage Header */}
        {activeObjectType ? (
          <ExplorerHeader
            activeObjectType={activeObjectType}
            activeTab={activeTab}
            onTabChange={setActiveTab}
            activeFilters={activeFilters}
            onRemoveFilter={handleRemoveFilter}
            showFilterCreator={showFilterCreator}
            onToggleFilterCreator={setShowFilterCreator}
            newFilterProp={newFilterProp}
            onNewFilterPropChange={setNewFilterProp}
            newFilterOp={newFilterOp}
            onNewFilterOpChange={setNewFilterOp}
            newFilterVal={newFilterVal}
            onNewFilterValChange={setNewFilterVal}
            onAddFilter={handleAddFilter}
            onOpenSaveModal={() => setShowSaveModal(true)}
          />
        ) : null}

        {/* Workspace Central Canvas */}
        <div className="flex-1 overflow-hidden relative">

          {/* Welcome view when no activeObjectType selected */}
          {!activeObjectTypeId ? (
            <ExplorerWelcome
              objectTypes={objectTypes}
              datasets={datasets}
              onSelectObjectType={setActiveObjectTypeId}
            />
          ) : (
            <div className="h-full flex overflow-hidden">

              {/* Work Desk Stage */}
              <div className="flex-1 flex flex-col overflow-hidden">

                {activeTab === 'table' ? (
                  <ExplorerTable
                    activeObjectType={activeObjectType}
                    processedInstances={processedInstances}
                    totalInstanceCount={allInstances.length}
                    localSearch={localSearch}
                    onLocalSearchChange={setLocalSearch}
                    sortBy={sortBy}
                    sortOrder={sortOrder}
                    onSort={handleSortColumn}
                    selectedInstance={selectedInstance}
                    onSelectInstance={handleSelectInstance}
                  />
                ) : (
                  // ANALYTICS / CHART TAB
                  <ExplorerAnalytics
                    analyticsData={analyticsData}
                    processedCount={processedInstances.length}
                    onDrillFilter={handleDrillFilter}
                  />
                )}
              </div>

              {/* DETAILED SLIDE-OVER OR SPLIT PANEL (Right hand side) */}
              {selectedInstance ? (
                <ExplorerDetailPanel
                  activeObjectType={activeObjectType}
                  selectedInstance={selectedInstance}
                  onClose={() => setSelectedInstance(null)}
                  availableActions={availableActions}
                  onOpenAction={handleOpenActionModal}
                  detailTab={detailTab}
                  onDetailTabChange={setDetailTab}
                  resolvedRelations={resolvedRelations}
                  onJumpToInstance={handleJumpToInstance}
                />
              ) : null}

            </div>
          )}

        </div>
      </div>

      {/* MODAL 1: Save Exploration Object List */}
      {showSaveModal && (
        <SaveSearchListModal
          newSearchName={newSearchName}
          onNameChange={setNewSearchName}
          onClose={() => setShowSaveModal(false)}
          onSave={handleSaveSearch}
        />
      )}

      {/* MODAL 2: Execute Action Parameters Form */}
      {selectedAction && (
        <ExecuteActionModal
          action={selectedAction}
          activeObjectType={activeObjectType}
          actionParams={actionParams}
          onActionParamsChange={setActionParams}
          actionError={actionError}
          onClose={() => setSelectedAction(null)}
          onExecute={handleExecuteAction}
        />
      )}

    </div>
  );
}
