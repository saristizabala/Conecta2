package com.example.worker_registry.Controllers;

import com.example.worker_registry.Entitys.CategoriaServicio;
import com.example.worker_registry.Entitys.Servicio;
import com.example.worker_registry.Services.ServicioClienteService;
import com.example.worker_registry.securtity.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
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

    // =========================================================
    // HU005 - Crear servicio (sin DTO) -> Map<String,Object>
    // =========================================================
    @PostMapping
    public ResponseEntity<?> crear(
            @RequestBody Map<String, Object> body,
            Authentication auth
    ) {
        Long clienteId = getUserId(auth); // extrae del SecurityContext (JwtAuthFilter)
        ensureIsClient(auth);             // valida rol CLIENT

        // Validar y mapear los campos manualmente para evitar 400 por binding
        String titulo = readString(body, "titulo", true);
        String descripcion = readString(body, "descripcion", true);
        String ubicacion = readString(body, "ubicacion", true);

        // categoria: viene como String y debe mapear al enum del backend
        String categoriaRaw = readString(body, "categoria", true);
        CategoriaServicio categoria = toCategoriaEnum(categoriaRaw);

        // fechaEstimada: aceptar varias formas (ISO con o sin ms, con Z, etc.)
        String fechaRaw = readString(body, "fechaEstimada", true);
        LocalDateTime fechaEstimada = parseFechaFlexible(fechaRaw);

        // Construir entidad
        Servicio s = new Servicio();
        s.setTitulo(titulo);
        s.setDescripcion(descripcion);
        s.setUbicacion(ubicacion);
        s.setCategoria(categoria);
        s.setFechaEstimada(fechaEstimada);
        // s.setEstado(...) // Si el service lo setea por defecto, no tocar aquí.

        var saved = service.crearServicio(clienteId, s);
        return ResponseEntity.status(201).body(Map.of(
                "id", saved.getId(),
                "mensaje", "Servicio publicado correctamente"
        ));
    }

    // =========================================================
    // Mis servicios (cliente autenticado)
    // =========================================================
    @GetMapping("/my")
    public ResponseEntity<?> misServicios(Authentication auth) {
        Long clienteId = getUserId(auth);
        ensureIsClient(auth);
        return ResponseEntity.ok(service.listarPorCliente(clienteId));
    }

    // =========================================================
    // Detalle público por id (si tu lógica lo permite)
    // =========================================================
    @GetMapping("/{id}")
    public ResponseEntity<?> detalle(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenerPorId(id));
    }

    // =========================================================
    // Editar servicio (cliente autenticado)
    // =========================================================
    @PutMapping("/{id}")
    public ResponseEntity<?> editar(
            @PathVariable Long id,
            @RequestBody ServicioClienteService.UpdateData body,
            Authentication auth
    ) {
        Long clienteId = getUserId(auth);
        ensureIsClient(auth);
        var upd = service.editarServicio(clienteId, id, body);
        return ResponseEntity.ok(Map.of("id", upd.getId(), "mensaje", "Servicio editado correctamente"));
    }

    // =========================================================
    // Eliminar servicio (cliente autenticado)
    // =========================================================
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(
            @PathVariable Long id,
            Authentication auth
    ) {
        Long clienteId = getUserId(auth);
        ensureIsClient(auth);
        service.eliminarServicio(clienteId, id);
        return ResponseEntity.ok(Map.of("mensaje", "Servicio eliminado correctamente"));
    }

    // =========================================================
    // Listado público de disponibles (no requiere auth)
    // =========================================================
    @GetMapping("/public/available")
    public ResponseEntity<?> disponibles() {
        return ResponseEntity.ok(service.listarDisponibles());
    }

    // =========================================================
    // Helpers
    // =========================================================

    private Long getUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            // Lanzar 401 coherente con tu GlobalExceptionHandler (puedes usar RuntimeException y mapear a 401)
            throw new IllegalStateException("No autorizado");
        }
        try {
            if (auth.getPrincipal() instanceof Long l) return l;
            return Long.valueOf(auth.getPrincipal().toString());
        } catch (Exception e) {
            throw new IllegalStateException("No autorizado");
        }
    }

    private void ensureIsClient(Authentication auth) {
        boolean isClient = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_CLIENT".equalsIgnoreCase(a.getAuthority()));
        if (!isClient) {
            // 403 si está autenticado, pero no es CLIENT
            throw new IllegalStateException("Permisos insuficientes (se requiere rol CLIENT)");
        }
    }

    private String readString(Map<String, Object> body, String key, boolean required) {
        Object v = body.get(key);
        if (v == null) {
            if (required) throw new IllegalArgumentException("Falta el campo requerido: " + key);
            return null;
        }
        String s = v.toString().trim();
        if (required && s.isEmpty()) {
            throw new IllegalArgumentException("El campo " + key + " no debe estar vacío");
        }
        return s;
    }

    private CategoriaServicio toCategoriaEnum(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("La categoría es obligatoria");
        }
        // Normalizar: quitar acentos y llevar a mayúsculas
        String n = normalize(raw).toUpperCase(Locale.ROOT);

        // Si tu enum incluye OTROS, podrías retornar OTROS en default.
        // Aquí exigimos que coincida con el Enum exacto del backend.
        try {
            return CategoriaServicio.valueOf(n);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Categoría inválida: " + raw + ". Usa valores del enum del backend.");
        }
    }

    private String normalize(String s) {
        return s.toLowerCase(Locale.ROOT)
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace("ñ", "n");
    }

    private LocalDateTime parseFechaFlexible(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("fechaEstimada es obligatoria");
        }
        String r = raw.trim();

        // Caso 1: ISO con 'Z' (UTC) -> usar Instant
        try {
            if (r.endsWith("Z")) {
                Instant ins = Instant.parse(r);
                return LocalDateTime.ofInstant(ins, ZoneId.systemDefault());
            }
        } catch (Exception ignored) {}

        // Caso 2: Con milisegundos: 2025-10-16T14:00:00.123
        // recortar a 19 chars si hay '.'
        try {
            String base = r.contains(".") ? r.substring(0, Math.min(19, r.indexOf('.'))) : r;
            return LocalDateTime.parse(base, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {}

        // Caso 3: Intento directo
        try {
            return LocalDateTime.parse(r, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {}

        // Caso 4: yyyy-MM-dd
        try {
            LocalDate d = LocalDate.parse(r, DateTimeFormatter.ISO_LOCAL_DATE);
            return d.atStartOfDay();
        } catch (Exception ignored) {}

        throw new IllegalArgumentException("Formato de fecha inválido: " + raw + " (usa ISO, ej: 2025-10-16T14:00:00)");
    }
}
