package com.thomas.order_management.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.io.InputStream;

@RestController
@RequestMapping("/api/images")
@CrossOrigin(origins = "http://localhost:4200", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.OPTIONS})
public class ImageController {

    @GetMapping("/{filename}")
    public ResponseEntity<Map<String, String>> getImage(@PathVariable String filename) {
        try {
            ClassPathResource resource = new ClassPathResource("static/images/" + filename);
            
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            // Lese Datei als Bytes
            try (InputStream inputStream = resource.getInputStream()) {
                byte[] imageBytes = inputStream.readAllBytes();
                String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                
                // Bestimme Content-Type
                String mimeType = "image/jpeg";
                if (filename.toLowerCase().endsWith(".png")) {
                    mimeType = "image/png";
                } else if (filename.toLowerCase().endsWith(".svg")) {
                    mimeType = "image/svg+xml";
                }
                
                // Erstelle Data URL
                String dataUrl = "data:" + mimeType + ";base64," + base64Image;
                
                Map<String, String> response = new HashMap<>();
                response.put("dataUrl", dataUrl);
                response.put("filename", filename);
                response.put("mimeType", mimeType);
                
                return ResponseEntity.ok()
                        .header("Access-Control-Allow-Origin", "http://localhost:4200")
                        .header("Access-Control-Allow-Methods", "GET, OPTIONS")
                        .header("Access-Control-Allow-Headers", "*")
                        .body(response);
            }
                    
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}