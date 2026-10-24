package edu.example.visualizer;

import java.net.URI;
import java.nio.file.Path;

public record Config(Coordinates root, URI repositoryUrl,
                     Path outputFile, String filter, int maxPackages,
                     boolean logRequests, boolean logPom) {}
