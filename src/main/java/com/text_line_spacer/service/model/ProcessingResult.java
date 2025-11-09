package com.text_line_spacer.service.model;

public record ProcessingResult(
        byte[] originalImage,
        byte[] processedImage,
        String originalText,
        String spacedText,
        String originalFilename,
        String processedFilename,
        String originalContentType,
        String processedContentType
) {
}
