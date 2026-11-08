package com.fierceadventurer.smartportfoliobackend.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentModuleService {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    @Value("${razorpay.key-id}")
    private String razorpayKeyId;
    @Value("${razorpay.key-secret}")
    private String razorpayKeySecret;

    private volatile RazorpayClient razorpayClient;

    private RazorpayClient getRazorpayClient() throws RazorpayException {
        if (razorpayClient == null) {
            synchronized (this) {
                if (razorpayClient == null) {
                    razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
                }
            }
        }
        return razorpayClient;
    }

    public Map<String , Object> createRazorpayOrder(double amount , String currency) throws RazorpayException{
        log.info("Initiating Razorpay Order creation for amount: {}", amount);

        RazorpayClient razorpay = getRazorpayClient();
        JSONObject orderRequest = new JSONObject();

        // Razorpay strictly requires the amount in paise
        orderRequest.put("amount", (int)(amount * 100));
        orderRequest.put("currency", currency);
        orderRequest.put("receipt","txn_" + UUID.randomUUID().toString().substring(0 , 8));

        Order order = razorpay.orders.create(orderRequest);

        // construct the exact JSON your react frontend is waiting for
        Map<String , Object> response = new HashMap<>();
        response.put("id", order.get("id"));
        response.put("amount", order.get("amount"));
        response.put("currency", order.get("currency"));

        String orderId = order.get("id");

        log.info("Successfully created Razorpay Order ID: {}", orderId);
        return response;
    }

    @Transactional
    @CacheEvict(value = "portfolio_sponsors", allEntries = true)
    public void processSuccessfulSponsorship(String razorpayEventId, String paymentId , String name , String email ,
                                             double amount, String currency){


        log.info("Processing verified Razorpay payment. Initiating Outbox transaction for Sponsor: {}", email);

        // save to sponsor table
        String insertSponsorSql = """
                INSERT INTO sponsors (id , sponsor_name, email, amount , currency, status , razorpay_payment_id)
                VALUES(?, ?, ?, ?, ?, 'SUCCESS', ?)
                RETURNING id
                """;

        // immediately grabbing UUID postgres generated
        UUID generatedSponsorId = jdbcClient.sql(insertSponsorSql)
                .param(UUID.randomUUID())
                .param(name)
                .param(email)
                .param(amount)
                .param(currency)
                .param(paymentId)
                .query(UUID.class)
                .single();

        try {
            // building json payload safely
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("sponsorName", name);
            payloadMap.put("amount", amount);
            payloadMap.put("currency", currency);
            payloadMap.put("email", email);
            String payloadJson = objectMapper.writeValueAsString(payloadMap);

            // save to outbox events table (JSONB cast is critical for Postgres)
            String insertOutboxSql = """
                INSERT INTO outbox_events(id , aggregate_type, aggregate_id, event_type, payload, is_processed, event_id)
                VALUES (?, 'SPONSOR', ?, 'SPONSOR_CREATED', ?::jsonb, false, ?)
                """;

            jdbcClient.sql(insertOutboxSql)
                    .param(UUID.randomUUID())
                    .param(generatedSponsorId.toString())
                    .param(payloadJson)
                    .param(razorpayEventId)
                    .update();

            log.info("Successfully committed Sponsor and OutboxEvent to PostgreSQL.");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to serialize sponsor payload for outbox", e);
            throw new RuntimeException("Internal error processing sponsorship data");
        }
    }
}
