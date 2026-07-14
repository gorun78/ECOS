/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { DataAsset, LineageNodeConfig, LineageLink, PipelineNode, EntityDefinition, EntityInstance, WorkflowDefinition, ToolDefinition, PromptTemplate, AgentDefinition, KnowledgeNode, KnowledgeEdge, Goal, CausalLink, Scenario, AuditEvent, SecurityPolicy } from "./types";

export const MOCK_DATA_ASSETS: DataAsset[] = [
  {
    id: "ds_customer360",
    name: "Customer360",
    description: "Curated 360-degree customer profile incorporating marketing tags, transactional totals, support history, and regional segments.",
    type: "dataset",
    owner: "Growth Analytics",
    domain: "CRM",
    tags: ["customer", "gold", "production", "analytics"],
    status: "Healthy",
    qualityScore: 98,
    rows: 12548992,
    columns: 6,
    storageSize: "4.2 GB",
    updatedAt: "5 min ago",
    schema: [
      { name: "customer_id", type: "string", nullable: false, description: "Unique identifier for corporate/retail customers", primaryKey: true, qualityScore: 100 },
      { name: "email", type: "string", nullable: false, description: "Primary contact email address", qualityScore: 99 },
      { name: "region", type: "string", nullable: true, description: "Geographic sales region (APAC, EMEA, LATAM, NA)", qualityScore: 98 },
      { name: "revenue", type: "double", nullable: false, description: "Accrued customer lifetime revenue in USD", qualityScore: 97 },
      { name: "churn_risk", type: "double", nullable: false, description: "Calculated ML score for customer churn risk (0-1.0)", qualityScore: 96 },
      { name: "last_active", type: "date", nullable: true, description: "Most recent user action timestamp", qualityScore: 98 }
    ],
    qualityRules: [
      { id: "qr_1", name: "Null Check", type: "null_check", status: "pass", description: "Ensures primaryKey (customer_id) has zero null values." },
      { id: "qr_2", name: "Duplicate Check", type: "duplicate_check", status: "pass", description: "Ensures no double entries on customer_id." },
      { id: "qr_3", name: "Reference Integrity", type: "reference_integrity", status: "pass", description: "Verifies matches in Sales Orders dataset (100% matched)." },
      { id: "qr_4", name: "Outlier Detection", type: "outlier_detection", status: "warn", description: "Flags daily orders that are 4x standard deviation." }
    ],
    history: [
      { id: "h_1", version: "v2.4", action: "Published", timestamp: "5 min ago", actor: "Data-Coordinator-Agent", summary: "Automated regression run validated schema structure and published updated records." },
      { id: "h_2", version: "v2.3", action: "Schema Changed", timestamp: "1 hour ago", actor: "Research-Agent", summary: "Added column churn_risk calculated by the customer feedback model." },
      { id: "h_3", version: "v2.2", action: "Modified", timestamp: "1 day ago", actor: "System Admin", summary: "Refactored regional clustering index to speed up analytical joins." },
      { id: "h_4", version: "v1.0", action: "Imported", timestamp: "5 days ago", actor: "SAP-Pipeline", summary: "Initial database dump import from SAP ERP." }
    ],
    permissions: {
      owner: ["Growth Team", "Admin"],
      editor: ["Operations", "Analyst"],
      viewer: ["Finance", "Default-Users"]
    }
  },
  {
    id: "ds_plantops",
    name: "PlantOps",
    description: "Real-time manufacturing plant telemetry tracking machine status, throughput, heat, efficiency, and pending logs.",
    type: "ontology",
    owner: "Operations Engineering",
    domain: "IoT-Operations",
    tags: ["plant", "sensors", "raw", "telemetry"],
    status: "Warning",
    qualityScore: 84,
    rows: 45780112,
    columns: 5,
    storageSize: "18.6 GB",
    updatedAt: "2 min ago",
    schema: [
      { name: "machine_id", type: "string", nullable: false, description: "Unique barcode ID for factory equipment", primaryKey: true, qualityScore: 100 },
      { name: "throughput_rate", type: "number", nullable: false, description: "Units processed per minute", qualityScore: 92 },
      { name: "temperature_c", type: "double", nullable: false, description: "Internal motor core temperature in Celsius", qualityScore: 88 },
      { name: "has_fault", type: "boolean", nullable: false, description: "Active error state binary switch", qualityScore: 99 },
      { name: "uptime_hours", type: "double", nullable: true, description: "Continuous operating time since last service", qualityScore: 90 }
    ],
    qualityRules: [
      { id: "qr_o1", name: "Null Check", type: "null_check", status: "pass", description: "machine_id is fully populated." },
      { id: "qr_o2", name: "Outlier Detection", type: "outlier_detection", status: "fail", description: "Temperature spiked past critical 120°C limits on 2 devices." },
      { id: "qr_o3", name: "Reference Integrity", type: "reference_integrity", status: "pass", description: "Equipment codes catalog matched successfully." }
    ],
    history: [
      { id: "h_o1", version: "v12.2", action: "Published", timestamp: "2 min ago", actor: "IoT-Bus", summary: "Automated stream ingest published 1,200 new metrics packets." },
      { id: "h_o2", version: "v12.1", action: "Modified", timestamp: "3 hours ago", actor: "Operations Engineer", summary: "Adjusted standard-deviation thresholds for thermal sensor alarms." }
    ],
    permissions: {
      owner: ["Engineering Team", "Admin"],
      editor: ["Operations"],
      viewer: ["Growth Team", "Default-Users"]
    }
  },
  {
    id: "ds_order_analytics",
    name: "OrderAnalytics",
    description: "Cleaned transactional log for financial auditing and predictive modeling of global supply contracts.",
    type: "dataset",
    owner: "Finance operations",
    domain: "Finance",
    tags: ["orders", "billing", "reconciliation"],
    status: "Healthy",
    qualityScore: 99,
    rows: 569300,
    columns: 5,
    storageSize: "850 MB",
    updatedAt: "10 min ago",
    schema: [
      { name: "order_id", type: "string", nullable: false, description: "System transaction voucher identifier", primaryKey: true, qualityScore: 100 },
      { name: "customer_id", type: "string", nullable: false, description: "Billing partner identifier", qualityScore: 100 },
      { name: "amount", type: "double", nullable: false, description: "Gross invoice billing total", qualityScore: 99 },
      { name: "order_status", type: "string", nullable: false, description: "Voucher condition: Pending, Fulfilled, Returned, Closed", qualityScore: 100 },
      { name: "transaction_date", type: "date", nullable: false, description: "Official sales ledger stamp", qualityScore: 99 }
    ],
    qualityRules: [
      { id: "qr_f1", name: "Null Check", type: "null_check", status: "pass", description: "order_id has no empty listings." },
      { id: "qr_f2", name: "Duplicate Check", type: "duplicate_check", status: "pass", description: "Lead tracking checks run validated unique indexes." },
      { id: "qr_f3", name: "Reference Integrity", type: "reference_integrity", status: "pass", description: "All customer IDs linked with active accounts." }
    ],
    history: [],
    permissions: {
      owner: ["Finance Team", "Admin"],
      editor: ["Finance Team"],
      viewer: ["Growth Team"]
    }
  }
];

