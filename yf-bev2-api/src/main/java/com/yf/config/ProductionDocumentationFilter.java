package com.yf.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Also blocks bundled documentation static assets when production is active. */
@Component
@Profile("prod")
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ProductionDocumentationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain chain) throws ServletException, IOException {
        String path = request.getServletPath();
        if (path.equals("/doc.html") || path.equals("/swagger-ui.html")
                || path.equals("/swagger-ui") || path.startsWith("/swagger-ui/")
                || path.equals("/v3/api-docs") || path.startsWith("/v3/api-docs/")
                || path.equals("/webjars") || path.startsWith("/webjars/")) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }
}
