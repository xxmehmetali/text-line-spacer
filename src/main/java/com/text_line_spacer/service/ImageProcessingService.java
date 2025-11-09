package com.text_line_spacer.service;

import com.text_line_spacer.service.model.ProcessingResult;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.leptonica.PIX;
import org.bytedeco.leptonica.global.leptonica;
import org.bytedeco.tesseract.TessBaseAPI;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
public class ImageProcessingService {

    public ProcessingResult process(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ImageProcessingException("Please select an image before uploading.");
        }

        try {
            byte[] originalBytes = file.getBytes();
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(originalBytes));
            if (originalImage == null) {
                throw new ImageProcessingException("The uploaded file does not appear to be a supported image.");
            }

            String originalText = extractText(originalImage);
            if (!StringUtils.hasText(originalText)) {
                throw new ImageProcessingException("We couldn't detect any text in the uploaded image.");
            }

            String spacedText = addBlankLines(originalText);
            BufferedImage processedImage = renderTextImage(spacedText, originalImage.getWidth());
            byte[] processedBytes = toImageBytes(processedImage);

            String originalFilename = resolveOriginalFilename(file.getOriginalFilename());
            String processedFilename = createProcessedFilename(originalFilename);
            String originalContentType = resolveContentType(file.getContentType());

            return new ProcessingResult(
                    originalBytes,
                    processedBytes,
                    originalText,
                    spacedText,
                    originalFilename,
                    processedFilename,
                    originalContentType,
                    "image/png"
            );
        } catch (IOException ex) {
            throw new ImageProcessingException("Unable to read the uploaded image.", ex);
        }
    }

    private String extractText(BufferedImage image) {
        try (TessBaseAPI api = new TessBaseAPI()) {
            if (api.Init((String) null, "eng") != 0) {
                throw new ImageProcessingException("Could not initialize the OCR engine.");
            }

            byte[] imageBytes = toImageBytes(image);
            try (BytePointer imagePointer = new BytePointer(imageBytes);
                 PIX pix = leptonica.pixReadMem(imagePointer, imageBytes.length)) {
                if (pix == null) {
                    throw new ImageProcessingException("Unable to read the image data for OCR.");
                }
                api.SetImage(pix);
                try (BytePointer textPointer = api.GetUTF8Text()) {
                    if (textPointer == null) {
                        return "";
                    }
                    String text = textPointer.getString();
                    return text != null ? text.trim() : "";
                }
            }
        } catch (ImageProcessingException ex) {
            throw ex;
        } catch (UnsatisfiedLinkError | RuntimeException | IOException ex) {
            throw new ImageProcessingException("Failed to perform OCR on the image.", ex);
        }
    }

    private String addBlankLines(String text) {
        List<String> lines = text.lines().toList();
        List<String> spaced = new ArrayList<>();
        for (String line : lines) {
            spaced.add(line);
            spaced.add("");
        }
        return String.join(System.lineSeparator(), spaced);
    }

    private BufferedImage renderTextImage(String text, int referenceWidth) {
        List<String> lines = text.lines().toList();
        if (lines.isEmpty()) {
            lines = List.of("");
        }

        Font font = new Font("SansSerif", Font.PLAIN, 28);
        int padding = 40;

        BufferedImage temporary = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D tempGraphics = temporary.createGraphics();
        tempGraphics.setFont(font);
        FontMetrics metrics = tempGraphics.getFontMetrics();

        int lineHeight = metrics.getHeight();
        int maxLineWidth = lines.stream()
                .mapToInt(metrics::stringWidth)
                .max()
                .orElse(0);
        tempGraphics.dispose();

        int width = Math.max(referenceWidth, maxLineWidth + padding * 2);
        int height = lineHeight * lines.size() + padding * 2;

        BufferedImage rendered = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rendered.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.BLACK);
        graphics.setFont(font);

        FontMetrics renderedMetrics = graphics.getFontMetrics();
        int y = padding + renderedMetrics.getAscent();
        for (String line : lines) {
            if (StringUtils.hasText(line)) {
                graphics.drawString(line, padding, y);
            }
            y += lineHeight;
        }

        graphics.dispose();
        return rendered;
    }

    private byte[] toImageBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }

    private String resolveOriginalFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "uploaded-image.png";
        }
        return originalFilename;
    }

    private String createProcessedFilename(String originalFilename) {
        String filename = StringUtils.hasText(originalFilename) ? originalFilename : "image";
        int dotIndex = filename.lastIndexOf('.');
        String baseName = dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
        String sanitizedBase = baseName.trim().replaceAll("[^a-zA-Z0-9-_]", "_");
        if (!StringUtils.hasText(sanitizedBase)) {
            sanitizedBase = "image";
        }
        return sanitizedBase + "-spaced.png";
    }

    private String resolveContentType(String contentType) {
        if (!StringUtils.hasText(contentType) || !contentType.toLowerCase(Locale.ENGLISH).startsWith("image/")) {
            return "image/png";
        }
        return contentType;
    }

    public String encodeToDataUrl(byte[] imageBytes, String contentType) {
        String safeContentType = resolveContentType(contentType);
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        return "data:" + safeContentType + ";base64," + base64;
    }
}
