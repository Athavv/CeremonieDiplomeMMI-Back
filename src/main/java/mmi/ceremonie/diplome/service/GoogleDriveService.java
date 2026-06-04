package mmi.ceremonie.diplome.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class GoogleDriveService {

    @Value("${google.credentials.path:}")
    private String credentialsPath;

    @Value("${google.credentials.json:}")
    private String credentialsJson;

    @Value("${google.drive.folder-id:}")
    private String folderId;

    private Drive drive;

    // Constructor for testing (inject mock Drive directly)
    GoogleDriveService(Drive drive, String folderId) {
        this.drive = drive;
        this.folderId = folderId;
    }

    // Default no-arg constructor for Spring
    public GoogleDriveService() {}

    @PostConstruct
    void init() {
        if (drive != null) return; // already set by test constructor
        try {
            GoogleCredentials credentials = loadCredentials();
            credentials = credentials.createScoped(
                Collections.singletonList(DriveScopes.DRIVE_FILE)
            );
            drive = new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials)
            ).setApplicationName("ceremonie-mmi").build();
            log.info("Google Drive service initialized");
        } catch (Exception e) {
            log.error("Failed to initialize Google Drive service: {}", e.getMessage());
        }
    }

    private GoogleCredentials loadCredentials() throws Exception {
        if (credentialsJson != null && !credentialsJson.isBlank()) {
            return GoogleCredentials.fromStream(
                new ByteArrayInputStream(credentialsJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        if (credentialsPath == null || credentialsPath.isBlank()) {
            throw new IllegalStateException(
                "No Google credentials configured. Set google.credentials.json or google.credentials.path.");
        }
        return GoogleCredentials.fromStream(new FileInputStream(credentialsPath));
    }

    /**
     * Upload a photo and make it publicly viewable.
     * Returns the public display URL (https://drive.google.com/uc?export=view&id=...), or null on failure.
     * NOTE: Content-type is hardcoded to image/jpeg; caller must pass JPEG bytes.
     */
    public String uploadPhotoPublic(byte[] data, String filename) {
        String fileId = uploadPhoto(data, filename);
        return makeFilePublic(fileId);
    }

    /**
     * Make an existing Drive file publicly readable.
     * Returns the public display URL, or null on failure.
     */
    public String makeFilePublic(String fileId) {
        if (drive == null || fileId == null) return null;
        try {
            Permission permission = new Permission();
            permission.setType("anyone");
            permission.setRole("reader");
            drive.permissions().create(fileId, permission).execute();
            String url = "https://drive.google.com/uc?export=view&id=" + fileId;
            log.info("File {} made public: {}", fileId, url);
            return url;
        } catch (Exception e) {
            log.error("Failed to make file public {}: {}", fileId, e.getMessage());
            return null;
        }
    }

    /**
     * Upload a photo to the configured Drive folder.
     * Returns the Drive file ID, or null if the upload fails (non-blocking).
     * NOTE: Content-type is hardcoded to image/jpeg; caller must pass JPEG bytes.
     */
    public String uploadPhoto(byte[] data, String filename) {
        if (drive == null) {
            log.warn("Drive not initialized, skipping upload of {}", filename);
            return null;
        }
        try {
            File metadata = new File();
            metadata.setName(filename);
            metadata.setParents(List.of(folderId));

            InputStreamContent content = new InputStreamContent(
                "image/jpeg",
                new ByteArrayInputStream(data)
            );

            File created = drive.files()
                .create(metadata, content)
                .setFields("id")
                .execute();

            log.info("Uploaded {} to Drive: {}", filename, created.getId());
            return created.getId();
        } catch (Exception e) {
            log.error("Drive upload failed for {}: {}", filename, e.getMessage());
            return null;
        }
    }
}
