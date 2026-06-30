package com.eightfold.transformer.normalizer;

import java.util.*;

/**
 * Normalises skill names to a canonical form.
 *
 * Examples:
 *   "node.js" → "Node.js"
 *   "nodejs"  → "Node.js"
 *   "JS"      → "JavaScript"
 *   "ml"      → "Machine Learning"
 *   "k8s"     → "Kubernetes"
 */
public class SkillNormalizer {

    // Maps lowercase aliases → canonical name
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        // JavaScript ecosystem
        put("js", "JavaScript");
        put("javascript", "JavaScript");
        put("ts", "TypeScript");
        put("typescript", "TypeScript");
        put("nodejs", "Node.js");
        put("node.js", "Node.js");
        put("node js", "Node.js");
        put("react.js", "React");
        put("reactjs", "React");
        put("react", "React");
        put("vue.js", "Vue.js");
        put("vuejs", "Vue.js");
        put("angular.js", "Angular");
        put("angularjs", "Angular");

        // Python
        put("py", "Python");
        put("python", "Python");
        put("django", "Django");
        put("flask", "Flask");
        put("fastapi", "FastAPI");

        // JVM
        put("java", "Java");
        put("kotlin", "Kotlin");
        put("scala", "Scala");
        put("spring", "Spring");
        put("spring boot", "Spring Boot");
        put("springboot", "Spring Boot");

        // ML / AI
        put("ml", "Machine Learning");
        put("machine learning", "Machine Learning");
        put("dl", "Deep Learning");
        put("deep learning", "Deep Learning");
        put("nlp", "NLP");
        put("natural language processing", "NLP");
        put("cv", "Computer Vision");
        put("computer vision", "Computer Vision");
        put("tensorflow", "TensorFlow");
        put("pytorch", "PyTorch");
        put("scikit-learn", "scikit-learn");
        put("sklearn", "scikit-learn");

        // Cloud & DevOps
        put("aws", "AWS");
        put("amazon web services", "AWS");
        put("gcp", "GCP");
        put("google cloud", "GCP");
        put("azure", "Azure");
        put("docker", "Docker");
        put("k8s", "Kubernetes");
        put("kubernetes", "Kubernetes");
        put("terraform", "Terraform");
        put("ci/cd", "CI/CD");
        put("cicd", "CI/CD");
        put("jenkins", "Jenkins");
        put("github actions", "GitHub Actions");

        // Data
        put("sql", "SQL");
        put("postgresql", "PostgreSQL");
        put("postgres", "PostgreSQL");
        put("mysql", "MySQL");
        put("mongodb", "MongoDB");
        put("mongo", "MongoDB");
        put("redis", "Redis");
        put("kafka", "Apache Kafka");
        put("apache kafka", "Apache Kafka");
        put("spark", "Apache Spark");
        put("apache spark", "Apache Spark");
        put("elasticsearch", "Elasticsearch");

        // Systems
        put("c++", "C++");
        put("cpp", "C++");
        put("c#", "C#");
        put("csharp", "C#");
        put("go", "Go");
        put("golang", "Go");
        put("rust", "Rust");
        put("swift", "Swift");

        // APIs
        put("rest", "REST APIs");
        put("rest api", "REST APIs");
        put("restful", "REST APIs");
        put("graphql", "GraphQL");
        put("grpc", "gRPC");

        // General
        put("git", "Git");
        put("linux", "Linux");
        put("agile", "Agile");
        put("scrum", "Scrum");
    }

    private static void put(String alias, String canonical) {
        ALIASES.put(alias.toLowerCase(), canonical);
    }

    // Return the canonical skill name, or a title-cased version if unknown.
    public static String canonical(String raw) {
        if (raw == null) return "";
        String key = raw.trim().toLowerCase();
        if (key.isEmpty()) return "";
        String found = ALIASES.get(key);
        if (found != null) return found;
        // title-case fallback
        return titleCase(raw.trim());
    }

    private static String titleCase(String s) {
        if (s.isEmpty()) return s;
        String[] words = s.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
