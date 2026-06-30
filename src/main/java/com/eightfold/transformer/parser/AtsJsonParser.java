package com.eightfold.transformer.parser;

import com.eightfold.transformer.model.CandidateProfile;
import com.eightfold.transformer.normalizer.PhoneNormalizer;
import com.eightfold.transformer.normalizer.SkillNormalizer;
import com.fasterxml.jackson.databind.*;

import java.io.*;
import java.util.*;

/*
 * Parses ATS JSON.
 *
 * ATS field names do NOT match our canonical schema - they vary by vendor.
 * Strategy: try a ranked list of known aliases for each canonical field.
 * Unknown fields are stored in provenance as "unmapped".
 *
 * Supports single-object or array-of-objects JSON.
 */
public class AtsJsonParser {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String SOURCE = "ats_json";

    @SuppressWarnings("unchecked")
    public List<CandidateProfile> parse(File file) throws IOException {
        List<CandidateProfile> profiles = new ArrayList<>();

        JsonNode root = MAPPER.readTree(file);
        List<JsonNode> nodes = new ArrayList<>();
        if (root.isArray()) root.forEach(nodes::add);
        else nodes.add(root);

        for (JsonNode node : nodes) {
            Map<String, Object> raw = MAPPER.convertValue(node, Map.class);
            CandidateProfile p = mapToProfile(raw);
            // Guard against non-candidate JSON files (e.g. an OutputConfig file
            // such as custom_config.json) sitting in the same input directory and
            // being misread as an ATS blob. If none of the alias keys matched
            // anything at all, this node isn't a candidate record - skip it
            // instead of emitting an empty/ghost profile.
            if (isCandidateData(p)) {
                profiles.add(p);
            }
        }
        return profiles;
    }

    // True if the mapped profile carries at least one piece of real candidate
    // signal. False means none of the known alias keys matched this node.
    private static boolean isCandidateData(CandidateProfile p) {
        return p.full_name != null
                || !p.emails.isEmpty()
                || !p.phones.isEmpty()
                || p.headline != null
                || !p.skills.isEmpty()
                || !p.experience.isEmpty()
                || !p.education.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private CandidateProfile mapToProfile(Map<String, Object> raw) {
        CandidateProfile p = new CandidateProfile();

        // full_name
        p.full_name = firstStr(raw, "full_name", "name", "candidate_name", "applicant_name");

        // emails
        String email = firstStr(raw, "email", "email_address", "primary_email", "contact_email");
        if (email != null) {
            p.emails.add(email.toLowerCase().trim());
            p.candidate_id = "email_" + email.toLowerCase().replaceAll("[^a-z0-9]", "_");
        }

        // phones
        String phone = firstStr(raw, "phone", "phone_number", "mobile", "contact_phone");
        if (phone != null) {
            String norm = PhoneNormalizer.toE164(phone);
            p.phones.add(norm != null ? norm : phone);
        }

        // headline / current title
        p.headline = firstStr(raw, "headline", "title", "current_title", "job_title", "position");

        // location
        String city    = firstStr(raw, "city", "location_city");
        String region  = firstStr(raw, "state", "region", "province");
        String country = firstStr(raw, "country", "country_code");
        if (city != null || region != null || country != null) {
            p.location = new CandidateProfile.Location(city, region, normaliseCountry(country));
        }

        // links
        String linkedin = firstStr(raw, "linkedin", "linkedin_url", "linkedin_profile");
        String github   = firstStr(raw, "github", "github_url", "github_profile");
        if (linkedin != null) p.links.linkedin = linkedin;
        if (github != null)   p.links.github   = github;

        // years_experience
        Object yoe = firstObj(raw, "years_experience", "experience_years", "total_experience");
        if (yoe instanceof Number) p.years_experience = ((Number) yoe).intValue();

        // skills - may be a list of strings or list of objects
        Object skillsRaw = firstObj(raw, "skills", "skill_list", "competencies");
        if (skillsRaw instanceof List) {
            for (Object s : (List<?>) skillsRaw) {
                String skillName = null;
                if (s instanceof String) skillName = (String) s;
                else if (s instanceof Map) skillName = firstStr((Map<String, Object>) s, "name", "skill", "label");
                if (skillName != null) {
                    p.skills.add(new CandidateProfile.Skill(
                            SkillNormalizer.canonical(skillName), 0.80, SOURCE));
                }
            }
        }

        // experience
        Object expRaw = firstObj(raw, "experience", "work_history", "jobs");
        if (expRaw instanceof List) {
            for (Object e : (List<?>) expRaw) {
                if (e instanceof Map) {
                    Map<String, Object> em = (Map<String, Object>) e;
                    CandidateProfile.Experience exp = new CandidateProfile.Experience();
                    exp.company = firstStr(em, "company", "employer", "organization");
                    exp.title   = firstStr(em, "title", "position", "role");
                    exp.start   = normaliseDate(firstStr(em, "start", "start_date", "from"));
                    exp.end     = normaliseDate(firstStr(em, "end", "end_date", "to"));
                    exp.summary = firstStr(em, "summary", "description", "responsibilities");
                    p.experience.add(exp);
                }
            }
        }

        // education
        Object eduRaw = firstObj(raw, "education", "education_history", "academic_background");
        if (eduRaw instanceof List) {
            for (Object e : (List<?>) eduRaw) {
                if (e instanceof Map) {
                    Map<String, Object> em = (Map<String, Object>) e;
                    CandidateProfile.Education edu = new CandidateProfile.Education();
                    edu.institution = firstStr(em, "institution", "school", "university", "college");
                    edu.degree      = firstStr(em, "degree", "qualification", "award");
                    edu.field       = firstStr(em, "field", "major", "subject", "field_of_study");
                    edu.year        = firstStr(em, "year", "graduation_year", "end_year");
                    p.education.add(edu);
                }
            }
        }

        p.addProvenance("*", SOURCE, "alias_mapping");
        p.overall_confidence = 0.80;
        return p;
    }

    private static String firstStr(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v instanceof String && !((String) v).isBlank()) return ((String) v).trim();
        }
        return null;
    }

    private static Object firstObj(Map<String, Object> m, String... keys) {
        for (String k : keys) { if (m.containsKey(k)) return m.get(k); }
        return null;
    }

    // Basic date normalisation → YYYY-MM
    public static String normaliseDate(String raw) {
        if (raw == null) return null;
        raw = raw.trim();
        // Already YYYY-MM or YYYY-MM-DD
        if (raw.matches("\\d{4}-\\d{2}.*")) return raw.substring(0, 7);
        // MM/YYYY
        if (raw.matches("\\d{2}[/-]\\d{4}")) {
            String[] p = raw.split("[/-]");
            return p[1] + "-" + p[0];
        }
        // Just year
        if (raw.matches("\\d{4}")) return raw + "-01";
        return raw;
    }

    private static String normaliseCountry(String raw) {
        if (raw == null) return null;
        if (raw.equalsIgnoreCase("india")) return "IN";
        if (raw.equalsIgnoreCase("united states") || raw.equalsIgnoreCase("us") || raw.equalsIgnoreCase("usa")) return "US";
        if (raw.equalsIgnoreCase("united kingdom") || raw.equalsIgnoreCase("uk")) return "GB";
        return raw.toUpperCase();
    }
}