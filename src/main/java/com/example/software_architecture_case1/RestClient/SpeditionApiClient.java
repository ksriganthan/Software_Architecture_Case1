package com.example.software_architecture_case1.RestClient;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;

public class SpeditionApiClient {

    private final String serviceUrl;

    public SpeditionApiClient(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public Consignment requestConsignment(NewConsignment request) {
        Client client = ClientBuilder.newClient();
        try {
            WebTarget target = client.target(serviceUrl);

            return target.request(MediaType.APPLICATION_JSON)
                    .post(Entity.entity(request, MediaType.APPLICATION_JSON), Consignment.class);

        } catch (WebApplicationException e) {
            // HTTP Fehler (z.B. 501 = fachliche Ablehnung)
            throw e;
        } catch (ProcessingException e) {
            // Timeout / Connection / DNS / etc.
            throw e;
        } finally {
            client.close();
        }
    }
}