// Phase 1-3 & Page 2 Lineage elements
export const INITIAL_LINEAGE_NODES: LineageNodeConfig[] = [
  { id: "source_sap", type: "source", label: "SAP ERP System", status: "Healthy", owner: "SAP Team", rows: "Database Connection" },
  { id: "source_crm", type: "source", label: "CRM Pipeline (Salesforce)", status: "Healthy", owner: "Sales Ops", rows: "Webhooks Feed" },
  { id: "raw_customer", type: "dataset", label: "Raw Customer Ingest", status: "Healthy", owner: "Engineering", rows: "12.8M rows" },
  { id: "raw_transactions", type: "dataset", label: "Raw Transaction Logs", status: "Healthy", owner: "Engineering", rows: "4.5M rows" },
  { id: "pipeline_transform", type: "pipeline", label: "Enrichment & De-dup Pipeline", status: "Running", owner: "Data-Coordinator-Agent", rows: "Vite Spark Job" },
  { id: "customer_360", type: "dataset", label: "Customer360 (Curated)", status: "Healthy", owner: "Growth Analytics", rows: "12.5M rows" },
  { id: "operations_dashboard", type: "dashboard", label: "Operations Executive Dashboard", status: "Healthy", owner: "Product Team" },
  { id: "churn_application", type: "application", label: "Pred Churn Operational App", status: "Warning", owner: "Operations Team" }
];

