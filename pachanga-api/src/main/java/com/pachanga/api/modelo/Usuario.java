package com.pachanga.api.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.Locale;

/** Una persona registrada en la app. Tabla "usuario". */
@Entity
@Table(name = "usuario")
public class Usuario extends EntidadBase {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, length = 50)
    private String nombre;

    /** Hash BCrypt de la contraseña. La contraseña en claro no se guarda nunca. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "foto_url", length = 500)
    private String fotoUrl;

    /** Opcional: el usuario puede no indicar su nivel. */
    @Enumerated(EnumType.STRING) // guarda el texto ("MEDIO"), no el número de orden
    @Column(length = 20)
    private Nivel nivel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol = Rol.USUARIO;

    /** Token que da Expo para enviar notificaciones push a su móvil. */
    @Column(name = "push_token")
    private String pushToken;

    /** JPA necesita un constructor vacío. Es protected para que el resto del código use el otro. */
    protected Usuario() {
    }

    public Usuario(String email, String nombre, String passwordHash) {
        setEmail(email);
        this.nombre = nombre;
        this.passwordHash = passwordHash;
    }

    public String getEmail() {
        return email;
    }

    /** Guardamos el email sin espacios y en minúsculas para que "Ruben@x.com" y "ruben@x.com" sean el mismo. */
    public void setEmail(String email) {
        this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFotoUrl() {
        return fotoUrl;
    }

    public void setFotoUrl(String fotoUrl) {
        this.fotoUrl = fotoUrl;
    }

    public Nivel getNivel() {
        return nivel;
    }

    public void setNivel(Nivel nivel) {
        this.nivel = nivel;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    public String getPushToken() {
        return pushToken;
    }

    public void setPushToken(String pushToken) {
        this.pushToken = pushToken;
    }
}
