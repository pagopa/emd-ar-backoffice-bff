package it.gov.pagopa.emd.ar.backoffice.service;

import org.springframework.stereotype.Service;

import it.gov.pagopa.emd.ar.backoffice.connector.v1.TppConnectorImplementationV1;
import it.gov.pagopa.emd.ar.backoffice.dto.v1.TppDTOV1;
import it.gov.pagopa.emd.ar.backoffice.model.v1.ContactV1;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class TppServiceImpl implements TppService {
    
    private final TppConnectorImplementationV1 tppConnector;
    private final AuthServiceImpl authService;
    
    public TppServiceImpl(TppConnectorImplementationV1 tppConnector, AuthServiceImpl authService) {
        this.tppConnector = tppConnector;
        this.authService = authService;
    }

    /**
     * <p>Creates a new TPP by first saving it through the connector and then creating a corresponding Keycloak client.</p>
     * @param tppDTO the TPP data to create
     * @return a Mono containing the tppId of the created TPP or an error if the operation fails
     */
    @Override
    public Mono<String> createTppAndKeycloakClient(TppDTOV1 tppDTO) {
        log.info("[AR-BFF][TPP_CREATE] Creating new TPP: {}", tppDTO.getBusinessName());
        
        //Default field
        tppDTO.setIdPsp("Temporary PSP ID");
        tppDTO.setPspDenomination(tppDTO.getBusinessName());
        tppDTO.setLegalAddress("Temporary Address");
        tppDTO.setContact(new ContactV1("Temporary Contact", "1234567890", "test@example.com"));
        tppDTO.setState(false);
        tppDTO.setIsPaymentEnabled(false);


        return tppConnector.saveTpp(tppDTO)
            .delayUntil(savedTpp -> {
                return authService.createKeycloakClient(tppDTO.getBusinessName())
                    .map(creds -> {
                        log.info("[AR-BFF][TPP_CREATE] Keycloak client created: {}", creds);
                        return String.format("TPP created successfully with ID: %s and Keycloak Client: %s",
                                savedTpp, creds);
                    });
            })
            .doOnError(e -> log.error("[AR-BFF][TPP_CREATE] Error: {}", e.getMessage()));
    }
}
