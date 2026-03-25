package com.stopforfuel.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Sanitizes incoming request parameters and JSON body to prevent XSS attacks.
 * Strips HTML tags and dangerous patterns from all string inputs.
 */
@Component
@Order(2)
public class InputSanitizationFilter implements Filter {

    private static final Pattern SCRIPT_TAG = Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern JAVASCRIPT_PROTO = Pattern.compile("javascript\\s*:", Pattern.CASE_INSENSITIVE);
    private static final Pattern ON_EVENT = Pattern.compile("\\bon\\w+\\s*=", Pattern.CASE_INSENSITIVE);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String contentType = httpRequest.getContentType();

        // Only sanitize JSON request bodies (our API uses JSON exclusively)
        if (contentType != null && contentType.contains("application/json")) {
            SanitizedRequestWrapper wrapper = new SanitizedRequestWrapper(httpRequest);
            chain.doFilter(wrapper, response);
        } else {
            chain.doFilter(new ParameterSanitizedRequestWrapper(httpRequest), response);
        }
    }

    static String sanitize(String input) {
        if (input == null) return null;
        String result = input;
        result = SCRIPT_TAG.matcher(result).replaceAll("");
        result = JAVASCRIPT_PROTO.matcher(result).replaceAll("");
        result = ON_EVENT.matcher(result).replaceAll("");
        result = HTML_TAG.matcher(result).replaceAll("");
        return result.trim();
    }

    /**
     * Wraps the request to sanitize the JSON body.
     */
    private static class SanitizedRequestWrapper extends HttpServletRequestWrapper {
        private final byte[] sanitizedBody;

        public SanitizedRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String sanitized = sanitizeJsonValues(body);
            this.sanitizedBody = sanitized.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(sanitizedBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return byteArrayInputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // no-op
                }

                @Override
                public int read() {
                    return byteArrayInputStream.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return sanitizedBody.length;
        }

        @Override
        public long getContentLengthLong() {
            return sanitizedBody.length;
        }

        /**
         * Sanitizes string values within JSON without breaking the JSON structure.
         * Only sanitizes the value portion of "key": "value" pairs.
         */
        private static String sanitizeJsonValues(String json) {
            if (json == null || json.isEmpty()) return json;

            StringBuilder result = new StringBuilder();
            boolean inString = false;
            boolean escaped = false;
            boolean isKey = true;
            int colonsSinceLastComma = 0;

            for (int i = 0; i < json.length(); i++) {
                char c = json.charAt(i);

                if (escaped) {
                    result.append(c);
                    escaped = false;
                    continue;
                }

                if (c == '\\' && inString) {
                    result.append(c);
                    escaped = true;
                    continue;
                }

                if (c == '"') {
                    if (!inString) {
                        inString = true;
                        result.append(c);
                        // Find the end of this string
                        int start = i + 1;
                        int end = findStringEnd(json, start);
                        if (end >= start && end < json.length()) {
                            String strValue = json.substring(start, end);
                            // Only sanitize values, not keys
                            if (!isKey) {
                                strValue = sanitize(strValue);
                            }
                            result.append(strValue);
                            result.append('"');
                            i = end; // skip past the closing quote
                            inString = false;
                            continue;
                        }
                    }
                    inString = false;
                    result.append(c);
                    continue;
                }

                if (!inString) {
                    if (c == ':') {
                        isKey = false;
                        colonsSinceLastComma++;
                    } else if (c == ',' || c == '{' || c == '[') {
                        isKey = (c == ',' || c == '{');
                        if (c == ',') colonsSinceLastComma = 0;
                    }
                }

                result.append(c);
            }

            return result.toString();
        }

        private static int findStringEnd(String json, int start) {
            for (int i = start; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '\\') {
                    i++; // skip escaped char
                    continue;
                }
                if (c == '"') {
                    return i;
                }
            }
            return -1;
        }
    }

    /**
     * Wraps the request to sanitize query parameters.
     */
    private static class ParameterSanitizedRequestWrapper extends HttpServletRequestWrapper {
        public ParameterSanitizedRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getParameter(String name) {
            String value = super.getParameter(name);
            return sanitize(value);
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = super.getParameterValues(name);
            if (values == null) return null;
            String[] sanitized = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                sanitized[i] = sanitize(values[i]);
            }
            return sanitized;
        }
    }
}
