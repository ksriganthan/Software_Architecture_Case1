package com.example.software_architecture_case1.DTO;


// DTO für den Request an die Spedition (Outbound Payload).
public class NewConsignment {

	private String destination;
	private String customerReference;
	private String recepientPhone;
	private Integer weight;

	public String getDestination() {
		return destination;
	}

	public void setDestination(String destination) {
		this.destination = destination;
	}

	public String getCustomerReference() {
		return customerReference;
	}

	public void setCustomerReference(String customerReference) {
		this.customerReference = customerReference;
	}

	public String getRecepientPhone() {
		return recepientPhone;
	}

	public void setRecepientPhone(String recepientPhone) {
		this.recepientPhone = recepientPhone;
	}

	public Integer getWeight() {
		return weight;
	}

	public void setWeight(Integer weight) {
		this.weight = weight;
	}

}