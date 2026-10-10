package com.renault.fleet.auth.entity;

/** Mirrors {@code ck_refresh_tokens_revoke_reason}. */
public enum RevokeReason {
    LOGOUT,
    REUSE_DETECTED,
    ADMIN,
    USER_DEACTIVATED
}
