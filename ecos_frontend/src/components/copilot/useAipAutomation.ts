/**
 * AIP Copilot — automation hook
 * Encapsulates the 12 scenario execution flows (runAgentAutomation),
 * plus the General Q&A fallback path and message state.
 * Display strings are pulled from the `copilot.*` i18n namespace.
 *
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { useCallback, useRef, useState } from 'react';
import { useLanguage } from '../LanguageContext';
import {
  SecurityOrg, ProjectDAC, SecurityMarking, PurposePBAC, RowColPolicy, SecurityAuditLog
} from '../../../pages/security-center/types';
import { AgentScenarioType, scenarioPrompt } from './AgentScenarioData';

type ViewMode = 'ontology' | 'explorer' | 'integration' | 'knowledge' | 'aip' | 'security' | 'workshop';

interface Message {
  id: string;
  sender: 'user' | 'agent';
  text: string;
  timestamp: string;
  isExecuting?: boolean;
  executionStep?: string;
  completed?: boolean;
  automationType?: AgentScenarioType;
}

export interface AipAutomationProps {
  viewMode: ViewMode;
  onViewModeChange: (mode: ViewMode) => void;
  selectedCategory: 'overview' | 'object' | 'link' | 'action' | 'interface' | 'shared_property' | 'dataset' | 'function';
  onSelectCategory: (category: 'overview' | 'object' | 'link' | 'action' | 'interface' | 'shared_property' | 'dataset' | 'function', id: string | null) => void;
  integrationTab: 'connections' | 'syncs' | 'pipelines' | 'health' | 'lineage' | 'pipeline-builder' | 'code-repositories' | 'code-workbooks' | 'contour' | 'guide';
  onIntegrationTabChange: (tab: 'connections' | 'syncs' | 'pipelines' | 'health' | 'lineage' | 'pipeline-builder' | 'code-repositories' | 'code-workbooks' | 'contour' | 'guide') => void;
  showToast: (type: 'success' | 'info' | 'error', message: string) => void;
  objectTypes: any[];
  setObjectTypes: (val: any[]) => void;
  linkTypes: any[];
  setLinkTypes: (val: any[]) => void;
  datasets: any[];
  setDatasets: (val: any[]) => void;
  securityTab: 'overview' | 'orgs' | 'dac' | 'mac' | 'pbac' | 'row_col' | 'audit';
  onSecurityTabChange: (tab: 'overview' | 'orgs' | 'dac' | 'mac' | 'pbac' | 'row_col' | 'audit') => void;
  securityOrgs: SecurityOrg[];
  setSecurityOrgs: (val: SecurityOrg[]) => void;
  securityProjects: ProjectDAC[];
  setSecurityProjects: (val: ProjectDAC[]) => void;
  securityMarkings: SecurityMarking[];
  setSecurityMarkings: (val: SecurityMarking[]) => void;
  securityPurposes: PurposePBAC[];
  setSecurityPurposes: (val: PurposePBAC[]) => void;
  securityRowColPolicies: RowColPolicy[];
  setSecurityRowColPolicies: (val: RowColPolicy[]) => void;
  securityAuditLogs: SecurityAuditLog[];
  setSecurityAuditLogs: (val: SecurityAuditLog[]) => void;
  securitySimUser: string;
  setSecuritySimUser: (val: string) => void;
  securitySimDataset: string;
  setSecuritySimDataset: (val: string) => void;
  securitySimPurpose: string;
  setSecuritySimPurpose: (val: string) => void;
  securitySimResult: { verdict: 'GRANTED' | 'DENIED'; traces: string[] } | null;
  setSecuritySimResult: (val: { verdict: 'GRANTED' | 'DENIED'; traces: string[] } | null) => void;
  securitySelectedRowColDs: string;
  setSecuritySelectedRowColDs: (val: string) => void;
}

interface CopilotAutomationApi {
  messages: Message[];
  isTyping: boolean;
  currentStep: string;
  setMessages: React.Dispatch<React.SetStateAction<Message[]>>;
  runAgentAutomation: (type: AgentScenarioType, customPromptText?: string) => Promise<void>;
}

/**
 * Encapsulates the heavy automation flow: scenario prompt match,
 * step-by-step UI roaming, mutation of App-level state, audit-log injection.
 */
