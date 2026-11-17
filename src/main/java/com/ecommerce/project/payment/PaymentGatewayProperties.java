package com.ecommerce.project.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Pakistani payment gateways (JazzCash + Easypaisa).
 *
 * <p>Every merchant credential is read from an environment variable (see
 * {@code application.properties}) so no secret is ever committed to the
 * repository. A gateway is only offered at checkout when it is enabled
 * <em>and</em> its credentials are present.</p>
 */
@ConfigurationProperties(prefix = "app.payments")
public class PaymentGatewayProperties {

    /** Frontend page the backend redirects to after a gateway callback. */
    private String frontendReturnUrl = "http://localhost:5173/order-confirm";

    /** Public base URL of this backend; used to build gateway callback URLs. */
    private String backendBaseUrl = "http://localhost:8080";

    private final JazzCash jazzcash = new JazzCash();
    private final Easypaisa easypaisa = new Easypaisa();

    public String callbackUrl(String gateway) {
        String base = backendBaseUrl == null ? "" : backendBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/api/payments/" + gateway + "/callback";
    }

    public String getFrontendReturnUrl() {
        return frontendReturnUrl;
    }

    public void setFrontendReturnUrl(String frontendReturnUrl) {
        this.frontendReturnUrl = frontendReturnUrl;
    }

    public String getBackendBaseUrl() {
        return backendBaseUrl;
    }

    public void setBackendBaseUrl(String backendBaseUrl) {
        this.backendBaseUrl = backendBaseUrl;
    }

    public JazzCash getJazzcash() {
        return jazzcash;
    }

    public Easypaisa getEasypaisa() {
        return easypaisa;
    }

    public static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /** JazzCash hosted checkout ("merchant form") settings. */
    public static class JazzCash {

        private boolean enabled = false;
        private boolean sandbox = true;
        private String merchantId = "";
        private String password = "";
        private String integritySalt = "";
        private String checkoutUrl = "";
        private String version = "1.1";
        private String txnType = "MWALLET";
        private String language = "EN";

        public boolean isConfigured() {
            return enabled && hasText(merchantId) && hasText(password) && hasText(integritySalt);
        }

        public String effectiveCheckoutUrl() {
            if (hasText(checkoutUrl)) {
                return checkoutUrl.trim();
            }
            return sandbox
                    ? "https://sandbox.jazzcash.com.pk/CustomerPortal/transactionmanagement/merchantform/"
                    : "https://payments.jazzcash.com.pk/CustomerPortal/transactionmanagement/merchantform/";
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isSandbox() {
            return sandbox;
        }

        public void setSandbox(boolean sandbox) {
            this.sandbox = sandbox;
        }

        public String getMerchantId() {
            return merchantId;
        }

        public void setMerchantId(String merchantId) {
            this.merchantId = merchantId;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getIntegritySalt() {
            return integritySalt;
        }

        public void setIntegritySalt(String integritySalt) {
            this.integritySalt = integritySalt;
        }

        public String getCheckoutUrl() {
            return checkoutUrl;
        }

        public void setCheckoutUrl(String checkoutUrl) {
            this.checkoutUrl = checkoutUrl;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getTxnType() {
            return txnType;
        }

        public void setTxnType(String txnType) {
            this.txnType = txnType;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }
    }

    /** Easypaisa Easypay hosted checkout settings. */
    public static class Easypaisa {

        private boolean enabled = false;
        private boolean sandbox = true;
        private String storeId = "";
        private String hashKey = "";
        private String checkoutUrl = "";
        private String paymentMethod = "MA";
        private String email = "";

        public boolean isConfigured() {
            return enabled && hasText(storeId) && hasText(hashKey);
        }

        public String effectiveCheckoutUrl() {
            if (hasText(checkoutUrl)) {
                return checkoutUrl.trim();
            }
            return sandbox
                    ? "https://easypaystg.easypaisa.com.pk/easypay/Index.jsf"
                    : "https://easypay.easypaisa.com.pk/easypay/Index.jsf";
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isSandbox() {
            return sandbox;
        }

        public void setSandbox(boolean sandbox) {
            this.sandbox = sandbox;
        }

        public String getStoreId() {
            return storeId;
        }

        public void setStoreId(String storeId) {
            this.storeId = storeId;
        }

        public String getHashKey() {
            return hashKey;
        }

        public void setHashKey(String hashKey) {
            this.hashKey = hashKey;
        }

        public String getCheckoutUrl() {
            return checkoutUrl;
        }

        public void setCheckoutUrl(String checkoutUrl) {
            this.checkoutUrl = checkoutUrl;
        }

        public String getPaymentMethod() {
            return paymentMethod;
        }

        public void setPaymentMethod(String paymentMethod) {
            this.paymentMethod = paymentMethod;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }
}
