package com.tartaritech.inventory_sync.controllers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tartaritech.inventory_sync.services.WebhookWorker;
import com.tartaritech.inventory_sync.utils.HmacMd5Generator;

@RestController
@RequestMapping("webhooks/pagstream")
public class PagStreamController {

    private final ObjectMapper objectMapper;
    private final WebhookWorker webhookWorker;

    public PagStreamController(ObjectMapper objectMapper, WebhookWorker webhookWorker) {
        this.objectMapper = objectMapper;
        this.webhookWorker = webhookWorker;
    }

    @Value("${pagbrasil.secret}")
    private String pagBrasilSecret;

    @Value("${pagbrasil.hmac.key}")
    private String pagBrasilKey;


    @PostMapping("/subscription-update")
    public ResponseEntity<Object> updateOrder(@RequestBody String obj) throws JsonProcessingException {

        System.out.println("RECEIVED WEBHOOK ORDER UPDATE");
        
        // Converte a string para JsonNode e formata
        JsonNode webhook = objectMapper.readTree(obj);
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(webhook));

        String signature = webhook.get("signature").asText();
      

        List<String> values = new ArrayList<>();
        processSignature(webhook, values, Arrays.asList("secret", "signature"));

        String concat = String.join("", values); 

        String concatFinal = concat + concat.length();

        String result = HmacMd5Generator.generateHmacMD5(concatFinal, pagBrasilKey);

        System.out.println("\n\nRESULT " + result + "\n\n");
        System.out.println("\nSIGNATURE " + signature + "\n\n");

        if (!result.equals(signature)) {
            return ResponseEntity.status(401).body("Unauthorized - invalid signature");
        }

        // Extrai o secret do payload para idempotência
        String sginature = webhook.get("signature").asText();
        webhookWorker.processSubscriptionUpdateJob(obj, sginature);


        return ResponseEntity.ok("Received successfully " + Instant.now());

    }


    private void processSignature (JsonNode node, List<String> result, List<String> remove) {

        if (node.isObject()){
            Iterator<String> fieldNames = node.fieldNames();
         
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode value = node.get(fieldName);

                if (remove.contains(fieldName)) continue;

                processSignature(value, result, remove);
            }
        } else {
            if (node != null) {
                result.add(node.asText());
            }
        }
    

    }

   
}
