package com.fierceadventurer.smartportfoliobackend.payment.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.web.servlet.function.RequestPredicates.param;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentModuleService {

    private final JdbcClient jdbcClient;

    @Value("${RAZORPAY_KEY_ID}")
    private String razorpayKeyId;
    @Value("${RAZORPAY_KEY_SECRET}")
    private String razorpayKeySecret;

    public Map<String , Object> createRazorpayOrder(double amount , String currency) throws RazorpayException{
        log.info("Initiating Razorpay Order creation for amount: {}", amount);

        RazorpayClient razorpay = new RazorpayClient(razorpayKeyId , razorpayKeySecret);
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
    public void processSuccessfulSponsorship(String razorpayEventId, String paymentId , String name , String email ,
                                             double amount, String currency){


        UUID eventId = UUID.randomUUID();

        log.info("Processing verified Razorpay payment. Initiating Outbox transaction for Sponsor: {}", email);

        // save to sponsor table
        String insertSponsorSql = """
                INSERT INTO sponsors (id , sponsor_name, sponsor_email, amount , currency, status , razorpay_payment_id)
                VALUES(?, ?, ?, ?, ?, 'SUCCESS', ?)
                RETURNING id
                """;

        // immediately grabbing UUID postgres generated
        UUID generatedSponsorId = jdbcClient.sql(insertSponsorSql)
                .param(name)
                .param(email)
                .param(amount)
                .param(currency)
                .param(paymentId)
                .query(UUID.class)
                .single();

        // building json payload
        String payloadJson = String.format(
                "{\"sponsorName\":\"%s\", \"amount\":%f, \"currency\":\"%s\", \"email\":\"%s\"}",
                name, amount , currency , email
        );

        // save to outbox events table (JSONB cast is critical for Postgres
        String insertOutboxSql = """
                Insert INTO outbox_events(id , aggregate_type, aggregate_id, event_type, payload, is_Processed, event_id)
                VALUES (?, 'SPONSOR', ?, 'SPONSOR_CREATED', ?::jsonb, 'false', ?)
                """;

        jdbcClient.sql(insertOutboxSql)
                .param(generatedSponsorId.toString())
                .param(payloadJson)
                .param(razorpayEventId)
                .update();

        log.info("Successfully committed Sponsor and OutboxEvent to PostgreSQL.");
    }
}
