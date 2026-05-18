package com.proyecto.emuladores.simulation;

/**
 * Catálogo de causas que explican por qué cambia la iluminancia.
 */
public enum LuxChangeCause {
    INITIAL,      // lectura de arranque, antes de cualquier acción
    LIGHTS_OFF,   // transición causada por apagar las luces del salón
    LIGHTS_ON,    // transición causada por encender las luces del salón
    BLIND_CLOSED, // transición causada por bajar las persianas
    BLIND_OPENED, // transición causada por abrir las persianas
    STABLE,       // el sistema llegó al objetivo, lux estable
    MANUAL        // cambio forzado manualmente desde el panel admin
}
