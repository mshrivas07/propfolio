package com.propfolio.documents;

import java.util.Locale;

/**
 * Account numbers are printed differently on bills and portals ("1234 5678-9" vs "123456789"),
 * so they are compared after removing spaces and dashes and ignoring case.
 */
public final class AccountNumbers {

    private AccountNumbers() {
    }

    public static String normalize(String accountNumber) {
        if (accountNumber == null) {
            return null;
        }
        String normalized = accountNumber.replaceAll("[\\s\\-]", "").toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    public static boolean same(String a, String b) {
        String left = normalize(a);
        return left != null && left.equals(normalize(b));
    }
}
