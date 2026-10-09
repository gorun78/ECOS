package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Design-06 F06-22 item-3 - source-scan-generated API contract artifact.
 *
 * <p>Instead of a missing OpenAPI artifact (X-86), this reuses the
 * {@code AiRouteIntegrityTest} source-scan idiom: it walks
 * {@code engine/ai-engine/ai-engine-impl/src/main/java}, picks every
 * {@code @RestController} class (excluding {@code @RestControllerAdvice}),
 * extracts the class-level {@code @RequestMapping} base plus each method-level
 * HTTP mapping, and GENERATES
 * {@code docs/40-implementation.../AI_ENGINE_API_CONTRACT.md}.
 *
 * <p>Key property demanded by the spec ("test-produced, not hand-written"): the
 * markdown is DERIVED from source on every run and overwrites the file, so it
 * cannot silently drift from the code (X-73/X-78 lesson). The assertion then
 * checks the artifact is non-trivial (>=1 controller, >=1 mapped endpoint) and
 * landed on disk, guarding against "scan silently matches zero" false-greens.</p>
 */
class AiEngineApiContractTest {

    /** class-level base mapping, e.g. @RequestMapping("/api/v1/agent-loop"). */
    private static final Pattern CLASS_BASE = Pattern.compile(
            "@RequestMapping\\(\\s*(?:value\\s*=\\s*)?[\"{]([^\"\\s]+)");

    /** method-level HTTP mappings: @GetMapping/@PostMapping/@PutMapping/@DeleteMapping/@PatchMapping (optional path value). */
    private static final Pattern METHOD_MAP = Pattern.compile(
            "@(Get|Post|Put|Delete|Patch)Mapping\\s*\\(\\s*(?:value\\s*=\\s*)?[\"{]([^\"\\s]+)");

    private static final Pattern CLASS_NAME = Pattern.compile(
            "class\\s+([A-Za-z0-9_]+)");

    /** docs/40-implementation relative dir for this feature slice. */
    private static final String DOC_REL =
            "docs/40-\u5b9e\u73b0/features/20260929-ai-engine/AI_ENGINE_API_CONTRACT.md";

    @Test
    @EnabledIf("ecosRepoResolvable")
    @DisplayName("F06-22 item-3: regenerate AI_ENGINE_API_CONTRACT.md from a source scan (not hand-written)")
    void contractArtifactIsGeneratedFromSourceScan() throws IOException {
        Path ecsRoot = repoRoot();
        Path aiImplMain = ecsRoot.resolve(
                "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java");
        assertTrue(Files.isDirectory(aiImplMain), "ai-engine-impl main missing: " + aiImplMain);

        List<ControllerContract> controllers = scanControllers(aiImplMain).stream()
                .filter(c -> !c.endpoints.isEmpty())
                .sorted((a, b) -> a.className.compareTo(b.className))
                .collect(Collectors.toList());

        long endpointCount = controllers.stream().mapToLong(c -> c.endpoints.size()).sum();
        assertTrue(!controllers.isEmpty(),
                "no @RestController with mappings scanned (parse broken?)");
        assertTrue(endpointCount >= 1, "zero endpoints scanned (parse broken?)");

        String markdown = render(ecsRoot, controllers, endpointCount);

        Path docPath = ecsRoot.resolve(DOC_REL);
        Files.createDirectories(docPath.getParent());
        Files.writeString(docPath, markdown, StandardCharsets.UTF_8);

        assertTrue(Files.exists(docPath) && Files.isReadable(docPath),
                "contract artifact not written: " + docPath);
        String written = Files.readString(docPath, StandardCharsets.UTF_8);
        assertFalse(written.isBlank(), "contract artifact empty: " + docPath);
        assertTrue(written.contains("GENERATED"),
                "contract artifact missing generated-header (must be test-produced)");
    }

