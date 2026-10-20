package it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class MessageSearchParameterV1 {

    private String messageId;
    private String recipientId;
    private String originId;
    private MessageStateV1 state;

    @DateTimeFormat (iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endDate;

    private String cursor;
    private Integer size = 10;
    private List<String> fields;

}
