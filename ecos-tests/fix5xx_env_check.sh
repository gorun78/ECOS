#!/usr/bin/env bash
set -u
echo '=== process check ==='
ps aux | grep -E 'java|vite' | grep -v grep | awk '{printf "%s %s %s\n",$2,$11,$12}' | head -10
echo '=== listening ports ==='
ss -ltn 2>/dev/null | awk '{print $4}' | grep -E ':(8080|3000|5432)\b' | sort -u
echo '=== DB tables ==='
export PGPASSWORD=postgres
psql -h localhost -U postgres -d sys_man -tAc "SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('ecos_ontology_proposals','ecos_agent_registry','td_catalog_item','ecos_dq_issue','ecos_dq_rule','ecos_workflow_instance','ecos_ontology_relationship','ecos_ontology_property','ecos_ontology_entity','ecos_glossary_term') ORDER BY 1;" 2>&1
echo '=== existing schema SQL files referencing these tables ==='
cd /home/guorongxiao/ECOS/ecos_backend 2>/dev/null || exit 0
grep -rlE 'CREATE TABLE' --include='*.sql' . 2>/dev/null | head -30
echo '=== sql dirs ==='
find . -type d -name sql 2>/dev/null | head
