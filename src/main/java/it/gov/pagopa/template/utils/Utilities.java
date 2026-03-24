package it.gov.pagopa.template.utils;

import org.slf4j.MDC;

public class Utilities {
    private Utilities(){}

    public static String getTraceId(){
        return MDC.get("traceId");
    }
}
