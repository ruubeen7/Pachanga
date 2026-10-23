package com.pachanga.api.repositorio;

import com.pachanga.api.modelo.Bloqueo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BloqueoRepository extends JpaRepository<Bloqueo, Long> {

    boolean existsByBloqueadorIdAndBloqueadoId(Long bloqueadorId, Long bloqueadoId);

    Optional<Bloqueo> findByBloqueadorIdAndBloqueadoId(Long bloqueadorId, Long bloqueadoId);

    /** Usuarios que ha bloqueado alguien (para su lista de bloqueados). */
    List<Bloqueo> findByBloqueadorId(Long bloqueadorId);
}
