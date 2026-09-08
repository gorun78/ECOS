import subprocess, json, time, sys

board = "ecos"
worker_id = "t_2fcd5b9a"
verifier_id = "t_6ff8d4aa"
synth_id = "t_5700791a"

def show(tid):
    r = subprocess.run(["hermes", "kanban", "--board", board, "show", tid, "--json"],
                       capture_output=True, text=True, timeout=20)
    try:
        d = json.loads(r.stdout)
        t = d.get("task", {})
        return t.get("status", "unknown"), d.get("events", [])
    except:
        return "archived", []

last_w = last_v = last_s = None
round_num = 0
while True:
    round_num += 1
    wst, wev = show(worker_id)
    if wst != last_w:
        last_w = wst
        print(f"[{round_num}] Worker: {wst}", flush=True)
        if wst == "done":
            for e in wev:
                if e.get("kind") == "completed":
                    print("  Summary:", e.get("payload",{}).get("summary","")[:600], flush=True)
    if wst in ("done", "blocked", "gave_up"):
        vst, vev = show(verifier_id)
        sst, sev = show(synth_id)
        if vst != last_v:
            last_v = vst
            print(f"[{round_num}] Verifier: {vst}", flush=True)
            if vst == "done":
                for e in vev:
                    if e.get("kind") in ("completed","approval"):
                        print("  ", json.dumps(e.get("payload",{}), ensure_ascii=False)[:400], flush=True)
        if sst != last_s:
            last_s = sst
            print(f"[{round_num}] Synth: {sst}", flush=True)
            for e in sev:
                if e.get("kind") in ("completed","approval"):
                    print("  ", json.dumps(e.get("payload",{}), ensure_ascii=False)[:400], flush=True)
        if vst in ("done","archived","gave_up") and sst in ("done","archived","gave_up"):
            print("=== ALL DONE ===", flush=True)
            sys.exit(0)
    if round_num >= 40:
        print("Timeout (40 polls)", flush=True)
        sys.exit(2)
    time.sleep(60)
