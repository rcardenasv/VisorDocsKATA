package com.visordocs.infrastructure.extraction;

import com.visordocs.domain.AppException;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Extracts plain text content from uploaded documents based on file type.
 * Supports: TXT, PDF, Markdown.
 */
@ApplicationScoped
public class TextExtractorService {

    private static final Logger LOG = Logger.getLogger(TextExtractorService.class);

    // Pre‑compiled regex patterns for markdown stripping
    private static final Pattern HEADER_PATTERN = Pattern.compile("(?m)^#+\\s*");
    private static final Pattern LIST_PATTERN = Pattern.compile("(?m)^\\s*[-*+]\\s+");
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[(.*?)\\]\\(.*?\\)");
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`{1,3}.*?`{1,3}");
    private static final Pattern BLOCKQUOTE_PATTERN = Pattern.compile("(?m)^>\\s*");

    // Re‑use a single PDFTextStripper instance (thread‑safe for sequential use)
    private static final PDFTextStripper PDF_STRIPPER = new PDFTextStripper();

    /**
     * Extracts text from a file based on its type.
     *
     * @param filePath path to the uploaded file on disk
     * @param fileType the file extension (txt, pdf, md)
     * @return extracted plain text content
     */
    public String extract(Path filePath, String fileType) {
        if (filePath == null) {
            throw AppException.validationError("filePath must not be null");
        }
        if (fileType == null) {
            throw AppException.unsupportedFileType(null);
        }
        if (LOG.isInfoEnabled()) {
            LOG.infof("Extracting text from file: %s (type: %s)", filePath, fileType);
        }
        String type = fileType.toLowerCase(Locale.ROOT);
        if ("txt".equals(type)) {
            return extractTxt(filePath);
        } else if ("pdf".equals(type)) {
            return extractPdf(filePath);
        } else if ("md".equals(type)) {
            return extractMarkdown(filePath);
        } else {
            throw AppException.unsupportedFileType(fileType);
        }
    }

    static String extractTxt(Path filePath) {
        // Try UTF-8 first
        try {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            // Remove null bytes that PostgreSQL doesn't allow in UTF8
            return content.replace("\u0000", "");
        } catch (java.nio.charset.MalformedInputException e) {
            // Fallback to ISO-8859-1
            try {
                String content = Files.readString(filePath, StandardCharsets.ISO_8859_1);
                return content.replace("\u0000", "");
            } catch (IOException e2) {
                throw AppException.textExtractionError("Failed to read TXT file with fallback encoding", e2);
            }
        } catch (IOException e) {
            throw AppException.textExtractionError("Failed to read TXT file", e);
        }
    }

    static String extractPdf(Path filePath) {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            String text = PDF_STRIPPER.getText(document);
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

    static String extractMarkdown(Path filePath) {
        try {
            String markdown = Files.readString(filePath, StandardCharsets.UTF_8);
            // Strip common markdown syntax to improve search quality
            String stripped = markdown;
            stripped = HEADER_PATTERN.matcher(stripped).replaceAll("");
            stripped = LIST_PATTERN.matcher(stripped).replaceAll("");
            stripped = LINK_PATTERN.matcher(stripped).replaceAll("$1");
            stripped = INLINE_CODE_PATTERN.matcher(stripped).replaceAll("");
            stripped = BLOCKQUOTE_PATTERN.matcher(stripped).replaceAll("");
            return stripped;
        } catch (IOException e) {
            throw AppException.textExtractionError("Failed to read Markdown file", e);
        }
    }
}