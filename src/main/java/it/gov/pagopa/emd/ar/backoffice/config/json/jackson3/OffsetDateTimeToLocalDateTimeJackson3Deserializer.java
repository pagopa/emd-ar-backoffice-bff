package it.gov.pagopa.emd.ar.backoffice.config.json.jackson3;

import org.springframework.context.annotation.Configuration;

import it.gov.pagopa.emd.ar.backoffice.config.json.OffsetDateTimeToLocalDateTimeDeserializer;
import tools.jackson.databind.ValueDeserializer;

import java.time.LocalDateTime;


@Configuration
public class OffsetDateTimeToLocalDateTimeJackson3Deserializer extends ValueDeserializer<LocalDateTime> {

  @Override
  public LocalDateTime deserialize(tools.jackson.core.JsonParser p, tools.jackson.databind.DeserializationContext ctxt) {
    return OffsetDateTimeToLocalDateTimeDeserializer.parse(p.getValueAsString());
  }
}

