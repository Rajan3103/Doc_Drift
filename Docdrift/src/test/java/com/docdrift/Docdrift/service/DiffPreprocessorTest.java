package com.docdrift.Docdrift.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiffPreprocessorTest {

    private static String fileDiff(String path, String body) {
        return "diff --git a/" + path + " b/" + path + "\n--- a/" + path + "\n+++ b/" + path + "\n" + body + "\n";
    }

    @Test
    void dropsLockfilesGeneratedOutputAndBinaries() {
        String raw = fileDiff("src/App.java", "-old\n+new")
                + fileDiff("frontend/package-lock.json", "+lots of lock data")
                + fileDiff("frontend/dist/index.js", "+bundle")
                + "diff --git a/logo.bin b/logo.bin\nindex 1..2\nBinary files a/logo.bin and b/logo.bin differ\n";

        DiffPreprocessor.Result result = DiffPreprocessor.process(raw, 10_000, 5_000);

        assertEquals(List.of("src/App.java"), result.changedFiles());
        assertEquals(List.of("frontend/package-lock.json", "frontend/dist/index.js", "logo.bin"), result.skippedFiles());
        assertTrue(result.diff().contains("src/App.java"));
        assertFalse(result.diff().contains("lock data"));
        assertTrue(result.hasCodeChanges());
    }

    @Test
    void docsOnlyDiffHasNoCodeChanges() {
        String raw = fileDiff("README.md", "-old\n+new") + fileDiff("docs/openapi.yaml", "+paths:");
        assertFalse(DiffPreprocessor.process(raw, 10_000, 5_000).hasCodeChanges());
    }

    @Test
    void capsPerFileAndTotalSize() {
        String raw = fileDiff("A.java", "+" + "a".repeat(500))
                + fileDiff("B.java", "+" + "b".repeat(500))
                + fileDiff("C.java", "+" + "c".repeat(500));

        DiffPreprocessor.Result result = DiffPreprocessor.process(raw, 700, 300);

        assertTrue(result.diff().contains("[diff for A.java truncated]"));
        assertFalse(result.diff().contains("a".repeat(400)));
        assertFalse(result.truncatedFiles().isEmpty());
        assertTrue(result.diff().contains("changes to these files were omitted"));
    }
}
