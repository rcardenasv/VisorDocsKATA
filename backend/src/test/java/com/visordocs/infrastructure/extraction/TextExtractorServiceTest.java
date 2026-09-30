package com.visordocs.infrastructure.extraction;

import com.visordocs.domain.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextExtractorServiceTest {

    private final TextExtractorService extractor = new TextExtractorService();

    @Test
    void extractTxt_returnsFileContent(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "Hello world\nLine 2", StandardCharsets.UTF_8);

        String result = extractor.extract(file, "txt");

        assertThat(result).isEqualTo("Hello world\nLine 2");
    }

    @Test
    void extractTxt_caseInsensitiveExtension(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.TXT");
        Files.writeString(file, "Content", StandardCharsets.UTF_8);

        String result = extractor.extract(file, "TXT");

        assertThat(result).isEqualTo("Content");
    }

    @Test
    void extractMd_stripsMarkdownSyntax(@TempDir Path tempDir) throws IOException {
        String markdown = """
                # Title
                Some intro text
                - bullet one
                * bullet two
                [link](http://example.com)
                `inline code`
                > quoted text
                """;
        Path file = tempDir.resolve("test.md");
        Files.writeString(file, markdown, StandardCharsets.UTF_8);

        String result = extractor.extract(file, "md");

        assertThat(result).contains("Title");
        assertThat(result).contains("Some intro text");
        assertThat(result).contains("bullet one");
        assertThat(result).contains("bullet two");
        assertThat(result).contains("link");
        assertThat(result).contains("quoted text");
        assertThat(result).doesNotContain("# ");
        assertThat(result).doesNotContain("[link]");
        assertThat(result).doesNotContain("http://example.com");
        assertThat(result).doesNotContain("`inline code`");
        assertThat(result).doesNotContain("> ");
    }

    @Test
    void extractMd_caseInsensitiveExtension(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.MD");
        Files.writeString(file, "# Hello", StandardCharsets.UTF_8);

        String result = extractor.extract(file, "MD");

        assertThat(result).contains("Hello");
    }

    @Test
    void extractPdf_returnsExtractedText(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.pdf");
        createSimplePdf(file, "PDF content here");

        String result = extractor.extract(file, "pdf");

        assertThat(result).contains("PDF content here");
    }

    @Test
    void extractPdf_caseInsensitiveExtension(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.PDF");
        createSimplePdf(file, "Uppercase extension");

        String result = extractor.extract(file, "PDF");

        assertThat(result).contains("Uppercase extension");
    }

    @Test
    void extractPdf_emptyPdf_throwsTextExtractionError(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("empty.pdf");
        createEmptyPdf(file);

        assertThatThrownBy(() -> extractor.extract(file, "pdf"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "TEXT_EXTRACTION_ERROR")
                .hasMessageContaining("PDF contains no extractable text");
    }

    @Test
    void extract_unsupportedFileType_throwsUnsupportedFileTypeError(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.xyz");
        Files.writeString(file, "content", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> extractor.extract(file, "xyz"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "UNSUPPORTED_FILE_TYPE");
    }

    @Test
    void extractTxt_nonExistentFile_throwsTextExtractionError() {
        Path file = Path.of("/non/existent/file.txt");

        assertThatThrownBy(() -> extractor.extract(file, "txt"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "TEXT_EXTRACTION_ERROR")
                .hasMessageContaining("Failed to read TXT file");
    }

    @Test
    void extractMd_nonExistentFile_throwsTextExtractionError() {
        Path file = Path.of("/non/existent/file.md");

        assertThatThrownBy(() -> extractor.extract(file, "md"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "TEXT_EXTRACTION_ERROR")
                .hasMessageContaining("Failed to read Markdown file");
    }

    @Test
    void extractPdf_nonExistentFile_throwsTextExtractionError() {
        Path file = Path.of("/non/existent/file.pdf");

        assertThatThrownBy(() -> extractor.extract(file, "pdf"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "TEXT_EXTRACTION_ERROR")
                .hasMessageContaining("Failed to parse PDF file");
    }

    @Test
    void extractPdf_corruptedPdf_throwsTextExtractionError(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("corrupted.pdf");
        Files.writeString(file, "not a pdf", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> extractor.extract(file, "pdf"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "TEXT_EXTRACTION_ERROR")
                .hasMessageContaining("Failed to parse PDF file");
    }

    @Test
    void extractPdf_withMultiplePages(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("multipage.pdf");
        createMultiPagePdf(file, "Page 1 content", "Page 2 content");

        String result = extractor.extract(file, "pdf");

        assertThat(result).contains("Page 1 content").contains("Page 2 content");
    }

    @Test
    void extractMd_mixedContent_stripsAllPatterns(@TempDir Path tempDir) throws IOException {
        String markdown = """
                # Main Title
                Intro paragraph with `inline code`.

                - Item 1
                - Item 2

                > Blockquote text
                > More quote

                [Link](https://example.com)

                ```code block```
                End.
                """;
        Path file = tempDir.resolve("mixed.md");
        Files.writeString(file, markdown, StandardCharsets.UTF_8);

        String result = extractor.extract(file, "md");

        assertThat(result).contains("Main Title").contains("Intro paragraph").contains("Item 1").contains("Item 2")
                .contains("Blockquote text").contains("More quote").contains("Link").contains("End");
        assertThat(result).doesNotContain("# ").doesNotContain("- ").doesNotContain("> ")
                .doesNotContain("`inline code`")
                .doesNotContain("```code block```").doesNotContain("https://example.com");
    }

    @Test
    void extractMd_withOnlySpecialChars(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("special.md");
        Files.writeString(file, "#\n-\n>\n``\n[]()", StandardCharsets.UTF_8);

        String result = extractor.extract(file, "md");

        assertThat(result).isEmpty();
    }

    @Test
    void extractMd_caseVariations(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("case.Md");
        Files.writeString(file, "# Title", StandardCharsets.UTF_8);

        String result = extractor.extract(file, "Md");

        assertThat(result).contains("Title");
    }

    @Test
    void extractMd_withUnicodeContent(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("unicode.md");
        Files.writeString(file, "# Título\n- Elemento\n> Citación", StandardCharsets.UTF_8);

        String result = extractor.extract(file, "md");

        assertThat(result).contains("Título").contains("Elemento").contains("Citación");
    }

    @Test
    void extractMd_handlesNullBytesInContent(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("nullbytes.md");
        Files.writeString(file, "Normal text\u0000null byte", StandardCharsets.UTF_8);

        String result = extractor.extract(file, "md");

        assertThat(result).contains("Normal text");
    }

    @Test
    void extract_nullFilePath_throwsValidationError() {
        assertThatThrownBy(() -> extractor.extract(null, "txt"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR")
                .hasMessageContaining("filePath must not be null");
    }

    @Test
    void extract_nullFileType_throwsUnsupportedFileTypeError(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "content", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> extractor.extract(file, null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "UNSUPPORTED_FILE_TYPE")
                .hasMessageContaining("File type 'null' is not supported");
    }

    @Test
    void extractMd_veryLongContent(@TempDir Path tempDir) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("# Header ").append(i).append("\n");
            sb.append("- Item ").append(i).append("\n");
        }
        Path file = tempDir.resolve("long.md");
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);

        String result = extractor.extract(file, "md");

        assertThat(result).contains("Header 0").contains("Header 999").contains("Item 0").contains("Item 999");
    }

    @Test
    void extractMd_stripsHeadersOnly(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("headers.md");
        Files.writeString(file, "# H1\n## H2\n### H3\nNo header", StandardCharsets.UTF_8);

        String result = extractor.extract(file, "md");

        assertThat(result).doesNotContain("# ");
        assertThat(result).doesNotContain("## ");
        assertThat(result).doesNotContain("### ");
        assertThat(result).contains("H1").contains("H2").contains("H3").contains("No header");
    }

    @Test
    void extractMd_stripsListBullets(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("bullets.md");
        Files.writeString(file, "- dash\n* asterisk\n+ plus\n  - nested", StandardCharsets.UTF_8);

        String result = extractor.extract(file, "md");

        assertThat(result).doesNotContain("- ");
        assertThat(result).doesNotContain("* ");
        assertThat(result).doesNotContain("+ ");
        assertThat(result).contains("dash").contains("asterisk").contains("plus").contains("nested");
    }

    @Test
    void extractMd_stripsLinksKeepsText(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("links.md");
        Files.writeString(file, "[text](url) and [another](http://test.com)", StandardCharsets.UTF_8);

        String result = extractor.extract(file, "md");

        assertThat(result).contains("text").contains("another");
        assertThat(result).doesNotContain("url").doesNotContain("http://test.com");
    }

    @Test
    void extractMd_stripsInlineCodeAndBlocks(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("code.md");
        Files.writeString(file, "`inline` and ```block``` and ``double``", StandardCharsets.UTF_8);

        String result = extractor.extract(file, "md");

        assertThat(result).doesNotContain("`inline`").doesNotContain("```block```").doesNotContain("``double``");
    }

    @Test
    void extractMd_stripsBlockquotes(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("quotes.md");
        Files.writeString(file, "> quoted\n> multi\n> line", StandardCharsets.UTF_8);

        String result = extractor.extract(file, "md");

        assertThat(result).doesNotContain("> ");
        assertThat(result).contains("quoted").contains("multi").contains("line");
    }

    @Test
    void extractMd_handlesEmptyAndWhitespace(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("empty.md");
        Files.writeString(file, "", StandardCharsets.UTF_8);

        String result = extractor.extract(file, "md");

        assertThat(result).isEmpty();
    }

    // NEW TESTS FOR IMPROVED COVERAGE

    @Test
    void extractTxt_withMalformedUtf8_fallbackToIso88591(@TempDir Path tempDir) throws IOException {
        // Create a file that is invalid UTF-8 but valid ISO-8859-1
        Path file = tempDir.resolve("bad.txt");
        byte[] bytes = "Café".getBytes(StandardCharsets.ISO_8859_1); // é is 0xE9 in ISO-8859-1, invalid UTF-8 alone
        Files.write(file, bytes);

        String result = TextExtractorService.extractTxt(file); // direct call to package-private method

        assertThat(result).contains("Café");
    }

    @Test
    void extractTxt_nullByteRemoved(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("null.txt");
        Files.writeString(file, "Start\u0000End", StandardCharsets.UTF_8);

        String result = TextExtractorService.extractTxt(file);

        assertThat(result).isEqualTo("StartEnd");
    }

    @Test
    void extractTxt_ioException_throwsTextExtractionError(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("isdir.txt");
        Files.createDirectory(file); // directory causes IOException when reading as file
        assertThatThrownBy(() -> TextExtractorService.extractTxt(file))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "TEXT_EXTRACTION_ERROR")
                .hasMessageContaining("Failed to read TXT file");
    }

    @Test
    void extractPdf_ioException_throwsTextExtractionError(@TempDir Path tempDir) throws Exception {
        // Mock PDDocument.load to throw IOException
        // We'll use PowerMock? Too heavy. Instead we trust existing tests.
        // We'll add a test that calls extractPdf with a path that causes Loader.loadPDF
        // to throw IOException by making it a directory.
        Path file = tempDir.resolve("isdir.pdf");
        Files.createDirectory(file); // make it a directory, Loader.loadPDF will throw IOException
        assertThatThrownBy(() -> TextExtractorService.extractPdf(file))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "TEXT_EXTRACTION_ERROR");
    }

    @Test
    void extractMarkdown_ioException_throwsTextExtractionError(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("isdir.md");
        Files.createDirectory(file);
        assertThatThrownBy(() -> TextExtractorService.extractMarkdown(file))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "TEXT_EXTRACTION_ERROR");
    }

    @Test
    void extractPdf_nullText_throwsTextExtractionError(@TempDir Path tempDir) throws IOException {
        // Create a PDF with no text (empty page)
        Path file = tempDir.resolve("emptytext.pdf");
        try (var document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            var page = new org.apache.pdfbox.pdmodel.PDPage();
            document.addPage(page);
            // No text added
            document.save(file.toFile());
        }
        assertThatThrownBy(() -> extractor.extract(file, "pdf"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "TEXT_EXTRACTION_ERROR")
                .hasMessageContaining("PDF contains no extractable text");
    }

    private void createSimplePdf(Path file, String text) throws IOException {
        try (var document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            var page = new org.apache.pdfbox.pdmodel.PDPage();
            document.addPage(page);
            try (var cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page)) {
                cs.beginText();
                cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                        org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            document.save(file.toFile());
        }
    }

    private void createEmptyPdf(Path file) throws IOException {
        try (var document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            var page = new org.apache.pdfbox.pdmodel.PDPage();
            document.addPage(page);
            document.save(file.toFile());
        }
    }

    private void createMultiPagePdf(Path file, String... texts) throws IOException {
        try (var document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            for (String text : texts) {
                var page = new org.apache.pdfbox.pdmodel.PDPage();
                document.addPage(page);
                try (var cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page)) {
                    cs.beginText();
                    cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                            org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText(text);
                    cs.endText();
                }
            }
            document.save(file.toFile());
        }
    }
}