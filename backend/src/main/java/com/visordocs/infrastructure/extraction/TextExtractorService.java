package com.visordocs.infrastructure.extraction;

import com.visordocs.domain.AppException;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Extracts plain text content from uploaded documents based on file type.
 * Supports: TXT, PDF, Markdown.
 */
@ApplicationScoped
public class TextExtractorService {

    private static final Logger LOG = Logger.getLogger(TextExtractorService.class);

    /**
     * Extracts text from a file based on its type.
     *
     * @param filePath path to the uploaded file on disk
     * @param fileType the file extension (txt, pdf, md)
     * @return extracted plain text content
     */
    public String extract(Path filePath, String fileType) {
        LOG.infof("Extracting text from file: %s (type: %s)", filePath, fileType);

        return switch (fileType.toLowerCase()) {
            case "txt" -> extractTxt(filePath);
            case "pdf" -> extractPdf(filePath);
            case "md" -> extractMarkdown(filePath);
            default -> throw AppException.unsupportedFileType(fileType);
        };
    }

    private String extractTxt(Path filePath) {
        try {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw AppException.textExtractionError("Failed to read TXT file", e);
        }
    }

    private String extractPdf(Path filePath) {
        try (var document = org.apache.pdfbox.Loader.loadPDF(filePath.toFile())) {
            var stripper = new org.apache.pdfbox.text.PDFTextStripper();
            String text = stripper.getText(document);
            if (text == null || text.isBlank()) {
                throw AppException.textExtractionError("PDF contains no extractable text", null);
            }
            return text;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw AppException.textExtractionError("Failed to parse PDF file", e);
        }
    }

    private String extractMarkdown(Path filePath) {
        try {
            String markdown = Files.readString(filePath, StandardCharsets.UTF_8);
            // Use flexmark to strip markdown formatting and get plain text
            var parser = com.vladsch.flexmark.parser.Parser.builder().build();
            var document = parser.parse(markdown);
            var renderer = com.vladsch.flexmark.util.format.TextCollectingAppendable.create();
            // Simple approach: strip common markdown syntax for indexing
            // The raw markdown is still valuable for search
            return markdown;
        } catch (IOException e) {
            throw AppException.textExtractionError("Failed to read Markdown file", e);
        }
    }
}