export const INITIAL_LINEAGE_LINKS: LineageLink[] = [
  { id: "l1", source: "source_sap", target: "raw_customer" },
  { id: "l2", source: "source_crm", target: "raw_customer" },
  { id: "l3", source: "source_sap", target: "raw_transactions" },
  { id: "l4", source: "raw_customer", target: "pipeline_transform" },
  { id: "l5", source: "raw_transactions", target: "pipeline_transform" },
  { id: "l6", source: "pipeline_transform", target: "customer_360" },
  { id: "l7", source: "customer_360", target: "operations_dashboard" },
  { id: "l8", source: "customer_360", target: "churn_application" }
];

// Phase 4-6 Pipeline builder state nodes and connection lines
export const INITIAL_PIPELINE_NODES: PipelineNode[] = [
  { id: "node_raw", type: "dataset", label: "Raw Transactions", status: "Success", config: { targetName: "raw_transactions" }, x: 80, y: 150 },
  { id: "node_clean", type: "transform", label: "Transaction Deduplicator", status: "Success", config: { operationType: "Spark Deduplicate", sqlSnippet: "SELECT DISTINCT * FROM raw_transactions WHERE transaction_date > '2026-01-01'" }, x: 300, y: 150 },
  { id: "node_aggregate", type: "aggregate", label: "Revenue Aggregator", status: "Running", config: { operationType: "Group By & Rollup", sqlSnippet: "SELECT customer_id, SUM(amount) as revenue FROM cleaned_tx GROUP BY customer_id" }, x: 520, y: 150 },
  { id: "node_publish", type: "publish", label: "Publish Production gold", status: "Queued", config: { targetName: "Customer360", params: { indexes: "customer_id", encryption: "AES-256" } }, x: 740, y: 150 }
];

