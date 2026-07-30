package com.chinacreator.gzcm.services.agent.runtime.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hermes MCP Client — stdio-based JSON-RPC bridge to ECOS Hermes MCP Server.
 *
 * Talks to Python process: python3 /engine/ecos-mcp-server.py
 * Protocol: MCP JSON-RPC 2.0, one JSON object per line over stdin/stdout.
 */
@Component("ecosHermesMCPClient")
public class HermesMCPClient implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(HermesMCPClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String pythonPath = "python3";
    private final String serverScript;
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private final AtomicInteger requestId = new AtomicInteger(1);
    private volatile boolean initialized = false;
    private boolean available = false;

    public HermesMCPClient() {
        this.serverScript = "/home/guorongxiao/ECOS/ecos_backend/engine/ecos-mcp-server.py";
    }

    /** Lazy init — only starts MCP server on first tool call. */
    private synchronized void ensureInitialized() {
        if (initialized) return;
        initialized = true;
        try {
            ProcessBuilder pb = new ProcessBuilder(pythonPath, serverScript);
            pb.redirectErrorStream(false);
            process = pb.start();
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

            // MCP handshake
            ObjectNode init = mapper.createObjectNode();
            init.put("jsonrpc", "2.0");
            init.put("id", 0);
            init.put("method", "initialize");
            ObjectNode params = init.putObject("params");
            params.put("protocolVersion", "2024-11-05");
            params.putObject("capabilities");
            params.putObject("clientInfo").put("name", "ecos-java").put("version", "1.0");

            String response = sendRaw(init.toString());
            if (response != null && response.contains("\"result\"")) {
                available = true;
                log.info("HermesMCPClient connected to MCP server");
            }
        } catch (Exception e) {
            log.warn("HermesMCPClient failed to start: {}", e.getMessage());
            available = false;
        }
    }

    /** Call a tool by name with arguments. Returns JSON string with response content. */
    public String callTool(String toolName, Map<String, Object> args) {
        if (!available && !initialized) ensureInitialized();
        if (!available) return "{\"error\":\"MCP server unavailable\"}";

        try {
            ObjectNode req = mapper.createObjectNode();
            req.put("jsonrpc", "2.0");
            req.put("id", requestId.getAndIncrement());
            req.put("method", "tools/call");
            ObjectNode p = req.putObject("params");
            p.put("name", toolName);
            p.putObject("arguments");
            if (args != null) {
                for (Map.Entry<String, Object> e : args.entrySet()) {
                    if (e.getValue() instanceof String) {
                        ((ObjectNode) p.get("arguments")).put(e.getKey(), (String) e.getValue());
                    } else if (e.getValue() instanceof Integer) {
                        ((ObjectNode) p.get("arguments")).put(e.getKey(), (Integer) e.getValue());
                    }
                }
            }

            String raw = sendRaw(req.toString());
            if (raw == null) return "{\"error\":\"no response\"}";

            JsonNode node = mapper.readTree(raw);
            JsonNode result = node.get("result");
            if (result != null) {
                JsonNode content = result.get("content");
                if (content != null && content.isArray() && content.size() > 0) {
                    return content.get(0).get("text").asText();
                }
                return result.toString();
            }
            JsonNode error = node.get("error");
            if (error != null) return "{\"error\":\"" + error.get("message").asText() + "\"}";

            return "{\"error\":\"unknown response format\"}";
        } catch (Exception e) {
            log.error("callTool {} failed: {}", toolName, e.getMessage());
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private synchronized String sendRaw(String json) {
        if (writer == null || reader == null) return null;
        try {
            writer.write(json);
            writer.newLine();
            writer.flush();
            return reader.readLine();
        } catch (IOException e) {
            log.error("MCP I/O error: {}", e.getMessage());
            available = false;
            return null;
        }
    }

    public boolean isAvailable() { return available; }

    @Override
    public synchronized void close() {
        if (process != null) {
            process.destroy();
            try { process.waitFor(); } catch (InterruptedException ignored) {}
            process = null;
        }
    }
}
