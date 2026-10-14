package com.smartedtech.material;

import com.smartedtech.common.BadRequestException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

@Component
public class FileTypeValidator {

    private static final long MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024; // 20 MB

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "txt", "ppt", "pptx"
    );

    public void validate(String filename, long fileSize, InputStream inputStream) {
        if (filename == null || filename.isBlank()) {
            throw new BadRequestException("File name is required");
        }

        if (fileSize <= 0) {
            throw new BadRequestException("File cannot be empty");
        }

        if (fileSize > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("File exceeds maximum allowed size of 20MB");
        }

        String extension = getExtension(filename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("File type '." + extension + "' is not supported. Allowed: PDF, DOC, DOCX, PPT, PPTX, TXT");
        }

        byte[] header = new byte[8];
        int bytesRead;
        try {
            bytesRead = inputStream.read(header);
        } catch (IOException e) {
            throw new BadRequestException("Could not read file contents for validation");
        }

        if (bytesRead < 4) {
            if ("txt".equals(extension)) {
                return; // small text file is ok
            }
            throw new BadRequestException("File content is too small or corrupt");
        }

        switch (extension) {
            case "pdf":
                // %PDF
                if (header[0] != 0x25 || header[1] != 0x50 || header[2] != 0x44 || header[3] != 0x46) {
                    throw new BadRequestException("File content does not match PDF format");
                }
                break;
            case "docx":
            case "pptx":
                // ZIP header: PK\x03\x04
                if (header[0] != 0x50 || header[1] != 0x4B || header[2] != 0x03 || header[3] != 0x04) {
                    throw new BadRequestException("File content does not match Office Open XML format");
                }
                break;
            case "doc":
            case "ppt":
                // OLE2 Compound Document header: \xD0\xCF\x11\xE0
                if ((header[0] & 0xFF) != 0xD0 || (header[1] & 0xFF) != 0xCF ||
                    (header[2] & 0xFF) != 0x11 || (header[3] & 0xFF) != 0xE0) {
                    throw new BadRequestException("File content does not match Microsoft Office format");
                }
                break;
            case "txt":
                // Text files should not have null bytes in first bytes
                for (int i = 0; i < bytesRead; i++) {
                    if (header[i] == 0) {
                        throw new BadRequestException("Text file contains binary null bytes");
                    }
                }
                break;
        }
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
