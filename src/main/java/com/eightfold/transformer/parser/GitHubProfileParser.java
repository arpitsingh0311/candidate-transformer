package com.eightfold.transformer.parser;

import com.eightfold.transformer.model.CandidateProfile;
import com.eightfold.transformer.normalizer.SkillNormalizer;
import com.fasterxml.jackson.databind.*;
import okhttp3.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * Fetches a GitHub profile via the public REST API and maps it to our
 * canonical schema.
 *
 * Endpoints used:
 *   GET /users/{username}          → name, bio, location, blog
 *   GET /users/{username}/repos    → languages (treated as skills)
 *
 * No auth token required for public profiles (60 req/hr rate limit).
 * Pass a GITHUB_TOKEN env-var to raise to 5 000 req/hr.
 */
public class GitHubProfileParser {

    private static final String SOURCE  = "github_api";
    private static final String BASE    = "https://api.github.com";
    private static final ObjectMapper   MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final OkHttpClient http;
    private final String token; // may be null

    public GitHubProfileParser() {
        this.token = System.getenv("GITHUB_TOKEN");
        this.http  = new OkHttpClient();
    }

    //Parse from a GitHub profile URL or username string
    public CandidateProfile parse(String urlOrUsername) throws IOException {
        String username = extractUsername(urlOrUsername);
        if (username == null) throw new IOException("Cannot extract GitHub username from: " + urlOrUsername);

        JsonNode user = fetch("/users/" + username);
        CandidateProfile p = new CandidateProfile();

        p.full_name       = text(user, "name");
        p.headline        = text(user, "bio");
        p.links.github    = "https://github.com/" + username;
        p.candidate_id    = "github_" + username;

        // emails
        String email = text(user, "email");
        if (email != null) p.emails.add(email.toLowerCase());

        // location
        String loc = text(user, "location");
        if (loc != null) p.location = new CandidateProfile.Location(loc, null, null);

        // blog / portfolio
        String blog = text(user, "blog");
        if (blog != null && !blog.isBlank()) {
            if (blog.contains("linkedin.com")) p.links.linkedin = blog;
            else                               p.links.portfolio = blog;
        }

        // repos -> languages -> skills
        try {
            JsonNode repos = fetch("/users/" + username + "/repos?per_page=100&sort=pushed");
            Set<String> langs = new LinkedHashSet<>();
            for (JsonNode repo : repos) {
                String lang = text(repo, "language");
                if (lang != null) langs.add(lang);
            }
            for (String lang : langs) {
                p.skills.add(new CandidateProfile.Skill(
                        SkillNormalizer.canonical(lang), 0.70, SOURCE));
            }
        } catch (IOException ignored) { /* non-fatal */ }

        p.addProvenance("*", SOURCE, "api_fetch");
        p.overall_confidence = 0.70; // unstructured / inferred
        return p;
    }

    private JsonNode fetch(String path) throws IOException {
        Request.Builder rb = new Request.Builder()
                .url(BASE + path)
                .header("Accept", "application/vnd.github+json");
        if (token != null) rb.header("Authorization", "Bearer " + token);
        try (Response resp = http.newCall(rb.build()).execute()) {
            if (!resp.isSuccessful()) throw new IOException("GitHub API " + resp.code() + " for " + path);
            return MAPPER.readTree(resp.body().string());
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return (n == null || n.isNull()) ? null : n.asText().trim();
    }

    public static String extractUsername(String raw) {
        if (raw == null) return null;
        raw = raw.trim();
        Pattern p = Pattern.compile("github\\.com/([A-Za-z0-9_-]+)");
        Matcher m = p.matcher(raw);
        if (m.find()) return m.group(1);
        // bare username (no slashes, no dots)
        if (raw.matches("[A-Za-z0-9_-]+")) return raw;
        return null;
    }
}
