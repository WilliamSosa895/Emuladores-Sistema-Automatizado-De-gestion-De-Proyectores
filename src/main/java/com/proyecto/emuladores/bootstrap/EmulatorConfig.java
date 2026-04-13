package com.proyecto.emuladores.bootstrap;

import java.util.List;

/**
 * POJO que SnakeYAML popula automáticamente leyendo emulator-config.yaml.
 * Todos los campos deben tener setter/getter públicos para que SnakeYAML
 * pueda asignarlos por reflexión.
 */
public class EmulatorConfig {

    // URL del broker EMQX — ej: "tcp://192.168.1.10:1883"
    private String brokerUrl;

    // Lista de IDs de aulas a simular — ej: ["aula-1", "aula-2"]
    private List<String> aulas;

    // Intervalo en ms entre mediciones del sensor de lux en estado estable
    // Recomendado: 2000 (2 segundos)
    private int luxIntervalMs;

    // Tiempo en ms que simula el retardo físico de los actuadores
    // Recomendado: 500 (medio segundo)
    private int processingDelayMs;

    // Intervalo en ms entre cada tick de la transición de lux
    // Recomendado: 300 (curva suave y fluida en la gráfica)
    private int luxTransitionStepMs;

    // Cuántos lux se mueven por tick durante una transición
    // Recomendado: 3 (descenso gradual, sin saltos bruscos)
    private int luxTransitionDelta;

    // Cuántos snapshots se guardan en memoria por aula antes de descartar los más viejos
    // Recomendado: 200
    private int luxHistoryMaxSize;

    // Valor inicial de lux al arrancar los emuladores
    // Recomendado: 200 (aula con luces encendidas y persianas abiertas)
    private int initialLux;

    // ------------------------------------------------------------------ getters y setters

    public String getBrokerUrl()             { return brokerUrl; }
    public void setBrokerUrl(String v)       { this.brokerUrl = v; }

    public List<String> getAulas()           { return aulas; }
    public void setAulas(List<String> v)     { this.aulas = v; }

    public int getLuxIntervalMs()            { return luxIntervalMs; }
    public void setLuxIntervalMs(int v)      { this.luxIntervalMs = v; }

    public int getProcessingDelayMs()        { return processingDelayMs; }
    public void setProcessingDelayMs(int v)  { this.processingDelayMs = v; }

    public int getLuxTransitionStepMs()      { return luxTransitionStepMs; }
    public void setLuxTransitionStepMs(int v){ this.luxTransitionStepMs = v; }

    public int getLuxTransitionDelta()       { return luxTransitionDelta; }
    public void setLuxTransitionDelta(int v) { this.luxTransitionDelta = v; }

    public int getLuxHistoryMaxSize()        { return luxHistoryMaxSize; }
    public void setLuxHistoryMaxSize(int v)  { this.luxHistoryMaxSize = v; }

    public int getInitialLux()               { return initialLux; }
    public void setInitialLux(int v)         { this.initialLux = v; }

    @Override
    public String toString() {
        return "EmulatorConfig{"
             + "brokerUrl='"        + brokerUrl          + '\''
             + ", aulas="           + aulas
             + ", luxIntervalMs="   + luxIntervalMs
             + ", processingDelayMs=" + processingDelayMs
             + ", luxTransitionStepMs=" + luxTransitionStepMs
             + ", luxTransitionDelta="  + luxTransitionDelta
             + ", luxHistoryMaxSize="   + luxHistoryMaxSize
             + ", initialLux="      + initialLux
             + '}';
    }
}
