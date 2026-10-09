package com.sbss.lexia.usuarios.service;

import com.sbss.lexia.catalogos.service.ConfiguracionService;
import com.sbss.lexia.expedientes.repository.ExpedienteRepositoryJdbc;
import com.sbss.lexia.shared.dto.PaginaDto;
import com.sbss.lexia.shared.exception.ParametroInvalidoException;
import com.sbss.lexia.shared.exception.RecursoNoEncontradoException;
import com.sbss.lexia.shared.security.SesionActual;
import com.sbss.lexia.usuarios.dto.UsuarioDtos.CargaDetallada;
import com.sbss.lexia.usuarios.dto.UsuarioDtos.CargaTrabajo;
import com.sbss.lexia.usuarios.dto.UsuarioDtos.CredencialTemporal;
import com.sbss.lexia.usuarios.dto.UsuarioDtos.GuardarUsuarioRequest;
import com.sbss.lexia.usuarios.dto.UsuarioDtos.ReasignarRequest;
import com.sbss.lexia.usuarios.dto.UsuarioDtos.ResultadoReasignacion;
import com.sbss.lexia.usuarios.dto.UsuarioDtos.ResumenUsuarios;
import com.sbss.lexia.usuarios.dto.UsuarioDtos.RolOpcion;
import com.sbss.lexia.usuarios.dto.UsuarioDtos.UsuarioDetalle;
import com.sbss.lexia.usuarios.dto.UsuarioDtos.UsuarioLista;
import com.sbss.lexia.usuarios.repository.UsuarioGestionRepositoryJdbc;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta, edicion y ciclo de vida del personal del estudio.
 *
 * Lo que este modulo protege de verdad no son los datos del usuario, sino sus
 * ENLACES: doce claves foraneas NOT NULL apuntan a `usuarios` (expedientes,
 * plazos, tareas, actuaciones, participantes de agenda, versiones de
 * documento...). Un usuario nunca se borra, y desactivarlo lo saca de todos los
 * selectores —el de abogados de clientes, el de responsables de expedientes—
 * dejando sus compromisos a nombre de una cuenta que ya nadie mira. Por eso la
 * baja se bloquea mientras tenga trabajo vivo.
 */
@Service
@RequiredArgsConstructor
public class UsuarioService {

    /**
     * Sin caracteres ambiguos (I, l, 1, O, 0): esta contrasena se dicta por
     * telefono o se copia a mano, y confundir un cero con una O se convierte en
     * "no puedo entrar".
     */
    private static final String ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int LARGO_TEMPORAL = 14;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UsuarioGestionRepositoryJdbc repository;
    /** Para cambiar responsables con la misma logica y auditoria que la edicion. */
    private final ExpedienteRepositoryJdbc expedienteRepository;
    private final ConfiguracionService configuracionService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UsuarioLista> listar(String texto, String rol, String estado) {
        return repository.listar(SesionActual.estudioId(), texto, rol, estado);
    }

    @Transactional(readOnly = true)
    public PaginaDto<UsuarioLista> listarPagina(String texto, String rol, String estado, int page, int size) {
        if (page < 0) {
            throw new ParametroInvalidoException("La pagina no puede ser negativa");
        }
        if (size != 10 && size != 20 && size != 40 && size != 100) {
            throw new ParametroInvalidoException("El tamano de pagina debe ser 10, 20, 40 o 100");
        }
        return repository.listarPagina(SesionActual.estudioId(), texto, rol, estado, page, size);
    }

    @Transactional(readOnly = true)
    public ResumenUsuarios resumen() {
        return repository.resumen(SesionActual.estudioId());
    }

    @Transactional(readOnly = true)
    public UsuarioDetalle detalle(String uuid) {
        UsuarioDetalle detalle = repository.detalle(uuid, SesionActual.estudioId());
        if (detalle == null) {
            throw noEncontrado();
        }
        return detalle;
    }

    @Transactional(readOnly = true)
    public List<RolOpcion> roles() {
        return repository.roles();
    }

