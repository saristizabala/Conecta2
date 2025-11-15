## Conecta2 – Worker Registry

Servicio Spring Boot que gestiona el registro de clientes/trabajadores, publicación de servicios y negociación de ofertas. Esta rama integra al microservicio externo **PasarelaPagos** para simular cobros.

### Integración con PasarelaPagos

- **Cliente HTTP**: `PaymentGatewayClient` usa `RestClient` con *timeouts* configurables y reintentos controlados (`payments.*` en `application.properties`). Inyecta `X-API-KEY` en cada request contra `/payments/intents`, `/payments/intents/{id}`, `/payments/intents/{id}/confirm`.
- **Persistencia**: `Oferta` guarda `paymentIntentId`, `paymentClientSecret`, `paymentStatus` y `paymentMetadata`. Los servicios aceptados quedan en estado `PENDIENTE_PAGO` hasta que la pasarela confirme.
- **Orquestación**: `PaymentIntegrationService` crea intents al aceptar/cerrar negociaciones, escucha webhooks (`POST /api/v1/payments/webhook`) y actualiza `EstadoServicio`:
  - `SUCCEEDED` → servicio `ASIGNADO`, se notifican cliente y trabajador vía `PushNotificationService`/`MailService`.
  - `FAILED` → servicio vuelve a `PENDIENTE`, la oferta vuelve a `EN_NEGOCIACION`.
- **Endpoints expuestos**:
  - `GET /api/v1/payments/offers/{offerId}` → entrega `paymentIntentId`, `clientSecret` y `paymentStatus` al cliente autenticado (query `?refresh=true` vuelve a consultar a la pasarela).
  - `POST /api/v1/payments/offers/{offerId}/refresh` → fuerza un poll manual.
  - `POST /api/v1/payments/webhook` → usado por la pasarela simulada, protegido con header `X-WEBHOOK-SECRET`.

### Configuración requerida

En `application.properties`:

```properties
payments.base-url=http://localhost:8090
payments.api-key=local-gateway-key
payments.webhook-secret=local-webhook-secret
payments.connect-timeout=2s
payments.read-timeout=4s
payments.max-retries=3
payments.retry-statuses=500,502,503
```

En la pasarela fake deberás configurar:

- `POST /payments/intents` para recibir `externalRef`, `amount`, `currency`, `description`, `metadata`.
- `POST /payments/intents/{id}/confirm` para confirmar pagos desde el frontend usando `clientSecret`.
- `GET /payments/intents/{id}` para *polling*.
- `POST /webhooks/test` (o endpoint equivalente) que reenvíe eventos a `worker-registry` con header `X-WEBHOOK-SECRET`.

### Flujo resumido

1. Cliente acepta una oferta → `OfertaService` invoca `PaymentIntegrationService.iniciarPago`.
2. Se crea un intent (`PaymentGatewayClient.createIntent`) y se guarda `clientSecret`. El servicio pasa a `PENDIENTE_PAGO`.
3. El frontend consulta `GET /api/v1/payments/offers/{id}` para obtener `clientSecret` y llama a `POST /payments/intents/{id}/confirm` en PasarelaPagos.
4. La pasarela envía webhook cuando el estado cambia:
   - `SUCCEEDED`: el servicio queda `ASIGNADO`, se cierran otras ofertas y se notifican las partes.
   - `FAILED`: se limpia el intent y la negociación se reabre.
5. Si el webhook se pierde, el frontend puede `refresh=true` para que el backend vuelva a consultar el intent remoto.

### Pruebas

Se añadieron pruebas unitarias (`PaymentIntegrationServiceTest`, `OfertaServiceTest`) usando mocks del gateway. `src/test/resources/application.properties` levanta una base H2 y desactiva correos para test.