export function useAipAutomation(props: AipAutomationProps): CopilotAutomationApi {
  const {
    viewMode,
    onViewModeChange,
    onSelectCategory,
    onIntegrationTabChange,
    securityTab,
    onSecurityTabChange,
    showToast,
    objectTypes, setObjectTypes,
    linkTypes, setLinkTypes,
    securityOrgs, setSecurityOrgs,
    securityProjects, setSecurityProjects,
    securityMarkings, setSecurityMarkings,
    securityPurposes, setSecurityPurposes,
    securityRowColPolicies, setSecurityRowColPolicies,
    securityAuditLogs, setSecurityAuditLogs,
    securitySimUser, setSecuritySimUser,
    securitySimDataset, setSecuritySimDataset,
    securitySimPurpose, setSecuritySimPurpose,
    securitySimResult, setSecuritySimResult,
    securitySelectedRowColDs, setSecuritySelectedRowColDs,
  } = props;

  const { t } = useLanguage();

  // Initial welcome message (agent greeting) — i18n-driven
  const [messages, setMessages] = useState<Message[]>(() => {
    const os = (typeof window !== 'undefined' && window.localStorage.getItem('ecos_locale')) as 'zh' | 'en' | null;
    return [{
      id: 'welcome',
      sender: 'agent',
      text: os ? t('copilot.chat.welcome') : t('copilot.chat.welcome'),
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    }];
  });
  const [isTyping, setIsTyping] = useState(false);
  const [currentStep, setCurrentStep] = useState('');

  const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

  /**
   * Update the agent-message reply for one scenario and clear the step banner.
   * Keeps the message mapping concise; safe for partial resets.
   */
  const setAgentResult = useCallback((agentMsgId: string, text: string) => {
    setMessages((prev) => prev.map((m) => m.id === agentMsgId
      ? { ...m, text, isExecuting: false, completed: true }
      : m));
    setCurrentStep('');
  }, []);

  const runAgentAutomation = useCallback(async (type: AgentScenarioType, customPromptText?: string) => {
    const userPrompt = customPromptText || scenarioPrompt(t, type);
    const userMsgId = `user-${Date.now()}`;
    setMessages((prev) => [...prev, {
      id: userMsgId,
      sender: 'user',
      text: userPrompt,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    }]);

    setIsTyping(true);
    await sleep(800);

    // Intent mapping — switch on scenario type → i18n label
    const intentKeyMap: Record<AgentScenarioType, string> = {
      pipeline: 'copilot.intent.pipeline',
      ontology: 'copilot.intent.ontology',
      health: 'copilot.intent.health',
      lineage: 'copilot.intent.lineage',
      sec_gdpr: 'copilot.intent.sec_gdpr',
      sec_row_col: 'copilot.intent.sec_row_col',
      sec_finance: 'copilot.intent.sec_finance',
      sec_audit: 'copilot.intent.sec_audit',
      ws_generate_dashboard: 'copilot.intent.default',
      ws_auto_bind: 'copilot.intent.default',
      ws_inject_copilot: 'copilot.intent.default',
      ws_transform_theme: 'copilot.intent.default',
    };
    const agentMsgId = `agent-exec-${Date.now()}`;
    const scenarioName = t(intentKeyMap[type] ?? 'copilot.intent.default');

    setMessages((prev) => [...prev, {
      id: agentMsgId,
      sender: 'agent',
      text: `🤖 **${t('copilot.chat.securityEngine')}**\n\n  ${t('copilot.chat.intentDetect')}: ${scenarioName}\n  ${t('copilot.chat.securityEngineName')}\n\n${t('copilot.chat.fullChain')}`,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      isExecuting: true,
      executionStep: t('copilot.chat.intentCompiling'),
      automationType: type,
    }]);

    setIsTyping(false);
    const resultKey = `copilot.result.${type}`;

    if (type === 'pipeline') {
      setCurrentStep(t('copilot.step.pipeline.1'));
      await sleep(1000);
      onViewModeChange('integration');

      setCurrentStep(t('copilot.step.pipeline.2'));
      await sleep(1000);
      onIntegrationTabChange('pipeline-builder');

      setCurrentStep(t('copilot.step.pipeline.3'));
      await sleep(1500);
      showToast('info', t('copilot.toast.pipeline.mount'));

      setCurrentStep(t('copilot.step.pipeline.4'));
      await sleep(1500);
      showToast('success', t('copilot.toast.pipeline.done'));

      setAgentResult(agentMsgId, t(resultKey));
    } else if (type === 'ontology') {
      setCurrentStep(t('copilot.step.ontology.1'));
      await sleep(1000);
      onViewModeChange('ontology');
      onSelectCategory('overview', null);

      setCurrentStep(t('copilot.step.ontology.2'));
      await sleep(1200);

      setCurrentStep(t('copilot.step.ontology.3'));
      await sleep(1500);

      const exists = objectTypes.some((o) => o.id === 'obj_aviation_flight');
      if (!exists) {
        const newObj = {
          id: 'obj_aviation_flight',
          displayName: t('copilot.data.ontology.flight'),
          apiName: 'AviationFlight',
          description: t('copilot.data.ontology.flightDesc'),
          iconName: 'Plane',
          primaryKey: 'flight_id',
          titleProperty: 'flight_num',
          status: 'draft',
          properties: [
            { name: 'flight_id', displayName: t('copilot.data.ontology.prop.id'), type: 'string', isPrimaryKey: true },
            { name: 'flight_num', displayName: t('copilot.data.ontology.prop.num'), type: 'string' },
            { name: 'dep_airport', displayName: t('copilot.data.ontology.prop.dep'), type: 'string' },
            { name: 'arr_airport', displayName: t('copilot.data.ontology.prop.arr'), type: 'string' },
            { name: 'is_delayed', displayName: t('copilot.data.ontology.prop.delay'), type: 'boolean' },
          ],
        };
        setObjectTypes([newObj, ...objectTypes]);

        const newLink = {
          id: 'link_flight_pilot',
          displayName: t('copilot.data.ontology.link'),
          apiName: 'Flight_to_Pilot',
          description: t('copilot.data.ontology.linkDesc'),
          sourceObjectType: 'obj_aviation_flight',
          targetObjectType: 'obj_pilot',
          cardinality: 'MANY_TO_ONE',
          foreignKey: 'pilot_id',
          status: 'draft',
        };
        setLinkTypes([newLink, ...linkTypes]);
      }

      onSelectCategory('object', 'obj_aviation_flight');
      showToast('success', t('copilot.toast.ontology.done'));

      setAgentResult(agentMsgId, t(resultKey));
    } else if (type === 'health') {
      setCurrentStep(t('copilot.step.health.1'));
      await sleep(1000);
      onViewModeChange('integration');

      setCurrentStep(t('copilot.step.health.2'));
      await sleep(1000);
      onIntegrationTabChange('health');

      setCurrentStep(t('copilot.step.health.3'));
      await sleep(1500);
      showToast('info', t('copilot.toast.health.sla'));

      setCurrentStep(t('copilot.step.health.4'));
      await sleep(1500);
      showToast('success', t('copilot.toast.health.done'));

      setAgentResult(agentMsgId, t(resultKey));
    } else if (type === 'lineage') {
      setCurrentStep(t('copilot.step.lineage.1'));
      await sleep(800);
      onViewModeChange('integration');

      setCurrentStep(t('copilot.step.lineage.2'));
      await sleep(1200);
      onIntegrationTabChange('lineage');

      setAgentResult(agentMsgId, t(resultKey));
    } else if (type === 'sec_gdpr') {
      setCurrentStep(t('copilot.step.security.1'));
      await sleep(1000);
      onViewModeChange('security');

      setCurrentStep(t('copilot.step.org.1'));
      await sleep(1200);
      props.onSecurityTabChange('orgs');

      const existsOrg = securityOrgs.some((o) => o.id === 'Org_EU_Ops');
      if (!existsOrg) {
        const newOrg: SecurityOrg = {
          id: 'Org_EU_Ops',
          name: t('copilot.data.org.euName'),
          isolationMode: true,
          memberCount: 120,
          ipRanges: ['10.150.0.0/16', '10.152.0.0/24'],
          crossOrgSharing: [],
          createdAt: new Date().toISOString().split('T')[0],
        };
        setSecurityOrgs([newOrg, ...securityOrgs]);
        showToast('success', t('copilot.toast.sec_gdpr.org'));
      }

      setCurrentStep(t('copilot.step.dac.1'));
      await sleep(1200);
      props.onSecurityTabChange('dac');

      const existsProj = securityProjects.some((p) => p.id === 'proj_passenger_eu');
      if (!existsProj) {
        const newProj: ProjectDAC = {
          id: 'proj_passenger_eu',
          name: t('copilot.data.proj.euName'),
          description: t('copilot.data.proj.euDesc'),
          members: [
            { username: 'admin_guorong', role: 'Owner', grantedBy: 'System', grantedAt: '2026-07-04' },
            { username: 'analyst_li', role: 'Editor', grantedBy: 'admin_guorong', grantedAt: '2026-07-04' },
          ],
          discoverableAllOrgs: false,
          autoPropagation: true,
        };
        setSecurityProjects([newProj, ...securityProjects]);
        showToast('success', t('copilot.toast.sec_gdpr.dac'));
      }

      setCurrentStep(t('copilot.step.mac.1'));
      await sleep(1200);
      props.onSecurityTabChange('mac');

      const existsMarking = securityMarkings.some((m) => m.id === 'M_GDPR_PII');
      if (existsMarking) {
        const updated = securityMarkings.map((m) => m.id === 'M_GDPR_PII'
          ? { ...m, appliedDatasets: Array.from(new Set([...m.appliedDatasets, 'ds_passenger_manifest'])) }
          : m);
        setSecurityMarkings(updated);
        showToast('success', t('copilot.toast.sec_gdpr.mac'));
      }

      setCurrentStep(t('copilot.step.purp.1'));
      await sleep(1200);
      onSecurityTabChange('pbac');

      const existsPurp = securityPurposes.some((p) => p.id === 'purpose_eu_passenger_audit');
      if (!existsPurp) {
        const newPurp: PurposePBAC = {
          id: 'purpose_eu_passenger_audit',
          name: t('copilot.data.purp.euName'),
          description: t('copilot.data.purp.euDesc'),
          authorizedUsers: ['analyst_li', 'eu_dpo_officer'],
          inputDatasets: ['ds_passenger_manifest'],
          redactionRules: ['MASK(customer_name)', 'HASH(passport_no)'],
          expiresAt: '2027-01-01',
          status: 'ACTIVE',
        };
        setSecurityPurposes([newPurp, ...securityPurposes]);
        showToast('success', t('copilot.toast.sec_gdpr.purp'));
      }

      setCurrentStep(t('copilot.step.sim.1'));
      await sleep(1500);
      props.onSecurityTabChange('overview');

      setSecuritySimUser('analyst_li');
      setSecuritySimDataset('ds_passenger_manifest');
      setSecuritySimPurpose('purpose_eu_passenger_audit');
      setSecuritySimResult({
        verdict: 'GRANTED',
        traces: [
          t('copilot.cite.trace.g1'),
          t('copilot.cite.trace.g2'),
          t('copilot.cite.trace.g3'),
          t('copilot.cite.trace.g4'),
          t('copilot.cite.trace.g5'),
        ],
      });

      const newLog: SecurityAuditLog = {
        id: `log_playbook_${Date.now()}`,
        timestamp: new Date().toTimeString().split(' ')[0],
        username: 'analyst_li',
        orgId: 'Org_EU_Ops',
        resourceId: 'ds_passenger_manifest',
        resourceType: 'Dataset',
        action: 'READ_DATASET',
        status: 'SUCCESS',
        details: t('copilot.cite.log.g1'),
      };
      setSecurityAuditLogs([newLog, ...securityAuditLogs]);

      setAgentResult(agentMsgId, t(resultKey));
    } else if (type === 'sec_row_col') {
      setCurrentStep(t('copilot.step.security.1'));
      await sleep(800);
      onViewModeChange('security');

      setCurrentStep(t('copilot.step.row_col.1'));
      await sleep(1000);
      props.onSecurityTabChange('row_col');
      setSecuritySelectedRowColDs('ds_pilots_biography');

      setCurrentStep(t('copilot.step.row_col.2'));
      await sleep(1500);

      const updatedPolicies = securityRowColPolicies.map((p) => p.datasetId === 'ds_pilots_biography'
        ? {
            ...p,
            columnMasks: [
              { column: 'ssn_number', maskType: 'REDACT' as const, active: true },
              { column: 'email_address', maskType: 'PARTIAL' as const, active: true },
              { column: 'base_salary', maskType: 'REDACT' as const, active: true },
            ],
            rowFilters: [
              { filterSql: "flight_hours_ytd > 300 OR role = 'HR_DIRECTOR'", description: t('copilot.cite.rowFilter.desc'), active: true },
            ],
          }
        : p);
      setSecurityRowColPolicies(updatedPolicies);

      const newLog: SecurityAuditLog = {
        id: `log_rowcol_${Date.now()}`,
        timestamp: new Date().toTimeString().split(' ')[0],
        username: 'admin_guorong',
        orgId: 'org_aviation_hq',
        resourceId: 'ds_pilots_biography',
        resourceType: 'Dataset',
        action: 'UPDATE_POLICY',
        status: 'SUCCESS',
        details: t('copilot.cite.log.r1'),
      };
      setSecurityAuditLogs([newLog, ...securityAuditLogs]);
      showToast('success', t('copilot.toast.sec_row_col.done'));

      setAgentResult(agentMsgId, t(resultKey));
    } else if (type === 'sec_finance') {
      setCurrentStep(t('copilot.step.security.1'));
      await sleep(800);
      onViewModeChange('security');

      setCurrentStep(t('copilot.step.fin.1'));
      await sleep(1000);
      props.onSecurityTabChange('orgs');

      const existsOrg = securityOrgs.some((o) => o.id === 'Org_Finance_Dept');
      if (!existsOrg) {
        const newOrg: SecurityOrg = {
          id: 'Org_Finance_Dept',
          name: t('copilot.data.org.finName'),
          isolationMode: true,
          memberCount: 45,
          ipRanges: ['10.130.0.0/16', '192.168.10.0/24'],
          crossOrgSharing: ['org_aviation_hq'],
          createdAt: new Date().toISOString().split('T')[0],
        };
        setSecurityOrgs([newOrg, ...securityOrgs]);
        showToast('success', t('copilot.toast.sec_finance.org'));
      }

      setCurrentStep(t('copilot.step.fin.2'));
      await sleep(1200);
      props.onSecurityTabChange('mac');

      const existsMarking = securityMarkings.some((m) => m.id === 'M_SENSITIVE_REVENUE');
      if (existsMarking) {
        const updated = securityMarkings.map((m) => m.id === 'M_SENSITIVE_REVENUE'
          ? { ...m, appliedDatasets: Array.from(new Set([...m.appliedDatasets, 'ds_ticket_sales'])) }
          : m);
        setSecurityMarkings(updated);
        showToast('success', t('copilot.toast.sec_finance.mac'));
      }

      setCurrentStep(t('copilot.step.fin.3'));
      await sleep(1200);
      onSecurityTabChange('pbac');

      setCurrentStep(t('copilot.step.fin.4'));
      await sleep(1500);
      props.onSecurityTabChange('overview');

      setSecuritySimUser('analyst_li');
      setSecuritySimDataset('ds_ticket_sales');
      setSecuritySimPurpose('purpose_expired_finance');
      setSecuritySimResult({
        verdict: 'DENIED',
        traces: [
          t('copilot.cite.trace.f1'),
          t('copilot.cite.trace.f2'),
          t('copilot.cite.trace.f3'),
          t('copilot.cite.trace.f4'),
        ],
      });

      const logDenied: SecurityAuditLog = {
        id: `log_denied_${Date.now()}`,
        timestamp: new Date().toTimeString().split(' ')[0],
        username: 'analyst_li',
        orgId: 'org_aviation_hq',
        resourceId: 'ds_ticket_sales',
        resourceType: 'Dataset',
        action: 'READ_DATASET',
        status: 'DENIED',
        details: t('copilot.cite.log.d1'),
      };
      setSecurityAuditLogs([logDenied, ...securityAuditLogs]);
      showToast('error', t('copilot.toast.sec_finance.blocked'));

      setAgentResult(agentMsgId, t(resultKey));
    } else if (type === 'sec_audit') {
      setCurrentStep(t('copilot.step.security.1'));
      await sleep(800);
      onViewModeChange('security');

      setCurrentStep(t('copilot.step.audit.1'));
      await sleep(1500);
      props.onSecurityTabChange('audit');

      const alertLogs: SecurityAuditLog[] = [
        {
          id: `alert_01_${Date.now()}`,
          timestamp: new Date().toTimeString().split(' ')[0],
          username: 'unknown_scanner',
          orgId: 'EXTERNAL_IP',
          resourceId: 'ds_special_routes',
          resourceType: 'Dataset',
          action: 'READ_DATASET',
          status: 'DENIED',
          details: t('copilot.cite.alert.1'),
        },
        {
          id: `alert_02_${Date.now()}`,
          timestamp: new Date().toTimeString().split(' ')[0],
          username: 'external_auditor',
          orgId: 'org_contractor',
          resourceId: 'proj_pilot_credentials',
          resourceType: 'Project',
          action: 'DISCOVER_PROJECT',
          status: 'DENIED',
          details: t('copilot.cite.alert.2'),
        },
      ];
      setSecurityAuditLogs([...alertLogs, ...securityAuditLogs]);
      showToast('error', t('copilot.toast.sec_audit.found'));

      setAgentResult(agentMsgId, t(resultKey));
    } else if (type === 'ws_generate_dashboard' || type === 'ws_auto_bind' || type === 'ws_inject_copilot' || type === 'ws_transform_theme') {
      // All workshop automation scenarios share: switch view, dispatch event, show toast, finalize.
      setCurrentStep(t('copilot.step.workshop.1'));
      await sleep(1000);
      onViewModeChange('workshop');
      if (type === 'ws_generate_dashboard') setCurrentStep(t('copilot.step.ws_gen.2'));
      else if (type === 'ws_auto_bind') setCurrentStep(t('copilot.step.ws_bind.2'));
      else if (type === 'ws_inject_copilot') setCurrentStep(t('copilot.step.ws_inject.2'));
      else setCurrentStep(t('copilot.step.ws_theme.2'));
      await sleep(1200);

      if (type === 'ws_generate_dashboard') setCurrentStep(t('copilot.step.ws_gen.3'));
      else if (type === 'ws_auto_bind') setCurrentStep(t('copilot.step.ws_bind.3'));
      else if (type === 'ws_inject_copilot') setCurrentStep(t('copilot.step.ws_inject.3'));
      else setCurrentStep(t('copilot.step.ws_theme.3'));
      window.dispatchEvent(new CustomEvent('aip-workshop-command', { detail: { action: type } }));
      await sleep(1800);

      if (type === 'ws_generate_dashboard') {
        showToast('success', t('copilot.toast.ws_gen.done'));
      } else if (type === 'ws_auto_bind') {
        showToast('success', t('copilot.toast.ws_bind.done'));
      } else if (type === 'ws_inject_copilot') {
        showToast('success', t('copilot.toast.ws_inject.done'));
      } else {
        showToast('info', t('copilot.toast.ws_theme.done'));
      }

      setAgentResult(agentMsgId, t(resultKey));
    } else {
      // Fallback path for unknown future scenario types
      setIsTyping(false);
    }
  }, [t, viewMode, onViewModeChange, onSelectCategory, onIntegrationTabChange, onSecurityTabChange, securityTab,
      showToast,
      objectTypes, setObjectTypes, linkTypes, setLinkTypes,
      securityOrgs, setSecurityOrgs, securityProjects, setSecurityProjects,
      securityMarkings, setSecurityMarkings, securityPurposes, setSecurityPurposes,
      securityRowColPolicies, setSecurityRowColPolicies,
      securityAuditLogs, setSecurityAuditLogs,
      securitySimUser, setSecuritySimUser,
      securitySimDataset, setSecuritySimDataset,
      securitySimPurpose, setSecuritySimPurpose,
      securitySimResult, setSecuritySimResult,
      setAgentResult,
      // Pass through all props to keep callbacks closed over latest values.
      setSecuritySelectedRowColDs, securitySelectedRowColDs]);

  return { messages, isTyping, currentStep, setMessages, runAgentAutomation };
}
