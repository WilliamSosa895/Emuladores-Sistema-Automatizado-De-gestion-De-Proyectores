package com.proyecto.emuladores.simulation;

/**
 * Callback para consumir eventos de transición de lux.
 */
public interface LuxTransitionListener {

    // Llamado en cada tick de la transición — es decir, cada stepMs milisegundos.
    // El snapshot contiene el valor actual de lux y la causa del cambio.
    // LuxSensorEmulator implementa este método para actualizar su estado
    // y publicarlo en MQTT en tiempo real.
    void onLuxChanged(LuxSnapshot snapshot);

    // Llamado una sola vez cuando currentLux alcanza el targetLux.
    // Spring Boot escucha esto para decidir el siguiente paso de la
    // secuencia de proyección (¿ya llegamos a <= 100? ¿bajamos persiana?).
    void onTargetReached(int finalLux, LuxChangeCause cause);
}
