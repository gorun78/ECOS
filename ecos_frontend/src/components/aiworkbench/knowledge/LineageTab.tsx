/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { useLanguage } from '../../../components/LanguageContext';
import type { ThemeStyles } from '../../../components/ThemeContext';
import * as Icons from 'lucide-react';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

interface LineageTabProps {
  styles: ThemeStyles;
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
  rawPayloadInput: string;
  setRawPayloadInput: (v: string) => void;
  parserFormatSelected: 'openlineage' | 'atlas';
  setParserFormatSelected: (v: 'openlineage' | 'atlas') => void;
  handleParseLineage: () => Promise<void>;
  isParsing: boolean;
  lineageNodes: any[];
  lineageLinks: any[];
  selectedStartNode: string;
  setSelectedStartNode: (v: string) => void;
  impactResult: any;
  isAnalyzing: boolean;
  handleRunImpactAnalysis: (startNodeId: string) => Promise<void>;
}

export default function LineageTab({
  styles,
  rawPayloadInput,
  setRawPayloadInput,
  parserFormatSelected,
  setParserFormatSelected,
  handleParseLineage,
  isParsing,
  lineageNodes,
  lineageLinks,
  selectedStartNode,
  setSelectedStartNode,
  impactResult,
  isAnalyzing,
}: LineageTabProps) {
  const { t } = useLanguage();
  return (
    <div className="space-y-6">
      <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-3`}>
        <div className="space-y-1">
          <h2 className={`text-sm font-black ${styles.cardText}`}>{t('aiworkbench.knowledge.lineage.title')} (Lineage Parser & Impact Lab)</h2>
          <p className={`text-xs ${styles.cardTextMuted}`}>
            {t('aiworkbench.knowledge.lineage.desc')}
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
        
        {/* Left Column: Parser Input */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 shadow-xs flex flex-col space-y-4`}>
          <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-2.5`}>
            <h3 className={`font-extrabold ${styles.cardText} text-xs flex items-center gap-1.5`}>
              <Icon name="Code" size={13} className="text-indigo-600" />
              <span>{t('aiworkbench.knowledge.lineage.payloadTitle')} (Lineage Parser)</span>
            </h3>
            <div className={`flex ${styles.appBg} p-0.5 rounded-lg border ${styles.cardBorder} text-[10px]`}>
              <button
                onClick={() => {
                  setParserFormatSelected('openlineage');
                  setRawPayloadInput(JSON.stringify({
                    "eventType": "COMPLETE",
                    "eventTime": "2026-07-04T12:00:00Z",
                    "producer": "https://github.com/OpenLineage/OpenLineage",
                    "job": { "namespace": "ds_scheduler", "name": "spark_clean_flight_acars_job" },
                    "inputs": [{ "namespace": "postgresql_raw_sched", "name": "flights_raw" }],
                    "outputs": [{ "namespace": "doris_production_olap", "name": "ds_flights_clean" }]
                  }, null, 2,)};
                }}
                className={`px-2.5 py-1 rounded-md font-bold transition-all cursor-pointer ${parserFormatSelected === 'openlineage' ? 'styles.cardBg styles.cardText shadow-xs' : 'styles.cardTextMuted hover:styles.cardText'}`}
              >
                OpenLineage
              </button>
              <button
                onClick={() => {
                  setParserFormatSelected('atlas');
                  setRawPayloadInput(JSON.stringify([
                    {
                      "typeName": "spark_process",
                      "attributes": {
                        "name": "spark_process_pilots_biography_sync",
                        "qualifiedName": "spark_process_pilots_biography_sync@cluster",
                        "inputs": [{ "typeName": "hive_table", "uniqueAttributes": { "qualifiedName": "postgresql_raw_sched.pilots_raw@cluster" } }],
                        "outputs": [{ "typeName": "hive_table", "uniqueAttributes": { "qualifiedName": "doris_production_olap.ds_pilots_biography@cluster" } }]
                      }
                    }
                  ], null, 2,)};
                }}
                className={`px-2.5 py-1 rounded-md font-bold transition-all cursor-pointer ${parserFormatSelected === 'atlas' ? 'styles.cardBg styles.cardText shadow-xs' : 'styles.cardTextMuted hover:styles.cardText'}`}
              >
                Apache Atlas
              </button>
            </div>
          </div>

          <div className="flex flex-col flex-1 space-y-2">
            <div className="flex justify-between items-center text-[10px]">
              <span className={`${styles.cardTextMuted} font-bold uppercase tracking-wider font-mono`}>Payload JSON Input</span>
              <button 
                onClick={() => {
                  if (parserFormatSelected === 'openlineage') {
                    setRawPayloadInput(JSON.stringify({
                      "eventType": "COMPLETE",
                      "eventTime": "2026-07-04T12:00:00Z",
                      "producer": "https://github.com/OpenLineage/OpenLineage",
                      "job": { "namespace": "ds_scheduler", "name": "spark_clean_flight_acars_job" },
                      "inputs": [{ "namespace": "postgresql_raw_sched", "name": "flights_raw" }],
                      "outputs": [{ "namespace": "doris_production_olap", "name": "ds_flights_clean" }]
                    }, null, 2,)};
                  } else {
                    setRawPayloadInput(JSON.stringify([
                      {
                        "typeName": "spark_process",
                        "attributes": {
                          "name": "spark_process_pilots_biography_sync",
                          "qualifiedName": "spark_process_pilots_biography_sync@cluster",
                          "inputs": [{ "typeName": "hive_table", "uniqueAttributes": { "qualifiedName": "postgresql_raw_sched.pilots_raw@cluster" } }],
                          "outputs": [{ "typeName": "hive_table", "uniqueAttributes": { "qualifiedName": "doris_production_olap.ds_pilots_biography@cluster" } }]
                        }
                      }
                    ], null, 2,)};
                  }
                }}
                className="text-indigo-600 hover:text-indigo-700 font-bold hover:underline cursor-pointer"
              >
                {t('aiworkbench.knowledge.lineage.resetDefault')}
              </button>
            </div>

            <textarea
              value={rawPayloadInput}
              onChange={(e) => setRawPayloadInput(e.target.value)}
              className={`flex-1 min-h-[160px] p-3 font-mono text-[10px] ${styles.appBg} styles.cardText rounded-lg border ${styles.cardBorder} focus:outline-none focus:ring-1 focus:ring-indigo-500 leading-relaxed resize-none`}
              placeholder="{t('aiworkbench.knowledge.lineage.inputHint')}"
            />
          </div>

          <button
            onClick={handleParseLineage}
            disabled={isParsing || !rawPayloadInput.trim()}
            className={`w-full py-2 ${styles.accentBg} ${styles.accentHover} text-white font-extrabold rounded-lg shadow-sm transition-all flex items-center justify-center gap-1.5 cursor-pointer text-xs`}
          >
            {isParsing ? (
              <>
                <span className="w-3 h-3 border-2 border-white border-t-transparent rounded-full animate-spin" />
                <span>{t('aiworkbench.knowledge.lineage.parsing')}</span>
              </>
            ) : (
              <>
                <Icon name="FileInput" size={12} />
                <span>{t('aiworkbench.knowledge.lineage.parseBtn')} (Parse Metadata)</span>
              </>
            )}
          </button>
        </div>

        {/* Right Column: Live Lineage Map */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 shadow-xs flex flex-col space-y-4`}>
          <h3 className={`font-extrabold ${styles.cardText} text-xs border-b ${styles.cardBorder} pb-2.5 flex items-center gap-1.5`}>
            <Icon name="Network" size={13} className="text-emerald-600" />
            <span>{t('aiworkbench.knowledge.lineage.topoTitle')} (Live Lineage Map)</span>
          </h3>

          <p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed font-sans -mt-1`}>
            {t('aiworkbench.knowledge.lineage.topoDesc').replace('{nodes}', String(lineageNodes.length,)}.replace('{links}', String(lineageLinks.length)}
          </p>

          <div className={`flex-1 ${styles.inputBg} border ${styles.cardBorder}/60 rounded-xl p-4 overflow-y-auto max-h-[300px] space-y-3`}>
            <div className={`text-[9px] font-bold ${styles.cardTextMuted} uppercase tracking-wider mb-2 flex justify-between font-mono`}>
              <span>{t('aiworkbench.knowledge.lineage.tierLabel')} (Lineage Layers)</span>
              <span>{t('aiworkbench.knowledge.lineage.tierLabel')}</span>
            </div>

            <div className="space-y-4">
              {/* Layer Group 1: Physical DataSources */}
              <div className="space-y-1.5">
                <div className={`text-[8px] font-extrabold ${styles.cardTextMuted} tracking-wider uppercase`}>1. {t('aiworkbench.knowledge.lineage.tierSource')} (Raw DataSources)</div>
                <div className="flex flex-wrap gap-2">
                  {lineageNodes.filter(n => n.type === 'physical_table').map(node => (
                    <div key={node.id} className={`p-2 ${styles.cardBg} border ${styles.cardBorder} rounded-lg flex items-center gap-2 ${styles.accentBorder} transition-all cursor-pointer shadow-xs`}>
                      <span className={`w-2 h-2 rounded-full ${styles.cardBorder}`} />
                      <div>
                        <div className={`font-mono text-[9px] font-bold ${styles.cardTextMuted}`}>{node.id}</div>
                        <div className={`text-[8px] ${styles.cardTextMuted} font-sans`}>{node.label}</div>
                      </div>
                    </div>
                  )}
                </div>
              </div>

              {/* Layer Group 2: ETL Pipelines */}
              <div className="space-y-1.5">
                <div className={`text-[8px] font-extrabold ${styles.cardTextMuted} tracking-wider uppercase`}>2. {t('aiworkbench.knowledge.lineage.tierJob')} (ETL Pipelines)</div>
                <div className="flex flex-wrap gap-2">
                  {lineageNodes.filter(n => n.type === 'etl_job').map(node => (
                    <div key={node.id} className="p-2 bg-blue-50/50 border border-blue-200 rounded-lg flex items-center gap-2 hover:border-blue-400 transition-all cursor-pointer shadow-xs">
                      <span className="w-2 h-2 rounded-full bg-blue-500 animate-pulse" />
                      <div>
                        <div className="font-mono text-[9px] font-bold text-blue-800">{node.id}</div>
                        <div className="text-[8px] text-blue-500 font-sans">{node.label}</div>
                      </div>
                    </div>
                  )}
                </div>
              </div>

              {/* Layer Group 3: Cleansed OLAP Tables */}
              <div className="space-y-1.5">
                <div className={`text-[8px] font-extrabold ${styles.cardTextMuted} tracking-wider uppercase`}>3. {t('aiworkbench.knowledge.lineage.tierTable')} (Cleansed OLAP)</div>
                <div className="flex flex-wrap gap-2">
                  {lineageNodes.filter(n => n.type === 'olap_table').map(node => (
                    <div key={node.id} className="p-2 bg-emerald-50/50 border border-emerald-200 rounded-lg flex items-center gap-2 hover:border-emerald-400 transition-all cursor-pointer shadow-xs">
                      <span className="w-2 h-2 rounded-full bg-emerald-500" />
                      <div>
                        <div className="font-mono text-[9px] font-bold text-emerald-800">{node.id}</div>
                        <div className="text-[8px] text-emerald-500 font-sans">{node.label}</div>
                      </div>
                    </div>
                  )}
                </div>
              </div>

              {/* Layer Group 4: Ontology Object Semantics */}
              <div className="space-y-1.5">
                <div className={`text-[8px] font-extrabold ${styles.cardTextMuted} tracking-wider uppercase`}>4. {t('aiworkbench.knowledge.lineage.tierOntology')} (Ontology Object Semantics)</div>
                <div className="flex flex-wrap gap-2">
                  {lineageNodes.filter(n => n.type === 'ontology_object').map(node => (
                    <div key={node.id} className="p-2 bg-indigo-50/50 border border-indigo-200 rounded-lg flex items-center gap-2 hover:border-indigo-400 transition-all cursor-pointer shadow-xs">
                      <span className={`w-2 h-2 rounded-full ${styles.accentBg}`} />
                      <div>
                        <div className="font-mono text-[9px] font-bold text-indigo-800">{node.id}</div>
                        <div className={`text-[8px] ${styles.accentText} font-sans`}>{node.label}</div>
                      </div>
                    </div>
                  )}
                </div>
              </div>

              {/* Layer Group 5: Reporting Dashboard */}
              <div className="space-y-1.5">
                <div className={`text-[8px] font-extrabold ${styles.cardTextMuted} tracking-wider uppercase`}>5. {t('aiworkbench.knowledge.lineage.tierConsumer')} (Downstream Reports)</div>
                <div className="flex flex-wrap gap-2">
                  {lineageNodes.filter(n => n.type === 'dashboard').map(node => (
                    <div key={node.id} className="p-2 bg-rose-50/50 border border-rose-200 rounded-lg flex items-center gap-2 hover:border-rose-400 transition-all cursor-pointer shadow-xs">
                      <span className="w-2 h-2 rounded-full bg-rose-500" />
                      <div>
                        <div className="font-mono text-[9px] font-bold text-rose-800">{node.id}</div>
                        <div className="text-[8px] text-rose-500 font-sans">{node.label}</div>
                      </div>
                    </div>
                  )}
                </div>
              </div>

            </div>
          </div>
        </div>

      </div>

      {/* Bottom Panel: Downstream Impact Analysis */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 shadow-xs space-y-4`}>
        <div className={`flex flex-col md:flex-row md:items-center justify-between border-b ${styles.cardBorder} pb-3 gap-3`}>
          <div className="space-y-1">
            <h3 className={`font-extrabold ${styles.cardText} text-xs flex items-center gap-1.5`}>
              <Icon name="ShieldCheck" size={13} className="text-rose-600" />
              <span>{t('aiworkbench.knowledge.lineage.impactTitle')} (Impact Analysis)</span>
            </h3>
            <p className={`text-[10px] ${styles.cardTextMuted}`}>{t('aiworkbench.knowledge.lineage.impactDesc')}</p>
          </div>

          <div className="flex items-center gap-2">
            <span className={`text-[10px] font-bold ${styles.cardTextMuted} font-sans`}>{t('aiworkbench.knowledge.lineage.selectObject')}:</span>
            <select
              value={selectedStartNode}
              onChange={(e) => setSelectedStartNode(e.target.value)}
              className={`px-3 py-1.5 border ${styles.cardBorder} rounded-lg text-xs font-mono ${styles.cardBg} ${styles.cardText} font-bold cursor-pointer`}
            >
              {lineageNodes.filter(n => n.type === 'physical_table' || n.type === 'olap_table').map(n => (
                <option key={n.id} value={n.id}>{n.id} ({n.type === 'physical_table' ? t('aiworkbench.knowledge.lineage.sourceTable').split(' / ')[0] : t('aiworkbench.knowledge.lineage.sourceTable').split(' / ')[1] || 'OLAP Wide Table'})</option>
              )}
            </select>
          </div>
        </div>

        {/* Impact analysis results dashboard */}
        {isAnalyzing ? (
          <div className="py-12 flex flex-col items-center justify-center space-y-2">
            <span className="w-6 h-6 border-2 border-indigo-600 border-t-transparent rounded-full animate-spin" />
            <span className={`${styles.cardTextMuted} font-bold text-[10px]`}>{t('aiworkbench.knowledge.lineage.analyzing')}</span>
          </div>
        ) : impactResult ? (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            
            {/* Dial Panel */}
            <div className={`${styles.inputBg} border ${styles.cardBorder} rounded-xl p-4 flex flex-col items-center justify-center text-center space-y-3 relative overflow-hidden`}>
              
              {/* Background indicator */}
              <div className={`absolute top-0 left-0 right-0 h-1.5 ${
                impactResult.severity === 'CRITICAL' ? 'bg-rose-500 animate-pulse' :
                impactResult.severity === 'HIGH' ? 'bg-amber-500' :
                impactResult.severity === 'MEDIUM' ? 'bg-orange-400' : 'bg-emerald-500'
              }`} />

              <span className={`text-[9px] font-extrabold ${styles.cardTextMuted} uppercase tracking-widest font-mono`}>{t('aiworkbench.knowledge.lineage.delayScore')}</span>
              
              <div className="space-y-1">
                <div className={`text-4xl font-black ${
                  impactResult.severity === 'CRITICAL' ? 'text-rose-600 animate-pulse' :
                  impactResult.severity === 'HIGH' ? 'text-amber-600' :
                  impactResult.severity === 'MEDIUM' ? 'text-orange-500' : 'text-emerald-600'
                }`}>
                  {impactResult.totalRisk}
                </div>
                <div className={`text-[10px] font-extrabold uppercase px-2 py-0.5 rounded-full inline-block ${
                  impactResult.severity === 'CRITICAL' ? 'bg-rose-100 text-rose-700 font-black animate-bounce' :
                  impactResult.severity === 'HIGH' ? 'bg-amber-100 text-amber-700' :
                  impactResult.severity === 'MEDIUM' ? 'bg-orange-100 text-orange-700' : 'bg-emerald-100 text-emerald-700'
                }`}>
                  {impactResult.severity} {t('aiworkbench.knowledge.lineage.riskLevel')}
                </div>
              </div>

              <div className={`text-[10px] ${styles.cardTextMuted} leading-relaxed font-sans max-w-xs font-medium`}>
                {impactResult.severity === 'CRITICAL' ? (
                  <p className="text-rose-600 font-bold">
                    {t('aiworkbench.knowledge.lineage.riskCritical')}
                  </p>
                ) : impactResult.severity === 'HIGH' ? (
                  <p className="text-amber-600 font-bold">
                    {t('aiworkbench.knowledge.lineage.riskWarning')}
                  </p>
                ) : (
                  <p className={`${styles.cardTextMuted}`}>
                    {t('aiworkbench.knowledge.lineage.riskNormal')}
                  </p>
                )}
              </div>
            </div>

            {/* Impact Path and Hops */}
            <div className={`lg:col-span-2 ${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 space-y-3`}>
              <h4 className={`font-extrabold ${styles.cardTextMuted} text-[10px] uppercase tracking-wider border-b ${styles.cardBorder} pb-2 flex justify-between`}>
                <span>{t('aiworkbench.knowledge.lineage.impactScope')} ({impactResult.impactedNodes.length})</span>
                <span className={`font-mono ${styles.cardTextMuted}`}>Starting Node: {selectedStartNode}</span>
              </h4>

              {impactResult.impactedNodes.length === 0 ? (
                <div className={`h-full flex flex-col items-center justify-center py-6 ${styles.cardTextMuted} text-center`}>
                  <Icon name="ShieldAlert" size={18} className={`${styles.cardTextMuted}`} />
                  <p className="text-[10px] font-bold">{t('aiworkbench.knowledge.lineage.noDownstream')}</p>
                  <p className={`text-[9px] ${styles.cardTextMuted}`}>{t('aiworkbench.knowledge.lineage.noDownstream')}</p>
                </div>
              ) : (
                <div className="space-y-2.5 max-h-48 overflow-y-auto">
                  {impactResult.impactedNodes.map((node: any, i: number) => (
                    <div key={i} className={`flex items-center justify-between p-2 rounded-lg ${styles.inputBg} border ${styles.cardBorder} hover:styles.appBg/70 transition-all text-[10px]`}>
                      <div className="space-y-0.5">
                        <div className="flex items-center gap-1.5">
                          <span className={`px-1.5 py-0.2 rounded-xs font-bold text-[8px] uppercase ${
                            node.type === 'etl_job' ? 'bg-blue-100 text-blue-700' :
                            node.type === 'olap_table' ? 'bg-emerald-100 text-emerald-700' :
                            node.type === 'ontology_object' ? 'bg-indigo-100 text-indigo-700' :
                            'bg-rose-100 text-rose-700'
                          }`}>
                            {node.type === 'etl_job' ? t('aiworkbench.knowledge.lineage.colNode').split(' / ')[0] :
                             node.type === 'olap_table' ? t('aiworkbench.knowledge.lineage.colNode').split(' / ')[1] :
                             node.type === 'ontology_object' ? t('aiworkbench.knowledge.lineage.colNode').split(' / ')[2] : t('aiworkbench.knowledge.lineage.colNode').split(' / ')[3]}
                          </span>
                          <span className={`font-bold ${styles.cardText} font-mono`}>{node.id}</span>
                        </div>
                        <p className={`text-[9px] ${styles.cardTextMuted} font-sans`}>
                          {t('aiworkbench.knowledge.lineage.colChain')}: {node.path.join(' ➔ ')}
                        </p>
                      </div>

                      <div className="text-right shrink-0">
                        <div className={`font-mono font-bold ${styles.cardTextMuted}`}>{t('aiworkbench.knowledge.lineage.colDistance')}: {node.hopCount} Hops</div>
                        <div className={`font-mono font-extrabold text-[11px] ${
                          node.riskScore > 80 ? 'text-rose-600' :
                          node.riskScore > 50 ? 'text-amber-600' : 'text-emerald-600'
                        }`}>
                          {t('aiworkbench.knowledge.lineage.colRiskScore')}: {node.riskScore}
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>

          </div>
        ) : (
          <div className={`py-6 text-center ${styles.cardTextMuted} text-[10px]`}>{t('aiworkbench.knowledge.lineage.selectHint')}</div>
        )}
      </div>

    </div>
  );
}