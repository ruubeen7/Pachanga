package com.pachanga.api.servicio;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Guarda las fotos como archivos en una carpeta del ordenador donde corre la API
 * (por defecto "fotos-subidas", dentro de pachanga-api; está en el .gitignore).
 * WebConfig hace que se puedan ver en http://IP:8080/fotos/nombre.jpg
 *
 * Sirve para desarrollar en local. En Render el disco se borra en cada despliegue,
 * por eso en producción se usa AlmacenFotosSupabase (pachanga.fotos.almacen=supabase).
 * matchIfMissing = true: si no se dice nada, se usa esta.
 */
@Component
@ConditionalOnProperty(name = "pachanga.fotos.almacen", havingValue = "local", matchIfMissing = true)
public class AlmacenFotosLocal implements AlmacenFotos {

    private final Path carpeta;

    public AlmacenFotosLocal(@Value("${pachanga.fotos.carpeta}") String carpeta) {
        this.carpeta = Path.of(carpeta).toAbsolutePath();
    }

    @Override
    public String guardar(byte[] datos, String extension) {
        // Nombre aleatorio (UUID): nunca usamos el nombre que manda el móvil, así nadie puede
        // pisar fotos de otros ni colar rutas raras como "../../algo".
        String nombre = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(carpeta);
            Files.write(carpeta.resolve(nombre), datos);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la foto", e);
        }
        return "/fotos/" + nombre;
    }

    public Path getCarpeta() {
        return carpeta;
    }
}
