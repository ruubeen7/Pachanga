package com.pachanga.api.servicio;

import com.pachanga.api.modelo.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/** Crea los tokens JWT de sesión. */
@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final Duration validez;

    public TokenService(JwtEncoder jwtEncoder, @Value("${pachanga.jwt.dias-validez}") long diasValidez) {
        this.jwtEncoder = jwtEncoder;
        this.validez = Duration.ofDays(diasValidez);
    }

    /**
     * Un JWT lleva dentro unos datos ("claims") y una firma. Guardamos:
     *  - sub (subject): el id del usuario. Es lo que usamos luego para saber quién hace cada petición.
     *  - iat / exp: cuándo se creó y cuándo caduca.
     * Ojo: el contenido de un JWT se puede leer (no está cifrado, solo firmado), así que nunca
     * se mete nada secreto dentro.
     */
    public String crearToken(Usuario usuario) {
        Instant ahora = Instant.now();
        JwtClaimsSet datos = JwtClaimsSet.builder()
                .issuer("pachanga-api")
                .subject(usuario.getId().toString())
                .issuedAt(ahora)
                .expiresAt(ahora.plus(validez))
                .build();
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(cabecera, datos)).getTokenValue();
    }
}