// Phase 7-9 Ontology Elements
export const MOCK_ONTOLOGY_ENTITIES: EntityDefinition[] = [
  {
    id: "Customer",
    name: "Customer Entity",
    description: "Core organizational representation of enterprise patrons who purchase equipment or services.",
    properties: [
      { name: "customerId", type: "string", required: true, searchable: true, editable: false },
      { name: "name", type: "string", required: true, searchable: true, editable: true },
      { name: "revenue", type: "number", required: true, editable: true },
      { name: "region", type: "enum", options: ["NA", "EMEA", "APAC", "LATAM"], editable: true },
      { name: "isActive", type: "boolean", editable: true }
    ],
    relationships: [
      { name: "orders", targetEntity: "Order", cardinality: "many" },
      { name: "supportCases", targetEntity: "Case", cardinality: "many" }
    ],
    actions: [
      {
        id: "approve_credit",
        label: "Approve Enterprise Credit",
        description: "Evaluates financial health and extends corporate spending limit rules.",
        impactLevel: "medium",
        fields: [
          { name: "creditLimit", label: "Spending Credit Limit ($)", type: "number", required: true, defaultValue: 50000 },
          { name: "managerNotes", label: "Manager Reason", type: "string", required: false }
        ]
      },
      {
        id: "suspend_account",
        label: "Suspend Customer Account",
        description: "Temporarily locks customer portal and pauses all pipeline orders due to legal, payment, or breach concerns.",
        impactLevel: "high",
        fields: [
          { name: "suspensionReason", label: "Reason for Hold", type: "string", required: true },
          { name: "pauseActiveOrders", label: "Pause Pending Shipments", type: "boolean", required: true, defaultValue: true }
        ]
      }
    ]
  },
  {
    id: "Order",
    name: "Sales Order",
    description: "Active business transaction recording purchased assets or software licenses.",
    properties: [
      { name: "orderId", type: "string", required: true },
      { name: "customerId", type: "string", required: true },
      { name: "amount", type: "number", required: true },
      { name: "orderDate", type: "date" },
      { name: "status", type: "enum", options: ["Pending", "Processing", "Delivered", "Cancelled"] }
    ],
    relationships: [
      { name: "customer", targetEntity: "Customer", cardinality: "one" },
      { name: "facilityOrigin", targetEntity: "Facility", cardinality: "one" }
    ],
    actions: [
      {
        id: "ship_order",
        label: "Ship Physical Asset",
        description: "Initiates supply chain distribution steps.",
        impactLevel: "low",
        fields: [
          { name: "carrier", label: "Logistics Carrier", type: "string", required: true, defaultValue: "FedEx Enterprise" },
          { name: "trackingNumber", label: "Tracking Number ID", type: "string", required: true }
        ]
      }
    ]
  },
  {
    id: "Facility",
    name: "Industrial Facility",
    description: "Manufacturing plant or distribution hub containing machinery, workforce units, and raw components.",
    properties: [
      { name: "facilityId", type: "string", required: true },
      { name: "name", type: "string", required: true },
      { name: "capacity", type: "number", required: true },
      { name: "region", type: "string" },
      { name: "operationalStatus", type: "enum", options: ["Active", "Degraded", "MaintenanceShutdown"] }
    ],
    relationships: [
      { name: "machinery", targetEntity: "Asset", cardinality: "many" },
      { name: "openCases", targetEntity: "Case", cardinality: "many" }
    ],
    actions: [
      {
        id: "trigger_maintenance",
        label: "Trigger Emergency Maintenance",
        description: "Signals urgent industrial shutoffs and reroutes pending workflows.",
        impactLevel: "high",
        fields: [
          { name: "facilityReason", label: "Emergency Breakdown Details", type: "string", required: true },
          { name: "reRouteCargo", label: "Reroute Supply Shipments", type: "boolean", required: true, defaultValue: true }
        ]
      }
    ]
  }
];

export const MOCK_ENTITY_INSTANCES: Record<string, EntityInstance[]> = {
  Customer: [
    { entityType: "Customer", id: "customer_123", properties: { customerId: "customer_123", name: "Glencore Global Corp", revenue: 890450000, region: "APAC", isActive: true } },
    { entityType: "Customer", id: "customer_456", properties: { customerId: "customer_456", name: "Trafigura Trading Ltd", revenue: 1205000000, region: "NA", isActive: true } },
    { entityType: "Customer", id: "customer_789", properties: { customerId: "customer_789", name: "Rio Tinto Mining Inc", revenue: 642000000, region: "EMEA", isActive: false } }
  ],
  Order: [
    { entityType: "Order", id: "ord_1001", properties: { orderId: "ord_1001", customerId: "customer_123", amount: 450000, status: "Delivered" } },
    { entityType: "Order", id: "ord_1002", properties: { orderId: "ord_1002", customerId: "customer_123", amount: 620000, status: "Processing" } },
    { entityType: "Order", id: "ord_1003", properties: { orderId: "ord_1003", customerId: "customer_456", amount: 1500000, status: "Pending" } }
  ],
  Facility: [
    { entityType: "Facility", id: "fac_alpha", properties: { facilityId: "fac_alpha", name: "Factory Alpha (Perth)", capacity: 450, region: "APAC", operationalStatus: "Active" } },
    { entityType: "Facility", id: "fac_beta", properties: { facilityId: "fac_beta", name: "Factory Beta (Houston)", capacity: 220, region: "NA", operationalStatus: "Active" } },
    { entityType: "Facility", id: "fac_gamma", properties: { facilityId: "fac_gamma", name: "Production Unit Gamma", capacity: 850, region: "EMEA", operationalStatus: "Degraded" } }
  ]
};

