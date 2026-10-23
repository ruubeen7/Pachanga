package com.pachanga.api.servicio;

import com.pachanga.api.dto.LoginPeticion;
import com.pachanga.api.dto.RegistroPeticion;
import com.pachanga.api.dto.SesionRespuesta;
import com.pachanga.api.dto.UsuarioRespuesta;
import com.pachanga.api.error.ExcepcionApi;
import com.pachanga.api.modelo.Usuario;
import com.pachanga.api.repositorio.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Lógica de registro e inicio de sesión.
 * Los controladores solo reciben la petición y llaman aquí; las reglas viven en los servicios.
 */
@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    @Transactional
    public SesionRespuesta registrar(RegistroPeticion peticion) {
        String email = normalizarEmail(peticion.email());
        if (usuarios.existsByEmail(email)) {
            throw new ExcepcionApi(HttpStatus.CONFLICT, "Ya hay una cuenta con ese email");
        }
        // Guardamos el hash, nunca la contraseña tal cual.
        String hash = passwordEncoder.encode(peticion.password());
        Usuario usuario = usuarios.save(new Usuario(email, peticion.nombre().trim(), hash));
        return crearSesion(usuario);
    }

    @Transactional(readOnly = true)
    public SesionRespuesta iniciarSesion(LoginPeticion peticion) {
        // Mismo mensaje si el email no existe o si la contraseña está mal:
        // así nadie puede averiguar qué emails están registrados.
        Usuario usuario = usuarios.findByEmail(normalizarEmail(peticion.email()))
                .filter(u -> passwordEncoder.matches(peticion.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ExcepcionApi(HttpStatus.UNAUTHORIZED, "Email o contraseña incorrectos"));
        return crearSesion(usuario);
    }

    private SesionRespuesta crearSesion(Usuario usuario) {
        return new SesionRespuesta(tokens.crearToken(usuario), UsuarioRespuesta.de(usuario));
    }

    private static String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
