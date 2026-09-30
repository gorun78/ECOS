#!/usr/bin/env python3
"""
ECOS Hermes MCP Server — exposes Hermes Agent core tools via Model Context Protocol.

Exposed tools:
  - memory_store / memory_search / memory_get
  - session_create / session_get / session_search
  - delegate_task
  - cronjob_create / cronjob_list / cronjob_pause
  - skill_manage / skills_list

Java agent-service connects here via stdio MCP Client.
"""
import json, sys, os

# ─── MCP Protocol Handler ──────────────────────────────────

def handle_request(request: dict) -> dict:
    method = request.get("method", "")
    req_id = request.get("id")
    
    if method == "initialize":
        return {
            "jsonrpc": "2.0", "id": req_id,
            "result": {
                "protocolVersion": "2024-11-05",
                "serverInfo": {"name": "ECOS Hermes MCP Server", "version": "1.0.0"},
                "capabilities": {"tools": {}}
            }
        }
    
    elif method == "tools/list":
        return {
            "jsonrpc": "2.0", "id": req_id,
            "result": {"tools": list_tools()}
        }
    
    elif method == "tools/call":
        tool_name = request["params"]["name"]
        tool_args = request["params"].get("arguments", {})
        result = call_tool(tool_name, tool_args)
        return {
            "jsonrpc": "2.0", "id": req_id,
            "result": {"content": [{"type": "text", "text": result}]}
        }
    
    elif method == "notifications/initialized":
        return None  # No response for notifications
    
    return {"jsonrpc": "2.0", "id": req_id, "error": {"code": -32601, "message": f"Unknown method: {method}"}}

# ─── Tool Registry ─────────────────────────────────────────

def list_tools():
    return [
        {"name": "memory_store", "description": "Store a fact in persistent memory. Args: key (str), value (str), category (str, default='general')",
         "inputSchema": {"type":"object","properties":{"key":{"type":"string"},"value":{"type":"string"},"category":{"type":"string"}},"required":["key","value"]}},
        {"name": "memory_search", "description": "Search persistent memory. Args: query (str), limit (int, default=5)",
         "inputSchema": {"type":"object","properties":{"query":{"type":"string"},"limit":{"type":"integer"}},"required":["query"]}},
        {"name": "session_create", "description": "Create a new agent session. Args: title (str), profile (str, default='ecos-ai-agent')",
         "inputSchema": {"type":"object","properties":{"title":{"type":"string"},"profile":{"type":"string"}},"required":["title"]}},
        {"name": "session_search", "description": "Search past sessions. Args: query (str), limit (int, default=3)",
         "inputSchema": {"type":"object","properties":{"query":{"type":"string"},"limit":{"type":"integer"}},"required":["query"]}},
        {"name": "delegate_task", "description": "Delegate a task to a subagent. Args: goal (str), context (str, optional)",
         "inputSchema": {"type":"object","properties":{"goal":{"type":"string"},"context":{"type":"string"}},"required":["goal"]}},
        {"name": "cronjob_create", "description": "Create a scheduled job. Args: name (str), schedule (str like '0 9 * * *'), prompt (str)",
         "inputSchema": {"type":"object","properties":{"name":{"type":"string"},"schedule":{"type":"string"},"prompt":{"type":"string"}},"required":["name","schedule","prompt"]}},
        {"name": "cronjob_list", "description": "List all scheduled cron jobs",
         "inputSchema": {"type":"object","properties":{}}},
        {"name": "cronjob_pause", "description": "Pause a cron job. Args: job_id (str)",
         "inputSchema": {"type":"object","properties":{"job_id":{"type":"string"}},"required":["job_id"]}},
        {"name": "skills_list", "description": "List available skills. Args: category (str, optional)",
         "inputSchema": {"type":"object","properties":{"category":{"type":"string"}}}},
    ]

def call_tool(name: str, args: dict) -> str:
    """Dispatch tool call to Hermes CLI"""
    import subprocess
    
    try:
        if name == "memory_store":
            proc = subprocess.run(
                ["hermes", "-p", "ecos-ai-agent", "memory", "add", 
                 args.get("category", "general"), args["key"], args["value"]],
                capture_output=True, text=True, timeout=10)
            return f"stored: {proc.stdout.strip()}"
        
        elif name == "memory_search":
            proc = subprocess.run(
                ["hermes", "-p", "ecos-ai-agent", "memory", "search", args["query"]],
                capture_output=True, text=True, timeout=10)
            return proc.stdout or "no results"
        
        elif name == "session_create":
            proc = subprocess.run(
                ["hermes", "-p", args.get("profile", "ecos-ai-agent"), "chat", "-q", 
                 f"/title {args['title']}"],
                capture_output=True, text=True, timeout=10)
            return f"session created: {args['title']}"
        
        elif name == "session_search":
            limit = args.get("limit", 3)
            proc = subprocess.run(
                ["hermes", "-p", "ecos-ai-agent", "sessions", "list"],
                capture_output=True, text=True, timeout=10)
            return proc.stdout or "[]"
        
        elif name == "delegate_task":
            proc = subprocess.run(
                ["hermes", "-p", "ecos-ai-agent", "chat", "-q", 
                 f"Delegate: {args['goal']}\nContext: {args.get('context', '')}"],
                capture_output=True, text=True, timeout=300)
            return f"delegated: {proc.stdout[:500]}"
        
        elif name == "cronjob_create":
            proc = subprocess.run(
                ["hermes", "-p", "ecos-ai-agent", "cron", "create",
                 args["schedule"], args["name"], "-p", args["prompt"]],
                capture_output=True, text=True, timeout=10)
            return proc.stdout or "created"
        
        elif name == "cronjob_list":
            proc = subprocess.run(
                ["hermes", "-p", "ecos-ai-agent", "cron", "list"],
                capture_output=True, text=True, timeout=10)
            return proc.stdout or "[]"
        
        elif name == "cronjob_pause":
            proc = subprocess.run(
                ["hermes", "-p", "ecos-ai-agent", "cron", "pause", args["job_id"]],
                capture_output=True, text=True, timeout=10)
            return "paused" if proc.returncode == 0 else proc.stderr
        
        elif name == "skills_list":
            proc = subprocess.run(
                ["hermes", "-p", "ecos-ai-agent", "skills", "list"],
                capture_output=True, text=True, timeout=10)
            return proc.stdout or "[]"
        
        return json.dumps({"error": f"unknown tool: {name}"})
    
    except Exception as e:
        return json.dumps({"error": str(e)})

# ─── Main Loop ─────────────────────────────────────────────

def main():
    while True:
        try:
            line = sys.stdin.readline()
            if not line:
                break
            line = line.strip()
            if not line:
                continue
            
            request = json.loads(line)
            response = handle_request(request)
            if response:
                sys.stdout.write(json.dumps(response) + '\n')
                sys.stdout.flush()
        except json.JSONDecodeError:
            pass
        except BrokenPipeError:
            break
        except KeyboardInterrupt:
            break

if __name__ == "__main__":
    main()
