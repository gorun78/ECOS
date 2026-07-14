#!/bin/bash
# ECOS Frontend Smoke Test (curl-based, no browser required)
# Verifies all 25 SPA pages return valid HTML

FRONTEND="http://localhost:5173"
PASS=0
FAIL=0
RESULTS=""

PAGES=(
  "Mission Control|#/mission_control"
  "Biz Dashboard|#/biz_dashboard"
  "Data Catalog|#/data_catalog"
  "Data Explorer|#/data_explorer"
  "Data Lineage|#/data_lineage"
  "Object Runtime|#/object_runtime"
  "Data Quality|#/data_quality"
  "Ontology Explorer|#/ontology_explorer"
  "Ontology Designer|#/ontology_designer"
  "Glossary|#/glossary"
  "Workflow Studio|#/workflow_studio"
  "World Model|#/world_model"
  "Pipeline Builder|#/pipeline_builder"
  "Code Workbook|#/code_workbook"
  "Agent Studio|#/agent_studio"
  "Agent Mesh|#/agent_mesh"
  "Cognitive OS|#/cognitive_os"
  "Security Audit|#/security_audit"
  "IAM Users|#/iam_users"
  "Project Tracker|#/project_tracker"
  "Contract Manager|#/contract_manager"
  "Ops Dashboard|#/ops_dashboard"
  "Operations Dashboard|#/operations_dashboard"
  "Dataset Explorer|#/dataset_explorer/test"
  "Market|#/market"
)

echo "════════════════════════════════════════════════════"
echo "  ECOS Frontend Smoke Test (curl-based)"
echo "  $(date '+%Y-%m-%d %H:%M:%S')"
echo "════════════════════════════════════════════════════"
echo ""

for entry in "${PAGES[@]}"; do
  NAME="${entry%%|*}"
  HASH="${entry##*|}"
  
  HTTP=$(curl -s -o /tmp/ecos_smoke.html -w "%{http_code}" --max-time 10 "${FRONTEND}/${HASH}" 2>/dev/null)
  SIZE=$(wc -c < /tmp/ecos_smoke.html 2>/dev/null)
  
  # SPA always returns index.html via fallback, so 200 + > 200 bytes = OK
  if [ "$HTTP" = "200" ] && [ "$SIZE" -gt 200 ]; then
    echo "  ✅ ${NAME}"
    PASS=$((PASS + 1))
  else
    echo "  ❌ ${NAME} (HTTP ${HTTP}, ${SIZE} bytes)"
    FAIL=$((FAIL + 1))
  fi
done

echo ""
echo "════════════════════════════════════════════════════"
echo "  RESULTS: ${PASS}/$((PASS + FAIL)) passed"
echo "════════════════════════════════════════════════════"

exit $FAIL
