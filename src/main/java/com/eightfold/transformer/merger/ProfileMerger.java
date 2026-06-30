package com.eightfold.transformer.merger;

import com.eightfold.transformer.model.CandidateProfile;

import java.util.*;

/*
 * Merges multiple partial CandidateProfiles for the same person into one
 * canonical record.
 *
 * Match keys (in priority order):
 *  1. email overlap
 *  2. phone overlap
 *  3. full_name + location city (fuzzy)
 *
 * Conflict resolution:
 *  - Structured sources (CSV, ATS JSON) win over unstructured (resume, notes).
 *  - Among equals, the source with higher overall_confidence wins.
 *  - Lists (emails, phones, skills, experience, education) are always unioned.
 */
public class ProfileMerger {

    /*
     * Input: list of profiles (from all parsers)
     * Working: group and merge them.
     * Output: one profile per unique candidate.
     */
    public List<CandidateProfile> merge(List<CandidateProfile> inputs) {
        // Group by shared identity key
        List<List<CandidateProfile>> groups = groupByIdentity(inputs);

        List<CandidateProfile> results = new ArrayList<>();
        for (List<CandidateProfile> group : groups) {
            results.add(mergeGroup(group));
        }
        return results;
    }

    // grouping

    private List<List<CandidateProfile>> groupByIdentity(List<CandidateProfile> inputs) {
        // Union-Find approach: merge groups that share an email or phone
        int n = inputs.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        // Build email -> index map
        Map<String, Integer> emailIdx = new HashMap<>();
        Map<String, Integer> phoneIdx = new HashMap<>();

        for (int i = 0; i < n; i++) {
            CandidateProfile p = inputs.get(i);
            for (String e : p.emails) {
                if (emailIdx.containsKey(e)) union(parent, i, emailIdx.get(e));
                else emailIdx.put(e, i);
            }
            for (String ph : p.phones) {
                if (phoneIdx.containsKey(ph)) union(parent, i, phoneIdx.get(ph));
                else phoneIdx.put(ph, i);
            }
        }

        // Also group by full_name + city (fuzzy, case-insensitive)
        Map<String, Integer> nameLocIdx = new HashMap<>();
        for (int i = 0; i < n; i++) {
            CandidateProfile p = inputs.get(i);
            if (p.full_name != null) {
                String nameLoc = p.full_name.toLowerCase().trim();
                if (p.location != null && p.location.city != null)
                    nameLoc += "|" + p.location.city.toLowerCase().trim();
                if (nameLocIdx.containsKey(nameLoc)) union(parent, i, nameLocIdx.get(nameLoc));
                else nameLocIdx.put(nameLoc, i);
            }
        }

        // Collect groups
        Map<Integer, List<CandidateProfile>> groupMap = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            groupMap.computeIfAbsent(root, k -> new ArrayList<>()).add(inputs.get(i));
        }
        return new ArrayList<>(groupMap.values());
    }

    private int find(int[] parent, int i) {
        if (parent[i] != i) parent[i] = find(parent, parent[i]);
        return parent[i];
    }

    private void union(int[] parent, int a, int b) {
        parent[find(parent, a)] = find(parent, b);
    }

    // merge group

    private CandidateProfile mergeGroup(List<CandidateProfile> group) {
        if (group.size() == 1) return group.get(0);

        // Sort: highest confidence first (primary record)
        group.sort((a, b) -> Double.compare(
                b.overall_confidence != null ? b.overall_confidence : 0,
                a.overall_confidence != null ? a.overall_confidence : 0));

        CandidateProfile primary = group.get(0);
        for (int i = 1; i < group.size(); i++) {
            CandidateProfile other = group.get(i);
            String otherSource = inferSource(other);
            primary.mergeFrom(other, otherSource);
        }

        // Recalculate overall_confidence: weighted avg
        double totalConf = 0;
        int count = 0;
        for (CandidateProfile p : group) {
            if (p.overall_confidence != null) { totalConf += p.overall_confidence; count++; }
        }
        if (count > 0) primary.overall_confidence = Math.min(1.0, totalConf / count + 0.05 * (group.size() - 1));

        return primary;
    }

    private String inferSource(CandidateProfile p) {
        if (p.provenance == null || p.provenance.isEmpty()) return "unknown";
        return p.provenance.get(0).source;
    }
}
