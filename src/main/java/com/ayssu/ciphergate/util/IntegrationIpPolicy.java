package com.ayssu.ciphergate.util;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Optional;

/** Literal IPs only: never resolve hostnames from user-controlled configuration. */
public final class IntegrationIpPolicy {
    private IntegrationIpPolicy() {}
    private static Optional<String> normalize(String value) {
        if (value == null) return Optional.empty();
        String ip = value.trim();
        if (!ip.matches("[0-9A-Fa-f:.]+") || ip.contains("%")) return Optional.empty();
        if (!ip.contains(":")) {
            String[] pieces = ip.split("\\.", -1);
            if (pieces.length != 4) return Optional.empty();
            for (String piece : pieces) {
                if (!piece.matches("0|[1-9][0-9]{0,2}")) return Optional.empty();
                if (Integer.parseInt(piece) > 255) return Optional.empty();
            }
        }
        try { return Optional.of(InetAddress.getByName(ip).getHostAddress()); }
        catch (Exception invalid) { return Optional.empty(); }
    }
    public static boolean valid(String value) { return normalize(value).isPresent(); }
    public static boolean allows(String configured, String source) {
        var ip = normalize(source);
        return configured != null && ip.isPresent() && Arrays.stream(configured.split(","))
                .map(IntegrationIpPolicy::normalize).anyMatch(v -> v.isPresent() && v.equals(ip));
    }
}
