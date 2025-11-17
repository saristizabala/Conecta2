package com.example.worker_registry.service;

import com.example.worker_registry.Entitys.Oferta;
import com.example.worker_registry.Entitys.Servicio;
import com.example.worker_registry.Entitys.EstadoNegociacion;
import com.example.worker_registry.Entitys.EstadoServicio;
import com.example.worker_registry.Repository.OfertaRepository;
import com.example.worker_registry.Repository.ServicioRepository;
import com.example.worker_registry.Services.MailService;
import com.example.worker_registry.Services.PushNotificationService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    private final OfertaRepository ofertaRepository;
    private final ServicioRepository servicioRepository;
    private final PushNotificationService pushNotificationService;
    private final MailService mailService;

    public PaymentWebhookService(OfertaRepository ofertaRepository,
                                 ServicioRepository servicioRepository,
                                 PushNotificationService pushNotificationService,
                                 MailService mailService) {
        this.ofertaRepository = ofertaRepository;
        this.servicioRepository = servicioRepository;
        this.pushNotificationService = pushNotificationService;
        this.mailService = mailService;
    }

    @Transactional
    public void handleEvent(Map<String, Object> event) {
        Map<String, Object> payload = extractObject(event);
        if (payload.isEmpty()) {
            log.warn("Webhook payload does not contain intent data");
            return;
        }
        String intentId = Optional.ofNullable(payload.get("id"))
                .map(Object::toString)
                .orElse(null);
        String status = Optional.ofNullable(payload.get("status"))
                .map(Object::toString)
                .map(String::toLowerCase)
                .orElse(null);
        if (intentId == null || status == null) {
            log.warn("Webhook missing id or status: {}", payload);
            return;
        }
        Optional<Oferta> ofertaOpt = ofertaRepository.findByPaymentIntentId(intentId);
        if (ofertaOpt.isEmpty()) {
            log.warn("No oferta found for payment intent {}", intentId);
            return;
        }
        Oferta oferta = ofertaOpt.get();
        if (oferta.getEstado() != EstadoNegociacion.ACEPTADA) {
            log.info("Ignoring webhook for oferta {} in state {}", oferta.getId(), oferta.getEstado());
            return;
        }
        oferta.setPaymentStatus(status);
        ofertaRepository.save(oferta);

        Servicio servicio = oferta.getServicio();
        if (servicio == null) {
            return;
        }
        if ("succeeded".equals(status)) {
            servicio.setEstado(EstadoServicio.ASIGNADO);
            servicioRepository.save(servicio);
            notifySuccess(servicio);
        } else if ("requires_payment_method".equals(status) || "failed".equals(status)) {
            servicio.setEstado(EstadoServicio.PENDIENTE);
            servicio.setAssignedWorkerId(null);
            servicioRepository.save(servicio);
            notifyFailure(servicio);
            revertNegotiation(oferta);
        }
    }

    private Map<String, Object> extractObject(Map<String, Object> event) {
        Object dataObj = event.get("data");
        if (dataObj instanceof Map<?, ?> data) {
            Object object = data.get("object");
            if (object instanceof Map<?, ?> objectMap) {
                Map<String, Object> safe = new LinkedHashMap<>();
                objectMap.forEach((key, value) -> {
                    if (key != null) {
                        safe.put(key.toString(), value);
                    }
                });
                return safe;
            }
        }
        if (event.containsKey("id") && event.containsKey("status")) {
            Map<String, Object> clone = new LinkedHashMap<>();
            clone.put("id", event.get("id"));
            clone.put("status", event.get("status"));
            return clone;
        }
        return Map.of();
    }

    private void notifySuccess(Servicio servicio) {
        if (servicio.getCliente() != null) {
            pushNotificationService.notifyCliente(servicio.getCliente().getId(),
                    "Pago confirmado",
                    "El pago para el servicio " + servicio.getTitulo() + " fue confirmado.");
            mailService.send(servicio.getCliente().getCorreo(),
                    "Pago confirmado en Conecta2",
                    "El pago para el servicio " + servicio.getTitulo() + " fue registrado con éxito.");
        }
    }

    private void notifyFailure(Servicio servicio) {
        if (servicio.getCliente() != null) {
            pushNotificationService.notifyCliente(servicio.getCliente().getId(),
                    "Pago rechazado",
                    "Hubo un problema al procesar el pago del servicio " + servicio.getTitulo() + ". Intenta de nuevo.");
            mailService.send(servicio.getCliente().getCorreo(),
                    "Pago rechazado en Conecta2",
                    "Hubo un problema al procesar el pago del servicio " + servicio.getTitulo() + ".");
        }
    }

    private void revertNegotiation(Oferta oferta) {
        oferta.setEstado(EstadoNegociacion.EN_NEGOCIACION);
        oferta.setUltimaPropuestaPor(com.example.worker_registry.Entitys.ParticipanteOferta.TRABAJADOR);
        ofertaRepository.save(oferta);
    }
}
