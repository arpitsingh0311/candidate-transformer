package com.eightfold.transformer;

import com.eightfold.transformer.merger.ProfileMerger;
import com.eightfold.transformer.model.CandidateProfile;
import com.eightfold.transformer.normalizer.PhoneNormalizer;
import com.eightfold.transformer.normalizer.SkillNormalizer;
import com.eightfold.transformer.parser.*;
import com.eightfold.transformer.validator.ProfileValidator;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TransformerTest {

    // ── PhoneNormalizer ────────────────────────────────────────────

    @Test void phone_us_10digit() {
        assertEquals("+12125551234", PhoneNormalizer.toE164("212-555-1234"));
    }

    @Test void phone_india_10digit() {
        assertEquals("+919876543210", PhoneNormalizer.toE164("9876543210"));
    }

    @Test void phone_already_e164() {
        assertEquals("+447911123456", PhoneNormalizer.toE164("+447911123456"));
    }

    @Test void phone_too_short_returns_null() {
        assertNull(PhoneNormalizer.toE164("1234"));
    }

    @Test void phone_with_spaces_and_dashes() {
        String result = PhoneNormalizer.toE164("+91 98765 43210");
        assertEquals("+919876543210", result);
    }

    // ── SkillNormalizer ────────────────────────────────────────────

    @Test void skill_js_alias() {
        assertEquals("JavaScript", SkillNormalizer.canonical("js"));
        assertEquals("JavaScript", SkillNormalizer.canonical("JS"));
        assertEquals("JavaScript", SkillNormalizer.canonical("javascript"));
    }

    @Test void skill_nodejs_aliases() {
        assertEquals("Node.js", SkillNormalizer.canonical("nodejs"));
        assertEquals("Node.js", SkillNormalizer.canonical("node.js"));
    }

    @Test void skill_kubernetes_alias() {
        assertEquals("Kubernetes", SkillNormalizer.canonical("k8s"));
    }

    @Test void skill_unknown_returns_titlecase() {
        assertEquals("Foobar", SkillNormalizer.canonical("foobar"));
    }

    // ── RecruiterNotesParser ───────────────────────────────────────

    @Test void notes_parser_key_value() {
        RecruiterNotesParser parser = new RecruiterNotesParser();
        String text = "Name: Jane Doe\nEmail: jane@example.com\nYears Experience: 5\nSkills: Java, Python, AWS";
        CandidateProfile p = parser.parseText(text, "test.txt");
        assertEquals("Jane Doe", p.full_name);
        assertEquals(1, p.emails.size());
        assertEquals("jane@example.com", p.emails.get(0));
        assertEquals(5, p.years_experience);
        assertEquals(3, p.skills.size());
    }

    @Test void notes_parser_handles_missing_keys() {
        RecruiterNotesParser parser = new RecruiterNotesParser();
        String text = "Some random text with no structure";
        CandidateProfile p = parser.parseText(text, "random.txt");
        assertNull(p.full_name);
        assertTrue(p.emails.isEmpty());
    }

    // ── ResumeParser ──────────────────────────────────────────────

    @Test void resume_parser_extracts_email() {
        ResumeParser parser = new ResumeParser();
        String text = "John Smith\nSoftware Engineer\njohn.smith@gmail.com\n+1-415-555-0101\nSkills: Java, Python, Docker";
        CandidateProfile p = parser.parseText(text, "resume.pdf");
        assertFalse(p.emails.isEmpty());
        assertEquals("john.smith@gmail.com", p.emails.get(0));
    }

    @Test void resume_parser_extracts_skills() {
        ResumeParser parser = new ResumeParser();
        String text = "Experience with java, python, and aws cloud infrastructure";
        CandidateProfile p = parser.parseText(text, "resume.pdf");
        assertTrue(p.skills.stream().anyMatch(s -> s.name.equalsIgnoreCase("Java")));
        assertTrue(p.skills.stream().anyMatch(s -> s.name.equalsIgnoreCase("Python")));
        assertTrue(p.skills.stream().anyMatch(s -> s.name.equalsIgnoreCase("AWS")));
    }

    // ── AtsJsonParser ─────────────────────────────────────────────

    @Test void ats_date_normalisation() {
        assertEquals("2021-03", AtsJsonParser.normaliseDate("2021-03-15"));
        assertEquals("2021-03", AtsJsonParser.normaliseDate("2021-03"));
        assertEquals("2021-01", AtsJsonParser.normaliseDate("2021"));
        assertEquals("2019-06", AtsJsonParser.normaliseDate("06/2019"));
        assertNull(AtsJsonParser.normaliseDate(null));
    }

    // ── GitHubProfileParser ───────────────────────────────────────

    @Test void github_username_extraction() {
        assertEquals("torvalds", GitHubProfileParser.extractUsername("https://github.com/torvalds"));
        assertEquals("torvalds", GitHubProfileParser.extractUsername("github.com/torvalds"));
        assertEquals("torvalds", GitHubProfileParser.extractUsername("torvalds"));
        assertNull(GitHubProfileParser.extractUsername("https://example.com/foo"));
    }

    // ── ProfileMerger ─────────────────────────────────────────────

    @Test void merger_deduplicates_by_email() {
        CandidateProfile p1 = profile("Alice", "alice@x.com", null, 0.85);
        CandidateProfile p2 = profile("Alice Smith", "alice@x.com", null, 0.70);
        ProfileMerger merger = new ProfileMerger();
        List<CandidateProfile> merged = merger.merge(Arrays.asList(p1, p2));
        assertEquals(1, merged.size());
        // Primary (higher conf) wins for full_name
        assertEquals("Alice", merged.get(0).full_name);
    }

    @Test void merger_keeps_distinct_candidates() {
        CandidateProfile p1 = profile("Alice", "alice@x.com", null, 0.85);
        CandidateProfile p2 = profile("Bob",   "bob@x.com",   null, 0.80);
        ProfileMerger merger = new ProfileMerger();
        List<CandidateProfile> merged = merger.merge(Arrays.asList(p1, p2));
        assertEquals(2, merged.size());
    }

    @Test void merger_unions_skills() {
        CandidateProfile p1 = profile("Alice", "alice@x.com", null, 0.85);
        p1.skills.add(new CandidateProfile.Skill("Java", 0.9, "csv"));
        CandidateProfile p2 = profile("Alice", "alice@x.com", null, 0.70);
        p2.skills.add(new CandidateProfile.Skill("Python", 0.8, "notes"));
        ProfileMerger merger = new ProfileMerger();
        List<CandidateProfile> merged = merger.merge(Arrays.asList(p1, p2));
        assertEquals(2, merged.get(0).skills.size());
    }

    // ── ProfileValidator ──────────────────────────────────────────

    @Test void validator_removes_invalid_email() {
        CandidateProfile p = new CandidateProfile();
        p.candidate_id = "test_1";
        p.emails.add("not-an-email");
        p.emails.add("valid@example.com");
        ProfileValidator v = new ProfileValidator();
        ProfileValidator.ValidationResult r = v.validate(p);
        assertEquals(1, p.emails.size());
        assertEquals("valid@example.com", p.emails.get(0));
        assertFalse(r.warnings.isEmpty());
    }

    @Test void validator_removes_non_e164_phone() {
        CandidateProfile p = new CandidateProfile();
        p.candidate_id = "test_1";
        p.phones.add("+919876543210"); // valid
        p.phones.add("123");            // invalid
        ProfileValidator v = new ProfileValidator();
        v.validate(p);
        assertEquals(1, p.phones.size());
    }

    @Test void validator_clamps_confidence() {
        CandidateProfile p = new CandidateProfile();
        p.candidate_id = "test_1";
        p.overall_confidence = 1.5;
        new ProfileValidator().validate(p);
        assertEquals(1.0, p.overall_confidence);
    }

    // ── Edge Cases ────────────────────────────────────────────────

    @Test void pipeline_handles_empty_directory() throws Exception {
        java.io.File tmp = java.nio.file.Files.createTempDirectory("empty").toFile();
        TransformerPipeline pipeline = new TransformerPipeline();
        List<?> results = pipeline.run(tmp, null);
        assertTrue(results.isEmpty());
        tmp.delete();
    }

    // ── helpers ────────────────────────────────────────────────────

    private CandidateProfile profile(String name, String email, String phone, double confidence) {
        CandidateProfile p = new CandidateProfile();
        p.full_name = name;
        p.candidate_id = "email_" + email.replace("@", "_at_").replace(".", "_");
        if (email != null) p.emails.add(email);
        if (phone != null) p.phones.add(phone);
        p.overall_confidence = confidence;
        return p;
    }
}
