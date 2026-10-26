package com.pachanga.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa dos cosas de Spring que vienen apagadas:
 * - @EnableAsync: que los métodos marcados con @Async se ejecuten en segundo plano
 *   (lo usa EnvioAvisosExpo para no hacer esperar al usuario).
 * - @EnableScheduling: que los métodos marcados con @Scheduled se ejecuten solos cada cierto tiempo
 *   (lo usa AvisoService para los recordatorios).
 */
@Configuration
@EnableAsync
@EnableScheduling
public class TareasConfig {
}
