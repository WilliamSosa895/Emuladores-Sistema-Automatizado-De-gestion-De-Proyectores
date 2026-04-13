package com.proyecto.emuladores.actuators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Emulador de monitor que puede sincronizarse automáticamente con el proyector.
 */
public class MonitorEmulator extends ActuatorEmulator {

    private static final Logger log = LoggerFactory.getLogger(MonitorEmulator.class);

    // ------------------------------------------------------------------ atributos

    private boolean isOn;
    private boolean syncedWithProjector;

    // Topic del proyector de este mismo salón.
    // MonitorEmulator se suscribe a él para reaccionar automáticamente
    // cuando el proyector se enciende o apaga.
    // Formato: aulas/{aulaId}/projector/state
    private final String projectorStateTopic;

    // ------------------------------------------------------------------ constructor

    public MonitorEmulator(String aulaId, String brokerUrl, int processingDelayMs) {
        super(aulaId, "monitor", brokerUrl, processingDelayMs);
        this.isOn                = false;
        this.syncedWithProjector = false;
        this.state               = "OFF";
        // Construir el topic del proyector del mismo salón
        this.projectorStateTopic = "aulas/" + aulaId + "/projector/state";
    }

    // ------------------------------------------------------------------ start
    // Además de la suscripción al topic de comandos propios (heredada de AbstractDeviceEmulator),
    // el monitor también se suscribe al topic de estado del proyector.

    @Override
    public void start() {
        super.start(); // conecta MQTT, suscribe a aulas/{aulaId}/monitor/cmd

        // Suscribirse al topic de estado del proyector para sincronización automática
        mqttClient.subscribe(projectorStateTopic, 1);
        log.info("[{}] Suscrito al proyector en: {}", getId(), projectorStateTopic);
    }

    // ------------------------------------------------------------------ handleMqttMessage
    // Sobrescribe el método de AbstractDeviceEmulator para manejar DOS topics:
    // 1. Su propio topic de comandos (aulas/{aulaId}/monitor/cmd)
    // 2. El topic de estado del proyector (aulas/{aulaId}/projector/state)

    @Override
    public void handleMqttMessage(String topic, String payload) {
        if (topic.equals(projectorStateTopic)) {
            // Mensaje del proyector — sincronizar automáticamente
            syncWithProjector(payload);
        } else {
            // Mensaje de comandos propios — flujo normal
            super.handleMqttMessage(topic, payload);
        }
    }

    // ------------------------------------------------------------------ applyAction
    // Comandos directos al monitor (encender/apagar manualmente desde el panel admin)

    @Override
    protected void applyAction(String action) {
        switch (action.toUpperCase()) {
            case "TURN_ON"  -> turnOn();
            case "TURN_OFF" -> turnOff();
            case "UNSYNC"   -> unsync();
            default -> log.warn("[{}] Acción desconocida: {}", getId(), action);
        }
    }

    // ------------------------------------------------------------------ turnOn

    public void turnOn() {
        isOn = true;
        confirmAction("TURN_ON", "ON");
        log.info("[{}] Monitor encendido", getId());
    }

    // ------------------------------------------------------------------ turnOff

    public void turnOff() {
        isOn             = false;
        syncedWithProjector = false;
        confirmAction("TURN_OFF", "OFF");
        log.info("[{}] Monitor apagado", getId());
    }

    // ------------------------------------------------------------------ unsync
    // Desvincula el monitor del proyector sin apagarlo
    // (el docente puede seguir usándolo de forma independiente)

    public void unsync() {
        syncedWithProjector = false;
        log.info("[{}] Monitor desvinculado del proyector", getId());
    }

    // ------------------------------------------------------------------ syncWithProjector
    // Llamado automáticamente cuando llega un mensaje del topic del proyector.
    // Parsea el payload JSON del proyector y actúa en consecuencia.

    public void syncWithProjector(String projectorPayload) {
        log.debug("[{}] Sincronizando con proyector: {}", getId(), projectorPayload);
        try {
            Map<?, ?> map     = JSON.readValue(projectorPayload, Map.class);
            Object stateObj   = map.get("state");
            Object inputObj   = map.get("input");

            if (stateObj == null) {
                log.warn("[{}] Payload del proyector sin campo 'state'", getId());
                return;
            }

            String projectorState = stateObj.toString();
            String input          = inputObj != null ? inputObj.toString() : "HDMI";

            if ("ON".equals(projectorState) && !isOn) {
                // El proyector se encendió → encender el monitor y sincronizarse
                isOn                = true;
                syncedWithProjector = true;
                confirmSyncAction("ON", input);
                log.info("[{}] Sincronizado con proyector (ON) — input={}", getId(), input);

            } else if ("OFF".equals(projectorState) && isOn && syncedWithProjector) {
                // El proyector se apagó → apagar el monitor si está en modo sync
                isOn                = false;
                syncedWithProjector = false;
                confirmSyncAction("OFF", input);
                log.info("[{}] Apagado por sincronización con proyector (OFF)", getId());
            }

        } catch (Exception e) {
            log.error("[{}] Error al parsear payload del proyector: {}", getId(), e.getMessage());
        }
    }

    // ------------------------------------------------------------------ confirmSyncAction
    // Publica el estado del monitor incluyendo info de sincronización.

    private void confirmSyncAction(String newState, String input) {
        this.state = newState;
        try {
            String payload = JSON.writeValueAsString(Map.of(
                "id",                getId(),
                "aulaId",            getAulaId(),
                "tipo",              getTipo(),
                "state",             newState,
                "isOn",              isOn,
                "syncedWithProjector", syncedWithProjector,
                "input",             input,
                "timestamp",         System.currentTimeMillis(),
                "executor",          "SYNC_AUTOMATICO"
            ));
            mqttClient.publish(getTopicState(), payload, 1, true);
            log.debug("[{}] Estado sync publicado: {}", getId(), payload);
        } catch (Exception e) {
            log.error("[{}] Error publicando estado sync: {}", getId(), e.getMessage());
            publishState(newState);
        }
    }

    // ------------------------------------------------------------------ getters

    public boolean isOn()                { return isOn; }
    public boolean isSyncedWithProjector(){ return syncedWithProjector; }
}
