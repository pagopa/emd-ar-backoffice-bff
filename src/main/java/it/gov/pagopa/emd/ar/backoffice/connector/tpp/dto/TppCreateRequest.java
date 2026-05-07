package it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto;

import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.AgentLink;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.AuthenticationType;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.Contact;
import it.gov.pagopa.emd.ar.backoffice.connector.tpp.dto.TokenSection;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Payload sent to the remote emd-tpp service when creating a new TPP.
 * <p>
 * All nested types are from the {@code connector.tpp.dto} package so that this class
 * has <strong>no dependency on the {@code api} layer</strong>.
 * Jakarta validation constraints are enforced upstream (API layer); they are intentionally
 * absent here.
 * </p>
 */
@Value
@Builder
public class TppCreateRequest {

    String entityId;
    String businessName;
    String idPsp;
    String pspDenomination;
    String legalAddress;
    String messageUrl;
    String authenticationUrl;
    AuthenticationType authenticationType;
    Contact contact;
    Boolean state;
    TokenSection tokenSection;
    Map<String, AgentLink> agentLinks;
    Boolean isPaymentEnabled;
    String messageTemplate;
    List<String> whitelistRecipient;
}
