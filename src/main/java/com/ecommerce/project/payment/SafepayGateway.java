package com.ecommerce.project.payment;

import com.ecommerce.project.exceptions.APIException;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.payload.GatewayInitResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Safepay card checkout (Visa / Mastercard).
 *
 * <p>Safepay is a hosted checkout: the backend creates a payment with the
 * merchant secret, the browser is sent to Safepay's page where the card is
 * entered, and Safepay notifies us with a signed webhook. The card number
 * never reaches this backend.</p>
 */
@Component
public class SafepayGateway {

    public static final String NAME = "safepay";
    public static final String LABEL = "Card (Safepay)";

    private static final Logger log = LoggerFactory.getLogger(SafepayGateway.class);

    private final PaymentGatewayProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public SafepayGateway(PaymentGatewayProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(25));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public boolean isEnabled() {
        return properties.getSafepay().isConfigured();
    }

    /**
     * Creates a Safepay payment for the order and returns the checkout URL the
     * browser must be sent to.
     */
    public GatewayInitResponse init(Order order) {
        PaymentGatewayProperties.Safepay config = properties.getSafepay();

        long amountPaisa = Math.round(order.getTotalAmount() * 100);
        // Unique per attempt; the part before "-" stays the order id so a
        // webhook can always be matched back to the order.
        String orderRef = order.getOrderId() + "-" + (System.currentTimeMillis() / 1000);
        String returnUrl = returnUrl(order.getOrderId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("client", Map.of("api_key", config.getApiKey()));
        payload.put("amount", amountPaisa);
        payload.put("currency", "PKR");
        payload.put("environment", config.environment());
        payload.put("order_id", orderRef);
        payload.put("redirect_url", returnUrl);
        payload.put("cancel_url", returnUrl);

        JsonNode response;
        try {
            response = restClient.post()
                    .uri(config.effectiveApiBase() + "/order/v1/init")
                    .header("X-SFPY-MERCHANT-SECRET", config.getSecret())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            log.warn("Safepay init failed with HTTP {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new APIException("Safepay could not start this payment (HTTP "
                    + e.getStatusCode().value() + ")");
        } catch (Exception e) {
            log.warn("Safepay init failed", e);
            throw new APIException("Safepay is unreachable right now — please try again");
        }

        String token = firstText(response, "data.token", "token");
        if (!PaymentGatewayProperties.hasText(token)) {
            log.warn("Safepay init returned no token: {}", response);
            throw new APIException("Safepay did not return a checkout token");
        }

        String checkoutUrl = firstText(response, "data.checkout_url", "checkout_url", "data.redirect_url");
        if (!PaymentGatewayProperties.hasText(checkoutUrl)) {
            checkoutUrl = config.effectiveCheckoutBase() + "?token=" + token;
            if (config.isSandbox()) {
                checkoutUrl = checkoutUrl + "&environment=sandbox";
            }
        }

        return new GatewayInitResponse(NAME, checkoutUrl, Map.of(),
                order.getOrderId(), order.getTotalAmount(), token);
    }

    /**
     * Safepay signs webhooks with {@code Base64(HMAC-SHA256(rawBody, webhookSecret))}.
     * A payload whose signature does not match is never trusted.
     */
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        PaymentGatewayProperties.Safepay config = properties.getSafepay();
        if (!config.isWebhookConfigured() || !PaymentGatewayProperties.hasText(signature) || rawBody == null) {
            return false;
        }
        try {
            String expected = hmacBase64(config.getWebhookSecret(), rawBody);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.trim().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Unable to verify Safepay webhook signature", e);
            return false;
        }
    }

    /** Reads a Safepay webhook payload into the fields the service needs. */
    public WebhookEvent parseWebhook(String rawBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw new APIException("Invalid Safepay webhook payload");
        }

        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            data = root;
        }

        String event = firstText(root, "type", "event", "name");
        String state = firstText(data, "state", "status", "payment_status");
        if (!PaymentGatewayProperties.hasText(state)) {
            state = firstText(root, "state", "status");
        }

        Long orderId = parseOrderRef(firstText(data, "order_id", "orderId", "reference"));
        if (orderId == null) {
            orderId = parseOrderRef(firstText(root, "order_id", "orderId"));
        }

        String tracker = firstText(data, "tracker", "token", "tracker_id");
        if (!PaymentGatewayProperties.hasText(tracker)) {
            tracker = firstText(root, "tracker", "token");
        }

        Double amount = amountInRupees(firstText(data, "amount", "total"));
        boolean paid = isPaid(event, state, data);
        boolean failed = !paid && isFailed(event, state);

