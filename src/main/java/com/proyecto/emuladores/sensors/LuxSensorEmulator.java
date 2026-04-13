package com.proyecto.emuladores.sensors;

import com.proyecto.emuladores.simulation.LuxChangeCause;
import com.proyecto.emuladores.simulation.LuxHistoryBuffer;
import com.proyecto.emuladores.simulation.LuxSnapshot;
import com.proyecto.emuladores.simulation.LuxTransitionListener;
import com.proyecto.emuladores.simulation.LuxTransitionSimulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Random;

/**
 * Emulador del sensor de iluminancia del aula.
 *
 * <p>Publica lecturas de lux, aplica ruido ambiental en estado estable y
 * coordina transiciones suaves de lux cuando cambian actuadores relevantes.</p>
 */
public class LuxSensorEmulator extends SensorEmulator implements LuxTransitionListener {

    private static final Logger log = LoggerFactory.getLogger(LuxSensorEmulator.class);

    // ------------------------------------------------------------------ atributos

    // Lux actual — se actualiza en cada tick del simulador y en cada measure()
    private volatile int currentLux;

    // Rango de variación aleatoria cuando NO hay transición en curso.
    // Simula el "ruido" natural de la luz ambiente (±variationRange lux).
    private static final int VARIATION_RANGE = 5;
    private final Random random = new Random();

    // El simulador de transición progresiva — delegado principal de este sensor
    private final LuxTransitionSimulator simulator;

    // El buffer compartido con AulaEmulatorManager para que Spring Boot
    // pueda consultarlo y enviarlo por WebSocket al frontend
    private final LuxHistoryBuffer historyBuffer;

    // ------------------------------------------------------------------ constructor

    public LuxSensorEmulator(String aulaId, String brokerUrl,
                              int intervalMs, int initialLux,
                              LuxTransitionSimulator simulator,
                              LuxHistoryBuffer historyBuffer) {
        super(aulaId, "lux_sensor", brokerUrl, intervalMs);

        this.currentLux    = initialLux;
        this.simulator     = simulator;
        this.historyBuffer = historyBuffer;

        // Registrarse como listener del simulador:
        // cuando el simulador hace tick, llamará a onLuxChanged() de este sensor
        this.simulator.setListener(this);

        log.info("[{}] Creado — luxInicial={}", getId(), initialLux);
    }

    // ------------------------------------------------------------------ start

    @Override
    public void start() {
        // Sincronizar el simulador con el lux inicial antes de arrancar el loop
        simulator.setCurrentLux(currentLux);

        // Publicar snapshot INITIAL en el buffer para que la gráfica
        // tenga el punto de partida antes de cualquier acción
        LuxSnapshot initialSnap = new LuxSnapshot(getAulaId(), currentLux, LuxChangeCause.INITIAL);
        historyBuffer.add(initialSnap);

        super.start(); // conecta MQTT, suscribe a topicCmd, arranca loopPublish
        log.info("[{}] Sensor de lux iniciado — lux={}", getId(), currentLux);
    }

    // ------------------------------------------------------------------ stop

    @Override
    public void stop() {
        simulator.stop();
        super.stop();
    }

    // ------------------------------------------------------------------ measure
    // Llamado por loopPublish() cada intervalMs cuando NO hay transición activa.
    // Añade una pequeña variación aleatoria para simular el ruido natural de la luz.
    // Si el simulador está corriendo, este método NO modifica el lux —
    // el simulador es quien lo controla durante una transición.

    @Override
    protected void measure() {
        if (simulator.isRunning()) {
            // Durante una transición, el simulador actualiza currentLux
            // a través de onLuxChanged() — measure() no interfiere
            return;
        }

        // Fuera de transición: variación aleatoria pequeña alrededor del valor actual
        int variation = random.nextInt(VARIATION_RANGE * 2 + 1) - VARIATION_RANGE;
        int newLux    = Math.max(0, currentLux + variation);

        currentLux = newLux;

        // Actualizar el estado serializado (lo usa publishState() de AbstractDeviceEmulator)
        state = buildLuxPayload(newLux, LuxChangeCause.STABLE);

        log.debug("[{}] Medición estable — lux={}", getId(), newLux);
    }

    // ------------------------------------------------------------------ publishState
    // Sobrescribe el publishState() de AbstractDeviceEmulator para incluir
    // el valor de lux y la causa en el payload MQTT, no solo el "state".

