package com.example.worker_registry.Services;

import com.example.worker_registry.Entitys.*;
import com.example.worker_registry.Repository.OfertaRepository;
import com.example.worker_registry.Repository.ServicioRepository;
import com.example.worker_registry.Repository.TrabajadorRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ServicioTrabajadorService {

    private final ServicioRepository servicioRepo;
    private final TrabajadorRepository trabajadorRepo;
    private final OfertaRepository ofertaRepo;

    public ServicioTrabajadorService(ServicioRepository servicioRepo,
                                     TrabajadorRepository trabajadorRepo,
                                     OfertaRepository ofertaRepo) {
        this.servicioRepo = servicioRepo;
        this.trabajadorRepo = trabajadorRepo;
        this.ofertaRepo = ofertaRepo;
    }

    public List<Servicio> listarDisponiblesPorArea(Long trabajadorId) {
        var trabajador = trabajadorRepo.findById(trabajadorId)
                .orElseThrow(() -> new EntityNotFoundException("Trabajador no encontrado"));
        var categoria = parseCategoria(trabajador.getAreaServicio());
        return servicioRepo.findByEstadoAndCategoria(EstadoServicio.PENDIENTE, categoria);
    }

    @Transactional
    public Oferta crearOferta(Long trabajadorId, Long servicioId, CrearOferta data) {
        if (data == null || data.monto == null) {
            throw new IllegalArgumentException("El monto es obligatorio");
        }
        if (data.monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a 0");
        }

        var trabajador = trabajadorRepo.findById(trabajadorId)
                .orElseThrow(() -> new EntityNotFoundException("Trabajador no encontrado"));
        var servicio = servicioRepo.findById(servicioId)
                .orElseThrow(() -> new EntityNotFoundException("Servicio no encontrado"));

        if (servicio.getEstado() != EstadoServicio.PENDIENTE) {
            throw new IllegalStateException("Solo se puede ofertar en servicios PENDIENTES");
        }

        var categoriaTrabajador = parseCategoria(trabajador.getAreaServicio());
        if (servicio.getCategoria() != categoriaTrabajador) {
            throw new IllegalArgumentException("El servicio no corresponde a tu area de servicio");
        }

        var existente = ofertaRepo.findByServicio_IdAndTrabajador_Id(servicioId, trabajadorId);
        if (existente.isPresent()) {
            throw new IllegalStateException("Ya has ofertado en este servicio");
        }

        var oferta = Oferta.builder()
                .servicio(servicio)
                .trabajador(trabajador)
                .monto(data.monto)
                .mensaje(data.mensaje)
                .build();
        return ofertaRepo.save(oferta);
    }

    private CategoriaServicio parseCategoria(String areaServicio) {
        try {
            return CategoriaServicio.fromJson(areaServicio);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Area de servicio invalida para el trabajador: " + areaServicio);
        }
    }

    public static class CrearOferta {
        public BigDecimal monto;
        public String mensaje;
    }
}

