package it.gov.pagopa.emd.ar.backoffice.api.v1.tpp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TppTemplateValidationRequestDTOV1 {

    /** Tetto massimo (in caratteri) del sorgente del template accettato dall'endpoint. */
    public static final int MAX_TEMPLATE_SIZE = 8192;

    @NotBlank
    @Size(max = MAX_TEMPLATE_SIZE, message = "Il template supera la dimensione massima consentita di " + MAX_TEMPLATE_SIZE + " caratteri.")
    private String templateSource;
}
