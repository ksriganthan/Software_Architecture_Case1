package com.example.software_architecture_case1.Service;

import com.example.software_architecture_case1.RestClient.Consignment;
import com.example.software_architecture_case1.RestClient.NewConsignment;
import com.example.software_architecture_case1.RestClient.SpeditionApiClient;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;

public class ShippingService {

    private final SpeditionApiClient apiClient;

    public ShippingService(SpeditionApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ShippingResult sendShippingOrder(String destination,
                                            String recepientPhone,
                                            String customerReference,
                                            Long weight) {

        // minimale fachliche Validierung
        if (customerReference == null || customerReference.isBlank()) {
            throw new IllegalArgumentException("customerReference is missing");
        }
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("destination is missing");
        }
        if (recepientPhone == null || recepientPhone.isBlank()) {
            throw new IllegalArgumentException("recepientPhone is missing");
        }
        if (weight == null || weight <= 0) {
            throw new IllegalArgumentException("weight must be > 0");
        }

        // Mapping -> Request DTO für Spedition
        NewConsignment req = new NewConsignment();
        req.setDestination(destination);
        req.setRecepientPhone(recepientPhone);
        req.setCustomerReference(customerReference);
        req.setWeight(Math.toIntExact(weight)); // BPMN: long -> API: Integer

        try {
            Consignment response = apiClient.requestConsignment(req);

            System.out.println("[SPEDITION] Response erhalten:");
            System.out.println("  orderId      : " + response.getOrderId());
            System.out.println("  pickupdate   : " + response.getPickupdate());
            System.out.println("  deliverydate : " + response.getDeliverydate());

            // Mapping -> fachliches Resultat
            return new ShippingResult(
                    true,
                    response.getOrderId(),
                    response.getPickupdate(),
                    response.getDeliverydate()
            );

        } catch (WebApplicationException e) {
            // fachliche Ablehnung (gemäss eurer Doku: 501)
            if (e.getResponse() != null && e.getResponse().getStatus() == 501) {
                //Konsolenausgabe
                System.out.println("[SPEDITION] Fachliche Absage erhalten (HTTP 501) -> accepted=false, kein Retry");
                System.out.println("  customerReference: " + customerReference);
                System.out.println("  destination     : " + destination);
                System.out.println("  recepientPhone  : " + recepientPhone);
                System.out.println("  weight          : " + weight);

                return new ShippingResult(false, null, null, null);
            }
            // alle anderen HTTP Fehler -> technisch
            throw e;

        } catch (ProcessingException e) {
            // technisch (timeout, connection)
            throw e;
        }
    }
}