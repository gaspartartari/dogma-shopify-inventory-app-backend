package com.tartaritech.inventory_sync.services;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tartaritech.inventory_sync.dtos.OrderDTO;
import com.tartaritech.inventory_sync.dtos.ProductDTO;
import com.tartaritech.inventory_sync.dtos.SubscriptionDTO;
import com.tartaritech.inventory_sync.dtos.SubscriptionShortDTO;
import com.tartaritech.inventory_sync.dtos.SubscriptionsDTO;
import com.tartaritech.inventory_sync.entities.ControlledSKu;
import com.tartaritech.inventory_sync.entities.Customer;
import com.tartaritech.inventory_sync.entities.Order;
import com.tartaritech.inventory_sync.entities.Product;
import com.tartaritech.inventory_sync.entities.ShopifySyncOperation;
import com.tartaritech.inventory_sync.entities.Subscription;
import com.tartaritech.inventory_sync.repositories.ControlledSkuRepository;
import com.tartaritech.inventory_sync.repositories.CustomerRepository;
import com.tartaritech.inventory_sync.repositories.OrderRepository;
import com.tartaritech.inventory_sync.repositories.ProductRepository;
import com.tartaritech.inventory_sync.repositories.ShopifySyncOperationRepository;
import com.tartaritech.inventory_sync.repositories.SubscriptionRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class SubscriptionService {

    private final ControlledSkuRepository controlledSkuRepository;

    private final ProductRepository productRepository;

    private final SubscriptionRepository subscriptionRepository;

    private final ShopifySyncOperationRepository shopifySyncOperationRepository;

    private final PagBrasilService pagBrasilService;

    private final ShopifyInventoryService shopifyInventoryService;

    private final CustomerRepository customerRepository;

    private final OrderRepository orderRepository;

    private final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

    @Value("${pagbrasil.api.delay:500}")
    private int apiCallDelay;

    @Value("${revenue.cache.parallel.batch.size:20}")
    private int parallelBatchSize;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
            ProductRepository productRepository,
            ControlledSkuRepository controlledSkuRepository,
            ShopifySyncOperationRepository shopifySyncOperationRepository,
            PagBrasilService pagBrasilService,
            ShopifyInventoryService shopifyInventoryService,
            CustomerRepository customerRepository,
            OrderRepository orderRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.productRepository = productRepository;
        this.controlledSkuRepository = controlledSkuRepository;
        this.shopifySyncOperationRepository = shopifySyncOperationRepository;
        this.pagBrasilService = pagBrasilService;
        this.shopifyInventoryService = shopifyInventoryService;
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
    }

    @Scheduled(fixedDelay = 120000)
    @Transactional
    public void checkForSubscriptionsWithControlledSku() {

        // Busca assinaturas com status ativo
        SubscriptionsDTO activeSubscriptionsDTO = pagBrasilService.fetchSubscriptionsByStatus("1");
        logger.info("Active Subs encontradas: {}",
                activeSubscriptionsDTO.getSubscriptions().size());

        SubscriptionsDTO pendingSubscriptionsDTO = pagBrasilService.fetchSubscriptionsByStatus("2");

        SubscriptionsDTO activeAndPendingSubscriptionsDTO = new SubscriptionsDTO();
        activeAndPendingSubscriptionsDTO.getSubscriptions().addAll(activeSubscriptionsDTO.getSubscriptions());
        activeAndPendingSubscriptionsDTO.getSubscriptions().addAll(pendingSubscriptionsDTO.getSubscriptions());

        // test - retomar acima em prod
        // SubscriptionsDTO activeAndPendingSubscriptionsDTO = new SubscriptionsDTO();
        // activeAndPendingSubscriptionsDTO.getSubscriptions().add(new
        // SubscriptionShortDTO("P175952281691"));
        // activeAndPendingSubscriptionsDTO.getSubscriptions().add(new
        // SubscriptionShortDTO("P175987505193"));
        // activeAndPendingSubscriptionsDTO.getSubscriptions().add(new
        // SubscriptionShortDTO("P176071568239"));

        // Busca assinaturas com SKUs controlados
        List<SubscriptionDTO> subscriptionsWithControlledSkus = findSubscriptionsWithControlledSkus(
                activeAndPendingSubscriptionsDTO.getSubscriptions());

        // Processa assinaturas com skus controlados
        for (SubscriptionDTO dto : subscriptionsWithControlledSkus) {
            processSubscription(dto);
        }

        // Verificar assinaturas existentes no DB interno que removeram todos os itens
        // controlados
        List<SubscriptionDTO> allSubscriptionsWithoutSku = finAllSubscriptionsWithoutControlledSku(
                activeAndPendingSubscriptionsDTO.getSubscriptions());

        for (SubscriptionDTO dto : allSubscriptionsWithoutSku) {
            if (subscriptionRepository.existsById(dto.getId())) {
                Subscription sub = subscriptionRepository.findById(dto.getId()).get();

                // Create Shopify sync operations for deletion

                List<ShopifySyncOperation> operations = createShopifySyncOperationsForDeletion(sub);
                shopifySyncOperationRepository.saveAll(operations);

                sub.getRecurrences().forEach(r -> {
                    productRepository.deleteAll(r.getProducts());
                });

                orderRepository.deleteAll(sub.getRecurrences());
                subscriptionRepository.delete(sub);
            }
        }

    }

    private void processSubscription(SubscriptionDTO dto) {

        // Verificar se dto é null
        if (dto == null) {
            logger.warn("SubscriptionDTO é null, pulando processamento");
            return;
        }

        // Verifica se existe no banco de dados
        Optional<Subscription> subscription = subscriptionRepository.findById(dto.getId());

        if (!subscription.isPresent()) {
            subscription = Optional.of(createNewSubscription(dto));
            // Create Shopify sync operations for new subscription
            List<ShopifySyncOperation> operations = createShopifySyncOperationsForNewSubscription(subscription.get());
            shopifySyncOperationRepository.saveAll(operations);
            return;

        }

        Subscription entity = subscription.get();
        List<ShopifySyncOperation> operations = new ArrayList<>();

        Set<Integer> existingNumberRecurrences = subscription.get().getRecurrences().stream()
                .map(r -> r.getNumberRecurrence()).collect(Collectors.toSet());
        Set<Integer> incomingNumberRecurrences = dto.getOrders().stream().map(r -> r.getNumberRecurrence())
                .collect(Collectors.toSet());

        Set<Integer> newRecurrences = incomingNumberRecurrences.stream()
                .filter(r -> !existingNumberRecurrences.contains(r)).collect(Collectors.toSet());

        if (!newRecurrences.isEmpty()) {
            newRecurrences.forEach(nr -> {
                Optional<OrderDTO> newOrderDTO = dto.getOrders().stream().filter(o -> o.getNumberRecurrence() == nr)
                        .findAny();
                if (newOrderDTO.isPresent()) {
                    createOrdersWithControlledItems(entity, newOrderDTO.get());

                }

            });
        }

        entity.getRecurrences().forEach(r -> {
            List<ProductDTO> updatedItems = dto.getOrders().stream()
                    .filter(o -> o.getNumberRecurrence() == r.getNumberRecurrence())
                    .flatMap(o -> o.getProducts().stream()).collect(Collectors.toList());

            operations.addAll(calculateDelta(r.getProducts(), updatedItems, entity));
            updateNextItems(updatedItems, r.getProducts());

        });

        Subscription updatedEntity = mapDtoToEntity(dto, entity);

        subscriptionRepository.save(updatedEntity);

        // Save Shopify sync operations
        shopifySyncOperationRepository.saveAll(operations);

    }

    private void createOrdersWithControlledItems(Subscription entity, OrderDTO dto) {

        List<Product> controlledSkus = new ArrayList<>();

        dto.getProducts().forEach(p -> {
            boolean isControlledSku = controlledSkuRepository.existsById(p.getSku());

            if (isControlledSku) {
                ControlledSKu sku = controlledSkuRepository.findById(p.getSku()).get();
                java.math.BigDecimal unitPrice = p.getUnitPrice() != null ? new java.math.BigDecimal(p.getUnitPrice())
                        : null;
                java.math.BigDecimal totalPrice = p.getTotalPrice() != null
                        ? new java.math.BigDecimal(p.getTotalPrice())
                        : null;
                Product newItem = Product.createProduct(sku, p.getQuantity(), unitPrice, totalPrice);
                newItem = productRepository.save(newItem);
                controlledSkus.add(newItem);
            }

        });

        if (controlledSkus.isEmpty()) {
            return;
        }

        Order newOrder = new Order();
        newOrder.getProducts().addAll(controlledSkus);
        newOrder.setNumberRecurrence(dto.getNumberRecurrence());
        newOrder.setOrderRec(dto.getOrder() != null ? dto.getOrder() : null);
        newOrder.setPaymentDate(dto.getPaymentDate() != null ? dto.getPaymentDate() : null);
        newOrder.setSubscription(entity);

        newOrder.getProducts().forEach(p -> {
            p.setOrder(newOrder);
        });

        orderRepository.save(newOrder);
        entity.getRecurrences().add(newOrder);

    }

    private void updateNextItems(List<ProductDTO> updatedItems, List<Product> currentItems) {

        // remove inexistentes
        Set<String> dtoSkus = updatedItems.stream()
                .map(ProductDTO::getSku)
                .collect(Collectors.toSet());

        Set<Product> itemsToDelete = currentItems.stream()
                .filter(i -> !dtoSkus.contains(i.getControlledSku().getSku())).collect(Collectors.toSet());
        currentItems.removeIf(i -> !dtoSkus.contains(i.getControlledSku().getSku()));

        productRepository.deleteAll(itemsToDelete);

        // atualiza quantidade e preços de itens existentes
        currentItems.forEach(currentItem -> {
            String currentSku = currentItem.getControlledSku().getSku();
            updatedItems.stream()
                    .filter(dtoItem -> dtoItem.getSku().equals(currentSku))
                    .findFirst()
                    .ifPresent(dtoItem -> {
                        currentItem.setQuantity(dtoItem.getQuantity());
                        if (dtoItem.getUnitPrice() != null) {
                            currentItem.setUnitPrice(new java.math.BigDecimal(dtoItem.getUnitPrice()));
                        }
                        if (dtoItem.getTotalPrice() != null) {
                            currentItem.setTotalPrice(new java.math.BigDecimal(dtoItem.getTotalPrice()));
                        }
                    });
        });

        // adiciona novos itens se for sku controlado
        updatedItems.stream()
                .filter(dtoItem -> !currentItems.stream()
                        .anyMatch(
                                entityItem -> entityItem.getControlledSku().getSku().equals(dtoItem.getSku())))
                .forEach(dtoItem -> {
                    if (checkIfControlledSKu(dtoItem)) {
                        ControlledSKu sku = controlledSkuRepository.findById(dtoItem.getSku()).get();
                        java.math.BigDecimal unitPrice = dtoItem.getUnitPrice() != null
                                ? new java.math.BigDecimal(dtoItem.getUnitPrice())
                                : null;
                        java.math.BigDecimal totalPrice = dtoItem.getTotalPrice() != null
                                ? new java.math.BigDecimal(dtoItem.getTotalPrice())
                                : null;
                        Product newItem = Product.createProduct(sku, dtoItem.getQuantity(), unitPrice, totalPrice);
                        newItem = productRepository.save(newItem);
                        currentItems.add(newItem);
                    }
                });

        productRepository.saveAll(currentItems);
    }

    private List<ShopifySyncOperation> calculateDelta(List<Product> currentItems, List<ProductDTO> newItems,
            Subscription subscription) {
        List<ShopifySyncOperation> operations = new ArrayList<>();
        Map<String, Integer> deltaMap = new HashMap<>();
        Map<String, Integer> skusToDelete = new HashMap<>();
        Map<String, Integer> skusToInsert = new HashMap<>();

        // Verify delta for matching items and skus to delete
        for (Product i : currentItems) {
            String currentSku = i.getControlledSku().getSku();
            Optional<ProductDTO> matchingItem = newItems.stream()
                    .filter(dto -> dto.getSku().equals(currentSku))
                    .findFirst();

            if (matchingItem.isPresent()) {
                int currentQuantity = i.getQuantity();
                int newQuantity = matchingItem.get().getQuantity();
                int delta = newQuantity - currentQuantity;

                if (delta != 0) {
                    deltaMap.put(i.getControlledSku().getSku(), delta);
                }
            } else {
                skusToDelete.put(currentSku, i.getQuantity());
            }
        }

        // Verify if there are any new controlled skus to insert
        for (ProductDTO i : newItems) {
            boolean existsInCurrent = currentItems.stream()
                    .anyMatch(item -> item.getControlledSku().getSku().equals(i.getSku()));

            if (!existsInCurrent) {
                boolean controlled = checkIfControlledSKu(i);
                if (controlled) {
                    skusToInsert.put(i.getSku(), i.getQuantity());
                }
            }
        }

        logger.info("Delta encontrado para {}", deltaMap.toString());
        logger.info("Skus a deletar {}", skusToDelete.toString());
        logger.info("skusToInsert {}", skusToInsert.toString());

        // Create ShopifySyncOperation objects instead of calling processDelta
        operations.addAll(createShopifySyncOperationsFromMap(deltaMap, "delta", subscription.getId()));
        operations.addAll(createShopifySyncOperationsFromMap(skusToInsert, "insert", subscription.getId()));
        operations.addAll(createShopifySyncOperationsFromMap(skusToDelete, "delete", subscription.getId()));

        return operations;
    }

    private List<ShopifySyncOperation> createShopifySyncOperationsFromMap(Map<String, Integer> skuMap, String operation,
            String subscriptionId) {
        List<ShopifySyncOperation> operations = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : skuMap.entrySet()) {
            ShopifySyncOperation op = new ShopifySyncOperation();
            op.setSubscriptionId(subscriptionId);
            op.setOperation(operation);
            op.setSku(entry.getKey());
            op.setQuantity(entry.getValue());
            op.setStatus("PENDING");
            operations.add(op);
        }

        return operations;
    }

    private List<ShopifySyncOperation> createShopifySyncOperationsForNewSubscription(Subscription subscription) {
        List<ShopifySyncOperation> operations = new ArrayList<>();

        subscription.getRecurrences().forEach(r -> {
            for (Product item : r.getProducts()) {
                ShopifySyncOperation op = new ShopifySyncOperation();
                op.setSubscriptionId(subscription.getId());
                op.setOperation("insert");
                op.setSku(item.getControlledSku().getSku());
                op.setQuantity(item.getQuantity());
                op.setStatus("PENDING");
                operations.add(op);
            }
        });

        return operations;
    }

    private List<ShopifySyncOperation> createShopifySyncOperationsForDeletion(Subscription subscription) {
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

    private boolean checkIfControlledSKu(ProductDTO itemDto) {

        boolean found = controlledSkuRepository.existsById(itemDto.getSku());
        if (found) {
            return true;
        }

        return false;

    }

    private Subscription createNewSubscription(SubscriptionDTO dto) {

        Subscription newSubscription = new Subscription();

        newSubscription = mapDtoToEntity(dto, newSubscription);

        newSubscription = subscriptionRepository.save(newSubscription);

        createOrdersWithControlledItems(newSubscription, dto);

        Customer customer = getExistingCustomerOrCreateNew(dto);

        customer.getSubscriptions().add(newSubscription);

        newSubscription.setCustomer(customer);

        logger.info("Novo subscription criado {}", newSubscription.toString());

        return newSubscription;

    }

    private Customer getExistingCustomerOrCreateNew(SubscriptionDTO dto) {

        Customer customer = new Customer();

        if (!customerRepository.existsById(dto.getCustomerEmail())) {
            customer = extractCustomerFromDto(dto, customer);
            customer = customerRepository.save(customer);
        } else {
            customer = customerRepository.findById(dto.getCustomerEmail()).get();
        }

        return customer;
    }

    private void createOrdersWithControlledItems(Subscription entity, SubscriptionDTO dto) {

        dto.getOrders().forEach(o -> {

            List<Product> controlledSkus = new ArrayList<>();

            o.getProducts().forEach(p -> {
                boolean isControlledSku = controlledSkuRepository.existsById(p.getSku());

                if (isControlledSku) {
                    ControlledSKu sku = controlledSkuRepository.findById(p.getSku()).get();
                    java.math.BigDecimal unitPrice = p.getUnitPrice() != null
                            ? new java.math.BigDecimal(p.getUnitPrice())
                            : null;
                    java.math.BigDecimal totalPrice = p.getTotalPrice() != null
                            ? new java.math.BigDecimal(p.getTotalPrice())
                            : null;
                    Product newItem = Product.createProduct(sku, p.getQuantity(), unitPrice, totalPrice);
                    newItem = productRepository.save(newItem);
                    controlledSkus.add(newItem);
                }

            });

            if (controlledSkus.isEmpty()) {
                return;
            }

            Order newOrder = new Order();
            newOrder.getProducts().addAll(controlledSkus);
            newOrder.setNumberRecurrence(o.getNumberRecurrence());
            newOrder.setOrderRec(o.getOrder() != null ? o.getOrder() : null);
            newOrder.setPaymentDate(o.getPaymentDate() != null ? o.getPaymentDate() : null);
            newOrder.setSubscription(entity);

            newOrder.getProducts().forEach(p -> {
                p.setOrder(newOrder);
            });

            orderRepository.save(newOrder);
            entity.getRecurrences().add(newOrder);
        });

    }

    private Customer extractCustomerFromDto(SubscriptionDTO dto, Customer customer) {
        customer.setEmail(dto.getCustomerEmail() != null ? dto.getCustomerEmail() : null);
        customer.setName(dto.getCustomerName() != null ? dto.getCustomerName() : null);
        customer.setPhone(dto.getCustomerPhone() != null ? dto.getCustomerPhone() : null);
        return customer;

    }

    private Subscription mapDtoToEntity(SubscriptionDTO dto, Subscription entity) {

        entity.setId(dto.getId());
        entity.setStatus(Subscription.convertStatusToEnum(dto.getStatus()));
        entity.setNumberRecurrences(dto.getNumberRecurrences());
        entity.setNextRecurrence(dto.getNumberRecurrences() + 1);
        entity.setNextBillingDate(dto.getNextBillingDate());
        entity.setCancellationDate(dto.getCancellationDate());

        return entity;
    }

    private List<SubscriptionDTO> findSubscriptionsWithControlledSkus(List<SubscriptionShortDTO> idList) {

        return Flux.fromIterable(idList)
                .flatMap(shortDto -> Mono.fromCallable(() -> {
                    try {
                        SubscriptionDTO dto = pagBrasilService.fetchSubscriptionById(shortDto);
                        if (dto == null)
                            return null;

                        boolean hasControlledSku = checkForControlledSkus(dto.getOrders());

                        if (!hasControlledSku) return null;

                        dto.processOrders();
                        return dto;
                    } catch (Exception e) {
                        logger.warn("Failed to fetch subscription {}: {}",
                                shortDto.getSubscription(), e.getMessage());
                        return null;
                    }
                })
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    logger.warn("Timeout or error fetching subscription {}",
                            shortDto.getSubscription());
                    return Mono.empty();
                }),
                parallelBatchSize // Concurrency limit
                )
                .filter(dto -> dto != null)
                .collectList()
                .block();

    }

    private List<SubscriptionDTO> finAllSubscriptionsWithoutControlledSku(List<SubscriptionShortDTO> idList) {
        return Flux.fromIterable(idList)
                .flatMap(shortDto -> Mono.fromCallable(() -> {
                    try {
                        SubscriptionDTO dto = pagBrasilService.fetchSubscriptionById(shortDto);
                        if (dto == null)
                            return null;

                        boolean hasControlledSku = checkForControlledSkus(dto.getOrders());

                        if (hasControlledSku)
                            return null;

                        return dto;
                    } catch (Exception e) {
                        logger.warn("Failed to fetch subscription {}: {}",
                                shortDto.getSubscription(), e.getMessage());
                        return null;
                    }
                })
                        .timeout(Duration.ofSeconds(30))
                        .onErrorResume(e -> {
                            logger.warn("Timeout or error fetching subscription {}",
                                    shortDto.getSubscription());
                            return Mono.empty();
                        }),
                        parallelBatchSize // Concurrency limit
                )
                .filter(dto -> dto != null)
                .collectList()
                .block();
    }

    private boolean checkForControlledSkus(Set<OrderDTO> orders) {
        boolean exists = false;

        if (orders.isEmpty())
            return false;

        for (OrderDTO o : orders) {
            exists = o.getProducts().stream().anyMatch(p -> controlledSkuRepository.existsById(p.getSku()));
            if (exists)
                return true;
        }

        return false;

    }

}
