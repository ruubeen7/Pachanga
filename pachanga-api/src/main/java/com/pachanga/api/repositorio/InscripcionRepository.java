package com.pachanga.api.repositorio;

import com.pachanga.api.modelo.Evento;
import com.pachanga.api.modelo.Inscripcion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /** Eventos a los que se ha apuntado un usuario. */
    List<Inscripcion> findByUsuarioId(Long usuarioId);

    /**
     * Eventos del usuario que aún no han terminado, el más cercano primero.
     * Como el organizador se apunta al crear el evento, aquí salen también los que organiza.
     * "join fetch e.cancha" trae la cancha en la misma consulta (si no, haría una consulta más por evento).
     */
    @Query("""
            select e from Inscripcion i join i.evento e join fetch e.cancha
            where i.usuario.id = :usuarioId and e.fin > :ahora
            order by e.inicio asc""")
    List<Evento> proximosDe(@Param("usuarioId") Long usuarioId, @Param("ahora") Instant ahora);

    /** Eventos del usuario que ya terminaron, el más reciente primero. Pageable dice cuántos como mucho. */
    @Query("""
            select e from Inscripcion i join i.evento e join fetch e.cancha
            where i.usuario.id = :usuarioId and e.fin <= :ahora
            order by e.inicio desc""")
    List<Evento> pasadosDe(@Param("usuarioId") Long usuarioId, @Param("ahora") Instant ahora, Pageable limite);
}
