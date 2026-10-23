package com.pachanga.api.repositorio;

import com.pachanga.api.modelo.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
