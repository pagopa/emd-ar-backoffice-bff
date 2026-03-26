package it.gov.pagopa.backoffice_bff.config.json.jackson3;

import org.springframework.context.annotation.Configuration;

import it.gov.pagopa.backoffice_bff.config.json.LocalDateTimeToOffsetDateTimeSerializer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.time.LocalDateTime;

@Configuration
public class LocalDateTimeToOffsetDateTimeJackson3Serializer extends ValueSerializer<LocalDateTime> {

  @Override
  public void serialize(LocalDateTime value, tools.jackson.core.JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
    if(value!=null) {
      gen.writeString(LocalDateTimeToOffsetDateTimeSerializer.convertToOffsetDateTime(value).toString());
    }
  }
}

