package com.proyecto.emuladores.core;

/**
 * Contrato mínimo de ciclo de vida y manejo de comandos para un emulador.
 */
public interface IEmulator {
    /** Inicia el emulador y sus recursos asociados. */
    void start();

    /** Detiene el emulador y libera sus recursos asociados. */
    void stop();

    /** Devuelve el estado lógico actual del emulador. */
    String getState();

    /** Procesa un comando de negocio recibido por MQTT u otro canal. */
    void onCommand(String cmd);
}

