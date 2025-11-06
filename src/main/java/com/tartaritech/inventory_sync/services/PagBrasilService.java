package com.tartaritech.inventory_sync.services;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.tartaritech.inventory_sync.dtos.SubscriptionDTO;
import com.tartaritech.inventory_sync.dtos.SubscriptionShortDTO;
import com.tartaritech.inventory_sync.dtos.SubscriptionsDTO;

import reactor.util.retry.Retry;

@Service
public class PagBrasilService {

    @Value("${pagbrasil.url}")
    private String pagbrasilUrl;

    @Value("${pagbrasil.secret}")
    private String secret;

    @Value("${pagbrasil.token}")
    private String pbtoken;

    private final WebClient webClient;

    private final Logger logger = LoggerFactory.getLogger(PagBrasilService.class);

    public PagBrasilService(WebClient webClient) {
        this.webClient = webClient;
    }

    public SubscriptionsDTO fetchSubscriptionsByStatus(String status) {
        logger.info("Iniciando busca de subscriptions com status " + status +  " da PagBrasil");

        SubscriptionsDTO subscriptions = new SubscriptionsDTO();
        int page = 1;

        try {
            while (true) {
                logger.debug("Buscando página {} de subscriptions", page);

                SubscriptionsDTO partial = webClient.post()
                        .uri(pagbrasilUrl + "/api/pagstream/subscription/get")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(BodyInserters
                                .fromFormData("secret", secret)
                                .with("pbtoken", pbtoken)
                                .with("status", status)
                                .with("response_type", "JSON")
                                .with("page", String.valueOf(page)))
                        .retrieve()
                        .bodyToMono(SubscriptionsDTO.class)
                        .timeout(Duration.ofSeconds(30))
                        .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                                .maxBackoff(Duration.ofSeconds(5)))
                        .block();

                // Verifica se a resposta é nula ou se não há subscriptions (fim da paginação)
                if (partial == null || partial.getSubscriptions() == null || partial.getSubscriptions().isEmpty()) {
                    logger.info("Fim da paginação alcançado na página {}", page);
                    break;
                }

                // Versao teste- retomar metodo mutado acima em prod
                // if (page == 2) {
                // logger.info("Fim da paginação MOCK TESTE alcançado na página {}", page);
                // break;
                // }

                // Adiciona todas as subscriptions da página atual ao resultado final
                int count = partial.getSubscriptions().size();
                subscriptions.getSubscriptions().addAll(partial.getSubscriptions());
                logger.debug("Adicionadas {} subscriptions da página {}", count, page);
                page++;
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

    public SubscriptionDTO fetchSubscriptionById(SubscriptionShortDTO subscriptionShort) {
        logger.info("Buscando detalhes da subscription: {}", subscriptionShort.getSubscription());

        try {
            SubscriptionDTO dto = webClient.post()
                    .uri(pagbrasilUrl + "/api/pagstream/subscription/get")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters
                            .fromFormData("secret", secret)
                            .with("pbtoken", pbtoken)
                            .with("subscription", subscriptionShort.getSubscription())
                            .with("response_type", "JSON"))
                    .retrieve()
                    .bodyToMono(SubscriptionDTO.class)
                    .timeout(Duration.ofSeconds(30))
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                            .maxBackoff(Duration.ofSeconds(5)))
                    .block();

            if (dto != null) {
                logger.info("Subscription encontrada: id:{} , dto: {}", dto.getId(), dto);
                return dto;
            }

        } catch (Exception e) {
            logger.error("Erro ao buscar subscription por id {}: {}", subscriptionShort.getSubscription(),
                    e.getMessage());
        }

        logger.warn("Subscription não encontrada: {}", subscriptionShort.getSubscription());
        return null;
    }
}
