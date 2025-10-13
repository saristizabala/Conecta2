package com.example.worker_registry.Services;

import com.example.worker_registry.Entitys.*;
import com.example.worker_registry.Repository.ServicioRepository;
import com.example.worker_registry.Repository.ClienteRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ServicioClienteService {

    private final ServicioRepository repo;
    private final ClienteRepository clienteRepo;

    public ServicioClienteService(ServicioRepository repo,
                                  ClienteRepository clienteRepo) {
        this.repo = repo;
        this.clienteRepo = clienteRepo;
    }

    // ==========================
    // HU005: Publicar servicio
    // ==========================
    @Transactional
    public Servicio crearServicio(Long clienteId, Servicio s) {
        var cliente = clienteRepo.findById(clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no existe"));

        // Validación de fecha (doble check por seguridad)
        if (s.getFechaEstimada() == null || s.getFechaEstimada().isBefore(LocalDateTime.now().minusMinutes(1))) {
            throw new IllegalArgumentException("La fecha estimada no puede ser anterior al día actual");
        }
        if (s.getCategoria() == null) {
            throw new IllegalArgumentException("La categoría es obligatoria");
        }
        if (s.getTitulo() == null || s.getTitulo().isBlank()) {
            throw new IllegalArgumentException("El título no puede estar vacío");
        }
        if (s.getDescripcion() == null || s.getDescripcion().isBlank()) {
            throw new IllegalArgumentException("La descripción no puede estar vacía");
        }
        if (s.getUbicacion() == null || s.getUbicacion().isBlank()) {
            throw new IllegalArgumentException("La ubicación es obligatoria");
        }

        s.setCliente(cliente);
        s.setEstado(EstadoServicio.PENDIENTE);

        return repo.save(s);
    }

    // Listado público (disponibles)
    public List<Servicio> listarDisponibles() {
        return repo.findByEstado(EstadoServicio.PENDIENTE);
    }

    // Mis servicios
    public List<Servicio> listarPorCliente(Long clienteId) {
        return repo.findByCliente_Id(clienteId);
    }

    public Servicio obtenerPorId(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado"));
    }

    // ==========================
    // HU006: Editar/Eliminar
    // Solo si está PENDIENTE y es del cliente.
    // ==========================
    @Transactional
    public Servicio editarServicio(Long clienteId, Long servicioId, UpdateData data) {
        var s = repo.findByIdAndCliente_Id(servicioId, clienteId)
                .orElseThrow(() -> new SecurityException("No tienes permiso sobre este servicio"));

        if (s.getEstado() != EstadoServicio.PENDIENTE) {
            throw new IllegalStateException("Solo los servicios PENDIENTES pueden editarse");
        }

        // Validaciones HU006
        if (isBlank(data.titulo)) throw new IllegalArgumentException("El título no puede estar vacío");
        if (isBlank(data.descripcion)) throw new IllegalArgumentException("La descripción no puede estar vacía");
        if (isBlank(data.ubicacion)) throw new IllegalArgumentException("La ubicación es obligatoria");
        if (data.fechaEstimada == null || data.fechaEstimada.isBefore(LocalDateTime.now().minusMinutes(1))) {
            throw new IllegalArgumentException("La fecha estimada no puede ser anterior al día actual");
        }

        s.setTitulo(data.titulo);
        s.setDescripcion(data.descripcion);
        s.setUbicacion(data.ubicacion);
        s.setFechaEstimada(data.fechaEstimada);

        // Si decides permitir cambiar categoría en la edición:
        if (data.categoria != null) s.setCategoria(data.categoria);

        return repo.save(s); // @PreUpdate setea actualizadoEn
    }

    @Transactional
    public void eliminarServicio(Long clienteId, Long servicioId) {
        var s = repo.findByIdAndCliente_Id(servicioId, clienteId)
                .orElseThrow(() -> new SecurityException("No tienes permiso sobre este servicio"));
        if (s.getEstado() != EstadoServicio.PENDIENTE) {
            throw new IllegalStateException("Solo los servicios PENDIENTES pueden eliminarse");
        }
        repo.delete(s);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // ===== DTO para actualización (HU006)
    public static class UpdateData {
        public String titulo;
        public String descripcion;
        public CategoriaServicio categoria; // opcional en edición
        public String ubicacion;
        public LocalDateTime fechaEstimada;
    }
}
