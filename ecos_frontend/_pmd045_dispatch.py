#!/usr/bin/env python3
"""派发 PMO-45 swarm 到 ecos board"""
import subprocess, json, sys

goal = open("/home/guorongxiao/ECOS/ecos_frontend/_pmd045_goal.txt").read()

cmd = [
    "hermes", "kanban", "--board", "ecos", "swarm",
    "--worker", "ecos-be:数据源多类型CRUD",
    "--verifier", "ecos-fe",
    "--synthesizer", "ecos-pm",
    "--created-by", "ecos-pm",
    "--json",
    goal,
]

r = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
print("RC:", r.returncode)
print("STDOUT:", r.stdout[:2000])
if r.stderr:
    print("STDERR:", r.stderr[:500])
