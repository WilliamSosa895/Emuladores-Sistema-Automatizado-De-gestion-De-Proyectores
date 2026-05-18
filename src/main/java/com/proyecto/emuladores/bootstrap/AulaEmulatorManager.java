package com.proyecto.emuladores.bootstrap;

import com.proyecto.emuladores.actuators.*;
import com.proyecto.emuladores.core.AbstractDeviceEmulator;
import com.proyecto.emuladores.sensors.LuxSensorEmulator;
import com.proyecto.emuladores.simulation.LuxChangeCause;
import com.proyecto.emuladores.simulation.LuxHistoryBuffer;
import com.proyecto.emuladores.simulation.LuxSnapshot;
import com.proyecto.emuladores.simulation.LuxTransitionListener;
import com.proyecto.emuladores.simulation.LuxTransitionSimulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Ensambla y coordina todos los emuladores de un aula.
 *
 * <p>Su responsabilidad principal es crear las dependencias en el orden correcto,
 * conectar listeners entre dispositivos y ofrecer operaciones de ciclo de vida
 * para iniciar y detener todo el conjunto.</p>
 */
public class AulaEmulatorManager {

    private static final Logger log = LoggerFactory.getLogger(AulaEmulatorManager.class);

    // ------------------------------------------------------------------ atributos

    private final String aulaId;

    // Lista de todos los emuladores del aula — usada para startAll/stopAll
    private final List<AbstractDeviceEmulator> emulators = new ArrayList<>();

    // Buffer compartido — AulaEmulatorManager lo expone para que Spring Boot
    // pueda consultarlo vía WebSocket cuando abre el panel de monitoreo
    private final LuxHistoryBuffer historyBuffer;

    // Referencias directas a los emuladores que Spring Boot necesita consultar
    private final LuxSensorEmulator luxSensor;
    private final LightEmulator     light;
    private final BlindEmulator     blind;
    private final ScreenEmulator    screen;
    private final ProjectorEmulator projector;
    private final MonitorEmulator   monitor;

    // ------------------------------------------------------------------ constructor
    // Aquí se ensambla todo: se crean los objetos, se cablea el grafo de dependencias
    // y se registran los listeners. El orden de creación importa.

