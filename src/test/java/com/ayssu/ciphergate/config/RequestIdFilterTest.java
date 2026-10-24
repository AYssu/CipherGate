package com.ayssu.ciphergate.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RequestIdFilterTest {
    private final RequestIdFilter filter = new RequestIdFilter();

    @Test void safeCallerRequestIdIsReturnedAndAvailableOnlyDuringRequest() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.addHeader(RequestIdFilter.HEADER, "support-ticket_123.trace");
        filter.doFilter(request, response, (req, res) ->
                assertEquals("support-ticket_123.trace", MDC.get("requestId")));
        assertEquals("support-ticket_123.trace", response.getHeader(RequestIdFilter.HEADER));
        assertNull(MDC.get("requestId"));
    }

    @Test void missingOversizedAndUnsafeIdsAreReplacedWithBoundedGeneratedIds() throws Exception {
        for (String supplied : new String[]{null, "", "x".repeat(65), "log\r\nforged", "<script>", "with space"}) {
            var request = new MockHttpServletRequest();
            var response = new MockHttpServletResponse();
            if (supplied != null) request.addHeader(RequestIdFilter.HEADER, supplied);
            filter.doFilter(request, response, (req, res) -> assertNotNull(MDC.get("requestId")));
            assertDoesNotThrow(() -> UUID.fromString(response.getHeader(RequestIdFilter.HEADER)));
            assertNull(MDC.get("requestId"));
        }
    }

    @Test void failingRequestsNeverLeakCorrelationIdToNextRequest() {
        assertThrows(ServletException.class, () -> filter.doFilter(new MockHttpServletRequest(),
                new MockHttpServletResponse(), (req, res) -> { throw new ServletException("downstream error"); }));
        assertNull(MDC.get("requestId"));
    }
}
