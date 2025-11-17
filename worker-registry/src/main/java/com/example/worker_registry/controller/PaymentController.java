package com.example.worker_registry.controller;

import com.example.worker_registry.Entitys.Oferta;
import com.example.worker_registry.Repository.OfertaRepository;
import com.example.worker_registry.Services.OfertaService;
import com.example.worker_registry.exceptions.StripeProcessingException;
import com.example.worker_registry.securtity.AuthenticatedUser;
import com.example.worker_registry.service.PaymentService;
import com.example.worker_registry.service.PaymentWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/payment")
public class PaymentController {

    private final PaymentService paymentService;
    private final OfertaService ofertaService;
    private final OfertaRepository ofertaRepository;
    private final PaymentWebhookService paymentWebhookService;

    public PaymentController(PaymentService paymentService,
                             OfertaService ofertaService,
                             OfertaRepository ofertaRepository,
                             PaymentWebhookService paymentWebhookService) {
        this.paymentService = paymentService;
        this.ofertaService = ofertaService;
        this.ofertaRepository = ofertaRepository;
        this.paymentWebhookService = paymentWebhookService;
    }

    @PostMapping("/create-intent")
    public ResponseEntity<?> createIntent(@RequestBody Map<String, Object> payload) {
        try {
            Map<String, Object> intent = paymentService.createPaymentIntent(payload);
            return ResponseEntity.ok(intent);
        } catch (StripeProcessingException ex) {
            return ResponseEntity.status(ex.getStatus()).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(@RequestBody Map<String, Object> payload) {
        try {
            Map<String, Object> intent = paymentService.confirmPayment(payload);
            return ResponseEntity.ok(intent);
        } catch (StripeProcessingException ex) {
            return ResponseEntity.status(ex.getStatus()).body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/status/{id}")
    public ResponseEntity<?> status(@PathVariable String id) {
        try {
            Map<String, Object> intent = paymentService.retrievePaymentStatus(id);
            return ResponseEntity.ok(intent);
        } catch (StripeProcessingException ex) {
            return ResponseEntity.status(ex.getStatus()).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/offers/{id}/accept-and-create-intent")
    public ResponseEntity<?> acceptAndCreateIntent(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @PathVariable Long id,
                                                   @RequestBody(required = false) Map<String, Object> payload) {
        Long clientId = requireClientRole(user);
        var result = ofertaService.responderOferta(clientId, id, "ACCEPT");
        if (!result.accepted()) {
            return ResponseEntity.status(409).body(Map.of(
                    "status", 409,
                    "message", "No fue posible aceptar la oferta",
                    "details", result
            ));
        }
        Optional<Oferta> ofertaOpt = ofertaRepository.findById(id);
        if (ofertaOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Oferta aceptada pero no encontrada"));
        }
        Oferta oferta = ofertaOpt.get();
        Map<String, Object> requestPayload = new HashMap<>();
        if (payload != null) {
            requestPayload.putAll(payload);
        }
        requestPayload.putIfAbsent("amount", oferta.getMontoAcordado() != null ? oferta.getMontoAcordado() : oferta.getMonto());
        requestPayload.compute("metadata", (key, value) -> {
            Map<String, Object> metadata = new HashMap<>();
            if (value instanceof Map<?, ?> existing) {
                existing.forEach((mapKey, mapValue) -> {
                    if (mapKey != null) {
                        metadata.put(mapKey.toString(), mapValue);
                    }
                });
            }
            metadata.put("ofertaId", oferta.getId());
            metadata.put("servicioId", oferta.getServicioId());
            metadata.put("trabajadorId", oferta.getTrabajador() != null ? oferta.getTrabajador().getId() : null);
            return metadata;
        });

        try {
            Map<String, Object> intent = paymentService.createPaymentIntent(requestPayload);
            updateOfferWithIntent(oferta, intent);
            return ResponseEntity.ok(Map.of(
                    "offer", result,
                    "paymentIntent", intent
            ));
        } catch (StripeProcessingException ex) {
            return ResponseEntity.status(ex.getStatus()).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(@RequestBody Map<String, Object> payload) {
        paymentWebhookService.handleEvent(payload);
        return ResponseEntity.ok(Map.of("received", true));
    }

    private void updateOfferWithIntent(Oferta oferta, Map<String, Object> intent) {
        oferta.setPaymentIntentId(stringValue(intent.get("id")));
        oferta.setPaymentClientSecret(stringValue(intent.get("clientSecret")));
        oferta.setPaymentStatus(stringValue(intent.get("status")));
        ofertaRepository.save(oferta);
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private Long requireClientRole(AuthenticatedUser user) {
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("Usuario no autenticado");
        }
        if (!user.hasRole("CLIENT")) {
            throw new org.springframework.security.access.AccessDeniedException("Rol no autorizado");
        }
        return user.userId();
    }
}
