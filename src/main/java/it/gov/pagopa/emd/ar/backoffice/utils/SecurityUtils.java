package it.gov.pagopa.emd.ar.backoffice.utils;

import java.net.URI;

public class SecurityUtils {
  private SecurityUtils() {}

  public static String removePiiFromURI(URI uri){
    return uri != null
      ? uri.toString().replaceAll("=[^&]*", "=***")
      : null;
  }
}
