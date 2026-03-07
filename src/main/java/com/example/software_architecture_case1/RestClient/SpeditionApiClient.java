package com.example.software_architecture_case1.RestClient;

import com.example.software_architecture_case1.DTO.Consignment;
import com.example.software_architecture_case1.DTO.NewConsignment;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;

//Technische Abstraktion (Adapter) zur Spedition-API - Schicht 3.
public class SpeditionApiClient {

    private final String serviceUrl;

    public SpeditionApiClient(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public Consignment requestConsignment(NewConsignment request) {
        // JAX-RS Client für HTTP Requests
        Client client = ClientBuilder.newClient();
        try {
            // Base URL als Target
            WebTarget target = client.target(serviceUrl);
            // POST mit JSON Body und erwarte JSON Response, gemappt auf Consignment.class
            return target.request(MediaType.APPLICATION_JSON)
                    .post(Entity.entity(request, MediaType.APPLICATION_JSON), Consignment.class);

        } catch (WebApplicationException e) {
            // HTTP Fehler (z.B. 501 = fachliche Ablehnung)
            // -> wird bewusst nach oben geworfen, weil Service-Schicht entscheiden soll, ob fachlich/technisch
            throw e;
        } catch (ProcessingException e) {
            // Technische Fehler: Timeout, Connection, SSL, DNS etc.
            throw e;
        } finally {
            // Ressourcen sauber freigeben (wichtig bei vielen Calls)
            client.close();
        }
    }
}