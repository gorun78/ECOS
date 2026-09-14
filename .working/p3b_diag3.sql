SELECT id, status,
       jsonb_typeof(simulation_result->'assumptionRefs') AS refs_type,
       jsonb_typeof(simulation_result->'riskMetrics') AS risk_type,
       simulation_result->'intervenedMean' AS im,
       simulation_result->'baselineMean' AS bm,
       simulation_result->'riskMetrics'->>'expectedBenefit' AS benefit,
       jsonb_typeof(simulation_result->'sensitivityTop3') AS s3_type
FROM ecos_scenario_run WHERE scenario_id='sc001' ORDER BY create_time DESC LIMIT 1;
