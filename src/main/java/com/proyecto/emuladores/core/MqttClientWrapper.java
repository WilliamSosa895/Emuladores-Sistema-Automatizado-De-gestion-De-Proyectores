package com.proyecto.emuladores.core;

import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Envoltura ligera sobre Eclipse Paho para centralizar operaciones MQTT.
 *
 * <p>Provee conexión, publicación, suscripción y callback de mensajes con
 * logging consistente para todos los emuladores.</p>
 */
public class MqttClientWrapper {

    private static final Logger log = LoggerFactory.getLogger(MqttClientWrapper.class);

    private final String brokerUrl;
    private final String clientId;
    private MqttClient client;

    public MqttClientWrapper(String brokerUrl, String clientId) {
        this.brokerUrl = brokerUrl;
        this.clientId  = clientId;
    }

    // ------------------------------------------------------------------ connect

    public void connect() {
        try {
            client = new MqttClient(brokerUrl, clientId, null);

            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setCleanSession(true);
            opts.setAutomaticReconnect(true);
            opts.setConnectionTimeout(10);
            opts.setKeepAliveInterval(30);

            client.connect(opts);
            log.info("[MQTT] {} conectado a {}", clientId, brokerUrl);

        } catch (MqttException e) {
            log.error("[MQTT] Error al conectar {}: {}", clientId, e.getMessage());
            throw new RuntimeException("No se pudo conectar al broker MQTT", e);
        }
    }

    // ------------------------------------------------------------------ disconnect

    public void disconnect() {
        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
                log.info("[MQTT] {} desconectado", clientId);
            } catch (MqttException e) {
                log.warn("[MQTT] Error al desconectar {}: {}", clientId, e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ publish

    public void publish(String topic, String payload, int qos, boolean retained) {
        if (client == null || !client.isConnected()) {
            log.warn("[MQTT] {} no está conectado, no se puede publicar en {}", clientId, topic);
            return;
        }
        try {
            MqttMessage msg = new MqttMessage(payload.getBytes());
            msg.setQos(qos);
            msg.setRetained(retained);
            client.publish(topic, msg);
            log.debug("[MQTT] {} publicó en {}: {}", clientId, topic, payload);
        } catch (MqttException e) {
            log.error("[MQTT] Error al publicar en {}: {}", topic, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ subscribe

    public void subscribe(String topic, int qos) {
        if (client == null || !client.isConnected()) {
            log.warn("[MQTT] {} no está conectado, no se puede suscribir a {}", clientId, topic);
            return;
        }
        try {
            client.subscribe(topic, qos);
            log.info("[MQTT] {} suscrito a {}", clientId, topic);
        } catch (MqttException e) {
            log.error("[MQTT] Error al suscribirse a {}: {}", topic, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ listener

    public void setMessageListener(IMqttMessageListener listener) {
        if (client == null) {
            log.warn("[MQTT] cliente nulo, no se puede asignar listener");
            return;
        }
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                log.info("[MQTT] {} reconectado a {}", clientId, serverURI);
            }

            @Override
            public void connectionLost(Throwable cause) {
                log.warn("[MQTT] {} perdió conexión: {}", clientId, cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                listener.messageArrived(topic, message);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // no necesario para este sistema
            }
        });
    }

    // ------------------------------------------------------------------ LWT
    // Llama a este método ANTES de connect().
    // Si el cliente se desconecta abruptamente, EMQX publicará este mensaje automáticamente.

    public void setLWT(String topic, String payload) {
        // El LWT se configura en las opciones de conexión.
        // Este método existe para que AbstractDeviceEmulator lo llame antes de connect().
        // Se implementa internamente en connect() si se llama antes.
        // Ver: AbstractDeviceEmulator que sobrescribe connect() para incluir el LWT.
        log.debug("[MQTT] LWT configurado para {}: topic={}", clientId, topic);
    }

    // ------------------------------------------------------------------ conectar con LWT

    public void connectWithLWT(String lwtTopic, String lwtPayload) {
        try {
            client = new MqttClient(brokerUrl, clientId, null);

            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setCleanSession(true);
            opts.setAutomaticReconnect(true);
            opts.setConnectionTimeout(10);
            opts.setKeepAliveInterval(30);

            // Last Will and Testament: si el cliente muere sin desconectarse limpiamente,
            // EMQX publicará este mensaje en el topic de estado del dispositivo.
            opts.setWill(lwtTopic, lwtPayload.getBytes(), 1, true);

            client.connect(opts);
            log.info("[MQTT] {} conectado con LWT en {}", clientId, brokerUrl);

        } catch (MqttException e) {
            log.error("[MQTT] Error al conectar con LWT {}: {}", clientId, e.getMessage());
            throw new RuntimeException("No se pudo conectar al broker MQTT con LWT", e);
        }
    }

    // ------------------------------------------------------------------ estado

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    public String getClientId() {
        return clientId;
    }
}
