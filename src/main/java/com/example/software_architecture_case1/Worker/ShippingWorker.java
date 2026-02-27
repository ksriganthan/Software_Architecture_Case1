package com.example.software_architecture_case1.Worker;

import com.example.software_architecture_case1.RestClient.TestClient;
import org.camunda.bpm.client.ExternalTaskClient;

import java.util.HashMap;


public class ShippingWorker {
    public static void main(String[] args) {

        // Verbindung zur Workflow Engine aufbauen
        ExternalTaskClient client = ExternalTaskClient.create()
                .baseUrl("http://group6:p5TuHbjEadLeT6L@192.168.111.3:8080/engine-rest").asyncResponseTimeout(1000).build();

        // für das Topic "group6_transportauftrag" registrieren und die folgende Funktion bei jedem Aufruf ausführen
        client.subscribe("group6_transportauftrag").lockDuration(1000).handler((externalTask, externalTaskService) -> {

            // Variable "customerReference" aus der Prozessinstanz auslesen
            String kundennummer = externalTask.getVariable("customerReference");
            System.out.println("Variable \"customerReference\" from process: " + kundennummer);

            // Variable "weight" aus der Prozessinstanz auslesen
            long gewicht =  externalTask.getVariable("weight");
            System.out.println("Variable \"weight\" from process: " + gewicht);

            // Variable "destination" aus der Prozessinstanz auslesen
            String kundenadresse = externalTask.getVariable("destination");
            System.out.println("Variable \"destination\" from process: " + kundenadresse);

            // Variable "recepientPhone" aus der Prozessinstanz auslesen
            String telefonnummer = externalTask.getVariable("recepientPhone");
            System.out.println("Variable \"recepientPhone\" from process: " + telefonnummer);

            // Variable "email" aus der Prozessinstanz auslesen
            String emailadresse = externalTask.getVariable("email");
            System.out.println("Variable \"email\" from process: " + emailadresse);


            //Map mit Prozessvariablen erzeugen
            HashMap<String, Object> processvariables = new HashMap<>();
            processvariables.put("customerReference", kundennummer);
            processvariables.put("weight", gewicht);
            processvariables.put("destination", kundenadresse);
            processvariables.put("recepientPhone", telefonnummer);
            processvariables.put("email", emailadresse);


            HashMap<String,Object> response = TestClient.sendRequest(processvariables, externalTask, externalTaskService);

            // Task erfolgreich abschliessen und die Map "processvariables" an die Process Engine
            // übergeben

            externalTaskService.complete(externalTask, response);
        }).open();
    }
}
