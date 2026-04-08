package it.gov.pagopa.common.utils;

import java.util.TimeZone;

import it.gov.pagopa.common.utils.Constants;

public class TestUtils {

  static {
    clearDefaultTimezone();
  }

  public static void clearDefaultTimezone() {
    TimeZone.setDefault(Constants.DEFAULT_TIMEZONE);
  }
}
