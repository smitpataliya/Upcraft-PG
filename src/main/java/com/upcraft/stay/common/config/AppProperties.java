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
}

