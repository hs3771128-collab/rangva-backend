package com.ecommerce.project.payment;

import com.ecommerce.project.model.Order;
import com.ecommerce.project.payload.GatewayInitResponse;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Easypaisa Easypay hosted checkout.
 *
 * <p>The customer is redirected to the Easypay page with a signed request and
 * Easypaisa POSTs the result back to our callback URL.</p>
 */
@Component
public class EasypaisaGateway {

    public static final String NAME = "easypaisa";
    public static final String LABEL = "Easypaisa";

    private final PaymentGatewayProperties properties;

    public EasypaisaGateway(PaymentGatewayProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.getEasypaisa().isConfigured();
    }

    public GatewayInitResponse init(Order order) {
        PaymentGatewayProperties.Easypaisa config = properties.getEasypaisa();

        String amount = String.format(Locale.ROOT, "%.2f", order.getTotalAmount());
        // Unique per attempt, but the part before "-" stays the order id so the
        // callback can be matched back to the order.
        String orderRef = order.getOrderId() + "-" + (System.currentTimeMillis() / 1000);
        String callbackUrl = properties.callbackUrl(NAME);
        String email = PaymentGatewayProperties.hasText(config.getEmail())
                ? config.getEmail()
                : order.getEmail();

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("storeId", config.getStoreId());
        fields.put("amount", amount);
        fields.put("postBackURL", callbackUrl);
        fields.put("orderRefNum", orderRef);
        fields.put("autoRedirect", "1");
        fields.put("paymentMethod", config.getPaymentMethod());
        fields.put("emailAddr", email == null ? "" : email);
        fields.put("mobileNum", "");
        fields.put("merchantHashedReq",
                sign(config.getStoreId(), amount, callbackUrl, orderRef, config.getHashKey()));

        return new GatewayInitResponse(NAME, config.effectiveCheckoutUrl(), fields,
                order.getOrderId(), order.getTotalAmount());
    }

    /**
     * The Easypay post-back usually carries no signature, so when one is present
     * it is verified; otherwise the caller only accepts fields it can validate
     * itself (store id, order reference and amount).
     */
    public boolean verifyCallback(Map<String, String> params) {
        String provided = params.get("merchantHashedReq");
        if (!PaymentGatewayProperties.hasText(provided)) {
            return true;
        }
        PaymentGatewayProperties.Easypaisa config = properties.getEasypaisa();
        String amount = params.get("amount");
        String orderRef = params.get("orderRefNum");
        if (!PaymentGatewayProperties.hasText(amount) || !PaymentGatewayProperties.hasText(orderRef)) {
            return false;
        }
        String expected = sign(config.getStoreId(), amount, properties.callbackUrl(NAME), orderRef,
                config.getHashKey());
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.trim().getBytes(StandardCharsets.UTF_8));
    }

    public boolean storeMatches(Map<String, String> params) {
        String storeId = params.get("storeId");
        return storeId == null || storeId.equals(properties.getEasypaisa().getStoreId());
    }

    public boolean isSuccess(Map<String, String> params) {
        String status = firstNonBlank(params.get("status"), params.get("responseCode"));
        if (status == null) {
            return false;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return "0000".equals(normalized)
                || "00".equals(normalized)
                || "SUCCESS".equals(normalized)
                || "PAID".equals(normalized)
                || "COMPLETED".equals(normalized);
    }

    public String transactionId(Map<String, String> params) {
        String id = firstNonBlank(params.get("transactionId"), params.get("transaction_id"),
                params.get("authCode"), params.get("orderRefNum"));
        return id == null ? "" : id;
    }

    public String responseMessage(Map<String, String> params) {
        String message = firstNonBlank(params.get("desc"), params.get("status"));
        return message == null ? "Easypaisa payment" : message;
    }

    /** HMAC-MD5(storeId + amount + postBackURL + orderRefNum) with the hash key, Base64 encoded. */
    String sign(String storeId, String amount, String postBackUrl, String orderRef, String hashKey) {
        try {
            Mac mac = Mac.getInstance("HmacMD5");
            mac.init(new SecretKeySpec(hashKey.getBytes(StandardCharsets.UTF_8), "HmacMD5"));
            byte[] digest = mac.doFinal((storeId + amount + postBackUrl + orderRef)
                    .getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign Easypaisa request", e);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (PaymentGatewayProperties.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