    /** The generator only runs where the full monorepo is on disk (not in a stripped CI slice). */
    static boolean ecosRepoResolvable() {
        try {
            return Files.isDirectory(repoRoot().resolve("ecos_backend"));
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    // -- scan --------------------------------------------------------------

    private static Path repoRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve("ecos_backend"))) return p;
            Path parent = p.getParent();
            if (parent == null || parent.equals(p)) break;
            p = parent;
        }
        throw new NoSuchElementException("cwd not anchored to ECOS repo root (no ecos_backend/)");
    }

    private static List<ControllerContract> scanControllers(Path root) throws IOException {
        List<ControllerContract> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(p -> p.toString().endsWith(".java")).sorted()
             .forEach(p -> {
                 String t = read(p);
                 if (t.contains("@RestControllerAdvice")) return;
                 if (!t.contains("@RestController")) return;
                 out.add(parseController(p.getFileName().toString(), t));
             });
        }
        return out;
    }

    private static ControllerContract parseController(String fileName, String text) {
        ControllerContract c = new ControllerContract();
        c.fileName = fileName;
        Matcher cn = CLASS_NAME.matcher(text);
        c.className = cn.find() ? cn.group(1) : fileName.replace(".java", "");

        Matcher base = CLASS_BASE.matcher(text);
        if (base.find()) {
            c.base = "\"" + base.group(1) + "\"";
        }

        Matcher mm = METHOD_MAP.matcher(text);
        while (mm.find()) {
            c.endpoints.add(verbs(mm.group(1)) + " " + unquote(mm.group(2)));
        }
        return c;
    }

    private static String verbs(String http) {
        switch (http) {
            case "Get": return "GET";
            case "Post": return "POST";
            case "Put": return "PUT";
            case "Delete": return "DELETE";
            case "Patch": return "PATCH";
            default: return http;
        }
    }

    private static String unquote(String s) {
        return s.replace("\"", "").trim();
    }

    private static String render(Path ecsRoot, List<ControllerContract> controllers, long endpointCount) {
        String ts = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        StringBuilder md = new StringBuilder();
        md.append("# AI Engine API Contract (source-scanned)\n\n");
        md.append("> **THIS FILE IS GENERATED** - do not edit by hand.\n");
        md.append("> Generated by `AiEngineApiContractTest#contractArtifactIsGeneratedFromSourceScan`\n");
        md.append("> Source: `engine/ai-engine/ai-engine-impl/src/main/java` (`@RestController` classes)\n");
        md.append("> Regenerate: `mvn -o -pl gateway -f ecos_backend/pom.xml test -Dtest='AiEngineApiContractTest' -Dsurefire.failIfNoSpecifiedTests=false`\n");
        md.append("> Generated at (UTC): ").append(ts).append("\n\n");
        md.append("Counts: **").append(controllers.size()).append(" controllers**, ");
        md.append("**").append(endpointCount).append(" mapped endpoints**.\n\n");

        md.append("## Overview\n\n");
        md.append("| Controller | Base | #Endpoints |\n");
        md.append("|------------|------|-----------|\n");
        for (ControllerContract c : controllers) {
            md.append("| `").append(c.className).append("` | `").append(c.base.replace("\"", "")).append("` | ")
              .append(c.endpoints.size()).append(" |\n");
        }
        md.append('\n');

        md.append("## Endpoints\n\n");
        for (ControllerContract c : controllers) {
            md.append("### ").append(c.className).append(" (").append(c.base.replace("\"", "")).append(")\n\n");
            md.append("| Method | Path |\n");
            md.append("|--------|------|\n");
            for (String ep : c.endpoints) {
                String[] split = ep.split(" ", 2);
                md.append("| ").append(split[0]).append(" | `").append(split[1]).append("` |\n");
            }
            md.append('\n');
        }
        return md.toString();
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("cannot read " + p, e);
        }
    }

    /** Mutable per-controller scan result. */
    private static final class ControllerContract {
        String className;
        String fileName;
        String base = "(none)";
        List<String> endpoints = new ArrayList<>();
        ControllerContract() { }
    }
}
