package it.gov.pagopa.emd.ar.backoffice.api.v1.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPageResponseDTOV1{
    private List<KeycloakUserDTOV1> content;
    private Integer totalElements;
    private Integer first;
    private Integer max;
}