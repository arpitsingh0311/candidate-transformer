package com.eightfold.transformer.validator;

import com.eightfold.transformer.model.CandidateProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.*;

/*
 * Validates a CandidateProfile against schema rules.
 *
 * On failure: invalid fields are set to null.
 * Validation errors are collected and returned - never thrown.
 */
public class ProfileValidator {

    private static final Pattern EMAIL_RE =
            Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern E164_RE  =
            Pattern.compile("^\\+[1-9]\\d{7,14}$");
    private static final Pattern DATE_RE  =
            Pattern.compile("^\\d{4}-\\d{2}$");
    private static final Pattern ISO_COUNTRY_RE =
            Pattern.compile("^[A-Z]{2}$");

    public static class ValidationResult {
        public boolean valid = true;
        public List<String> errors = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();

        void error(String msg)   { valid = false; errors.add(msg); }
        void warning(String msg) { warnings.add(msg); }
    }

    public ValidationResult validate(CandidateProfile p) {
        ValidationResult r = new ValidationResult();

        // candidate_id required
        if (blank(p.candidate_id)) r.error("candidate_id is blank");

        // emails
        List<String> validEmails = new ArrayList<>();
        for (String e : p.emails) {
            if (EMAIL_RE.matcher(e).matches()) validEmails.add(e);
            else r.warning("Invalid email removed: " + e);
        }
        p.emails = validEmails;

        // phones (E.164)
        List<String> validPhones = new ArrayList<>();
        for (String ph : p.phones) {
            if (E164_RE.matcher(ph).matches()) validPhones.add(ph);
            else r.warning("Non-E164 phone removed: " + ph);
        }
        p.phones = validPhones;

        // location country
        if (p.location != null && p.location.country != null) {
            if (!ISO_COUNTRY_RE.matcher(p.location.country).matches()) {
                r.warning("Invalid ISO country code: " + p.location.country + " — set to null");
                p.location.country = null;
            }
        }

        // years_experience: must be 0–70
        if (p.years_experience != null && (p.years_experience < 0 || p.years_experience > 70)) {
            r.warning("years_experience out of range: " + p.years_experience + " — set to null");
            p.years_experience = null;
        }

        // experience dates: must be YYYY-MM
        for (CandidateProfile.Experience e : p.experience) {
            if (e.start != null && !DATE_RE.matcher(e.start).matches()) {
                r.warning("Invalid start date: " + e.start);
                e.start = null;
            }
            if (e.end != null && !DATE_RE.matcher(e.end).matches()) {
                r.warning("Invalid end date: " + e.end);
                e.end = null;
            }
        }

        // overall_confidence: must in between 0.0 to 1.0
        if (p.overall_confidence != null && (p.overall_confidence < 0 || p.overall_confidence > 1)) {
            r.warning("overall_confidence out of range: " + p.overall_confidence + " — clamped");
            p.overall_confidence = Math.max(0.0, Math.min(1.0, p.overall_confidence));
        }

        return r;
    }

    private boolean blank(String s) { return s == null || s.isBlank(); }
}
