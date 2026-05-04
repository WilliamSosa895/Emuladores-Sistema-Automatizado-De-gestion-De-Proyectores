package com.proyecto.emuladores.actuators;

import com.proyecto.emuladores.simulation.LuxChangeCause;
import com.proyecto.emuladores.simulation.LuxTransitionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Emulador de luminarias del aula.
 */
public class LightEmulator extends ActuatorEmulator {

    private static final Logger log = LoggerFactory.getLogger(LightEmulator.class);

    // ------------------------------------------------------------------ atributos

    // Brillo de 0 a 100. 100 = encendidas al máximo, 0 = apagadas.
    private int brightness;

    // Referencia al LuxSensorEmulator a través de su listener.
    // Cuando las luces se apagan, notifica al sensor cuánto lux
    // debe bajar para que inicie la transición progresiva.
    // Se asigna desde AulaEmulatorManager después de construir todos los emuladores.
    private LuxTransitionListener luxListener;

    // Cuántos lux aportan las luces encendidas al ambiente del aula.
    // Cuando se apagan, el lux objetivo = currentLux - LUX_CONTRIBUTION.
    // Este valor es una estimación — en un sistema real vendría de calibración.
    private static final int LUX_CONTRIBUTION = 120;

    // ------------------------------------------------------------------ constructor

    public LightEmulator(String aulaId, String brokerUrl, int processingDelayMs) {
        super(aulaId, "light", brokerUrl, processingDelayMs);
        this.brightness = 100; // arrancan encendidas por defecto
        this.state      = "ON";
    }

    // ------------------------------------------------------------------ applyAction
    // Interpreta el comando recibido por MQTT y ejecuta la acción correspondiente.

    @Override
    protected void applyAction(String action) {
        switch (action.toUpperCase()) {
            case "TURN_ON"  -> turnOn();
            case "TURN_OFF" -> turnOff();
            default -> log.warn("[{}] Acción desconocida: {}", getId(), action);
        }
    }

    // ------------------------------------------------------------------ turnOn

    public void turnOn() {
        brightness = 100;
        confirmAction("TURN_ON", "ON");
        log.info("[{}] Luces encendidas", getId());

        // Notificar al sensor que el lux subirá por LUX_CONTRIBUTION
        // (cuando las luces se encienden en medio de una sesión, el lux sube)
        if (luxListener != null) {
            luxListener.onLuxChanged(
                new com.proyecto.emuladores.simulation.LuxSnapshot(
                    getAulaId(), LUX_CONTRIBUTION, LuxChangeCause.LIGHTS_ON
                )
            );
        }
    }

    // ------------------------------------------------------------------ turnOff

    public void turnOff() {
        brightness = 0;
        confirmAction("TURN_OFF", "OFF");
        log.info("[{}] Luces apagadas", getId());

        // Notificar al sensor de lux que debe iniciar la transición descendente.
        // El AulaEmulatorManager calcula el targetLux restando la contribución
        // de las luces al lux actual del sensor.
        if (luxListener != null) {
            luxListener.onLuxChanged(
                new com.proyecto.emuladores.simulation.LuxSnapshot(
                    getAulaId(), LUX_CONTRIBUTION, LuxChangeCause.LIGHTS_OFF
                )
            );
        } else {
            log.warn("[{}] luxListener no asignado — el sensor no recibirá el cambio", getId());
        }
    }

    // ------------------------------------------------------------------ setLuxListener
    // Llamado por AulaEmulatorManager durante el ensamblado inicial.

    public void setLuxListener(LuxTransitionListener luxListener) {
        this.luxListener = luxListener;
        log.debug("[{}] luxListener asignado", getId());
    }

    // ------------------------------------------------------------------ getters

    public int getBrightness() { return brightness; }
}
