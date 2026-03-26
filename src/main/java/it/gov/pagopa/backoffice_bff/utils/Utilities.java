package it.gov.pagopa.backoffice_bff.utils;

import org.slf4j.MDC;

public class Utilities {
    private Utilities(){}

    public static String getTraceId(){
        return MDC.get("traceId");
    }
}
