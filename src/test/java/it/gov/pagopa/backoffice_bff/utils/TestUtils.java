package it.gov.pagopa.backoffice_bff.utils;

import java.util.TimeZone;

import it.gov.pagopa.backoffice_bff.utils.Constants;

public class TestUtils {

  static {
    clearDefaultTimezone();
  }

  public static void clearDefaultTimezone() {
    TimeZone.setDefault(Constants.DEFAULT_TIMEZONE);
  }
}
