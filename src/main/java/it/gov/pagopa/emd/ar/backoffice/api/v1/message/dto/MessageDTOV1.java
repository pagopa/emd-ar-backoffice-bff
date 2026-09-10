package it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonInclude (JsonInclude.Include.NON_NULL)
@Data 
@Builder 
@AllArgsConstructor 
@NoArgsConstructor 
public class MessageDTOV1 {
    

    private String messageId;

    private String recipientId;

    private String triggerDateTime;

    private String senderDescription;

    private String messageUrl;

    private String originId;

    private String title;

    private String content;

    private Boolean associatedPayment;

    private String analogSchedulingDate;

    private ChannelV1 channel;

    private WorkflowTypeV1 workflowType;

    private String idPsp;

    private String messageRegistrationDate;

    private MessageStateV1 messageState;
    
}