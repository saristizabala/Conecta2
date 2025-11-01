package com.example.worker_registry.Controllers;

import com.example.worker_registry.Services.OfertaService;
import com.example.worker_registry.securtity.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class OfertaController {

    private final OfertaService ofertaService;

    public OfertaController(OfertaService ofertaService) {
        this.ofertaService = ofertaService;
    }

    // Ofertas para el cliente (servicios propios) aún pendientes
    @GetMapping("/clients/{clientId}/offers/pending")
    public ResponseEntity<?> pendientesCliente(@AuthenticationPrincipal AuthenticatedUser user,
                                               @PathVariable Long clientId) {
        requireClient(user, clientId);
        return ResponseEntity.ok(ofertaService.listarPendientesCliente(clientId));
    }

    // Ofertas creadas por el trabajador en servicios aún pendientes
    @GetMapping("/workers/{workerId}/offers/pending")
    public ResponseEntity<?> pendientesTrabajador(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @PathVariable Long workerId) {
        requireWorker(user, workerId);
        return ResponseEntity.ok(ofertaService.listarPendientesTrabajador(workerId));
    }

    // Responder una oferta (cliente): aceptar o rechazar
    @PostMapping("/offers/{id}/respond")
    public ResponseEntity<?> responder(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable Long id,
                                       @RequestBody(required = false) OfertaService.ResponderOferta body,
                                       @RequestParam(name = "accept", required = false) Boolean acceptParam) {
        requireClientRole(user);
        if (body == null && acceptParam != null) {
            body = new OfertaService.ResponderOferta();
            body.accept = acceptParam;
        }
        var result = ofertaService.responderOferta(user.userId(), id, body);
        return ResponseEntity.ok(java.util.Map.of(
                "mensaje", result.mensaje(),
                "accepted", result.accepted()
        ));
    }

    private void requireClient(AuthenticatedUser user, Long expectedId) {
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("Usuario no autenticado");
        }
        if (!user.hasRole("CLIENT")) {
            throw new org.springframework.security.access.AccessDeniedException("Rol no autorizado");
        }
        if (!user.userId().equals(expectedId)) {
            throw new org.springframework.security.access.AccessDeniedException("No puedes acceder a recursos de otro usuario");
        }
    }

    private void requireWorker(AuthenticatedUser user, Long expectedId) {
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("Usuario no autenticado");
        }
        if (!user.hasRole("WORKER")) {
            throw new org.springframework.security.access.AccessDeniedException("Rol no autorizado");
        }
        if (!user.userId().equals(expectedId)) {
            throw new org.springframework.security.access.AccessDeniedException("No puedes acceder a recursos de otro usuario");
        }
    }

    private void requireClientRole(AuthenticatedUser user) {
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("Usuario no autenticado");
        }
        if (!user.hasRole("CLIENT")) {
            throw new org.springframework.security.access.AccessDeniedException("Rol no autorizado");
        }
    }
}
