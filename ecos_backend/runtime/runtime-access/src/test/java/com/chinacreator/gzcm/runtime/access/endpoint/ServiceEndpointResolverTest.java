package com.chinacreator.gzcm.runtime.access.endpoint;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-00 C.2.3 两态寻址（ADR-15）— ServiceEndpointResolver 的跨服务 base-url 单出口。
 */
class ServiceEndpointResolverTest {

    private final ServiceEndpointResolver r = new ServiceEndpointResolver();

    @Test
    void serviceMode_usesServicePort_whenRouteIsService() {
        assertEquals(Optional.of("http://localhost:18082"),
                r.resolve("datanet", ServiceEndpointResolver.RouteMode.SERVICE));
        assertEquals(Optional.of("http://localhost:18090"),
                r.resolve("workspace", ServiceEndpointResolver.RouteMode.SERVICE));
    }

    @Test
    void serviceMode_fallsBackToMonolith_whenRouteNotYetSwitched() {
        // sysman route=monolith：尚未切流，SERVICE 形态仍回落到 gateway fat-jar
        assertEquals(Optional.of("http://localhost:8080"),
                r.resolve("sysman", ServiceEndpointResolver.RouteMode.SERVICE));
    }

    @Test
    void monolithMode_alwaysUsesMonolithPort() {
        assertEquals(Optional.of("http://localhost:8080"),
                r.resolve("datanet", ServiceEndpointResolver.RouteMode.MONOLITH));
        assertEquals(Optional.of("http://localhost:8080"),
                r.resolve("workspace", ServiceEndpointResolver.RouteMode.MONOLITH));
    }

    @Test
    void resolve_isCaseInsensitiveOnOwner() {
        assertEquals(Optional.of("http://localhost:18082"),
                r.resolve("DATANET", ServiceEndpointResolver.RouteMode.SERVICE));
    }

    @Test
    void resolve_returnsEmptyForUnknownService() {
        assertTrue(r.resolve("no-such-owner", ServiceEndpointResolver.RouteMode.SERVICE).isEmpty());
        assertTrue(r.resolve("no-such-owner", ServiceEndpointResolver.RouteMode.MONOLITH).isEmpty());
    }

    @Test
    void resolve_returnsEmptyForNullService() {
        assertTrue(r.resolve(null, ServiceEndpointResolver.RouteMode.SERVICE).isEmpty());
    }
}
