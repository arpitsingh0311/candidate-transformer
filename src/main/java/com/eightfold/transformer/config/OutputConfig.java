package com.eightfold.transformer.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Runtime config that reshapes the canonical output.
 * Matches the JSON schema described in the assignment.
 *
 * Example config:
 * {
 *   "fields": [
 *     {"path":"full_name","type":"string","required":true},
 *     {"path":"primary_email","from":"emails[0]","type":"string","required":true},
 *     {"path":"phone","from":"phones[0]","type":"string","normalize":"E164"},
 *     {"path":"skills","from":"skills[0].name","type":"string[]","normalize":"canonical"}
 *   ],
 *   "include_confidence": true,
 *   "on_missing": "null"
 * }
 */
public class OutputConfig {

    public List<FieldSpec> fields;
    public boolean include_confidence = true;
    public String on_missing = "null";   // "null" | "omit" | "error"

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldSpec {
        public String path;       // output field name
        public String from;       // source path in canonical (optional; defaults to path)
        public String type;       // "string" | "string[]" | "number" | etc.
        public boolean required;
        public String normalize;  // "E164" | "canonical" | null
    }
}
