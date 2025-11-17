package com.example.worker_registry.Services;

import com.example.worker_registry.Entitys.Cliente;
import com.example.worker_registry.Entitys.EstadoNegociacion;
import com.example.worker_registry.Entitys.EstadoServicio;
import com.example.worker_registry.Entitys.Oferta;
import com.example.worker_registry.Entitys.PaymentStatus;
import com.example.worker_registry.Entitys.ParticipanteOferta;
import com.example.worker_registry.Entitys.Servicio;
import com.example.worker_registry.Entitys.Trabajador;
import com.example.worker_registry.Repository.OfertaRepository;
import com.example.worker_registry.Repository.ServicioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OfertaServiceTest {

    private OfertaRepository ofertaRepository;
    private ServicioRepository servicioRepository;
    private PushNotificationService pushNotificationService;
    private PaymentIntegrationService paymentIntegrationService;
    private OfertaService ofertaService;

    @BeforeEach
    void setUp() {
        ofertaRepository = Mockito.mock(OfertaRepository.class);
        servicioRepository = Mockito.mock(ServicioRepository.class);
        pushNotificationService = Mockito.mock(PushNotificationService.class);
        paymentIntegrationService = Mockito.mock(PaymentIntegrationService.class);
        ofertaService = new OfertaService(ofertaRepository, servicioRepository, pushNotificationService, paymentIntegrationService);
    }

    @Test
    void contraOfertaTrabajador_actualizaMontoYTurno() {
        var cliente = Cliente.builder().id(1L).build();
        var servicio = Servicio.builder()
                .id(10L)
                .cliente(cliente)
                .estado(EstadoServicio.PENDIENTE)
                .fechaEstimada(LocalDateTime.now().plusDays(2))
                .build();
        var trabajador = new Trabajador();
        trabajador.setId(99L);

        var oferta = Oferta.builder()
                .id(5L)
                .servicio(servicio)
                .trabajador(trabajador)
                .estado(EstadoNegociacion.EN_NEGOCIACION)
                .ultimaPropuestaPor(ParticipanteOferta.CLIENTE)
                .monto(BigDecimal.valueOf(100))
                .build();

        when(ofertaRepository.findById(5L)).thenReturn(Optional.of(oferta));
        when(ofertaRepository.save(any(Oferta.class))).thenAnswer(inv -> inv.getArgument(0));

        var contra = new OfertaService.ContraOferta();
        contra.monto = BigDecimal.valueOf(150);
        contra.mensaje = "subo el monto";

        var result = ofertaService.contraOfertaTrabajador(99L, 5L, contra);

        assertEquals(BigDecimal.valueOf(150), result.getMonto());
        assertEquals(ParticipanteOferta.TRABAJADOR, result.getUltimaPropuestaPor());
        assertEquals(BigDecimal.valueOf(150), result.getMontoTrabajador());
        assertEquals("subo el monto", result.getMensaje());
        verify(ofertaRepository).save(oferta);
    }

    @Test
    void contraOfertaTrabajador_fallaSiNoEsTurnoDelTrabajador() {
        var servicio = Servicio.builder()
                .id(11L)
                .cliente(Cliente.builder().id(2L).build())
                .estado(EstadoServicio.PENDIENTE)
                .fechaEstimada(LocalDateTime.now().plusDays(1))
                .build();
        var trabajador = new Trabajador();
        trabajador.setId(7L);

        var oferta = Oferta.builder()
                .id(6L)
                .servicio(servicio)
                .trabajador(trabajador)
                .estado(EstadoNegociacion.EN_NEGOCIACION)
                .ultimaPropuestaPor(ParticipanteOferta.TRABAJADOR)
                .build();

        when(ofertaRepository.findById(6L)).thenReturn(Optional.of(oferta));

        var contra = new OfertaService.ContraOferta();
        contra.monto = BigDecimal.TEN;

        assertThrows(IllegalStateException.class,
                () -> ofertaService.contraOfertaTrabajador(7L, 6L, contra));
        verify(ofertaRepository, never()).save(any(Oferta.class));
    }

    @Test
    void responderOfertaAceptada_cambiaEstadoDeServicio() {
        var cliente = Cliente.builder().id(3L).build();
        var servicio = Servicio.builder()
                .id(20L)
                .cliente(cliente)
                .titulo("Servicio Demo")
                .estado(EstadoServicio.PENDIENTE)
                .fechaEstimada(LocalDateTime.now().plusDays(1))
                .build();
        var trabajador = new Trabajador();
        trabajador.setId(4L);

        var oferta = Oferta.builder()
                .id(8L)
                .servicio(servicio)
                .trabajador(trabajador)
                .estado(EstadoNegociacion.EN_NEGOCIACION)
                .ultimaPropuestaPor(ParticipanteOferta.TRABAJADOR)
                .monto(BigDecimal.valueOf(80))
                .build();

        when(ofertaRepository.findById(8L)).thenReturn(Optional.of(oferta));
        when(ofertaRepository.save(any(Oferta.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentIntegrationService.iniciarPago(oferta)).thenAnswer(inv -> {
            servicio.setEstado(EstadoServicio.PENDIENTE_PAGO);
            oferta.setEstado(EstadoNegociacion.ACEPTADA);
            return oferta;
        });

        var resultado = ofertaService.responderOferta(3L, 8L, "ACCEPT");

        assertTrue(resultado.accepted());
        assertEquals("Pago pendiente de confirmacion", resultado.mensaje());
        assertEquals(EstadoNegociacion.ACEPTADA, oferta.getEstado());

        verify(paymentIntegrationService).iniciarPago(oferta);
        verify(pushNotificationService).notifyCliente(eq(3L), eq("Pago pendiente"), contains("Servicio"));
    }

    @Test
    void responderOfertaAceptada_sinPasarelaNoEnviaRecordatorioDePago() {
        var cliente = Cliente.builder().id(5L).build();
        var servicio = Servicio.builder()
                .id(50L)
                .cliente(cliente)
                .titulo("Servicio sin pasarela")
                .estado(EstadoServicio.PENDIENTE)
                .fechaEstimada(LocalDateTime.now().plusDays(2))
                .build();
        var trabajador = new Trabajador();
        trabajador.setId(6L);

        var oferta = Oferta.builder()
                .id(60L)
                .servicio(servicio)
                .trabajador(trabajador)
                .estado(EstadoNegociacion.EN_NEGOCIACION)
                .ultimaPropuestaPor(ParticipanteOferta.TRABAJADOR)
                .monto(BigDecimal.valueOf(120))
                .build();

        when(ofertaRepository.findById(60L)).thenReturn(Optional.of(oferta));
        when(paymentIntegrationService.iniciarPago(oferta)).thenAnswer(inv -> {
            servicio.setEstado(EstadoServicio.ASIGNADO);
            oferta.setPaymentStatus(PaymentStatus.NOT_REQUIRED);
            oferta.setEstado(EstadoNegociacion.ACEPTADA);
            return oferta;
        });

        var resultado = ofertaService.responderOferta(cliente.getId(), 60L, "ACCEPT");

        assertTrue(resultado.accepted());
        assertEquals("Oferta aceptada y servicio asignado sin pago en linea", resultado.mensaje());
        verify(pushNotificationService, never()).notifyCliente(anyLong(), anyString(), anyString());
    }

    @Test
    void responderOfertaLanzaErrorCuandoServicioExpirado() {
        var cliente = Cliente.builder().id(40L).build();
        var servicio = Servicio.builder()
                .id(30L)
                .cliente(cliente)
                .estado(EstadoServicio.PENDIENTE)
                .fechaEstimada(LocalDateTime.now().minusDays(1))
                .build();
        var trabajador = new Trabajador();
        trabajador.setId(12L);

        var oferta = Oferta.builder()
                .id(15L)
                .servicio(servicio)
                .trabajador(trabajador)
                .estado(EstadoNegociacion.EN_NEGOCIACION)
                .ultimaPropuestaPor(ParticipanteOferta.TRABAJADOR)
                .monto(BigDecimal.valueOf(50))
                .build();

        when(ofertaRepository.findById(15L)).thenReturn(Optional.of(oferta));

        var body = new OfertaService.ResponderOferta();
        body.accept = true;

        assertThrows(IllegalStateException.class,
                () -> ofertaService.responderOferta(cliente.getId(), 15L, body));

        assertEquals(EstadoServicio.CANCELADO, servicio.getEstado());
        verify(servicioRepository).save(servicio);
        verify(ofertaRepository, never()).save(oferta);
    }
}
