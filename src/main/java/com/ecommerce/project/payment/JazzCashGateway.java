package com.ecommerce.project.payment;

import com.ecommerce.project.model.Order;
import com.ecommerce.project.payload.GatewayInitResponse;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JazzCash hosted checkout.
 *
 * <p>The customer is redirected with a signed HTML form to the JazzCash
 * merchant page. JazzCash then POSTs the result back to our callback URL where
 * the {@code pp_SecureHash} signature is verified before an order is marked
 * paid.</p>
 */
@Component
public class JazzCashGateway {

    public static final String NAME = "jazzcash";
    public static final String LABEL = "JazzCash";

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String SUCCESS_CODE = "000";

    private final SecureRandom random = new SecureRandom();
    private final PaymentGatewayProperties properties;

    public JazzCashGateway(PaymentGatewayProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.getJazzcash().isConfigured();
    }

    public GatewayInitResponse init(Order order) {
        PaymentGatewayProperties.JazzCash config = properties.getJazzcash();
        LocalDateTime now = LocalDateTime.now();

        // JazzCash works in paisa: Rs. 1,234.50 -> 123450
        String amount = String.valueOf(Math.round(order.getTotalAmount() * 100));
        String txnRef = "T" + now.format(TIMESTAMP) + String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("pp_Version", config.getVersion());
        fields.put("pp_TxnType", config.getTxnType());
        fields.put("pp_Language", config.getLanguage());
        fields.put("pp_MerchantID", config.getMerchantId());
        fields.put("pp_SubMerchantID", "");
        fields.put("pp_Password", config.getPassword());
        fields.put("pp_BankID", "");
        fields.put("pp_ProductID", "");
        fields.put("pp_TxnRefNo", txnRef);
        fields.put("pp_Amount", amount);
        fields.put("pp_TxnCurrency", "PKR");
        fields.put("pp_TxnDateTime", now.format(TIMESTAMP));
        fields.put("pp_BillReference", String.valueOf(order.getOrderId()));
        fields.put("pp_Description", "Rangva order #" + order.getOrderId());
        fields.put("pp_TxnExpiryDateTime", now.plusHours(24).format(TIMESTAMP));
        fields.put("pp_ReturnURL", properties.callbackUrl(NAME));
        fields.put("pp_SecureHash", secureHash(config.getIntegritySalt(), fields));

        return new GatewayInitResponse(NAME, config.effectiveCheckoutUrl(), fields,
                order.getOrderId(), order.getTotalAmount());
    }

    /**
     * Recomputes the signature over the callback payload and compares it with
     * the {@code pp_SecureHash} JazzCash sent. A callback without a valid
     * signature is never trusted.
     */
    public boolean verifyCallback(Map<String, String> params) {
        String provided = params.get("pp_SecureHash");
        if (!PaymentGatewayProperties.hasText(provided)) {
            return false;
        }
        String expected = secureHash(properties.getJazzcash().getIntegritySalt(), params);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.trim().toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    public boolean isSuccess(Map<String, String> params) {
        String code = params.get("pp_ResponseCode");
        if (code == null) {
            code = params.get("pp_Status");
        }
        return SUCCESS_CODE.equals(code);
    }

    public String transactionId(Map<String, String> params) {
        String id = params.get("pp_RetreivalReferenceNo");
        return PaymentGatewayProperties.hasText(id) ? id : params.get("pp_TxnRefNo");
    }

    public String responseMessage(Map<String, String> params) {
        String message = params.get("pp_ResponseMessage");
        return PaymentGatewayProperties.hasText(message) ? message : "JazzCash payment";
    }

    /**
     * JazzCash signature: HMAC-SHA256 over the integrity salt followed by the
     * values of every non-empty {@code pp_*} field (excluding the signature
     * itself) sorted by field name. Hex, upper case.
     */
    String secureHash(String salt, Map<String, String> fields) {
        String values = fields.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().startsWith("pp_"))
                .filter(entry -> !"pp_SecureHash".equals(entry.getKey()))
                .filter(entry -> PaymentGatewayProperties.hasText(entry.getValue()))
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .collect(Collectors.joining());

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((salt + values).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString().toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign JazzCash request", e);
        }
    }
}
