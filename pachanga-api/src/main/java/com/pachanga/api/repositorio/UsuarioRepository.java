package com.pachanga.api.repositorio;

import com.pachanga.api.modelo.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Acceso a la tabla usuario. Al extender JpaRepository ya tenemos gratis
 * save, findById, findAll, deleteById... Spring genera el SQL a partir del nombre del método.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** SELECT * FROM usuario WHERE email = ? (para el login). */
    Optional<Usuario> findByEmail(String email);

    /** Para avisar en el registro de que el email ya está en uso. */
    boolean existsByEmail(String email);

    /**
     * Quita un token de push de quien lo tenga (UPDATE, no SELECT: por eso @Modifying).
     * Se usa si Expo dice que el móvil ya no existe, y cuando alguien inicia sesión con otra
     * cuenta en el mismo móvil (el token pasa a la cuenta nueva).
     */
    @Modifying
    @Query("update Usuario u set u.pushToken = null where u.pushToken = :token")
    int quitarPushToken(@Param("token") String token);
}
