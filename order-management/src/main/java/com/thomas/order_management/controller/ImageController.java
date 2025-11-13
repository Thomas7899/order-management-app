// src/main/java/com/thomas/order_management/controller/ImageController.java
package com.thomas.order_management.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/images")
@CrossOrigin(origins = "http://localhost:4200") 
public class ImageController {

    @GetMapping("/{filename:.+}") // 👈 erlaubt Punkte im Dateinamen (z. B. .jpg, .png)
    public ResponseEntity<Resource> getImage(@PathVariable String filename) {
        try {
            Resource resource = new ClassPathResource("static/images/" + filename);

            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            String lowerCaseFilename = filename.toLowerCase();
            MediaType mediaType;

            if (lowerCaseFilename.endsWith(".png")) {
                mediaType = MediaType.IMAGE_PNG;
            } else if (lowerCaseFilename.endsWith(".svg")) {
                mediaType = MediaType.valueOf("image/svg+xml");
            } else if (lowerCaseFilename.endsWith(".jpg") || lowerCaseFilename.endsWith(".jpeg")) {
                mediaType = MediaType.IMAGE_JPEG;
            } else {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .contentType(mediaType)
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
