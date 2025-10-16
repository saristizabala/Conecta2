package com.example.worker_registry.Controllers;

import com.example.worker_registry.Entitys.Cliente;
import com.example.worker_registry.Repository.ClienteRepository;
import com.example.worker_registry.Services.RegistroCliente;
import com.example.worker_registry.securtity.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/clients")
public class AuthClientController {

    private final RegistroCliente service;
    private final ClienteRepository repo;
    private final JwtService jwt;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthClientController(RegistroCliente service,
                                ClienteRepository repo,
                                JwtService jwt) {
        this.service = service;
        this.repo = repo;
        this.jwt = jwt;
    }

    // ====================================================
    // Registro cliente  (envía correo con token de activación)
    // ====================================================
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody Cliente c) {
        var saved = service.registrarCliente(c);
        return ResponseEntity.status(201).body(Map.of(
                "id", saved.getId(),
                "mensaje", "Registro recibido. Revisa tu correo para activar la cuenta."
        ));
    }

    // ====================================================
    // Verificación de cuenta (token type=activation)
    // ====================================================
    @GetMapping("/verify")
    public ResponseEntity<?> verify(@RequestParam("token") String token) {
        Long userId = jwt.parseActivationToken(token); // valida type=activation
        service.activarCuenta(userId);
        return ResponseEntity.ok(Map.of(
                "mensaje", "Cuenta de cliente activada correctamente. Ya puedes iniciar sesión."
        ));
    }

    // ====================================================
    // Reenviar activación (opcional)
    // ====================================================
    @PostMapping("/resend-activation")
    public ResponseEntity<?> resendActivation(@RequestBody Map<String, String> req) {
        String email = req.getOrDefault("email", "").trim();
        if (email.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", "Email es requerido"));
        }
        try {
            service.reenviarActivacion(email);
            return ResponseEntity.ok(Map.of("mensaje", "Se envió un nuevo enlace de activación"));
        } catch (IllegalStateException e) {
            // ya activa
            return ResponseEntity.badRequest().body(Map.of("mensaje", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("mensaje", e.getMessage()));
        }
    }

    // ====================================================
    // Login cliente -> devuelve JWT de acceso (type=access, role=CLIENT)
    // ====================================================
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> req) {
        var email = req.getOrDefault("email", "").trim();
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

    // ====================================================
    // Perfil del cliente autenticado (protegido, requiere Bearer)
    // ====================================================
    @GetMapping("/me")
    public ResponseEntity<?> me() {
        // JwtAuthFilter pone userId como principal en la Authentication
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("mensaje", "No autorizado"));
        }
        Long userId;
        try {
            userId = (Long) auth.getPrincipal();
        } catch (ClassCastException e) {
            return ResponseEntity.status(401).body(Map.of("mensaje", "No autorizado"));
        }

        var c = repo.findById(userId).orElse(null);
        if (c == null) {
            return ResponseEntity.status(404).body(Map.of("mensaje", "Cliente no encontrado"));
        }

        // Devuelve un perfil básico (evitamos exponer contraseña, etc.)
        return ResponseEntity.ok(Map.of(
                "id", c.getId(),
                "nombreCompleto", c.getNombreCompleto(),
                "correo", c.getCorreo(),
                "celular", c.getCelular(),
                "activo", c.isActivo(),
                "role", "CLIENT"
        ));
    }
}
