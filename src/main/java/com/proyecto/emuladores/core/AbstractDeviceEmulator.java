package com.proyecto.emuladores.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Implementación base para todos los emuladores MQTT del sistema.
 *
 * <p>Encapsula la conexión MQTT, la suscripción a comandos, la publicación de estado
 * y el parseo inicial de payloads para delegar la acción concreta a subclases.</p>
 */
public abstract class AbstractDeviceEmulator implements IEmulator {

    private static final Logger log = LoggerFactory.getLogger(AbstractDeviceEmulator.class);

    // ObjectMapper compartido para serializar/deserializar JSON
    protected static final ObjectMapper JSON = new ObjectMapper();

    // ------------------------------------------------------------------ atributos

    protected final String id;        // ej: "light-aula-1"
    protected final String aulaId;    // ej: "aula-1"
    protected final String tipo;      // ej: "light"
    protected String state;           // estado actual del dispositivo como String

    protected final MqttClientWrapper mqttClient;

    protected final String topicCmd;   // aulas/{aulaId}/{tipo}/cmd
    protected final String topicState; // aulas/{aulaId}/{tipo}/state

    // ------------------------------------------------------------------ constructor

    protected AbstractDeviceEmulator(String aulaId, String tipo, String brokerUrl) {
        this.aulaId      = aulaId;
        this.tipo        = tipo;
        this.id          = tipo + "-" + aulaId;          // ej: "light-aula-1"
        this.topicCmd    = "aulas/" + aulaId + "/" + tipo + "/cmd";
        this.topicState  = "aulas/" + aulaId + "/" + tipo + "/state";
        this.state       = "UNKNOWN";
        this.mqttClient  = new MqttClientWrapper(brokerUrl, this.id);
    }

    // ------------------------------------------------------------------ IEmulator: start

    @Override
    public void start() {
        // 1. Conectar al broker con LWT apuntando al topic de estado.
        //    Si el proceso muere abruptamente, EMQX publicará {"state":"OFFLINE"}
        //    con retained=true para que cualquier suscriptor nuevo lo reciba.
        String lwtPayload = buildStatePayload("OFFLINE");
        mqttClient.connectWithLWT(topicState, lwtPayload);

        // 2. Registrar el listener de mensajes entrantes ANTES de suscribirse.
        mqttClient.setMessageListener((topic, message) -> {
            String payload = new String(message.getPayload());
            handleMqttMessage(topic, payload);
        });

        // 3. Suscribirse al topic de comandos.
        mqttClient.subscribe(topicCmd, 1);

        // publicar el estado inicial RETAINED para que la API reciba
        // el estado real inmediatamente al conectar
        publishState();

        log.info("[{}] Emulador iniciado — cmd: {} | state: {}", id, topicCmd, topicState);
    }

    // ------------------------------------------------------------------ IEmulator: stop

    @Override
    public void stop() {
        // Publicar estado OFFLINE antes de desconectarse limpiamente.
        publishState("OFFLINE");
        mqttClient.disconnect();
        log.info("[{}] Emulador detenido", id);
    }

    // ------------------------------------------------------------------ IEmulator: getState

    @Override
    public String getState() {
        return state;
    }

    // ------------------------------------------------------------------ publishState (estado actual)

    public void publishState() {
        publishState(state);
    }

    // ------------------------------------------------------------------ publishState (estado explícito)

    public void publishState(String stateValue) {
        String payload = buildStatePayload(stateValue);
        // retained=true: cualquier suscriptor nuevo recibe el último estado inmediatamente
        mqttClient.publish(topicState, payload, 1, true);
        log.debug("[{}] Estado publicado: {}", id, payload);
    }

    // ------------------------------------------------------------------ handleMqttMessage
    // Punto de entrada para todos los mensajes MQTT recibidos.
    // Extrae el campo "action" del JSON y lo pasa a onCommand().

    public void handleMqttMessage(String topic, String payload) {
        log.debug("[{}] Mensaje recibido en {}: {}", id, topic, payload);
        try {
            if (payload == null || payload.isBlank()) {
                log.warn("[{}] Payload vacío en {}", id, topic);
                return;
            }

            // El payload esperado es siempre: {"action": "TURN_OFF"} o similar
            Map<?, ?> map = JSON.readValue(payload, Map.class);
            Object action = map.get("action");
            if (action != null && !action.toString().isBlank()) {
                onCommand(action.toString().trim());
            } else {
                log.warn("[{}] Payload sin campo 'action': {}", id, payload);
            }
        } catch (Exception e) {
            log.error("[{}] Error al parsear payload '{}': {}", id, payload, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ onCommand (abstracto)
    // Cada subclase define qué hace con el comando recibido.

    @Override
    public abstract void onCommand(String cmd);

    // ------------------------------------------------------------------ helpers

    // Construye el JSON de estado estándar: {"state":"ON","id":"light-aula-1","aulaId":"aula-1"}
    protected String buildStatePayload(String stateValue) {
        try {
            return JSON.writeValueAsString(Map.of(
                "state",  stateValue,
                "id",     id,
                "aulaId", aulaId,
                "tipo",   tipo
            ));
        } catch (Exception e) {
            // Fallback si Jackson falla (no debería ocurrir con tipos simples)
            return "{\"state\":\"" + stateValue + "\",\"id\":\"" + id + "\"}";
        }
    }

    // ------------------------------------------------------------------ getters útiles para subclases

    public String getId()        { return id; }
    public String getAulaId()    { return aulaId; }
    public String getTipo()      { return tipo; }
    public String getTopicCmd()  { return topicCmd; }
    public String getTopicState(){ return topicState; }
}
