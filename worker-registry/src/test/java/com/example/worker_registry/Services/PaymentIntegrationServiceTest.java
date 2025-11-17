package com.example.worker_registry.Services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import com.example.worker_registry.Entitys.Cliente;
import com.example.worker_registry.Entitys.EstadoNegociacion;
import com.example.worker_registry.Entitys.EstadoServicio;
import com.example.worker_registry.Entitys.Oferta;
import com.example.worker_registry.Entitys.PaymentStatus;
import com.example.worker_registry.Entitys.Servicio;
import com.example.worker_registry.Repository.OfertaRepository;
import com.example.worker_registry.Repository.ServicioRepository;
import com.example.worker_registry.Services.payments.PaymentGatewayClient;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PaymentIntegrationServiceTest {

    @Mock
    private PaymentGatewayClient paymentGatewayClient;
    @Mock
    private OfertaRepository ofertaRepository;
    @Mock
    private ServicioRepository servicioRepository;
    @Mock
    private PushNotificationService pushNotificationService;
    @Mock
    private MailService mailService;

    @InjectMocks
    private PaymentIntegrationService paymentIntegrationService;

    @BeforeEach
    void setup() {
        paymentIntegrationService = new PaymentIntegrationService(
                paymentGatewayClient,
                ofertaRepository,
                servicioRepository,
                pushNotificationService,
                mailService,
                new ObjectMapper());
    }

    @Test
    void iniciarPagoDisparaIntent() {
        Oferta oferta = buildOferta();
        Servicio servicio = oferta.getServicio();

        var response = new PaymentGatewayClient.PaymentIntentResponse(
                "pit_123",
                "REQUIRES_ACTION",
                "sec_123",
                "1",
                BigDecimal.valueOf(1500),
                "MXN",
                Map.of());
        when(paymentGatewayClient.createIntent(any())).thenReturn(response);
        when(ofertaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(servicioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        paymentIntegrationService.iniciarPago(oferta);

        verify(paymentGatewayClient).createIntent(any());
        verify(ofertaRepository).save(oferta);
        verify(servicioRepository).save(servicio);
        assertEquals(PaymentStatus.REQUIRES_ACTION, oferta.getPaymentStatus());
        assertEquals(EstadoServicio.PENDIENTE_PAGO, servicio.getEstado());
    }

    @Test
    void webhookExitosoAsignaServicio() {
        Oferta oferta = buildOferta();
        oferta.setPaymentIntentId("pit_777");
        Servicio servicio = oferta.getServicio();

        when(ofertaRepository.findByPaymentIntentId("pit_777")).thenReturn(Optional.of(oferta));
        when(ofertaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(servicioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = new PaymentGatewayClient.PaymentIntentResponse(
                "pit_777",
                "SUCCEEDED",
                "secret",
                "1",
                oferta.getMonto(),
                "MXN",
                Map.of());

        paymentIntegrationService.procesarWebhook("pit_777", response);

        assertEquals(EstadoServicio.ASIGNADO, servicio.getEstado());
        assertEquals(PaymentStatus.SUCCEEDED, oferta.getPaymentStatus());
        verify(pushNotificationService).notifyCliente(any(), any(), any());
        verify(mailService).send(any(), any(), any());
    }

    @Test
    void webhookFalloRevierteNegociacion() {
        Oferta oferta = buildOferta();
        oferta.setPaymentIntentId("pit_fail");
        oferta.setEstado(EstadoNegociacion.ACEPTADA);
        Servicio servicio = oferta.getServicio();
        servicio.setEstado(EstadoServicio.PENDIENTE_PAGO);

        when(ofertaRepository.findByPaymentIntentId("pit_fail")).thenReturn(Optional.of(oferta));
        when(ofertaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(servicioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = new PaymentGatewayClient.PaymentIntentResponse(
                "pit_fail",
                "FAILED",
                null,
                "1",
                oferta.getMonto(),
                "MXN",
                Map.of());

        paymentIntegrationService.procesarWebhook("pit_fail", response);

        assertEquals(EstadoServicio.PENDIENTE, servicio.getEstado());
        assertEquals(EstadoNegociacion.EN_NEGOCIACION, oferta.getEstado());
        assertEquals(PaymentStatus.FAILED, oferta.getPaymentStatus());
    }

    @Test
    void iniciarPagoFallaPasarela_AsignaSinPago() {
        Oferta oferta = buildOferta();
        oferta.getServicio().setEstado(EstadoServicio.PENDIENTE);
        var trabajador = new com.example.worker_registry.Entitys.Trabajador();
        trabajador.setId(77L);
        trabajador.setNombreCompleto("Trabajador");
        oferta.setTrabajador(trabajador);

        when(paymentGatewayClient.createIntent(any())).thenThrow(new ResourceAccessException("Connection refused"));
        when(ofertaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(servicioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        paymentIntegrationService.iniciarPago(oferta);

        assertEquals(EstadoNegociacion.ACEPTADA, oferta.getEstado());
        assertEquals(PaymentStatus.NOT_REQUIRED, oferta.getPaymentStatus());
        assertEquals(EstadoServicio.ASIGNADO, oferta.getServicio().getEstado());
        assertEquals(trabajador.getId(), oferta.getServicio().getAssignedWorkerId());
    }

    @Test
    void iniciarPagoFallaPorErrorHttp_AsignaSinPago() {
        Oferta oferta = buildOferta();
        oferta.getServicio().setEstado(EstadoServicio.PENDIENTE);

        when(paymentGatewayClient.createIntent(any())).thenThrow(new RestClientException("502 Bad Gateway"));
        when(ofertaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(servicioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        paymentIntegrationService.iniciarPago(oferta);

        assertEquals(PaymentStatus.NOT_REQUIRED, oferta.getPaymentStatus());
        assertEquals(EstadoServicio.ASIGNADO, oferta.getServicio().getEstado());
    }

    private Oferta buildOferta() {
        Cliente cliente = Cliente.builder()
                .id(10L)
                .nombreCompleto("Cliente Test")
                .correo("cliente@test.com")
                .celular("555")
                .contrasena("pass")
                .build();
        Servicio servicio = Servicio.builder()
                .id(20L)
                .cliente(cliente)
                .titulo("Servicio Demo")
                .descripcion("desc")
                .categoria(com.example.worker_registry.Entitys.CategoriaServicio.ASEO)
                .ubicacion("CDMX")
                .fechaEstimada(java.time.LocalDateTime.now().plusDays(1))
                .estado(EstadoServicio.PENDIENTE)
                .build();

        Oferta oferta = Oferta.builder()
                .id(30L)
                .servicio(servicio)
                .monto(BigDecimal.valueOf(1500))
                .estado(EstadoNegociacion.EN_NEGOCIACION)
                .build();
        return oferta;
    }
}