// Phase 10-12 Workflow definition
export const MOCK_WORKFLOWS: WorkflowDefinition[] = [
  {
    id: "wf_credit_approval",
    name: "Approve Enterprise Credit Run",
    trigger: "approve_credit",
    status: "active",
    actions: [
      { id: "a_1", type: "updateStatus", name: "Adjust Client Tier Level", config: { updateTo: "Gold_Sponsor" } },
      { id: "a_2", type: "createAuditLog", name: "Record Audit Log Cryptographic Index", config: { message: "Approved Credit limits raised dynamically via coordinator consensus." } },
      { id: "a_3", type: "notifyOwner", name: "E-mail Accounts representative", config: { template: "credit_increase_alert" } }
    ]
  },
  {
    id: "wf_emergency_shutdown",
    name: "Emergency Facility Rerouting",
    trigger: "trigger_maintenance",
    status: "active",
    actions: [
      { id: "a_4", type: "updateStatus", name: "Set Status to MaintenanceShutdown", config: { updateTo: "MaintenanceShutdown" } },
      { id: "a_5", type: "triggerExternalApi", name: "Alert IoT Factory Core Grid Control Boss", config: { endpoint: "https://api.grid.factory/shutdown" } },
      { id: "a_6", type: "createAuditLog", name: "Log Shutdown Event for Regulatory Audit", config: { level: "CRITICAL" } }
    ]
  }
];

// Phase 13-14 Agent Studio
export const MOCK_TOOLS: ToolDefinition[] = [
  {
    id: "SearchCustomer",
    name: "SearchCustomer",
    description: "Search corporate client data matching specified fields, regional location, or revenue margins.",
    parameters: [
      { name: "searchTerm", type: "string", description: "Fuzzy company name or ID string", required: true },
      { name: "region", type: "string", description: "Limit search to standard regions (APAC, EMEA, etc.)", required: false }
    ]
  },
  {
    id: "QueryOrders",
    name: "QueryOrders",
    description: "Fetches lists of order records from transactional schemas linked to a unique client ID.",
    parameters: [
      { name: "customerId", type: "string", description: "Target client database ID", required: true },
      { name: "status", type: "string", description: "Filter results by status (Pending, processing)", required: false }
    ]
  },
  {
    id: "CreateCase",
    name: "CreateCase",
    description: "Spins up analytical Case management files inside the central workspace with alerts and tasks.",
    parameters: [
      { name: "title", type: "string", description: "Short descriptive name for the core case file", required: true },
      { name: "customerId", type: "string", description: "Client reference ID associated with issues", required: true }
    ]
  }
];

export const MOCK_PROMPTS: PromptTemplate[] = [
  {
    id: "investigation",
    title: "Fraud & Outlier Investigation Blueprint",
    filename: "prompts/investigation.md",
    content: `# Forensic Transaction Investigation Prompt template
System Role: Financial Analyst Bot
Context: Reviewing transaction outliers flagged in the database systems.
Goal: Run analytical checks across linked Customer nodes, cross-analyze order histories, identify unusual deviations, and generate a case profile.
Steps:
1. Call SearchCustomer with matching name to fetch core profile tags.
2. Call QueryOrders to inspect previous 12 months billing.
3. Compare standard deviations of payments relative to peers.
Version: v1.4 (Active)`,
    category: "investigation",
    version: "v1.4"
  },
  {
    id: "planner",
    title: "Multi-Agent Planning Grid System",
    filename: "prompts/planner.md",
    content: `# Planner Agent System Instruction
You are the Cognitive Coordinator Agent. Given a corporate performance plan or diagnostic goal, decompose the goal into high-level milestones:
1. Discover relevant nodes and relationships via the Semantic Graph.
2. Allocate specific sub-tasks to Specialist Agents (Research, Analyst, Workflow).
3. Consolidate results, outline causal reasoning loops, and present scenarios for human review.`,
    category: "planning",
    version: "v2.1"
  }
];

