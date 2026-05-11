# Configurazione — Variabili d'ambiente

| Variabile | Default | Obbligatoria | Categoria | Descrizione |
|-----------|---------|:------------:|-----------|-------------|
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | **Sì in prod** | CORS | Origini abilitate per le chiamate cross-origin. In produzione deve essere impostata esplicitamente; il default è pensato solo per sviluppo locale. |
| `KEYCLOAK_AUTH_URL` | — | **Sì** | Keycloak | URL base di Keycloak (es. `https://keycloak.itn.internal.dev.cstar.pagopa.it`) |
| `KEYCLOAK_REALM` | — | **Sì** | Keycloak | Nome del realm (es. `mdc`) |
| `KEYCLOAK_IDP_ALIAS` | — | **Sì** | Keycloak | Alias dell'Identity Provider per il federated identity link (es. `selfcare-dev-jwt-grant`) |
| `KEYCLOAK_MANAGER_CLIENT_ID` | — | **Sì** | Keycloak | Client ID con permessi `manage-users` e `manage-clients`; usato per upsert utenti e creazione client TPP |
| `KEYCLOAK_MANAGER_CLIENT_SECRET` | — | **Sì** | Keycloak | Client secret del manager client |
| `KEYCLOAK_BACKOFFICE_CLIENT_ID` | — | **Sì** | Keycloak | Client ID del backoffice senza permessi admin; usato per il token exchange finale |
| `KEYCLOAK_BACKOFFICE_CLIENT_SECRET` | — | **Sì** | Keycloak | Client secret del backoffice |
| `KEYCLOAK_TPP_GROUP_NAME` | — | **Sì** | Keycloak | Nome del gruppo a cui viene aggiunto il service account di ogni client TPP (es. `emd-tpp`) |
| `SC_JWT_SET_URI` | — | **Sì** | Area Riservata | JWK Set URI di Area Riservata per la verifica della firma dei token in ingresso |
| `SC_AUTH_EXPECTED_ISSUER` | — | **Sì** | Area Riservata | Valore atteso del claim `iss` nel token AR (es. `https://dev.selfcare.pagopa.it`) |
| `SC_AUTH_EXPECTED_AUDIENCE` | — | **Sì** | Area Riservata | Valore atteso del claim `aud` nel token AR |
| `EMD_TPP` | — | **Sì** | Microservizi | URL base del microservizio `emd-tpp` (es. `http://emd-tpp-microservice-chart:8080`) |
| `WEBCLIENT_POOL_MAX_CONNECTIONS` | `300` | No | WebClient pool | N. massimo connessioni live per host remoto. Reactor Netty mantiene un bucket separato per ogni `(host, port)`. |
| `WEBCLIENT_POOL_MAX_CONNECTIONS_PER_ROUTE` | `50` | No | WebClient pool | *(Deprecato — mantenuto per retrocompatibilità, non usato dal pool builder corrente.)* |
| `WEBCLIENT_POOL_MAX_IDLE_TIME_SECONDS` | `20` | No | WebClient pool | Idle max di una connessione prima dello scarto. Tenuto < 4 min (soglia silent-close dell'Azure LB). |
| `WEBCLIENT_POOL_MAX_LIFE_TIME_SECONDS` | `60` | No | WebClient pool | Durata massima assoluta di una connessione indipendentemente dall'attività. |
| `WEBCLIENT_POOL_PENDING_ACQUIRE_MAX_COUNT` | `600` | No | WebClient pool | Dimensione massima della coda di attesa quando il pool è saturo (≈ 2× `MAX_CONNECTIONS`). |
| `WEBCLIENT_POOL_PENDING_ACQUIRE_TIMEOUT_SECONDS` | `5` | No | WebClient pool | Timeout prima di restituire errore al chiamante se il pool è pieno. |
| `WEBCLIENT_POOL_EVICT_IN_BACKGROUND_SECONDS` | `30` | No | WebClient pool | Periodo del task di eviction in background. |
| `WEBCLIENT_POOL_TIME_TO_LIVE_MINUTES` | `1` | No | WebClient pool | *(Deprecato — mantenuto per retrocompatibilità, non usato dal pool builder corrente.)* |
| `WEBCLIENT_TIMEOUT_CONNECT_MILLIS` | `5000` | No | WebClient timeout | Timeout TCP handshake (ms). |
| `WEBCLIENT_TIMEOUT_IO_MILLIS` | `8000` | No | WebClient timeout | Inattività in lettura sulla pipeline Netty (ms). Deve essere ≥ `RESPONSE_MILLIS`. |
| `WEBCLIENT_TIMEOUT_WRITE_MILLIS` | `8000` | No | WebClient timeout | Inattività in scrittura sulla pipeline Netty (ms). Fallback su `IO_MILLIS` se non impostato. |
| `WEBCLIENT_TIMEOUT_RESPONSE_MILLIS` | `8000` | No | WebClient timeout | Cap end-to-end dell'intera request/response (ms). |
| `TRACING_EXPORT` | `false` | No | Osservabilità | Abilita l'export delle metriche verso un collector OTLP. |
| `TRACING_SAMPLING_PROBABILITY` | `1.0` | No | Osservabilità | Probabilità di sampling dei trace (0.0 = nessuno, 1.0 = tutti). |
| `HEALTH_ACTUATOR_LOGGER_TIMEOUT_DURATION` | `PT1S` | No | Osservabilità | Soglia oltre la quale viene loggata una risposta lenta dell'health check (ISO-8601). |
| `HEALTH_ACTUATOR_SHOW_DETAILS` | `when-authorized` | No | Osservabilità | Visibilità dei dettagli dell'health endpoint (`always`, `never`, `when-authorized`). |
| `LOG_LEVEL_ROOT` | `INFO` | No | Logging | Livello di log globale. |
| `LOG_LEVEL_PAGOPA` | `INFO` | No | Logging | Livello per i package `it.gov.pagopa`. |
| `LOG_LEVEL_SPRING` | `INFO` | No | Logging | Livello per i package `org.springframework`. |
| `LOG_LEVEL_SPRING_BOOT_AVAILABILITY` | `DEBUG` | No | Logging | Livello per gli eventi di availability di Spring Boot. |
| `LOGGING_LEVEL_API_REQUEST_EXCEPTION` | `INFO` | No | Logging | Livello per il `ControllerExceptionHandler`. |
| `LOG_LEVEL_SPRING_DOC` | `ERROR` | No | Logging | Livello per SpringDoc (silenzia warning di annotation parsing). |
| `LOG_LEVEL_PERFORMANCE_LOG` | `INFO` | No | Logging | Livello base per i log di performance. |
| `LOG_LEVEL_PERFORMANCE_LOG_API_REQUEST` | *(eredita `PERFORMANCE_LOG`)* | No | Logging | Override per i log di performance delle request API in ingresso. |
| `LOG_LEVEL_PERFORMANCE_LOG_REST_INVOKE` | *(eredita `PERFORMANCE_LOG`)* | No | Logging | Override per i log di performance delle chiamate REST uscenti. |

