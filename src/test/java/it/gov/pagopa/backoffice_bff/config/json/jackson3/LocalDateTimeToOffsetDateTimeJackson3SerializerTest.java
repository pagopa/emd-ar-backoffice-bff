package it.gov.pagopa.backoffice_bff.config.json.jackson3;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import it.gov.pagopa.backoffice_bff.config.json.LocalDateTimeToOffsetDateTimeSerializer;
import it.gov.pagopa.backoffice_bff.config.json.jackson3.LocalDateTimeToOffsetDateTimeJackson3Serializer;
import tools.jackson.core.JsonGenerator;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

class LocalDateTimeToOffsetDateTimeJackson3SerializerTest {

  private final LocalDateTimeToOffsetDateTimeJackson3Serializer serializer = new LocalDateTimeToOffsetDateTimeJackson3Serializer();

  @Test
  void whenDeserializeThenCallHandler(){
    try(MockedStatic<LocalDateTimeToOffsetDateTimeSerializer> serializerStatic = Mockito.mockStatic(LocalDateTimeToOffsetDateTimeSerializer.class)){
      LocalDateTime value = LocalDateTime.now();
      OffsetDateTime expectedResult = OffsetDateTime.now();
      JsonGenerator gen = Mockito.mock(JsonGenerator.class);

      serializerStatic.when(()-> LocalDateTimeToOffsetDateTimeSerializer.convertToOffsetDateTime(value))
          .thenReturn(expectedResult);

      serializer.serialize(value, gen, null);

      Mockito.verify(gen)
        .writeString(expectedResult.toString());
    }
  }

  @Test
  void givenNullWhenDeserializeThenDoNothing(){
    JsonGenerator gen = Mockito.mock(JsonGenerator.class);

    serializer.serialize(null, gen, null);

    Mockito.verifyNoInteractions(gen);
  }
}
