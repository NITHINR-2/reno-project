package com.renault.fleet.auth.service;

import jakarta.servlet.http.HttpServletRequest;

/** Request metadata stored with refresh tokens and audit rows. No proxy headers are trusted yet. */
public record ClientInfo(String ipAddress, String userAgent) {

    public static ClientInfo from(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && userAgent.length() > 500) {
            userAgent = userAgent.substring(0, 500);
        }
        return new ClientInfo(request.getRemoteAddr(), userAgent);
    }
}
