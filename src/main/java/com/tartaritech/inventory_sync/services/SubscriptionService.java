package com.tartaritech.inventory_sync.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tartaritech.inventory_sync.dtos.ProductDTO;
import com.tartaritech.inventory_sync.dtos.RecurrenceDTO;
import com.tartaritech.inventory_sync.dtos.SubscriptionFullDTO;
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

@Service
public class SubscriptionService {

    private final ControlledSkuRepository controlledSkuRepository;

    private final ProductRepository productRepository;

    private final SubscriptionRepository subscriptionRepository;

    private final ShopifySyncOperationRepository shopifySyncOperationRepository;

    private final PagBrasilService pagBrasilService;

    private final CustomerRepository customerRepository;

    private final OrderRepository orderRepository;

    private final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

    @Value("${pagbrasil.request.delay:2000}")
    private int requestDelayMs;

    @Value("${pagbrasil.status.delay:2000}")
    private int statusDelayMs;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
            ProductRepository productRepository,
            ControlledSkuRepository controlledSkuRepository,
            ShopifySyncOperationRepository shopifySyncOperationRepository,
            PagBrasilService pagBrasilService,
            CustomerRepository customerRepository,
            OrderRepository orderRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.productRepository = productRepository;
        this.controlledSkuRepository = controlledSkuRepository;
        this.shopifySyncOperationRepository = shopifySyncOperationRepository;
        this.pagBrasilService = pagBrasilService;
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
    }

    @Scheduled(fixedDelay = 500000) // ~8.3 minutes
    @Transactional
    public void checkForSubscriptionsWithControlledSku() {

        if (!pagBrasilService.tryAcquireApiLock()) {
            logger.warn("PagBrasil API lock not available (another service is using it). Skipping this cycle.");
            return;
        }

        try {
            executeSubscriptionCheck();
        } finally {
            pagBrasilService.releaseApiLock();
        }
    }

    private void executeSubscriptionCheck() {

        // // Busca assinaturas com status ativo
        SubscriptionsDTO activeSubscriptionsDTO = pagBrasilService.fetchSubscriptionsByStatus("1");
        logger.info("Active Subs encontradas: {}",
                activeSubscriptionsDTO.getSubscriptions().size());

        // Add delay between status fetches to avoid rate limiting (with jitter)
        try {
            Thread.sleep((long) (statusDelayMs * (0.5 + Math.random() * 0.5)));
        } catch (InterruptedException e) {
            logger.warn("Delay interrupted between status fetches");
            Thread.currentThread().interrupt();
        }

        SubscriptionsDTO pendingSubscriptionsDTO = pagBrasilService.fetchSubscriptionsByStatus("2");

        // Add delay between status fetches to avoid rate limiting (with jitter)
        try {
            Thread.sleep((long) (statusDelayMs * (0.5 + Math.random() * 0.5)));
        } catch (InterruptedException e) {
            logger.warn("Delay interrupted between status fetches");
            Thread.currentThread().interrupt();
        }

        // Busca assinaturas com status cancelado
        SubscriptionsDTO canceledSubscriptionsDTO = pagBrasilService.fetchSubscriptionsByStatus("3");
        logger.info("Canceled Subs encontradas: {}",
                canceledSubscriptionsDTO.getSubscriptions().size());

        SubscriptionsDTO activeAndPendingSubscriptionsDTO = new SubscriptionsDTO();
        activeAndPendingSubscriptionsDTO.getSubscriptions().addAll(activeSubscriptionsDTO.getSubscriptions());
        activeAndPendingSubscriptionsDTO.getSubscriptions().addAll(pendingSubscriptionsDTO.getSubscriptions());

        // TEST
        // SubscriptionsDTO activeAndPendingSubscriptionsDTO = new SubscriptionsDTO();
        // activeAndPendingSubscriptionsDTO.getSubscriptions().add(new
        // SubscriptionShortDTO("P177055173991"));

        // SubscriptionsDTO canceledSubscriptionsDTO = new SubscriptionsDTO();
        // canceledSubscriptionsDTO.getSubscriptions().add(new
        // SubscriptionShortDTO("P175510778868"));

        // Para assinaturas canceladas (status 3), se existir no nosso DB interno:
        // - criar operações de delete no Shopify para todos itens controlados
        // - remover subscription/recurrences/products do DB interno
        for (SubscriptionShortDTO canceled : canceledSubscriptionsDTO.getSubscriptions()) {
            if (canceled == null || canceled.getSubscription() == null) {
                continue;
            }
            String subscriptionId = canceled.getSubscription();
            if (!subscriptionRepository.existsById(subscriptionId)) {
                continue;
            }

            Subscription sub = subscriptionRepository.findById(subscriptionId).get();
            List<ShopifySyncOperation> operations = createShopifySyncOperationsForDeletion(sub);
            shopifySyncOperationRepository.saveAll(operations);

            sub.getRecurrences().forEach(r -> productRepository.deleteAll(r.getProducts()));
            orderRepository.deleteAll(sub.getRecurrences());
            subscriptionRepository.delete(sub);
            logger.info("Subscription {} cancelada: operações de delete criadas e entidade removida do DB interno",
                    subscriptionId);
        }

        // test - retomar acima em prod
        // SubscriptionsDTO activeAndPendingSubscriptionsDTO = new SubscriptionsDTO();
        // activeAndPendingSubscriptionsDTO.getSubscriptions().add(new
        // SubscriptionShortDTO("P175952281691"));
        // activeAndPendingSubscriptionsDTO.getSubscriptions().add(new
        // SubscriptionShortDTO("P175987505193"));
        // activeAndPendingSubscriptionsDTO.getSubscriptions().add(new
        // SubscriptionShortDTO("P176071568239"));

        // Add delay before starting individual subscription fetches to avoid rate limiting (with jitter)
        try {
            Thread.sleep((long) (statusDelayMs * (0.5 + Math.random() * 0.5)));
        } catch (InterruptedException e) {
            logger.warn("Delay interrupted before individual fetches");
            Thread.currentThread().interrupt();
            return;
        }

        // Fetch all subscriptions by ID once and partition by controlled SKUs
        List<SubscriptionFullDTO> allFetchedSubscriptions = fetchAllSubscriptionsById(
                activeAndPendingSubscriptionsDTO.getSubscriptions());

        // Partition subscriptions into those with and without controlled SKUs
        Map<Boolean, List<SubscriptionFullDTO>> partitioned = allFetchedSubscriptions.stream()
                .collect(Collectors.partitioningBy(dto -> checkForControlledSkus(dto.getRecurrences())));

        List<SubscriptionFullDTO> subscriptionsWithControlledSkus = partitioned.get(true);
        List<SubscriptionFullDTO> subscriptionsWithoutControlledSkus = partitioned.get(false);

        // Process assinaturas com skus controlados
        if (subscriptionsWithControlledSkus != null) {
            for (SubscriptionFullDTO dto : subscriptionsWithControlledSkus) {
                processSubscription(dto);
            }
        }

        // Verificar assinaturas existentes no DB interno que removeram todos os itens
        // controlados
        if (subscriptionsWithoutControlledSkus != null) {
            for (SubscriptionFullDTO dto : subscriptionsWithoutControlledSkus) {
                if (subscriptionRepository.existsById(dto.getSubscription())) {
                    Subscription sub = subscriptionRepository.findById(dto.getSubscription()).get();

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

    }

    private void processSubscription(SubscriptionFullDTO dto) {

        // Verificar se dto é null
        if (dto == null) {
            logger.warn("SubscriptionFullDTO é null, pulando processamento");
            return;
        }

        // Verifica se existe no banco de dados
        Optional<Subscription> subscription = subscriptionRepository.findById(dto.getSubscription());

        if (!subscription.isPresent()) {
            subscription = Optional.of(createNewSubscription(dto));
            // Create Shopify sync operations for new subscription
            List<ShopifySyncOperation> operations = createShopifySyncOperationsForNewSubscription(subscription.get());
            shopifySyncOperationRepository.saveAll(operations);
            return;

        }

        Subscription entity = subscription.get();
        List<ShopifySyncOperation> operations = new ArrayList<>();

        if (dto.getRecurrences() == null || dto.getRecurrences().isEmpty()) {
            logger.warn("Subscription {} has no recurrences, skipping", dto.getSubscription());
            return;
        }

        // Identify the last 2 recurrence numbers (by numberRecurrence descending)
        Set<Integer> lastTwoRecurrenceNumbers = getLastTwoRecurrenceNumbers(dto.getRecurrences());
        logger.debug("Last 2 recurrence numbers for subscription {}: {}", dto.getSubscription(), lastTwoRecurrenceNumbers);

        // Handle new recurrences that appear
        Set<Integer> existingNumberRecurrences = entity.getRecurrences().stream()
                .map(r -> r.getNumberRecurrence())
                .filter(nr -> nr != null)
                .collect(Collectors.toSet());
        Set<Integer> incomingNumberRecurrences = dto.getRecurrences().stream()
                .filter(r -> r.getNumberRecurrence() != null)
                .map(r -> r.getNumberRecurrence())
                .collect(Collectors.toSet());

        Set<Integer> newRecurrences = incomingNumberRecurrences.stream()
                .filter(r -> !existingNumberRecurrences.contains(r))
                .collect(Collectors.toSet());

        // Create new orders and immediately process them for stock operations
        if (!newRecurrences.isEmpty()) {
            for (Integer nr : newRecurrences) {
                Optional<RecurrenceDTO> newRecurrenceDTO = dto.getRecurrences().stream()
                        .filter(o -> o.getNumberRecurrence() != null && o.getNumberRecurrence().equals(nr))
                        .findAny();
                if (newRecurrenceDTO.isPresent()) {
                    createOrdersWithControlledItems(entity, newRecurrenceDTO.get());
                    // The newly created order will be processed in the loop below
                }
            }
        }

        // Process each order (including newly created ones) with state-aware reconciliation
        for (Order order : entity.getRecurrences()) {
            if (order.getNumberRecurrence() == null) {
                continue;
            }

            // Find matching DTO recurrence
            Optional<RecurrenceDTO> recurrenceDTO = dto.getRecurrences().stream()
                    .filter(o -> o.getNumberRecurrence() != null
                            && o.getNumberRecurrence().equals(order.getNumberRecurrence()))
                    .findFirst();

            if (!recurrenceDTO.isPresent()) {
                // Recurrence no longer exists in PagBrasil - treat as expired
                logger.debug("Recurrence {} no longer in PagBrasil, treating as expired", order.getNumberRecurrence());
                if (!order.getStockReleased() && !order.getProducts().isEmpty()) {
                    // Release reserved stock
                    operations.addAll(createShopifySyncOperationsForOrderDeletion(order, entity.getId()));
                    order.setStockReleased(true);
                }
                continue;
            }

            RecurrenceDTO dtoRec = recurrenceDTO.get();
            
            // Update order fields from DTO
            mapRecurrenceDtoToEntity(dtoRec, order);

            // Determine if this order SHOULD have reserved stock
            boolean isInLastTwo = lastTwoRecurrenceNumbers.contains(order.getNumberRecurrence());
            boolean isPaid = dtoRec.getPaymentDate() != null && !dtoRec.getPaymentDate().isEmpty();
            boolean shouldHaveReservedStock = isInLastTwo && !isPaid;

            // Determine if this order CURRENTLY has reserved stock
            boolean currentlyHasReservedStock = !order.getStockReleased() && !order.getProducts().isEmpty();

            logger.debug("Order {}: isInLastTwo={}, isPaid={}, shouldHaveReserved={}, currentlyHasReserved={}",
                    order.getNumberRecurrence(), isInLastTwo, isPaid, shouldHaveReservedStock, currentlyHasReservedStock);

            // Reconcile desired vs current state
            if (shouldHaveReservedStock) {
                // Should have reserved stock
                if (currentlyHasReservedStock) {
                    // Already reserved - check for quantity changes
                    List<ProductDTO> updatedItems = dtoRec.getProducts() != null
                            ? dtoRec.getProducts()
                            : new ArrayList<>();
                    operations.addAll(calculateDelta(order.getProducts(), updatedItems, entity));
                    updateNextItems(updatedItems, order.getProducts());
                } else {
                    // Not reserved yet - create insert operations
                    logger.info("Creating insert operations for order {} (newly should have reserved stock)",
                            order.getNumberRecurrence());
                    for (Product product : order.getProducts()) {
                        ShopifySyncOperation op = new ShopifySyncOperation();
                        op.setSubscriptionId(entity.getId());
                        op.setOperation("insert");
                        op.setSku(product.getControlledSku().getSku());
                        op.setQuantity(product.getQuantity());
                        op.setStatus("PENDING");
                        operations.add(op);
                    }
                    order.setStockReleased(false);
                }
            } else {
                // Should NOT have reserved stock (paid or expired)
                if (currentlyHasReservedStock) {
                    // Currently reserved but shouldn't be - release it
                    logger.info("Creating delete operations for order {} (paid or expired, releasing reserved stock)",
                            order.getNumberRecurrence());
                    operations.addAll(createShopifySyncOperationsForOrderDeletion(order, entity.getId()));
                    order.setStockReleased(true);
                }
                // If not reserved, no-op (already released or never had stock)
            }
        }

        Subscription updatedEntity = mapDtoToEntity(dto, entity);
        subscriptionRepository.save(updatedEntity);

        // Save Shopify sync operations
        shopifySyncOperationRepository.saveAll(operations);

    }

    public void mapRecurrenceDtoToEntity(RecurrenceDTO dto, Order entity) {
        entity.setOrderRec(dto.getOrder() != null ? dto.getOrder() : null);
        entity.setPaymentDate(dto.getPaymentDate() != null ? dto.getPaymentDate() : null);
        entity.setNumberRecurrence(dto.getNumberRecurrence());
        entity.setSkipped(dto.getSkipped());
        entity.setPaymentMethod(dto.getPaymentMethod());
        entity.setOrderStatus(dto.getOrderStatus());
        entity.setLink(dto.getLink());
        entity.setAmountBrl(dto.getAmountBrl());
        entity.setAmountOriginal(dto.getAmountOriginal());
        entity.setCustomerEmail(dto.getCustomerEmail());
        orderRepository.save(entity);
    }

    private void createOrdersWithControlledItems(Subscription entity, RecurrenceDTO dto) {

        if (dto.getProducts() == null || dto.getProducts().isEmpty()) {
            return;
        }

        List<Product> controlledSkus = new ArrayList<>();

        dto.getProducts().forEach(p -> {
            if (p.getSku() == null) {
                return;
            }
            boolean isControlledSku = controlledSkuRepository.existsById(p.getSku());

            if (isControlledSku) {
                ControlledSKu sku = controlledSkuRepository.findById(p.getSku()).get();
                java.math.BigDecimal unitPrice = p.getUnitPrice() != null ? new java.math.BigDecimal(p.getUnitPrice())
                        : null;
                java.math.BigDecimal totalPrice = p.getAmountTotal() != null
                        ? new java.math.BigDecimal(p.getAmountTotal())
                        : null;
                Product newItem = Product.createProduct(sku, p.getQuantity() != null ? p.getQuantity() : 0, unitPrice,
                        totalPrice);
                newItem.setDiscount(p.getDiscount());
                newItem.setCategory(p.getCategory());
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
        newOrder.setSkipped(dto.getSkipped());
        newOrder.setPaymentMethod(dto.getPaymentMethod());
        newOrder.setOrderStatus(dto.getOrderStatus());
        newOrder.setLink(dto.getLink());
        newOrder.setAmountBrl(dto.getAmountBrl());
        newOrder.setAmountOriginal(dto.getAmountOriginal());
        newOrder.setCustomerEmail(dto.getCustomerEmail());
        newOrder.setSubscription(entity);
        // New orders start with stockReleased=false (will be set appropriately in processSubscription)
        newOrder.setStockReleased(false);

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
                    .filter(dtoItem -> dtoItem.getSku() != null && dtoItem.getSku().equals(currentSku))
                    .findFirst()
                    .ifPresent(dtoItem -> {
                        currentItem.setQuantity(dtoItem.getQuantity() != null ? dtoItem.getQuantity() : 0);
                        if (dtoItem.getUnitPrice() != null) {
                            currentItem.setUnitPrice(new java.math.BigDecimal(dtoItem.getUnitPrice()));
                        }
                        if (dtoItem.getAmountTotal() != null) {
                            currentItem.setTotalPrice(new java.math.BigDecimal(dtoItem.getAmountTotal()));
                        }
                        currentItem.setDiscount(dtoItem.getDiscount());
                        currentItem.setCategory(dtoItem.getCategory());
                    });
        });

        // adiciona novos itens se for sku controlado
        updatedItems.stream()
                .filter(dtoItem -> dtoItem.getSku() != null && !currentItems.stream()
                        .anyMatch(
                                entityItem -> entityItem.getControlledSku().getSku().equals(dtoItem.getSku())))
                .forEach(dtoItem -> {
                    if (checkIfControlledSKu(dtoItem)) {
                        ControlledSKu sku = controlledSkuRepository.findById(dtoItem.getSku()).get();
                        java.math.BigDecimal unitPrice = dtoItem.getUnitPrice() != null
                                ? new java.math.BigDecimal(dtoItem.getUnitPrice())
                                : null;
                        java.math.BigDecimal totalPrice = dtoItem.getAmountTotal() != null
                                ? new java.math.BigDecimal(dtoItem.getAmountTotal())
                                : null;
                        Product newItem = Product.createProduct(sku,
                                dtoItem.getQuantity() != null ? dtoItem.getQuantity() : 0, unitPrice, totalPrice);
                        newItem.setDiscount(dtoItem.getDiscount());
                        newItem.setCategory(dtoItem.getCategory());
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

        // Get the last 2 recurrences (by numberRecurrence descending)
        List<Order> lastTwoOrders = subscription.getRecurrences().stream()
                .filter(r -> r.getNumberRecurrence() != null)
                .sorted((a, b) -> b.getNumberRecurrence().compareTo(a.getNumberRecurrence()))
                .limit(2)
                .toList();

        // Only create insert operations for the last 2 recurrences that are unpaid (paymentDate == null)
        for (Order order : lastTwoOrders) {
            if (order.getPaymentDate() == null || order.getPaymentDate().isEmpty()) {
                for (Product item : order.getProducts()) {
                    ShopifySyncOperation op = new ShopifySyncOperation();
                    op.setSubscriptionId(subscription.getId());
                    op.setOperation("insert");
                    op.setSku(item.getControlledSku().getSku());
                    op.setQuantity(item.getQuantity());
                    op.setStatus("PENDING");
                    operations.add(op);
                }
                // Mark as not released since we're creating insert operations
                order.setStockReleased(false);
            } else {
                // Paid recurrence - mark as released
                order.setStockReleased(true);
            }
        }

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

    private Subscription createNewSubscription(SubscriptionFullDTO dto) {

        Subscription newSubscription = new Subscription();

        mapDtoToEntity(dto, newSubscription);

        Subscription savedSubscription = subscriptionRepository.save(newSubscription);

        if (dto.getRecurrences() != null) {
            dto.getRecurrences().forEach(recurrenceDTO -> {
                createOrdersWithControlledItems(savedSubscription, recurrenceDTO);
            });
        }

        Customer customer = getExistingCustomerOrCreateNew(dto);

        customer.getSubscriptions().add(savedSubscription);

        savedSubscription.setCustomer(customer);

        logger.info("Novo subscription criado {}", savedSubscription.toString());

        return savedSubscription;

    }

    private Customer getExistingCustomerOrCreateNew(SubscriptionFullDTO dto) {

        Customer customer = new Customer();

        if (dto.getCustomerEmail() != null && !customerRepository.existsById(dto.getCustomerEmail())) {
            customer = extractCustomerFromDto(dto, customer);
            customer = customerRepository.save(customer);
        } else if (dto.getCustomerEmail() != null) {
            customer = customerRepository.findById(dto.getCustomerEmail()).get();
        }

        return customer;
    }

    private Customer extractCustomerFromDto(SubscriptionFullDTO dto, Customer customer) {
        customer.setEmail(dto.getCustomerEmail() != null ? dto.getCustomerEmail() : null);
        customer.setName(dto.getCustomerName() != null ? dto.getCustomerName() : null);
        customer.setPhone(dto.getCustomerPhone() != null ? dto.getCustomerPhone() : null);
        return customer;

    }

    private Subscription mapDtoToEntity(SubscriptionFullDTO dto, Subscription entity) {

        entity.setId(dto.getSubscription());
        if (dto.getStatus() != null) {
            entity.setStatus(Subscription.convertStatusToEnum(dto.getStatus()));
        }
        entity.setBillingCycle(dto.getBillingCycle());
        entity.setShippingCycle(dto.getShippingCycle());
        entity.setAmountBrl(dto.getAmountBrl());
        entity.setNumberRecurrences(dto.getNumberRecurrences() != null ? dto.getNumberRecurrences() : 0);
        entity.setLimit(dto.getLimit());
        if (dto.getNextBillingDate() != null) {
            entity.setNextBillingDate(java.time.LocalDate.parse(dto.getNextBillingDate()));
        }
        if (dto.getCancellationDate() != null) {
            entity.setCancellationDate(java.time.LocalDate.parse(dto.getCancellationDate()));
        }
        if (dto.getEffectiveCancellationDate() != null) {
            entity.setEffectiveCancellationDate(java.time.LocalDate.parse(dto.getEffectiveCancellationDate()));
        }
        entity.setOrderToken(dto.getOrderToken());
        entity.setPixRecId(dto.getPixRecId());

        return entity;
    }

    /**
     * Fetch all subscriptions by ID sequentially with rate limiting.
     * This replaces the previous double-fetch pattern that caused 429 errors.
     */
    private List<SubscriptionFullDTO> fetchAllSubscriptionsById(List<SubscriptionShortDTO> idList) {
        List<SubscriptionFullDTO> results = new ArrayList<>();

        logger.info("Fetching {} subscriptions sequentially with {}ms delay between requests",
                idList.size(), requestDelayMs);

        for (int i = 0; i < idList.size(); i++) {
            SubscriptionShortDTO shortDto = idList.get(i);

            try {
                SubscriptionFullDTO dto = pagBrasilService.fetchSubscriptionById(shortDto);
                if (dto != null) {
                    results.add(dto);
                } else {
                    // If fetch returned null (likely due to 429 errors), add extra delay
                    logger.debug("Subscription {} returned null, adding extra delay before next request",
                            shortDto.getSubscription());
                    if (i < idList.size() - 1) {
                        Thread.sleep(3000); // 3 second extra delay after failed fetch
                    }
                }

                // Add delay between requests to avoid rate limiting (with jitter: 50-100% of delay)
                if (i < idList.size() - 1) {
                    long jitter = (long) (requestDelayMs * (0.5 + Math.random() * 0.5));
                    Thread.sleep(jitter);
                }
            } catch (InterruptedException e) {
                logger.warn("Delay interrupted while fetching subscription {}",
                        shortDto.getSubscription());
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.warn("Failed to fetch subscription {}: {}",
                        shortDto.getSubscription(), e.getMessage());
                // Add extra delay after exception before continuing
                if (i < idList.size() - 1) {
                    try {
                        Thread.sleep(3000); // 3 second delay after exception
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        logger.info("Successfully fetched {} out of {} subscriptions", results.size(), idList.size());
        return results;
    }

    /**
     * Identifies the last 2 recurrence numbers (by numberRecurrence descending).
     * These are the only recurrences that can hold reserved stock.
     */
    private Set<Integer> getLastTwoRecurrenceNumbers(List<RecurrenceDTO> recurrences) {
        if (recurrences == null || recurrences.isEmpty()) {
            return Set.of();
        }

        return recurrences.stream()
                .filter(r -> r.getNumberRecurrence() != null)
                .sorted((a, b) -> b.getNumberRecurrence().compareTo(a.getNumberRecurrence()))
                .limit(2)
                .map(RecurrenceDTO::getNumberRecurrence)
                .collect(Collectors.toSet());
    }

    /**
     * Creates delete operations for all controlled SKUs in an order.
     */
    private List<ShopifySyncOperation> createShopifySyncOperationsForOrderDeletion(Order order, String subscriptionId) {
        List<ShopifySyncOperation> operations = new ArrayList<>();
        for (Product item : order.getProducts()) {
            ShopifySyncOperation op = new ShopifySyncOperation();
            op.setSubscriptionId(subscriptionId);
            op.setOperation("delete");
            op.setSku(item.getControlledSku().getSku());
            op.setQuantity(item.getQuantity());
            op.setStatus("PENDING");
            operations.add(op);
        }
        return operations;
    }

    private boolean checkForControlledSkus(List<RecurrenceDTO> recurrences) {
        if (recurrences == null || recurrences.isEmpty())
            return false;

        // Only the last 2 recurrences matter for inventory:
        // - Last (un-emitted/null order): future reserved stock
        // - Second-to-last (last emitted): customer can still pay until next is due
        List<RecurrenceDTO> lastTwo = recurrences.stream()
                .filter(r -> r.getNumberRecurrence() != null)
                .sorted((a, b) -> b.getNumberRecurrence().compareTo(a.getNumberRecurrence()))
                .limit(2)
                .toList();

        for (RecurrenceDTO r : lastTwo) {
            if (r.getProducts() != null) {
                boolean exists = r.getProducts().stream()
                        .anyMatch(p -> p.getSku() != null && controlledSkuRepository.existsById(p.getSku()));
                if (exists)
                    return true;
            }
        }

        return false;
    }

}
