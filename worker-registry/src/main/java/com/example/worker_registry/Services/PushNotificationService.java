package com.example.worker_registry.Services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    public void notifyCliente(Long clienteId, String title, String message) {
        log.info("[PUSH] Cliente {} | {}: {}", clienteId, title, message);
    }
}
