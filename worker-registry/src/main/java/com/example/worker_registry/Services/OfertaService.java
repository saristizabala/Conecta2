package com.example.worker_registry.Services;

import com.example.worker_registry.Entitys.EstadoServicio;
import com.example.worker_registry.Entitys.Oferta;
import com.example.worker_registry.Entitys.Servicio;
import com.example.worker_registry.Repository.OfertaRepository;
import com.example.worker_registry.Repository.ServicioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OfertaService {

    private final OfertaRepository ofertaRepository;
    private final ServicioRepository servicioRepository;

    public OfertaService(OfertaRepository ofertaRepository,
                         ServicioRepository servicioRepository) {
        this.ofertaRepository = ofertaRepository;
        this.servicioRepository = servicioRepository;
    }

    /**
     * Ofertas recibidas por un cliente en sus servicios que aún están PENDIENTES.
     */
    public List<Oferta> listarPendientesCliente(Long clienteId) {
        return ofertaRepository.findByServicio_Cliente_IdAndServicio_Estado(clienteId, EstadoServicio.PENDIENTE);
    }

    /**
     * Ofertas realizadas por un trabajador en servicios que aún están PENDIENTES.
     */
    public List<Oferta> listarPendientesTrabajador(Long trabajadorId) {
        return ofertaRepository.findByTrabajador_IdAndServicio_Estado(trabajadorId, EstadoServicio.PENDIENTE);
    }

    @Transactional
    public ResultadoRespuesta responderOferta(Long clientId, Long ofertaId, ResponderOferta data) {
        Oferta oferta = ofertaRepository.findById(ofertaId)
                .orElseThrow(() -> new EntityNotFoundException("Oferta no encontrada"));

        Servicio servicio = oferta.getServicio();
        if (servicio == null || servicio.getCliente() == null || !clientId.equals(servicio.getCliente().getId())) {
            throw new SecurityException("No tienes permiso para responder esta oferta");
        }
        if (servicio.getEstado() != EstadoServicio.PENDIENTE) {
            throw new IllegalStateException("Solo puedes responder ofertas de servicios PENDIENTES");
        }

        boolean aceptar = data != null && Boolean.TRUE.equals(data.accept);
        if (aceptar) {
            servicio.setEstado(EstadoServicio.ASIGNADO);
            servicioRepository.save(servicio);
            return new ResultadoRespuesta("Oferta aceptada", true);
        } else {
            ofertaRepository.delete(oferta);
            return new ResultadoRespuesta("Oferta rechazada", false);
        }
    }

    public static class ResponderOferta {
        public Boolean accept; // true = aceptar, false/null = rechazar
    }

    public record ResultadoRespuesta(String mensaje, boolean accepted) {}
}
