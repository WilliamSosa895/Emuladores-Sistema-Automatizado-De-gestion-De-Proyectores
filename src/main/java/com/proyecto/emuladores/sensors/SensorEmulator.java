package com.proyecto.emuladores.sensors;

import com.proyecto.emuladores.core.AbstractDeviceEmulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Base común para sensores con publicación periódica.
 *
 * <p>Extiende el comportamiento MQTT base y agrega un scheduler que ejecuta
 * ciclos de medición a intervalo fijo.</p>
 */
public abstract class SensorEmulator extends AbstractDeviceEmulator {

    private static final Logger log = LoggerFactory.getLogger(SensorEmulator.class);

    // ------------------------------------------------------------------ atributos

    // Intervalo en ms entre cada medición periódica (viene de EmulatorConfig.luxIntervalMs)
    protected final int intervalMs;

    // Hilo programado que ejecuta loopPublish() cada intervalMs
    private final ScheduledExecutorService scheduledExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sensor-loop-" + getId());
                t.setDaemon(true);
                return t;
            });

    private ScheduledFuture<?> loopTask;

    // ------------------------------------------------------------------ constructor

    protected SensorEmulator(String aulaId, String tipo,
                              String brokerUrl, int intervalMs) {
        super(aulaId, tipo, brokerUrl);
        this.intervalMs = intervalMs;
    }

    // ------------------------------------------------------------------ start
    // Llama al start() de AbstractDeviceEmulator (conecta MQTT y suscribe al topic cmd)
    // y después arranca el loop de medición periódica.

    @Override
    public void start() {
        super.start();

        // Publicar una lectura inicial inmediatamente al arrancar
        measure();
        publishState();

        // Luego continuar midiendo cada intervalMs
        loopTask = scheduledExecutor.scheduleAtFixedRate(
                this::loopPublish,
                intervalMs,
                intervalMs,
                TimeUnit.MILLISECONDS
        );

        log.info("[{}] Loop de medición iniciado cada {}ms", getId(), intervalMs);
    }

    // ------------------------------------------------------------------ stop

    @Override
    public void stop() {
        if (loopTask != null) {
            loopTask.cancel(false);
        }
        scheduledExecutor.shutdownNow();
        super.stop(); // desconecta MQTT y publica OFFLINE
        log.info("[{}] Loop de medición detenido", getId());
    }

    // ------------------------------------------------------------------ loopPublish
    // Ejecutado periódicamente por el ScheduledExecutor.
    // Llama a measure() para actualizar el estado interno y luego lo publica.

    protected void loopPublish() {
        try {
            measure();
            publishState();
        } catch (Exception e) {
            log.error("[{}] Error en loopPublish: {}", getId(), e.getMessage());
        }
    }

    // ------------------------------------------------------------------ measure (abstracto)
    // Cada subclase implementa su propia lógica de medición.
    // LuxSensorEmulator añade variación aleatoria al lux actual.

    protected abstract void measure();

    // ------------------------------------------------------------------ onCommand
    // Los sensores también pueden recibir comandos, por ejemplo para
    // forzar un valor de lux desde el panel admin (causa MANUAL).
    // Las subclases pueden sobrescribir este método para agregar más comandos.

    @Override
    public void onCommand(String cmd) {
        log.debug("[{}] Comando recibido: {}", getId(), cmd);
        // Las subclases manejan sus propios comandos sobrescribiendo este método
    }
}
