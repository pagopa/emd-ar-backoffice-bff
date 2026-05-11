# Architettura BFF – AR Backoffice

## 4.2 Gestione Autenticazione e Sessione (SSO)

Il BFF adotta il pattern **BFF** combinato con il protocollo **JWT Authorization Grant (RFC 7523)** su Keycloak.

**Flusso:**

1. **Atterraggio** – L'Area Riservata reindirizza al Backoffice FE con l'Identity Token nell'hash fragment (`#token=...`).
2. **Cattura** – Il FE legge il token, pulisce l'URL e lo invia al BFF.
3. **Validazione** – Il BFF valida firma e claims del token SelfCare (`organization`, `uid`, `email`, `name`, `family_name`).
4. **JIT Provisioning** – Il BFF crea/aggiorna l'utente "ombra" su Keycloak e stabilisce il federated identity link con il provider SelfCare.
5. **Token Exchange** – Il BFF invia il token SelfCare a Keycloak (`grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer`). Keycloak valida l'assertion ed emette un token MDC.
6. **Risposta** – Il BFF restituisce al FE il token MDC insieme alle info utente.

**Alternative scartate:**

| Opzione | Motivo |
|---|---|
| Token Exchange via Azure APIM (C#) | Token esposto al FE (XSS), scarsa manutenibilità |
| ROPC Grant con password UUID | Debito tecnico, gestione password lato BFF |

---

## 4.2.1 Setup Keycloak

Versione minima richiesta: **Keycloak ≥ 26.6.0**.

**Declarative User Profile** – Censire gli attributi `org_id` e `org_role` (View/Edit per Admin e User, non obbligatori).

### Client Admin (`mdc-user-manager`)

| Parametro | Valore |
|---|---|
| Tipo | Confidential, Service Accounts Enabled |
| Ruoli Service Account | `manage-users`, `query-users` (da `realm-management`) |

### Client Backoffice (`backoffice-bff`)

| Parametro | Valore |
|---|---|
| Tipo | Confidential |
| Capability | **JWT Authorization Grant: ON** |
| Mappers | `org_id` → `organization.id`; `org_role` → `role` |

### Identity Provider SelfCare

Da creare in *Identity Providers*, tipo **JWT Authorization Grant**.

| Parametro | Valore |
|---|---|
| Alias | valore di `keycloak.idp-alias` |
| Issuer | URL issuer SelfCare |
| JWKS URL | URL chiavi pubbliche SelfCare |

> ⚠️ **Vincolo audience:** Keycloak accetta il JWT Bearer Grant solo se `aud` del token in ingresso corrisponde esattamente all'URL del Token Endpoint del realm. SelfCare deve aggiornare l'audience da `mdc.pagopa.it` all'URL fisico del Token Endpoint.

### Fix `KC_HOSTNAME`

Verificare che `token_endpoint` nel discovery endpoint (`.well-known/openid-configuration`) esponga l'URL pubblico e non quello interno.

---

## 4.2.2 Flusso di Interazione

```
FE              BFF                   Keycloak Admin            Keycloak Token
|               |                          |                          |
|-- POST /auth/token (SC token) ------->  |                          |
|               |-- valida JWT + claims    |                          |
|               |-- POST client_credentials -----------------------> |
|               |<-- manager token -----------------------------------  |
|               |-- GET /users?username=&exact=true ------------>    |
|               |-- POST|PUT /users --------------------------->     |
|               |-- POST /users/{id}/federated-identity/{idp} -->    |
|               |-- POST jwt-bearer (SC token) ------------------->  |
|               |<-- MDC token ---------------------------------------  |
|<-- AuthResponseV1 (userInfo + token) -- |                          |
```

---

## 4.2.3 Chiamate HTTP (BFF → Keycloak)

### A. Token Manager (`client_credentials`)

Il token viene **cachato in memoria** con refresh proattivo 60s prima della scadenza.

```http
POST /realms/mdc/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&client_id=mdc-user-manager&client_secret=[SECRET]
```

### B. Ricerca Utente

```http
GET /admin/realms/mdc/users?username=[UID]&exact=true
Authorization: Bearer [ADMIN_TOKEN]
```

### C. Upsert Utente

```http
POST /admin/realms/mdc/users          # creazione
PUT  /admin/realms/mdc/users/[ID]     # aggiornamento
Authorization: Bearer [ADMIN_TOKEN]
Content-Type: application/json

{
  "username": "[UID]", "email": "[EMAIL]",
  "firstName": "[NOME]", "lastName": "[COGNOME]",
  "enabled": true, "emailVerified": true,
  "attributes": { "org_id": ["[ORG_ID]"], "org_role": ["[RUOLO]"] }
}
```

> In creazione, l'ID interno Keycloak viene estratto dall'header `Location` della risposta.

### D. Federated Identity Link

Se il link esiste già, l'errore `"User is already linked with provider"` viene gestito silenziosamente.

```http
POST /admin/realms/mdc/users/[ID]/federated-identity/[IDP_ALIAS]
Authorization: Bearer [ADMIN_TOKEN]
Content-Type: application/json

{ "userId": "[UID]", "userName": "[UID]" }
```

### E. JWT Bearer Grant (token finale)

```http
POST /realms/mdc/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer
&client_id=backoffice-bff&client_secret=[SECRET]&assertion=[SC_TOKEN]
```

---

## 4.2.4 Cache Token Manager

- **Warm-up** asincrono a startup (non bloccante).
- **Refresh proattivo** 60s prima della scadenza, un solo task attivo alla volta.
- **Fallback** sincrono se il refresh proattivo fallisce.

---

## 4.2.5 Sicurezza

| Misura | Dettaglio |
|---|---|
| Nessuna password generata | Il JWT Bearer Grant elimina la gestione di password lato BFF |
| Federated Identity Link | L'utente Keycloak è legato indissolubilmente all'identità SelfCare tramite `uid` |
| Separazione dei privilegi | `mdc-user-manager` gestisce utenti; `backoffice-bff` riceve il token finale |
| Token non esposto al FE | Il token SelfCare non ritorna mai al browser |
| Validazione Gateway | APIM verifica la firma del token MDC tramite `.well-known` di Keycloak |

---

## 4.2.6 Requisiti di Deploy

| Componente | Requisito |
|---|---|
| Keycloak | ≥ 26.6.0 |
| `KC_HOSTNAME` | URL pubblico corretto nel discovery endpoint |
| SelfCare | Campo `aud` aggiornato all'URL del Token Endpoint Keycloak |

**Configurazione BFF (`application.yml`):**

```yaml
keycloak:
  auth-server-url: https://<keycloak-host>
  realm: mdc
  idp-alias: selfcare
  manager:
    client-id: mdc-user-manager
    client-secret: <secret>
  ar-backoffice:
    client-id: backoffice-bff
    client-secret: <secret>
```
