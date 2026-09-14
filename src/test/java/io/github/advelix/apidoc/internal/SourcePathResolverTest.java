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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourcePathResolverTest {

    @TempDir
    File baseDir;

    @Test
    void resolvesExplicitPathsAgainstBasedir() {
        List<File> result = SourcePathResolver.resolveExplicit("src/main/java,other", baseDir);

        assertEquals(2, result.size());
        assertEquals(new File(baseDir, "src/main/java").getAbsolutePath(), result.get(0).getAbsolutePath());
        assertEquals(new File(baseDir, "other").getAbsolutePath(), result.get(1).getAbsolutePath());
    }

    @Test
    void tokenizesCommasSemicolonsAndWhitespace() {
        List<File> result = SourcePathResolver.resolveExplicit("a, b ;  c", baseDir);

        assertEquals(3, result.size());
        assertEquals("a", result.get(0).getName());
        assertEquals("b", result.get(1).getName());
        assertEquals("c", result.get(2).getName());
    }

    @Test
    void keepsAbsolutePathsUntouched() {
        List<File> result = SourcePathResolver.resolveExplicit("/opt/code/src", baseDir);

        assertEquals(1, result.size());
        assertEquals(new File("/opt/code/src").getAbsolutePath(), result.get(0).getAbsolutePath());
    }

    @Test
    void defaultsNormalizeAndDeduplicate() {
        List<File> result = SourcePathResolver.resolveDefaults(
                Arrays.asList(new File(baseDir, "src/main/java").getAbsolutePath(), "src/main/java", "src/main/java"),
                baseDir);

        assertEquals(1, result.size());
        assertEquals(new File(baseDir, "src/main/java").getAbsolutePath(), result.get(0).getAbsolutePath());
    }

    @Test
    void defaultsHandleBlankAndNullEntries() {
        List<File> result = SourcePathResolver.resolveDefaults(Arrays.asList("  ", null, "src"), baseDir);

        assertEquals(1, result.size());
        assertEquals("src", result.get(0).getName());
    }

    @Test
    void defaultsHandleNullCollection() {
        List<File> result = SourcePathResolver.resolveDefaults(null, baseDir);

        assertEquals(0, result.size());
    }

    @Test
    void toAbsoluteFileNormalizesRelativeAndAbsolutePaths() {
        assertEquals(new File(baseDir, "docs").getAbsolutePath(),
                SourcePathResolver.toAbsoluteFile("docs", baseDir).getAbsolutePath());
        assertEquals("/opt/out", SourcePathResolver.toAbsoluteFile("/opt/out", baseDir).getPath());
    }
}
