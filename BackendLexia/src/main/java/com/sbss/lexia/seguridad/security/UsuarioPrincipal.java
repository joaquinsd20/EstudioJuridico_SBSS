package com.sbss.lexia.seguridad.security;

import com.sbss.lexia.seguridad.entity.Usuario;
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
 */
@Getter
public class UsuarioPrincipal implements UserDetails {

    private final Long id;
    private final String uuid;
    private final String email;
    private final String passwordHash;
    private final String nombreCompleto;
    private final boolean habilitado;
    private final boolean noBloqueado;
    private final Collection<GrantedAuthority> authorities;

    private UsuarioPrincipal(Usuario usuario, Collection<GrantedAuthority> authorities) {
        this.id = usuario.getId();
        this.uuid = usuario.getUuidPublico();
        this.email = usuario.getEmail();
        this.passwordHash = usuario.getPasswordHash();
        this.nombreCompleto = usuario.getNombreCompleto();
        this.habilitado = Boolean.TRUE.equals(usuario.getActivo())
                && "ACTIVO".equals(usuario.getEstado());
        this.noBloqueado = !"BLOQUEADO".equals(usuario.getEstado());
        this.authorities = authorities;
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
