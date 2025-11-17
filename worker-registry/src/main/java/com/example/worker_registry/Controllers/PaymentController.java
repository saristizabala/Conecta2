package com.example.worker_registry.Controllers;

import com.example.worker_registry.Config.PaymentGatewayProperties;
import com.example.worker_registry.Entitys.Oferta;
import com.example.worker_registry.Entitys.PaymentStatus;
import com.example.worker_registry.Repository.OfertaRepository;
import com.example.worker_registry.Services.PaymentIntegrationService;
import com.example.worker_registry.Services.payments.PaymentGatewayClient;
import com.example.worker_registry.securtity.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final OfertaRepository ofertaRepository;
    private final PaymentIntegrationService paymentIntegrationService;
    private final PaymentGatewayProperties paymentGatewayProperties;

    public PaymentController(OfertaRepository ofertaRepository,
                             PaymentIntegrationService paymentIntegrationService,
                             PaymentGatewayProperties paymentGatewayProperties) {
        this.ofertaRepository = ofertaRepository;
        this.paymentIntegrationService = paymentIntegrationService;
        this.paymentGatewayProperties = paymentGatewayProperties;
    }

    @GetMapping("/offers/{offerId}")
    public ResponseEntity<PaymentInfoDto> obtenerPago(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long offerId,
            @RequestParam(name = "refresh", defaultValue = "false") boolean refresh) {
        Oferta oferta = ofertaRepository.findById(offerId)
                .orElseThrow(() -> new IllegalArgumentException("Oferta no encontrada"));
        validarClientePropietario(user, oferta);
        if (refresh && oferta.getPaymentIntentId() != null && !oferta.getPaymentStatus().isFinal()) {
            oferta = paymentIntegrationService.refreshFromGateway(offerId);
        }
        return ResponseEntity.ok(PaymentInfoDto.from(oferta));
    }

    @PostMapping("/offers/{offerId}/refresh")
    public ResponseEntity<PaymentInfoDto> refrescarPago(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long offerId) {
        Oferta oferta = ofertaRepository.findById(offerId)
                .orElseThrow(() -> new IllegalArgumentException("Oferta no encontrada"));
        validarClientePropietario(user, oferta);
        oferta = paymentIntegrationService.refreshFromGateway(offerId);
        return ResponseEntity.ok(PaymentInfoDto.from(oferta));
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> recibirWebhook(
            @RequestHeader(name = "X-WEBHOOK-SECRET", required = false) String secret,
            @RequestBody PaymentWebhookPayload payload) {
        if (secret == null || !secret.equals(paymentGatewayProperties.getWebhookSecret())) {
            throw new AccessDeniedException("Firma de webhook invalida");
        }
        if (payload.intentId == null || payload.intentId.isBlank()) {
            throw new IllegalArgumentException("intentId obligatorio");
        }
        var response = new PaymentGatewayClient.PaymentIntentResponse(
                payload.intentId,
                payload.status,
                payload.clientSecret,
                payload.externalRef,
                payload.amount,
                payload.currency,
                payload.metadata
        );
        paymentIntegrationService.procesarWebhook(payload.intentId, response);
        return ResponseEntity.ok(Map.of(
                "status", "received",
                "intentId", payload.intentId
        ));
    }

    private void validarClientePropietario(AuthenticatedUser user, Oferta oferta) {
        if (user == null || !user.hasRole("CLIENT")) {
            throw new AccessDeniedException("Rol de cliente requerido");
        }
        Long expectedClient = oferta.getServicio() != null && oferta.getServicio().getCliente() != null
                ? oferta.getServicio().getCliente().getId() : null;
        if (expectedClient != null && !expectedClient.equals(user.userId())) {
            throw new AccessDeniedException("No puedes acceder a este pago");
        }
    }

    public record PaymentInfoDto(
            Long offerId,
            Long serviceId,
            String serviceTitle,
            java.math.BigDecimal amount,
            String currency,
            String paymentIntentId,
            String paymentClientSecret,
            PaymentStatus paymentStatus) {
        public static PaymentInfoDto from(Oferta oferta) {
            Long serviceId = oferta.getServicio() != null ? oferta.getServicio().getId() : null;
            String title = oferta.getServicio() != null ? oferta.getServicio().getTitulo() : null;
            return new PaymentInfoDto(
                    oferta.getId(),
                    serviceId,
                    title,
                    oferta.getMonto(),
                    "MXN",
                    oferta.getPaymentIntentId(),
                    oferta.getPaymentClientSecret(),
                    oferta.getPaymentStatus()
            );
        }
    }

    public static class PaymentWebhookPayload {
        public String intentId;
        public String status;
        public String clientSecret;
        public String externalRef;
        public BigDecimal amount;
        public String currency;
        public Map<String, Object> metadata;
    }
}
