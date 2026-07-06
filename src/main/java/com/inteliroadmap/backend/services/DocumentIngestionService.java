package com.inteliroadmap.backend.services;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface DocumentIngestionService {

    void ingestPdfDocument(MultipartFile file) throws IOException ;
}
