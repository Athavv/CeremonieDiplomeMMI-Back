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
        return googleDriveService.listImages(GoogleDriveService.FOLDER_GALERIE);
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
