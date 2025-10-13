package com.example.worker_registry.Controllers;

import com.example.worker_registry.Entitys.Servicio;
import com.example.worker_registry.Services.ServicioClienteService;
import com.example.worker_registry.securtity.JwtService; // tu paquete 'securtity'
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/clients/services")
public class ClienteServicioController {

    private final ServicioClienteService service;
    private final JwtService jwt; // inyectable si existe
    private final boolean jwtAvailable;

    public ClienteServicioController(ServicioClienteService service,
                                     @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
                                     @org.springframework.beans.factory.annotation.Autowired(required = false)
                                     JwtService jwt) {
        this.service = service;
        this.jwt = jwt;
        this.jwtAvailable = (jwt != null);
    }

    // ==========================
    // HU005: Publicar servicio
    // ==========================
    @PostMapping
    public ResponseEntity<?> crear(
            @Valid @RequestBody Servicio s,
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(value = "clientIdDev", required = false) Long clientIdDev
    ) {
        Long clienteId = resolveUserId(auth, clientIdDev);
        var saved = service.crearServicio(clienteId, s);
        return ResponseEntity.status(201).body(Map.of(
                "id", saved.getId(),
                "mensaje", "Servicio publicado correctamente"
        ));
    }

    // Mis servicios
    @GetMapping("/my")
    public ResponseEntity<?> misServicios(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(value = "clientIdDev", required = false) Long clientIdDev
    ) {
        Long clienteId = resolveUserId(auth, clientIdDev);
        return ResponseEntity.ok(service.listarPorCliente(clienteId));
    }

    // Obtener detalle (propietario)
    @GetMapping("/{id}")
    public ResponseEntity<?> detalle(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(value = "clientIdDev", required = false) Long clientIdDev
    ) {
        Long clienteId = resolveUserId(auth, clientIdDev);
        return ResponseEntity.ok(service.obtenerDetallePropietario(clienteId, id));
    }

    // ==========================
    // HU006: editar / eliminar
    // ==========================
    @PutMapping("/{id}")
    public ResponseEntity<?> editar(
            @PathVariable Long id,
            @RequestBody ServicioClienteService.UpdateData body,
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(value = "clientIdDev", required = false) Long clientIdDev
    ) {
        Long clienteId = resolveUserId(auth, clientIdDev);
        var upd = service.editarServicio(clienteId, id, body);
        return ResponseEntity.ok(Map.of(
                "id", upd.getId(),
                "mensaje", "Servicio editado correctamente"
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(value = "clientIdDev", required = false) Long clientIdDev
    ) {
        Long clienteId = resolveUserId(auth, clientIdDev);
        service.eliminarServicio(clienteId, id);
        return ResponseEntity.ok(Map.of("mensaje", "Servicio eliminado correctamente"));
    }

    // ==========================
    // Listado público (disponibles)
    // ==========================
    @GetMapping("/public/available")
    public ResponseEntity<?> disponibles() {
        return ResponseEntity.ok(service.listarDisponibles());
    }

    // ===== Helper para obtener userId de JWT o parámetro dev =====
    private Long resolveUserId(String authHeader, Long fallback) {
        if (jwtAvailable && authHeader != null && authHeader.startsWith("Bearer ")) {
            return jwt.getUserId(authHeader.substring(7));
        }
        if (fallback != null) return fallback;
        throw new IllegalArgumentException("Falta Authorization Bearer o parámetro ?clientIdDev= en modo dev");
    }
}