export const MOCK_AGENTS: AgentDefinition[] = [
  {
    id: "coordinator",
    name: "Coordinator Agent",
    role: "AIP Coordinator & Orchestrator",
    goal: "Assign corporate goals into structured plans and coordinate expert Specialist Agents.",
    tools: ["SearchCustomer", "CreateCase"],
    systemPrompt: "You are the Coordinator Agent. Plan execution loops, delegates subtasks, and aggregate outcomes.",
    capabilities: ["Planning", "Delegate Subtasks", "Aggregation"]
  },
  {
    id: "analyst",
    name: "Analyst Agent",
    role: "Technical Data & Forensic Analyst",
    goal: "Examine analytical schemas, calculate outlier thresholds, and detect reference integrity flags.",
    tools: ["QueryOrders"],
    systemPrompt: "You are the Analyst Agent. Focus on statistical analysis, data aggregations, and mathematical validation.",
    capabilities: ["Numerical Validation", "Anomaly Detection", "Chart Generation"]
  },
  {
    id: "researcher",
    name: "Research Agent",
    role: "Semantic Knowledge & Ontology Crawler",
    goal: "Inspect Ontology schemas to find rich relationships, tags, and cross-object connections.",
    tools: ["SearchCustomer"],
    systemPrompt: "You are the Research Agent. Crawl the Semantic graphs and retrieve connected entity facts.",
    capabilities: ["Ontology Graph Ingestion", "Fuzzy Matching", "Tag Extraction"]
  },
  {
    id: "workflow",
    name: "Workflow Agent",
    role: "Automation Rules executioner",
    goal: "Execute automated actions, verify security constraints, and log outcomes in cryptographic audit logs.",
    tools: ["CreateCase"],
    systemPrompt: "You are the Workflow Agent. Perform system validations, write logs, and trigger workflows for review.",
    capabilities: ["API Integrations", "Security Compliance", "Transactional Locking"]
  }
];

// Phase 15 - Enterprise Cognitive Operating System (C2EOS)
export const MOCK_KNOWLEDGE_NODES: KnowledgeNode[] = [
  // Goals
  { id: "ekg_goal_revenue", type: "Goals", label: "Increase Annual Revenue Goal (+15%)", properties: { target: "revenue > 1B", priority: "CRITICAL" } },
  { id: "ekg_goal_uptime", type: "Goals", label: "Reduce Machine Downtime <2%", properties: { target: "downtime_rate < 0.02" } },
  
  // Metrics
  { id: "ekg_metric_rev", type: "Metrics", label: "Gross Revenue Current Index", properties: { current: "$890M", gap: "-$110M" } },
  { id: "ekg_metric_availability", type: "Metrics", label: "Core Factory Availability Rate", properties: { current: "94%", normal: "98%" } },

  // Risks
  { id: "ekg_risk_thermal", type: "Risks", label: "Thermal Sensor spike alarm", properties: { severity: "HIGH", count: "3 machines" } },
  { id: "ekg_risk_reroute", type: "Risks", label: "Supply Chain Bottlenecks NA", properties: { probability: "MED" } },

  // Processes & Policies
  { id: "ekg_proc_maintenance", type: "Processes", label: "Emergency Breakdown Plan", properties: { trigger: "Temp > 120C", state: "Active" } },
  { id: "ekg_policy_safety", type: "Policies", label: "Regulatory Workplace Safety Act", properties: { compliant: "Yes" } },

  // Assets
  { id: "ekg_asset_alpha_factory", type: "Assets", label: "Factory Alpha (Perth Plant)", properties: { output: "420 units/hr", temperature: "105 C" } }
];

export const MOCK_KNOWLEDGE_EDGES: KnowledgeEdge[] = [
  { id: "ke1", source: "ekg_metric_rev", target: "ekg_goal_revenue", relationship: "tracks_status_of" },
  { id: "ke2", source: "ekg_metric_availability", target: "ekg_goal_uptime", relationship: "verifies_progress" },
  { id: "ke3", source: "ekg_risk_thermal", target: "ekg_proc_maintenance", relationship: "triggers" },
  { id: "ke4", source: "ekg_proc_maintenance", target: "ekg_metric_availability", relationship: "directly_impacts" },
  { id: "ke5", source: "ekg_asset_alpha_factory", target: "ekg_risk_thermal", relationship: "hosts_faulty_sensors_on" },
  { id: "ke6", source: "ekg_policy_safety", target: "ekg_proc_maintenance", relationship: "regulates" }
];

