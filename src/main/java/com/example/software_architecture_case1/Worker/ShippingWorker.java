package com.example.software_architecture_case1.Worker;

import com.example.software_architecture_case1.RestClient.SpeditionApiClient;
import com.example.software_architecture_case1.Service.ShippingService;
import org.camunda.bpm.client.ExternalTaskClient;

public class ShippingWorker {

    public static void main(String[] args) {

        ExternalTaskClient client = ExternalTaskClient.create()
                .baseUrl("http://group6:p5TuHbjEadLeT6L@192.168.111.3:8080/engine-rest")
                .asyncResponseTimeout(1000)
                .build();

        SpeditionApiClient apiClient =
                new SpeditionApiClient("http://192.168.111.5:8080/v1/consignment/request");
        ShippingService shippingService = new ShippingService(apiClient);

        client.subscribe("group6_transportauftrag")
                .lockDuration(1000)
                .handler(new ShippingExternalTaskHandler(shippingService))
                .open();
    }
}