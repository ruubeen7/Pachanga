package com.pachanga.api.repositorio;

import com.pachanga.api.modelo.Mensaje;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MensajeRepository extends JpaRepository<Mensaje, Long> {

    /**
     * Los últimos mensajes de un evento, del más nuevo al más viejo (el servicio les da la vuelta).
     * Se usa al abrir el chat. "join fetch m.autor" trae el autor en la misma consulta.
     */
    @Query("select m from Mensaje m join fetch m.autor where m.evento.id = :eventoId order by m.id desc")
    List<Mensaje> ultimos(@Param("eventoId") Long eventoId, Pageable limite);

    /**
     * Los mensajes posteriores a uno dado, del más viejo al más nuevo.
     * La app lo llama cada pocos segundos con el id del último mensaje que tiene, y así solo
     * recibe los nuevos. Como los ids crecen siempre, "id > X" es lo mismo que "llegó después de X".
     */
    @Query("""
            select m from Mensaje m join fetch m.autor
            where m.evento.id = :eventoId and m.id > :despuesDe
            order by m.id asc""")
    List<Mensaje> posterioresA(@Param("eventoId") Long eventoId, @Param("despuesDe") Long despuesDe,
                               Pageable limite);
}
