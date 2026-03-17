package com.fierceadventurer.smartportfoliobackend.payment.controller;

import com.fierceadventurer.smartportfoliobackend.payment.service.PaymentModuleService;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentOrderController {

    private final PaymentModuleService paymentModuleService;

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> data){
        try{
            // amount requested by frontend
            double amount = Double.parseDouble(data.get("amount").toString());
            String currency = data.getOrDefault("currency", "INR").toString();

            // calling service
            Map<String, Object> orderDetails = paymentModuleService.createRazorpayOrder(amount , currency);
            return ResponseEntity.ok(orderDetails);

        } catch (Exception e) {
            log.error("Failed to generate Razorpay order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to create order: " + e.getMessage()));
        }
    }
}
