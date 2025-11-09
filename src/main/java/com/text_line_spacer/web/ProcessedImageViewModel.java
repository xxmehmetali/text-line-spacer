package com.text_line_spacer.web;

public record ProcessedImageViewModel(
        String downloadId,
        String originalDataUrl,
        String processedDataUrl,
        String originalText,
        String spacedText,
        String originalFilename,
        String processedFilename
) {
}
