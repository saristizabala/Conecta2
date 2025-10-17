package com.example.worker_registry.Services;

import com.example.worker_registry.Entitys.*;
import com.example.worker_registry.Repository.ServicioRepository;
import com.example.worker_registry.Repository.ClienteRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

        // Evitar updates accidentales vía JSON
        s.setId(null);

        // --------- Normalización de fecha ----------
        // Si el front envía solo fecha (00:00), no debe fallar por estar "antes de ahora".
        // Reglas:
        // - La fecha NO puede ser anterior a HOY.
        // - Si no viene hora (00:00) la normalizamos a 09:00 para consistencia.
        LocalDateTime fecha = s.getFechaEstimada();
        if (fecha == null) {
            throw new IllegalArgumentException("La fecha estimada es obligatoria");
        }

        LocalDate hoy = LocalDate.now();
        LocalDate fechaDia = fecha.toLocalDate();

        if (fechaDia.isBefore(hoy)) {
            throw new IllegalArgumentException("La fecha estimada no puede ser anterior al día actual");
        }

        // Si la hora es 00:00:00 (común cuando solo eliges fecha en UI), ajustamos a 09:00
        if (fecha.toLocalTime().equals(LocalTime.MIDNIGHT)) {
            fecha = LocalDateTime.of(fechaDia, LocalTime.of(9, 0));
        }
        s.setFechaEstimada(fecha);
        // --------- Fin normalización fecha -----------

        if (s.getCategoria() == null) {
            throw new IllegalArgumentException("La categoría es obligatoria");
        }
        if (s.getTitulo() == null || s.getTitulo().trim().isEmpty()) {
            throw new IllegalArgumentException("El título no puede estar vacío");
        }
        if (s.getDescripcion() == null || s.getDescripcion().trim().isEmpty()) {
            throw new IllegalArgumentException("La descripción no puede estar vacía");
        }
        if (s.getUbicacion() == null || s.getUbicacion().trim().isEmpty()) {
            throw new IllegalArgumentException("La ubicación es obligatoria");
        }

        s.setTitulo(s.getTitulo().trim());
        s.setDescripcion(s.getDescripcion().trim());
        s.setUbicacion(s.getUbicacion().trim());

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

    /** Detalle para el propietario (valida permisos). */
    public Servicio obtenerDetallePropietario(Long clienteId, Long servicioId) {
        return repo.findByIdAndCliente_Id(servicioId, clienteId)
                .orElseThrow(() -> new SecurityException("No tienes permiso sobre este servicio"));
    }

    // ==========================
    // HU006: Editar/Eliminar
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
        if (data.fechaEstimada == null) {
            throw new IllegalArgumentException("La fecha estimada es obligatoria");
        }

        // --------- Normalización de fecha en edición ----------
        LocalDate hoy = LocalDate.now();
        LocalDate fechaDia = data.fechaEstimada.toLocalDate();

        if (fechaDia.isBefore(hoy)) {
            throw new IllegalArgumentException("La fecha estimada no puede ser anterior al día actual");
        }

        LocalDateTime fechaNormalizada = data.fechaEstimada;
        if (fechaNormalizada.toLocalTime().equals(LocalTime.MIDNIGHT)) {
            fechaNormalizada = LocalDateTime.of(fechaDia, LocalTime.of(9, 0));
        }
        // --------- Fin normalización -----------

        s.setTitulo(data.titulo.trim());
        s.setDescripcion(data.descripcion.trim());
        s.setUbicacion(data.ubicacion.trim());
        s.setFechaEstimada(fechaNormalizada);

        if (data.categoria != null) s.setCategoria(data.categoria);

        return repo.save(s); // @PreUpdate setea actualizadoEn (si lo tienes en la entidad)
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

    // ===== "DTO" interno para actualización (HU006) – no creamos clases externas
    public static class UpdateData {
        public String titulo;
        public String descripcion;
        public CategoriaServicio categoria; // opcional en edición
        public String ubicacion;
        public LocalDateTime fechaEstimada;
    }
}
