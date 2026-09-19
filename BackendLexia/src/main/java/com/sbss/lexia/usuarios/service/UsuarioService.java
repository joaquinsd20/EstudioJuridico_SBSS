package com.sbss.lexia.usuarios.service;

import com.sbss.lexia.catalogos.service.ConfiguracionService;
import com.sbss.lexia.shared.exception.ParametroInvalidoException;
import com.sbss.lexia.shared.exception.RecursoNoEncontradoException;
import com.sbss.lexia.shared.security.SesionActual;
import com.sbss.lexia.usuarios.dto.UsuarioDtos.CargaTrabajo;
import com.sbss.lexia.usuarios.dto.UsuarioDtos.CredencialTemporal;
import com.sbss.lexia.usuarios.dto.UsuarioDtos.GuardarUsuarioRequest;
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
    private final ConfiguracionService configuracionService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UsuarioLista> listar(String texto, String rol, String estado) {
        return repository.listar(SesionActual.estudioId(), texto, rol, estado);
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
        String uuid = repository.crear(estudioId, repository.siguienteCodigo(estudioId),
                passwordEncoder.encode(temporal), SesionActual.usuarioId(), peticion);
        repository.reemplazarRoles(repository.idDe(uuid, estudioId), roles, SesionActual.usuarioId());

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
    }

    @Transactional
    public void activar(String uuid) {
        repository.cambiarActivo(exigirId(uuid, SesionActual.estudioId()), true, SesionActual.usuarioId());
    }

    /** Devuelve al trabajo a quien quedo bloqueado por intentos fallidos de login. */
    @Transactional
    public void desbloquear(String uuid) {
        repository.desbloquear(exigirId(uuid, SesionActual.estudioId()), SesionActual.usuarioId());
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

        return new CredencialTemporal(uuid, repository.emailDe(id), temporal,
                "Sus sesiones abiertas quedaron cerradas. Debera cambiarla al entrar.");
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
