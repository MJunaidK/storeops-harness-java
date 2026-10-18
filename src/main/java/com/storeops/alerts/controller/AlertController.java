package com.storeops.alerts.controller;

import com.storeops.alerts.model.Notification;
import com.storeops.alerts.service.AlertService;
import com.storeops.shared.security.AuthenticatedUser;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public List<Notification> list(AuthenticatedUser caller) {
        return alertService.listForUser(caller);
    }
}