    @Transactional
    public CredencialTemporal crear(GuardarUsuarioRequest peticion) {
        Integer estudioId = SesionActual.estudioId();
        String email = normalizar(peticion.email());

        validarDominio(email, estudioId);
        if (repository.existeEmail(email)) {
            throw new ParametroInvalidoException("Ya existe un usuario con el correo " + email);
        }
        List<String> roles = validarRoles(peticion.roles());

        String temporal = generarPasswordTemporal();
        // Bloquea la fila del estudio durante la asignacion del codigo U-XXXX.
        // Dos altas simultaneas del mismo estudio ya no pueden calcular el mismo MAX+1.
        String codigo = repository.siguienteCodigoBloqueado(estudioId);
        String uuid = repository.crear(estudioId, codigo,
                passwordEncoder.encode(temporal), SesionActual.usuarioId(), peticion);
        Long nuevoId = repository.idDe(uuid, estudioId);
        repository.reemplazarRoles(nuevoId, roles, SesionActual.usuarioId());
        repository.auditar(estudioId, SesionActual.usuarioId(), "usuarios", nuevoId, null, "CREAR",
                "Usuario creado: " + codigo + " - " + peticion.nombres().trim() + " "
                        + peticion.apellidos().trim() + ". Roles: " + String.join(", ", roles) + ".");

        return new CredencialTemporal(uuid, email, temporal,
                "Entregala en persona. No se vuelve a mostrar y debera cambiarla al entrar.");
    }

    /**
     * Edicion de datos y roles. El correo NO se puede cambiar: es el nombre de
     * usuario del login y viaja dentro del token, asi que cambiarlo dejaria
     * fuera a la persona en mitad de su sesion. Para eso se crea otra cuenta.
     */
    @Transactional
    public void actualizar(String uuid, GuardarUsuarioRequest peticion) {
        Integer estudioId = SesionActual.estudioId();
        Long id = exigirId(uuid, estudioId);

        if (!normalizar(peticion.email()).equals(repository.emailDe(id))) {
            throw new ParametroInvalidoException(
                    "El correo no se puede cambiar: es el usuario del login. Crea otra cuenta si hace falta");
        }
        List<String> roles = validarRoles(peticion.roles());
        if (!roles.contains("ADMINISTRADOR")) {
            exigirOtroAdministrador(estudioId, id,
                    "No puedes quitarle el rol de Administrador: es el unico que queda activo");
        }

        repository.actualizarDatos(id, SesionActual.usuarioId(), peticion);
        repository.reemplazarRoles(id, roles, SesionActual.usuarioId());
        repository.auditar(estudioId, SesionActual.usuarioId(), "usuarios", id, null, "ACTUALIZAR",
                "Datos y roles del usuario actualizados. Roles: " + String.join(", ", roles) + ".");
    }

    /**
     * Baja logica. Se niega si la persona sigue siendo responsable de algo:
     * expedientes, plazos abiertos o tareas abiertas. La alternativa —dar de
     * baja y que el trabajo quede a nombre de una cuenta inactiva— es la forma
     * mas silenciosa de perder un plazo, que es justo lo que LEXIA existe para
     * evitar.
     */
    @Transactional
    public void desactivar(String uuid) {
        Integer estudioId = SesionActual.estudioId();
        Long id = exigirId(uuid, estudioId);

        if (id.equals(SesionActual.usuarioId())) {
            throw new ParametroInvalidoException("No puedes desactivar tu propia cuenta");
        }
        exigirOtroAdministrador(estudioId, id,
                "No puedes desactivar al unico Administrador activo del estudio");

        CargaTrabajo carga = repository.cargaTrabajo(id);
        if (carga.bloqueaBaja()) {
            throw new ParametroInvalidoException(
                    "Antes de dar de baja hay que reasignar: " + describir(carga)
                    + ". Mientras tanto quedarian a nombre de una cuenta inactiva");
        }
        repository.cambiarActivo(id, false, SesionActual.usuarioId());
        repository.auditar(estudioId, SesionActual.usuarioId(), "usuarios", id, null, "ACTUALIZAR",
                "Usuario desactivado.");
    }

    @Transactional
    public void activar(String uuid) {
        Integer estudioId = SesionActual.estudioId();
        Long id = exigirId(uuid, estudioId);
        repository.cambiarActivo(id, true, SesionActual.usuarioId());
        repository.auditar(estudioId, SesionActual.usuarioId(), "usuarios", id, null, "ACTUALIZAR",
                "Usuario activado.");
    }

