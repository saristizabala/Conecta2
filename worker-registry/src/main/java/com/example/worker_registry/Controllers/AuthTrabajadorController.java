package com.example.worker_registry.Controllers;

import com.example.worker_registry.Entitys.Trabajador;
import com.example.worker_registry.Repository.TrabajadorRepository;
import com.example.worker_registry.securtity.JwtService;
import com.example.worker_registry.Services.RegistroTrabajador;
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

    public AuthTrabajadorController(RegistroTrabajador regService, TrabajadorRepository repo, JwtService jwt) {
        this.regService = regService;
        this.repo = repo;
        this.jwt = jwt;
    }

    // Registro trabajador
    @PostMapping("/workers/register")
    public ResponseEntity<?> registerWorker(@Valid @RequestBody Trabajador t) {
        var saved = regService.registrarTrabajador(t);
        return ResponseEntity.status(201).body(Map.of(
                "id", saved.getId(),
                "mensaje", "Registro recibido. Revisa tu correo para activar la cuenta."
        ));
    }

    // Verificación de cuenta por token JWT
    @GetMapping("/verify")
    public ResponseEntity<?> verify(@RequestParam("token") String token) {
        if (!jwt.isActivationToken(token)) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", "Token inválido o expirado"));
        }
        var userId = jwt.getUserId(token);
        regService.activarCuenta(userId);
        return ResponseEntity.ok(Map.of("mensaje", "Cuenta activada. Ya puedes iniciar sesión."));
    }

    // Login
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> req) {
        var email = req.getOrDefault("email", "");
        var password = req.getOrDefault("password", "");

        var t = repo.findByCorreo(email).orElse(null);
        if (t == null) {
            return ResponseEntity.status(401).body(Map.of("mensaje", "Credenciales inválidas"));
        }
        if (!t.isActivo()) {
            return ResponseEntity.status(403).body(Map.of("mensaje", "Cuenta no verificada. Revisa tu correo."));
        }
        if (!encoder.matches(password, t.getContrasena())) {
            return ResponseEntity.status(401).body(Map.of("mensaje", "Credenciales inválidas"));
        }

        var access = jwt.generateAccessToken(t.getId(), "WORKER");
        return ResponseEntity.ok(Map.of(
                "token", access,
                "userId", t.getId(),
                "nombre", t.getNombreCompleto(),
                "role", "WORKER"
        ));
    }
}
