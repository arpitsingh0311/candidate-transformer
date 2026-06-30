package com.eightfold.transformer;

import com.eightfold.transformer.config.OutputConfig;
import com.eightfold.transformer.merger.ProfileMerger;
import com.eightfold.transformer.model.CandidateProfile;
import com.eightfold.transformer.output.OutputProjector;
import com.eightfold.transformer.parser.*;
import com.eightfold.transformer.validator.ProfileValidator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 *
 *   Multi-Source Candidate Data Transformer — Pipeline
 *
 * Pipeline stages:
 *   detect -> parse -> normalise -> merge -> validate -> project -> emit
 *
 * Usage:
 *   java -jar transformer.jar <input-dir> [--config config.json] [--output out.json]
 */
public class TransformerPipeline {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    // Parsers
    private final RecruiterCsvParser    csvParser    = new RecruiterCsvParser();
    private final AtsJsonParser         atsParser    = new AtsJsonParser();
    private final ResumeParser          resumeParser = new ResumeParser();
    private final RecruiterNotesParser  notesParser  = new RecruiterNotesParser();
    private final GitHubProfileParser   githubParser = new GitHubProfileParser();

    // Pipeline stages
    private final ProfileMerger    merger    = new ProfileMerger();
    private final ProfileValidator validator = new ProfileValidator();
    private final OutputProjector  projector = new OutputProjector();

    /*
     * Run the full pipeline on an input directory.
     *
     * @param inputDir   directory containing source files
     * @param config     runtime output config (null = use default schema)
     * @return list of projected candidate JSON objects
     */
    public List<ObjectNode> run(File inputDir, OutputConfig config) throws IOException {
        // STAGE 1: Detect & Parse
        List<CandidateProfile> allProfiles = new ArrayList<>();
        File[] files = inputDir.listFiles();
        if (files == null) throw new IOException("Input directory not found: " + inputDir);

        for (File file : files) {
            String name  = file.getName().toLowerCase();
            List<CandidateProfile> parsed = new ArrayList<>();

            try {
                if (name.endsWith(".csv")) {
                    System.out.println("[CSV]    Parsing: " + file.getName());
                    parsed = csvParser.parse(file);

                } else if (name.endsWith(".json")) {
                    System.out.println("[JSON]   Parsing: " + file.getName());
                    // Check if it looks like an ATS blob or a GitHub URL file
                    String content = Files.readString(file.toPath());
                    if (content.contains("\"github_url\"") || content.contains("github.com")) {
                        // Could have embedded GitHub URLs — handle below
                    }
                    parsed = atsParser.parse(file);

                } else if (name.endsWith(".pdf") || name.endsWith(".docx")) {
                    System.out.println("[RESUME] Parsing: " + file.getName());
                    parsed.add(resumeParser.parse(file));

                } else if (name.endsWith(".txt")) {
                    System.out.println("[NOTES]  Parsing: " + file.getName());
                    parsed.add(notesParser.parse(file));

                } else if (name.equals("github_urls.txt") || name.contains("github")) {
                    // Special: file of GitHub URLs, one per line
                    System.out.println("[GITHUB] Fetching URLs from: " + file.getName());
                    for (String line : Files.readAllLines(file.toPath())) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            try { parsed.add(githubParser.parse(line)); }
                            catch (IOException e) { System.err.println("  GitHub fetch failed for " + line + ": " + e.getMessage()); }
                        }
                    }
                } else {
                    System.out.println("[SKIP]   Unknown file type: " + file.getName());
                }
            } catch (IOException e) {
                System.err.println("[ERROR]  Failed to parse " + file.getName() + ": " + e.getMessage());
            }

            allProfiles.addAll(parsed);
            System.out.println("         → " + parsed.size() + " profile(s) extracted");
        }

        System.out.println("\n[MERGE]  Total raw profiles: " + allProfiles.size());

        // STAGE 2: Merge
        List<CandidateProfile> merged = merger.merge(allProfiles);
        System.out.println("[MERGE]  Merged into: " + merged.size() + " unique candidate(s)\n");

        // STAGE 3: Validate
        List<CandidateProfile> validated = new ArrayList<>();
        for (CandidateProfile p : merged) {
            ProfileValidator.ValidationResult vr = validator.validate(p);
            if (!vr.errors.isEmpty()) {
                System.out.println("[VALID]  Errors for " + p.candidate_id + ": " + vr.errors + " — dropping record");
                continue; // invalid record (e.g. blank candidate_id) - exclude from output
            }
            if (!vr.warnings.isEmpty()) {
                System.out.println("[VALID]  Warnings for " + p.candidate_id + ": " + vr.warnings);
            }
            validated.add(p);
        }

        // STAGE 4: Project / Output
        List<ObjectNode> results = new ArrayList<>();

        if (config == null) {
            // Default schema: emit full canonical record
            for (CandidateProfile p : validated) {
                results.add(MAPPER.valueToTree(p));
            }
        } else {
            // Custom config projection
            for (CandidateProfile p : validated) {
                try {
                    results.add(projector.project(p, config));
                } catch (IllegalArgumentException e) {
                    System.err.println("[OUTPUT] Projection error for " + p.candidate_id + ": " + e.getMessage());
                }
            }
        }

        return results;
    }

    /* Write results to a JSON file */
    public void writeOutput(List<ObjectNode> results, File outputFile) throws IOException {
        MAPPER.writeValue(outputFile, results);
        System.out.println("[OUTPUT] Written to: " + outputFile.getAbsolutePath());
    }
}