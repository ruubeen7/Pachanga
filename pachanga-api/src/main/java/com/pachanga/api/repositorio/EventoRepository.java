package com.pachanga.api.repositorio;

import com.pachanga.api.modelo.EstadoEvento;
import com.pachanga.api.modelo.Evento;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EventoRepository extends JpaRepository<Evento, Long> {

    /**
     * Próximos eventos de una cancha. El nombre del método se lee así:
     * WHERE cancha_id = ? AND estado = ? AND fin > ? ORDER BY inicio ASC
     * (usamos "fin" para que un evento que ya ha empezado pero no ha acabado siga saliendo).
     */
    List<Evento> findByCanchaIdAndEstadoAndFinAfterOrderByInicioAsc(Long canchaId, EstadoEvento estado, Instant ahora);

    /**
     * Para cada cancha de la lista: cuántos eventos activos tiene que aún no han terminado
     * y cuándo empieza el primero. Cada fila es un array {cancha_id, cuántos, primer inicio}.
     * No se cuentan los eventos de los organizadores de {@code ocultos} (bloqueos).
     * ocultos nunca puede ir vacío: "not in ()" no es SQL válido (ver CanchaService.resumenEventos).
     */
    @Query("""
            select e.cancha.id, count(e), min(e.inicio) from Evento e
            where e.cancha.id in :canchas
              and e.estado = com.pachanga.api.modelo.EstadoEvento.ACTIVO
              and e.fin > :ahora
              and e.organizador.id not in :ocultos
            group by e.cancha.id""")
    List<Object[]> contarProximosPorCancha(@Param("canchas") Collection<Long> canchas,
                                           @Param("ahora") Instant ahora,
                                           @Param("ocultos") Collection<Long> ocultos);

    /** Eventos que organiza un usuario, del más reciente al más antiguo. */
    List<Evento> findByOrganizadorIdOrderByInicioDesc(Long organizadorId);

    /**
     * Eventos activos que empiezan entre ahora y {@code limite} y aún no tienen el recordatorio enviado.
     * Las comparaciones con enums en JPQL se escriben con el nombre completo de la clase.
     */
    @Query("""
            select e from Evento e join fetch e.cancha
            where e.estado = com.pachanga.api.modelo.EstadoEvento.ACTIVO
              and e.recordatorioEnviado = false
              and e.inicio > :ahora and e.inicio <= :limite""")
    List<Evento> pendientesDeRecordatorio(@Param("ahora") Instant ahora, @Param("limite") Instant limite);

    /**
     * Busca el evento y BLOQUEA su fila hasta que acabe la transacción (SELECT ... FOR UPDATE).
     * Lo usamos al apuntarse: si dos personas pulsan "Apuntarme" a la vez por la última plaza,
     * la segunda espera a que termine la primera y entonces ya ve que no quedan plazas.
     * Sin el bloqueo, las dos contarían "9 de 10" a la vez y entrarían 11.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Evento e where e.id = :id")
    Optional<Evento> buscarBloqueando(@Param("id") Long id);
}
