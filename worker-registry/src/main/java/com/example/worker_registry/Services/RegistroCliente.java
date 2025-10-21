package com.example.worker_registry.Services;

import com.example.worker_registry.Entitys.Cliente;
import com.example.worker_registry.Repository.ClienteRepository;
import com.example.worker_registry.securtity.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class RegistroCliente {

    private final ClienteRepository clienteRepository;
    private final JwtService jwtService;
    private final MailService mailService;

    // Encoder local
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    // Para construir el enlace de activación
    @Value("${app.base-url}")
    private String baseUrl;

    public RegistroCliente(ClienteRepository clienteRepository,
                           JwtService jwtService,
                           MailService mailService) {
        this.clienteRepository = clienteRepository;
        this.jwtService = jwtService;
        this.mailService = mailService;
    }

    /**
     * ✅ Mantiene tu lógica y agrega:
     * - Validación confirmarContrasena
     * - Encriptación de contraseña
     * - Marcado como inactivo
     * - Envío de email con enlace de verificación (JWT)
     */
    @Transactional
    public Cliente registrarCliente(Cliente cliente) {
        // Validaciones básicas (además de @Valid en el controller si lo usas)
        if (cliente.getContrasena() == null || cliente.getConfirmarContrasena() == null) {
            throw new IllegalArgumentException("Debe ingresar y confirmar la contraseña");
        }
        if (!cliente.getContrasena().equals(cliente.getConfirmarContrasena())) {
            throw new IllegalArgumentException("Las contraseñas no coinciden");
        }

        // Unicidad por correo (tu lógica original)
        if (clienteRepository.existsByCorreo(cliente.getCorreo())) {
            throw new RuntimeException("El correo ya está registrado");
        }

        // (Opcional) Unicidad por celular si agregaste existsByCelular en el repositorio
        try {
            var m = clienteRepository.getClass().getMethod("existsByCelular", String.class);
            boolean exists = (boolean) m.invoke(clienteRepository, cliente.getCelular());
            if (exists) throw new RuntimeException("El número de celular ya está registrado");
        } catch (NoSuchMethodException ignored) {
            // Si no existe el método en el repo, omitimos la validación de celular
        } catch (Exception e) {
            throw new RuntimeException("Error validando celular: " + e.getMessage());
        }

        // Encriptar contraseña y preparar entidad
        cliente.setContrasena(encoder.encode(cliente.getContrasena()));
        cliente.setConfirmarContrasena(null); // no persistir campo de confirmación
        cliente.setActivo(false);             // pendiente de verificación

        // Guardar
        Cliente saved = clienteRepository.save(cliente);

        // Generar token de activación
        String token = jwtService.generateActivationToken(saved.getId());

        // Construir enlace para verificación (ajusta ruta si usas otra convención)
        String link = baseUrl + "/api/v1/auth/clients/verify?token=" + token; 

        // Enviar correo
        String subject = "Activa tu cuenta Conecta2 (Cliente)";
        String body = """
                Hola %s,

                Gracias por registrarte en Conecta2. Para activar tu cuenta, haz clic en el siguiente enlace:

                %s

                Si no solicitaste este registro, ignora este mensaje.

                Saludos,
                Equipo Conecta2
                """.formatted(saved.getNombreCompleto(), link);

        mailService.send(saved.getCorreo(), subject, body);

        return saved;
    }

    /**
     * ✅ Mantiene tu lógica original.
     */
    public Cliente obtenerClientePorCorreo(String correo) {
        return clienteRepository.findByCorreo(correo)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
    }

    /**
     * ✅ Mantiene tu lógica original.
     */
    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    /**
     * 🔹 Nuevo: activar cuenta desde el controlador de verificación.
     */
    @Transactional
    public void activarCuenta(Long id) {
        Cliente c = clienteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no existe"));
        if (!c.isActivo()) {
            c.setActivo(true);
            clienteRepository.save(c);
        }
    }

    /**
     * 🔹 Nuevo (opcional): reenvío de activación.
     */
    public void reenviarActivacion(String email) {
        Cliente c = clienteRepository.findByCorreo(email)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        if (c.isActivo()) {
            throw new IllegalStateException("La cuenta ya está activa");
        }

        String token = jwtService.generateActivationToken(c.getId());
        String link = baseUrl + "/api/v1/clients/auth/verify?token=" + token;

        String subject = "Reenvío: activa tu cuenta Conecta2 (Cliente)";
        String body = """
                Hola %s,

                Aquí tienes un nuevo enlace para activar tu cuenta:

                %s

                Saludos,
                Equipo Conecta2
                """.formatted(c.getNombreCompleto(), link);

        mailService.send(c.getCorreo(), subject, body);
    }
    /**
     * Permite que un cliente actualice los datos de su cuenta.
     */
    @Transactional
    public Cliente actualizarCliente(Long id, Cliente cambios) {
        Cliente existente = clienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        if (cambios.getNombreCompleto() != null && !cambios.getNombreCompleto().isBlank()) {
            existente.setNombreCompleto(cambios.getNombreCompleto());
        }

        if (cambios.getCorreo() != null && !cambios.getCorreo().isBlank()
                && !cambios.getCorreo().equalsIgnoreCase(existente.getCorreo())) {
            clienteRepository.findByCorreo(cambios.getCorreo())
                    .filter(otro -> !otro.getId().equals(id))
                    .ifPresent(otro -> { throw new RuntimeException("El correo ya esta registrado"); });
            existente.setCorreo(cambios.getCorreo());
        }

        if (cambios.getCelular() != null && !cambios.getCelular().isBlank()
                && !cambios.getCelular().equals(existente.getCelular())) {
            clienteRepository.findByCelular(cambios.getCelular())
                    .filter(otro -> !otro.getId().equals(id))
                    .ifPresent(otro -> { throw new RuntimeException("El numero de celular ya esta registrado"); });
            existente.setCelular(cambios.getCelular());
        }

        if (cambios.getContrasena() != null || cambios.getConfirmarContrasena() != null) {
            if (cambios.getContrasena() == null || cambios.getConfirmarContrasena() == null) {
                throw new IllegalArgumentException("Debe ingresar y confirmar la contrasena");
            }
            if (!cambios.getContrasena().equals(cambios.getConfirmarContrasena())) {
                throw new IllegalArgumentException("Las contrasenas no coinciden");
            }
            existente.setContrasena(encoder.encode(cambios.getContrasena()));
        }

        return clienteRepository.save(existente);
    }
}
