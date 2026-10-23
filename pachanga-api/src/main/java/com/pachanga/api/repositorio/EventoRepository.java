package com.pachanga.api.repositorio;

import com.pachanga.api.modelo.EstadoEvento;
import com.pachanga.api.modelo.Evento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface EventoRepository extends JpaRepository<Evento, Long> {

    /**
     * Próximos eventos de una cancha. El nombre del método se lee así:
     * WHERE cancha_id = ? AND estado = ? AND fin > ? ORDER BY inicio ASC
     * (usamos "fin" para que un evento que ya ha empezado pero no ha acabado siga saliendo).
     */
    List<Evento> findByCanchaIdAndEstadoAndFinAfterOrderByInicioAsc(Long canchaId, EstadoEvento estado, Instant ahora);

    /** Eventos que organiza un usuario, del más reciente al más antiguo. */
    List<Evento> findByOrganizadorIdOrderByInicioDesc(Long organizadorId);
}
