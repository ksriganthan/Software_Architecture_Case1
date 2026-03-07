package com.example.software_architecture_case1.Worker;

import com.example.software_architecture_case1.RestClient.SpeditionApiClient;
import com.example.software_architecture_case1.Service.ShippingService;
import org.camunda.bpm.client.ExternalTaskClient;

// Bootstrap-Klasse (Main) für den Worker-Prozess.
public class ShippingWorker {

    public static void main(String[] args) {

        // ── 1) Camunda External Task Client konfigurieren ──────────────
        ExternalTaskClient client = ExternalTaskClient.create()
                .baseUrl("http://group6:p5TuHbjEadLeT6L@192.168.111.3:8080/engine-rest")
                // Long polling / async response timeout (ms)
                .asyncResponseTimeout(1000)
                .build();
        // ── 2) Technische Schicht (Adapter) ────────────────────────────
        SpeditionApiClient apiClient = new SpeditionApiClient("http://192.168.111.5:8080/v1/consignment/request");

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