package com.proyecto.emuladores.actuators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Emulador del telón/pantalla de proyección.
 */
public class ScreenEmulator extends ActuatorEmulator {

    private static final Logger log = LoggerFactory.getLogger(ScreenEmulator.class);

    // ------------------------------------------------------------------ atributos

    // true = telón desplegado y listo para proyectar, false = recogido
    private boolean isDeployed;

    // ------------------------------------------------------------------ constructor

    public ScreenEmulator(String aulaId, String brokerUrl, int processingDelayMs) {
        super(aulaId, "screen", brokerUrl, processingDelayMs);
        this.isDeployed = false;
        this.state      = "RETRACTED";
    }

    // ------------------------------------------------------------------ applyAction

    @Override
    protected void applyAction(String action) {
        switch (action.toUpperCase()) {
            case "DEPLOY"  -> deploy();
            case "RETRACT" -> retract();
            default -> log.warn("[{}] Acción desconocida: {}", getId(), action);
        }
    }

    // ------------------------------------------------------------------ deploy
    // Despliega el telón de proyección.
    // Se ejecuta cuando el lux ya está en el rango óptimo (<=100)
    // y antes de encender el proyector.

    public void deploy() {
        isDeployed = true;
        confirmAction("DEPLOY", "DEPLOYED");
        log.info("[{}] Telón desplegado", getId());
    }

    // ------------------------------------------------------------------ retract
    // Recoge el telón al finalizar la sesión de proyección.

    public void retract() {
        isDeployed = false;
        confirmAction("RETRACT", "RETRACTED");
        log.info("[{}] Telón recogido", getId());
    }

    // ------------------------------------------------------------------ getters

    public boolean isDeployed() { return isDeployed; }
}
