package com.text_line_spacer.storage;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProcessedImageStorage {

    private final Map<String, StoredImage> storage = new ConcurrentHashMap<>();

    public String store(byte[] data, String filename, String contentType) {
        String id = UUID.randomUUID().toString();
        storage.put(id, new StoredImage(data, filename, contentType));
        return id;
    }

    public Optional<StoredImage> find(String id) {
        return Optional.ofNullable(storage.get(id));
    }
}
