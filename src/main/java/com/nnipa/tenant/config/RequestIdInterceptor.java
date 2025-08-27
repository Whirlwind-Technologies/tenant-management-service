package com.nnipa.tenant.config;

import com.nnipa.tenant.util.ResponseUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor to manage request IDs for tracing
 */
@Slf4j
@Component
@Order(1) // Execute before TenantInterceptor
public class RequestIdInterceptor implements HandlerInterceptor {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // Get or generate request ID
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isEmpty()) {
            requestId = ResponseUtil.generateRequestId();
        }

        // Store for current thread
        ResponseUtil.setRequestId(requestId);

        // Add to response headers
        response.setHeader(REQUEST_ID_HEADER, requestId);

        log.debug("Request ID: {} for path: {}", requestId, request.getRequestURI());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) throws Exception {
        ResponseUtil.clearRequestId();
    }
}