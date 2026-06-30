package com.eightfold.transformer.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.*;

/*
 * Canonical candidate profile — the single authoritative record
 * output by the transformer pipeline.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CandidateProfile {

    public String candidate_id;
    public String full_name;
    public List<String> emails = new ArrayList<>();
    public List<String> phones = new ArrayList<>();
    public Location location;
    public Links links = new Links();
    public String headline;
    public Integer years_experience;
    public List<Skill> skills = new ArrayList<>();
    public List<Experience> experience = new ArrayList<>();
    public List<Education> education = new ArrayList<>();
    public List<Provenance> provenance = new ArrayList<>();
    public Double overall_confidence;

//     Helper Classes

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Location {
        public String city;
        public String region;
        public String country; // ISO-3166 alpha-2

        public Location() {}
        public Location(String city, String region, String country) {
            this.city = city; this.region = region; this.country = country;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Links {
        public String linkedin;
        public String github;
        public String portfolio;
        public Map<String, String> other = new LinkedHashMap<>();

        public Links() {
        }

        public Links(String linkedin, String github, String portfolio, Map<String, String> other) {
            this.linkedin = linkedin;
            this.github = github;
            this.portfolio = portfolio;
            this.other = other;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Skill {
        public String name;
        public Double confidence;
        public List<String> sources = new ArrayList<>();

        public Skill() {}
        public Skill(String name, Double confidence, String source) {
            this.name = name;
            this.confidence = confidence;
            this.sources.add(source);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Experience {
        public String company;
        public String title;
        public String start;   // YYYY-MM
        public String end;     // YYYY-MM or null (current)
        public String summary;

    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Education {
        public String institution;
        public String degree;
        public String field;
        public String year;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Provenance {
        public String field;
        public String source;   // e.g. "recruiter_csv", "github_api"
        public String method;   // e.g. "direct_parse", "inferred", "api_fetch"
    }

    // Helper Functions

    // Record where a field value came from
    public void addProvenance(String field, String source, String method) {
        Provenance p = new Provenance();
        p.field = field; p.source = source; p.method = method;
        this.provenance.add(p);
    }

    // Merge another profile into this one (this = higher priority winner)
    public void mergeFrom(CandidateProfile other, String otherSource) {
        // emails – union
        for (String e : other.emails) {
            if (!this.emails.contains(e)) this.emails.add(e);
        }
        // phones – union, normalised
        for (String ph : other.phones) {
            if (!this.phones.contains(ph)) this.phones.add(ph);
        }
        // headline – fill if missing
        if (this.headline == null && other.headline != null) {
            this.headline = other.headline;
            addProvenance("headline", otherSource, "fill_missing");
        }
        // location – fill if missing
        if (this.location == null && other.location != null) {
            this.location = other.location;
            addProvenance("location", otherSource, "fill_missing");
        }
        // links – fill individual keys
        if (this.links.linkedin == null && other.links.linkedin != null)
            this.links.linkedin = other.links.linkedin;
        if (this.links.github == null && other.links.github != null)
            this.links.github = other.links.github;
        if (this.links.portfolio == null && other.links.portfolio != null)
            this.links.portfolio = other.links.portfolio;

        // skills – union by canonical name
        Set<String> existingSkills = new HashSet<>();
        for (Skill s : this.skills) existingSkills.add(s.name.toLowerCase());
        for (Skill s : other.skills) {
            if (!existingSkills.contains(s.name.toLowerCase())) {
                this.skills.add(s);
            }
        }
        // experience – union by (company + title)
        Set<String> existingExp = new HashSet<>();
        for (Experience ex : this.experience)
            existingExp.add((ex.company + "|" + ex.title).toLowerCase());
        for (Experience ex : other.experience) {
            String key = (ex.company + "|" + ex.title).toLowerCase();
            if (!existingExp.contains(key)) this.experience.add(ex);
        }
        // education – union by institution
        Set<String> existingEdu = new HashSet<>();
        for (Education ed : this.education)
            existingEdu.add(ed.institution != null ? ed.institution.toLowerCase() : "");
        for (Education ed : other.education) {
            String key = ed.institution != null ? ed.institution.toLowerCase() : "";
            if (!existingEdu.contains(key)) this.education.add(ed);
        }
        // years_experience – take max
        if (other.years_experience != null) {
            if (this.years_experience == null || other.years_experience > this.years_experience)
                this.years_experience = other.years_experience;
        }
    }
}
