package it.gov.pagopa.emd.ar.backoffice.dto.v1;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
//Will force the field to be included when null, to avoid confusion for the client when the field is missing
@JsonInclude(JsonInclude.Include.ALWAYS)
public class TppIdResponseDTOV1 {
    private String tppId;
}
