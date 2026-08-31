package com.example.software_architecture_case1.Worker;

import com.example.software_architecture_case1.RestClient.SpeditionApiClient;
import com.example.software_architecture_case1.Service.ShippingService;
import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.client.ExternalTaskClientBuilder;
import org.camunda.bpm.client.interceptor.auth.BasicAuthProvider;

// Bootstrap-Klasse (Main) für den Worker-Prozess.
public class ShippingWorker {

    /**
     * Endpunkte und Zugangsdaten werden aus Umgebungsvariablen gelesen,
     * damit keine Zugangsdaten im Repository landen.
     *
     * CAMUNDA_BASE_URL   z.B. http://<camunda-host>:8080/engine-rest
     * CAMUNDA_USER       Benutzername für Basic Auth (optional)
     * CAMUNDA_PASSWORD   Passwort für Basic Auth (optional)
     * SPEDITION_URL      Endpunkt des Speditions-REST-Service
     */
    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    public static void main(String[] args) {

        // ── 1) Camunda External Task Client konfigurieren ──────────────
        ExternalTaskClientBuilder builder = ExternalTaskClient.create()
                .baseUrl(env("CAMUNDA_BASE_URL", "http://localhost:8080/engine-rest"))
                // Long polling / async response timeout (ms)
                .asyncResponseTimeout(1000);

        // Basic Auth nur aktivieren, wenn Zugangsdaten gesetzt sind
        String camundaUser = env("CAMUNDA_USER", "");
        if (!camundaUser.isBlank()) {
            builder = builder.addInterceptor(
                    new BasicAuthProvider(camundaUser, env("CAMUNDA_PASSWORD", "")));
        }

        ExternalTaskClient client = builder.build();

        // ── 2) Technische Schicht (Adapter) ────────────────────────────
        SpeditionApiClient apiClient = new SpeditionApiClient(
                env("SPEDITION_URL", "http://localhost:8080/v1/consignment/request"));

        // ── 3) Service-Schicht (Fachlogik + Idempotenz) ────────────────
        ShippingService shippingService = new ShippingService(apiClient);

        // ── 4) Topic Subscription: "group6_transportauftrag" ───────────
        // lockDuration: wie lange der Task "gesperrt" ist, während dieser Worker ihn bearbeitet
        client.subscribe("group6_transportauftrag")
                .lockDuration(1000)
                .handler(new ShippingExternalTaskHandler(shippingService))  // Handler ist Schicht 1: Camunda-Anbindung
                .open();                                                    // Start listening (läuft weiter)
    }
}