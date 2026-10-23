package com.pachanga.api.repositorio;

import com.pachanga.api.modelo.Inscripcion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InscripcionRepository extends JpaRepository<Inscripcion, Long> {

    /** Cuántos apuntados tiene un evento (para saber si quedan plazas). */
    long countByEventoId(Long eventoId);

    boolean existsByEventoIdAndUsuarioId(Long eventoId, Long usuarioId);

    /** Para desapuntarse: buscamos la inscripción y la borramos. */
    Optional<Inscripcion> findByEventoIdAndUsuarioId(Long eventoId, Long usuarioId);

    /** Lista de apuntados a un evento, por orden de llegada. */
    List<Inscripcion> findByEventoIdOrderByCreadoEnAsc(Long eventoId);

    /** Eventos a los que se ha apuntado un usuario (para "Mis eventos"). */
    List<Inscripcion> findByUsuarioId(Long usuarioId);
}
