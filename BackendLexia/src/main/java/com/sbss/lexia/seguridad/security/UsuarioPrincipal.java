package com.sbss.lexia.seguridad.security;

import com.sbss.lexia.seguridad.entity.Usuario;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Usuario autenticado, tal como lo ve Spring Security.
 *
 * Las authorities salen de `roles.codigo` y `permisos.codigo`, NUNCA de
 * `roles.nombre`: el nombre es editable desde Configuracion y derivar permisos de
 * el significaria que corregir una tilde revoca accesos en silencio.
 *
 * Guarda el id BIGINT porque la auditoria lo necesita, pero este objeto no se
 * serializa a JSON: hacia Angular va UsuarioAutenticadoDto, que expone el uuid.
 *
 * Tambien guarda `estudioId`: es la frontera de multi-tenencia. Toda consulta
 * del CRM (clientes, expedientes...) debe acotarse con este valor — ver
 * SesionActual. Deliberadamente NO va en el JWT: el token dura 8 horas, y si un
 * administrador reasigna a alguien de estudio, meterlo en el token dejaria a
 * esa persona viendo datos del estudio anterior hasta que expire, sin que
 * ningun log lo delate. Se lee de la base en cada peticion, vía el JOIN FETCH
 * de UsuarioRepository.findByEmailConRoles.
 */
@Getter
public class UsuarioPrincipal implements UserDetails {

    private final Long id;
    private final String uuid;
    private final Integer estudioId;

    /**
     * Solo tiene valor cuando tipoUsuario = CLIENTE: es el cliente del portal
     * al que pertenece este usuario. Junto con tipoUsuario es lo que permite
     * acotar el portal a los expedientes de SU cliente y no a los de todo el
     * estudio. Sale del Usuario ya cargado, sin consulta extra.
     */
    private final Long clienteId;

    /** INTERNO (personal del estudio) o CLIENTE (portal). */
    private final String tipoUsuario;
    private final String email;
    private final String passwordHash;
    private final String nombreCompleto;
    private final boolean habilitado;
    private final boolean noBloqueado;
    /** Mientras sea true, solo puede consultar /auth/yo y cambiar su contrasena. */
    private final boolean debeCambiarPassword;
    /** Version vigente de la contrasena (ver JwtService). Null = nunca se cambio. */
    private final Instant fechaCambioPassword;
    private final Collection<GrantedAuthority> authorities;

    private UsuarioPrincipal(Usuario usuario, Collection<GrantedAuthority> authorities) {
        this.id = usuario.getId();
        this.uuid = usuario.getUuidPublico();
        this.estudioId = usuario.getEstudio().getId();
        this.clienteId = usuario.getClienteId();
        this.tipoUsuario = usuario.getTipoUsuario();
        this.email = usuario.getEmail();
        this.passwordHash = usuario.getPasswordHash();
        this.nombreCompleto = usuario.getNombreCompleto();
        this.habilitado = Boolean.TRUE.equals(usuario.getActivo())
                && "ACTIVO".equals(usuario.getEstado());
        this.noBloqueado = !"BLOQUEADO".equals(usuario.getEstado());
        this.debeCambiarPassword = Boolean.TRUE.equals(usuario.getDebeCambiarPassword());
        // LocalDateTime.now() se escribe y se relee con la zona de la JVM, asi
        // que se convierte con esa misma zona para compararla con el `iat`.
        this.fechaCambioPassword = usuario.getFechaCambioPassword() == null ? null
                : usuario.getFechaCambioPassword().atZone(ZoneId.systemDefault()).toInstant();
        this.authorities = authorities;
    }

    /**
     * True si es un usuario del portal del cliente. Lo decide tipoUsuario y no
     * el rol: el rol es configurable desde Configuracion, el tipo es un hecho
     * estructural de la fila.
     */
    public boolean esPortalCliente() {
        return "CLIENTE".equals(tipoUsuario);
    }

    public static UsuarioPrincipal desde(Usuario usuario) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        usuario.getRoles().forEach(rol -> {
            authorities.add(new SimpleGrantedAuthority(rol.getCodigo()));
            rol.getPermisos().forEach(p -> authorities.add(new SimpleGrantedAuthority(p.getCodigo())));
        });
        return new UsuarioPrincipal(usuario, authorities);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** El nombre de usuario en LEXIA es el correo. */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonLocked() {
        return noBloqueado;
    }

    @Override
    public boolean isEnabled() {
        return habilitado;
    }
}
