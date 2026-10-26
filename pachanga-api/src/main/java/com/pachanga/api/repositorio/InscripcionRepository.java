package com.pachanga.api.repositorio;

import com.pachanga.api.modelo.Evento;
import com.pachanga.api.modelo.Inscripcion;
import com.pachanga.api.modelo.Usuario;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /** Apuntados a un evento que tienen las notificaciones activadas (token guardado). */
    @Query("select u from Inscripcion i join i.usuario u where i.evento.id = :eventoId and u.pushToken is not null")
    List<Usuario> apuntadosConAvisos(@Param("eventoId") Long eventoId);

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

    /**
     * Saca a un usuario de los eventos que organiza otro y aún no han terminado (se usa al bloquear).
     * @Modifying: avisa a Spring de que esta consulta cambia datos (es un DELETE, no un SELECT).
     * Devuelve cuántas inscripciones ha borrado.
     */
    @Modifying
    @Query("""
            delete from Inscripcion i
            where i.usuario.id = :usuarioId
              and i.evento.id in (select e.id from Evento e where e.organizador.id = :organizadorId and e.fin > :ahora)""")
    int quitarDeEventosDe(@Param("usuarioId") Long usuarioId, @Param("organizadorId") Long organizadorId,
                          @Param("ahora") Instant ahora);

    /** Eventos del usuario que ya terminaron, el más reciente primero. Pageable dice cuántos como mucho. */
    @Query("""
            select e from Inscripcion i join i.evento e join fetch e.cancha
            where i.usuario.id = :usuarioId and e.fin <= :ahora
            order by e.inicio desc""")
    List<Evento> pasadosDe(@Param("usuarioId") Long usuarioId, @Param("ahora") Instant ahora, Pageable limite);
}
