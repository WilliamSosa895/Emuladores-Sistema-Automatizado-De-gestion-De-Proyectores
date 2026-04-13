package com.proyecto.emuladores.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Inmutable que representa una lectura puntual de iluminancia.
 */
public class LuxSnapshot {

    private static final ObjectMapper JSON = new ObjectMapper();

    // ------------------------------------------------------------------ campos

    public final long           timestamp; // epoch ms
    public final int            luxValue;
    public final String         aulaId;
    public final LuxChangeCause cause;

    // ------------------------------------------------------------------ constructor

    public LuxSnapshot(String aulaId, int luxValue, LuxChangeCause cause) {
        this.timestamp = System.currentTimeMillis();
        this.luxValue  = luxValue;
        this.aulaId    = aulaId;
        this.cause     = cause;
    }

    // ------------------------------------------------------------------ serialización

    // Produce el JSON que se publica en el topic MQTT y que Spring Boot
    // persiste en la tabla lecturas_lux (incluido el campo "causa").
    public String toJson() {
        try {
            return JSON.writeValueAsString(new SerializableForm(this));
        } catch (Exception e) {
            // Fallback manual si Jackson falla (no debería ocurrir)
            return "{\"aulaId\":\"" + aulaId + "\""
                 + ",\"luxValue\":"  + luxValue
                 + ",\"cause\":\""   + cause.name() + "\""
                 + ",\"timestamp\":" + timestamp + "}";
        }
    }

    // Clase interna solo para que Jackson serialice correctamente los campos finales
    private static class SerializableForm {
        public final long   timestamp;
        public final int    luxValue;
        public final String aulaId;
        public final String cause;

        SerializableForm(LuxSnapshot s) {
            this.timestamp = s.timestamp;
            this.luxValue  = s.luxValue;
            this.aulaId    = s.aulaId;
            this.cause     = s.cause.name();
        }
    }

    @Override
    public String toString() {
        return "LuxSnapshot{aulaId='" + aulaId + "', lux=" + luxValue
             + ", cause=" + cause + ", ts=" + timestamp + "}";
    }
}
