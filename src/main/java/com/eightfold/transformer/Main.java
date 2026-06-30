package com.eightfold.transformer;

import com.eightfold.transformer.config.OutputConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.*;
import java.util.*;

/**
 * CLI entry point.
 *
 * Usage:
 *   java -jar transformer.jar <input-dir> [--config config.json] [--output result.json]
 *
 * Examples:
 *   java -jar transformer.jar ./sample-inputs
 *   java -jar transformer.jar ./sample-inputs --config custom.json --output out.json
 */
public class Main {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);

    public static void main(String[] args) throws Exception {
        System.out.println("Eightfold Candidate Data Transformer");

        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        // Parse args
        File inputDir   = new File(args[0]);
        File configFile = null;
        File outputFile = new File("output.json");

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--config" -> configFile = new File(args[++i]);
                case "--output" -> outputFile = new File(args[++i]);
                default         -> { System.err.println("Unknown argument: " + args[i]); printUsage(); System.exit(1); }
            }
        }

        if (!inputDir.exists() || !inputDir.isDirectory()) {
            System.err.println("ERROR: Input directory not found: " + inputDir);
            System.exit(1);
        }

        // Load optional config
        OutputConfig config = null;
        if (configFile != null) {
            if (!configFile.exists()) {
                System.err.println("ERROR: Config file not found: " + configFile);
                System.exit(1);
            }
            config = MAPPER.readValue(configFile, OutputConfig.class);
            System.out.println("[CONFIG] Loaded custom config: " + configFile.getName());
        } else {
            System.out.println("[CONFIG] Using default canonical schema\n");
        }

        // Run pipeline
        TransformerPipeline pipeline = new TransformerPipeline();
        List<ObjectNode> results = pipeline.run(inputDir, config);

        // Print to stdout
        System.out.println("\n── Result (" + results.size() + " candidate(s)) -----------------");
        System.out.println(MAPPER.writeValueAsString(results));

        // Write to file
        pipeline.writeOutput(results, outputFile);
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar transformer.jar <input-dir> [--config config.json] [--output result.json]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  <input-dir>          Directory containing source files (.csv, .json, .pdf, .docx, .txt)");
        System.out.println("  --config <file>      Optional runtime output config JSON");
        System.out.println("  --output <file>      Output file (default: output.json)");
        System.out.println();
        System.out.println("Supported source types:");
        System.out.println("  *.csv                Recruiter CSV export");
        System.out.println("  *.json               ATS JSON blob");
        System.out.println("  *.pdf / *.docx       Resume file");
        System.out.println("  *.txt                Recruiter notes (free text)");
        System.out.println("  github_urls.txt      File of GitHub profile URLs (one per line)");
    }
}
