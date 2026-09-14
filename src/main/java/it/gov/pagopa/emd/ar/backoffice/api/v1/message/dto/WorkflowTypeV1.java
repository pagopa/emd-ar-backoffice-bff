package it.gov.pagopa.emd.ar.backoffice.api.v1.message.dto;

import lombok.Getter;

/**
 * Enum representing the notification type.
 */
@Getter 
public enum WorkflowTypeV1 {

        ANALOG("ANALOG"),
        DIGITAL("DIGITAL");

    private final String workflowType;

    WorkflowTypeV1(String workflowType) {
        this.workflowType = workflowType;
    }

}
