package mmi.ceremonie.diplome.controller;

import lombok.RequiredArgsConstructor;
import mmi.ceremonie.diplome.model.GuestbookMessage;
import mmi.ceremonie.diplome.repository.GuestbookRepository;
import mmi.ceremonie.diplome.service.FileStorageService;
import mmi.ceremonie.diplome.service.GoogleDriveService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GuestbookController {

    private final GuestbookRepository repository;
    private final FileStorageService fileStorageService;
    private final GoogleDriveService googleDriveService;

    @GetMapping("/guestbook")
    @PreAuthorize("hasRole('ADMIN')")
    public List<GuestbookMessage> getAllMessagesForAdmin() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/public/guestbook")
    public List<GuestbookMessage> getAllMessages() {
        List<GuestbookMessage> messages = repository.findAllByOrderByCreatedAtDesc();
        // Sanitise: strip base64 images that would cause massive JSON payloads crashing Render's free tier
        messages.forEach(msg -> {
            if (msg.getImage() != null && msg.getImage().startsWith("data:")) {
                msg.setImage(null);
            }
        });
        return messages;
    }

    @PostMapping("/public/guestbook")
    public GuestbookMessage postMessage(@RequestBody GuestbookMessage message) {
        // Refuse les images base64 directes - elles doivent passer par /api/files/upload/gallery
        if (message.getImage() != null && message.getImage().startsWith("data:")) {
            message.setImage(null);
        }
        message.setCreatedAt(LocalDateTime.now());
        return repository.save(message);
    }

    @PostMapping("/public/guestbook/submit")
    public GuestbookMessage submitWithPhotos(
            @RequestParam String author,
            @RequestParam String content,
            @RequestParam(required = false) MultipartFile photoRaw,
            @RequestParam(required = false) MultipartFile photoTemplate) throws Exception {

        String imageUrl = null;

        if (photoRaw != null && !photoRaw.isEmpty()) {
            // Store raw photo locally for display in guestbook
            String filePath = fileStorageService.storeFile(photoRaw, "gallery");
            imageUrl = fileStorageService.getFileUrl(filePath);

            // Upload both versions to Drive (non-blocking — returns null on failure)
            String safeName = author.replaceAll("[^a-zA-Z0-9]", "-")
                    + "_" + System.currentTimeMillis();
            googleDriveService.uploadPhoto(photoRaw.getBytes(), safeName + "_brut.jpg");

            if (photoTemplate != null && !photoTemplate.isEmpty()) {
                googleDriveService.uploadPhoto(
                    photoTemplate.getBytes(), safeName + "_template.jpg");
            }
        }

        GuestbookMessage message = new GuestbookMessage();
        message.setAuthor(author);
        message.setContent(content);
        message.setImage(imageUrl);
        message.setCreatedAt(java.time.LocalDateTime.now());
        return repository.save(message);
    }

    @DeleteMapping("/guestbook/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteMessage(@PathVariable Long id) {
        repository.deleteById(id);
    }

    /**
     * Endpoint de nettoyage d'urgence: supprime toutes les images base64 stockees en BD
     * qui causent les erreurs 500 sur Render (payload trop grand).
     * Appeler une seule fois: POST /api/guestbook/cleanup-images
     */
    @PostMapping("/guestbook/cleanup-images")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> cleanupBase64Images() {
        List<GuestbookMessage> messages = repository.findAll();
        int cleaned = 0;
        for (GuestbookMessage msg : messages) {
            if (msg.getImage() != null && msg.getImage().startsWith("data:")) {
                msg.setImage(null);
                repository.save(msg);
                cleaned++;
            }
        }
        return ResponseEntity.ok("Nettoyage terminé: " + cleaned + " images base64 supprimées.");
    }
}
