package com.example.worker_registry.Entitys;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

@Entity
@Table(
    name = "trabajadores",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"correo"}),
        @UniqueConstraint(columnNames = {"celular"})
    }
)
public class Trabajador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ Nombre completo no vacío
    @NotBlank(message = "El nombre completo es obligatorio")
    private String nombreCompleto;

    // ✅ Correo único, no vacío, con formato válido
    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El correo electrónico no tiene un formato válido")
    @Column(unique = true)
    private String correo;

    // ✅ Contraseña con reglas de seguridad
    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    @Pattern(
        // 8+ caracteres, al menos 1 minúscula, 1 mayúscula, 1 número y 1 especial
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$",
        message = "La contraseña debe incluir mayúsculas, minúsculas, números y un carácter especial"
    )
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String contrasena;

    /**
     * ⚠️ Campo solo para lectura desde el request.
     * - No se persiste (Transient)
     * - NO tiene anotaciones de validación para no disparar 400 automáticos.
     *   La verificación de coincidencia se hace en el servicio.
     */
    @Transient
    @JsonProperty(value = "confirmarContrasena", access = JsonProperty.Access.WRITE_ONLY)
    private String confirmarContrasena;

    // ✅ Celular único y no vacío
    @NotBlank(message = "El número de celular es obligatorio")
    @Column(unique = true)
    private String celular;

    // ✅ Área de servicio no vacía
    @NotBlank(message = "El área de servicio es obligatoria")
    private String areaServicio;

    // ✅ Estado de activación (se marca true tras verificar correo)
    private boolean activo = false;

    // ===== Getters y Setters =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getContrasena() { return contrasena; }
    public void setContrasena(String contrasena) { this.contrasena = contrasena; }

    public String getConfirmarContrasena() { return confirmarContrasena; }
    public void setConfirmarContrasena(String confirmarContrasena) { this.confirmarContrasena = confirmarContrasena; }

    public String getCelular() { return celular; }
    public void setCelular(String celular) { this.celular = celular; }

    public String getAreaServicio() { return areaServicio; }
    public void setAreaServicio(String areaServicio) { this.areaServicio = areaServicio; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
