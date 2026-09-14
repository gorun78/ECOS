SELECT id, status,
       simulation_result->'assumptionRefs' AS refs,
       (simulation_result->'assumptionRefs') @> '["cog_hyp_4a34a896-ca9"]'::jsonb AS contains_hyp2
FROM ecos_scenario_run WHERE scenario_id='sc001' AND run_type='SAFEGUARD'
ORDER BY create_time DESC LIMIT 1;
