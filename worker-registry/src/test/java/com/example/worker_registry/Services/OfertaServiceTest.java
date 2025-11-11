package com.example.worker_registry.Services;

import com.example.worker_registry.Entitys.Cliente;
import com.example.worker_registry.Entitys.EstadoNegociacion;
import com.example.worker_registry.Entitys.EstadoServicio;
import com.example.worker_registry.Entitys.Oferta;
import com.example.worker_registry.Entitys.ParticipanteOferta;
import com.example.worker_registry.Entitys.Servicio;
import com.example.worker_registry.Entitys.Trabajador;
import com.example.worker_registry.Repository.OfertaRepository;
import com.example.worker_registry.Repository.ServicioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class OfertaServiceTest {

    private OfertaRepository ofertaRepository;
    private ServicioRepository servicioRepository;
    private OfertaService ofertaService;

    @BeforeEach
    void setUp() {
        ofertaRepository = Mockito.mock(OfertaRepository.class);
        servicioRepository = Mockito.mock(ServicioRepository.class);
        ofertaService = new OfertaService(ofertaRepository, servicioRepository);
    }

    @Test
    void contraOfertaTrabajador_actualizaMontoYTurno() {
        var cliente = Cliente.builder().id(1L).build();
        var servicio = Servicio.builder()
                .id(10L)
                .cliente(cliente)
                .estado(EstadoServicio.PENDIENTE)
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
                .estado(EstadoServicio.PENDIENTE)
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
        when(servicioRepository.saveAndFlush(any(Servicio.class))).thenAnswer(inv -> inv.getArgument(0));

        var body = new OfertaService.ResponderOferta();
        body.accept = true;

        var resultado = ofertaService.responderOferta(3L, 8L, body);

        assertTrue(resultado.accepted());
        assertEquals("Oferta aceptada", resultado.mensaje());
        assertEquals(EstadoNegociacion.ACEPTADA, oferta.getEstado());
        assertEquals(oferta.getMonto(), oferta.getMontoAcordado());

        verify(servicioRepository).saveAndFlush(servicio);
    }
}
