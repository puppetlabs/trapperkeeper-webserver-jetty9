package com.puppetlabs.trapperkeeper.services.webserver.jetty9.utils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/**
 * This class provides a wrapper for an existing HttpServletRequest object
 * which returns an alternate request URI from the one that the injected
 * HttpServletRequest object would return when its getRequestURI() method
 * is called.
 */
public class HttpServletRequestWithAlternateRequestUri
        extends HttpServletRequestWrapper {

    private String requestUri;

    public HttpServletRequestWithAlternateRequestUri(
            HttpServletRequest request,
            String requestUri) {
        super(request);
        this.requestUri = requestUri;
    }

    @Override
    public String getRequestURI() {
        return requestUri;
    }
}
