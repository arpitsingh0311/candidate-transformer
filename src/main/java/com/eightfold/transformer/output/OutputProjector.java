package com.eightfold.transformer.output;

import com.eightfold.transformer.config.OutputConfig;
import com.eightfold.transformer.model.CandidateProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;

import java.util.*;
import java.util.regex.*;

/*
 * Applies a runtime OutputConfig to a canonical CandidateProfile,
 * producing a reshaped JSON object for downstream consumption.
 *
 * Supports:
 *  - Field selection & renaming (path / from)
 *  - Per-field normalisation (E164, canonical)
 *  - include_confidence toggle
 *  - on_missing: null | omit | error
 */
public class OutputProjector {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /*
     * Project the canonical profile to the shape defined by config.
     * Returns a JsonNode so callers can serialise however they like.
     *
     * @throws IllegalArgumentException if on_missing=error and a required field is absent
     */
    public ObjectNode project(CandidateProfile profile, OutputConfig config) {
        ObjectNode out = MAPPER.createObjectNode();

        // Flatten the canonical profile to a dot-path map for easy lookup
        Map<String, Object> flat = flatten(profile);

        for (OutputConfig.FieldSpec spec : config.fields) {
            String sourcePath = spec.from != null ? spec.from : spec.path;
            Object rawValue   = resolve(flat, sourcePath);

            // Apply normalisation
            rawValue = applyNormalize(rawValue, spec.normalize);

            if (rawValue == null || isBlank(rawValue)) {
                switch (config.on_missing) {
                    case "omit"  -> { /* skip */ continue; }
                    case "error" -> {
                        if (spec.required)
                            throw new IllegalArgumentException("Required field missing: " + spec.path);
                    }
                    default      -> out.putNull(spec.path);
                }
                if (rawValue == null) { out.putNull(spec.path); continue; }
            } else {
                putValue(out, spec.path, rawValue);
            }
        }

        // Confidence toggle
        if (config.include_confidence && profile.overall_confidence != null) {
            out.put("overall_confidence", profile.overall_confidence);
        }

        return out;
    }

    // flat map of canonical profile

    private Map<String, Object> flatten(CandidateProfile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("candidate_id",     p.candidate_id);
        m.put("full_name",        p.full_name);
        m.put("headline",         p.headline);
        m.put("years_experience", p.years_experience);

        // emails[0], emails[1], …
        putList(m, "emails", p.emails);
        m.put("primary_email", p.emails.isEmpty() ? null : p.emails.get(0));

        // phones[0], …
        putList(m, "phones", p.phones);
        m.put("phone", p.phones.isEmpty() ? null : p.phones.get(0));

        // location
        if (p.location != null) {
            m.put("location.city",    p.location.city);
            m.put("location.region",  p.location.region);
            m.put("location.country", p.location.country);
        }

        // links
        m.put("links.linkedin",  p.links.linkedin);
        m.put("links.github",    p.links.github);
        m.put("links.portfolio", p.links.portfolio);

        // skills (flat list of names)
        List<String> skillNames = new ArrayList<>();
        for (CandidateProfile.Skill s : p.skills) skillNames.add(s.name);
        m.put("skills",          skillNames);
        if (!skillNames.isEmpty()) m.put("skills[0].name", skillNames.get(0));

        // experience
        for (int i = 0; i < p.experience.size(); i++) {
            CandidateProfile.Experience e = p.experience.get(i);
            m.put("experience[" + i + "].company", e.company);
            m.put("experience[" + i + "].title",   e.title);
            m.put("experience[" + i + "].start",   e.start);
            m.put("experience[" + i + "].end",     e.end);
        }

        m.put("overall_confidence", p.overall_confidence);
        return m;
    }

    private void putList(Map<String, Object> m, String prefix, List<String> list) {
        m.put(prefix, list);
        for (int i = 0; i < list.size(); i++) m.put(prefix + "[" + i + "]", list.get(i));
    }

    // path resolution

    /*
     * Resolve a source path like "emails[0]" or "skills[0].name"
     * from the flattened map.
     */
    private Object resolve(Map<String, Object> flat, String path) {
        if (flat.containsKey(path)) return flat.get(path);
        // try normalised key
        String norm = path.toLowerCase().replace(" ", "_");
        return flat.getOrDefault(norm, null);
    }

    // normalisation

    private Object applyNormalize(Object value, String mode) {
        if (mode == null || value == null) return value;
        switch (mode.toUpperCase()) {
            case "E164" -> {
                if (value instanceof String s)
                    return com.eightfold.transformer.normalizer.PhoneNormalizer.toE164(s);
            }
            case "CANONICAL" -> {
                if (value instanceof String s)
                    return com.eightfold.transformer.normalizer.SkillNormalizer.canonical(s);
                if (value instanceof List<?> lst) {
                    List<String> out = new ArrayList<>();
                    for (Object o : lst) if (o instanceof String s) out.add(
                            com.eightfold.transformer.normalizer.SkillNormalizer.canonical(s));
                    return out;
                }
            }
        }
        return value;
    }

    // JSON node helpers

    @SuppressWarnings("unchecked")
    private void putValue(ObjectNode node, String key, Object value) {
        if (value instanceof String s)          node.put(key, s);
        else if (value instanceof Integer i)    node.put(key, i);
        else if (value instanceof Double d)     node.put(key, d);
        else if (value instanceof Long l)       node.put(key, l);
        else if (value instanceof List<?> lst) {
            ArrayNode arr = node.putArray(key);
            for (Object o : lst) {
                if (o instanceof String s) arr.add(s);
                else arr.add(o != null ? o.toString() : (String) null);
            }
        } else if (value != null) node.put(key, value.toString());
    }

    private boolean isBlank(Object v) {
        if (v instanceof String s) return s.isBlank();
        if (v instanceof List<?> l) return l.isEmpty();
        return false;
    }
}
