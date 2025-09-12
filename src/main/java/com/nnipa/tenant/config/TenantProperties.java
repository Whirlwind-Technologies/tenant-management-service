package com.nnipa.tenant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "tenant")
public class TenantProperties {
    private Defaults defaults = new Defaults();
    private Subscription subscription = new Subscription();
    private Billing billing = new Billing();
    private FeatureFlags featureFlags = new FeatureFlags();

    @Data
    public static class Defaults {
        private int trialDays;
        private int maxUsers;
        private int maxProjects;
        private int storageQuotaGb;
        private int apiRateLimit;
    }

    @Data
    public static class Subscription {
        private int gracePeriodDays;
        private boolean autoRenewEnabled;
        private int paymentRetryAttempts;
    }

    @Data
    public static class Billing {
        private Stripe stripe = new Stripe();
        private String defaultCurrency;
        private double taxRate;

        @Data
        public static class Stripe {
            private boolean enabled;
            private String apiKey;
            private String webhookSecret;
        }
    }

    @Data
    public static class FeatureFlags {
        private List<String> defaultEnabled = new ArrayList<>();
        private List<String> premiumFeatures = new ArrayList<>();
    }
}