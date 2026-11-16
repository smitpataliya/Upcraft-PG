package com.upcraft.stay.common.config;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly typed application configuration bound from {@code app.*} properties.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String timezone = "Asia/Kolkata";
    private String currency = "INR";
    private Pagination pagination = new Pagination();
    private RateLimit rateLimit = new RateLimit();
    private Invitation invitation = new Invitation();
    private Cors cors = new Cors();
    private Jwt jwt = new Jwt();
    private Google google = new Google();
    private Otp otp = new Otp();
    private Storage storage = new Storage();
    private Electricity electricity = new Electricity();
    private Notifications notifications = new Notifications();
    private Reports reports = new Reports();
    private Share share = new Share();

    @Getter
    @Setter
    public static class Pagination {
        private int defaultSize = 10;
        private int maxSize = 100;
    }

    @Getter
    @Setter
    public static class RateLimit {
        private int authRequestsPerMinute = 30;
        private int publicShareRequestsPerMinute = 60;
        private int uploadRequestsPerMinute = 20;
        private int exportRequestsPerMinute = 30;
        private List<String> trustedProxies = List.of(
                "127.0.0.1", "::1", "0:0:0:0:0:0:0:1",
                "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"
        );
    }

    @Getter
    @Setter
    public static class Invitation {
        private int expiryHours = 72;
        private String baseUrl = "http://localhost:3000";
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:3000", "http://localhost:5173");
    }

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private long expirationMs = 604800000L;
    }

    @Getter
    @Setter
    public static class Google {
        private String clientId = "";
    }

    @Getter
    @Setter
    public static class Otp {
        private int expiryMinutes = 10;
    }

    @Getter
    @Setter
    public static class Storage {
        private String localBaseDir = "./uploads/private";
        private long maxFileBytes = 5_242_880L;
    }

    @Getter
    @Setter
    public static class Electricity {
        private double unusualMultiplier = 2.5;
        private double unusualMinUnits = 300.0;
        private int defaultInvoiceDueDays = 7;
    }

    @Getter
    @Setter
    public static class Notifications {
        private boolean emailEnabled = false;
        private boolean pushEnabled = true;
        private String expoPushUrl = "https://exp.host/--/api/v2/push/send";
        private String expoAccessToken = "";
        private int providerTimeoutMs = 5000;
        private String smsProvider = "";
        private String smsApiKey = "";
        private String whatsappProvider = "";
        private String whatsappApiKey = "";
        private int asyncCorePoolSize = 4;
        private int asyncMaxPoolSize = 16;
        private int asyncQueueCapacity = 200;
    }

    @Getter
    @Setter
    public static class Reports {
        private int maxExportRows = 2000;
        private int dashboardCacheSeconds = 30;
    }

    @Getter
    @Setter
    public static class Share {
        private int defaultExpiryHours = 72;
        private int maxExpiryHours = 168;
        private String publicBaseUrl = "http://localhost:3000/share";
    }
}

