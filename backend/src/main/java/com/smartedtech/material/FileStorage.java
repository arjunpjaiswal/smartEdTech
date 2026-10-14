package com.smartedtech.material;

import org.springframework.core.io.Resource;
import java.io.InputStream;

public interface FileStorage {

    String store(InputStream inputStream, String originalFilename, String contentType);

    Resource loadAsResource(String storedFilename);

    void delete(String storedFilename);
}
