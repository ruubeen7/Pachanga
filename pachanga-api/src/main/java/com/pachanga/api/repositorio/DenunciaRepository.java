package com.pachanga.api.repositorio;

import com.pachanga.api.modelo.Denuncia;
import com.pachanga.api.modelo.EstadoDenuncia;
import com.pachanga.api.modelo.TipoContenido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DenunciaRepository extends JpaRepository<Denuncia, Long> {

    boolean existsByDenuncianteIdAndTipoContenidoAndContenidoId(Long denuncianteId, TipoContenido tipoContenido,
                                                               Long contenidoId);

    /** Denuncias por revisar, las más antiguas primero (para el administrador). */
    List<Denuncia> findByEstadoOrderByCreadoEnAsc(EstadoDenuncia estado);
}
