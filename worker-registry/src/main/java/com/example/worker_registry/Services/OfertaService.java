package com.example.worker_registry.Services;

import com.example.worker_registry.Entitys.EstadoNegociacion;
import com.example.worker_registry.Entitys.EstadoServicio;
import com.example.worker_registry.Entitys.Oferta;
import com.example.worker_registry.Entitys.ParticipanteOferta;
import com.example.worker_registry.Entitys.Servicio;
import com.example.worker_registry.Repository.OfertaRepository;
import com.example.worker_registry.Repository.ServicioRepository;
import com.example.worker_registry.Services.PushNotificationService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class OfertaService {

    private final OfertaRepository ofertaRepository;
    private final ServicioRepository servicioRepository;
    private final PushNotificationService pushNotificationService;
    private final PaymentIntegrationService paymentIntegrationService;

    public OfertaService(OfertaRepository ofertaRepository,
                         ServicioRepository servicioRepository,
                         PushNotificationService pushNotificationService,
                         PaymentIntegrationService paymentIntegrationService) {
        this.ofertaRepository = ofertaRepository;
        this.servicioRepository = servicioRepository;
        this.pushNotificationService = pushNotificationService;
        this.paymentIntegrationService = paymentIntegrationService;
    }

    public List<Oferta> listarPendientesCliente(Long clienteId) {
        var ofertas = ofertaRepository.findByServicio_Cliente_IdAndServicio_EstadoAndEstadoAndUltimaPropuestaPorOrderByActualizadoEnDesc(
                clienteId,
                EstadoServicio.PENDIENTE,
                EstadoNegociacion.EN_NEGOCIACION,
                ParticipanteOferta.TRABAJADOR
        );
        return filtrarOfertasConServicioVigente(ofertas);
    }

    public List<Oferta> listarPendientesTrabajador(Long trabajadorId) {
        var ofertas = ofertaRepository.findByTrabajador_IdAndServicio_EstadoAndEstadoAndUltimaPropuestaPorOrderByActualizadoEnDesc(
                trabajadorId,
                EstadoServicio.PENDIENTE,
                EstadoNegociacion.EN_NEGOCIACION,
                ParticipanteOferta.CLIENTE
        );
        return filtrarOfertasConServicioVigente(ofertas);
    }

    @Transactional
    public ResultadoRespuesta responderOferta(Long clientId, Long ofertaId, ResponderOferta body) {
        String resolvedAction = resolveAction(body);
        return responderOferta(clientId, ofertaId, resolvedAction);
    }

    @Transactional
    public ResultadoRespuesta responderOferta(Long clientId, Long ofertaId, String action) {
        Oferta oferta = ofertaRepository.findById(ofertaId)
                .orElseThrow(() -> new EntityNotFoundException("Oferta no encontrada"));

        Servicio servicio = oferta.getServicio();
        validarClientePropietario(clientId, servicio);
        validarServicioPendiente(servicio);

        if (oferta.getEstado() != EstadoNegociacion.EN_NEGOCIACION) {
            throw new IllegalStateException("La oferta ya no se encuentra disponible para respuesta");
        }
        if (oferta.getUltimaPropuestaPor() != ParticipanteOferta.TRABAJADOR) {
            throw new IllegalStateException("Solo puedes aceptar o rechazar ofertas activas del trabajador");
        }

        String normalizedAction = normalizeAction(action);
        if ("ACCEPT".equals(normalizedAction)) {
            return aceptarOferta(oferta, "Oferta aceptada");
        }
        if (normalizedAction == null || "REJECT".equals(normalizedAction)) {
            oferta.setEstado(EstadoNegociacion.RECHAZADA);
            ofertaRepository.save(oferta);
            return ResultadoRespuesta.sinPago("Oferta rechazada", oferta, false, oferta.getPaymentStatus());
        }
        throw new IllegalArgumentException("Acción no soportada: " + action);
    }

    @Transactional
    public Oferta contraOfertaCliente(Long clientId, Long ofertaId, ContraOferta data) {
        if (data == null || data.monto == null) {
            throw new IllegalArgumentException("El monto es obligatorio");
        }
        if (data.monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a 0");
        }

        Oferta oferta = ofertaRepository.findById(ofertaId)
                .orElseThrow(() -> new EntityNotFoundException("Oferta no encontrada"));
        Servicio servicio = oferta.getServicio();

        validarClientePropietario(clientId, servicio);
        validarServicioPendiente(servicio);

        if (oferta.getEstado() != EstadoNegociacion.EN_NEGOCIACION) {
            throw new IllegalStateException("Esta negociacion ya fue cerrada");
        }
        if (oferta.getUltimaPropuestaPor() != ParticipanteOferta.TRABAJADOR) {
            throw new IllegalStateException("Ya enviaste una contraoferta, espera la respuesta del trabajador");
        }

        oferta.setMonto(data.monto);
        oferta.setMontoCliente(data.monto);
        oferta.setUltimaPropuestaPor(ParticipanteOferta.CLIENTE);
        oferta.setMensaje(data.mensaje);

        return ofertaRepository.save(oferta);
    }

    @Transactional
    public Oferta contraOfertaTrabajador(Long trabajadorId, Long ofertaId, ContraOferta data) {
        if (data == null || data.monto == null) {
            throw new IllegalArgumentException("El monto es obligatorio");
        }
        if (data.monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a 0");
        }

        Oferta oferta = ofertaRepository.findById(ofertaId)
                .orElseThrow(() -> new EntityNotFoundException("Oferta no encontrada"));
        validarTrabajadorPropietario(trabajadorId, oferta);

        Servicio servicio = oferta.getServicio();
        validarServicioPendiente(servicio);

        if (oferta.getEstado() != EstadoNegociacion.EN_NEGOCIACION) {
            throw new IllegalStateException("Esta negociacion ya fue cerrada");
        }
        if (oferta.getUltimaPropuestaPor() != ParticipanteOferta.CLIENTE) {
            throw new IllegalStateException("Ya enviaste una contraoferta, espera la respuesta del cliente");
        }

        oferta.setMonto(data.monto);
        oferta.setMontoTrabajador(data.monto);
        oferta.setUltimaPropuestaPor(ParticipanteOferta.TRABAJADOR);
        oferta.setMensaje(data.mensaje);

        return ofertaRepository.save(oferta);
    }

    @Transactional
    public ResultadoRespuesta responderOfertaTrabajador(Long trabajadorId, Long ofertaId, ResponderOferta body) {
        String resolvedAction = resolveAction(body);
        return responderOfertaTrabajador(trabajadorId, ofertaId, resolvedAction);
    }

    @Transactional
    public ResultadoRespuesta responderOfertaTrabajador(Long trabajadorId, Long ofertaId, String action) {
        Oferta oferta = ofertaRepository.findById(ofertaId)
                .orElseThrow(() -> new EntityNotFoundException("Oferta no encontrada"));

        validarTrabajadorPropietario(trabajadorId, oferta);

        Servicio servicio = oferta.getServicio();
        validarServicioPendiente(servicio);

        if (oferta.getEstado() != EstadoNegociacion.EN_NEGOCIACION) {
            throw new IllegalStateException("Esta negociacion ya fue cerrada");
        }
        if (oferta.getUltimaPropuestaPor() != ParticipanteOferta.CLIENTE) {
            throw new IllegalStateException("No hay una contraoferta del cliente por responder");
        }

        String normalizedAction = normalizeAction(action);
        if ("ACCEPT".equals(normalizedAction)) {
            return aceptarOferta(oferta, "Contraoferta aceptada");
        }

        oferta.setEstado(EstadoNegociacion.RECHAZADA);
        ofertaRepository.save(oferta);
        return ResultadoRespuesta.sinPago("Contraoferta rechazada", oferta, false, oferta.getPaymentStatus());
    }

    private void validarClientePropietario(Long clientId, Servicio servicio) {
        if (servicio == null || servicio.getCliente() == null || !clientId.equals(servicio.getCliente().getId())) {
            throw new SecurityException("No tienes permiso para responder esta oferta");
        }
    }

    private void validarTrabajadorPropietario(Long workerId, Oferta oferta) {
        if (oferta.getTrabajador() == null || oferta.getTrabajador().getId() == null
                || !workerId.equals(oferta.getTrabajador().getId())) {
            throw new SecurityException("No puedes responder ofertas de otro trabajador");
        }
    }

    private void validarServicioPendiente(Servicio servicio) {
        if (servicio == null || servicio.getEstado() != EstadoServicio.PENDIENTE) {
            throw new IllegalStateException("Solo puedes negociar sobre servicios PENDIENTES");
        }
        if (marcarServicioComoVencido(servicio)) {
            throw new IllegalStateException("El servicio ya expiro");
        }
    }

    private ResultadoRespuesta aceptarOferta(Oferta oferta, String mensaje) {
        Oferta ofertaActualizada = paymentIntegrationService.iniciarPago(oferta);
        Servicio servicio = ofertaActualizada.getServicio();

        if (servicio != null && servicio.getEstado() == EstadoServicio.PENDIENTE_PAGO) {
            notificarClientePagoPendiente(servicio);
            return ResultadoRespuesta.conPagoPendiente(servicio, ofertaActualizada);
        }
        return ResultadoRespuesta.sinPago("Oferta aceptada y servicio asignado sin pago en linea", ofertaActualizada);
    }

    private void notificarClientePagoPendiente(Servicio servicio) {
        if (servicio == null || servicio.getCliente() == null) {
            return;
        }
        pushNotificationService.notifyCliente(servicio.getCliente().getId(),
                "Pago pendiente",
                "Tu servicio " + servicio.getTitulo() + " esta a la espera de pago.");
    }

    private List<Oferta> filtrarOfertasConServicioVigente(List<Oferta> ofertas) {
        LocalDate hoy = LocalDate.now();
        List<Oferta> vigentes = new ArrayList<>();
        List<Servicio> expirados = new ArrayList<>();
        for (Oferta oferta : ofertas) {
            Servicio servicio = oferta.getServicio();
            if (servicio == null) {
                continue;
            }
            if (servicio.getEstado() != EstadoServicio.PENDIENTE) {
                continue;
            }
            if (servicio.getFechaEstimada() != null && servicio.getFechaEstimada().toLocalDate().isBefore(hoy)) {
                servicio.setEstado(EstadoServicio.CANCELADO);
                expirados.add(servicio);
                continue;
            }
            vigentes.add(oferta);
        }
        if (!expirados.isEmpty()) {
            servicioRepository.saveAll(expirados);
        }
        return vigentes;
    }

    private boolean marcarServicioComoVencido(Servicio servicio) {
        if (servicio.getFechaEstimada() == null) {
            return false;
        }
        if (servicio.getFechaEstimada().toLocalDate().isBefore(LocalDate.now())) {
            servicio.setEstado(EstadoServicio.CANCELADO);
            servicioRepository.save(servicio);
            return true;
        }
        return false;
    }

    public static class ResponderOferta {
        public String action; // EXPECTED: ACCEPT or REJECT
        public Boolean accept; // true = aceptar, false/null = rechazar
    }

    public static class ContraOferta {
        public BigDecimal monto;
        public String mensaje;
    }

    public record ResultadoRespuesta(
            String mensaje,
            boolean accepted,
            Servicio servicio,
            Long offerId,
            Long serviceId,
            String serviceTitle,
            BigDecimal amount,
            String currency,
            String paymentIntentId,
            String paymentClientSecret,
            com.example.worker_registry.Entitys.PaymentStatus paymentStatus
    ) {
        public static ResultadoRespuesta conPagoPendiente(Servicio servicio, Oferta oferta) {
            return new ResultadoRespuesta(
                    "Pago pendiente de confirmacion",
                    true,
                    servicio,
                    oferta.getId(),
                    servicio != null ? servicio.getId() : null,
                    servicio != null ? servicio.getTitulo() : null,
                    oferta.getMonto(),
                    "MXN",
                    oferta.getPaymentIntentId(),
                    oferta.getPaymentClientSecret(),
                    oferta.getPaymentStatus()
            );
        }

        public static ResultadoRespuesta sinPago(String mensaje, Oferta oferta) {
            return sinPago(mensaje, oferta, true, oferta.getPaymentStatus());
        }

        public static ResultadoRespuesta sinPago(String mensaje, Oferta oferta, boolean accepted, com.example.worker_registry.Entitys.PaymentStatus status) {
            Servicio servicio = oferta.getServicio();
            return new ResultadoRespuesta(
                    mensaje,
                    accepted,
                    servicio,
                    oferta.getId(),
                    servicio != null ? servicio.getId() : null,
                    servicio != null ? servicio.getTitulo() : null,
                    oferta.getMonto(),
                    "MXN",
                    oferta.getPaymentIntentId(),
                    oferta.getPaymentClientSecret(),
                    status
            );
        }
    }

    private String normalizeAction(String action) {
        if (action == null) return null;
        var trimmed = action.trim().toUpperCase();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveAction(ResponderOferta body) {
        if (body == null) {
            return null;
        }
        if (body.action != null && !body.action.isBlank()) {
            return body.action;
        }
        if (body.accept != null) {
            return body.accept ? "ACCEPT" : "REJECT";
        }
        return null;
    }
}
