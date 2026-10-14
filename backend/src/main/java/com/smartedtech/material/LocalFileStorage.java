package com.smartedtech.material;

import com.smartedtech.common.BadRequestException;
import com.smartedtech.common.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class LocalFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);

    private final Path rootLocation;

    public LocalFileStorage(@Value("${app.file-storage.path:./uploads}") String storagePath) {
        this.rootLocation = Paths.get(storagePath).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage directory", e);
        }
    }

    @Override
    public String store(InputStream inputStream, String originalFilename, String contentType) {
        String safeExtension = extractSafeExtension(originalFilename);
        String storedFilename = UUID.randomUUID() + safeExtension;

        try {
            Path destinationFile = this.rootLocation.resolve(storedFilename).normalize().toAbsolutePath();
            if (!destinationFile.getParent().equals(this.rootLocation)) {
                throw new BadRequestException("Cannot store file outside current directory");
            }
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            return storedFilename;
        } catch (IOException e) {
            log.error("Failed to store file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to store file", e);
        }
    }

    @Override
    public Resource loadAsResource(String storedFilename) {
        try {
            Path file = rootLocation.resolve(storedFilename).normalize().toAbsolutePath();
            if (!file.startsWith(rootLocation)) {
                throw new BadRequestException("Invalid file path");
            }
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("Could not read file: " + storedFilename);
            }
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("Could not read file: " + storedFilename);
        }
    }

    @Override
    public void delete(String storedFilename) {
        // Best-effort cleanup of physical file on disk
        try {
            Path file = rootLocation.resolve(storedFilename).normalize().toAbsolutePath();
            if (file.startsWith(rootLocation)) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            log.warn("Failed to delete stored file '{}': {}", storedFilename, e.getMessage(), e);
        }
    }

    private String extractSafeExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        int lastDotIndex = originalFilename.lastIndexOf('.');
        String ext = originalFilename.substring(lastDotIndex).toLowerCase();
        // Only alphanumeric extensions
        if (ext.matches("^\\.[a-zA-Z0-9]{1,10}$")) {
            return ext;
        }
        return "";
    }
}
