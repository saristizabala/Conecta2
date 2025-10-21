package com.example.worker_registry.Controllers;

import com.example.worker_registry.Entitys.Trabajador;
import com.example.worker_registry.Repository.TrabajadorRepository;
import com.example.worker_registry.Services.RegistroTrabajador;
import com.example.worker_registry.securtity.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthTrabajadorController {

    private final RegistroTrabajador regService;
    private final TrabajadorRepository repo;
    private final JwtService jwt;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthTrabajadorController(RegistroTrabajador regService,
                                    TrabajadorRepository repo,
                                    JwtService jwt) {
        this.regService = regService;
        this.repo = repo;
        this.jwt = jwt;
    }

    @PostMapping("/workers/register")
    public ResponseEntity<?> registerWorker(@Valid @RequestBody Trabajador trabajador) {
        var saved = regService.registrarTrabajador(trabajador);
        return ResponseEntity.status(201).body(Map.of(
                "id", saved.getId(),
                "mensaje", "Registro recibido. Revisa tu correo para activar la cuenta."
        ));
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verify(@RequestParam("token") String token) {
        try {
            Long userId = jwt.parseActivationToken(token);
            regService.activarCuenta(userId);
            return ResponseEntity.ok(Map.of("mensaje", "Cuenta activada. Ya puedes iniciar sesion."));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", ex.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> payload) {
        String email = payload.containsKey("email")
                ? payload.get("email")
                : payload.getOrDefault("correo", "");
        email = email == null ? "" : email.trim();

        String password = payload.containsKey("password")
                ? payload.get("password")
                : payload.getOrDefault("contrasena", "");
        password = password == null ? "" : password;

        if (email.isEmpty() || password.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("mensaje", "Credenciales invalidas"));
        }

        var trabajador = repo.findByCorreo(email).orElse(null);
        if (trabajador == null) {
            return ResponseEntity.status(401).body(Map.of("mensaje", "Credenciales invalidas"));
        }
        if (!trabajador.isActivo()) {
            return ResponseEntity.status(403).body(Map.of("mensaje", "Cuenta no verificada. Revisa tu correo."));
        }
        if (!encoder.matches(password, trabajador.getContrasena())) {
            return ResponseEntity.status(401).body(Map.of("mensaje", "Credenciales invalidas"));
        }

        var access = jwt.generateAccessToken(trabajador.getId(), "WORKER");
        return ResponseEntity.ok(Map.of(
                "token", access,
                "userId", trabajador.getId(),
                "nombre", trabajador.getNombreCompleto(),
                "role", "WORKER"
        ));
    }
}
