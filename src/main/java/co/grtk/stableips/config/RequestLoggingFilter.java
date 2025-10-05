package co.grtk.stableips.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Configurable filter to log incoming HTTP requests with detailed caller information.
 *
 * <p>This filter can be enabled/disabled via configuration property:
 * {@code logging.requests.enabled=true/false}. When enabled, it logs all incoming
 * requests with full details including headers, IP, and User-Agent.</p>
 *
 * <p>Usage: Set {@code logging.requests.enabled=true} in application.yml to enable
 * detailed request logging for debugging purposes.</p>
 *
 * @author StableIPs Development Team
 * @since 1.0
 */
@Component
public class RequestLoggingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Value("${logging.requests.enabled:false}")
    private boolean loggingEnabled;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (loggingEnabled && request instanceof HttpServletRequest httpRequest) {
            logRequestDetails(httpRequest);
        }

        chain.doFilter(request, response);
    }

    /**
     * Logs detailed information about the incoming HTTP request.
     *
     * @param request the HTTP request to log
     */
    private void logRequestDetails(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String remoteAddr = request.getRemoteAddr();
        String remoteHost = request.getRemoteHost();
        int remotePort = request.getRemotePort();
        String userAgent = request.getHeader("User-Agent");
        String referer = request.getHeader("Referer");
        String contentType = request.getContentType();

        StringBuilder logMessage = new StringBuilder();
        logMessage.append(String.format("%n=== Incoming Request ===%n"));
        logMessage.append(String.format("Method: %s%n", method));
        logMessage.append(String.format("URI: %s%n", uri));

        if (queryString != null) {
            logMessage.append(String.format("Query String: %s%n", queryString));
        }

        logMessage.append(String.format("Remote Address: %s%n", remoteAddr));
        logMessage.append(String.format("Remote Host: %s%n", remoteHost));
        logMessage.append(String.format("Remote Port: %d%n", remotePort));

        if (userAgent != null) {
            logMessage.append(String.format("User-Agent: %s%n", userAgent));
        }

        if (referer != null) {
            logMessage.append(String.format("Referer: %s%n", referer));
        }

        if (contentType != null) {
            logMessage.append(String.format("Content-Type: %s%n", contentType));
        }

        // Log all headers
        logMessage.append("Headers:%n");
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            for (String headerName : Collections.list(headerNames)) {
                String headerValue = request.getHeader(headerName);
                logMessage.append(String.format("  %s: %s%n", headerName, headerValue));
            }
        }

        logMessage.append("======================");

        log.info(logMessage.toString());
    }
}
