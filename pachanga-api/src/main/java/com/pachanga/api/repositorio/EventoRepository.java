package com.pachanga.api.repositorio;

import com.pachanga.api.modelo.EstadoEvento;
import com.pachanga.api.modelo.Evento;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventoRepository extends JpaRepository<Evento, Long> {

    /**
     * Próximos eventos de una cancha. El nombre del método se lee así:
     * WHERE cancha_id = ? AND estado = ? AND fin > ? ORDER BY inicio ASC
     * (usamos "fin" para que un evento que ya ha empezado pero no ha acabado siga saliendo).
     */
    List<Evento> findByCanchaIdAndEstadoAndFinAfterOrderByInicioAsc(Long canchaId, EstadoEvento estado, Instant ahora);

    /** Eventos que organiza un usuario, del más reciente al más antiguo. */
    List<Evento> findByOrganizadorIdOrderByInicioDesc(Long organizadorId);

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