    /** Devuelve al trabajo a quien quedo bloqueado por intentos fallidos de login. */
    @Transactional
    public void desbloquear(String uuid) {
        Integer estudioId = SesionActual.estudioId();
        Long id = exigirId(uuid, estudioId);
        repository.desbloquear(id, SesionActual.usuarioId());
        repository.auditar(estudioId, SesionActual.usuarioId(), "usuarios", id, null, "ACTUALIZAR",
                "Usuario desbloqueado y contador de intentos fallidos reiniciado.");
    }

    /**
     * Genera una contrasena temporal nueva. Ademas de obligar a cambiarla,
     * cierra las sesiones abiertas de esa persona (ver V13): si se restablece
     * porque la cuenta esta comprometida, dejar viva la sesion del intruso
     * haria inutil el restablecimiento.
     */
    @Transactional
    public CredencialTemporal restablecerPassword(String uuid) {
        Integer estudioId = SesionActual.estudioId();
        Long id = exigirId(uuid, estudioId);

        String temporal = generarPasswordTemporal();
        repository.restablecerPassword(id, passwordEncoder.encode(temporal), SesionActual.usuarioId());
        repository.auditar(estudioId, SesionActual.usuarioId(), "usuarios", id, null, "ACTUALIZAR",
                "Contrasena restablecida: se genero una credencial temporal y se invalidaron las sesiones anteriores.");

        return new CredencialTemporal(uuid, repository.emailDe(id), temporal,
                "Sus sesiones abiertas quedaron cerradas. Debera cambiarla al entrar.");
    }

    // -------------------------------------------------------------------------
    // Reasignacion de trabajo
    // -------------------------------------------------------------------------

    /** Lo concreto que tiene a su nombre y a quien se le puede traspasar. */
    @Transactional(readOnly = true)
    public CargaDetallada cargaDetallada(String uuid) {
        Integer estudioId = SesionActual.estudioId();
        return repository.cargaDetallada(exigirId(uuid, estudioId), estudioId);
    }

