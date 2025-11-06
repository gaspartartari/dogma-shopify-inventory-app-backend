package com.tartaritech.inventory_sync.enums;

public enum SubscriptionStatus {
   AGUARDANDO_PRIMEIRO_PAGAMENTO (0),
   ATIVO (1),
   PAGAMENTO_PENDENTE(2),
   INATIVO_CANCELADO(3),
   EXPIRADO(4),
   PAUSADO(5),
   PAGAMENTO_ATRASADO(6);

   private final int value;

    SubscriptionStatus (int value) {
    this.value = value;
   }

   public int getSubscriptionStatus() {
    return value;
   }
}
