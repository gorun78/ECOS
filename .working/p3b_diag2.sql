SELECT id, status, jsonb_typeof(simulation_result->
\assumptionRefs\'), jsonb_typeof(simulation_result->\riskMetrics\'), simulation_result->\riskMetrics\'->\'expectedBenefit' FROM ecos_scenario_run WHERE scenario_id=\sc001\' ORDER BY create_time DESC LIMIT 1;
