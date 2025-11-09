package com.text_line_spacer.controller;

import com.text_line_spacer.service.ImageProcessingException;
import com.text_line_spacer.service.ImageProcessingService;
import com.text_line_spacer.service.model.ProcessingResult;
import com.text_line_spacer.storage.ProcessedImageStorage;
import com.text_line_spacer.storage.StoredImage;
import com.text_line_spacer.web.ProcessedImageViewModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

@Controller
public class ImageController {

    private final ImageProcessingService imageProcessingService;
    private final ProcessedImageStorage processedImageStorage;

    public ImageController(ImageProcessingService imageProcessingService,
                           ProcessedImageStorage processedImageStorage) {
        this.imageProcessingService = imageProcessingService;
        this.processedImageStorage = processedImageStorage;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/process")
    public String processImage(@RequestParam("image") MultipartFile image, Model model) {
        try {
            ProcessingResult result = imageProcessingService.process(image);
            String downloadId = processedImageStorage.store(
                    result.processedImage(),
                    result.processedFilename(),
                    result.processedContentType()
            );

            ProcessedImageViewModel viewModel = new ProcessedImageViewModel(
                    downloadId,
                    imageProcessingService.encodeToDataUrl(result.originalImage(), result.originalContentType()),
                    imageProcessingService.encodeToDataUrl(result.processedImage(), result.processedContentType()),
                    result.originalText(),
                    result.spacedText(),
                    result.originalFilename(),
                    result.processedFilename()
            );

            model.addAttribute("result", viewModel);
        } catch (ImageProcessingException ex) {
            model.addAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            model.addAttribute("error", "Something went wrong while processing the image. Please try again.");
        }
        return "index";
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> downloadImage(@PathVariable String id) {
        Optional<StoredImage> storedImage = processedImageStorage.find(id);
        if (storedImage.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        StoredImage image = storedImage.get();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + image.filename() + "\"")
                .body(image.data());
    }
}
