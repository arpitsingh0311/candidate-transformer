package com.eightfold.transformer.parser;

import com.eightfold.transformer.model.CandidateProfile;
import com.eightfold.transformer.normalizer.PhoneNormalizer;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.*;
import java.util.*;

/*
 * Parses Recruiter CSV exports.
 * Expected headers (case-insensitive, order-independent):
 *   name, email, phone, current_company, title
 *
 * Handles: missing columns, blank cells, duplicate rows, quoted values.
 */
public class RecruiterCsvParser {

    private static final String SOURCE = "recruiter_csv";

    /*
     * Parse a CSV file and return one CandidateProfile per unique email.
     * If two rows share the same email the later row is ignored (first-write wins).
     */
    public List<CandidateProfile> parse(File file) throws IOException {
        List<CandidateProfile> profiles = new ArrayList<>();
        Set<String> seenEmails = new HashSet<>();

        try (CSVReader reader = new CSVReader(new FileReader(file))) {
            List<String[]> rows = reader.readAll();
            if (rows.isEmpty()) return profiles;

            // HEADER MAPPING
            String[] headers = rows.get(0);
            Map<String, Integer> colIndex = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                colIndex.put(headers[i].trim().toLowerCase(), i);
            }

            // ROWS
            for (int rowNum = 1; rowNum < rows.size(); rowNum++) {
                String[] row = rows.get(rowNum);
                if (isBlankRow(row)) continue;

                CandidateProfile p = new CandidateProfile();

                // full_name
                String name = get(row, colIndex, "name");
                if (name == null) name = get(row, colIndex, "full_name");
                p.full_name = name;

                // email
                String email = get(row, colIndex, "email");
                if (email != null) email = email.toLowerCase().trim();
                if (email == null || email.isEmpty()) {
                    // generate deterministic id from name+row
                    p.candidate_id = "csv_row_" + rowNum;
                } else {
                    if (seenEmails.contains(email)) continue; // dedup
                    seenEmails.add(email);
                    p.emails.add(email);
                    p.candidate_id = "email_" + email.replaceAll("[^a-z0-9]", "_");
                }

                // phones
                String phone = get(row, colIndex, "phone");
                if (phone != null && !phone.isBlank()) {
                    String normalised = PhoneNormalizer.toE164(phone);
                    p.phones.add(normalised != null ? normalised : phone.trim());
                }

                // current company + title → experience
                String company = get(row, colIndex, "current_company");
                String title   = get(row, colIndex, "title");
                if (company != null || title != null) {
                    CandidateProfile.Experience exp = new CandidateProfile.Experience();
                    exp.company = company;
                    exp.title   = title;
                    p.experience.add(exp);
                }

                // provenance
                p.addProvenance("*", SOURCE, "direct_parse");
                p.overall_confidence = 0.85; // structured source → high confidence

                profiles.add(p);
            }
        } catch (CsvException e) {
            throw new IOException("CSV parse error: " + e.getMessage(), e);
        }

        return profiles;
    }

    // helper functions

    private static String get(String[] row, Map<String, Integer> idx, String col) {
        Integer i = idx.get(col);
        if (i == null || i >= row.length) return null;
        String v = row[i].trim();
        return v.isEmpty() ? null : v;
    }

    private static boolean isBlankRow(String[] row) {
        for (String cell : row) if (cell != null && !cell.isBlank()) return false;
        return true;
    }
}
