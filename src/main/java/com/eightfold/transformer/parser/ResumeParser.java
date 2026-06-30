package com.eightfold.transformer.parser;

import com.eightfold.transformer.model.CandidateProfile;
import com.eightfold.transformer.normalizer.PhoneNormalizer;
import com.eightfold.transformer.normalizer.SkillNormalizer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/*
 * Extracts text from PDF or DOCX resumes and maps it to the canonical schema.
 *
 * Strategy: regex heuristics + section detection.
 * Confidence is intentionally lower (0.60) since parsing is inference-based.
 */
public class ResumeParser {

    private static final String SOURCE = "resume_file";

    // Known tech skills for lightweight NER
    private static final Set<String> KNOWN_SKILLS = new HashSet<>(Arrays.asList(
        "java","python","javascript","typescript","go","rust","c++","c#","kotlin","swift",
        "spring","django","react","angular","vue","node.js","nodejs",
        "aws","azure","gcp","docker","kubernetes","terraform","git","ci/cd",
        "sql","postgresql","mysql","mongodb","redis","elasticsearch",
        "machine learning","deep learning","nlp","computer vision",
        "rest","graphql","grpc","kafka","spark","hadoop"
    ));

    private static final Pattern EMAIL_RE =
            Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_RE =
            Pattern.compile("[+]?[\\d][\\d .\\-()]{7,}[\\d]");
    private static final Pattern LINKEDIN_RE =
            Pattern.compile("linkedin\\.com/in/[A-Za-z0-9_\\-]+");
    private static final Pattern GITHUB_RE =
            Pattern.compile("github\\.com/[A-Za-z0-9_\\-]+");
    private static final Pattern YEAR_RANGE_RE =
            Pattern.compile("(\\d{4})\\s*[-–—]\\s*(\\d{4}|present|current)", Pattern.CASE_INSENSITIVE);

    public CandidateProfile parse(File file) throws IOException {
        String text = extractText(file);
        return parseText(text, file.getName());
    }

    public CandidateProfile parseText(String text, String filename) {
        CandidateProfile p = new CandidateProfile();
        p.candidate_id = "resume_" + filename.replaceAll("[^a-zA-Z0-9]", "_");

        String[] lines = text.split("\\r?\\n");

        // email
        Matcher em = EMAIL_RE.matcher(text);
        while (em.find()) {
            String e = em.group().toLowerCase();
            if (!p.emails.contains(e)) p.emails.add(e);
        }

        // phone
        Matcher pm = PHONE_RE.matcher(text);
        while (pm.find()) {
            String raw = pm.group();
            String norm = PhoneNormalizer.toE164(raw);
            String ph = norm != null ? norm : raw;
            if (!p.phones.contains(ph)) p.phones.add(ph);
        }

        // links
        Matcher lim = LINKEDIN_RE.matcher(text);
        if (lim.find()) p.links.linkedin = "https://" + lim.group();
        Matcher ghm = GITHUB_RE.matcher(text);
        if (ghm.find()) p.links.github = "https://" + ghm.group();

        // name (heuristic: first non-blank line, title-cased, ≤5 words)
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.split("\\s+").length <= 5 && line.matches("[A-Z][a-zA-Z .\\-']+")) {
                p.full_name = line;
                break;
            }
        }

        // headline (first short line after name that looks like a job title)
        boolean nameSeen = false;
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (!nameSeen && p.full_name != null && line.equals(p.full_name)) { nameSeen = true; continue; }
            if (nameSeen && line.length() > 5 && line.length() < 80 && !EMAIL_RE.matcher(line).find()) {
                p.headline = line;
                break;
            }
        }

        // skills
        String lower = text.toLowerCase();
        for (String skill : KNOWN_SKILLS) {
            if (lower.contains(skill)) {
                p.skills.add(new CandidateProfile.Skill(
                        SkillNormalizer.canonical(skill), 0.60, SOURCE));
            }
        }

        // years_experience
        Matcher yrm = YEAR_RANGE_RE.matcher(text);
        Set<String> ranges = new HashSet<>();
        while (yrm.find()) ranges.add(yrm.group());
        if (!ranges.isEmpty()) p.years_experience = ranges.size();

        p.addProvenance("*", SOURCE, "regex_heuristic");
        p.overall_confidence = 0.60;
        return p;
    }

    // text extraction
    private String extractText(File file) throws IOException {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".pdf"))  return extractPdf(file);
        if (name.endsWith(".docx")) return extractDocx(file);
        return new String(java.nio.file.Files.readAllBytes(file.toPath()));
    }

    private String extractPdf(File file) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private String extractDocx(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument doc = new XWPFDocument(fis);
             XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            return ex.getText();
        }
    }
}
