package com.chinacreator.gzcm.services.apigateway.filter;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Component
@Order(1)
public class JwtAuthFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    @Value("${auth.whitelist.paths:}")
    private List<String> whitelistPaths;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isWhitelisted(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"success\":false,\"code\":\"AUTH_UNAUTHORIZED\",\"message\":\"Missing or invalid Authorization header\"}");
            return;
        }

        String token = authHeader.substring(7);
        try {
            JWT jwt = JWTParser.parse(token);
            if (jwt.getJWTClaimsSet().getExpirationTime() != null &&
                jwt.getJWTClaimsSet().getExpirationTime().before(new java.util.Date())) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"success\":false,\"code\":\"AUTH_TOKEN_EXPIRED\",\"message\":\"Token has expired\"}");
                return;
            }

            request.setAttribute("userId", jwt.getJWTClaimsSet().getSubject());
            request.setAttribute("tenantId", jwt.getJWTClaimsSet().getStringClaim("tenant"));
            request.setAttribute("roles", jwt.getJWTClaimsSet().getStringListClaim("roles"));
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("JWT validation failed: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"success\":false,\"code\":\"AUTH_INVALID_TOKEN\",\"message\":\"Invalid token\"}");
        }
    }

    private boolean isWhitelisted(String path) {
        if (whitelistPaths == null) return false;
        return whitelistPaths.stream().anyMatch(pattern -> {
            if (pattern.endsWith("/**")) {
                return path.startsWith(pattern.substring(0, pattern.length() - 3));
            }
            return path.equals(pattern);
        });
    }
}
