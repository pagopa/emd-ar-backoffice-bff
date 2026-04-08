package it.gov.pagopa.common.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import it.gov.pagopa.common.utils.SecurityUtils;

import java.net.URI;

class SecurityUtilsTest {

  @Test
  void givenUriWhenRemovePiiFromURIThenOk(){
    String result = SecurityUtils.removePiiFromURI(URI.create("https://host/path?param1=PII&param2=noPII"));
    Assertions.assertEquals("https://host/path?param1=***&param2=***", result);
  }

  @Test
  void givenNullUriWhenRemovePiiFromURIThenOk(){
    Assertions.assertNull(SecurityUtils.removePiiFromURI(null));
  }
}
