package com.proyecto.emuladores.simulation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simula cambios graduales de iluminancia entre un valor actual y un objetivo.
 *
 * <p>Genera ticks periódicos, persiste snapshots en buffer y notifica al listener
 * en cada paso y al completar la transición.</p>
 */
public class LuxTransitionSimulator {

    private static final Logger log = LoggerFactory.getLogger(LuxTransitionSimulator.class);

    // ------------------------------------------------------------------ atributos

    private final String aulaId; 
    private final int    stepMs;    // intervalo entre ticks en ms (ej: 300)
    private final int    stepDelta; // cuántos lux se mueven por tick (ej: 3)

    private final LuxHistoryBuffer buffer;

    // AtomicInteger para que tick() pueda leer/escribir desde su propio hilo
    private final AtomicInteger currentLux = new AtomicInteger(0);
    private final AtomicInteger targetLux  = new AtomicInteger(0);
    private final AtomicBoolean running    = new AtomicBoolean(false);

    // cause de la transición en curso
    private volatile LuxChangeCause currentCause = LuxChangeCause.STABLE;

    // listener que recibe cada tick y el evento de "objetivo alcanzado"
    private volatile LuxTransitionListener listener;

    // hilo programado — un solo hilo es suficiente
    private final ScheduledExecutorService executor;

    private ScheduledFuture<?> currentTask;

    // ------------------------------------------------------------------ constructor

    public LuxTransitionSimulator(String aulaId, int initialLux,
                                   int stepMs, int stepDelta,
                                   LuxHistoryBuffer buffer) {
        this.aulaId    = aulaId;
        this.stepMs    = stepMs;
        this.stepDelta = stepDelta;
        this.buffer    = buffer;

        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lux-transition-" + aulaId);
            t.setDaemon(true); // no bloquea el shutdown del proceso
            return t;
        });

        this.currentLux.set(initialLux);
        this.targetLux.set(initialLux);

        log.info("[LuxSim-{}] Creado — luxInicial={} stepMs={} stepDelta={}",
                aulaId, initialLux, stepMs, stepDelta);
    }

    // ------------------------------------------------------------------ startTransition
    // Llamado por LuxSensorEmulator cuando LightEmulator o BlindEmulator
    // notifican que ejecutaron una acción que cambia la iluminancia.

    public synchronized void startTransition(int newTargetLux, LuxChangeCause cause) {
        // Cancelar la transición anterior si todavía está corriendo
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(false); // false: no interrumpir si está en medio de un tick
        }

        this.targetLux.set(newTargetLux);
        this.currentCause = cause;
        this.running.set(true);

        log.info("[LuxSim-{}] Iniciando transición {} → {} | causa={}",
                aulaId, currentLux.get(), newTargetLux, cause);

        // Publicar el snapshot inicial de la transición para que la gráfica
        // tenga el punto de partida exacto con la anotación de la causa
        snapAndNotify(cause);

        // Programar el tick periódico
        currentTask = executor.scheduleAtFixedRate(
                this::tick, stepMs, stepMs, TimeUnit.MILLISECONDS
        );
    }

    // ------------------------------------------------------------------ tick
    // Ejecutado por el ScheduledExecutor cada stepMs ms.
    // Mueve currentLux un stepDelta hacia targetLux.
    // Cuando llega al objetivo, detiene el executor y notifica al listener.

    private void tick() {
        if (!running.get()) return;

        int current = currentLux.get();
        int target  = targetLux.get();

        if (current == target) {
            // Ya llegamos — notificar y detener
            finishTransition();
            return;
        }

        // Mover un step hacia el objetivo
        int next;
        if (current > target) {
            // Bajando (luces apagadas, persiana bajada)
            next = Math.max(target, current - stepDelta);
        } else {
            // Subiendo (luces encendidas, persiana abierta)
            next = Math.min(target, current + stepDelta);
        }

        currentLux.set(next);
        snapAndNotify(currentCause);

        // Verificar si en este step llegamos al objetivo
        if (next == target) {
            finishTransition();
        }
    }

    // ------------------------------------------------------------------ finishTransition

    private void finishTransition() {
        running.set(false);
        if (currentTask != null) {
            currentTask.cancel(false);
        }

        int finalLux = currentLux.get();
        log.info("[LuxSim-{}] Transición completada — luxFinal={} causa={}",
                aulaId, finalLux, currentCause);

        // Publicar snapshot STABLE para marcar el fin de la curva en la gráfica
        LuxSnapshot stableSnap = new LuxSnapshot(aulaId, finalLux, LuxChangeCause.STABLE);
        buffer.add(stableSnap);

        // Notificar al listener que el objetivo fue alcanzado
        if (listener != null) {
            try {
                listener.onTargetReached(finalLux, currentCause);
            } catch (Exception e) {
                log.error("[LuxSim-{}] Error en onTargetReached: {}", aulaId, e.getMessage());
            }
        }

        currentCause = LuxChangeCause.STABLE;
    }

    // ------------------------------------------------------------------ snapAndNotify
    // Crea un LuxSnapshot, lo guarda en el buffer y notifica al listener.

    private void snapAndNotify(LuxChangeCause cause) {
        LuxSnapshot snap = new LuxSnapshot(aulaId, currentLux.get(), cause);
        buffer.add(snap);

        if (listener != null) {
            try {
                listener.onLuxChanged(snap);
            } catch (Exception e) {
                log.error("[LuxSim-{}] Error en onLuxChanged: {}", aulaId, e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ stop
    // Llamado cuando el sistema se apaga (AulaEmulatorManager.stopAll())

    public void stop() {
        running.set(false);
        if (currentTask != null) {
            currentTask.cancel(false);
        }
        executor.shutdownNow();
        log.info("[LuxSim-{}] Simulador detenido", aulaId);
    }

    // ------------------------------------------------------------------ getters

    public int getCurrentLux() {
        return currentLux.get();
    }

    public boolean isRunning() {
        return running.get();
    }

    public void setListener(LuxTransitionListener listener) {
        this.listener = listener;
    }

    // Permite actualizar el lux actual directamente sin transición
    // (útil para el estado INITIAL al arrancar)
    public void setCurrentLux(int lux) {
        this.currentLux.set(lux);
        this.targetLux.set(lux);
    }
}
