package com.proyecto.emuladores.actuators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Emulador del proyector del aula con control de encendido y fuente de entrada.
 */
public class ProjectorEmulator extends ActuatorEmulator {

    private static final Logger log = LoggerFactory.getLogger(ProjectorEmulator.class);

    // ------------------------------------------------------------------ atributos

    private boolean isOn;

    // Fuente de entrada activa. Valores posibles: "HDMI", "VGA", "WIRELESS"
    private String currentInput;

    // ------------------------------------------------------------------ constructor

    public ProjectorEmulator(String aulaId, String brokerUrl, int processingDelayMs) {
        super(aulaId, "projector", brokerUrl, processingDelayMs);
        this.isOn         = false;
        this.currentInput = "HDMI"; // entrada por defecto
        this.state        = "OFF";
    }

    // ------------------------------------------------------------------ applyAction

    @Override
    protected void applyAction(String action) {
        // Comandos simples
        switch (action.toUpperCase()) {
            case "TURN_ON"  -> turnOn();
            case "TURN_OFF" -> turnOff();
            default -> {
                // Comando de cambio de entrada: "SET_INPUT:HDMI"
                if (action.toUpperCase().startsWith("SET_INPUT:")) {
                    String input = action.substring(10).trim();
                    setInput(input);
                } else {
                    log.warn("[{}] Acción desconocida: {}", getId(), action);
                }
            }
        }
    }

    // ------------------------------------------------------------------ turnOn

    public void turnOn() {
        isOn = true;
        // El payload del proyector es más rico — incluye el input activo.
        // MonitorEmulator se suscribe a este topic y usa este payload
        // para sincronizarse con el proyector.
        confirmActionWithInput("TURN_ON", "ON");
        log.info("[{}] Proyector encendido — input={}", getId(), currentInput);
    }

    // ------------------------------------------------------------------ turnOff

    public void turnOff() {
        isOn = false;
        confirmActionWithInput("TURN_OFF", "OFF");
        log.info("[{}] Proyector apagado", getId());
    }

    // ------------------------------------------------------------------ setInput

    public void setInput(String input) {
        String previous = this.currentInput;
        this.currentInput = input.toUpperCase();
        confirmActionWithInput("SET_INPUT:" + currentInput, isOn ? "ON" : "OFF");
        log.info("[{}] Input cambiado: {} → {}", getId(), previous, currentInput);
    }

    // ------------------------------------------------------------------ confirmActionWithInput
    // Versión extendida de confirmAction que incluye el campo "input" en el payload.
    // MonitorEmulator necesita ese campo para saber con qué fuente sincronizarse.

    private void confirmActionWithInput(String action, String newState) {
        this.state = newState;
        try {
            String payload = JSON.writeValueAsString(Map.of(
                "id",            getId(),
                "aulaId",        getAulaId(),
                "tipo",          getTipo(),
                "state",         newState,
                "isOn",          isOn,
                "input",         currentInput,
                "action",        action,
                "timestamp",     System.currentTimeMillis(),
                "executor",      "EMULADOR"
            ));
            // retained=true: MonitorEmulator recibirá este estado al conectarse,
            // incluso si el proyector se encendió antes de que el monitor arranque
            mqttClient.publish(getTopicState(), payload, 1, true);
            log.debug("[{}] Estado publicado: {}", getId(), payload);
        } catch (Exception e) {
            log.error("[{}] Error publicando estado: {}", getId(), e.getMessage());
            publishState(newState);
        }
    }

    // ------------------------------------------------------------------ getters

    public boolean isOn()           { return isOn; }
    public String getCurrentInput() { return currentInput; }
}
