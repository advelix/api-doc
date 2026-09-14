/*
 * Copyright 2026 the api-doc authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.advelix.apidoc.internal;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure path handling helpers so that path semantics can be unit tested without a Maven runtime.
 */
public final class SourcePathResolver {

    private SourcePathResolver() {
    }

    /**
     * Splits an explicit {@code -Dsource} value into candidate source roots. Comma and semicolon
     * separators are accepted; tokens may be absolute or relative to the base directory.
     */
    public static List<File> resolveExplicit(String rawSource, File basedir) {
        List<File> result = new ArrayList<>();
        for (String token : tokenize(rawSource)) {
            result.add(toAbsoluteFile(token, basedir));
        }
        return result;
    }

    /**
     * Resolves the project's default compile source roots, keeping only entries that are absolute
     * after normalization and de-duplicating them while preserving order.
     */
    public static List<File> resolveDefaults(List<String> sourceRoots, File basedir) {
        if (sourceRoots == null || sourceRoots.isEmpty()) {
            return new ArrayList<>();
        }
        Set<File> result = new LinkedHashSet<>();
        for (String sourceRoot : sourceRoots) {
            if (sourceRoot == null || sourceRoot.trim().isEmpty()) {
                continue;
            }
            result.add(toAbsoluteFile(sourceRoot.trim(), basedir));
        }
        return new ArrayList<>(result);
    }

    /**
     * Normalizes a single path token into an absolute, normalized file. Relative paths are resolved
     * against {@code basedir}.
     */
    public static File toAbsoluteFile(String token, File basedir) {
        File file = new File(token.trim());
        if (!file.isAbsolute()) {
            file = new File(basedir, file.getPath());
        }
        return file.getAbsoluteFile();
    }

    /**
     * Splits on commas and semicolons, trimming tokens and dropping empty ones.
     */
    public static List<String> tokenize(String raw) {
        List<String> tokens = new ArrayList<>();
        if (raw == null) {
            return tokens;
        }
        for (String part : raw.split("[,;]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                tokens.add(trimmed);
            }
        }
        return tokens;
    }
}