    @Override
    public void publishState() {
        String payload = buildLuxPayload(currentLux, LuxChangeCause.STABLE);
        mqttClient.publish(getTopicState(), payload, 1, true);
        log.debug("[{}] Estado publicado: {}", getId(), payload);
    }

    // ------------------------------------------------------------------ LuxTransitionListener: onLuxChanged
    // Llamado por LuxTransitionSimulator en cada tick (cada stepMs ms).
    // Actualiza currentLux y publica el nuevo valor en MQTT inmediatamente.
    // Esto produce la curva suave que ve el frontend en tiempo real.

    @Override
    public void onLuxChanged(LuxSnapshot snapshot) {
        currentLux = snapshot.luxValue;
        state      = buildLuxPayload(snapshot.luxValue, snapshot.cause);

        // Publicar en MQTT para que Spring Boot lo reciba, persista en
        // lecturas_lux y reenvíe por WebSocket al frontend
        mqttClient.publish(getTopicState(), snapshot.toJson(), 1, false);

        log.debug("[{}] Lux en transición: {} ({})", getId(), snapshot.luxValue, snapshot.cause);
    }

    // ------------------------------------------------------------------ LuxTransitionListener: onTargetReached
    // Llamado por el simulador cuando currentLux llegó al targetLux.
    // Este sensor solo actualiza su estado — la decisión de qué hacer
    // a continuación (bajar persiana, encender proyector) la toma Spring Boot
    // cuando recibe el mensaje MQTT con cause=STABLE.

    @Override
    public void onTargetReached(int finalLux, LuxChangeCause cause) {
        currentLux = finalLux;
        state      = buildLuxPayload(finalLux, LuxChangeCause.STABLE);

        // Publicar el estado final con retained=true para que cualquier
        // suscriptor nuevo (ej: el frontend al abrir el panel) reciba el valor actual
        mqttClient.publish(getTopicState(), state, 1, true);

        log.info("[{}] Objetivo alcanzado — luxFinal={} causa={}", getId(), finalLux, cause);
    }

    // ------------------------------------------------------------------ applyExternalChange
    // Llamado por AulaEmulatorManager cuando LightEmulator o BlindEmulator
    // confirman que ejecutaron una acción que cambia la iluminancia.
    // Delega al simulador para que inicie la transición progresiva.

    public void applyExternalChange(int targetLux, LuxChangeCause cause) {
        log.info("[{}] Cambio externo recibido — target={} causa={}", getId(), targetLux, cause);
        simulator.startTransition(targetLux, cause);
    }

    // ------------------------------------------------------------------ onCommand
    // Comandos que puede recibir el sensor vía MQTT desde el panel admin.
    // Por ejemplo: forzar un valor de lux manualmente para pruebas.

    @Override
    public void onCommand(String cmd) {
        log.info("[{}] Comando recibido: {}", getId(), cmd);

        // Comando esperado: SET_LUX:<valor>  ej: "SET_LUX:150"
        if (cmd.startsWith("SET_LUX:")) {
            try {
                int forcedLux = Integer.parseInt(cmd.substring(8).trim());
                simulator.startTransition(forcedLux, LuxChangeCause.MANUAL);
                log.info("[{}] Lux forzado manualmente a {}", getId(), forcedLux);
            } catch (NumberFormatException e) {
                log.warn("[{}] Valor inválido en SET_LUX: {}", getId(), cmd);
            }
        }
    }

    // ------------------------------------------------------------------ getCurrentLux

    public int getCurrentLux() {
        return currentLux;
    }

    // ------------------------------------------------------------------ helper privado
    // Construye el payload JSON completo que incluye lux, causa, aulaId y timestamp.
    // Este payload es el que Spring Boot persiste en lecturas_lux.

    private String buildLuxPayload(int lux, LuxChangeCause cause) {
        try {
            return JSON.writeValueAsString(Map.of(
                "aulaId",    getAulaId(),
                "id",        getId(),
                "luxValue",  lux,
                "cause",     cause.name(),
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            return "{\"aulaId\":\"" + getAulaId() + "\""
                 + ",\"luxValue\":"  + lux
                 + ",\"cause\":\""   + cause.name() + "\""
                 + ",\"timestamp\":" + System.currentTimeMillis() + "}";
        }
    }
}
