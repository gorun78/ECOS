#!/usr/bin/env bash
H=/home/guorongxiao/.hermes/hermes-agent/venv/bin/hermes
$H kanban help 2>&1
echo
echo === Swarm tour ===
$H kanban swarm --help 2>&1 | head -40
