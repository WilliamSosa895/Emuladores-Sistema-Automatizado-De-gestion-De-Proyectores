package com.proyecto.emuladores.actuators;

import com.proyecto.emuladores.simulation.LuxChangeCause;
import com.proyecto.emuladores.simulation.LuxSnapshot;
import com.proyecto.emuladores.simulation.LuxTransitionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Emulador de persiana motorizada del aula.
 */
public class BlindEmulator extends ActuatorEmulator {

    private static final Logger log = LoggerFactory.getLogger(BlindEmulator.class);

    // ------------------------------------------------------------------ atributos

    // Posición de 0 (totalmente abierta) a 100 (totalmente cerrada).
    private int position;

    // Listener del sensor de lux — igual que LightEmulator.
    // Se asigna desde AulaEmulatorManager.
    private LuxTransitionListener luxListener;

    // Cuántos lux aporta la luz solar que entra por las ventanas abiertas.
    // Al cerrar las persianas, el lux baja en esta cantidad aproximada.
    private static final int LUX_CONTRIBUTION = 80;

    // ------------------------------------------------------------------ constructor

    public BlindEmulator(String aulaId, String brokerUrl, int processingDelayMs) {
        super(aulaId, "blind", brokerUrl, processingDelayMs);
        this.position = 0; // arrancan abiertas por defecto
        this.state    = "OPEN";
    }

    // ------------------------------------------------------------------ applyAction

    @Override
    protected void applyAction(String action) {
        switch (action.toUpperCase()) {
            case "OPEN"  -> open();
            case "CLOSE" -> close();
            default -> log.warn("[{}] Acción desconocida: {}", getId(), action);
        }
    }

    // ------------------------------------------------------------------ open

    public void open() {
        position = 0;
        confirmAction("OPEN", "OPEN");
        log.info("[{}] Persianas abiertas", getId());
    }

    // ------------------------------------------------------------------ close

    public void close() {
        position = 100;
        confirmAction("CLOSE", "CLOSED");
        log.info("[{}] Persianas cerradas", getId());

        // Notificar al sensor de lux que debe iniciar la transición descendente.
        // Se usa la causa BLIND_CLOSED para que la gráfica lo anote correctamente.
        if (luxListener != null) {
            luxListener.onLuxChanged(
                new LuxSnapshot(getAulaId(), LUX_CONTRIBUTION, LuxChangeCause.BLIND_CLOSED)
            );
        } else {
            log.warn("[{}] luxListener no asignado — el sensor no recibirá el cambio", getId());
        }
    }

    // ------------------------------------------------------------------ setLuxListener

    public void setLuxListener(LuxTransitionListener luxListener) {
        this.luxListener = luxListener;
        log.debug("[{}] luxListener asignado", getId());
    }

    // ------------------------------------------------------------------ getters

    public int getPosition() { return position; }
}
