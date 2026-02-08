package com.tartaritech.inventory_sync.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tartaritech.inventory_sync.dtos.SubscriptionFullDTO;
import com.tartaritech.inventory_sync.dtos.SubscriptionShortDTO;
import com.tartaritech.inventory_sync.dtos.SubscriptionsDTO;

@Service
public class PagBrasilService {

    // Global lock to prevent concurrent PagBrasil API access from multiple services
    private final ReentrantLock apiLock = new ReentrantLock();



    @Value("${pagbrasil.url}")
    private String pagbrasilUrl;

    @Value("${pagbrasil.secret}")
    private String secret;

    @Value("${pagbrasil.token}")
    private String pbtoken;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Logger logger = LoggerFactory.getLogger(PagBrasilService.class);

    public void acquireApiLock() {
        apiLock.lock();
    }

    public void releaseApiLock() {
        apiLock.unlock();
    }

    public boolean tryAcquireApiLock() {
        return apiLock.tryLock();
    }

    /**
     * Attempts to acquire the lock, waiting up to the specified timeout.
     * Returns true if lock was acquired, false if timeout expired.
     * 
     * @param timeoutSeconds Maximum time to wait for the lock
     * @return true if lock acquired, false if timeout expired
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean tryAcquireApiLockWithTimeout(long timeoutSeconds) throws InterruptedException {
        return apiLock.tryLock(timeoutSeconds, TimeUnit.SECONDS);
    }

    public PagBrasilService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public SubscriptionsDTO fetchSubscriptionsByStatus(String status) {
        logger.info("Iniciando busca de subscriptions com status {} da PagBrasil", status);

        SubscriptionsDTO subscriptions = new SubscriptionsDTO();
        int page = 1;

        try {
            while (true) {
                logger.debug("Buscando página {} de subscriptions", page);

                SubscriptionsDTO partial = fetchSubscriptionsByStatusWithRetry(status, page);

                // Verifica se a resposta é nula ou se não há subscriptions (fim da paginação)
                if (partial == null || partial.getSubscriptions() == null || partial.getSubscriptions().isEmpty()) {
                    logger.info("Fim da paginação alcançado na página {}", page);
                    break;
                }

                // Adiciona todas as subscriptions da página atual ao resultado final
                int count = partial.getSubscriptions().size();
                subscriptions.getSubscriptions().addAll(partial.getSubscriptions());
                logger.debug("Adicionadas {} subscriptions da página {}", count, page);
                page++;

                // Delay between pages to respect rate limits (with jitter: 50-100% of 2s)
                try {
                    Thread.sleep((long) (2000 * (0.5 + Math.random() * 0.5)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            logger.info("Busca finalizada. Total de {} subscriptions encontradas",
                    subscriptions.getSubscriptions().size());

        } catch (Exception e) {
            logger.error("Erro ao buscar dados da PagBrasil na página {}: {}", page, e.getMessage());
            logger.warn("Retornando {} subscriptions já coletadas antes do erro",
                    subscriptions.getSubscriptions().size());
        }

        return subscriptions;
    }

    private SubscriptionsDTO fetchSubscriptionsByStatusWithRetry(String status, int page) {
        int maxRetries = 5;
        long initialBackoffMs = 5000;  // 5 seconds
        long maxBackoffMs = 120000;    // 2 minutes

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                String formData = String.format("secret=%s&pbtoken=%s&status=%s&response_type=JSON&page=%d",
                        secret, pbtoken, status, page);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(pagbrasilUrl + "/api/pagstream/subscription/get"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(formData))
                        .timeout(Duration.ofSeconds(30))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 429) {
                    long waitMs = getWaitTimeFrom429(response, initialBackoffMs, attempt, maxBackoffMs);
                    if (waitMs < 0) {
                        logger.warn("429 for status {} page {} with Retry-After exceeding max. Aborting retries.",
                                status, page);
                        return null;
                    }
                    logger.warn("429 for status {} page {}. Attempt {}/{}. Waiting {}ms...",
                            status, page, attempt + 1, maxRetries, waitMs);
                    Thread.sleep(waitMs);
                    continue;
                }

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return objectMapper.readValue(response.body(), SubscriptionsDTO.class);
                } else {
                    logger.warn("Unexpected status code {} for status {} page {}. Attempt {}/{}",
                            response.statusCode(), status, page, attempt + 1, maxRetries);
                    if (attempt < maxRetries - 1) {
                        Thread.sleep(initialBackoffMs * (attempt + 1));
                    }
                }

            } catch (java.net.http.HttpTimeoutException e) {
                logger.warn("Timeout for status {} page {}. Attempt {}/{}",
                        status, page, attempt + 1, maxRetries);
                if (attempt < maxRetries - 1) {
                    try {
                        Thread.sleep(initialBackoffMs * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (InterruptedException e) {
                logger.warn("Interrupted while waiting for retry");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.warn("Error for status {} page {}. Attempt {}/{}: {}",
                        status, page, attempt + 1, maxRetries, e.getMessage());
                if (attempt < maxRetries - 1) {
                    try {
                        Thread.sleep(initialBackoffMs * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        logger.error("Failed to fetch status {} page {} after {} attempts", status, page, maxRetries);
        return null;
    }

    public SubscriptionFullDTO fetchSubscriptionById(SubscriptionShortDTO subscriptionShort) {
        logger.info("Buscando detalhes da subscription: {}", subscriptionShort.getSubscription());

        int maxRetries = 5;
        long initialBackoffMs = 5000;  // 5 seconds
        long maxBackoffMs = 120000;    // 2 minutes
        boolean encountered429 = false;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                String formData = String.format("secret=%s&pbtoken=%s&subscription=%s&response_type=JSON",
                        secret, pbtoken, subscriptionShort.getSubscription());

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(pagbrasilUrl + "/api/pagstream/subscription/get"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(formData))
                        .timeout(Duration.ofSeconds(30))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 429) {
                    encountered429 = true;
                    long waitMs = getWaitTimeFrom429(response, initialBackoffMs, attempt, maxBackoffMs);
                    if (waitMs < 0) {
                        logger.warn("429 for subscription {} with Retry-After exceeding max. Aborting retries.",
                                subscriptionShort.getSubscription());
                        return null;
                    }
                    logger.warn("429 for subscription {}. Attempt {}/{}. Waiting {}ms...",
                            subscriptionShort.getSubscription(), attempt + 1, maxRetries, waitMs);
                    Thread.sleep(waitMs);
                    continue;
                }

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    SubscriptionFullDTO dto = objectMapper.readValue(response.body(), SubscriptionFullDTO.class);
                    if (dto != null) {
                        logger.info("Subscription encontrada: id:{}", dto.getSubscription());
                        if (encountered429) {
                            logger.debug("Adding extra delay after 429 recovery for subscription {}",
                                    subscriptionShort.getSubscription());
                            Thread.sleep(5000);
                        }
                        return dto;
                    }
                } else {
                    logger.warn("Unexpected status code {} for subscription {}. Attempt {}/{}",
                            response.statusCode(), subscriptionShort.getSubscription(), attempt + 1, maxRetries);
                    if (attempt < maxRetries - 1) {
                        Thread.sleep(initialBackoffMs * (attempt + 1));
                    }
                }

            } catch (java.net.http.HttpTimeoutException e) {
                logger.warn("Timeout for subscription {}. Attempt {}/{}",
                        subscriptionShort.getSubscription(), attempt + 1, maxRetries);
                if (attempt < maxRetries - 1) {
                    try {
                        Thread.sleep(initialBackoffMs * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (InterruptedException e) {
                logger.warn("Interrupted while waiting for retry");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.warn("Error for subscription {}. Attempt {}/{}: {}",
                        subscriptionShort.getSubscription(), attempt + 1, maxRetries, e.getMessage());
                if (attempt < maxRetries - 1) {
                    try {
                        Thread.sleep(initialBackoffMs * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        if (encountered429) {
            logger.warn("Failed to fetch subscription {} after 429 errors. Waiting 10s before continuing...",
                    subscriptionShort.getSubscription());
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        logger.warn("Subscription not found or error fetching: {}", subscriptionShort.getSubscription());
        return null;
    }

    /**
     * Extracts wait time from a 429 response.
     * Checks the Retry-After header first; falls back to exponential backoff.
     */
    private long getWaitTimeFrom429(HttpResponse<String> response, long initialBackoffMs, int attempt,
            long maxBackoffMs) {
        var retryAfter = response.headers().firstValue("Retry-After");
        if (retryAfter.isPresent()) {
            try {
                long retryAfterSeconds = Long.parseLong(retryAfter.get());
                long waitMs = retryAfterSeconds * 1000;
                if (waitMs > maxBackoffMs) {
                    logger.warn("Retry-After says {}s ({}ms) which exceeds max backoff {}ms. Signaling abort.",
                            retryAfterSeconds, waitMs, maxBackoffMs);
                    return -1;
                }
                return waitMs;
            } catch (NumberFormatException e) {
                logger.debug("Retry-After unparseable: '{}', falling back to exponential backoff", retryAfter.get());
            }
        }
        long backoff = Math.min(initialBackoffMs * (1L << attempt), maxBackoffMs);
        return (long) (backoff * (0.5 + Math.random() * 0.5)); // jitter: 50-100% of backoff
    }
}
