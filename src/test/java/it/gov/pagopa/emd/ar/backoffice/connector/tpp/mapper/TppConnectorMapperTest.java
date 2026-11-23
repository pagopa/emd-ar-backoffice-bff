package it.gov.pagopa.emd.ar.backoffice.connector.tpp.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.common.config.json.JsonConfig;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppDTOWithoutTokenSectionV1;
import it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto.TppResponseDTOV1;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppEntityIdResponse;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TppSearchResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TppConnectorMapperTest {

    private final JsonConfig jsonConfig = new JsonConfig();
    private final ObjectMapper jackson2 = jsonConfig.objectMapper();
    private final JsonMapper jackson3 = jsonConfig.objectMapperJackson3();

    @Test
    void preservesOffsetDateTimeAndSerializesUtcForDetailedAndSearchResponses() throws Exception {
        OffsetDateTime upstreamDate = OffsetDateTime.parse("2026-11-23T12:21:45.139+01:00");
        TppEntityIdResponse connectorResponse = TppEntityIdResponse.builder()
                .creationDate(upstreamDate)
                .lastUpdateDate(upstreamDate)
                .build();

        TppResponseDTOV1 detailed = TppConnectorMapper.toTppResponseDTOV1Detailed(connectorResponse);
        TppDTOWithoutTokenSectionV1 searchItem = TppConnectorMapper.toTppDTOWithoutTokenSectionV1(connectorResponse);

        assertThat(detailed.getCreationDate()).isEqualTo(upstreamDate);
        assertThat(detailed.getLastUpdateDate()).isEqualTo(upstreamDate);
        assertThat(searchItem.getCreationDate()).isEqualTo(upstreamDate);
        assertThat(searchItem.getLastUpdateDate()).isEqualTo(upstreamDate);

        assertUtcJson(jackson2.writeValueAsString(detailed));
        assertUtcJson(jackson2.writeValueAsString(searchItem));
        assertUtcJson(jackson3.writeValueAsString(detailed));
        assertUtcJson(jackson3.writeValueAsString(searchItem));
    }

    @Test
    void searchResponsePreservesDateOffset() {
        OffsetDateTime upstreamDate = OffsetDateTime.parse("2026-11-23T12:21:45.139+01:00");
        TppSearchResponse connectorResponse = TppSearchResponse.builder()
                .content(List.of(TppEntityIdResponse.builder().creationDate(upstreamDate).build()))
                .build();

        assertThat(TppConnectorMapper.toTppSearchResponseDTOV1(connectorResponse)
                .getContent().getFirst().getCreationDate())
                .isEqualTo(upstreamDate);
    }

    private static void assertUtcJson(String json) {
        assertThat(json).contains("\"creationDate\":\"2026-11-23T11:21:45.139Z\"")
                .contains("\"lastUpdateDate\":\"2026-11-23T11:21:45.139Z\"")
                .doesNotContain("+01:00");
    }
}
