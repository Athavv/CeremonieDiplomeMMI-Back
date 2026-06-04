package mmi.ceremonie.diplome.controller;

import lombok.RequiredArgsConstructor;
import mmi.ceremonie.diplome.service.FileStorageService;
import mmi.ceremonie.diplome.service.GoogleDriveService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FileController {

    private final FileStorageService fileStorageService;
    private final GoogleDriveService googleDriveService;

    @PostMapping("/upload/gallery")
    public ResponseEntity<String> uploadGalleryImage(@RequestParam("file") MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            // Upload to Drive "Galerie" subfolder — returns public URL
            String driveUrl = googleDriveService.uploadPhotoPublic(
                bytes, file.getOriginalFilename(), GoogleDriveService.FOLDER_GALERIE);
            if (driveUrl != null) {
                return ResponseEntity.ok(driveUrl);
            }
        } catch (java.io.IOException e) {
            // Fall through to local storage
        }
        // Fallback: store locally if Drive unavailable
        String filePath = fileStorageService.storeFile(file, "gallery");
        return ResponseEntity.ok(fileStorageService.getFileUrl(filePath));
    }

    @GetMapping("/{subdirectory}/{filename:.+}")
    public ResponseEntity<Resource> getFile(
            @PathVariable String subdirectory,
            @PathVariable String filename) {
        String filePath = subdirectory + "/" + filename;
        Resource resource = fileStorageService.loadFileAsResource(filePath);
        
        String contentType = "application/octet-stream";
        try {
            String originalContentType = resource.getURL().openConnection().getContentType();
            if (originalContentType != null) {
                contentType = originalContentType;
            }
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
}
