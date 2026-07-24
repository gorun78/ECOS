/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useMemo, useEffect } from 'react';
import { AreaChart, BarChart3, Bookmark, CheckCircle, ChevronRight, ChevronsUpDown, Compass, Filter, GitMerge, Plus, Search, Table2, Terminal, Trash2, X, Zap } from 'lucide-react';
import DynamicIcon from '../components/ontology/DynamicIcon';

import SaveSearchModal from './object-explorer/SaveSearchModal';
import ActionExecutorModal from './object-explorer/ActionExecutorModal';
import { ObjectType, LinkType, ActionType, Dataset, DataRecord } from '../types/ontology';
import { useLanguage } from '../components/LanguageContext';
import { useTheme } from '../components/ThemeContext';
import { fetchOntologyData } from '../services/ontologyApi';

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
  objectTypes,
  linkTypes,
  actionTypes,
  datasets,
  onUpdateDatasets,
  showToast,
  initialActiveObjectTypeId = null,
  onActiveObjectTypeIdChange
}: ObjectExplorerViewProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

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

  const [instanceData, setInstanceData] = useState<DataRecord[]>([]);
  const [dataLoading, setDataLoading] = useState(false);
  const [dataPage, setDataPage] = useState(1);
  const [dataTotalPages, setDataTotalPages] = useState(1);
  const [dataTotal, setDataTotal] = useState(0);

  const [relatedInstanceCache, setRelatedInstanceCache] = useState<Record<string, DataRecord[]>>({});

  // Active object type metadata
  const activeObjectType = useMemo(() => {
    return objectTypes.find(ot => ot.id === activeObjectTypeId) || null;
  }, [objectTypes, activeObjectTypeId]);

  // Load saved searches from local storage
  useEffect(() => {
    const cached = localStorage.getItem('ecos_saved_searches');
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
    localStorage.setItem('ecos_saved_searches', JSON.stringify(updated));
  };

  // 1. Dynamic Object Instantiation from Mapping
  const allInstances = useMemo(() => {
    if (!activeObjectType || instanceData.length === 0) return [];
    return instanceData.map((record, index) => {
      const instance: Record<string, any> = {
        _index: index,
        _objectTypeId: activeObjectType.id
      };
      activeObjectType.properties.forEach(prop => {
        instance[prop.id] = record.properties[prop.id] ?? null;
      });
      return instance;
    });
  }, [activeObjectType, instanceData]);

  useEffect(() => {
    if (!activeObjectType) {
      setInstanceData([]);
      return;
    }
    let cancelled = false;
    setDataLoading(true);
    fetchOntologyData({ objectTypeId: activeObjectType.id, page: dataPage, size: 20 })
      .then(res => {
        if (cancelled) return;
        setInstanceData(res.data ?? []);
        setDataTotalPages(res.totalPages ?? 1);
        setDataTotal(res.total ?? 0);
      })
      .catch(() => {
        if (cancelled) return;
        setInstanceData([]);
        setDataTotalPages(1);
      })
      .finally(() => {
        if (!cancelled) setDataLoading(false);
      });
    return () => { cancelled = true; };
  }, [activeObjectType, dataPage]);

  // Instantiation helper for arbitrary object type (for relationships)
  const getInstancesOfObjectType = (otId: string): any[] => {
    const ot = objectTypes.find(o => o.id === otId);
    if (!ot) return [];
    const cached = relatedInstanceCache[otId];
    if (!cached) return [];
    return cached.map((record, idx) => {
      const inst: Record<string, any> = {
        _index: idx,
        _objectTypeId: ot.id,
      };
      ot.properties.forEach(prop => {
        inst[prop.id] = record.properties[prop.id] ?? null;
      });
      return inst;
    });
  };

  useEffect(() => {
    const otIds = new Set<string>();
    linkTypes.forEach(lt => {
      if (lt.sourceObjectType === activeObjectTypeId && lt.targetObjectType) otIds.add(lt.targetObjectType);
      if (lt.targetObjectType === activeObjectTypeId && lt.sourceObjectType) otIds.add(lt.sourceObjectType);
    });
    if (otIds.size === 0) return;
    let cancelled = false;
    const fetchRelated = async () => {
      const newCache: Record<string, DataRecord[]> = {};
      for (const otId of otIds) {
        try {
          const res = await fetchOntologyData({ objectTypeId: otId, page: 1, size: 50 });
          newCache[otId] = res.data ?? [];
        } catch { newCache[otId] = []; }
      }
      if (!cancelled) setRelatedInstanceCache(prev => ({ ...prev, ...newCache }));
    };
    fetchRelated();
    return () => { cancelled = true; };
  }, [activeObjectTypeId, linkTypes]);

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
      setDataPage(1);
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
  const analyticsData = useMemo((): { property: typeof activeObjectType extends infer T ? T extends { properties: (infer P)[] } ? P : never : never; data: Array<{ name: string; count: number; percentage: string }> } | any[] => {
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
      const key = String(inst[groupProp.id] || t('ow.label.unspecified'));
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
    showToast('info', t('ow.msg.filterAdded'));
  };

  const handleRemoveFilter = (index: number) => {
    const updated = activeFilters.filter((_, idx) => idx !== index);
    setActiveFilters(updated);
    showToast('info', t('ow.msg.filterRemoved'));
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
    showToast('success', t('ow.msg.searchSaved').replace('{name}', newSearch.name));
  };

  const handleLoadSavedSearch = (search: SavedSearch) => {
    setActiveObjectTypeId(search.objectTypeId);
    setActiveFilters(search.filters);
    setSortBy(search.sortBy);
    setSortOrder(search.sortOrder);
    showToast('success', t('ow.msg.searchLoaded').replace('{name}', search.name));
  };

  const handleDeleteSavedSearch = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    const updated = savedSearches.filter(s => s.id !== id);
    saveSearches(updated);
    showToast('info', t('ow.msg.searchRemoved'));
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
        if (lt.mapping?.type === 'foreign_key' && lt.mapping.foreignKeyMapping) {
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
        if (lt.mapping?.type === 'foreign_key' && lt.mapping.foreignKeyMapping) {
          const targetVal = selectedInstance[lt.mapping.foreignKeyMapping.targetKey];
          matches = sourceInstances.filter(s => 
            String(s[lt.mapping.foreignKeyMapping!.sourceKey]) === String(targetVal)
          );
        } else if (lt.mapping?.type === 'join_table' && lt.mapping.joinTableMapping) {
          // Join table resolution requires separate API — skip for now
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
  }, [selectedInstance, activeObjectType, linkTypes, objectTypes, relatedInstanceCache]);

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
      showToast('info', t('ow.msg.jumpToInstance').replace('{name}', ot.displayName).replace('{id}', instId));
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

    let valid = true;
    let errMessage = '';

    selectedAction.validationRules.forEach(rule => {
      if (selectedAction.id === 'update_flight_status') {
        const newVal = actionParams['new_status_param'];
        const allowed = ['ON_TIME', 'DELAYED', 'BOARDING', 'CANCELLED'];
        if (!allowed.includes(newVal)) {
          valid = false;
          errMessage = rule.errorMessage;
        }
      } else if (selectedAction.id === 'schedule_maintenance_check') {
        if (selectedInstance.status === 'MAINTENANCE') {
          valid = false;
          errMessage = rule.errorMessage;
        }
      }
    });

    if (!valid) {
      setActionError(errMessage);
      showToast('error', t('ow.msg.validationFailed') + errMessage);
      return;
    }

    showToast('success', t('ow.msg.actionExecuted').replace('{name}', selectedAction.displayName));
    setSelectedAction(null);

    setDataPage(1);
    fetchOntologyData({ objectTypeId: activeObjectType.id, page: 1, size: 20 })
      .then(res => {
        setInstanceData(res.data ?? []);
        setDataTotalPages(res.totalPages ?? 1);
        setDataTotal(res.total ?? 0);
        setSelectedInstance(null);
      })
      .catch(() => {});
  };

  return (
    <div className={`h-full flex overflow-hidden ${styles.appBg} relative select-none`}>
      
      {/* LEFT PANEL: Object Selector & Saved Searches */}
      <div className={`w-64 border-r ${styles.cardBorder} ${styles.cardBg} flex flex-col shrink-0 text-xs`}>
        {/* Section title */}
        <div className={`p-4 border-b ${styles.cardBorder} flex items-center justify-between`}>
          <div className={`font-semibold ${styles.cardText} flex items-center gap-1.5`}>
            <Compass size={14} className={styles.accentText} />
            <span>{t('ow.explore.directoryTitle')}</span>
          </div>
        </div>

        {/* Object Types list */}
        <div className="p-3 space-y-1">
          <span className={`text-[10px] ${styles.muted} font-bold uppercase tracking-wider block px-2 mb-2`}>{t('ow.explore.objectsSection')}</span>
          {objectTypes.map(ot => {
            const isActive = ot.id === activeObjectTypeId;
            const count = (ot.id === activeObjectTypeId && instanceData.length > 0) ? dataTotal : (relatedInstanceCache[ot.id]?.length ?? 0);

            return (
              <button
                key={ot.id}
                onClick={() => {
                  setActiveObjectTypeId(ot.id);
                  setActiveTab('table');
                }}
                className={`w-full text-left py-2 px-2.5 rounded-lg flex items-center justify-between transition-all group ${
                  isActive
                    ? `${styles.accentBg} text-white font-semibold shadow-xs`
                    : `${styles.cardTextMuted} hover:bg-slate-100`
                }`}
              >
                <div className="flex items-center gap-2 truncate">
                  <span className={`p-1 rounded border ${isActive ? 'bg-blue-500 border-blue-400 text-white' : ot.color}`}>
                    <DynamicIcon name={ot.icon} size={12} />
                  </span>
                  <span className="truncate">{ot.displayName}</span>
                </div>
                <span className={`font-mono text-[10px] px-1.5 py-0.5 rounded-full ${isActive ? 'bg-blue-500 text-white' : `bg-slate-100 ${styles.cardTextMuted}`}`}>
                  {count}
                </span>
              </button>
            );
          })}
        </div>

        {/* Saved Search Lists */}
        <div className={`flex-1 border-t ${styles.cardBorder} p-3 space-y-1.5 overflow-y-auto`}>
          <div className="flex justify-between items-center px-2 mb-1">
            <span className={`text-[10px] ${styles.muted} font-bold uppercase tracking-wider`}>{t('ow.explore.savedLists')}</span>
            <span className={`text-[10px] bg-slate-100 ${styles.cardTextMuted} px-1 py-0.2 rounded-sm font-mono`}>{savedSearches.length}</span>
          </div>

          {savedSearches.length === 0 ? (
            <div className={`p-4 text-center ${styles.muted} border border-dashed ${styles.cardBorder} rounded-lg text-[10px]`}>
              {t('ow.empty.noSavedLists')}
              {t('ow.empty.noSavedListsHint')}
            </div>
          ) : (
            <div className="space-y-1">
              {savedSearches.map(search => (
                <div
                  key={search.id}
                  onClick={() => handleLoadSavedSearch(search)}
                  className={`group flex items-center justify-between p-2 rounded-lg border ${styles.cardBorder} hover:border-blue-400 bg-slate-50/50 hover:bg-blue-50/20 cursor-pointer transition-all`}
                >
                  <div className="flex items-center gap-1.5 truncate">
                    <Bookmark size={11} className="text-blue-500 shrink-0" />
                    <span className={`font-medium ${styles.cardTextMuted} truncate`}>{search.name}</span>
                  </div>
                  <button
                    onClick={(e) => handleDeleteSavedSearch(search.id, e)}
                    className={`opacity-0 group-hover:opacity-100 hover:text-red-500 ${styles.muted} transition-opacity p-0.5`}
                  >
                    <Trash2 size={11} />
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
          <div className={`${styles.cardBg} border-b ${styles.cardBorder} px-6 py-4 flex flex-col gap-3`}>
            {/* Breadcrumb & Title */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <span className={`p-1.5 rounded-lg border ${activeObjectType.color}`}>
                  <DynamicIcon name={activeObjectType.icon} size={15} />
                </span>
                <div>
                  <h2 className={`text-sm font-bold ${styles.cardText} flex items-center gap-1.5`}>
                    {activeObjectType.displayName}
                    <span className={`text-[10px] bg-slate-100 ${styles.cardTextMuted} px-1.5 py-0.5 rounded font-mono uppercase`}>{activeObjectType.id}</span>
                  </h2>
                  <p className={`text-[10px] ${styles.muted} mt-0.5`}>{activeObjectType.description}</p>
                </div>
              </div>

              {/* View Selector Tabs */}
              <div className="flex bg-slate-100 p-1 rounded-lg">
                <button
                  onClick={() => setActiveTab('table')}
                  className={`px-3 py-1.5 rounded-md text-[11px] font-semibold flex items-center gap-1.5 transition-all ${
                    activeTab === 'table' ? `${styles.cardBg} ${styles.cardText} shadow-3xs` : `${styles.cardTextMuted} hover:text-slate-800`
                  }`}
                >
                  <Table2 size={13} />
                  {t('ow.explore.tabTable')}
                </button>
                <button
                  onClick={() => setActiveTab('analytics')}
                  className={`px-3 py-1.5 rounded-md text-[11px] font-semibold flex items-center gap-1.5 transition-all ${
                    activeTab === 'analytics' ? `${styles.cardBg} ${styles.cardText} shadow-3xs` : `${styles.cardTextMuted} hover:text-slate-800`
                  }`}
                >
                  <BarChart3 size={13} />
                  {t('ow.explore.tabAnalytics')}
                </button>
              </div>
            </div>

            {/* Quick Filter & Save Search Toolbar */}
            <div className={`flex flex-wrap items-center gap-3 ${styles.appBg} p-2.5 rounded-lg border border-slate-150`}>
              <div className={`flex items-center gap-1.5 text-[11px] font-semibold ${styles.cardTextMuted} shrink-0`}>
                <Filter size={13} />
                {t('ow.explore.filtersLabel')}
              </div>

              {/* Existing active filters badges */}
              {activeFilters.length === 0 && (
                <span className={`text-[10px] ${styles.muted} italic`}>{t('ow.empty.noFilters')}</span>
              )}
              {activeFilters.map((f, idx) => {
                const prop = activeObjectType.properties.find(p => p.id === f.propertyId);
                const propName = prop ? prop.displayName : f.propertyId;
                
                const opName = f.operator === 'equals' ? '=' 
                  : f.operator === 'contains' ? t('ow.explore.opContains')
                  : f.operator === 'gt' ? '>'
                  : f.operator === 'lt' ? '<'
                  : f.operator === 'is_empty' ? t('ow.explore.opIsEmpty') : t('ow.explore.opIsNotEmpty');

                return (
                  <span key={idx} className={`flex items-center gap-1 ${styles.sidebarActiveBg} border ${styles.accentBorder} text-blue-700 px-2 py-1 rounded font-medium text-[10px]`}>
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
                  className={`${styles.cardBg} border ${styles.cardBorder} ${styles.cardTextMuted} hover:bg-slate-50 text-[10px] font-semibold py-1 px-2 rounded-md flex items-center gap-1 transition-colors`}
                >
                  <Plus size={11} />
                  {t('ow.btn.addFilter')}
                </button>

                {/* Filter Creator Popover */}
                {showFilterCreator && (
                  <div className={`absolute right-0 top-7 ${styles.cardBg} border ${styles.cardBorder} rounded-lg shadow-lg p-3 z-30 w-72 space-y-3`}>
                    <h4 className={`font-semibold ${styles.cardText} text-[11px]`}>{t('ow.explore.newFilterRule')}</h4>
                    <div className="space-y-2">
                      <div>
                        <label className={`text-[10px] ${styles.muted} block mb-0.5`}>{t('ow.label.selectProperty')}</label>
                        <select
                          value={newFilterProp}
                          onChange={e => setNewFilterProp(e.target.value)}
                          className={`w-full h-8 text-[11px] ${styles.appBg} border ${styles.cardBorder} rounded px-2`}
                        >
                          <option value="">{t('ow.placeholder.selectOption')}</option>
                          {activeObjectType.properties.map(p => (
                            <option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>
                          ))}
                        </select>
                      </div>

                      <div className="grid grid-cols-2 gap-2">
                        <div>
                          <label className={`text-[10px] ${styles.muted} block mb-0.5`}>{t('ow.label.comparisonOperator')}</label>
                          <select
                            value={newFilterOp}
                            onChange={e => setNewFilterOp(e.target.value as any)}
                            className={`w-full h-8 text-[11px] ${styles.appBg} border ${styles.cardBorder} rounded px-2`}
                          >
                            <option value="equals">{t('ow.explore.opEquals')}</option>
                            <option value="contains">{t('ow.explore.opContainsFull')}</option>
                            <option value="gt">{t('ow.explore.opGreaterThan')}</option>
                            <option value="lt">{t('ow.explore.opLessThan')}</option>
                            <option value="is_empty">{t('ow.explore.opIsEmptyFull')}</option>
                            <option value="is_not_empty">{t('ow.explore.opIsNotEmptyFull')}</option>
                          </select>
                        </div>
                        <div>
                          <label className={`text-[10px] ${styles.muted} block mb-0.5`}>{t('ow.label.setValue')}</label>
                          <input
                            type="text"
                            disabled={newFilterOp === 'is_empty' || newFilterOp === 'is_not_empty'}
                            placeholder={t('ow.placeholder.searchValue')}
                            value={newFilterVal}
                            onChange={e => setNewFilterVal(e.target.value)}
                            className={`w-full h-8 text-[11px] ${styles.appBg} border ${styles.cardBorder} rounded px-2`}
                          />
                        </div>
                      </div>
                    </div>

                    <div className="flex justify-end gap-1.5 pt-1">
                      <button
                        onClick={() => setShowFilterCreator(false)}
                        className={`h-7 px-2.5 rounded text-[10px] bg-slate-100 hover:bg-slate-200 ${styles.cardTextMuted}`}
                      >
                        {t('ow.btn.cancel')}
                      </button>
                      <button
                        onClick={handleAddFilter}
                        disabled={!newFilterProp}
                        className={`h-7 px-3 rounded text-[10px] ${styles.accentBg} hover:bg-blue-500 text-white disabled:bg-slate-200 disabled:cursor-not-allowed`}
                      >
                        {t('ow.btn.applyRule')}
                      </button>
                    </div>
                  </div>
                )}

                {/* Save exploration list */}
                {activeFilters.length > 0 && (
                  <button
                    onClick={() => setShowSaveModal(true)}
                    className={`${styles.sidebarActiveBg} border ${styles.accentBorder} text-blue-700 hover:bg-blue-100 text-[10px] font-semibold py-1 px-2.5 rounded-md flex items-center gap-1 transition-colors`}
                  >
                    <Bookmark size={11} />
                    {t('ow.btn.saveAsList')}
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
            <div className={`flex flex-col items-center justify-center h-full p-8 text-center ${styles.appBg}`}>
              <div className={`w-16 h-16 rounded-2xl ${styles.sidebarActiveBg} border ${styles.accentBorder} flex items-center justify-center ${styles.accentText} mb-4 animate-pulse`}>
                <Compass size={32} />
              </div>
              <h2 className={`text-sm font-semibold ${styles.cardText}`}>{t('ow.explore.welcomeTitle')}</h2>
              <p className={`text-xs ${styles.cardTextMuted} max-w-lg leading-relaxed mt-2`}>
                {t('ow.explore.welcomeDesc1')}
                {t('ow.explore.welcomeDesc2')}
              </p>
              
              {/* Grid of quick choices */}
              <div className="grid grid-cols-2 gap-4 w-full max-w-xl mt-8">
                {objectTypes.map(ot => {
                  const count = (ot.id === activeObjectTypeId && instanceData.length > 0) ? dataTotal : (relatedInstanceCache[ot.id]?.length ?? 0);
                  return (
                    <div
                      key={ot.id}
                      onClick={() => setActiveObjectTypeId(ot.id)}
                      className={`${styles.cardBg} border ${styles.cardBorder} hover:border-blue-500 p-4 rounded-xl shadow-3xs hover:shadow-xs transition-all cursor-pointer flex items-start gap-3 group text-left`}
                    >
                      <span className={`p-2.5 rounded-lg border ${ot.color} shrink-0`}>
                        <DynamicIcon name={ot.icon} size={16} />
                      </span>
                      <div className="space-y-0.5">
                        <div className={`text-xs font-semibold ${styles.cardText} group-hover:text-blue-600`}>{ot.displayName}</div>
                        <p className={`text-[10px] ${styles.muted} line-clamp-1`}>{ot.description}</p>
                        <div className={`text-[10px] font-mono ${styles.cardTextMuted} mt-1`}>
                          <strong>{count}</strong> {t('ow.explore.runningInstances')}
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
                  <div className={`flex-1 flex flex-col overflow-hidden ${styles.cardBg}`}>
                    {/* Search & Statistics bar */}
                    <div className={`px-6 py-2 ${styles.appBg} border-b ${styles.cardBorder} flex items-center justify-between`}>
                      <div className="relative w-80">
                        <span className={`absolute left-2.5 top-2.5 ${styles.muted}`}>
                          <Search size={12} />
                        </span>
                        <input
                          type="text"
                          placeholder={t('ow.placeholder.localSearch')}
                          value={localSearch}
                          onChange={e => setLocalSearch(e.target.value)}
                          className={`w-full h-7 pl-7 pr-3 text-[10px] ${styles.cardBg} border ${styles.cardBorder} rounded focus:border-blue-500 focus:outline-hidden ${styles.cardTextMuted}`}
                        />
                      </div>
                      <div className={`text-[10px] ${styles.cardTextMuted} font-mono`}>
                        {t('ow.explore.showingInstances')} <strong>{processedInstances.length}</strong> / {allInstances.length}
                      </div>
                    </div>

                    {dataLoading ? (
                      <div className={`flex-1 flex items-center justify-center py-24 ${styles.muted} font-medium italic text-xs`}>
                        {t('ow.label.loadingData')}
                      </div>
                    ) : allInstances.length === 0 ? (
                      <div className={`flex-1 flex items-center justify-center py-24 ${styles.muted} font-medium italic text-xs`}>
                        {t('ow.empty.noInstanceData')}
                      </div>
                    ) : (
                    <div className="flex-1 overflow-auto">
                      <table className="w-full text-left border-collapse text-xs select-none">
                        <thead>
                          <tr className={`bg-slate-50/50 border-b ${styles.cardBorder} ${styles.cardTextMuted} font-semibold sticky top-0 ${styles.cardBg} z-10 shadow-3xs`}>
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
                                      <DynamicIcon name={sortOrder === 'asc' ? 'ChevronUp' : 'ChevronDown'} size={11} className={styles.accentText} />
                                    ) : (
                                      <ChevronsUpDown size={10} className={`text-slate-300 group-hover:${styles.cardTextMuted}`} />
                                    )}
                                  </div>
                                </th>
                              );
                            })}
                          </tr>
                        </thead>
                        <tbody className={`divide-y divide-slate-100 ${styles.cardTextMuted}`}>
                          {processedInstances.length === 0 ? (
                            <tr>
                              <td colSpan={(activeObjectType?.properties.length || 0) + 1} className={`text-center py-24 ${styles.muted} font-medium italic`}>
                                {t('ow.empty.noResults')}
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
                                    isSelected ? `bg-blue-50/40 text-blue-950 font-medium border-l-2 ${styles.accentBorder}` : ''
                                  }`}
                                >
                                  <td className={`py-2.5 px-4 font-mono ${styles.muted}`}>{idx + 1}</td>
                                  {activeObjectType?.properties.map(prop => {
                                    const val = inst[prop.id];
                                    const isPk = prop.isPrimaryKey;
                                    return (
                                      <td key={prop.id} className="py-2.5 px-4">
                                        {isPk ? (
                                          <span className={`font-mono ${styles.cardText} bg-slate-100 border border-slate-200/80 rounded-md px-1.5 py-0.5 text-[10px] font-semibold`}>
                                            {String(val ?? '')}
                                          </span>
                                        ) : prop.id === 'status' ? (
                                          <span className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                                            val === 'ACTIVE' || val === 'ON_TIME' ? 'bg-emerald-100 text-emerald-800' :
                                            val === 'MAINTENANCE' || val === 'DELAYED' ? 'bg-amber-100 text-amber-800 font-semibold' :
                                            `bg-slate-100 ${styles.cardTextMuted}`
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
                      <div className={`px-6 py-2 border-t ${styles.cardBorder} flex items-center justify-between`}>
                        <span className={`text-[10px] ${styles.cardTextMuted} font-mono`}>
                          {t('ow.explore.showingInstances')} {dataPage} / {dataTotalPages}
                        </span>
                        <div className="flex items-center gap-2">
                          <button
                            disabled={dataPage <= 1}
                            onClick={() => setDataPage(p => Math.max(1, p - 1))}
                            className={`h-6 px-2.5 rounded text-[10px] ${styles.cardBg} border ${styles.cardBorder} ${styles.cardTextMuted} disabled:opacity-40 disabled:cursor-not-allowed hover:bg-slate-50 transition-colors`}
                          >
                            {t('ow.btn.previousPage')}
                          </button>
                          <button
                            disabled={dataPage >= dataTotalPages}
                            onClick={() => setDataPage(p => Math.min(dataTotalPages, p + 1))}
                            className={`h-6 px-2.5 rounded text-[10px] ${styles.cardBg} border ${styles.cardBorder} ${styles.cardTextMuted} disabled:opacity-40 disabled:cursor-not-allowed hover:bg-slate-50 transition-colors`}
                          >
                            {t('ow.btn.nextPage')}
                          </button>
                        </div>
                      </div>
                    </div>
                    )}
                   </div>
                ) : (
                  // ANALYTICS / CHART TAB
                  <div className={`flex-1 ${styles.cardBg} p-6 space-y-6 overflow-y-auto`}>
                    <div className="space-y-1">
                      <h3 className={`text-xs font-semibold ${styles.cardText} flex items-center gap-1.5`}>
                        <AreaChart size={14} className={styles.accentText} />
                        {t('ow.explore.analyticsTitle')}
                      </h3>
                      <p className={`text-[10px] ${styles.cardTextMuted}`}>
                        {t('ow.explore.analyticsDesc')}<strong>「{analyticsData ? (analyticsData as any).property?.displayName : ''}」</strong>
                      </p>
                    </div>

                    {processedInstances.length === 0 ? (
                      <div className={`text-center py-20 ${styles.muted}`}>{t('ow.empty.noDataForChart')}</div>
                    ) : (
                      <div className="grid grid-cols-2 gap-8 items-start">
                        {/* Custom visual distribution bars */}
                        <div className={`border ${styles.cardBorder} rounded-xl p-5 space-y-3 shadow-3xs bg-slate-50/20`}>
                          <h4 className={`text-[11px] font-semibold ${styles.cardTextMuted}`}>{t('ow.explore.barChartTitle')}</h4>
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
                                  showToast('info', t('ow.msg.chartDrillDown').replace('{prop}', propId).replace('{value}', item.name));
                                }}
                                className="group cursor-pointer space-y-1"
                              >
                                <div className="flex justify-between text-[11px]">
                                  <span className={`font-medium ${styles.cardTextMuted} group-hover:text-blue-600 font-mono transition-colors`}>{item.name}</span>
                                  <span className={`${styles.cardTextMuted} font-mono`}><strong>{item.count}</strong> {t('ow.label.countUnit')} ({item.percentage}%)</span>
                                </div>
                                <div className={`h-4 w-full bg-slate-100 rounded overflow-hidden flex`}>
                                  <div
                                    className={`${styles.accentBg} group-hover:bg-blue-500 transition-all rounded-r duration-500`}
                                    style={{ width: `${item.percentage}%` } as React.CSSProperties}
                                  />
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>

                        {/* Summary table list */}
                        <div className={`border ${styles.cardBorder} rounded-xl p-5 space-y-3 ${styles.cardBg}`}>
                          <h4 className={`text-[11px] font-semibold ${styles.cardTextMuted}`}>{t('ow.explore.groupCountTable')}</h4>
                          <table className="w-full text-left border-collapse text-[11px]">
                            <thead>
                              <tr className={`border-b border-slate-100 ${styles.muted}`}>
                                <th className="pb-2">{t('ow.explore.groupCategory')}</th>
                                <th className="pb-2 text-right">{t('ow.explore.instanceCount')}</th>
                                <th className="pb-2 text-right">{t('ow.explore.percentage')}</th>
                              </tr>
                            </thead>
                            <tbody className={`divide-y divide-slate-50 ${styles.cardTextMuted}`}>
                              {(analyticsData as any).data?.map((item: any) => (
                                <tr key={item.name} className="hover:bg-slate-50">
                                  <td className={`py-2 font-mono ${styles.cardTextMuted} font-medium`}>{item.name}</td>
                                  <td className={`py-2 text-right font-mono font-semibold ${styles.cardText}`}>{item.count}</td>
                                  <td className={`py-2 text-right font-mono ${styles.cardTextMuted}`}>{item.percentage}%</td>
                                </tr>
                              ))}
                              <tr className={`border-t ${styles.cardBorder} ${styles.cardText} font-bold`}>
                                <td className="py-2">{t('ow.explore.total')}</td>
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
                <div className={`w-96 border-l ${styles.cardBorder} ${styles.cardBg} flex flex-col shrink-0 overflow-hidden relative`}>
                  
                  {/* Detailed Panel Header */}
                  <div className={`p-4 border-b ${styles.cardBorder} bg-slate-50/50 flex flex-col gap-3`}>
                    <div className="flex justify-between items-start">
                      <div className="flex items-center gap-2">
                        <span className={`p-1.5 rounded-lg border ${activeObjectType.color}`}>
                          <DynamicIcon name={activeObjectType.icon} size={14} />
                        </span>
                        <div>
                          <div className={`text-[10px] ${styles.muted} font-bold uppercase tracking-wider`}>{activeObjectType.displayName} {t('ow.explore.detail')}</div>
                          <h3 className={`text-xs font-bold font-mono text-slate-950 mt-0.5`}>
                            {selectedInstance[activeObjectType.titleProperty]}
                          </h3>
                        </div>
                      </div>
                      <button
                        onClick={() => setSelectedInstance(null)}
                        className={`p-1 rounded hover:bg-slate-200 ${styles.muted} hover:text-slate-700`}
                      >
                        <X size={14} />
                      </button>
                    </div>

                    {/* Action Execution Button Dropdown */}
                    {availableActions.length > 0 && (
                      <div className="pt-1.5">
                        <div className={`text-[10px] ${styles.muted} uppercase tracking-wider font-semibold mb-1 flex items-center gap-1`}>
                          <Terminal size={10} />
                          <span>{t('ow.explore.boundActions')}</span>
                        </div>
                        <div className="flex flex-col gap-1">
                          {availableActions.map(act => (
                            <button
                              key={act.id}
                              onClick={() => handleOpenActionModal(act)}
                              className="w-full h-8 px-2.5 rounded border border-amber-200 bg-amber-50/40 hover:bg-amber-50 text-amber-800 text-[10px] font-semibold flex items-center justify-between transition-all"
                            >
                              <div className="flex items-center gap-1.5">
                                <Zap size={12} className="fill-amber-400/20 text-amber-600" />
                                <span>{t('ow.explore.trigger')}{act.displayName}</span>
                              </div>
                              <ChevronRight size={10} className="text-amber-500" />
                            </button>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>

                  {/* Panel Tab switch */}
                  <div className={`flex border-b border-slate-100 px-2 text-[11px] font-medium bg-slate-50/20`}>
                    <button
                      onClick={() => setDetailTab('properties')}
                      className={`flex-1 py-2 text-center border-b-2 font-semibold transition-all ${
                        detailTab === 'properties' ? `${styles.accentBorder} text-blue-700 font-bold` : `border-transparent ${styles.cardTextMuted} hover:text-slate-800`
                      }`}
                    >
                      {t('ow.explore.tabProperties')}
                    </button>
                    <button
                      onClick={() => setDetailTab('relations')}
                      className={`flex-1 py-2 text-center border-b-2 font-semibold transition-all flex items-center justify-center gap-1 ${
                        detailTab === 'relations' ? `${styles.accentBorder} text-blue-700 font-bold` : `border-transparent ${styles.cardTextMuted} hover:text-slate-800`
                      }`}
                    >
                      {t('ow.explore.tabRelations')} ({resolvedRelations.reduce((acc, curr) => acc + curr.instances.length, 0)})
                    </button>
                    <button
                      onClick={() => setDetailTab('activity')}
                      className={`flex-1 py-2 text-center border-b-2 font-semibold transition-all ${
                        detailTab === 'activity' ? `${styles.accentBorder} text-blue-700 font-bold` : `border-transparent ${styles.cardTextMuted} hover:text-slate-800`
                      }`}
                    >
                      {t('ow.explore.tabActivity')}
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
                            <div key={p.id} className={`p-2.5 rounded-lg border border-slate-100 hover:border-slate-200 hover:bg-slate-50/30 transition-colors`}>
                              <div className={`flex items-center justify-between text-[10px] ${styles.muted} font-mono`}>
                                <span className={`font-semibold ${styles.cardTextMuted}`}>{p.displayName}</span>
                                <span className="uppercase">{p.dataType}</span>
                              </div>
                              <div className={`mt-1 font-mono text-xs font-semibold ${styles.cardText} flex items-center justify-between`}>
                                {isPk ? (
                                  <span className={`bg-slate-150 border ${styles.cardBorder} ${styles.cardText} rounded px-1.5 py-0.5 text-[10px]`}>
                                    {String(val ?? t('ow.label.unspecifiedValue'))}
                                  </span>
                                ) : p.id === 'status' ? (
                                  <span className={`px-1.5 py-0.5 rounded text-[10px] ${
                                    val === 'ACTIVE' || val === 'ON_TIME' ? 'bg-emerald-100 text-emerald-800' :
                                    val === 'MAINTENANCE' || val === 'DELAYED' ? 'bg-amber-100 text-amber-800' :
                                    `bg-slate-100 ${styles.cardTextMuted}`
                                  }`}>
                                    {String(val ?? 'N/A')}
                                  </span>
                                ) : (
                                  <span>{String(val ?? t('ow.label.nullValue'))}</span>
                                )}
                                
                                {isPk && (
                                  <span className="text-[9px] font-semibold text-red-500 bg-red-50 border border-red-100 px-1 rounded uppercase">Primary Key</span>
                                )}
                              </div>
                              <p className={`text-[10px] ${styles.muted} mt-1 leading-relaxed`}>{p.description}</p>
                            </div>
                          );
                        })}
                      </div>
                    )}

                    {/* tab 2: Relations / Connection Traversal */}
                    {detailTab === 'relations' && (
                      <div className="space-y-5">
                        <div className={`text-[10px] ${styles.muted} font-semibold uppercase leading-relaxed`}>
                          {t('ow.explore.relationTraversal')}
                        </div>

                        {resolvedRelations.length === 0 ? (
                          <div className={`text-center py-10 border border-dashed ${styles.cardBorder} rounded-lg ${styles.muted} text-[10px]`}>
                            {t('ow.empty.noDeclaredRelations')}
                          </div>
                        ) : (
                          <div className="space-y-4">
                            {resolvedRelations.map(rel => (
                              <div key={rel.linkType.id} className={`space-y-2 border border-slate-200/60 rounded-lg p-3 bg-slate-50/10`}>
                                {/* Header */}
                                <div className={`flex items-center justify-between text-[11px] pb-1.5 border-b border-slate-100`}>
                                  <div className={`flex items-center gap-1.5 font-semibold ${styles.cardText}`}>
                                    <GitMerge size={12} className="text-emerald-600" />
                                    <span>{rel.linkType.displayName}</span>
                                  </div>
                                  <span className="text-[10px] bg-emerald-100 text-emerald-700 px-1 py-0.2 rounded font-mono font-bold uppercase">
                                    {rel.linkType.cardinality}
                                  </span>
                                </div>
                                
                                <p className={`text-[10px] ${styles.muted}`}>{rel.linkType.description}</p>

                                {/* List matching connected instances */}
                                {rel.instances.length === 0 ? (
                                  <div className={`text-[10px] ${styles.muted} italic bg-slate-50 p-2 rounded text-center`}>
                                    {t('ow.empty.noRelatedInstances').replace('{name}', rel.otherObjectType.displayName)}
                                  </div>
                                ) : (
                                  <div className="space-y-1 pt-1">
                                    {rel.instances.map(inst => (
                                      <div
                                        key={inst[rel.otherObjectType.primaryKey]}
                                        onClick={() => handleJumpToInstance(rel.otherObjectType.id, inst[rel.otherObjectType.primaryKey])}
                                        className={`p-2 border border-slate-150 hover:border-blue-400 ${styles.cardBg} hover:bg-blue-50/10 rounded-md cursor-pointer flex justify-between items-center transition-all group`}
                                      >
                                        <div className="flex items-center gap-2 truncate">
                                          <span className={`p-1 rounded ${rel.otherObjectType.color}`}>
                                            <DynamicIcon name={rel.otherObjectType.icon} size={11} />
                                          </span>
                                          <span className={`font-mono text-xs font-semibold ${styles.cardText}`}>
                                            {inst[rel.otherObjectType.primaryKey]}
                                          </span>
                                          <span className={`text-[10px] ${styles.muted} truncate max-w-[100px]`}>
                                            ({inst[rel.otherObjectType.titleProperty]})
                                          </span>
                                        </div>
                                        <Compass size={11} className={`${styles.muted} group-hover:text-blue-600 transition-colors`} />
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
                        <div className={`relative border-l ${styles.cardBorder} pl-4 ml-2 space-y-5 py-2`}>
                          <div className="relative text-[11px]">
                            <span className={`absolute -left-6 top-1 w-3 h-3 rounded-full bg-blue-500 border-2 border-white`} />
                            <div className={`font-semibold ${styles.cardText}`}>{t('ow.explore.activityLoaded')}</div>
                            <p className={`${styles.muted} text-[10px] mt-0.5`}>{t('ow.explore.activityLoadedDesc')}</p>
                            <span className={`text-[9px] font-mono ${styles.muted}`}>2026-07-02 20:34</span>
                          </div>
                          
                          {selectedInstance.status === 'MAINTENANCE' && (
                            <div className="relative text-[11px]">
                              <span className="absolute -left-6 top-1 w-3 h-3 rounded-full bg-amber-500 border-2 border-white" />
                              <div className={`font-semibold ${styles.cardText}`}>{t('ow.explore.activityMaintenance')}</div>
                              <p className={`${styles.muted} text-[10px] mt-0.5`}>{t('ow.explore.activityMaintenanceDesc')}</p>
                              <span className={`text-[9px] font-mono ${styles.muted}`}>{t('ow.label.justNow')}</span>
                            </div>
                          )}

                          {selectedInstance.status === 'DELAYED' && (
                            <div className="relative text-[11px]">
                              <span className="absolute -left-6 top-1 w-3 h-3 rounded-full bg-red-400 border-2 border-white" />
                              <div className={`font-semibold ${styles.cardText}`}>{t('ow.explore.activityDelayed')}</div>
                              <p className={`${styles.muted} text-[10px] mt-0.5`}>{t('ow.explore.activityDelayedDesc')}</p>
                              <span className={`text-[9px] font-mono ${styles.muted}`}>{t('ow.label.justNow')}</span>
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

      {/* Save Search Modal */}
      {showSaveModal && (
        <SaveSearchModal newSearchName={newSearchName} setNewSearchName={setNewSearchName}
          setShowSaveModal={setShowSaveModal} handleSaveSearch={handleSaveSearch} />
      )}

      {/* Execute Action Modal */}
      {selectedAction && (
        <ActionExecutorModal selectedAction={selectedAction} activeObjectType={activeObjectType}
          actionParams={actionParams} setActionParams={setActionParams}
          actionError={actionError} setSelectedAction={setSelectedAction}
          handleExecuteAction={handleExecuteAction} />
      )}

    </div>
  );
}