    /**
     * Traspasa el trabajo vivo de una persona, en UNA transaccion: o se mueve
     * todo o no se mueve nada. Orden deliberado:
     *
     * 1. Expedientes: con la logica del modulo de expedientes, para que el
     *    cambio quede en la linea de tiempo y el equipo quede coherente.
     * 2. Plazos: siguen al responsable de su expediente, que tras el paso 1 ya
     *    es el nuevo. Se llevan sus alertas todavia programadas.
     * 3. Actuaciones abiertas: igual que los plazos, siguen a su expediente,
     *    con las alertas que cuelgan de ellas.
     * 4. Tareas y clientes: a su destino.
     * 5. Apoyo en otros equipos: se retira (no se traspasa: el equipo lo decide
     *    el responsable de cada expediente).
     *
     * Despues de esto la carga que bloquea la baja queda en cero.
     */
    @Transactional
    public ResultadoReasignacion reasignar(String uuid, ReasignarRequest peticion) {
        Integer estudioId = SesionActual.estudioId();
        Long autorId = SesionActual.usuarioId();
        Long origenId = exigirId(uuid, estudioId);
        String origen = repository.nombreDe(origenId);

        List<Long> expedientes = repository.expedientesComoResponsable(origenId, estudioId);
        List<long[]> tareas = repository.tareasAbiertas(origenId, estudioId);
        List<Long> clientes = repository.clientesComoResponsable(origenId, estudioId);

        Long expedientesA = expedientes.isEmpty() ? null
                : destino(peticion.expedientesA(), "expedientes", origenId, estudioId, true);
        Long tareasA = tareas.isEmpty() ? null
                : destino(peticion.tareasA(), "tareas", origenId, estudioId, false);
        Long clientesA = clientes.isEmpty() ? null
                : destino(peticion.clientesA(), "clientes", origenId, estudioId, true);

        // 1. Expedientes
        for (Long expedienteId : expedientes) {
            expedienteRepository.cambiarResponsable(estudioId, expedienteId, expedientesA, autorId);
        }

        // 2. Plazos: al responsable ACTUAL de su expediente
        int plazos = 0;
        int alertas = 0;
        List<long[]> plazosAbiertos = repository.plazosAbiertos(origenId, estudioId);
        List<long[]> actuacionesAbiertas = repository.actuacionesAbiertas(origenId, estudioId);
        java.util.Map<Long, String> nombresDestino = new java.util.HashMap<>();
        if (expedientesA != null) nombresDestino.put(expedientesA, repository.nombreDe(expedientesA));
        if (tareasA != null) nombresDestino.put(tareasA, repository.nombreDe(tareasA));
        if (clientesA != null) nombresDestino.put(clientesA, repository.nombreDe(clientesA));
        for (long[] plazo : plazosAbiertos) {
            long plazoId = plazo[0];
            long expedienteId = plazo[1];
            long responsableExpediente = plazo[2];
            // Si el expediente sigue a su nombre (p.ej. esta archivado y no entro
            // en el paso 1), el plazo va al destino de los expedientes.
            Long destino = responsableExpediente != origenId ? responsableExpediente : expedientesA;
            if (destino == null) {
                throw new ParametroInvalidoException("Hay plazos en expedientes que siguen a nombre de "
                        + origen + ": indica a quien pasan sus expedientes");
            }
            alertas += repository.moverPlazo(plazoId, destino);
            repository.auditar(estudioId, autorId, "plazos", plazoId, expedienteId, "ACTUALIZAR",
                    "Plazo reasignado de " + origen + " a " + nombresDestino.computeIfAbsent(destino, repository::nombreDe) + ".");
            plazos++;
        }

        // 3. Actuaciones abiertas: misma regla que los plazos
        int actuaciones = 0;
        for (long[] actuacion : actuacionesAbiertas) {
            long actuacionId = actuacion[0];
            long expedienteId = actuacion[1];
            long responsableExpediente = actuacion[2];
            Long destino = responsableExpediente != origenId ? responsableExpediente : expedientesA;
            if (destino == null) {
                throw new ParametroInvalidoException("Hay actuaciones en expedientes que siguen a nombre de "
                        + origen + ": indica a quien pasan sus expedientes");
            }
            alertas += repository.moverActuacion(actuacionId, destino, origenId);
            repository.auditar(estudioId, autorId, "actuaciones", actuacionId, expedienteId, "ACTUALIZAR",
                    "Actuacion reasignada de " + origen + " a " + nombresDestino.computeIfAbsent(destino, repository::nombreDe) + ".");
            actuaciones++;
        }

        // 4. Tareas y clientes. Los UPDATE se envian por lote; la auditoria sigue siendo por registro.
        repository.moverTareas(tareas, tareasA);
        String nombreTareas = tareasA == null ? null : nombresDestino.get(tareasA);
        for (long[] tarea : tareas) {
            repository.auditar(estudioId, autorId, "tareas", tarea[0], tarea[1], "ACTUALIZAR",
                    "Tarea reasignada de " + origen + " a " + nombreTareas + ".");
        }

        repository.moverClientes(clientes, clientesA, autorId);
        String nombreClientes = clientesA == null ? null : nombresDestino.get(clientesA);
        for (Long clienteId : clientes) {
            repository.auditar(estudioId, autorId, "clientes", clienteId, null, "ACTUALIZAR",
                    "Abogado responsable cambiado de " + origen + " a " + nombreClientes + ".");
        }

        // 5. Apoyo en otros equipos
        int equipos = 0;
        for (Long expedienteId : repository.expedientesComoApoyo(origenId, estudioId)) {
            equipos += expedienteRepository.retirarDelEquipo(estudioId, expedienteId, origenId, autorId);
        }

        ResultadoReasignacion resultado = new ResultadoReasignacion(
                expedientes.size(), plazos, alertas, tareas.size(), actuaciones, clientes.size(), equipos);
        repository.auditar(estudioId, autorId, "usuarios", origenId, null, "ACTUALIZAR",
                "Reasignacion del trabajo de " + origen + ": " + resultado.expedientes() + " expediente(s), "
                        + plazos + " plazo(s), " + tareas.size() + " tarea(s), " + actuaciones
                        + " actuacion(es), " + clientes.size()
                        + " cliente(s); retirado de " + equipos + " equipo(s).");
        return resultado;
    }

