package com.example.software_architecture_case1.Worker;

import com.example.software_architecture_case1.Service.ShippingResult;
import com.example.software_architecture_case1.Service.ShippingService;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;

import java.util.HashMap;
import java.util.Map;

public class ShippingExternalTaskHandler implements ExternalTaskHandler {

    private final ShippingService shippingService;

    public ShippingExternalTaskHandler(ShippingService shippingService) {
        this.shippingService = shippingService;
    }

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {

        // Prozessvariablen aus BPMN (passen zu eurem Modell)
        String customerReference = externalTask.getVariable("customerReference");
        Long weight = externalTask.getVariable("weight");
        String destination = externalTask.getVariable("destination");
        String recepientPhone = externalTask.getVariable("recepientPhone");
        String email = externalTask.getVariable("email"); // aktuell nur geloggt, aber ok

        System.out.println("customerReference: " + customerReference);
        System.out.println("weight          : " + weight);
        System.out.println("destination     : " + destination);
        System.out.println("recepientPhone  : " + recepientPhone);
        System.out.println("email           : " + email);

        try {
            ShippingResult result = shippingService.sendShippingOrder(
                    destination,
                    recepientPhone,
                    customerReference,
                    weight
            );

            Map<String, Object> vars = new HashMap<>();
            vars.put("accepted", result.isAccepted());

            if (result.isAccepted()) {
                // Daten für euren späteren UserTask "A38-Formular ergänzen ..."
                vars.put("orderId", result.getOrderId());
                vars.put("pickupdate", result.getPickupdate());
                vars.put("deliverydate", result.getDeliverydate());
            }

            externalTaskService.complete(externalTask, vars);

        } catch (IllegalArgumentException e) {
            // Fachlicher Fehler in Inputs -> kein Retry sinnvoll
            externalTaskService.handleFailure(
                    externalTask,
                    "Invalid process variables",
                    e.getMessage(),
                    0,
                    0L
            );

        } catch (WebApplicationException | ProcessingException e) {
            // Technischer Fehler -> Retry Strategie (wie ihr es konzeptionell wolltet)
            Integer retries = externalTask.getRetries();
            int remainingRetries = (retries == null) ? 3 : retries - 1;

            System.out.println("Technical error, remaining retries: " + remainingRetries);

            externalTaskService.handleFailure(
                    externalTask,
                    "REST not reachable / technical error",
                    e.getMessage(),
                    remainingRetries,
                    60_000L
            );
        }
    }
}