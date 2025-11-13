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
import java.util.List;

@Service
public class OfertaService {

    private final OfertaRepository ofertaRepository;
    private final ServicioRepository servicioRepository;
    private final PushNotificationService pushNotificationService;

    public OfertaService(OfertaRepository ofertaRepository,
                         ServicioRepository servicioRepository,
                         PushNotificationService pushNotificationService) {
        this.ofertaRepository = ofertaRepository;
        this.servicioRepository = servicioRepository;
        this.pushNotificationService = pushNotificationService;
    }

    public List<Oferta> listarPendientesCliente(Long clienteId) {
        return ofertaRepository.findByServicio_Cliente_IdAndServicio_EstadoAndEstadoAndUltimaPropuestaPorOrderByActualizadoEnDesc(
                clienteId,
                EstadoServicio.PENDIENTE,
                EstadoNegociacion.EN_NEGOCIACION,
                ParticipanteOferta.TRABAJADOR
        );
    }

    public List<Oferta> listarPendientesTrabajador(Long trabajadorId) {
        return ofertaRepository.findByTrabajador_IdAndServicio_EstadoAndEstadoAndUltimaPropuestaPorOrderByActualizadoEnDesc(
                trabajadorId,
                EstadoServicio.PENDIENTE,
                EstadoNegociacion.EN_NEGOCIACION,
                ParticipanteOferta.CLIENTE
        );
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
            throw new IllegalStateException("Esta negociación ya fue cerrada");
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
            throw new IllegalStateException("Esta negociaci��n ya fue cerrada");
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
    public ResultadoRespuesta responderOfertaTrabajador(Long trabajadorId, Long ofertaId, String action) {
        Oferta oferta = ofertaRepository.findById(ofertaId)
                .orElseThrow(() -> new EntityNotFoundException("Oferta no encontrada"));

        validarTrabajadorPropietario(trabajadorId, oferta);

        Servicio servicio = oferta.getServicio();
        validarServicioPendiente(servicio);

        if (oferta.getEstado() != EstadoNegociacion.EN_NEGOCIACION) {
            throw new IllegalStateException("Esta negociación ya fue cerrada");
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
    }

    private ResultadoRespuesta aceptarOferta(Oferta oferta, String mensaje) {
        oferta.setEstado(EstadoNegociacion.ACEPTADA);
        oferta.setMontoAcordado(oferta.getMonto());
        ofertaRepository.save(oferta);

        Servicio servicio = oferta.getServicio();
        servicio.setEstado(EstadoServicio.ASIGNADO);
        if (oferta.getTrabajador() != null && oferta.getTrabajador().getId() != null) {
            servicio.setAssignedWorkerId(oferta.getTrabajador().getId());
        }
        servicio = servicioRepository.save(servicio);
        notificarClienteAsignacion(servicio);

        cerrarOtrasOfertas(servicio, oferta.getId());

        return new ResultadoRespuesta(mensaje, true, servicio);
    }

    private void notificarClienteAsignacion(Servicio servicio) {
        if (servicio == null || servicio.getCliente() == null) {
            return;
        }
        Long clienteId = servicio.getCliente().getId();
        if (clienteId == null) {
            return;
        }
        String titulo = "Servicio asignado";
        String cuerpo = String.format(
                "El servicio %s (id=%d) ha sido asignado a un trabajador.",
                servicio.getTitulo(), servicio.getId()
        );
        pushNotificationService.notifyCliente(clienteId, titulo, cuerpo);
    }

    private void cerrarOtrasOfertas(Servicio servicio, Long aceptadaOfertaId) {
        var otras = ofertaRepository.findByServicio_Id(servicio.getId());
        var pendientes = otras.stream()
                .filter(o -> o.getId() != null
                        && !o.getId().equals(aceptadaOfertaId)
                        && o.getEstado() == EstadoNegociacion.EN_NEGOCIACION)
                .toList();
        if (!pendientes.isEmpty()) {
            pendientes.forEach(o -> o.setEstado(EstadoNegociacion.RECHAZADA));
            ofertaRepository.saveAll(pendientes);
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
}
