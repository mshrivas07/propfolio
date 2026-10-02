package com.propfolio.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Supabase project settings, bound from {@code app.supabase.*}.
 *
 * @param url     project URL, e.g. https://abcd.supabase.co
 * @param jwksUri public signing keys used to verify access tokens
 */
@ConfigurationProperties(prefix = "app.supabase")
public record SupabaseProperties(String url, String jwksUri) {

    /** Value of the "iss" claim in Supabase access tokens. */
    public String issuer() {
        return stripTrailingSlash(url) + "/auth/v1";
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
