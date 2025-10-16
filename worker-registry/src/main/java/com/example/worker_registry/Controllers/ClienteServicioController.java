package com.example.worker_registry.Controllers;

import com.example.worker_registry.Entitys.Servicio;
import com.example.worker_registry.Services.ServicioClienteService;
import com.example.worker_registry.securtity.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/clients/services")
public class ClienteServicioController {

    private final ServicioClienteService service;
    private final JwtService jwt;

    public ClienteServicioController(ServicioClienteService service, JwtService jwt) {
        this.service = service;
        this.jwt = jwt;
    }

    @PostMapping
    public ResponseEntity<?> crear(
            @Valid @RequestBody Servicio s,
            @RequestHeader("Authorization") String auth
    ) {
        Long clienteId = resolveUserId(auth);
        var saved = service.crearServicio(clienteId, s);
        return ResponseEntity.status(201).body(Map.of(
                "id", saved.getId(),
                "mensaje", "Servicio publicado correctamente"
        ));
    }

    @GetMapping("/my")
    public ResponseEntity<?> misServicios(@RequestHeader("Authorization") String auth) {
        Long clienteId = resolveUserId(auth);
        return ResponseEntity.ok(service.listarPorCliente(clienteId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detalle(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> editar(
            @PathVariable Long id,
            @RequestBody ServicioClienteService.UpdateData body,
            @RequestHeader("Authorization") String auth
    ) {
        Long clienteId = resolveUserId(auth);
        var upd = service.editarServicio(clienteId, id, body);
        return ResponseEntity.ok(Map.of("id", upd.getId(), "mensaje","Servicio editado correctamente"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(
            @PathVariable Long id,
            @RequestHeader("Authorization") String auth
    ) {
        Long clienteId = resolveUserId(auth);
        service.eliminarServicio(clienteId, id);
        return ResponseEntity.ok(Map.of("mensaje","Servicio eliminado correctamente"));
    }

    @GetMapping("/public/available")
    public ResponseEntity<?> disponibles() {
        return ResponseEntity.ok(service.listarDisponibles());
    }

    private Long resolveUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            throw new IllegalArgumentException("Falta Authorization: Bearer <token>");
        return jwt.getUserId(authHeader.substring(7));
    }
}
