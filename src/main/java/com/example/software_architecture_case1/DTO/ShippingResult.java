package com.example.software_architecture_case1.DTO;

/**
 * Internes Ergebnis-Objekt (Domain/Business DTO) für den weiteren Prozess.
 * Idee:
 *  * - Der Handler soll NICHT direkt mit "Consignment" arbeiten
 */

public class ShippingResult {

    private final boolean accepted;
    private final String orderId;
    private final String pickupdate;
    private final String deliverydate;

    public ShippingResult(boolean accepted, String orderId, String pickupdate, String deliverydate) {
        this.accepted = accepted;
        this.orderId = orderId;
        this.pickupdate = pickupdate;
        this.deliverydate = deliverydate;
    }

    //true -> Spedition hat angenommen false -> Spedition hat fachlich abgelehnt (Gebiet)
    public boolean isAccepted() {
        return accepted;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getPickupdate() {
        return pickupdate;
    }

    public String getDeliverydate() {
        return deliverydate;
    }
}