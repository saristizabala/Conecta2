package com.example.worker_registry.Entitys;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.example.worker_registry.Entitys.PaymentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

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

    @Column(nullable = false)
    private LocalDateTime actualizadoEn;

    @JsonIgnore
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoNegociacion estado = EstadoNegociacion.EN_NEGOCIACION;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "ultima_propuesta_por", nullable = false)
    private ParticipanteOferta ultimaPropuestaPor = ParticipanteOferta.TRABAJADOR;

    @Column(name = "monto_trabajador", precision = 14, scale = 2)
    private BigDecimal montoTrabajador;

    @Column(name = "monto_cliente", precision = 14, scale = 2)
    private BigDecimal montoCliente;

    @Column(name = "monto_acordado", precision = 14, scale = 2)
    private BigDecimal montoAcordado;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "payment_intent_id", length = 128)
    private String paymentIntentId;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "payment_client_secret", length = 256)
    private String paymentClientSecret;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 32)
    private PaymentStatus paymentStatus = PaymentStatus.NOT_REQUIRED;

    @JsonIgnore
    @Column(name = "payment_metadata", columnDefinition = "TEXT")
    private String paymentMetadata;

    @PrePersist
    void onCreate() {
        this.creadoEn = LocalDateTime.now();
        this.actualizadoEn = this.creadoEn;
    }

    @PreUpdate
    void onUpdate() {
        this.actualizadoEn = LocalDateTime.now();
    }

    @com.fasterxml.jackson.annotation.JsonProperty("servicioId")
    public Long getServicioId() {
        return servicio != null ? servicio.getId() : null;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("trabajadorId")
    public Long getTrabajadorId() {
        return trabajador != null ? trabajador.getId() : null;
    }

    @JsonProperty("estadoNegociacion")
    public EstadoNegociacion getEstadoNegociacion() {
        return estado;
    }

    @JsonProperty("paymentStatus")
    public PaymentStatus getJsonPaymentStatus() {
        return paymentStatus;
    }

    @JsonProperty("serviceId")
    public Long getServiceId() {
        return getServicioId();
    }

    public Map<String, Object> getPaymentMetadataAsMap() {
        if (paymentMetadata == null || paymentMetadata.isBlank()) {
            return java.util.Collections.emptyMap();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(paymentMetadata, Map.class);
        } catch (Exception ex) {
            return java.util.Collections.emptyMap();
        }
    }
}
