package com.sbss.lexia.seguridad.service;

import com.sbss.lexia.seguridad.repository.UsuarioRepository;
import com.sbss.lexia.seguridad.security.UsuarioPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga el usuario para Spring Security.
 *
 * Usa findByEmailConRoles, que trae roles y permisos con JOIN FETCH en una sola
 * consulta. No es una optimizacion: con `open-in-view: false` la sesion de
 * Hibernate ya esta cerrada cuando se arman las authorities, asi que las
 * colecciones LAZY fallarian.
 */
@Service
@RequiredArgsConstructor
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional(readOnly = true)
    public UsuarioPrincipal loadUserByUsername(String email) {
        return usuarioRepository.findByEmailConRoles(email)
                .map(UsuarioPrincipal::desde)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales invalidas"));
    }
}
