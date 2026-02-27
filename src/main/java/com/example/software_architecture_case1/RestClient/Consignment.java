package com.example.software_architecture_case1.RestClient;



public class Consignment {

	private String orderId;
	private Integer weight;
	private String pickupdate;
	private String deliverydate;
	private String customerRefernce;
	private String recepientPhone;
	private String destination;

	public String getOrderId() {
		return orderId;
	}

	public void setOrderId(String orderId) {
		this.orderId = orderId;
	}

	public Integer getWeight() {
		return weight;
	}

	public void setWeight(Integer weight) {
		this.weight = weight;
	}

	public String getPickupdate() {
		return pickupdate;
	}

	public void setPickupdate(String pickupdate) {
		this.pickupdate = pickupdate;
	}

	public String getDeliverydate() {
		return deliverydate;
	}

	public void setDeliverydate(String deliverydate) {
		this.deliverydate = deliverydate;
	}

	public String getCustomerRefernce() {
		return customerRefernce;
	}

	public void setCustomerRefernce(String customerRefernce) {
		this.customerRefernce = customerRefernce;
	}

	public String getRecepientPhone() {
		return recepientPhone;
	}

	public void setRecepientPhone(String recepientPhone) {
		this.recepientPhone = recepientPhone;
	}

	public String getDestination() {
		return destination;
	}

	public void setDestination(String destination) {
		this.destination = destination;
	}

}