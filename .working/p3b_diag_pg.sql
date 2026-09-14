SELECT simulation_result::text FROM ecos_scenario_run WHERE id='run_1cf83b73-5e7';
SELECT (simulation_result->'assumptionRefs') @> '["cog_hyp_795a3871-11b"]'::jsonb AS contains_arr,
       (simulation_result->'assumptionRefs') @> '"cog_hyp_795a3871-11b"'::jsonb AS contains_str,
       jsonb_typeof(simulation_result->'assumptionRefs') AS jtype
FROM ecos_scenario_run WHERE id='run_1cf83b73-5e7';
