package com.pachanga.api.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Configuración de seguridad de la API.
 *
 * Cómo funciona la sesión:
 *  1. El usuario hace login o se registra y la API le devuelve un token (JWT): un texto firmado
 *     con nuestra clave secreta que dice "este es el usuario 5, válido hasta tal fecha".
 *  2. La app guarda el token y lo manda en cada petición en la cabecera
 *     "Authorization: Bearer <token>".
 *  3. Spring comprueba la firma en cada petición. Si alguien modifica el token, la firma
 *     ya no cuadra y se rechaza. Así no hace falta guardar sesiones en el servidor.
 */
@Configuration
public class SeguridadConfig {

    /** Qué rutas son públicas y cuáles necesitan token. */
    @Bean
    public SecurityFilterChain filtrosDeSeguridad(HttpSecurity http) throws Exception {
        http
                // CSRF protege formularios web con cookies. Nosotros usamos tokens, así que no aplica.
                .csrf(csrf -> csrf.disable())
                // Sin sesiones en el servidor: cada petición se identifica solo con su token.
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers("/api/salud", "/api/auth/**", "/error").permitAll()
                        // Las fotos se pueden ver sin token (como cualquier imagen de una web);
                        // subirlas sí lo necesita (POST /api/canchas/{id}/fotos).
                        .requestMatchers(HttpMethod.GET, "/fotos/**").permitAll()
                        .anyRequest().authenticated())
                // Activa la comprobación del token JWT en la cabecera Authorization.
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /** BCrypt: convierte la contraseña en un hash que no se puede deshacer. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** La clave secreta para firmar tokens, sacada de application.properties. */
    @Bean
    public SecretKey claveJwt(@Value("${pachanga.jwt.secreto}") String secreto) {
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("pachanga.jwt.secreto debe tener al menos 32 caracteres");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    /** Crea (firma) tokens. Lo usa TokenService al hacer login o registro. */
    @Bean
    public JwtEncoder jwtEncoder(SecretKey claveJwt) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(claveJwt));
    }

    /** Lee y comprueba tokens. Lo usa Spring Security en cada petición. */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey claveJwt) {
        return NimbusJwtDecoder.withSecretKey(claveJwt).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
