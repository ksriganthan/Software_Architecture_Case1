package com.example.software_architecture_case1.Service;

import com.example.software_architecture_case1.DTO.Consignment;
import com.example.software_architecture_case1.DTO.NewConsignment;
import com.example.software_architecture_case1.DTO.ShippingResult;
import com.example.software_architecture_case1.RestClient.SpeditionApiClient;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;

import java.util.concurrent.ConcurrentHashMap;

public class ShippingService {

    //Service-Schicht (Business Layer) - Schicht 2
    private final SpeditionApiClient apiClient;

    /**
     * Idempotenz-Cache: speichert pro idempotencyKey das Ergebnis eines
     * bereits erfolgreich ausgeführten Speditions-Aufrufs.
     * So wird bei einem Retry (z.B. wenn complete() an Camunda fehlschlug)
     * derselbe Auftrag NICHT nochmal an die Spedition geschickt.
     *
     * Warum?
     * Wenn REST-Call erfolgreich war, aber externalTaskService.complete(...) scheitert,
     * liefert Camunda dieselbe ExternalTask später erneut aus. Ohne Cache -> selber Auftrag würde 2x geschicht werden
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

        // ── 1) Idempotenz-Prüfung ──────────────────────────────────────
        //Wenn wir diesen Task bereits erfolgreich bearbeitet haben, geben wir einfach das alte Resultat zurück.
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

        // ── 2) Minimale fachliche Validierung ──────────────────────────
        // Ziel: offensichtliche Prozessdatenfehler früh abfangen -> kein Retry.
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

        // ── 3) Mapping: Prozessdaten -> Request DTO ─────────────────────
        NewConsignment req = new NewConsignment();
        req.setDestination(destination);
        req.setRecepientPhone(recepientPhone);
        req.setCustomerReference(customerReference);
        req.setWeight(Math.toIntExact(weight)); // BPMN: long -> API: Integer

        try {
            // ── 4) Technischer REST Call via API Client ──────────────────
            Consignment response = apiClient.requestConsignment(req);

            System.out.println("[SPEDITION] Response erhalten:");
            System.out.println("  orderId      : " + response.getOrderId());
            System.out.println("  pickupdate   : " + response.getPickupdate());
            System.out.println("  deliverydate : " + response.getDeliverydate());

            // ── 5) Mapping: Spedition Response -> internes Ergebnis ─────
            ShippingResult result = new ShippingResult(
                    true,
                    response.getOrderId(),
                    response.getPickupdate(),
                    response.getDeliverydate()
            );

            // ── 6) Cache speichern (Idempotenz) ─────────────────────────
            // Wichtig: erst NACH erfolgreichem Call + Mapping speichern.
            idempotencyCache.put(idempotencyKey, result);
            return result;

        } catch (WebApplicationException e) {
            // HTTP Fehler: kann fachlich oder technisch sein.
            if (e.getResponse() != null && e.getResponse().getStatus() == 501) {
                // 501-> "fachliche Absage"
                // -> accepted=false, KEIN Retry, Gateway führt auf Hotline-Pfad.
                System.out.println("[SPEDITION] Fachliche Absage erhalten (HTTP 501) -> accepted=false, kein Retry");
                System.out.println("  customerReference: " + customerReference);
                System.out.println("  destination     : " + destination);
                System.out.println("  recepientPhone  : " + recepientPhone);
                System.out.println("  weight          : " + weight);
                // Auch rejected cachen
                ShippingResult rejected = new ShippingResult(false, null, null, null);
                idempotencyCache.put(idempotencyKey, rejected);
                return rejected;
            }
            // alle anderen HTTP Fehler -> technisch
            // -> Handler soll Retries/Incident steuern.
            throw e;

        } catch (ProcessingException e) {
            // Technische Fehler (Timeout / Connection) -> Handler macht Retry
            throw e;
        }
    }
}