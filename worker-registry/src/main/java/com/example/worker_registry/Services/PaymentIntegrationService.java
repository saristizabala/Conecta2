package com.example.worker_registry.Services;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import com.example.worker_registry.Entitys.EstadoNegociacion;
import com.example.worker_registry.Entitys.EstadoServicio;
import com.example.worker_registry.Entitys.Oferta;
import com.example.worker_registry.Entitys.PaymentStatus;
import com.example.worker_registry.Entitys.Servicio;
import com.example.worker_registry.Repository.OfertaRepository;
import com.example.worker_registry.Repository.ServicioRepository;
import com.example.worker_registry.Services.payments.PaymentGatewayClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PaymentIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentIntegrationService.class);

    private final PaymentGatewayClient paymentGatewayClient;
    private final OfertaRepository ofertaRepository;
    private final ServicioRepository servicioRepository;
    private final PushNotificationService pushNotificationService;
    private final MailService mailService;
    private final ObjectMapper objectMapper;

    public PaymentIntegrationService(PaymentGatewayClient paymentGatewayClient,
                                     OfertaRepository ofertaRepository,
                                     ServicioRepository servicioRepository,
                                     PushNotificationService pushNotificationService,
                                     MailService mailService,
                                     ObjectMapper objectMapper) {
        this.paymentGatewayClient = paymentGatewayClient;
        this.ofertaRepository = ofertaRepository;
        this.servicioRepository = servicioRepository;
        this.pushNotificationService = pushNotificationService;
        this.mailService = mailService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Oferta iniciarPago(Oferta oferta) {
        try {
            return iniciarPagoEnPasarela(oferta);
        } catch (RestClientException ex) {
            log.warn("[Payment] Pasarela de pagos no disponible o respondio con error, se asignara sin cobro remoto", ex);
            return aceptarSinPasarela(oferta);
        } catch (RuntimeException ex) {
            // Salvaguarda final para evitar que la aceptacion devuelva 500 por fallas no previstas
            log.error("[Payment] Error inesperado al iniciar pago, se asigna sin pasarela", ex);
            return aceptarSinPasarela(oferta);
        }
    }

    private Oferta iniciarPagoEnPasarela(Oferta oferta) {
        Servicio servicio = oferta.getServicio();
        var request = new PaymentGatewayClient.PaymentIntentRequest(
                String.valueOf(oferta.getId()),
                oferta.getMonto(),
                "MXN",
                servicio.getTitulo(),
                buildMetadata(oferta)
        );

        var response = paymentGatewayClient.createIntent(request);
        applyGatewaySnapshot(oferta, response);

        oferta.setEstado(EstadoNegociacion.ACEPTADA);
        oferta.setMontoAcordado(oferta.getMonto());
        ofertaRepository.save(oferta);

        servicio.setEstado(EstadoServicio.PENDIENTE_PAGO);
        if (oferta.getTrabajador() != null) {
            servicio.setAssignedWorkerId(oferta.getTrabajador().getId());
        }
        servicioRepository.save(servicio);

        return oferta;
    }

    private Oferta aceptarSinPasarela(Oferta oferta) {
        Servicio servicio = oferta.getServicio();

        oferta.setEstado(EstadoNegociacion.ACEPTADA);
        oferta.setMontoAcordado(oferta.getMonto());
        oferta.setPaymentStatus(PaymentStatus.NOT_REQUIRED);
        oferta.setPaymentIntentId(null);
        oferta.setPaymentClientSecret(null);
        ofertaRepository.save(oferta);

        servicio.setEstado(EstadoServicio.ASIGNADO);
        if (oferta.getTrabajador() != null) {
            servicio.setAssignedWorkerId(oferta.getTrabajador().getId());
        }
        servicioRepository.save(servicio);

        cerrarOtrasOfertas(servicio, oferta.getId());
        log.info("[Payment] Oferta {} aceptada sin pasarela; servicio {} asignado manualmente", oferta.getId(), servicio.getId());
        return oferta;
    }

    private Map<String, Object> buildMetadata(Oferta oferta) {
        Servicio servicio = oferta.getServicio();
        Long serviceId = servicio != null ? servicio.getId() : null;
        Long clientId = servicio != null && servicio.getCliente() != null ? servicio.getCliente().getId() : null;
        Long workerId = oferta.getTrabajador() != null ? oferta.getTrabajador().getId() : null;
        java.util.Map<String, Object> metadata = new java.util.HashMap<>();
        if (serviceId != null) metadata.put("serviceId", serviceId);
        if (oferta.getId() != null) metadata.put("offerId", oferta.getId());
        if (clientId != null) metadata.put("clientId", clientId);
        if (workerId != null) metadata.put("workerId", workerId);
        return metadata;
    }

    @Transactional
    public void procesarWebhook(String paymentIntentId, PaymentGatewayClient.PaymentIntentResponse payload) {
        Optional<Oferta> ofertaOpt = ofertaRepository.findByPaymentIntentId(paymentIntentId);
        if (ofertaOpt.isEmpty()) {
            log.warn("[PaymentWebhook] No se encontro oferta asociada a intent {}", paymentIntentId);
            return;
        }
        Oferta oferta = ofertaOpt.get();
        applyGatewaySnapshot(oferta, payload);
        ofertaRepository.save(oferta);

        Servicio servicio = oferta.getServicio();
        if (servicio == null) {
            return;
        }

        if (oferta.getPaymentStatus() == PaymentStatus.SUCCEEDED) {
            completarServicio(oferta, servicio);
        } else if (oferta.getPaymentStatus() == PaymentStatus.FAILED) {
            revertirServicio(oferta, servicio);
        }
    }

    @Transactional
    public Oferta refreshFromGateway(Long ofertaId) {
        Oferta oferta = ofertaRepository.findById(ofertaId)
                .orElseThrow(() -> new IllegalArgumentException("Oferta no encontrada"));
        if (oferta.getPaymentIntentId() == null) {
            return oferta;
        }
        var response = paymentGatewayClient.retrieveIntent(oferta.getPaymentIntentId());
        applyGatewaySnapshot(oferta, response);
        ofertaRepository.save(oferta);

        Servicio servicio = oferta.getServicio();
        if (servicio != null) {
            if (oferta.getPaymentStatus() == PaymentStatus.SUCCEEDED) {
                completarServicio(oferta, servicio);
            } else if (oferta.getPaymentStatus() == PaymentStatus.FAILED) {
                revertirServicio(oferta, servicio);
            }
        }
        return oferta;
    }

    private void completarServicio(Oferta oferta, Servicio servicio) {
        servicio.setEstado(EstadoServicio.ASIGNADO);
        servicioRepository.save(servicio);

        cerrarOtrasOfertas(servicio, oferta.getId());
        notificarPagoExitoso(servicio);
        log.info("[Payment] Pago exitoso para servicio {} (oferta {})", servicio.getId(), oferta.getId());
    }

    private void revertirServicio(Oferta oferta, Servicio servicio) {
        servicio.setEstado(EstadoServicio.PENDIENTE);
        servicio.setAssignedWorkerId(null);
        servicioRepository.save(servicio);

        oferta.setEstado(EstadoNegociacion.EN_NEGOCIACION);
        oferta.setUltimaPropuestaPor(com.example.worker_registry.Entitys.ParticipanteOferta.TRABAJADOR);
        oferta.setPaymentClientSecret(null);
        oferta.setPaymentIntentId(null);
        ofertaRepository.save(oferta);

        log.info("[Payment] Pago fallido para servicio {}. Negociacion reabierta.", servicio.getId());
        notificarPagoFallido(servicio);
    }

    private void cerrarOtrasOfertas(Servicio servicio, Long aceptadaOfertaId) {
        var otras = ofertaRepository.findByServicio_Id(servicio.getId());
        var pendientes = otras.stream()
                .filter(o -> o.getId() != null
                        && !o.getId().equals(aceptadaOfertaId))
                .toList();
        if (!pendientes.isEmpty()) {
            pendientes.forEach(o -> {
                if (o.getEstado() == EstadoNegociacion.EN_NEGOCIACION) {
                    o.setEstado(EstadoNegociacion.RECHAZADA);
                }
                o.setPaymentIntentId(null);
                o.setPaymentClientSecret(null);
            });
            ofertaRepository.saveAll(pendientes);
        }
    }

    private void notificarPagoExitoso(Servicio servicio) {
        if (servicio.getCliente() != null) {
            pushNotificationService.notifyCliente(servicio.getCliente().getId(),
                    "Pago confirmado",
                    "Tu pago para el servicio " + servicio.getTitulo() + " fue confirmado.");
            mailService.send(servicio.getCliente().getCorreo(),
                    "Pago confirmado en Conecta2",
                    "El pago para el servicio " + servicio.getTitulo() + " fue registrado con éxito.");
        }
    }

    private void notificarPagoFallido(Servicio servicio) {
        if (servicio.getCliente() != null) {
            pushNotificationService.notifyCliente(servicio.getCliente().getId(),
                    "Pago rechazado",
                    "El pago para el servicio " + servicio.getTitulo() + " no pudo completarse. Intenta de nuevo.");
            mailService.send(servicio.getCliente().getCorreo(),
                    "Pago rechazado en Conecta2",
                    "Hubo un problema al procesar el pago del servicio " + servicio.getTitulo());
        }
    }

    private void applyGatewaySnapshot(Oferta oferta, PaymentGatewayClient.PaymentIntentResponse response) {
        oferta.setPaymentIntentId(response.id());
        oferta.setPaymentClientSecret(response.clientSecret());
        oferta.setPaymentStatus(PaymentStatus.fromGatewayStatus(response.status()));
        oferta.setPaymentMetadata(writeMetadata(response.safeMetadata()));
    }

    private String writeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("No se pudo serializar metadata de pago", e);
            return null;
        }
    }
}
