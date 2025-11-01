package com.example.worker_registry.Entitys;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ofertas",
       uniqueConstraints = @UniqueConstraint(columnNames = {"servicio_id", "trabajador_id"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Oferta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "servicio_id", nullable = false)
    @JsonIgnore
    private Servicio servicio;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "trabajador_id", nullable = false)
    @JsonIgnore
    private Trabajador trabajador;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false, message = "El monto debe ser mayor a 0")
    @Digits(integer = 12, fraction = 2, message = "El monto debe tener como maximo 2 decimales")
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    @Size(max = 500, message = "El mensaje no puede exceder 500 caracteres")
    @Column(length = 500)
    private String mensaje;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    void onCreate() {
        this.creadoEn = LocalDateTime.now();
    }

    @com.fasterxml.jackson.annotation.JsonProperty("servicioId")
    public Long getServicioId() {
        return servicio != null ? servicio.getId() : null;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("trabajadorId")
    public Long getTrabajadorId() {
        return trabajador != null ? trabajador.getId() : null;
    }
}

