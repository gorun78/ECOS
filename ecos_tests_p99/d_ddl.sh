#!/bin/bash
docker exec ecos-postgres psql -U postgres -d sys_man -c "\d ecos_pipeline_task"
docker exec ecos-postgres psql -U postgres -d sys_man -c "\d ecos_knowledge.graph_node"
docker exec ecos-postgres psql -U postgres -d sys_man -c "\d td_datasource"
docker exec ecos-postgres psql -U postgres -d sys_man -c "\d sys_compliance_rule"
