package com.example.worker_registry.Services;

import com.example.worker_registry.Entitys.Cliente;
import com.example.worker_registry.Entitys.EstadoServicio;
import com.example.worker_registry.Entitys.Servicio;
import com.example.worker_registry.Repository.ClienteRepository;
import com.example.worker_registry.Repository.OfertaRepository;
import com.example.worker_registry.Repository.ServicioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ServicioClienteServiceTest {

    private ServicioRepository servicioRepository;
    private ClienteRepository clienteRepository;
    private OfertaRepository ofertaRepository;
    private ServicioClienteService servicioClienteService;

    @BeforeEach
    void setUp() {
        servicioRepository = Mockito.mock(ServicioRepository.class);
        clienteRepository = Mockito.mock(ClienteRepository.class);
        ofertaRepository = Mockito.mock(OfertaRepository.class);
        servicioClienteService = new ServicioClienteService(servicioRepository, clienteRepository, ofertaRepository);
    }

    @Test
    void eliminarServicio_borraOfertasAntesDelServicio() {
        Long clienteId = 10L;
        Long servicioId = 200L;

        var cliente = Cliente.builder().id(clienteId).build();
        var servicio = Servicio.builder()
                .id(servicioId)
                .cliente(cliente)
                .estado(EstadoServicio.PENDIENTE)
                .build();

        when(servicioRepository.findByIdAndCliente_Id(servicioId, clienteId))
                .thenReturn(Optional.of(servicio));

        var resultado = servicioClienteService.eliminarServicio(clienteId, servicioId);

        assertTrue(resultado.exitoso());
        verify(ofertaRepository).deleteByServicio_Id(servicioId);
        verify(servicioRepository).delete(servicio);
    }
}
