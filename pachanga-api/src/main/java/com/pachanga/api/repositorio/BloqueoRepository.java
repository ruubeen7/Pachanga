package com.pachanga.api.repositorio;

import com.pachanga.api.modelo.Bloqueo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BloqueoRepository extends JpaRepository<Bloqueo, Long> {

    boolean existsByBloqueadorIdAndBloqueadoId(Long bloqueadorId, Long bloqueadoId);

    Optional<Bloqueo> findByBloqueadorIdAndBloqueadoId(Long bloqueadorId, Long bloqueadoId);

    /** Usuarios que ha bloqueado alguien, el más reciente primero (para su lista de bloqueados). */
    List<Bloqueo> findByBloqueadorIdOrderByCreadoEnDesc(Long bloqueadorId);

    /** Ids de los usuarios que ha bloqueado {@code usuarioId}. */
    @Query("select b.bloqueado.id from Bloqueo b where b.bloqueador.id = :usuarioId")
    List<Long> idsBloqueadosPor(@Param("usuarioId") Long usuarioId);

    /** Ids de los usuarios que han bloqueado a {@code usuarioId}. */
    @Query("select b.bloqueador.id from Bloqueo b where b.bloqueado.id = :usuarioId")
    List<Long> idsQueHanBloqueadoA(@Param("usuarioId") Long usuarioId);

    /** ¿Hay bloqueo entre estos dos, en cualquiera de las dos direcciones? */
    @Query("""
            select count(b) > 0 from Bloqueo b
            where (b.bloqueador.id = :a and b.bloqueado.id = :b)
               or (b.bloqueador.id = :b and b.bloqueado.id = :a)""")
    boolean hayBloqueoEntre(@Param("a") Long a, @Param("b") Long b);
}