        return new WebhookEvent(orderId, tracker, amount, paid, failed,
                firstNonBlank(state, event), firstNonBlank(
                        firstText(data, "message", "reason", "failure_reason"),
                        firstText(root, "message", "reason"),
                        paid ? "Card payment received" : "Safepay payment update"));
    }

    /**
     * Asks Safepay for the current state of a tracker. This is the fallback
     * used when the customer returns before the webhook is delivered.
     */
    public PaymentStatus checkStatus(String tracker) {
        PaymentGatewayProperties.Safepay config = properties.getSafepay();
        if (!PaymentGatewayProperties.hasText(tracker)) {
            return new PaymentStatus(false, false, "PENDING", "No Safepay tracker supplied");
        }

        JsonNode response;
        try {
            response = restClient.get()
                    .uri(config.effectiveApiBase() + "/order/v1/authorized?tracker={tracker}", tracker)
                    .header("X-SFPY-MERCHANT-SECRET", config.getSecret())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            log.warn("Safepay status check failed with HTTP {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            return new PaymentStatus(false, false, "PENDING",
                    "Safepay is still confirming this payment");
        } catch (Exception e) {
            log.warn("Safepay status check failed", e);
            return new PaymentStatus(false, false, "PENDING",
                    "Safepay is still confirming this payment");
        }

        JsonNode data = response.path("data");
        if (data.isMissingNode() || data.isNull()) {
            data = response;
        }
        String state = firstNonBlank(
                firstText(data, "state", "status", "payment_status"),
                firstText(response, "state", "status"));

        boolean paid = firstBoolean(data, "authorized", "paid", "captured")
                || isPaid(firstText(response, "type", "event"), state, data)
                || isPaid(null, state, data);
        boolean failed = !paid && isFailed(firstText(response, "type", "event"), state);

        return new PaymentStatus(paid, failed, firstNonBlank(state, paid ? "COMPLETED" : "PENDING"),
                paid ? "Card payment received"
                        : failed ? "Card payment was declined"
                        : "Safepay is still confirming this payment");
    }

    private String returnUrl(Long orderId) {
        String base = properties.getFrontendReturnUrl();
        String separator = base.contains("?") ? "&" : "?";
        return base + separator + "payment=return&gateway=" + NAME + "&orderId=" + orderId;
    }

    private String hmacBase64(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private Long parseOrderRef(String value) {
        if (!PaymentGatewayProperties.hasText(value)) {
            return null;
        }
        String first = value.trim().split("-")[0].trim();
        return first.matches("\\d+") ? Long.valueOf(first) : null;
    }

    /** Safepay works in paisa, so convert it back to rupees. */
    private Double amountInRupees(String value) {
        if (!PaymentGatewayProperties.hasText(value)) {
            return null;
        }
        try {
            return Double.valueOf(value.trim()) / 100.0;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isPaid(String event, String state, JsonNode data) {
        String eventName = normalize(event);
        if (eventName.contains("FAIL") || eventName.contains("DECLIN")
                || eventName.contains("CANCEL") || eventName.contains("EXPIRE")) {
            return false;
        }
        if (eventName.contains("SUCCEED") || eventName.contains("SUCCESS")
                || eventName.contains("PAID") || eventName.contains("COMPLETED")) {
            return true;
        }

        String normalized = normalize(state);
        if (normalized.contains("FAIL") || normalized.contains("DECLIN")
                || normalized.contains("CANCEL") || normalized.contains("EXPIRE")
                || normalized.contains("VOID") || normalized.contains("ABANDON")) {
            return false;
        }
        if (normalized.contains("COMPLETED") || normalized.contains("AUTHORIZED")
                || normalized.contains("PAID") || normalized.contains("SUCCESS")
                || normalized.contains("APPROVED") || normalized.contains("CAPTURED")) {
            return true;
        }
        return false;
    }

    private boolean isFailed(String event, String state) {
        String normalized = normalize(event) + " " + normalize(state);
        return normalized.contains("FAIL") || normalized.contains("DECLIN")
                || normalized.contains("CANCEL") || normalized.contains("EXPIRE")
                || normalized.contains("VOID") || normalized.contains("ABANDON");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String firstText(JsonNode node, String... paths) {
        if (node == null) {
            return null;
        }
        for (String path : paths) {
            JsonNode value = node;
            for (String part : path.split("\\.")) {
                value = value.path(part);
            }
            if (!value.isMissingNode() && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private boolean firstBoolean(JsonNode node, String... fields) {
        if (node == null) {
            return false;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isBoolean()) {
                return value.asBoolean();
            }
            if (value.isTextual() && "true".equalsIgnoreCase(value.asText().trim())) {
                return true;
            }
        }
        return false;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (PaymentGatewayProperties.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /** Fields extracted from a Safepay webhook. */
    public record WebhookEvent(Long orderId,
                               String tracker,
                               Double amount,
                               boolean paid,
                               boolean failed,
                               String status,
                               String message) {
    }

    /** Result of asking Safepay about a tracker. */
    public record PaymentStatus(boolean paid, boolean failed, String status, String message) {
    }
}