export const MOCK_GOALS: Goal[] = [
  { id: "g_1", name: "Maximize Corporate Client Portfolio Value", metrics: ["Gross Revenue", "Average Contract Margin"], target: "revenue > $1.1B", status: "at_risk", currentValue: "$890M", targetValue: "$1.1B" },
  { id: "g_2", name: "Minimize Factory Alpha Maintenance Delays", metrics: ["Uptime Hours", "Mean Time to Repair (MTTR)"], target: "Downtime Rate < 2.0%", status: "behind", currentValue: "6.0% Downtime", targetValue: "< 2.0%" },
  { id: "g_3", name: "Guarantee Environmental/Workplace Compliance", metrics: ["Workplace Safety Incidents"], target: "0 incidents", status: "on_track", currentValue: "0 Incidents", targetValue: "0 Incidents" }
];

export const MOCK_CAUSAL_LINKS: CausalLink[] = [
  { id: "c_1", cause: "Maintenance Delay Escalating", effect: "Machine Failure Rate Spikes", weight: 0.85, type: "positive", description: "Delayed scheduled maintenance causes physical parts degradation, leading directly to unplanned motorized shutoffs." },
  { id: "c_2", cause: "Machine Failure Rate Spikes", effect: "Daily Production Outflow Drop", weight: -0.92, type: "negative", description: "Sudden motor locks pause active conveyor assembly loops. Production counts slide rapidly." },
  { id: "c_3", cause: "Daily Production Outflow Drop", effect: "Sales Contract Fulfillments fail", weight: 0.78, type: "positive", description: "Missing product stock directly drags on sales pipeline deliveries to logistics hubs." },
  { id: "c_4", cause: "Sales Contract Fulfillments fail", effect: "Quarterly Revenue Slides", weight: -0.88, type: "negative", description: "Failed deliveries invoke contractual rebate penalties, causing client cancellations and revenue contraction." }
];

export const MOCK_SCENARIOS: Scenario[] = [
  {
    id: "scen_reroute",
    name: "Reroute Australian Minerals to Factory Beta",
    description: "Reroute incoming raw APAC mining feedstocks to North America (Houston Plant) to offset degradation delays in Factory Alpha.",
    assumptions: [
      { target: "Factory Alpha", action: "Pause Input Ingest", value: "100%" },
      { target: "Factory Beta", action: "Boost Assembly Shifts", value: "3 extra shifts/week" }
    ],
    outcomes: [
      { metric: "Aggregate Supply Throughput", change: "+12.4%", value: "95% of schedule met", impact: "positive" },
      { metric: "Logistics Costs Accrued", change: "+$45,000", value: "+8.5% Shipping costs", impact: "negative" },
      { metric: "Operational Danger Severity", change: "-80%", value: "Zero overheating alerts", impact: "positive" }
    ],
    risksAdded: ["NA Carbon Cap Limit warnings", "Worker union overtime regulations NA"]
  },
  {
    id: "scen_overhaul",
    name: "Perform Factory Alpha Weekend Motor Overhaul",
    description: "Halt Perth conveyor loops for 36 continuous hours on Saturday/Sunday. Bring in expert engineering contract agents to replace core hydraulic drives.",
    assumptions: [
      { target: "Factory Alpha", action: "Complete Planned Outage", value: "36 hrs" },
      { target: "Operations Budget", action: "Disburse Emergency CapEx", value: "$120,000" }
    ],
    outcomes: [
      { metric: "Mean Time Between Failures (MTBF)", change: "+350%", value: "Extends from 40 to 180 days", impact: "positive" },
      { metric: "CapEx Allocation impact", change: "-$120,000", value: "Within Q2 emergency reserves", impact: "neutral" },
      { metric: "Immediate Contract Fulfillments", change: "-2.4%", value: "Fully caught up by Tuesday", impact: "positive" }
    ],
    risksAdded: ["CapEx reserve depletion for Q3", "Contractor safety compliance tracking audits"]
  }
];

