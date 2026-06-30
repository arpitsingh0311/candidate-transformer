package com.eightfold.transformer.parser;

import com.eightfold.transformer.model.CandidateProfile;
import com.eightfold.transformer.normalizer.SkillNormalizer;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.*;

/*
 * Parses free-text recruiter notes (.txt files).
 *
 * Format is unstructured — we use keyword extraction and named entity
 * heuristics. Confidence is intentionally low (0.50).
 *
 * Handles key-value patterns like:
 *   Name: John Doe
 *   Years experience: 5
 *   Skills: Java, Python, AWS
 */
public class RecruiterNotesParser {

    private static final String SOURCE = "recruiter_notes";

    private static final Pattern KV_RE =
            Pattern.compile("^([A-Za-z _/]+):\\s*(.+)$");
    private static final Pattern EMAIL_RE =
            Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");
    private static final Pattern YEARS_RE =
            Pattern.compile("(\\d+)\\s*(?:\\+)?\\s*years?", Pattern.CASE_INSENSITIVE);

    public CandidateProfile parse(File file) throws IOException {
        String text = Files.readString(file.toPath());
        return parseText(text, file.getName());
    }

    public CandidateProfile parseText(String text, String filename) {
        CandidateProfile p = new CandidateProfile();
        p.candidate_id = "notes_" + filename.replaceAll("[^a-zA-Z0-9]", "_");

        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isBlank()) continue;

            Matcher kv = KV_RE.matcher(line);
            if (kv.matches()) {
                String key = kv.group(1).toLowerCase().trim();
                String val = kv.group(2).trim();

                if (matches(key, "name", "candidate", "candidate name")) {
                    p.full_name = val;
                } else if (matches(key, "email", "e-mail", "email address")) {
                    p.emails.add(val.toLowerCase());
                } else if (matches(key, "phone", "mobile", "contact")) {
                    p.phones.add(val);
                } else if (matches(key, "skills", "tech skills", "technologies")) {
                    for (String s : val.split("[,;]+")) {
                        String norm = SkillNormalizer.canonical(s.trim());
                        if (!norm.isBlank())
                            p.skills.add(new CandidateProfile.Skill(norm, 0.55, SOURCE));
                    }
                } else if (matches(key, "years experience", "years of experience", "experience years", "yoe")) {
                    try { p.years_experience = Integer.parseInt(val.replaceAll("\\D", "")); } catch (NumberFormatException ignored) {}
                } else if (matches(key, "location", "city", "city/region")) {
                    p.location = new CandidateProfile.Location(val, null, null);
                } else if (matches(key, "headline", "current title", "title", "role")) {
                    p.headline = val;
                } else if (matches(key, "linkedin")) {
                    p.links.linkedin = val;
                } else if (matches(key, "github")) {
                    p.links.github = val;
                } else if (matches(key, "notes", "summary", "comments")) {
                    // store as headline if empty
                    if (p.headline == null) p.headline = val;
                }
            } else {
                // free-text line - try to extract emails and year-ranges
                Matcher em = EMAIL_RE.matcher(line);
                while (em.find()) {
                    String e = em.group().toLowerCase();
                    if (!p.emails.contains(e)) p.emails.add(e);
                }
                Matcher ym = YEARS_RE.matcher(line);
                if (ym.find() && p.years_experience == null) {
                    try { p.years_experience = Integer.parseInt(ym.group(1)); } catch (NumberFormatException ignored) {}
                }
            }
        }

        if (!p.emails.isEmpty()) {
            p.candidate_id = "email_" + p.emails.get(0).replaceAll("[^a-z0-9]", "_");
        }

        p.addProvenance("*", SOURCE, "keyword_extraction");
        p.overall_confidence = 0.50;
        return p;
    }

    private static boolean matches(String key, String... options) {
        for (String o : options) if (key.equals(o)) return true;
        return false;
    }
}
