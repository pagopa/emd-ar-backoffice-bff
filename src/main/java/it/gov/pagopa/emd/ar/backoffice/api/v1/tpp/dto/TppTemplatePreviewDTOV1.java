package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TppTemplatePreviewDTOV1 {
    private String rawTemplate;
    private String renderedMessage;
}