package com.pachanga.api.servicio;

import com.pachanga.api.dto.FotoRespuesta;
import com.pachanga.api.error.ExcepcionApi;
import com.pachanga.api.modelo.Cancha;
import com.pachanga.api.modelo.FotoCancha;
import com.pachanga.api.modelo.Usuario;
import com.pachanga.api.repositorio.FotoCanchaRepository;
import com.pachanga.api.repositorio.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** Subida de fotos de canchas. */
@Service
public class FotoService {

    /** Máximo de fotos por cancha, para no llenar el almacenamiento gratuito. */
    public static final int MAX_FOTOS_POR_CANCHA = 10;

    private final CanchaService canchas;
    private final FotoCanchaRepository fotos;
    private final UsuarioRepository usuarios;
    private final AlmacenFotos almacen;

    public FotoService(CanchaService canchas, FotoCanchaRepository fotos, UsuarioRepository usuarios,
                       AlmacenFotos almacen) {
        this.canchas = canchas;
        this.fotos = fotos;
        this.usuarios = usuarios;
        this.almacen = almacen;
    }

    /** Añade una foto a una cancha. Cualquier usuario con sesión puede aportar fotos. */
    @Transactional
    public FotoRespuesta subir(Long canchaId, Long usuarioId, MultipartFile archivo) {
        Cancha cancha = canchas.buscarCancha(canchaId);
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new ExcepcionApi(HttpStatus.UNAUTHORIZED, "La sesión ya no es válida"));

        if (fotos.countByCanchaId(canchaId) >= MAX_FOTOS_POR_CANCHA) {
            throw new ExcepcionApi(HttpStatus.BAD_REQUEST,
                    "Esta cancha ya tiene " + MAX_FOTOS_POR_CANCHA + " fotos, que es el máximo");
        }

        byte[] datos = leer(archivo);
        String extension = tipoDeImagen(datos);
        String url = almacen.guardar(datos, extension);

        return FotoRespuesta.de(fotos.save(new FotoCancha(cancha, url, usuario)));
    }

    private static byte[] leer(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ExcepcionApi(HttpStatus.BAD_REQUEST, "No ha llegado ninguna foto");
        }
        try {
            return archivo.getBytes();
        } catch (IOException e) {
            throw new ExcepcionApi(HttpStatus.BAD_REQUEST, "No se pudo leer la foto");
        }
    }

    /**
     * Mira los primeros bytes del archivo ("número mágico") para saber si de verdad es una imagen.
     * No nos fiamos del nombre ni del tipo que dice el móvil: se podrían falsear.
     *  - JPEG empieza por FF D8 FF
     *  - PNG empieza por 89 50 4E 47 ("‰PNG")
     *  - WebP empieza por "RIFF" y en la posición 8 pone "WEBP"
     */
    static String tipoDeImagen(byte[] d) {
        if (d.length > 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (d.length > 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
            return "png";
        }
        if (d.length > 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "webp";
        }
        throw new ExcepcionApi(HttpStatus.BAD_REQUEST, "Solo se pueden subir fotos JPG, PNG o WebP");
    }
}
