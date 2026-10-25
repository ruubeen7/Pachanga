package com.pachanga.api.repositorio;

import com.pachanga.api.modelo.FotoCancha;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FotoCanchaRepository extends JpaRepository<FotoCancha, Long> {

    /** Fotos de una cancha, de la más antigua a la más nueva. */
    List<FotoCancha> findByCanchaIdOrderByCreadoEnAsc(Long canchaId);

    /** Cuántas fotos tiene una cancha (hay un máximo por cancha). */
    long countByCanchaId(Long canchaId);
}
