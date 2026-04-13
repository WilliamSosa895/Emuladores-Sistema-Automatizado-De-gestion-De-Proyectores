package com.proyecto.emuladores.actuators;

import com.proyecto.emuladores.core.AbstractDeviceEmulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Base común para actuadores con retardo de procesamiento simulado.
 */
public abstract class ActuatorEmulator extends AbstractDeviceEmulator {

    private static final Logger log = LoggerFactory.getLogger(ActuatorEmulator.class);

    // ------------------------------------------------------------------ atributos

    // Tiempo en ms que simula el retardo físico del dispositivo real.
    // Una persiana motorizada no se mueve instantáneamente.
    // Viene de EmulatorConfig.processingDelayMs (ej: 500ms)
    protected final int processingDelayMs;

    // ------------------------------------------------------------------ constructor

    protected ActuatorEmulator(String aulaId, String tipo,
                                String brokerUrl, int processingDelayMs) {
        super(aulaId, tipo, brokerUrl);
        this.processingDelayMs = processingDelayMs;
    }

    // ------------------------------------------------------------------ onCommand
    // Punto de entrada de todos los comandos MQTT recibidos.
    // Aplica el delay de procesamiento para simular la respuesta física
    // y luego delega en applyAction() que cada subclase implementa.

    @Override
    public void onCommand(String cmd) {
        log.info("[{}] Comando recibido: {}", getId(), cmd);
        try {
            // Simular el tiempo de respuesta físico del dispositivo
            if (processingDelayMs > 0) {
                Thread.sleep(processingDelayMs);
            }
            applyAction(cmd);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[{}] Procesamiento interrumpido", getId());
        }
    }

    // ------------------------------------------------------------------ applyAction (abstracto)
    // Cada actuador concreto implementa su propia lógica aquí.
    // Recibe el string del comando (ej: "TURN_OFF", "CLOSE", "DEPLOY").

    protected abstract void applyAction(String action);

    // ------------------------------------------------------------------ confirmAction
    // Llamado al final de applyAction() en cada subclase para:
    // 1. Actualizar el estado interno del emulador
    // 2. Publicar el nuevo estado en MQTT con retained=true
    // Spring Boot recibirá este mensaje y escribirá en acciones_dispositivo.

    protected void confirmAction(String action, String newState) {
        String previousState = this.state;
        this.state = newState;

        // Construir payload extendido que incluye la acción ejecutada
        // para que Spring Boot lo persista en acciones_dispositivo
        try {
            String payload = JSON.writeValueAsString(Map.of(
                "id",            getId(),
                "aulaId",        getAulaId(),
                "tipo",          getTipo(),
                "state",         newState,
                "previousState", previousState,
                "action",        action,
                "timestamp",     System.currentTimeMillis(),
                "executor",      "EMULADOR"
            ));
            // retained=true: cualquier suscriptor nuevo ve el último estado
            mqttClient.publish(getTopicState(), payload, 1, true);
            log.info("[{}] Acción confirmada: {} → estado={}", getId(), action, newState);
        } catch (Exception e) {
            log.error("[{}] Error al confirmar acción: {}", getId(), e.getMessage());
            // Fallback: publicar al menos el estado nuevo
            publishState(newState);
        }
    }
}
