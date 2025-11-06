package com.tartaritech.inventory_sync.services;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tartaritech.inventory_sync.entities.WebhookJob;
import com.tartaritech.inventory_sync.dtos.OrderDTO;
import com.tartaritech.inventory_sync.entities.Order;
import com.tartaritech.inventory_sync.entities.Product;
import com.tartaritech.inventory_sync.entities.ShopifySyncOperation;
import com.tartaritech.inventory_sync.entities.Subscription;
import com.tartaritech.inventory_sync.entities.WebhookIdempotency;
import com.tartaritech.inventory_sync.enums.JobStatus;
import com.tartaritech.inventory_sync.repositories.WebhookJobRepository;
import com.tartaritech.inventory_sync.repositories.OrderRepository;
import com.tartaritech.inventory_sync.repositories.ProductRepository;
import com.tartaritech.inventory_sync.repositories.ShopifySyncOperationRepository;
import com.tartaritech.inventory_sync.repositories.SubscriptionRepository;
import com.tartaritech.inventory_sync.repositories.WebhookIdempotencyRepository;

@Service
public class WebhookWorker {

    private final WebhookJobRepository webhookJobRepository;
    private final WebhookIdempotencyRepository webhookIdempotencyRepository;
    private final WebhookJobService webhookJobService;
    private final ObjectMapper objectMapper;
    private final ProductRepository productRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ShopifySyncOperationRepository shopifySyncOperationRepository;
    private final OrderRepository orderRepository;

    private final Logger logger = LoggerFactory.getLogger(WebhookWorker.class);

    @Value("${webhook.job.max.attempts:5}")
    private int maxAttempts;


    @Value("${webhook.job.processing.interval:10000}")
    private long processingInterval;

