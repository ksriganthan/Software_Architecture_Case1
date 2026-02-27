package com.example.software_architecture_case1.RestClient;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;

import java.util.HashMap;


// Documentation on the JAX-RS Client API: https://eclipse-ee4j.github.io/jersey.github.io/documentation/latest/client.html

public class TestClient {

	final static String SERVICE_URL = "http://192.168.111.5:8080/v1/consignment/request";

	public static HashMap<String, Object> sendRequest(HashMap<String, Object> processvariables, ExternalTask externalTask, ExternalTaskService externalTaskService) {

		// Create a REST Service Client and a Target where the client should send
		// requests to
		Client client = ClientBuilder.newClient();
		WebTarget target = client.target(SERVICE_URL);
		HashMap<String, Object> speditionAPIResponse = new HashMap<>();

		// create the message object that we will send to the service
		NewConsignment nc = new NewConsignment();
		nc.setDestination(processvariables.get("destination").toString());
		nc.setRecepientPhone(processvariables.get("recepientPhone").toString());
		nc.setCustomerReference(processvariables.get("customerReference").toString());
		//nc.setWeight(Integer.parseInt(processvariables.get("weight").toString()));


		try {
			// send a POST request to the URL from above with the NewConsignment object as
			// request body. The Java object "nc" is automatically converted into JSON
			// before transmission. Furthermore the JSON reply from the service is parsed
			// into a Java object of the class "Consignment".

			Consignment response = target.request(MediaType.APPLICATION_JSON)
					.post(Entity.entity(nc, MediaType.APPLICATION_JSON), Consignment.class);

			speditionAPIResponse.put("orderId", response.getOrderId());
			speditionAPIResponse.put("pickupdate", response.getPickupdate());
			speditionAPIResponse.put("deliverydate", response.getDeliverydate());


			// print some results received from the service
			System.out.println("Shipping Order ID: " + response.getOrderId());
			System.out.println("Pickup Date      : " + response.getPickupdate());
			System.out.println("Delivery Date    : " + response.getDeliverydate());
			speditionAPIResponse.put("accepted", true);

		} catch (WebApplicationException e) {
			// In case the request was not successful, we decide based on the HTTP status
			// code returned by the service
			if (e.getResponse().getStatus() == 501) {
				speditionAPIResponse.put("accepted", false);
				System.out.println("Request was not possible, please use hotline to order");

			} else {
				// Technischer Fehler: REST-Service nicht erreichbar
				// 3 Versuche
				// Task wird nach jedem Retry von Camunda zum Worker zurückgegeben, damit die Verbindung zum REST-Service
				// neu aufgebaut werden kann
				Integer retries = externalTask.getRetries();
				int remainingRetries = (retries == null) ? 3 : retries - 1;
				System.out.println("Retry Count: " + remainingRetries);
				externalTaskService.handleFailure(
						externalTask,
						"REST not reachable",
						"Timeout / error while calling TestClient: " + e.getMessage(),
						remainingRetries,
						60_000L
				);
				// Nach jedem Versuch den Client schließen, damit die Verbindungsressourcen freigegeben werden
				client.close();
				return speditionAPIResponse;  // Früh zurückgeben, nicht weitermachen
			}
		}

		client.close();
		return speditionAPIResponse;
	}
}
