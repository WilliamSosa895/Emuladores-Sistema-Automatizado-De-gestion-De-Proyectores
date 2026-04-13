package com.proyecto.emuladores.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Buffer circular en memoria con historial reciente de lecturas de lux.
 *
 * <p>Se comparte con la capa de exposición para recuperar rápidamente trazas
 * temporales sin consultar almacenamiento externo.</p>
 */
public class LuxHistoryBuffer {

    private static final Logger log = LoggerFactory.getLogger(LuxHistoryBuffer.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    // ------------------------------------------------------------------ atributos

    private final String aulaId;
    private final int maxSize;

    // LinkedList como buffer circular: cuando llega al límite descarta el más viejo
    // Collections.synchronizedList porque el simulador escribe desde su propio hilo
    private final LinkedList<LuxSnapshot> snapshots;
    private final List<LuxSnapshot> safeSnapshots;

    // ------------------------------------------------------------------ constructor

    public LuxHistoryBuffer(String aulaId, int maxSize) {
        this.aulaId       = aulaId;
        this.maxSize      = maxSize;
        this.snapshots    = new LinkedList<>();
        this.safeSnapshots = Collections.synchronizedList(snapshots);
    }

    // ------------------------------------------------------------------ add

    public void add(LuxSnapshot snapshot) {
        synchronized (snapshots) {
            if (snapshots.size() >= maxSize) {
                snapshots.removeFirst(); // descarta el más viejo
            }
            snapshots.addLast(snapshot);
        }
        log.debug("[LuxBuffer-{}] +snapshot lux={} cause={} total={}",
                aulaId, snapshot.luxValue, snapshot.cause, snapshots.size());
    }

    // ------------------------------------------------------------------ getLast
    // Devuelve los últimos n snapshots en orden cronológico (el más reciente al final).
    // Spring Boot llama esto para enviar el historial por WebSocket cuando abre el panel.

    public List<LuxSnapshot> getLast(int n) {
        synchronized (snapshots) {
            int size  = snapshots.size();
            int from  = Math.max(0, size - n);
            return new ArrayList<>(snapshots.subList(from, size));
        }
    }

    // ------------------------------------------------------------------ getAll

    public List<LuxSnapshot> getAll() {
        synchronized (snapshots) {
            return new ArrayList<>(snapshots);
        }
    }

    // ------------------------------------------------------------------ toJson
    // Serializa todo el buffer a JSON array.
    // Útil para exponer el historial completo al backend bajo demanda.

    public String toJson() {
        try {
            List<LuxSnapshot> copy = getAll();
            // Construimos la lista de formas serializables
            List<Object> forms = new ArrayList<>(copy.size());
            for (LuxSnapshot s : copy) {
                forms.add(new SerializableSnapshot(s));
            }
            return JSON.writeValueAsString(forms);
        } catch (Exception e) {
            log.error("[LuxBuffer-{}] Error serializando buffer: {}", aulaId, e.getMessage());
            return "[]";
        }
    }

    // ------------------------------------------------------------------ clear

    public void clear() {
        synchronized (snapshots) {
            snapshots.clear();
        }
        log.info("[LuxBuffer-{}] Buffer limpiado", aulaId);
    }

    // ------------------------------------------------------------------ info

    public int size() {
        synchronized (snapshots) {
            return snapshots.size();
        }
    }

    public String getAulaId() { return aulaId; }
    public int    getMaxSize(){ return maxSize; }

    // ------------------------------------------------------------------ helper de serialización

    private static class SerializableSnapshot {
        public final long   timestamp;
        public final int    luxValue;
        public final String aulaId;
        public final String cause;

        SerializableSnapshot(LuxSnapshot s) {
            this.timestamp = s.timestamp;
            this.luxValue  = s.luxValue;
            this.aulaId    = s.aulaId;
            this.cause     = s.cause.name();
        }
    }
}
