package mmi.ceremonie.diplome.controller;

import lombok.RequiredArgsConstructor;
import mmi.ceremonie.diplome.model.GalleryImage;
import mmi.ceremonie.diplome.repository.GalleryRepository;
import mmi.ceremonie.diplome.service.GoogleDriveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gallery")
@RequiredArgsConstructor
public class GalleryController {

    private final GalleryRepository repository;

    @Autowired(required = false)
    @Nullable
    private GoogleDriveService googleDriveService;

    @GetMapping
    public List<GalleryImage> getAllImages() {
        return repository.findAll();
    }

    /**
     * Lists images placed directly in the Drive "Galerie" folder
     * (including ones added manually outside the app).
     */
    @GetMapping("/drive")
    public List<Map<String, String>> getDriveImages() {
        if (googleDriveService == null) return List.of();
        // Public (not logged in) gallery shows the curated "accesslibre" Drive folder
        return googleDriveService.listImages(GoogleDriveService.FOLDER_ACCESSLIBRE);
    }

    /**
     * Full gallery for authenticated users — the complete "Galerie" Drive folder.
     */
    @GetMapping("/drive-all")
    public List<Map<String, String>> getAllDriveImages() {
        if (googleDriveService == null) return List.of();
        return googleDriveService.listImages(GoogleDriveService.FOLDER_GALERIE);
    }

    /** Temporary diagnostic — shows what's actually under the Drive root folder. */
    @GetMapping("/drive-debug")
    public List<Map<String, String>> getDriveDebug() {
        if (googleDriveService == null) return List.of(Map.of("error", "drive not configured"));
        return googleDriveService.debugRootContents();
    }

    @PostMapping
    public GalleryImage addImage(@RequestBody GalleryImage image) {
        return repository.save(image);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteImage(@PathVariable Long id) {
        repository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
