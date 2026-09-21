package com.yf.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ProductionDocumentationFilterTest {
    @Test
    void blocksDocumentationButAllowsBusinessAndStaticRoutes() throws Exception {
        for (String path : new String[]{"/doc.html", "/swagger-ui.html", "/swagger-ui/index.html",
                "/v3/api-docs", "/v3/api-docs/group", "/webjars/knife4j/test.js"}) {
            var request = new MockHttpServletRequest("GET", path);
            request.setServletPath(path);
            var response = new MockHttpServletResponse();
            new ProductionDocumentationFilter().doFilter(request, response,
                    (req, res) -> fail("Documentation request was not blocked"));
            assertEquals(404, response.getStatus(), path);
        }
        for (String path : new String[]{"/", "/assets/app.js", "/api/sys/config/detail", "/upload/file/test.png"}) {
            var request = new MockHttpServletRequest("GET", path);
            request.setServletPath(path);
            var reached = new AtomicBoolean();
            new ProductionDocumentationFilter().doFilter(request, new MockHttpServletResponse(),
                    (req, res) -> reached.set(true));
            assertTrue(reached.get(), path);
        }
    }
}
