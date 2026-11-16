package com.upcraft.stay.common.filter;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Resolves client IP safely by honoring forwarding headers strictly from configured trusted proxies.
 */
public final class TrustedProxyIpResolver {

    private TrustedProxyIpResolver() {
    }

    /**
     * Resolves caller IP without allowing spoofed {@code X-Forwarded-For} from untrusted hosts.
     *
     * @param request        incoming HTTP servlet request
     * @param trustedProxies configured trusted proxy IP/CIDR entries
     * @return resolved client IP address string
     */
    public static String resolveClientIp(HttpServletRequest request, List<String> trustedProxies) {
        String remoteAddress = StringUtils.defaultIfBlank(request.getRemoteAddr(), "unknown").trim();
        if (!isTrustedProxy(remoteAddress, trustedProxies)) {
            return remoteAddress;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(forwardedFor)) {
            return StringUtils.substringBefore(forwardedFor, ",").trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.isNotBlank(realIp)) {
            return realIp.trim();
        }
        return remoteAddress;
    }

    static boolean isTrustedProxy(String candidateIp, List<String> trustedProxies) {
        if (StringUtils.isBlank(candidateIp) || CollectionUtils.isEmpty(trustedProxies)) {
            return false;
        }
        return parseAddress(candidateIp)
                .map(candidate -> trustedProxies.stream().anyMatch(entry -> matchesRule(candidate, entry)))
                .orElse(false);
    }

    private static boolean matchesRule(InetAddress candidate, String cidrOrIp) {
        if (StringUtils.isBlank(cidrOrIp)) {
            return false;
        }
        String trimmed = cidrOrIp.trim();
        int slashIdx = trimmed.indexOf('/');
        if (slashIdx < 0) {
            return parseAddress(trimmed).map(addr -> addr.equals(candidate)).orElse(false);
        }
        String baseIp = trimmed.substring(0, slashIdx);
        int prefixLength = parsePrefixLength(trimmed.substring(slashIdx + 1));
        return parseAddress(baseIp)
                .map(network -> matchesCidrPrefix(candidate.getAddress(), network.getAddress(), prefixLength))
                .orElse(false);
    }

    private static boolean matchesCidrPrefix(byte[] candidateBytes, byte[] networkBytes, int prefixBits) {
        if (candidateBytes.length != networkBytes.length || prefixBits < 0 || prefixBits > candidateBytes.length * 8) {
            return false;
        }
        int fullBytes = prefixBits / 8;
        for (int index = 0; index < fullBytes; index++) {
            if (candidateBytes[index] != networkBytes[index]) {
                return false;
            }
        }
        int remainingBits = prefixBits % 8;
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainingBits);
        return (candidateBytes[fullBytes] & mask) == (networkBytes[fullBytes] & mask);
    }

    private static int parsePrefixLength(String rawPrefix) {
        try {
            return Integer.parseInt(rawPrefix.trim());
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static Optional<InetAddress> parseAddress(String rawIp) {
        try {
            return Optional.of(InetAddress.getByName(rawIp));
        } catch (UnknownHostException exception) {
            return Optional.empty();
        }
    }
}

