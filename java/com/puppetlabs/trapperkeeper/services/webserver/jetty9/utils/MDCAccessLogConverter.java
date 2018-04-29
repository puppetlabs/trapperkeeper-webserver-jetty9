package com.puppetlabs.trapperkeeper.services.webserver.jetty9.utils;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import ch.qos.logback.core.util.OptionHelper;
import ch.qos.logback.access.pattern.AccessConverter;
import ch.qos.logback.access.spi.IAccessEvent;

/**
 * A Logback pattern converter for logback-access that provides access to
 * items in the SLF4J Mapped Diagnostic Context (MDC).
 *
 * This implementation is adapted from the MDCConverter in logback-classic,
 * with the modification that MDC data is pulled directly from SLF4J rather
 * than from the log event object as IAccessEvent has no accessor method for
 * the MDC at this time. The TrapperKeeper framework configures SLF4J as the
 * logging interface, so we don't need to worry about abstracting over multiple
 * backends to the same degree that logback-access does.
 *
 * This class may be removable if MDC support lands upstream in logback-access:
 *
 *   https://jira.qos.ch/browse/LOGBACK-1016
 *   https://github.com/qos-ch/logback/pull/359
 */
public class MDCAccessLogConverter extends AccessConverter {

  private String key;
  private String defaultValue = "";

  @Override
  public void start() {
    String[] keyInfo = OptionHelper.extractDefaultReplacement(getFirstOption());
    key = keyInfo[0];
    if (keyInfo[1] != null) {
      defaultValue = keyInfo[1];
    }

    super.start();
  }

  @Override
  public void stop() {
    key = null;
    defaultValue = "";

    super.stop();
  }

  @Override
  public String convert(IAccessEvent accessEvent) {
    Map<String, String> mdcPropertyMap = null;
    HttpServletRequest request = accessEvent.getRequest();

    if (request != null) {
      mdcPropertyMap = extractMdcFromRequest(request);
    }

    if (mdcPropertyMap == null) {
      return defaultValue;
    }

    if (key == null) {
      return outputMDCForAllKeys(mdcPropertyMap);
    } else {
      String value = mdcPropertyMap.get(key);

      if (value != null) {
          return value;
      } else {
          return defaultValue;
      }
    }
  }

  private String outputMDCForAllKeys(Map<String, String> mdcPropertyMap) {
    StringBuilder buf = new StringBuilder();
    boolean first = true;

    for (Map.Entry<String, String> entry : mdcPropertyMap.entrySet()) {
      if (first) {
        first = false;
      } else {
        buf.append(", ");
      }
      // format: key0=value0, key1=value1
      buf.append(entry.getKey()).append('=').append(entry.getValue());
    }

    return buf.toString();
  }

  private Map<String, String> extractMdcFromRequest(HttpServletRequest request) {
    // Will either be null or a Map<String, String> stored by an instance of
    // the MDCRequestLogHandler class.
    @SuppressWarnings("unchecked")
    Map<String, String> result = (Map<String, String>) request.getAttribute(MDCRequestLogHandler.MDC_ATTR);

    return result;
  }
}
