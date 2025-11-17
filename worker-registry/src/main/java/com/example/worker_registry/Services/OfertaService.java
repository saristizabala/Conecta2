package com.example.worker_registry.Services;

import com.example.worker_registry.Entitys.EstadoNegociacion;
import com.example.worker_registry.Entitys.EstadoServicio;
import com.example.worker_registry.Entitys.Oferta;
import com.example.worker_registry.Entitys.ParticipanteOferta;
import com.example.worker_registry.Entitys.Servicio;
import com.example.worker_registry.Repository.OfertaRepository;
import com.example.worker_registry.Repository.ServicioRepository;
import com.example.worker_registry.Services.PushNotificationService;
import com.example.worker_registry.service.PaymentService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OfertaService {

    private final OfertaRepository ofertaRepository;
    private final ServicioRepository servicioRepository;
    private final PushNotificationService pushNotificationService;
    private final PaymentService paymentService;

    public OfertaService(OfertaRepository ofertaRepository,
                         ServicioRepository servicioRepository,
                         PushNotificationService pushNotificationService,
                         PaymentService paymentService) {
        this.ofertaRepository = ofertaRepository;
        this.servicioRepository = servicioRepository;
        this.pushNotificationService = pushNotificationService;
        this.paymentService = paymentService;
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
            return new ResultadoRespuesta("Oferta rechazada", false, null);
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
        return new ResultadoRespuesta("Contraoferta rechazada", false, null);
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
        Servicio servicio = oferta.getServicio();
        if (servicio != null) {
            servicio.setEstado(EstadoServicio.PENDIENTE);
            if (oferta.getTrabajador() != null && oferta.getTrabajador().getId() != null) {
                servicio.setAssignedWorkerId(oferta.getTrabajador().getId());
            }
            servicioRepository.save(servicio);
        }
        oferta.setEstado(EstadoNegociacion.ACEPTADA);
        oferta.setMontoAcordado(oferta.getMonto());
        Map<String, Object> intent = crearIntentParaOferta(oferta);
        guardarIntentEnOferta(oferta, intent);
        ofertaRepository.save(oferta);
        String respuesta = mensaje != null ? mensaje : "Oferta aceptada";
        if (servicio != null) {
            notificarClientePendientePago(servicio);
        }
        return new ResultadoRespuesta(respuesta, true, servicio);
    }

    private void notificarClienteServicioAsignado(Servicio servicio) {
        if (servicio == null || servicio.getCliente() == null) {
            return;
        }
        pushNotificationService.notifyCliente(servicio.getCliente().getId(),
                "Servicio asignado",
                "Tu servicio " + servicio.getTitulo() + " fue aceptado y el trabajador ya está asignado.");
    }

    private void notificarClientePendientePago(Servicio servicio) {
        if (servicio == null || servicio.getCliente() == null) {
            return;
        }
        pushNotificationService.notifyCliente(servicio.getCliente().getId(),
                "Pago pendiente",
                "Tu servicio " + servicio.getTitulo() + " está pendiente de pago.");
    }

    private Map<String, Object> crearIntentParaOferta(Oferta oferta) {
        BigDecimal monto = oferta.getMonto();
        Servicio servicio = oferta.getServicio();
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("amount", montoACentavos(monto));
        if (servicio != null) {
            payload.put("description", servicio.getTitulo());
            payload.put("metadata", Map.of(
                    "offerId", oferta.getId(),
                    "serviceId", servicio.getId()
            ));
        } else {
            payload.put("description", "Servicio en Conecta2");
            payload.put("metadata", Map.of("offerId", oferta.getId()));
        }
        payload.put("payment_method_types", java.util.List.of("card"));
        return paymentService.createPaymentIntent(payload);
    }

    private long montoACentavos(BigDecimal monto) {
        if (monto == null) return 0L;
        return monto.multiply(BigDecimal.valueOf(100)).longValue();
    }

    private void guardarIntentEnOferta(Oferta oferta, Map<String, Object> intent) {
        if (intent == null || intent.isEmpty()) return;
        oferta.setPaymentIntentId(getString(intent.get("id")));
        oferta.setPaymentClientSecret(getString(intent.get("clientSecret")));
        oferta.setPaymentStatus(getString(intent.get("status")));
    }

    private String getString(Object value) {
        return value == null ? null : value.toString();
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

    public void actualizarEstadoPago(Map<String, Object> intent) {
        if (intent == null || intent.isEmpty()) return;
        String intentId = getString(intent.get("id"));
        Long offerId = extractOfferId(intent);
        Optional<Oferta> opt = findOferta(intentId, offerId);
        if (opt.isEmpty()) return;
        Oferta oferta = opt.get();
        guardarIntentEnOferta(oferta, intent);
        String status = Optional.ofNullable(intent.get("status"))
                .map(Object::toString)
                .map(String::toUpperCase)
                .orElse(null);
        if (status != null) {
            oferta.setPaymentStatus(status);
        }
        Servicio servicio = oferta.getServicio();
        Long workerId = oferta.getTrabajador() != null ? oferta.getTrabajador().getId() : null;
        if (servicio != null) {
            if ("SUCCEEDED".equals(status)) {
                servicio.setEstado(EstadoServicio.ASIGNADO);
                if (workerId != null) {
                    servicio.setAssignedWorkerId(workerId);
                }
                servicioRepository.save(servicio);
                notificarClienteServicioAsignado(servicio);
            } else {
                servicio.setEstado(EstadoServicio.PENDIENTE);
                servicioRepository.save(servicio);
            }
        }
        ofertaRepository.save(oferta);
    }

    private Optional<Oferta> findOferta(String intentId, Long offerId) {
        if (intentId != null) {
            Optional<Oferta> byIntent = ofertaRepository.findByPaymentIntentId(intentId);
            if (byIntent.isPresent()) {
                return byIntent;
            }
        }
        if (offerId != null) {
            return ofertaRepository.findById(offerId);
        }
        return Optional.empty();
    }

    private Long extractOfferId(Map<String, Object> intent) {
        Object metadata = intent.get("metadata");
        if (metadata instanceof Map<?, ?> meta) {
            Object raw = meta.get("offerId");
            Long parsed = parseLong(raw);
            if (parsed != null) return parsed;
        }
        return parseLong(intent.get("offerId"));
    }

    private Long parseLong(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(raw.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static class ResponderOferta {
        public String action; // EXPECTED: ACCEPT or REJECT
        public Boolean accept; // true = aceptar, false/null = rechazar
    }

    public static class ContraOferta {
        public BigDecimal monto;
        public String mensaje;
    }

    public record ResultadoRespuesta(String mensaje, boolean accepted, Servicio servicio) {}

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