    /**
     * Resuelve y valida un destino. Obligatorio solo si hay trabajo de esa
     * categoria. Debe ser del mismo estudio, interno, activo y distinto del que
     * se va; para expedientes y clientes, ademas, con rol de abogado.
     */
    private Long destino(String uuid, String categoria, Long origenId, Integer estudioId, boolean exigeAbogado) {
        if (uuid == null || uuid.isBlank()) {
            throw new ParametroInvalidoException("Indica a quien pasan los " + categoria);
        }
        Long id = repository.destinoActivo(uuid, estudioId);
        if (id == null) {
            throw new ParametroInvalidoException("El destino de los " + categoria
                    + " no existe en el estudio o no esta activo");
        }
        if (id.equals(origenId)) {
            throw new ParametroInvalidoException("Los " + categoria + " no pueden reasignarse a la misma persona");
        }
        if (exigeAbogado && !repository.esAbogado(id)) {
            throw new ParametroInvalidoException("Los " + categoria
                    + " solo pueden pasar a alguien con rol de abogado, socio o administrador");
        }
        return id;
    }

    // -------------------------------------------------------------------------

    private Long exigirId(String uuid, Integer estudioId) {
        Long id = repository.idDe(uuid, estudioId);
        if (id == null) {
            throw noEncontrado();
        }
        return id;
    }

    /** 404 y no 403: confirmar que el uuid existe ya seria filtrar informacion. */
    private RecursoNoEncontradoException noEncontrado() {
        return new RecursoNoEncontradoException("Usuario no encontrado");
    }

    private void exigirOtroAdministrador(Integer estudioId, Long usuarioId, String mensaje) {
        if (repository.administradoresActivosSalvo(estudioId, usuarioId) == 0) {
            throw new ParametroInvalidoException(mensaje);
        }
    }

    /**
     * El correo del personal debe ser del dominio del estudio. Vive en
     * `configuracion.dominio_correo_interno` y no en un CHECK, porque cada
     * estudio tiene el suyo y se edita desde Configuracion.
     */
    private void validarDominio(String email, Integer estudioId) {
        String dominio = configuracionService.obtenerTexto(estudioId, "dominio_correo_interno", null);
        if (dominio != null && !email.endsWith("@" + dominio.toLowerCase(Locale.ROOT))) {
            throw new ParametroInvalidoException(
                    "El correo del personal del estudio debe terminar en @" + dominio);
        }
    }

    private List<String> validarRoles(List<String> solicitados) {
        List<String> codigos = solicitados.stream()
                .filter(c -> c != null && !c.isBlank())
                .map(c -> c.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        if (codigos.contains("CLIENTE")) {
            throw new ParametroInvalidoException(
                    "El rol Cliente es del portal y se asigna desde la ficha del cliente, no aqui");
        }
        List<String> existentes = repository.codigosDeRolExistentes(codigos);
        if (existentes.size() != codigos.size()) {
            throw new ParametroInvalidoException("Hay roles que no existen: "
                    + codigos.stream().filter(c -> !existentes.contains(c)).toList());
        }
        return codigos;
    }

    private static String describir(CargaTrabajo c) {
        StringBuilder texto = new StringBuilder();
        if (c.expedientes() > 0) texto.append(c.expedientes()).append(" expediente(s), ");
        if (c.plazosAbiertos() > 0) texto.append(c.plazosAbiertos()).append(" plazo(s) abierto(s), ");
        if (c.tareasAbiertas() > 0) texto.append(c.tareasAbiertas()).append(" tarea(s) abierta(s), ");
        if (c.actuacionesAbiertas() > 0) texto.append(c.actuacionesAbiertas()).append(" actuacion(es) abierta(s), ");
        if (c.clientes() > 0) texto.append(c.clientes()).append(" cliente(s), ");
        if (c.equiposApoyo() > 0) texto.append("apoyo en ").append(c.equiposApoyo()).append(" equipo(s), ");
        return texto.substring(0, texto.length() - 2);
    }

    private static String normalizar(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String generarPasswordTemporal() {
        StringBuilder password = new StringBuilder(LARGO_TEMPORAL);
        for (int i = 0; i < LARGO_TEMPORAL; i++) {
            password.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
        }
        return password.toString();
    }
}
