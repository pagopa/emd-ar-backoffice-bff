package it.gov.pagopa.common.config.json.jackson3;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class OffsetDateTimeToUtcJackson3Serializer extends ValueSerializer<OffsetDateTime> {

    @Override
    public void serialize(OffsetDateTime value, tools.jackson.core.JsonGenerator gen, SerializationContext ctxt)
            throws JacksonException {
        gen.writeString(value.withOffsetSameInstant(ZoneOffset.UTC).toString());
    }
}
