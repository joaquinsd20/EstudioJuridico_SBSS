package com.sbss.lexia.shared.bootstrap;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Crea el primer estudio y su Administrador cuando la base no tiene ninguno.
 *
 * El motivo: produccion no carga la semilla de desarrollo, asi que una
 * instalacion nueva arrancaba sin estudio, sin catalogos y sin un solo usuario
 * que pudiera entrar. Roles y permisos ya vienen de V12; lo que depende de un
 * estudio concreto se crea aqui.
 *
 * Por que no es una migracion: la contrasena del Administrador no puede vivir en
 * git, ni siquiera como hash. Llega por variable de entorno, se usa una vez y
 * deja de leerse en cuanto existe un estudio.
 *
 * Solo actua si `estudios` esta VACIA. En desarrollo la semilla siempre crea el
 * estudio 1, asi que aqui no hace nada. Si la base esta vacia y faltan las
 * variables, la aplicacion NO arranca: un sistema sin nadie capaz de entrar no
 * sirve, y es mejor que falle ruidoso al inicio que descubrirlo en el login.
 *
 * Todo ocurre en UNA transaccion: si el script de catalogos falla a la mitad,
 * no queda un estudio a medio crear que impida reintentar (la proxima vez ya
 * no estaria vacia la tabla y nunca se completaria).
 */
@Component
public class InicializadorEstudio implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorEstudio.class);

    static final String NOMBRE_ESTUDIO = "Estudio SBSS";
    static final int LONGITUD_MINIMA_PASSWORD = 12;

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transaccion;
    private final PasswordEncoder passwordEncoder;

    private final String adminEmail;
    private final String adminPassword;
    private final String adminNombres;
    private final String adminApellidos;
    private final String estudioRuc;

    public InicializadorEstudio(
            NamedParameterJdbcTemplate jdbc,
            TransactionTemplate transaccion,
            PasswordEncoder passwordEncoder,
            @Value("${lexia.inicializacion.admin-email:}") String adminEmail,
            @Value("${lexia.inicializacion.admin-password:}") String adminPassword,
            @Value("${lexia.inicializacion.admin-nombres:Administrador}") String adminNombres,
            @Value("${lexia.inicializacion.admin-apellidos:del sistema}") String adminApellidos,
            @Value("${lexia.inicializacion.estudio-ruc:}") String estudioRuc) {
        this.jdbc = jdbc;
        this.transaccion = transaccion;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
        this.adminPassword = adminPassword;
        this.adminNombres = adminNombres.trim();
        this.adminApellidos = adminApellidos.trim();
        this.estudioRuc = estudioRuc.trim();
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer estudios = jdbc.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM estudios", Integer.class);
        if (estudios != null && estudios > 0) {
            return;
        }

        validarVariables();
        log.info("Base sin estudios: creando '{}' y su Administrador {}", NOMBRE_ESTUDIO, adminEmail);

        transaccion.executeWithoutResult(estado -> {
            long estudioId = crearEstudio();
            long adminId = crearAdministrador(estudioId);
            cargarCatalogos(estudioId, adminId);
        });

        // Nunca se registra la contrasena, ni siquiera su longitud.
        log.info("'{}' creado. Entrar con {} y cambiar la contrasena; despues, "
                + "retirar LEXIA_ADMIN_PASSWORD del entorno: ya no se vuelve a leer.",
                NOMBRE_ESTUDIO, adminEmail);
    }

    private void validarVariables() {
        List<String> errores = new ArrayList<>();
        if (!adminEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")) {
            errores.add("LEXIA_ADMIN_EMAIL falta o no es un correo valido");
        }
        if (adminPassword.length() < LONGITUD_MINIMA_PASSWORD) {
            errores.add("LEXIA_ADMIN_PASSWORD falta o tiene menos de " + LONGITUD_MINIMA_PASSWORD + " caracteres");
        }
        if (!estudioRuc.matches("^[0-9]{11}$")) {
            errores.add("LEXIA_ESTUDIO_RUC falta o no tiene 11 digitos");
        }
        if (adminNombres.isEmpty() || adminApellidos.isEmpty()) {
            errores.add("LEXIA_ADMIN_NOMBRES y LEXIA_ADMIN_APELLIDOS no pueden estar vacios");
        }
        if (!errores.isEmpty()) {
            throw new IllegalStateException("La base no tiene ningun estudio y no se puede crear el primero: "
                    + String.join("; ", errores)
                    + ". Definir esas variables de entorno y volver a arrancar.");
        }
    }

    private long crearEstudio() {
        GeneratedKeyHolder clave = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO estudios (razon_social, nombre_comercial, ruc)
                VALUES (:nombre, :nombre, :ruc)
                """,
                new MapSqlParameterSource()
                        .addValue("nombre", NOMBRE_ESTUDIO)
                        .addValue("ruc", estudioRuc),
                clave, new String[] {"id"});
        return clave.getKey().longValue();
    }

    private long crearAdministrador(long estudioId) {
        GeneratedKeyHolder clave = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO usuarios (estudio_id, tipo_usuario, codigo_usuario, nombres, apellidos,
                                      email, password_hash, cargo, estado, debe_cambiar_password)
                VALUES (:estudioId, 'INTERNO', 'U-0001', :nombres, :apellidos,
                        :email, :hash, 'Administrador del sistema', 'ACTIVO', 1)
                """,
                new MapSqlParameterSource()
                        .addValue("estudioId", estudioId)
                        .addValue("nombres", adminNombres)
                        .addValue("apellidos", adminApellidos)
                        .addValue("email", adminEmail)
                        .addValue("hash", passwordEncoder.encode(adminPassword)),
                clave, new String[] {"id"});
        long adminId = clave.getKey().longValue();

        // Por codigo, no por id (regla 8 de CLAUDE.md). Si no inserta nada es que
        // V12 no se aplico: se aborta la transaccion entera.
        int asignados = jdbc.update("""
                INSERT INTO usuarios_roles (usuario_id, rol_id)
                SELECT :usuarioId, id FROM roles WHERE codigo = 'ADMINISTRADOR'
                """, new MapSqlParameterSource("usuarioId", adminId));
        if (asignados != 1) {
            throw new IllegalStateException("No existe el rol ADMINISTRADOR: falta aplicar V12");
        }
        return adminId;
    }

    private void cargarCatalogos(long estudioId, long adminId) {
        String dominio = adminEmail.substring(adminEmail.indexOf('@') + 1);
        // Misma conexion para el SET y el script: las variables @ son de sesion.
        // Dentro del TransactionTemplate, JdbcTemplate reutiliza la conexion de
        // la transaccion, asi que el script tambien queda dentro de ella.
        jdbc.getJdbcTemplate().execute((ConnectionCallback<Void>) conexion -> {
            try (PreparedStatement ps = conexion.prepareStatement(
                    "SET @estudio_id = ?, @admin_id = ?, @dominio_correo = ?")) {
                ps.setLong(1, estudioId);
                ps.setLong(2, adminId);
                ps.setString(3, dominio);
                ps.execute();
            }
            ScriptUtils.executeSqlScript(conexion, new EncodedResource(
                    new ClassPathResource("db/bootstrap/catalogos-estudio.sql"), StandardCharsets.UTF_8));
            return null;
        });
    }
}
