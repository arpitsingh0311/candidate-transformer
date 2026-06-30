package com.eightfold.transformer.normalizer;

import java.util.regex.*;

/**
 * Normalises phone numbers to E.164 format: +[country_code][national_number]
 *
 * Handles:
 *  - US/CA numbers (10 digits) -> +1XXXXXXXXXX
 *  - Numbers already starting with + -> strip non-digits, re-add +
 *  - Indian numbers (10 digits starting with 6-9) -> +91XXXXXXXXXX
 *  - Graceful fallback: return null if number is unrecognisable
 */
public class PhoneNormalizer {

    private static final Pattern DIGITS_RE = Pattern.compile("\\d+");

    public static String toE164(String raw) {
        if (raw == null) return null;
        raw = raw.trim();

        // Collect all digit groups
        StringBuilder digits = new StringBuilder();
        Matcher m = DIGITS_RE.matcher(raw);
        while (m.find()) digits.append(m.group());
        String d = digits.toString();

        if (d.length() < 7) return null; // too short

        // Already has country code indicated by leading +
        if (raw.startsWith("+")) {
            if (d.length() >= 10 && d.length() <= 15) return "+" + d;
            return null;
        }

        // US/CA: 11 digits starting with 1
        if (d.length() == 11 && d.startsWith("1")) return "+" + d;

        // US/CA: 10 digits
        if (d.length() == 10 && !d.startsWith("0")) {
            char first = d.charAt(0);
            // Could be India or US heuristic: Indian mobiles start 6-9
            if (first >= '6' && first <= '9') return "+91" + d;
            return "+1" + d;
        }

        // India: 12 digits starting with 91
        if (d.length() == 12 && d.startsWith("91")) return "+" + d;

        // Generic: if 10–15 digits, prepend +
        if (d.length() >= 10 && d.length() <= 15) return "+" + d;

        return null; // unrecognisable
    }
}
