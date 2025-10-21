package com.example.worker_registry.Controllers;

import com.example.worker_registry.Entitys.Servicio;
import com.example.worker_registry.Services.ServicioClienteService;
import com.example.worker_registry.securtity.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/clients/services")
public class ClienteServicioController {

    private final ServicioClienteService service;

    public ClienteServicioController(ServicioClienteService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<?> crear(@AuthenticationPrincipal AuthenticatedUser user,
                                   @Valid @RequestBody Servicio servicio) {
        Long clienteId = requireClient(user);
        var saved = service.crearServicio(clienteId, servicio);
        return ResponseEntity.status(201).body(Map.of(
                "id", saved.getId(),
                "mensaje", "Servicio publicado correctamente"
        ));
    }

    @GetMapping
    public ResponseEntity<?> listarPropios(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(service.listarPorCliente(requireClient(user)));
    }

    @GetMapping("/my")
    public ResponseEntity<?> listarPropiosAlias(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(service.listarPorCliente(requireClient(user)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detalle(@AuthenticationPrincipal AuthenticatedUser user,
                                     @PathVariable Long id) {
        return ResponseEntity.ok(service.obtenerDetallePropietario(requireClient(user), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> editar(@AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable Long id,
                                    @RequestBody ServicioClienteService.UpdateData body) {
        var actualizado = service.editarServicio(requireClient(user), id, body);
        return ResponseEntity.ok(Map.of(
                "id", actualizado.getId(),
                "mensaje", "Servicio editado correctamente"
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@AuthenticationPrincipal AuthenticatedUser user,
                                      @PathVariable Long id) {
        service.eliminarServicio(requireClient(user), id);
        return ResponseEntity.ok(Map.of("mensaje", "Servicio eliminado correctamente"));
    }

    @GetMapping("/public/available")
    public ResponseEntity<?> disponibles() {
        return ResponseEntity.ok(service.listarDisponibles());
    }

    @GetMapping("/public/by-client/{clientId}")
    public ResponseEntity<?> disponiblesPorCliente(@PathVariable Long clientId) {
        return ResponseEntity.ok(service.listarPorCliente(clientId));
    }

    private Long requireClient(AuthenticatedUser user) {
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("Usuario no autenticado");
        }
        if (!user.hasRole("CLIENT")) {
            throw new org.springframework.security.access.AccessDeniedException("Rol no autorizado");
        }
        return user.userId();
    }
}
