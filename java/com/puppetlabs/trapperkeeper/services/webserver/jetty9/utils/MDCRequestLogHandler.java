package com.puppetlabs.trapperkeeper.services.webserver.jetty9.utils;

import java.io.IOException;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.RequestLogHandler;

import org.slf4j.MDC;

/**
 * An implementation of the Jetty RequestLogHandler that imbues Request objects
 * with a copy of the SLF4J Mapped Diagnostic Context (MDC) and handles clearing
 * the MDC after each request.
 *
 * Patterned after:
 *
 *   https://github.com/jetty-project/jetty-and-logback-example/blob/master/jetty-slf4j-mdc-handler/src/main/java/org/eclipse/jetty/examples/logging/MDCHandler.java
 */
public class MDCRequestLogHandler extends RequestLogHandler {
  public static final String MDC_ATTR = "com.puppetlabs.trapperkeeper.services.webserver.jetty9.MDC";

  @Override
  public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
          throws IOException, ServletException
  {
    // NOTE: Will return null if nothing has been set in the MDC of the worker
    //       thread currently handling this request.
    Map<String, String> savedContext = MDC.getCopyOfContextMap();

    try {
      super.handle(target, baseRequest, request, response);

      // Tag request with a copy of the MDC so that values are accessible if
      // logging happens in a separate thread.
      Map<String, String> mdcPropertyMap = MDC.getCopyOfContextMap();
      baseRequest.setAttribute(MDC_ATTR, mdcPropertyMap);
    } finally {
      // Clears any context items created during the request so they don't
      // contaminate other requests when a worker thread is re-used.
      if (savedContext != null) {
        MDC.setContextMap(savedContext);
      } else {
        MDC.clear();
      }
    }
  }
}
