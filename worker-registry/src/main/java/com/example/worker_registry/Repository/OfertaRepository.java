package com.example.worker_registry.Repository;

import com.example.worker_registry.Entitys.Oferta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OfertaRepository extends JpaRepository<Oferta, Long> {
    List<Oferta> findByServicio_Id(Long servicioId);
    List<Oferta> findByTrabajador_Id(Long trabajadorId);
    Optional<Oferta> findByServicio_IdAndTrabajador_Id(Long servicioId, Long trabajadorId);

    // Ofertas dirigidas a un cliente (servicios del cliente) con el servicio aún PENDIENTE
    java.util.List<Oferta> findByServicio_Cliente_IdAndServicio_Estado(Long clienteId,
                                                                       com.example.worker_registry.Entitys.EstadoServicio estado);

    // Ofertas realizadas por un trabajador en servicios aún PENDIENTES
    java.util.List<Oferta> findByTrabajador_IdAndServicio_Estado(Long trabajadorId,
                                                                 com.example.worker_registry.Entitys.EstadoServicio estado);
}