export const MOCK_SECURITY_POLICIES: SecurityPolicy[] = [
  { id: "p1", role: "Field Analyst", objectType: "Customer", permission: "read", condition: "Region = APAC (Read-Only)" },
  { id: "p2", role: "Growth Analyst", objectType: "Customer", permission: "write", condition: "All APAC & NA (Editable)" },
  { id: "p3", role: "Operations Lead", objectType: "Facility", permission: "admin", condition: "All (Global Permissions)" }
];

export const MOCK_AUDIT_LOG: AuditEvent[] = [
  { eventId: "aud_909", timestamp: "Just now", userId: "Coordinator-Agent", eventType: "DATA_ACCESS", resource: "Case Management", action: "Resolve Case File", result: "SUCCESS", details: { caseId: "Case #12345", summary: "Closed case following successful automated motor replacement in Perth." } },
  { eventId: "aud_808", timestamp: "5 min ago", userId: "Analyst-Agent", eventType: "DATA_ACCESS", resource: "Dataset", action: "Run Outlier Predictor", result: "SUCCESS", details: { dataset: "Customer360", summary: "Calculated 3 anomalies across APAC shipping schedules." } },
  { eventId: "aud_707", timestamp: "15 min ago", userId: "Operations-Lead-Human", eventType: "PERMISSION", resource: "Customer", action: "Authorize Credit limit Increase", result: "FAILURE", details: { customerId: "customer_123", summary: "Raised Glencore Global credit limit to $200,000 USD." } },
  { eventId: "aud_606", timestamp: "1 hour ago", userId: "Workflow-Agent", eventType: "CONFIG_CHANGE", resource: "Facility", action: "Activate Grid Cutoff Switch", result: "SUCCESS", details: { facilityId: "fac_gamma", summary: "Pausing high loads due to critical core motor thermal spike indicators." } }
];

// === WorkshopView mock data (from ceos_new) ===
import type { ObjectType, ActionType, Dataset, LinkType } from './types';

export const _wsMockObjectTypes: ObjectType[] = [
  { id: 'flight', apiName: 'Flight', displayName: '航班', description: '航班实体', icon: 'Plane', domainId: 'aviation', properties: [] },
  { id: 'aircraft', apiName: 'Aircraft', displayName: '飞机', description: '飞机实体', icon: 'Plane', domainId: 'aviation', properties: [] },
  { id: 'pilot', apiName: 'Pilot', displayName: '飞行员', description: '飞行员实体', icon: 'User', domainId: 'aviation', properties: [] },
  { id: 'passenger', apiName: 'Passenger', displayName: '旅客', description: '旅客实体', icon: 'Users', domainId: 'aviation', properties: [] },
];

export const _wsMockActionTypes: ActionType[] = [
  { id: 'delay-flight', apiName: 'delayFlight', displayName: '延误航班', description: '标记航班延误', icon: 'Clock', objectTypeId: 'flight' },
  { id: 'cancel-flight', apiName: 'cancelFlight', displayName: '取消航班', description: '取消航班', icon: 'XCircle', objectTypeId: 'flight' },
  { id: 'assign-pilot', apiName: 'assignPilot', displayName: '分配飞行员', description: '为航班分配飞行员', icon: 'UserPlus', objectTypeId: 'flight' },
];

export const _wsMockDatasets: Dataset[] = [
  { id: 'ds_flights', name: '航班数据', description: '航班记录数据集', columns: [{name:'flight_id',type:'string'},{name:'status',type:'string'}] },
  { id: 'ds_aircrafts', name: '飞机数据', description: '飞机信息数据集', columns: [{name:'aircraft_id',type:'string'},{name:'model',type:'string'}] },
  { id: 'ds_pilots', name: '飞行员数据', description: '飞行员信息数据集', columns: [{name:'pilot_id',type:'string'},{name:'name',type:'string'}] },
];

export const _wsMockLinkTypes: LinkType[] = [
  { id: 'flight-aircraft', apiName: 'FlightAircraft', displayName: '航班-飞机', sourceObjectId: 'flight', targetObjectId: 'aircraft' },
  { id: 'flight-pilot', apiName: 'FlightPilot', displayName: '航班-飞行员', sourceObjectId: 'flight', targetObjectId: 'pilot' },
];
