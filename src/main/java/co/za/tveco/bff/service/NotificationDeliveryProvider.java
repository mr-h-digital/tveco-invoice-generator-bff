package co.za.tveco.bff.service;

import co.za.tveco.bff.entity.EmailOutboxMessage;

public interface NotificationDeliveryProvider {

    boolean isConfigured();

    DeliveryResult deliver(EmailOutboxMessage message);

    record DeliveryResult(boolean success, String error) {}
}
