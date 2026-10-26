package com.pachanga.api.config;

import com.pachanga.api.servicio.AlmacenFotosLocal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Hace que los archivos de la carpeta de fotos se puedan ver por HTTP:
 * GET /fotos/abc.jpg → devuelve el archivo fotos-subidas/abc.jpg
 * (Spring se encarga de las cabeceras, la caché y de no salirse de la carpeta).
 *
 * Solo hace falta con las fotos en local. Con Supabase las fotos se ven directamente desde Supabase.
 */
@Configuration
@ConditionalOnProperty(name = "pachanga.fotos.almacen", havingValue = "local", matchIfMissing = true)
public class WebConfig implements WebMvcConfigurer {

    private final AlmacenFotosLocal almacen;

    public WebConfig(AlmacenFotosLocal almacen) {
        this.almacen = almacen;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registro) {
        // La carpeta tiene que acabar en "/"; si aún no existe, Java no la pone solo.
        String carpeta = almacen.getCarpeta().toUri().toString();
        if (!carpeta.endsWith("/")) carpeta += "/";
        registro.addResourceHandler("/fotos/**").addResourceLocations(carpeta);
    }
}
