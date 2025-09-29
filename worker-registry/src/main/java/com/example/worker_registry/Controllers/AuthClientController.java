package com.example.worker_registry.Controllers;

import com.example.worker_registry.Entitys.Cliente;
import com.example.worker_registry.Repository.ClienteRepository;
import com.example.worker_registry.securtity.JwtService; // ajusta paquete real
import com.example.worker_registry.Services.RegistroCliente;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/clients")
public class AuthClientController {

    private final RegistroCliente service;
    private final ClienteRepository repo;
    private final JwtService jwt;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthClientController(RegistroCliente service, ClienteRepository repo, JwtService jwt) {
        this.service = service;
        this.repo = repo;
        this.jwt = jwt;
    }

    // Registro cliente
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody Cliente c) {
        var saved = service.registrarCliente(c);
        return ResponseEntity.status(201).body(Map.of(
            "id", saved.getId(),
            "mensaje", "Registro recibido. Revisa tu correo para activar la cuenta."
        ));
    }

    // Verificación de cuenta del cliente
    @GetMapping("/verify")
    public ResponseEntity<?> verify(@RequestParam("token") String token) {
        // Si usas un claim 'type' puedes validar aquí; si no, simplemente parsea el subject
        var userId = jwt.parseActivationToken(token);
        service.activarCuenta(userId);
        return ResponseEntity.ok(Map.of("mensaje", "Cuenta de cliente activada correctamente. Ya puedes iniciar sesión."));
    }

    // Login cliente
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> req) {
        var email = req.getOrDefault("email", "");
        var password = req.getOrDefault("password", "");

        var c = repo.findByCorreo(email).orElse(null);
        if (c == null) {
            return ResponseEntity.status(401).body(Map.of("mensaje", "Credenciales inválidas"));
        }
        if (!c.isActivo()) {
            return ResponseEntity.status(403).body(Map.of("mensaje", "Cuenta no verificada. Revisa tu correo."));
        }
        if (!encoder.matches(password, c.getContrasena())) {
            return ResponseEntity.status(401).body(Map.of("mensaje", "Credenciales inválidas"));
        }

        var access = jwt.generateAccessToken(c.getId(), "CLIENT");
        return ResponseEntity.ok(Map.of(
            "token", access,
            "userId", c.getId(),
            "nombreCompleto", c.getNombreCompleto(),
            "role", "CLIENT"
        ));
    }
}
