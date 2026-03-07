package com.example.software_architecture_case1.Worker;

import com.example.software_architecture_case1.DTO.ShippingResult;
import com.example.software_architecture_case1.Service.ShippingService;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;

import java.util.HashMap;
import java.util.Map;
// Camunda External Task Handler (Orchestrierungs-/Integrationsschicht) - Schicht 1
public class ShippingExternalTaskHandler implements ExternalTaskHandler {

    private final ShippingService shippingService;

    public ShippingExternalTaskHandler(ShippingService shippingService) {
        this.shippingService = shippingService;
    }

    //execute(...) ist der Einstiegspunkt, den der Camunda External Task Client aufruft.
    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {

        // ── 1) Prozessvariablen lesen ──────────────────────────────────
        // Variablennamen müssen exakt zum BPMN-Modell passen.
        String customerReference = externalTask.getVariable("customerReference");
        Long weight = externalTask.getVariable("weight");
        String destination = externalTask.getVariable("destination");
        String recepientPhone = externalTask.getVariable("recepientPhone");
        String email = externalTask.getVariable("email"); // aktuell nur geloggt, aber ok
        // Logging für Demo/Debug (zeigt was aus dem Prozess kommt)
        System.out.println("customerReference: " + customerReference);
        System.out.println("weight          : " + weight);
        System.out.println("destination     : " + destination);
        System.out.println("recepientPhone  : " + recepientPhone);
        System.out.println("email           : " + email);

        try {
            // ── 2) Idempotency Key festlegen ────────────────────────────
            // ExternalTask-ID bleibt bei Retries gleich -> perfekt als Key
            // (Schützt vor Doppel-POSTs an die Spedition)
            String idempotencyKey = externalTask.getId();

            // ── 3) Fachliche Operation ausführen (Service Layer) ───────
            ShippingResult result = shippingService.sendShippingOrder(
                    idempotencyKey,
                    destination,
                    recepientPhone,
                    customerReference,
                    weight
            );

            // ── 4) Prozessvariablen zurückschreiben ─────────────────────
            Map<String, Object> vars = new HashMap<>();
            vars.put("accepted", result.isAccepted()); // immer setzen, Gateway entscheidet danach

            if (result.isAccepted()) {
                // Daten für späteren UserTask "A38-Formular ergänzen ..."
                vars.put("orderId", result.getOrderId());
                vars.put("pickupdate", result.getPickupdate());
                vars.put("deliverydate", result.getDeliverydate());
            }
            // Task erfolgreich abschliessen -> Prozess läuft weiter
            externalTaskService.complete(externalTask, vars);

        } catch (IllegalArgumentException e) {
            // ── Fachlicher Fehler: Inputs unplausibel / fehlen ──────────
            // Kein Retry sinnvoll, weil gleiche falsche Prozessdaten wiederkommen würden.
            // retries = 0 -> Camunda kann einen Incident erzeugen -> Mensch muss korrigieren.
            externalTaskService.handleFailure(
                    externalTask,
                    "Invalid process variables",
                    e.getMessage(),
                    0,
                    0L
            );

        } catch (WebApplicationException | ProcessingException e) {
            // ── Technischer Fehler: REST nicht erreichbar / Timeout / HTTP 500 etc. ──
            // Retry Strategie:
            // - Wenn retries null ist, starten wir mit 3
            // - sonst 1 abziehen
            Integer retries = externalTask.getRetries();
            int remainingRetries = (retries == null) ? 3 : retries - 1;

            System.out.println("Technical error, remaining retries: " + remainingRetries);

            externalTaskService.handleFailure(
                    externalTask,
                    "REST not reachable / technical error",
                    e.getMessage(),
                    remainingRetries,   // - remainingRetries wird an Camunda zurückgemeldet
                    60_000L             // 60 Sekunden warten bis nächster Versuch
            );
        }
    }
}