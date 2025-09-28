package com.example.worker_registry.Entitys;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
@Table(
    name = "clientes",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"correo"}),
        @UniqueConstraint(columnNames = {"celular"})
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ Nombre no vacío
    @NotBlank(message = "El nombre completo es obligatorio")
    private String nombreCompleto;

    // ✅ Correo único + formato válido
    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El correo electrónico no tiene un formato válido")
    @Column(unique = true, nullable = false)
    private String correo;

    // ✅ Contraseña segura (no se expone en respuestas JSON)
    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    @Pattern(
        // Al menos 1 minúscula, 1 mayúscula, 1 número y 1 carácter especial
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$",
        message = "La contraseña debe incluir mayúsculas, minúsculas, números y un carácter especial"
    )
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String contrasena;

    // ⚠️ Campo solo para recibir por JSON y validar; no se persiste
    @Transient
    @NotBlank(message = "Debe confirmar la contraseña")
    @JsonProperty(value = "confirmarContrasena", access = JsonProperty.Access.WRITE_ONLY)
    private String confirmarContrasena;

    // ✅ Celular único
    @NotBlank(message = "El número de celular es obligatorio")
    @Column(unique = true, nullable = false)
    private String celular;

    // ✅ Activo solo tras verificar por correo
    @Builder.Default
    private boolean activo = false;
}
