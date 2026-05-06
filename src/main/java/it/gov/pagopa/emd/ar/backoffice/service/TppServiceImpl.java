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
     * {@inheritDoc}
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

        //Create Keycloak client
        return authService.createKeycloakClient(tppDTO.getBusinessName())
            .flatMap(creds -> {
                log.info("[AR-BFF][TPP_CREATE] Keycloak client created. Now saving TPP on Database.");
                //Save TPP on Database
                return tppConnector.saveTpp(tppDTO);
            })
            .doOnSuccess(tppId -> log.info("[AR-BFF][TPP_CREATE] TPP creation completed. Generated ID: {}", tppId))
            .doOnError(e -> log.error("[AR-BFF][TPP_CREATE] Error during TPP creation process: {}", e.getMessage()));
    }
}
