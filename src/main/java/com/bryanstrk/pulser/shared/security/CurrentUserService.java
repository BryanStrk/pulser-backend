package com.bryanstrk.pulser.shared.security;

import com.bryanstrk.pulser.usuario.Usuario;
import com.bryanstrk.pulser.usuario.UsuarioRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Resuelve el Usuario autenticado a partir del SecurityContext (el principal solo lleva
 * el email; aqui recuperamos la entidad para poder aplicar reglas de propiedad).
 */
@Service
public class CurrentUserService {

    private final UsuarioRepository usuarioRepository;

    public CurrentUserService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Usuario autenticado obligatorio. Lanza AccessDeniedException (-> 403) si no lo hay.
     */
    @Transactional(readOnly = true)
    public Usuario getCurrentUsuario() {
        return resolver()
                .orElseThrow(() -> new AccessDeniedException("No autenticado"));
    }

    /**
     * Usuario autenticado opcional (para rutas publicas donde el token puede o no venir).
     */
    @Transactional(readOnly = true)
    public Optional<Usuario> getCurrentUsuarioOptional() {
        return resolver();
    }

    private Optional<Usuario> resolver() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return usuarioRepository.findByEmail(auth.getName());
    }
}
