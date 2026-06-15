package com.proyecto.emuladores.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Punto de entrada del sistema de emuladores.
 *
 * <p>Carga configuración desde YAML, construye los managers por aula,
 * inicia los emuladores y registra el apagado ordenado del proceso.</p>
 */
public class EmulatorLauncher {

    private static final Logger log = LoggerFactory.getLogger(EmulatorLauncher.class);

    // ------------------------------------------------------------------ main

    public static void main(String[] args) {
        log.info("=================================================");
        log.info("  Sistema de Emuladores — Iniciando...");
        log.info("=================================================");

        // 1. Leer la configuración del YAML
        EmulatorConfig config = loadConfig();
        config.setBrokerUrl(resolveBrokerUrl(config.getBrokerUrl()));
        config.validate();
        log.info("Configuración cargada: {}", config);

        // 2. Construir un AulaEmulatorManager por cada aula
        List<AulaEmulatorManager> managers = buildAulas(config);

        // 3. Registrar el shutdown hook ANTES de startAll()
        //    Así, si el proceso se detiene con Ctrl+C o kill,
        //    todos los emuladores publican OFFLINE y se desconectan limpiamente
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown detectado — deteniendo emuladores...");
            for (AulaEmulatorManager manager : managers) {
                manager.stopAll();
            }
            log.info("Todos los emuladores detenidos. Hasta luego.");
        }, "shutdown-hook"));

        // 4. Arrancar todos los emuladores de todas las aulas
        for (AulaEmulatorManager manager : managers) {
            manager.startAll();
        }

        log.info("=================================================");
        log.info("  {} aula(s) activa(s) — Sistema en línea",
                config.getAulas().size());
        log.info("  Broker: {}", config.getBrokerUrl());
        log.info("  Presiona Ctrl+C para detener");
        log.info("=================================================");

        // 5. Mantener el proceso vivo indefinidamente
        //    El ScheduledExecutor de cada sensor y el simulador corren en hilos daemon,
        //    por eso necesitamos este bloqueo explícito en el hilo principal.
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            log.info("Hilo principal interrumpido — iniciando apagado...");
            Thread.currentThread().interrupt();
        }
    }

    private static String resolveBrokerUrl(String configuredValue) {
        String envValue = System.getenv("MQTT_BROKER_URL");
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }

        return configuredValue;
    }

    // ------------------------------------------------------------------ loadConfig
    // Lee src/main/resources/emulator-config.yaml del classpath
    // y lo deserializa en un EmulatorConfig usando SnakeYAML.

    private static EmulatorConfig loadConfig() {
        try {
            LoaderOptions loaderOptions = new LoaderOptions();
            Yaml yaml = new Yaml(new Constructor(EmulatorConfig.class, loaderOptions));

            InputStream input = EmulatorLauncher.class
                    .getClassLoader()
                    .getResourceAsStream("emulator-config.yaml");

            if (input == null) {
                throw new RuntimeException(
                    "No se encontró emulator-config.yaml en el classpath. "
                  + "Asegúrate de que existe en src/main/resources/"
                );
            }

            EmulatorConfig config = yaml.load(input);
            if (config == null) {
                throw new IllegalArgumentException("emulator-config.yaml está vacío o no tiene formato válido");
            }
            log.info("emulator-config.yaml cargado correctamente");
            return config;

        } catch (Exception e) {
            log.error("Error crítico al leer emulator-config.yaml: {}", e.getMessage());
            throw new RuntimeException("No se puede iniciar sin configuración válida", e);
        }
    }

    // ------------------------------------------------------------------ buildAulas
    // Crea un AulaEmulatorManager por cada aula definida en el YAML.
    // Cada manager construye y cablea sus 7 emuladores de forma independiente.

    private static List<AulaEmulatorManager> buildAulas(EmulatorConfig config) {
        List<AulaEmulatorManager> managers = new ArrayList<>();

        for (String aulaId : config.getAulas()) {
            log.info("Construyendo emuladores para: {}", aulaId);
            try {
                AulaEmulatorManager manager = new AulaEmulatorManager(aulaId, config);
                managers.add(manager);
            } catch (Exception e) {
                log.error("Error al construir emuladores para {}: {}", aulaId, e.getMessage());
                // Continuar con las demás aulas aunque una falle
            }
        }

        log.info("{} aula(s) construidas correctamente", managers.size());
        return managers;
    }
}
