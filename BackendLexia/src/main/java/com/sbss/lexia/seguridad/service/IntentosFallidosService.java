package com.sbss.lexia.seguridad.service;

import com.sbss.lexia.catalogos.service.ConfiguracionService;
import com.sbss.lexia.seguridad.repository.UsuarioRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra un login fallido y bloquea la cuenta al llegar al limite.
 *
 * ES UN BEAN APARTE A PROPOSITO, no un metodo privado de AuthService. Si
 * estuviera en AuthService y AuthService.login() lo llamara con `this.`, el
 * @Transactional(REQUIRES_NEW) de abajo se ignoraria en silencio: Spring
 * implementa @Transactional con un proxy, y una llamada de un metodo a otro
 * DENTRO de la misma clase nunca pasa por el proxy. Es un error clasico y
 * silencioso — compila, no lanza nada, y el contador simplemente nunca se
 * guarda.
 *
 * REQUIRES_NEW importa aqui especificamente: login() esta en su propia
 * transaccion, y BadCredentialsException la marca para rollback. Sin una
 * transaccion NUEVA e independiente para este registro, incrementar el
 * contador se deshaWria junto con todo lo demas al terminar login() —
 * el bloqueo de cuentas dejaria de funcionar sin que ninguna prueba lo
 * notara, porque el 401 seguiria viendose identico.
 */
@Service
@RequiredArgsConstructor
public class IntentosFallidosService {

    /** Se usa solo si `configuracion.intentos_login_maximos` no esta cargado. */
    private static final int LIMITE_POR_DEFECTO = 5;

    private final UsuarioRepository usuarioRepository;
    private final ConfiguracionService configuracionService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarIntentoFallido(String email) {
        // Si el correo no existe, no hay nada que incrementar. No se distingue
        // esto de una password incorrecta en la respuesta HTTP (ver
        // ManejadorGlobalExcepciones): revelarlo permitiria enumerar cuentas.
        usuarioRepository.findByEmail(email).ifPresent(usuario -> {
            int limite = configuracionService.obtenerEntero(
                    usuario.getEstudio().getId(), "intentos_login_maximos", LIMITE_POR_DEFECTO);

            short intentos = (short) (usuario.getIntentosFallidos() + 1);
            usuario.setIntentosFallidos(intentos);

            if (intentos >= limite && !"BLOQUEADO".equals(usuario.getEstado())) {
                usuario.setEstado("BLOQUEADO");
                usuario.setFechaBloqueo(LocalDateTime.now());
            }

            usuarioRepository.save(usuario);
        });
    }
}
