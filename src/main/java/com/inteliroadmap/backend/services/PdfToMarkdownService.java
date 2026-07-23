package com.inteliroadmap.backend.services;

import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;

public interface PdfToMarkdownService {

    String convertToMarkdown(MultipartFile file) throws IOException;

    String convertToMarkdown(String pdfUrl) throws IOException;

    String convertToMarkdown(byte[] pdfBytes, String sourceName) throws IOException;

}
