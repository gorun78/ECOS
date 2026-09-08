import subprocess, json, time, sys

board = "ecos"
worker_id = "t_6c638d9e"
verifier_id = "t_cb5c8f39"
synth_id = "t_6fcd1f07"

def show(tid):
    r = subprocess.run(["hermes", "kanban", "--board", board, "show", tid, "--json"],
                       capture_output=True, text=True, timeout=20)
    try:
        d = json.loads(r.stdout)
        t = d.get("task", {})
        return t.get("status", "unknown"), d.get("events", [])
    except:
        return "archived", []

print("=== PMO-45 后台轮询开始 ===", flush=True)
start = time.time()
polled = 0
while True:
    wst, wevents = show(worker_id)
    vst, _ = show(verifier_id)
    sst, _ = show(synth_id)
    elapsed = int(time.time() - start)
    print(f"[{elapsed//60}m{elapsed%60:02d}s] Worker={wst:8s} Verifier={vst:8s} Synth={sst:8s}", flush=True)
    polled += 1
    if wst == "done" or wst == "archived" or wst == "gave_up":
        print(f"Worker 终态: {wst}，开始追踪 Verifier/Synth", flush=True)
        if wst == "done":
            for e in wevents:
                if e.get("kind") == "completed":
                    print("Worker Summary:", e.get("payload",{}).get("summary","")[:500], flush=True)
        # 继续轮询 Verifier + Synth
        for v in range(20):
            time.sleep(30)
            vst, vevents = show(verifier_id)
            sst, sevents = show(synth_id)
            print(f"  [{(time.time()-start)//60}m] Verifier={vst:8s} Synth={sst:8s}", flush=True)
            if vst in ("done","archived","gave_up") and sst in ("done","archived","gave_up"):
                print("=== 全部完成 ===", flush=True)
                if vst == "done":
                    for e in vevents:
                        if e.get("kind") in ("completed","approval"):
                            print("Verifier:", json.dumps(e.get("payload",{}), ensure_ascii=False)[:500], flush=True)
                break
        sys.exit(0)
    if wst == "blocked":
        print("Worker BLOCKED，自动 unblock 重试", flush=True)
        subprocess.run(["hermes","kanban","--board",board,"unblock",worker_id], capture_output=True, text=True)
    if polled >= 90:  # 90 * 60s = 90min 超时上限
        print("=== 轮询超时（90min），停止 ===", flush=True)
        sys.exit(2)
    time.sleep(60)
