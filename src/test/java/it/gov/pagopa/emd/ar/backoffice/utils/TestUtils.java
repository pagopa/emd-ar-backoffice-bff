package it.gov.pagopa.emd.ar.backoffice.utils;

import java.util.TimeZone;

import it.gov.pagopa.emd.ar.backoffice.utils.Constants;

public class TestUtils {

  static {
    clearDefaultTimezone();
  }

  public static void clearDefaultTimezone() {
    TimeZone.setDefault(Constants.DEFAULT_TIMEZONE);
  }
}
