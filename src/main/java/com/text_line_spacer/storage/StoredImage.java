package com.text_line_spacer.storage;

public record StoredImage(byte[] data, String filename, String contentType) {
}
