package com.fierceadventurer.smartportfoliobackend.payment.controller;

import com.fierceadventurer.smartportfoliobackend.payment.service.PaymentModuleService;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentOrderController {

    private final PaymentModuleService paymentModuleService;

    @Value("${razorpay.key-secret}")
    private String razorpayKeySecret;

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

    @PostMapping("/verify-payment")
    public ResponseEntity<?> verifyPayment(@RequestBody Map<String, Object> data) {
        try {
            String orderId = data.get("razorpay_order_id").toString();
            String paymentId = data.get("razorpay_payment_id").toString();
            String signature = data.get("razorpay_signature").toString();
            
            String name = data.getOrDefault("sponsorName", "Anonymous Sponsor").toString();
            String email = data.getOrDefault("email", "").toString();
            double amount = Double.parseDouble(data.get("amount").toString());
            String currency = data.getOrDefault("currency", "INR").toString();

            // Verify signature
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", orderId);
            options.put("razorpay_payment_id", paymentId);
            options.put("razorpay_signature", signature);

            boolean isValid = Utils.verifyPaymentSignature(options, razorpayKeySecret);

            if (isValid) {
                // Use paymentId as a unique event ID for the outbox to prevent duplicates from direct verification
                String directEventId = "direct_" + paymentId;
                paymentModuleService.processSuccessfulSponsorship(directEventId, paymentId, name, email, amount, currency);
                return ResponseEntity.ok(Map.of("message", "Payment verified and sponsorship registered successfully."));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Invalid payment signature."));
            }

        } catch (org.springframework.dao.DuplicateKeyException e) {
            log.info("Duplicate sponsorship registration attempt for payment ID: {}", data.get("razorpay_payment_id"));
            return ResponseEntity.ok(Map.of("message", "Sponsorship already registered."));
        } catch (Exception e) {
            log.error("Failed to verify Razorpay payment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to verify payment: " + e.getMessage()));
        }
    }
}
