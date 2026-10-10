package com.chinacreator.gzcm.engine.ai.capability.web;

/**
 * Model & Capability Center read-endpoint path single-source (Supplementary Design v1.0, §13).
 * Three /api/v1/ai/capabilities endpoints: getCapabilities / executeCapability / submitCapabilityTask.
 *
 * <p>Prefix policy: canonical BASE_PREFIX = /api/v1/ai (no rewrite needed; no legacy
 * /api/ai Controller exists in the codebase, same as wagent /api/v1/wagent KEEP semantics —
 * VersionPrefixRewriteFilter has no forward entry for this prefix, direct map to canonical path);
 * ClearanceInterceptor uses existing PATH_RULES /api/v1/ => L1 fallback, JWT auth surface,
 * <b>not</b> in AnonymousEndpointRegistry anonymous allow-list.</p>
 */
public final class AiCapabilityApiPaths {

    private AiCapabilityApiPaths() {}

    // ── Path param names single-source (must be declared before use) ──
    public static final String P_ID = "id";
    public static final String P_TASK_ID = "taskId";

    /** Versioned canonical prefix (§13 table baseline). */
    public static final String BASE_PREFIX = "/api/v1/ai";

    /** §13 #1 GET: query available capability catalog and contract version (read-only). */
    public static final String CAPABILITIES_COLLECTION = BASE_PREFIX + "/capabilities";

    /** §13 #2 POST: execute specified capability (sync, via unified router; side-effect per Tool Contract). */
    public static final String CAPABILITY_EXECUTE = CAPABILITIES_COLLECTION + "/{id}/execute";

    /** §13 #3 POST: submit long-running capability task (async, runtime-task managed, returns taskId). */
    public static final String CAPABILITY_TASKS = CAPABILITIES_COLLECTION + "/{" + P_ID + "}/tasks";

    /** Task status echo: GET {BASE}/capabilities/tasks/{taskId} (§10.4 getStatus REST face). */
    public static final String CAPABILITY_TASK_STATUS = CAPABILITY_TASKS + "/status";

    /** Task status by ID: GET /capabilities/tasks/{taskId}/status (validation constant). */
    public static final String CAPABILITY_TASK_STATUS_BY_ID = CAPABILITIES_COLLECTION
            + "/tasks/{" + P_TASK_ID + "}/status";
}
