package it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LogsResponseDTO {
    
    private List<LogsDTO> content;

    private int page;
    
    private int size;
    
    private long totalElements;
    
    private int totalPages;
}