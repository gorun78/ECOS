#!/usr/bin/env bash
# w4-pose-probe-run.sh
mkdir -p /tmp
python3 /home/guorongxiao/ECOS/ecos-gen-scratch/w4-pose-probe.py > /tmp/w4-pose-probe.log 2>&1
cp /tmp/w4-pose-probe.log /home/guorongxiao/ECOS/docs/7-integration/wave4-pose-probe.log 2>/dev/null || true
tail -150 /tmp/w4-pose-probe.log
