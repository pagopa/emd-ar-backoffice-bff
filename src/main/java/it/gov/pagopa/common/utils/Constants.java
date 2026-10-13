package it.gov.pagopa.common.utils;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

public class Constants {

  private Constants(){}

  public static final ZoneId ZONEID = ZoneId.of("Europe/Rome");
  public static final TimeZone DEFAULT_TIMEZONE = TimeZone.getTimeZone(ZONEID);
  public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

}

