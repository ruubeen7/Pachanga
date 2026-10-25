package com.pachanga.api.servicio;

/**
 * Dónde se guardan los archivos de las fotos.
 *
 * Es una interfaz para poder cambiar el "dónde" sin tocar el resto del código:
 * ahora hay una versión que guarda en una carpeta del PC (AlmacenFotosLocal) y,
 * al publicar la app, habrá otra que las suba a Supabase Storage.
 * FotoService solo conoce esta interfaz; Spring le inyecta la versión que haya.
 */
public interface AlmacenFotos {

    /**
     * Guarda la imagen y devuelve la URL con la que se podrá ver.
     *
     * @param datos     los bytes de la imagen
     * @param extension "jpg", "png" o "webp"
     */
    String guardar(byte[] datos, String extension);
}
