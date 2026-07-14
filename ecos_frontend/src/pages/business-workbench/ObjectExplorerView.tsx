/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useMemo, useEffect } from 'react';
import { ObjectType, LinkType, ActionType, Dataset } from '../../types/ontology';
import LucideIcon from './LucideIcon';

// ── Backend API helpers ────────────────────────────────────────
// ObjectExplorerView augments its prop/seed data with real ontology instance
// data fetched from the ECOS backend. On any failure it gracefully degrades
// to the seed data supplied via props (mockObjectTypes from seedData.ts).

/** Build standard request headers including the Bearer auth token. */
function authHeaders(): HeadersInit {
  const token = localStorage.getItem('token') || '';
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

/**
 * Normalise the various possible shapes of the `/api/v1/ontology/data`
 * response payload into a flat ObjectType[] array.
 *
 * Handles: bare arrays, `{ data: [...] }`, and objects that nest the list
 * under a key such as `objectTypes` / `objects` / `list` / `records`.
 * Items missing a stable `id` are dropped so they cannot corrupt the
 * dedup-by-id merge performed later.
 */
function normalizeObjectTypes(payload: any): ObjectType[] {
  let raw: any[] | null = null;
  if (Array.isArray(payload)) {
    raw = payload;
  } else if (payload && typeof payload === 'object') {
    if (Array.isArray(payload.data)) {
      raw = payload.data;
    } else {
      for (const key of ['objectTypes', 'objects', 'objectList', 'list', 'records', 'items']) {
        if (Array.isArray(payload[key])) { raw = payload[key]; break; }
      }
    }
  }
  if (!raw) return [];
  return raw.filter((it: any) => it && typeof it === 'object' && typeof it.id === 'string');
}

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

interface SavedSearch {
  id: string;
  name: string;
  objectTypeId: string;
  filters: FilterQuery[];
  sortBy: string;
  sortOrder: 'asc' | 'desc';
}

interface FilterQuery {
  propertyId: string;
  operator: 'equals' | 'contains' | 'gt' | 'lt' | 'is_empty' | 'is_not_empty';
  value: string;
}

export default function ObjectExplorerView({
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
    fetch('/api/v1/ontology/data', { headers: authHeaders() })
      .then(r => r.json())
      .then((resp: any) => {
        if (cancelled) return;
        // Backend signals success with code === 0 (and sometimes code === 200).
        if (resp && (resp.code === 0 || resp.code === 200)) {
          const items = normalizeObjectTypes(resp.data);
          if (items.length > 0) {
            setApiObjectTypes(items);
            showToast('success', `已从后端加载 ${items.length} 个对象类型`);
          }
        } else if (resp && resp.code && resp.code !== 0 && resp.code !== 200) {
          // Explicit backend error code — surface it but keep seed data.
          setApiError(resp.message || `后端返回错误码 ${resp.code}`);
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
    if (initialActiveObjectTypeId !== undefined) {
      setActiveObjectTypeId(initialActiveObjectTypeId);
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
      const key = String(inst[groupProp.id] || '未指定/Null');
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
    showToast('info', '已成功添加筛选过滤器');
  };

  const handleRemoveFilter = (index: number) => {
    const updated = activeFilters.filter((_, idx) => idx !== index);
    setActiveFilters(updated);
    showToast('info', '筛选过滤器已移除');
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
    showToast('success', `成功保存对象列表:「${newSearch.name}」`);
  };

  const handleLoadSavedSearch = (search: SavedSearch) => {
    setActiveObjectTypeId(search.objectTypeId);
    setActiveFilters(search.filters);
    setSortBy(search.sortBy);
    setSortOrder(search.sortOrder);
    showToast('success', `已载入保存的筛选:「${search.name}」`);
  };

  const handleDeleteSavedSearch = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    const updated = savedSearches.filter(s => s.id !== id);
    saveSearches(updated);
    showToast('info', '已移除保存的对象列表');
  };

  // 6. Relational Connection Traversal Parser
  const resolvedRelations = useMemo(() => {
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
      showToast('info', `已穿透跳转至 ${ot.displayName}: ${instId}`);
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
      showToast('error', '校验未通过：' + errMessage);
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
    
    showToast('success', `🚀 操作「${selectedAction.displayName}」执行成功并提交写回底层数据集！`);
    
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

  return (
    <div className="h-full flex overflow-hidden bg-slate-50 relative select-none">
      
      {/* LEFT PANEL: Object Selector & Saved Searches */}
      <div className="w-64 border-r border-slate-200 bg-white flex flex-col shrink-0 text-xs">
        {/* Section title */}
        <div className="p-4 border-b border-slate-100 flex items-center justify-between">
          <div className="font-semibold text-slate-800 flex items-center gap-1.5">
            <LucideIcon name="Compass" size={14} className="text-blue-600" />
            <span>对象浏览器目录</span>
            {apiLoading && (
              <span className="ml-1 inline-flex items-center gap-1 text-[9px] font-normal text-blue-500">
                <span className="w-1.5 h-1.5 rounded-full bg-blue-500 animate-pulse" />
                同步后端…
              </span>
            )}
          </div>
          {apiError && (
            <span
              title={`后端同步失败：${apiError}（已降级为本地种子数据）`}
              className="text-[9px] text-amber-500 cursor-help"
            >
              离线
            </span>
          )}
        </div>

        {/* Object Types list */}
        <div className="p-3 space-y-1">
          <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider block px-2 mb-2">对象实体 (Objects)</span>
          {objectTypes.map(ot => {
            const isActive = ot.id === activeObjectTypeId;
            // Get mock instances count
            const ds = datasets.find(d => d.id === ot.mapping?.datasetId);
            const count = ds ? ds.sampleData.length : 0;

            return (
              <button
                key={ot.id}
                onClick={() => {
                  setActiveObjectTypeId(ot.id);
                  setActiveTab('table');
                }}
                className={`w-full text-left py-2 px-2.5 rounded-lg flex items-center justify-between transition-all group ${
                  isActive
                    ? 'bg-blue-600 text-white font-semibold shadow-xs'
                    : 'text-slate-600 hover:bg-slate-100'
                }`}
              >
                <div className="flex items-center gap-2 truncate">
                  <span className={`p-1 rounded border ${isActive ? 'bg-blue-500 border-blue-400 text-white' : ot.color}`}>
                    <LucideIcon name={ot.icon} size={12} />
                  </span>
                  <span className="truncate">{ot.displayName}</span>
                </div>
                <span className={`font-mono text-[10px] px-1.5 py-0.5 rounded-full ${isActive ? 'bg-blue-500 text-white' : 'bg-slate-100 text-slate-500'}`}>
                  {count}
                </span>
              </button>
            );
          })}
        </div>

        {/* Saved Search Lists */}
        <div className="flex-1 border-t border-slate-100 p-3 space-y-1.5 overflow-y-auto">
          <div className="flex justify-between items-center px-2 mb-1">
            <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider">我的保存列表 (Object Lists)</span>
            <span className="text-[10px] bg-slate-100 text-slate-500 px-1 py-0.2 rounded-sm font-mono">{savedSearches.length}</span>
          </div>

          {savedSearches.length === 0 ? (
            <div className="p-4 text-center text-slate-400 border border-dashed border-slate-200 rounded-lg text-[10px]">
              暂无保存的对象列表。
              可以在筛选过滤后，将其保存。
            </div>
          ) : (
            <div className="space-y-1">
              {savedSearches.map(search => (
                <div
                  key={search.id}
                  onClick={() => handleLoadSavedSearch(search)}
                  className="group flex items-center justify-between p-2 rounded-lg border border-slate-100 hover:border-blue-400 bg-slate-50/50 hover:bg-blue-50/20 cursor-pointer transition-all"
                >
                  <div className="flex items-center gap-1.5 truncate">
                    <LucideIcon name="Bookmark" size={11} className="text-blue-500 shrink-0" />
                    <span className="font-medium text-slate-700 truncate">{search.name}</span>
                  </div>
                  <button
                    onClick={(e) => handleDeleteSavedSearch(search.id, e)}
                    className="opacity-0 group-hover:opacity-100 hover:text-red-500 text-slate-400 transition-opacity p-0.5"
                  >
                    <LucideIcon name="Trash2" size={11} />
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* CENTER & MAIN WORKSPACE */}
      <div className="flex-1 flex flex-col overflow-hidden">
        
        {/* Active Stage Header */}
        {activeObjectType ? (
          <div className="bg-white border-b border-slate-200 px-6 py-4 flex flex-col gap-3">
            {/* Breadcrumb & Title */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <span className={`p-1.5 rounded-lg border ${activeObjectType.color}`}>
                  <LucideIcon name={activeObjectType.icon} size={15} />
                </span>
                <div>
                  <h2 className="text-sm font-bold text-slate-900 flex items-center gap-1.5">
                    {activeObjectType.displayName}
                    <span className="text-[10px] bg-slate-100 text-slate-500 px-1.5 py-0.5 rounded font-mono uppercase">{activeObjectType.id}</span>
                  </h2>
                  <p className="text-[10px] text-slate-400 mt-0.5">{activeObjectType.description}</p>
                </div>
              </div>

              {/* View Selector Tabs */}
              <div className="flex bg-slate-100 p-1 rounded-lg">
                <button
                  onClick={() => setActiveTab('table')}
                  className={`px-3 py-1.5 rounded-md text-[11px] font-semibold flex items-center gap-1.5 transition-all ${
                    activeTab === 'table' ? 'bg-white text-slate-900 shadow-3xs' : 'text-slate-500 hover:text-slate-800'
                  }`}
                >
                  <LucideIcon name="Table2" size={13} />
                  数据实例列表
                </button>
                <button
                  onClick={() => setActiveTab('analytics')}
                  className={`px-3 py-1.5 rounded-md text-[11px] font-semibold flex items-center gap-1.5 transition-all ${
                    activeTab === 'analytics' ? 'bg-white text-slate-900 shadow-3xs' : 'text-slate-500 hover:text-slate-800'
                  }`}
                >
                  <LucideIcon name="BarChart3" size={13} />
                  运行统计与聚合
                </button>
              </div>
            </div>

            {/* Quick Filter & Save Search Toolbar */}
            <div className="flex flex-wrap items-center gap-3 bg-slate-50 p-2.5 rounded-lg border border-slate-150">
              <div className="flex items-center gap-1.5 text-[11px] font-semibold text-slate-500 shrink-0">
                <LucideIcon name="Filter" size={13} />
                筛选器:
              </div>

              {/* Existing active filters badges */}
              {activeFilters.length === 0 && (
                <span className="text-[10px] text-slate-400 italic">当前没有添加任何筛选过滤器</span>
              )}
              {activeFilters.map((f, idx) => {
                const prop = activeObjectType.properties.find(p => p.id === f.propertyId);
                const propName = prop ? prop.displayName : f.propertyId;
                
                const opName = f.operator === 'equals' ? '=' 
                  : f.operator === 'contains' ? '包含'
                  : f.operator === 'gt' ? '>'
                  : f.operator === 'lt' ? '<'
                  : f.operator === 'is_empty' ? '为空' : '不为空';

                return (
                  <span key={idx} className="flex items-center gap-1 bg-blue-50 border border-blue-200 text-blue-700 px-2 py-1 rounded font-medium text-[10px]">
                    <span className="text-blue-500">{propName}</span>
                    <span className="text-blue-400 italic font-mono">{opName}</span>
                    {f.operator !== 'is_empty' && f.operator !== 'is_not_empty' && (
                      <strong className="text-blue-900 font-semibold">{f.value}</strong>
                    )}
                    <button
                      onClick={() => handleRemoveFilter(idx)}
                      className="text-blue-400 hover:text-blue-600 ml-1 font-bold"
                    >
                      ×
                    </button>
                  </span>
                );
              })}

              {/* Add filter creator dropdown trigger */}
              <div className="relative ml-auto flex items-center gap-2">
                <button
                  onClick={() => setShowFilterCreator(!showFilterCreator)}
                  className="bg-white border border-slate-200 text-slate-700 hover:bg-slate-50 text-[10px] font-semibold py-1 px-2 rounded-md flex items-center gap-1 transition-colors"
                >
                  <LucideIcon name="Plus" size={11} />
                  添加筛选过滤器
                </button>

                {/* Filter Creator Popover */}
                {showFilterCreator && (
                  <div className="absolute right-0 top-7 bg-white border border-slate-200 rounded-lg shadow-lg p-3 z-30 w-72 space-y-3">
                    <h4 className="font-semibold text-slate-800 text-[11px]">新建筛选规则</h4>
                    <div className="space-y-2">
                      <div>
                        <label className="text-[10px] text-slate-400 block mb-0.5">选择属性</label>
                        <select
                          value={newFilterProp}
                          onChange={e => setNewFilterProp(e.target.value)}
                          className="w-full h-8 text-[11px] bg-slate-50 border border-slate-200 rounded px-2"
                        >
                          <option value="">-- 请选择 --</option>
                          {activeObjectType.properties.map(p => (
                            <option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>
                          ))}
                        </select>
                      </div>

                      <div className="grid grid-cols-2 gap-2">
                        <div>
                          <label className="text-[10px] text-slate-400 block mb-0.5">比较算子</label>
                          <select
                            value={newFilterOp}
                            onChange={e => setNewFilterOp(e.target.value as any)}
                            className="w-full h-8 text-[11px] bg-slate-50 border border-slate-200 rounded px-2"
                          >
                            <option value="equals">等于 (Equals)</option>
                            <option value="contains">包含 (Contains)</option>
                            <option value="gt">大于 (&gt;)</option>
                            <option value="lt">小于 (&lt;)</option>
                            <option value="is_empty">为空 (Is Empty)</option>
                            <option value="is_not_empty">不为空 (Is Not Empty)</option>
                          </select>
                        </div>
                        <div>
                          <label className="text-[10px] text-slate-400 block mb-0.5">设定值</label>
                          <input
                            type="text"
                            disabled={newFilterOp === 'is_empty' || newFilterOp === 'is_not_empty'}
                            placeholder="搜索值"
                            value={newFilterVal}
                            onChange={e => setNewFilterVal(e.target.value)}
                            className="w-full h-8 text-[11px] bg-slate-50 border border-slate-200 rounded px-2"
                          />
                        </div>
                      </div>
                    </div>

                    <div className="flex justify-end gap-1.5 pt-1">
                      <button
                        onClick={() => setShowFilterCreator(false)}
                        className="h-7 px-2.5 rounded text-[10px] bg-slate-100 hover:bg-slate-200 text-slate-600"
                      >
                        取消
                      </button>
                      <button
                        onClick={handleAddFilter}
                        disabled={!newFilterProp}
                        className="h-7 px-3 rounded text-[10px] bg-blue-600 hover:bg-blue-500 text-white disabled:bg-slate-200 disabled:cursor-not-allowed"
                      >
                        应用规则
                      </button>
                    </div>
                  </div>
                )}

                {/* Save exploration list */}
                {activeFilters.length > 0 && (
                  <button
                    onClick={() => setShowSaveModal(true)}
                    className="bg-blue-50 border border-blue-200 text-blue-700 hover:bg-blue-100 text-[10px] font-semibold py-1 px-2.5 rounded-md flex items-center gap-1 transition-colors"
                  >
                    <LucideIcon name="Bookmark" size={11} />
                    保存为对象列表
                  </button>
                )}
              </div>
            </div>
          </div>
        ) : null}

        {/* Workspace Central Canvas */}
        <div className="flex-1 overflow-hidden relative">
          
          {/* Welcome view when no activeObjectType selected */}
          {!activeObjectTypeId ? (
            <div className="flex flex-col items-center justify-center h-full p-8 text-center bg-slate-50">
              <div className="w-16 h-16 rounded-2xl bg-blue-50 border border-blue-200 flex items-center justify-center text-blue-600 mb-4 animate-pulse">
                <LucideIcon name="Compass" size={32} />
              </div>
              <h2 className="text-sm font-semibold text-slate-800">欢迎使用 Palantir Foundry 对象浏览器 (Object Explorer)</h2>
              <p className="text-xs text-slate-500 max-w-lg leading-relaxed mt-2">
                对象浏览器是围绕底层异构数据源构建的数据模型透视工作台。
                在此您可以全局探索所有数字孪生实例、设定复杂的交叉筛选条件、跨对象级联穿透挖掘、以及触发运行微事务 Action。
              </p>
              
              {/* Grid of quick choices */}
              <div className="grid grid-cols-2 gap-4 w-full max-w-xl mt-8">
                {objectTypes.map(ot => {
                  const ds = datasets.find(d => d.id === ot.mapping?.datasetId);
                  const count = ds ? ds.sampleData.length : 0;
                  return (
                    <div
                      key={ot.id}
                      onClick={() => setActiveObjectTypeId(ot.id)}
                      className="bg-white border border-slate-200 hover:border-blue-500 p-4 rounded-xl shadow-3xs hover:shadow-xs transition-all cursor-pointer flex items-start gap-3 group text-left"
                    >
                      <span className={`p-2.5 rounded-lg border ${ot.color} shrink-0`}>
                        <LucideIcon name={ot.icon} size={16} />
                      </span>
                      <div className="space-y-0.5">
                        <div className="text-xs font-semibold text-slate-800 group-hover:text-blue-600">{ot.displayName}</div>
                        <p className="text-[10px] text-slate-400 line-clamp-1">{ot.description}</p>
                        <div className="text-[10px] font-mono text-slate-500 mt-1">
                          <strong>{count}</strong> 个当前运行实体
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          ) : (
            <div className="h-full flex overflow-hidden">
              
              {/* Work Desk Stage */}
              <div className="flex-1 flex flex-col overflow-hidden">
                
                {activeTab === 'table' ? (
                  <div className="flex-1 flex flex-col overflow-hidden bg-white">
                    {/* Search & Statistics bar */}
                    <div className="px-6 py-2 bg-slate-50 border-b border-slate-200 flex items-center justify-between">
                      <div className="relative w-80">
                        <span className="absolute left-2.5 top-2.5 text-slate-400">
                          <LucideIcon name="Search" size={12} />
                        </span>
                        <input
                          type="text"
                          placeholder="局部搜索当前展示的数据实例..."
                          value={localSearch}
                          onChange={e => setLocalSearch(e.target.value)}
                          className="w-full h-7 pl-7 pr-3 text-[10px] bg-white border border-slate-200 rounded focus:border-blue-500 focus:outline-hidden text-slate-700"
                        />
                      </div>
                      <div className="text-[10px] text-slate-500 font-mono">
                        正在展示 <strong>{processedInstances.length}</strong> / {allInstances.length} 个实例化对象
                      </div>
                    </div>

                    {/* Table stage */}
                    <div className="flex-1 overflow-auto">
                      <table className="w-full text-left border-collapse text-xs select-none">
                        <thead>
                          <tr className="bg-slate-50/50 border-b border-slate-200 text-slate-500 font-semibold sticky top-0 bg-white z-10 shadow-3xs">
                            <th className="py-2.5 px-4 w-10">#</th>
                            {activeObjectType?.properties.map(prop => {
                              const isSorting = sortBy === prop.id;
                              return (
                                <th
                                  key={prop.id}
                                  onClick={() => {
                                    setSortBy(prop.id);
                                    setSortOrder(isSorting && sortOrder === 'asc' ? 'desc' : 'asc');
                                  }}
                                  className="py-2.5 px-4 cursor-pointer hover:bg-slate-100 transition-colors"
                                >
                                  <div className="flex items-center gap-1">
                                    <span>{prop.displayName}</span>
                                    {isSorting ? (
                                      <LucideIcon name={sortOrder === 'asc' ? 'ChevronUp' : 'ChevronDown'} size={11} className="text-blue-600" />
                                    ) : (
                                      <LucideIcon name="ChevronsUpDown" size={10} className="text-slate-300 group-hover:text-slate-500" />
                                    )}
                                  </div>
                                </th>
                              );
                            })}
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100 text-slate-600">
                          {processedInstances.length === 0 ? (
                            <tr>
                              <td colSpan={(activeObjectType?.properties.length || 0) + 1} className="text-center py-24 text-slate-400 font-medium italic">
                                未能查询到符合任何当前筛选条件的实例化对象 (No results)
                              </td>
                            </tr>
                          ) : (
                            processedInstances.map((inst, idx) => {
                              const isSelected = selectedInstance && selectedInstance[activeObjectType!.primaryKey] === inst[activeObjectType!.primaryKey];
                              return (
                                <tr
                                  key={idx}
                                  onClick={() => {
                                    setSelectedInstance(inst);
                                    setDetailTab('properties');
                                  }}
                                  className={`hover:bg-slate-50/70 cursor-pointer transition-colors ${
                                    isSelected ? 'bg-blue-50/40 text-blue-950 font-medium border-l-2 border-blue-600' : ''
                                  }`}
                                >
                                  <td className="py-2.5 px-4 font-mono text-slate-400">{idx + 1}</td>
                                  {activeObjectType?.properties.map(prop => {
                                    const val = inst[prop.id];
                                    const isPk = prop.isPrimaryKey;
                                    return (
                                      <td key={prop.id} className="py-2.5 px-4">
                                        {isPk ? (
                                          <span className="font-mono text-slate-900 bg-slate-100 border border-slate-200/80 rounded-md px-1.5 py-0.5 text-[10px] font-semibold">
                                            {String(val ?? '')}
                                          </span>
                                        ) : prop.id === 'status' ? (
                                          <span className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                                            val === 'ACTIVE' || val === 'ON_TIME' ? 'bg-emerald-100 text-emerald-800' :
                                            val === 'MAINTENANCE' || val === 'DELAYED' ? 'bg-amber-100 text-amber-800 font-semibold' :
                                            'bg-slate-100 text-slate-600'
                                          }`}>
                                            {String(val ?? '')}
                                          </span>
                                        ) : (
                                          <span className="truncate max-w-[160px] inline-block">{String(val ?? '')}</span>
                                        )}
                                      </td>
                                    );
                                  })}
                                </tr>
                              );
                            })
                          )}
                        </tbody>
                      </table>
                    </div>
                  </div>
                ) : (
                  // ANALYTICS / CHART TAB
                  <div className="flex-1 bg-white p-6 space-y-6 overflow-y-auto">
                    <div className="space-y-1">
                      <h3 className="text-xs font-semibold text-slate-800 flex items-center gap-1.5">
                        <LucideIcon name="AreaChart" size={14} className="text-blue-600" />
                        分布聚合分析 (Categorical Distribution)
                      </h3>
                      <p className="text-[10px] text-slate-500">
                        智能分析当前筛选规则下的数据集。基于标准关键枚举属性<strong>「{analyticsData ? (analyticsData as any).property?.displayName : ''}」</strong>进行快速分组及分布统计。
                      </p>
                    </div>

                    {processedInstances.length === 0 ? (
                      <div className="text-center py-20 text-slate-400">暂无数据用以绘图统计。</div>
                    ) : (
                      <div className="grid grid-cols-2 gap-8 items-start">
                        {/* Custom visual distribution bars */}
                        <div className="border border-slate-200 rounded-xl p-5 space-y-3 shadow-3xs bg-slate-50/20">
                          <h4 className="text-[11px] font-semibold text-slate-700">条形占比统计图 (点击柱体可直接追加筛选)</h4>
                          <div className="space-y-3 pt-2">
                            {(analyticsData as any).data?.map((item: any) => (
                              <div
                                key={item.name}
                                onClick={() => {
                                  // Add filter on click
                                  const propId = (analyticsData as any).property.id;
                                  setActiveFilters([...activeFilters, {
                                    propertyId: propId,
                                    operator: 'equals',
                                    value: item.name
                                  }]);
                                  setActiveTab('table');
                                  showToast('info', `已通过图表下钻筛选 ${propId} = "${item.name}"`);
                                }}
                                className="group cursor-pointer space-y-1"
                              >
                                <div className="flex justify-between text-[11px]">
                                  <span className="font-medium text-slate-700 group-hover:text-blue-600 font-mono transition-colors">{item.name}</span>
                                  <span className="text-slate-500 font-mono"><strong>{item.count}</strong> 个 ({item.percentage}%)</span>
                                </div>
                                <div className="h-4 w-full bg-slate-100 rounded overflow-hidden flex">
                                  <div
                                    style={{ width: `${item.percentage}%` }}
                                    className="bg-blue-600 group-hover:bg-blue-500 transition-all rounded-r duration-500"
                                  />
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>

                        {/* Summary table list */}
                        <div className="border border-slate-200 rounded-xl p-5 space-y-3 bg-white">
                          <h4 className="text-[11px] font-semibold text-slate-700">分组计数表</h4>
                          <table className="w-full text-left border-collapse text-[11px]">
                            <thead>
                              <tr className="border-b border-slate-100 text-slate-400">
                                <th className="pb-2">分组类别</th>
                                <th className="pb-2 text-right">实例数</th>
                                <th className="pb-2 text-right">所占比例</th>
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-50 text-slate-600">
                              {(analyticsData as any).data?.map((item: any) => (
                                <tr key={item.name} className="hover:bg-slate-50">
                                  <td className="py-2 font-mono text-slate-700 font-medium">{item.name}</td>
                                  <td className="py-2 text-right font-mono font-semibold text-slate-900">{item.count}</td>
                                  <td className="py-2 text-right font-mono text-slate-500">{item.percentage}%</td>
                                </tr>
                              ))}
                              <tr className="border-t border-slate-200 text-slate-900 font-bold">
                                <td className="py-2">总计 (Total)</td>
                                <td className="py-2 text-right font-mono">{processedInstances.length}</td>
                                <td className="py-2 text-right font-mono">100.0%</td>
                              </tr>
                            </tbody>
                          </table>
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </div>

              {/* DETAILED SLIDE-OVER OR SPLIT PANEL (Right hand side) */}
              {selectedInstance ? (
                <div className="w-96 border-l border-slate-200 bg-white flex flex-col shrink-0 overflow-hidden relative">
                  
                  {/* Detailed Panel Header */}
                  <div className="p-4 border-b border-slate-200 bg-slate-50/50 flex flex-col gap-3">
                    <div className="flex justify-between items-start">
                      <div className="flex items-center gap-2">
                        <span className={`p-1.5 rounded-lg border ${activeObjectType.color}`}>
                          <LucideIcon name={activeObjectType.icon} size={14} />
                        </span>
                        <div>
                          <div className="text-[10px] text-slate-400 font-bold uppercase tracking-wider">{activeObjectType.displayName} 详情</div>
                          <h3 className="text-xs font-bold font-mono text-slate-950 mt-0.5">
                            {selectedInstance[activeObjectType.titleProperty]}
                          </h3>
                        </div>
                      </div>
                      <button
                        onClick={() => setSelectedInstance(null)}
                        className="p-1 rounded hover:bg-slate-200 text-slate-400 hover:text-slate-700"
                      >
                        <LucideIcon name="X" size={14} />
                      </button>
                    </div>

                    {/* Action Execution Button Dropdown */}
                    {availableActions.length > 0 && (
                      <div className="pt-1.5">
                        <div className="text-[10px] text-slate-400 uppercase tracking-wider font-semibold mb-1 flex items-center gap-1">
                          <LucideIcon name="Terminal" size={10} />
                          <span>绑定的可用操作 (Actions)</span>
                        </div>
                        <div className="flex flex-col gap-1">
                          {availableActions.map(act => (
                            <button
                              key={act.id}
                              onClick={() => handleOpenActionModal(act)}
                              className="w-full h-8 px-2.5 rounded border border-amber-200 bg-amber-50/40 hover:bg-amber-50 text-amber-800 text-[10px] font-semibold flex items-center justify-between transition-all"
                            >
                              <div className="flex items-center gap-1.5">
                                <LucideIcon name="Zap" size={12} className="fill-amber-400/20 text-amber-600" />
                                <span>触发：{act.displayName}</span>
                              </div>
                              <LucideIcon name="ChevronRight" size={10} className="text-amber-500" />
                            </button>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>

                  {/* Panel Tab switch */}
                  <div className="flex border-b border-slate-100 px-2 text-[11px] font-medium bg-slate-50/20">
                    <button
                      onClick={() => setDetailTab('properties')}
                      className={`flex-1 py-2 text-center border-b-2 font-semibold transition-all ${
                        detailTab === 'properties' ? 'border-blue-600 text-blue-700 font-bold' : 'border-transparent text-slate-500 hover:text-slate-800'
                      }`}
                    >
                      实体属性 (Properties)
                    </button>
                    <button
                      onClick={() => setDetailTab('relations')}
                      className={`flex-1 py-2 text-center border-b-2 font-semibold transition-all flex items-center justify-center gap-1 ${
                        detailTab === 'relations' ? 'border-blue-600 text-blue-700 font-bold' : 'border-transparent text-slate-500 hover:text-slate-800'
                      }`}
                    >
                      关联探索 ({resolvedRelations.reduce((acc, curr) => acc + curr.instances.length, 0)})
                    </button>
                    <button
                      onClick={() => setDetailTab('activity')}
                      className={`flex-1 py-2 text-center border-b-2 font-semibold transition-all ${
                        detailTab === 'activity' ? 'border-blue-600 text-blue-700 font-bold' : 'border-transparent text-slate-500 hover:text-slate-800'
                      }`}
                    >
                      事件记录
                    </button>
                  </div>

                  {/* Panel tab bodies */}
                  <div className="flex-1 overflow-y-auto p-4">
                    
                    {/* tab 1: Properties */}
                    {detailTab === 'properties' && (
                      <div className="space-y-4">
                        {activeObjectType.properties.map(p => {
                          const val = selectedInstance[p.id];
                          const isPk = p.isPrimaryKey;

                          return (
                            <div key={p.id} className="p-2.5 rounded-lg border border-slate-100 hover:border-slate-200 hover:bg-slate-50/30 transition-colors">
                              <div className="flex items-center justify-between text-[10px] text-slate-400 font-mono">
                                <span className="font-semibold text-slate-500">{p.displayName}</span>
                                <span className="uppercase">{p.dataType}</span>
                              </div>
                              <div className="mt-1 font-mono text-xs font-semibold text-slate-900 flex items-center justify-between">
                                {isPk ? (
                                  <span className="bg-slate-150 border border-slate-200 text-slate-800 rounded px-1.5 py-0.5 text-[10px]">
                                    {String(val ?? '未指定')}
                                  </span>
                                ) : p.id === 'status' ? (
                                  <span className={`px-1.5 py-0.5 rounded text-[10px] ${
                                    val === 'ACTIVE' || val === 'ON_TIME' ? 'bg-emerald-100 text-emerald-800' :
                                    val === 'MAINTENANCE' || val === 'DELAYED' ? 'bg-amber-100 text-amber-800' :
                                    'bg-slate-100 text-slate-600'
                                  }`}>
                                    {String(val ?? 'N/A')}
                                  </span>
                                ) : (
                                  <span>{String(val ?? '未赋值 (Null)')}</span>
                                )}
                                
                                {isPk && (
                                  <span className="text-[9px] font-semibold text-red-500 bg-red-50 border border-red-100 px-1 rounded uppercase">Primary Key</span>
                                )}
                              </div>
                              <p className="text-[10px] text-slate-400 mt-1 leading-relaxed">{p.description}</p>
                            </div>
                          );
                        })}
                      </div>
                    )}

                    {/* tab 2: Relations / Connection Traversal */}
                    {detailTab === 'relations' && (
                      <div className="space-y-5">
                        <div className="text-[10px] text-slate-400 font-semibold uppercase leading-relaxed">
                          当前关系网跨对象关联查找
                        </div>

                        {resolvedRelations.length === 0 ? (
                          <div className="text-center py-10 border border-dashed border-slate-200 rounded-lg text-slate-400 text-[10px]">
                            当前对象类型在本体中没有声明关联。
                          </div>
                        ) : (
                          <div className="space-y-4">
                            {resolvedRelations.map(rel => (
                              <div key={rel.linkType.id} className="space-y-2 border border-slate-200/60 rounded-lg p-3 bg-slate-50/10">
                                {/* Header */}
                                <div className="flex items-center justify-between text-[11px] pb-1.5 border-b border-slate-100">
                                  <div className="flex items-center gap-1.5 font-semibold text-slate-800">
                                    <LucideIcon name="GitMerge" size={12} className="text-emerald-600" />
                                    <span>{rel.linkType.displayName}</span>
                                  </div>
                                  <span className="text-[10px] bg-emerald-100 text-emerald-700 px-1 py-0.2 rounded font-mono font-bold uppercase">
                                    {rel.linkType.cardinality}
                                  </span>
                                </div>
                                
                                <p className="text-[10px] text-slate-400">{rel.linkType.description}</p>

                                {/* List matching connected instances */}
                                {rel.instances.length === 0 ? (
                                  <div className="text-[10px] text-slate-400 italic bg-slate-50 p-2 rounded text-center">
                                    没有查找到关联的 {rel.otherObjectType.displayName}
                                  </div>
                                ) : (
                                  <div className="space-y-1 pt-1">
                                    {rel.instances.map(inst => (
                                      <div
                                        key={inst[rel.otherObjectType.primaryKey]}
                                        onClick={() => handleJumpToInstance(rel.otherObjectType.id, inst[rel.otherObjectType.primaryKey])}
                                        className="p-2 border border-slate-150 hover:border-blue-400 bg-white hover:bg-blue-50/10 rounded-md cursor-pointer flex justify-between items-center transition-all group"
                                      >
                                        <div className="flex items-center gap-2 truncate">
                                          <span className={`p-1 rounded ${rel.otherObjectType.color}`}>
                                            <LucideIcon name={rel.otherObjectType.icon} size={11} />
                                          </span>
                                          <span className="font-mono text-xs font-semibold text-slate-900">
                                            {inst[rel.otherObjectType.primaryKey]}
                                          </span>
                                          <span className="text-[10px] text-slate-400 truncate max-w-[100px]">
                                            ({inst[rel.otherObjectType.titleProperty]})
                                          </span>
                                        </div>
                                        <LucideIcon name="Compass" size={11} className="text-slate-400 group-hover:text-blue-600 transition-colors" />
                                      </div>
                                    ))}
                                  </div>
                                )}
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    )}

                    {/* tab 3: Timeline Activity */}
                    {detailTab === 'activity' && (
                      <div className="space-y-4">
                        <div className="relative border-l border-slate-200 pl-4 ml-2 space-y-5 py-2">
                          <div className="relative text-[11px]">
                            <span className="absolute -left-6 top-1 w-3 h-3 rounded-full bg-blue-500 border-2 border-white" />
                            <div className="font-semibold text-slate-800">实体已装载</div>
                            <p className="text-slate-400 text-[10px] mt-0.5">从关联的原始数据集成功实例化并部署在内存沙箱中。</p>
                            <span className="text-[9px] font-mono text-slate-400">2026-07-02 20:34</span>
                          </div>
                          
                          {selectedInstance.status === 'MAINTENANCE' && (
                            <div className="relative text-[11px]">
                              <span className="absolute -left-6 top-1 w-3 h-3 rounded-full bg-amber-500 border-2 border-white" />
                              <div className="font-semibold text-slate-800">触发适航检修维护</div>
                              <p className="text-slate-400 text-[10px] mt-0.5">飞机运营状态转设为 MAINTENANCE 并更新检修戳记。</p>
                              <span className="text-[9px] font-mono text-slate-400">刚刚</span>
                            </div>
                          )}

                          {selectedInstance.status === 'DELAYED' && (
                            <div className="relative text-[11px]">
                              <span className="absolute -left-6 top-1 w-3 h-3 rounded-full bg-red-400 border-2 border-white" />
                              <div className="font-semibold text-slate-800">修改航班运行状态为 DELAYED</div>
                              <p className="text-slate-400 text-[10px] mt-0.5">触发副作用：运行状态修改写回至对应航班物理数据行中。</p>
                              <span className="text-[9px] font-mono text-slate-400">刚刚</span>
                            </div>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              ) : null}

            </div>
          )}

        </div>
      </div>

      {/* MODAL 1: Save Exploration Object List */}
      {showSaveModal && (
        <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-3xs flex items-center justify-center z-50">
          <div className="bg-white border border-slate-200 rounded-xl shadow-2xl p-5 w-96 space-y-4">
            <div className="flex justify-between items-center pb-2 border-b border-slate-100">
              <h3 className="text-xs font-semibold text-slate-900 flex items-center gap-1.5">
                <LucideIcon name="Bookmark" size={13} className="text-blue-600" />
                保存当前过滤器为对象列表 (Object List)
              </h3>
              <button onClick={() => setShowSaveModal(false)} className="text-slate-400 hover:text-slate-600">
                <LucideIcon name="X" size={14} />
              </button>
            </div>
            <div>
              <label className="text-[10px] text-slate-400 block mb-1">输入对象列表名称</label>
              <input
                type="text"
                placeholder="例如：旧金山基地待检修飞机"
                value={newSearchName}
                onChange={e => setNewSearchName(e.target.value)}
                className="w-full h-8 text-[11px] bg-slate-50 border border-slate-200 rounded px-2.5 focus:border-blue-500 focus:outline-hidden"
              />
            </div>
            <div className="flex justify-end gap-2 pt-1 text-[11px]">
              <button
                onClick={() => setShowSaveModal(false)}
                className="h-8 px-3 rounded bg-slate-100 hover:bg-slate-200 text-slate-600 font-semibold"
              >
                取消
              </button>
              <button
                onClick={handleSaveSearch}
                disabled={!newSearchName.trim()}
                className="h-8 px-4 rounded bg-blue-600 hover:bg-blue-500 text-white font-semibold disabled:bg-slate-200 disabled:cursor-not-allowed"
              >
                确定保存
              </button>
            </div>
          </div>
        </div>
      )}

      {/* MODAL 2: Execute Action Parameters Form */}
      {selectedAction && (
        <div className="fixed inset-0 bg-slate-900/50 backdrop-blur-3xs flex items-center justify-center z-50">
          <div className="bg-white border border-slate-200 rounded-xl shadow-2xl p-5 w-[420px] space-y-4">
            <div className="flex justify-between items-center pb-2.5 border-b border-slate-100">
              <div className="flex items-center gap-1.5">
                <LucideIcon name="Zap" size={14} className="text-amber-500 fill-amber-500/10" />
                <h3 className="text-xs font-semibold text-slate-900">
                  执行操作：{selectedAction.displayName}
                </h3>
              </div>
              <button onClick={() => setSelectedAction(null)} className="text-slate-400 hover:text-slate-600">
                <LucideIcon name="X" size={14} />
              </button>
            </div>

            <p className="text-[10px] text-slate-400 leading-relaxed">{selectedAction.description}</p>

            <div className="space-y-3 pt-1">
              {selectedAction.parameters.map(param => {
                const isObjectParam = param.dataType === 'object';
                const isLocked = isObjectParam && param.objectTypeId === activeObjectType?.id;
                
                return (
                  <div key={param.id} className="space-y-1 text-xs">
                    <label className="text-[10px] text-slate-500 font-semibold flex items-center justify-between">
                      <span>{param.displayName} ({param.id})</span>
                      {param.isRequired && <span className="text-red-500 font-bold">* 必填</span>}
                    </label>

                    {isLocked ? (
                      <input
                        type="text"
                        disabled
                        value={actionParams[param.id] || ''}
                        className="w-full h-8 text-[11px] bg-slate-100 border border-slate-200 rounded px-2.5 text-slate-500 font-mono"
                      />
                    ) : param.id === 'new_status_param' ? (
                      <select
                        value={actionParams[param.id] || ''}
                        onChange={e => setActionParams({ ...actionParams, [param.id]: e.target.value })}
                        className="w-full h-8 text-[11px] bg-slate-50 border border-slate-200 rounded px-2 focus:border-blue-500"
                      >
                        <option value="">-- 请选择目标状态 --</option>
                        {activeObjectType?.id === 'flight' && (
                          <>
                            <option value="ON_TIME">ON_TIME (准点)</option>
                            <option value="DELAYED">DELAYED (延误)</option>
                            <option value="BOARDING">BOARDING (登机中)</option>
                            <option value="CANCELLED">CANCELLED (取消)</option>
                          </>
                        )}
                        {activeObjectType?.id === 'aircraft' && (
                          <>
                            <option value="ACTIVE">ACTIVE (活跃运行)</option>
                            <option value="MAINTENANCE">MAINTENANCE (适航检修)</option>
                            <option value="INSPECTION">INSPECTION (深度安全安检)</option>
                          </>
                        )}
                      </select>
                    ) : param.dataType === 'date' ? (
                      <input
                        type="date"
                        value={actionParams[param.id] || ''}
                        onChange={e => setActionParams({ ...actionParams, [param.id]: e.target.value })}
                        className="w-full h-8 text-[11px] bg-slate-50 border border-slate-200 rounded px-2.5 focus:border-blue-500"
                      />
                    ) : (
                      <input
                        type="text"
                        placeholder={`请输入 ${param.displayName}`}
                        value={actionParams[param.id] || ''}
                        onChange={e => setActionParams({ ...actionParams, [param.id]: e.target.value })}
                        className="w-full h-8 text-[11px] bg-slate-50 border border-slate-200 rounded px-2.5 focus:border-blue-500"
                      />
                    )}

                    <p className="text-[9px] text-slate-400">{param.description}</p>
                  </div>
                );
              })}
            </div>

            {/* validation error line */}
            {actionError && (
              <div className="p-2 bg-red-50 border border-red-200 rounded text-[10px] text-red-600 font-medium">
                ❌ 事务约束违背：{actionError}
              </div>
            )}

            <div className="flex justify-end gap-2 pt-2 border-t border-slate-100 text-[11px]">
              <button
                onClick={() => setSelectedAction(null)}
                className="h-8 px-3 rounded bg-slate-100 hover:bg-slate-200 text-slate-600 font-semibold"
              >
                取消
              </button>
              <button
                onClick={handleExecuteAction}
                className="h-8 px-4 rounded bg-amber-500 hover:bg-amber-400 text-slate-900 font-semibold flex items-center gap-1"
              >
                <LucideIcon name="CheckCircle" size={12} />
                <span>执行写回 (Execute)</span>
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}
