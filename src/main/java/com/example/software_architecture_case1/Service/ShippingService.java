package com.example.software_architecture_case1.Service;

import com.example.software_architecture_case1.DTO.Consignment;
import com.example.software_architecture_case1.DTO.NewConsignment;
import com.example.software_architecture_case1.DTO.ShippingResult;
import com.example.software_architecture_case1.RestClient.SpeditionApiClient;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;

import java.util.concurrent.ConcurrentHashMap;

public class ShippingService {

    private final SpeditionApiClient apiClient;

    /**
     * Idempotenz-Cache: speichert pro idempotencyKey das Ergebnis eines
     * bereits erfolgreich ausgeführten Speditions-Aufrufs.
     * So wird bei einem Retry (z.B. wenn complete() an Camunda fehlschlug)
     * derselbe Auftrag NICHT nochmal an die Spedition geschickt.
     */
    private final ConcurrentHashMap<String, ShippingResult> idempotencyCache = new ConcurrentHashMap<>();

    public ShippingService(SpeditionApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Sendet einen Versandauftrag an die Spedition – idempotent.
     *
     * @param idempotencyKey  eindeutiger Schlüssel pro Auftrag (z.B. Camunda External-Task-ID).
     *                        Bei erneutem Aufruf mit demselben Key wird das gecachte Ergebnis
     *                        zurückgegeben, ohne die Spedition erneut aufzurufen.
     */
    public ShippingResult sendShippingOrder(String idempotencyKey,
                                            String destination,
                                            String recepientPhone,
                                            String customerReference,
                                            Long weight) {

        // ── Idempotenz-Prüfung ──────────────────────────────────────
        ShippingResult cached = idempotencyCache.get(idempotencyKey);
        if (cached != null) {
            System.out.println("[IDEMPOTENZ] Ergebnis für Key '" + idempotencyKey
                    + "' bereits vorhanden – überspringe REST-Call.");
            System.out.println("Versandauftrag wurde bereits zuvor mit demselben Key erfolgreich ausgeführt. Rückgabe des gecachten Ergebnisses:");
            System.out.println("  orderId      : " + cached.getOrderId());
            System.out.println("  pickupdate   : " + cached.getPickupdate());
            System.out.println("  deliverydate : " + cached.getDeliverydate());
            return cached;
        }

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
            ShippingResult result = new ShippingResult(
                    true,
                    response.getOrderId(),
                    response.getPickupdate(),
                    response.getDeliverydate()
            );

            // Ergebnis cachen, damit bei Retry kein Doppelauftrag entsteht
            idempotencyCache.put(idempotencyKey, result);
            return result;

        } catch (WebApplicationException e) {
            // fachliche Ablehnung
            if (e.getResponse() != null && e.getResponse().getStatus() == 501) {
                //Konsolenausgabe
                System.out.println("[SPEDITION] Fachliche Absage erhalten (HTTP 501) -> accepted=false, kein Retry");
                System.out.println("  customerReference: " + customerReference);
                System.out.println("  destination     : " + destination);
                System.out.println("  recepientPhone  : " + recepientPhone);
                System.out.println("  weight          : " + weight);

                ShippingResult rejected = new ShippingResult(false, null, null, null);
                idempotencyCache.put(idempotencyKey, rejected);
                return rejected;
            }
            // alle anderen HTTP Fehler -> technisch
            throw e;

        } catch (ProcessingException e) {
            // technisch (timeout, connection)
            throw e;
        }
    }
}