package com.chinacreator.gzcm.cli.commands;

import com.chinacreator.gzcm.cli.CliOptions;
import com.chinacreator.gzcm.cli.CliStatus;
import com.chinacreator.gzcm.cli.EnvelopeMapper;
import com.chinacreator.gzcm.cli.HttpTransport;
import com.chinacreator.gzcm.cli.OperationRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code ecos capabilities list} — 命令发现与自描述（REQ-CLI-02）。 */
public final class CapabilitiesCommand {

    private CapabilitiesCommand() {
    }

    public static Map<String, Object> run(OperationRegistry registry, CliOptions options,
                                          HttpTransport transport) {
        List<Map<String, Object>> commands = new ArrayList<>();
        List<Map<String, Object>> refs = new ArrayList<>();
        for (OperationRegistry.Command command : registry.commands()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", command.name());
            row.put("description", command.description());
            row.put("accessMode", command.accessMode());
            List<Map<String, Object>> ops = new ArrayList<>();
            for (String operationId : command.operationIds()) {
                OperationRegistry.Operation op = registry.operation(operationId);
                Map<String, Object> opRow = new LinkedHashMap<>();
                opRow.put("operationId", op.operationId());
                opRow.put("method", op.method());
                opRow.put("path", op.path());
                opRow.put("status", op.status());
                ops.add(opRow);
                Map<String, Object> ref = new LinkedHashMap<>();
                ref.put("operationId", op.operationId());
                ref.put("path", op.path());
                refs.add(ref);
            }
            row.put("operations", ops);
            List<Map<String, Object>> flags = new ArrayList<>();
            for (OperationRegistry.Flag flag : command.flags()) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("flag", flag.flag());
                f.put("parameter", flag.parameter());
                f.put("description", flag.description());
                flags.add(f);
            }
            row.put("flags", flags);
            commands.add(row);
        }

        Map<String, Object> out = EnvelopeMapper.local(CliStatus.SUCCESS, null, null, null,
                refs, transport.traceId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("commands", commands);
        out.put("data", data);
        return out;
    }
}