    public WebhookWorker(WebhookJobRepository webhookJobRepository,
            WebhookIdempotencyRepository webhookIdempotencyRepository,
            WebhookJobService webhookJobService,
            ObjectMapper objectMapper,
            SubscriptionRepository subscriptionRepository,
            ProductRepository productRepository,
            ShopifySyncOperationRepository shopifySyncOperationRepository,
            OrderRepository orderRepository) {
        this.webhookJobRepository = webhookJobRepository;
        this.webhookIdempotencyRepository = webhookIdempotencyRepository;
        this.webhookJobService = webhookJobService;
        this.objectMapper = objectMapper;
        this.subscriptionRepository = subscriptionRepository;
        this.productRepository = productRepository;
        this.shopifySyncOperationRepository = shopifySyncOperationRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public void processSubscriptionUpdateJob(String payload, String signature)
            throws JsonMappingException, JsonProcessingException {

        // Verifica se já existe um processamento para este signature
        Optional<WebhookIdempotency> existingIdempotency = webhookIdempotencyRepository.findBySignature(signature);

        if (existingIdempotency.isPresent()) {
            // Atualiza o lastProcessedAt para rastrear reenvios
            webhookIdempotencyRepository.updateLastProcessedAt(signature, Instant.now());
            System.out.println("Webhook já processado anteriormente (reenvio detectado). Assiantura: " + signature
                    + " | Job ID: " +
                    existingIdempotency.get().getWebhookJobId());
            return; // Webhook já foi processado, retorna sem fazer nada
        }

        JsonNode node = objectMapper.readTree(payload);
        String subscriptionId = node.get("subscription").asText();

        // Cria o job usando factory method
        WebhookJob job = WebhookJob.createNewJob("pagstream", "subscription_update", payload, subscriptionId);

        WebhookJob savedJob = webhookJobRepository.save(job);

        // Cria o registro de idempotência usando factory method
        WebhookIdempotency idempotency = WebhookIdempotency.createNew(
                signature + Instant.now(), // remover o Instant.now() em produção
                "pagstream",
                "subscription_update",
                subscriptionId,
                savedJob.getId());

        webhookIdempotencyRepository.save(idempotency);

        System.out
                .println("Webhook processado com sucesso. Job ID: " + savedJob.getId() + " | Assinatura: " + signature);
    }

    /**
     * Processa jobs pendentes da fila
     * Intervalo configurável via webhook.job.processing.interval
     * Padrão: 30 segundos (para testes)
     */
    @Scheduled(fixedRateString = "${webhook.job.processing.interval:30000}")
    @Transactional
    public void processPendingJobs() {

        List<WebhookJob> pendingJobs = webhookJobService.getPendingJobsReadyForExecution();

        if (pendingJobs.isEmpty()) {
            System.out.println("Nenhum job pendente encontrado para processamento");
            return;
        }

        System.out.println("Processando " + pendingJobs.size() + " jobs pendentes");

        int processedCount = 0;
        int successCount = 0;
        int failureCount = 0;

        for (WebhookJob job : pendingJobs) {
            try {
                processJob(job);
                successCount++;
                processedCount++;
            } catch (Exception e) {
                failureCount++;
                processedCount++;
                System.err.println("Erro ao processar job " + job.getId() + ": " + e.getMessage());
            }
        }

        System.out.println("Processamento concluído: " + processedCount + " jobs processados, " +
                successCount + " sucessos, " + failureCount + " falhas");
    }

    @Transactional
    public void processJob(WebhookJob job) {
        Instant now = Instant.now();

        // Marca job como RUNNING
        webhookJobService.updateJobStatus(job.getId(), JobStatus.RUNNING);

        try {
            // Processa o job baseado no tipo de evento
            if ("subscription_update".equals(job.getEventType())) {
                processSubscriptionUpdate(job);
            } else {
                throw new IllegalArgumentException("Tipo de evento não suportado: " + job.getEventType());
            }

            // Marca como DONE se processou com sucesso
            webhookJobService.updateJobStatus(job.getId(), JobStatus.DONE);
            System.out.println("Job " + job.getId() + " processado com sucesso");

        } catch (Exception e) {
            handleJobFailure(job, e, now);
        }
    }

    private void processSubscriptionUpdate(WebhookJob job) throws Exception {

        JsonNode payload = objectMapper.readTree(job.getPayload());

        String subscriptionId = payload.get("subscription").asText();
        Integer payLoadStatus = payload.get("status").asInt();
        Integer payloadNumberRecurrences = payload.get("number_recurrences").asInt();

        System.out.println("Processando subscription update para: " + subscriptionId);

        Optional<Subscription> existingSubscription = subscriptionRepository.findById(subscriptionId);

        if (!existingSubscription.isPresent()) {
            return;
        }

        switch (payLoadStatus) {
            case 1:
                processActiveSubscriptionPayload(payload, existingSubscription.get(), payloadNumberRecurrences);
                break;
            case 2:
                processPendingPaymentSubscriptionPayload(payload, existingSubscription.get(), payloadNumberRecurrences);
                break;
            case 3:
                processCancelOrPausedSubscriptionPayload(payLoadStatus, existingSubscription.get());
                break;
            case 5:
                processCancelOrPausedSubscriptionPayload(payLoadStatus, existingSubscription.get());
                break;
            default:
                logger.info(
                        "Pulando update de assinatura. Payload recebido via webhook com status diferente de ativo, cancleado, pausado ou aguardando pagamento. Status recebido {}",
                        payLoadStatus);
                break;
        }

        // Subscription subscription = existingSubscription.get();
        // // Set<NextItem> currentItems = subscription.getNextItems();
        // Integer nextRecurrence = subscription.getNextRecurrence();

        // if (payLoadStatus != 1) {
        // boolean wasProcessed = processCancel(payLoadStatus,
        // subscription);
        // if (wasProcessed) {
        // return;
        // }
        // }

        // // if (currentItems.isEmpty())
        // // return;

        // if (payloadNumberRecurrences < nextRecurrence) {
        // // Verifica se tem pedido anterior salvo no DB
        // boolean hasPrevOrder = subscription.getRecurrences().stream().anyMatch(r ->
        // r.getNumberRecurrence() == payloadNumberRecurrences);
        // if (!hasPrevOrder) return;
        // processPrevOrder(subscription, payload, payLoadStatus);
        // }

        // if (payloadNumberRecurrences > nextRecurrence) {
        // List<ShopifySyncOperation> operations =
        // createShopifySyncOperationsForExpiredOrders(subscription);
        // shopifySyncOperationRepository.saveAll(operations);
        // deleteSubscriptionAndOrders(subscription);
        // return;
        // }

        // // Se estiver na mesma recorrencia, e não tiver sido pago - > criar lista de
        // pendingNextItem (sku, quantity e numberRecurrence);
        // if (payLoadStatus != 1) {
        // // List<NextItem> pendingItems = subscription.getNextItems()
        // return;
        // }

        // List<ShopifySyncOperation> operations =
        // createShopifySyncOperationsForSubscriptionPaid(subscription);
        // shopifySyncOperationRepository.saveAll(operations);
        // deleteSubscriptionAndOrders(subscription);

    }

    private void processPendingPaymentSubscriptionPayload(JsonNode payload, Subscription subscription,
            Integer payloadNumberRecurrences) {

        List<Order> orders = subscription.getRecurrences().stream()
                .filter(r -> r.getNumberRecurrence() < payloadNumberRecurrences).collect(Collectors.toList());

        if (orders.isEmpty()) {
            logger.info(
                    "Pulando update da subscription #{} com pagamento pendente. Nenhuma Order menor do que a Recorrencia recebida no Payload",
                    subscription.getId());
            return;
        }

        List<ShopifySyncOperation> operations = createShopifySyncOperationsForExpiredOrders(orders, subscription.getId());
        shopifySyncOperationRepository.saveAll(operations);

        boolean haveMoreRecurrences = subscription.getRecurrences().stream()
                .anyMatch(r -> r.getNumberRecurrence() >= payloadNumberRecurrences);

        if (!haveMoreRecurrences) {
            deleteSubscriptionAndOrders(subscription);
            return;
        }

        deleteOrdersFromSubscription(subscription, payloadNumberRecurrences);

    }

    private void processActiveSubscriptionPayload(JsonNode payload, Subscription subscription,
            Integer payloadNumberRecurrences) {

        int highestSubscriptionRecurrenceNumber = findHighestSubscriptionRecurrenceNumber(subscription);
        boolean isPayLoadRecurrenceNumberHighest = payloadNumberRecurrences > highestSubscriptionRecurrenceNumber;

        if (isPayLoadRecurrenceNumberHighest) {
            List<ShopifySyncOperation> operations = createShopifySyncOperationsForExpiredOrders(subscription);
            shopifySyncOperationRepository.saveAll(operations);
            deleteSubscriptionAndOrders(subscription);
            return;
        }

        if (payloadNumberRecurrences == highestSubscriptionRecurrenceNumber) {

            List<Order> ordersPaid = subscription.getRecurrences().stream()
                    .filter(r -> r.getNumberRecurrence() == payloadNumberRecurrences).collect(Collectors.toList());

            List<ShopifySyncOperation> operationsPaid = createShopifySyncOperationsForSubscriptionPaid(ordersPaid,
                    subscription.getId());

            List<Order> ordersExpired = subscription.getRecurrences().stream()
                    .filter(r -> r.getNumberRecurrence() < payloadNumberRecurrences).collect(Collectors.toList());

            List<ShopifySyncOperation> operationsExpired = createShopifySyncOperationsForExpiredOrders(ordersExpired,
                    subscription.getId());

            List<ShopifySyncOperation> allOperations = new ArrayList<>();

            allOperations.addAll(operationsPaid);
            allOperations.addAll(operationsExpired);

            shopifySyncOperationRepository.saveAll(allOperations);
            deleteSubscriptionAndOrders(subscription);

            return;
        }

        boolean existOrderWithPayloadNumberRecurrence = subscription.getRecurrences().stream().anyMatch(r -> r.getNumberRecurrence() == payloadNumberRecurrences);
        if (!existOrderWithPayloadNumberRecurrence) {
            return;
        }

        List<Order> ordersPaid = subscription.getRecurrences().stream().filter(r -> r.getNumberRecurrence() == payloadNumberRecurrences).collect(Collectors.toList());

        List<ShopifySyncOperation> operations = createShopifySyncOperationsForSubscriptionPaid(ordersPaid, subscription.getId());
        shopifySyncOperationRepository.saveAll(operations);

        Optional<Order> orderToPreserve = subscription.getRecurrences().stream().filter(r -> r.getNumberRecurrence() != payloadNumberRecurrences).findAny();

        if (orderToPreserve.isPresent()){
            deleteOrdersFromSubscription(subscription, ordersPaid.get(0));
            return;
        }

        deleteSubscriptionAndOrders(subscription);

    }

    private void processCancelOrPausedSubscriptionPayload(Integer payloadStatus, Subscription subscription) {

        List<ShopifySyncOperation> operations = createShopifySyncOperationsForExpiredOrders(subscription);
        shopifySyncOperationRepository.saveAll(operations);
        deleteSubscriptionAndOrders(subscription);

    }

    private int findHighestSubscriptionRecurrenceNumber(Subscription subscription) {
        if (subscription.getRecurrences().size() == 1) {
            return subscription.getRecurrences().get(0).getNumberRecurrence();
        }

        int o1 = subscription.getRecurrences().get(0).getNumberRecurrence();
        int o2 = subscription.getRecurrences().get(1).getNumberRecurrence();

        int biggest = Integer.compare(o1, o2);

        return biggest < 0 ? o2 : o1;

    }

    private void deleteSubscriptionAndOrders(Subscription subscription) {
        subscription.getRecurrences().forEach(r -> {
            productRepository.deleteAll(r.getProducts());
        });
        orderRepository.deleteAll(subscription.getRecurrences());
        subscriptionRepository.delete(subscription);
    }

    private void deleteOrdersFromSubscription(Subscription subscription, Integer payloadNumberRecurrences) {
        subscription.getRecurrences().forEach(r -> {
            if (r.getNumberRecurrence() < payloadNumberRecurrences) {
                productRepository.deleteAll(r.getProducts());
                orderRepository.delete(r);
            }
        });
    }

    private void deleteOrdersFromSubscription(Subscription subscription, Order order) {

                productRepository.deleteAll(order.getProducts());
                orderRepository.delete(order);

    }


    private List<ShopifySyncOperation> createShopifySyncOperationsForSubscriptionPaid(List<Order> ordersPaid,
            String subscriptionId) {

        List<ShopifySyncOperation> operations = new ArrayList<>();

        ordersPaid.forEach(r -> {

            for (Product item : r.getProducts()) {
                ShopifySyncOperation op = new ShopifySyncOperation();
                op.setSubscriptionId(subscriptionId);
                op.setOperation("delete");
                op.setSku(item.getControlledSku().getSku());
                op.setQuantity(item.getQuantity());
                op.setStatus("PENDING");
                operations.add(op);
            }

        });

        return operations;

    }

    private List<ShopifySyncOperation> createShopifySyncOperationsForExpiredOrders(Subscription subscription) {
        List<ShopifySyncOperation> operations = new ArrayList<>();

        subscription.getRecurrences().forEach(r -> {

            for (Product item : r.getProducts()) {
                ShopifySyncOperation op = new ShopifySyncOperation();
                op.setSubscriptionId(subscription.getId());
                op.setOperation("delete");
                op.setSku(item.getControlledSku().getSku());
                op.setQuantity(item.getQuantity());
                op.setStatus("PENDING");
                operations.add(op);
            }

        });

        return operations;
    }

    private List<ShopifySyncOperation> createShopifySyncOperationsForExpiredOrders(List<Order> orders,
            String subscriptionId) {
        List<ShopifySyncOperation> operations = new ArrayList<>();

        orders.forEach(r -> {

            for (Product item : r.getProducts()) {
                ShopifySyncOperation op = new ShopifySyncOperation();
                op.setSubscriptionId(subscriptionId);
                op.setOperation("delete");
                op.setSku(item.getControlledSku().getSku());
                op.setQuantity(item.getQuantity());
                op.setStatus("PENDING");
                operations.add(op);
            }

        });

        return operations;
    }

    private void handleJobFailure(WebhookJob job, Exception e, Instant now) {

        logger.error("Job falhado pelo motivo: {}", e.getMessage());

        int newAttempts = job.getAttempts() + 1;

        if (newAttempts >= maxAttempts) {
            // Máximo de tentativas atingido - marca como DEAD
            webhookJobService.updateJobStatus(job.getId(), JobStatus.DEAD);
            System.err.println("Job " + job.getId() + " marcado como DEAD após " + newAttempts + " tentativas");
        } else {
            // Agenda próxima tentativa com backoff exponencial
            Instant nextRun = calculateNextRunTime(newAttempts);
            webhookJobService.incrementAttemptsAndScheduleNext(job.getId(), nextRun);
            webhookJobService.updateJobStatus(job.getId(), JobStatus.PENDING);

            System.out.println("Job " + job.getId() + " agendado para nova tentativa em " + nextRun +
                    " (tentativa " + newAttempts + "/" + maxAttempts + ")");
        }
    }

    /**
     * Calcula próxima execução com backoff exponencial
     */
    private Instant calculateNextRunTime(int attempts) {
        // Backoff exponencial: 1min, 2min, 4min, 8min, 16min
        long delayMinutes = (long) Math.pow(2, attempts - 1);
        return Instant.now().plus(delayMinutes, ChronoUnit.MINUTES);
    }

}
