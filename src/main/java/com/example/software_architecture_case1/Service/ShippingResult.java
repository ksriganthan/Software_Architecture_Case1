package com.example.software_architecture_case1.Service;

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