    public AulaEmulatorManager(String aulaId, EmulatorConfig config) {
        this.aulaId = aulaId;
        log.info("[Manager-{}] Ensamblando emuladores...", aulaId);

        // 1. Crear el buffer de historial compartido
        historyBuffer = new LuxHistoryBuffer(aulaId, config.getLuxHistoryMaxSize());

        // 2. Crear el simulador de transición de lux
        //    Todavía no tiene listener — se lo asignaremos al crear LuxSensorEmulator
        LuxTransitionSimulator simulator = new LuxTransitionSimulator(
            aulaId,
            config.getInitialLux(),
            config.getLuxTransitionStepMs(),
            config.getLuxTransitionDelta(),
            historyBuffer
        );

        // 3. Crear el sensor de lux
        //    Al construirse, se registra como listener del simulador internamente
        luxSensor = new LuxSensorEmulator(
            aulaId,
            config.getBrokerUrl(),
            config.getLuxIntervalMs(),
            config.getInitialLux(),
            simulator,
            historyBuffer
        );

        // 4. Crear los actuadores
        light     = new LightEmulator(aulaId, config.getBrokerUrl(), config.getProcessingDelayMs());
        blind     = new BlindEmulator(aulaId, config.getBrokerUrl(), config.getProcessingDelayMs());
        screen    = new ScreenEmulator(aulaId, config.getBrokerUrl(), config.getProcessingDelayMs());
        projector = new ProjectorEmulator(aulaId, config.getBrokerUrl(), config.getProcessingDelayMs());
        monitor   = new MonitorEmulator(aulaId, config.getBrokerUrl(), config.getProcessingDelayMs());

        // 5. Cablear los luxListeners:
        //    LightEmulator y BlindEmulator necesitan notificar al sensor cuando actúan.
        //    Usamos un LuxTransitionListener anónimo que delega en applyExternalChange()
        //    del sensor, pasando el targetLux calculado en base al lux actual.
        LuxTransitionListener luxListenerForLight = new LuxTransitionListener() {
            @Override
            public void onLuxChanged(LuxSnapshot snapshot) {
                int currentLux = luxSensor.getCurrentLux();
                int targetLux;

                if (snapshot.cause == LuxChangeCause.LIGHTS_ON) {
                    targetLux = currentLux + snapshot.luxValue;
                } else {
                    targetLux = Math.max(0, currentLux - snapshot.luxValue);
                }

                luxSensor.applyExternalChange(targetLux, snapshot.cause);
            }

            @Override
            public void onTargetReached(int finalLux, LuxChangeCause cause) {
                // No necesario aquí — el sensor ya maneja este evento internamente
            }
        };

        LuxTransitionListener luxListenerForBlind = new LuxTransitionListener() {
            @Override
            public void onLuxChanged(LuxSnapshot snapshot) {
                int currentLux = luxSensor.getCurrentLux();
                int targetLux;

                if (snapshot.cause == LuxChangeCause.BLIND_OPENED) {
                    targetLux = currentLux + snapshot.luxValue;
                } else {
                    targetLux = Math.max(0, currentLux - snapshot.luxValue);
                }

                luxSensor.applyExternalChange(targetLux, snapshot.cause);
            }

            @Override
            public void onTargetReached(int finalLux, LuxChangeCause cause) {
                // No necesario aquí
            }
        };

        light.setLuxListener(luxListenerForLight);
        blind.setLuxListener(luxListenerForBlind);

        // 6. Registrar todos en la lista para startAll/stopAll
        //    El orden de arranque también importa: primero el sensor, luego los actuadores
        emulators.add(luxSensor);
        emulators.add(light);
        emulators.add(blind);
        emulators.add(screen);
        emulators.add(projector);
        emulators.add(monitor);  // último: necesita que el proyector ya esté en EMQX

        log.info("[Manager-{}] {} emuladores ensamblados correctamente", aulaId, emulators.size());
    }

    // ------------------------------------------------------------------ startAll

    public void startAll() {
        log.info("[Manager-{}] Iniciando todos los emuladores...", aulaId);
        for (AbstractDeviceEmulator emulator : emulators) {
            try {
                emulator.start();
                // Pequeña pausa entre arranques para no saturar el broker
                // con 14 conexiones simultáneas al iniciar
                Thread.sleep(100);
            } catch (Exception e) {
                log.error("[Manager-{}] Error al iniciar {}: {}",
                        aulaId, emulator.getId(), e.getMessage());
            }
        }
        log.info("[Manager-{}] Todos los emuladores iniciados", aulaId);
    }

    // ------------------------------------------------------------------ stopAll

    public void stopAll() {
        log.info("[Manager-{}] Deteniendo todos los emuladores...", aulaId);
        // Detener en orden inverso al arranque
        for (int i = emulators.size() - 1; i >= 0; i--) {
            try {
                emulators.get(i).stop();
            } catch (Exception e) {
                log.error("[Manager-{}] Error al detener {}: {}",
                        aulaId, emulators.get(i).getId(), e.getMessage());
            }
        }
        log.info("[Manager-{}] Todos los emuladores detenidos", aulaId);
    }

    // ------------------------------------------------------------------ getEmulator
    // Permite a Spring Boot recuperar un emulador específico por tipo
    // si necesita consultarlo directamente (ej: leer el lux actual via HTTP)

    public AbstractDeviceEmulator getEmulator(String tipo) {
        return emulators.stream()
                .filter(e -> e.getTipo().equals(tipo))
                .findFirst()
                .orElse(null);
    }

    // ------------------------------------------------------------------ getters

    public String           getAulaId()       { return aulaId; }
    public LuxHistoryBuffer getHistoryBuffer() { return historyBuffer; }
    public LuxSensorEmulator getLuxSensor()   { return luxSensor; }
}
