package mmi.ceremonie.diplome.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.Permission;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.oauth2.UserCredentials;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GoogleDriveService {

    public static final String FOLDER_GALERIE       = "Galerie";
    public static final String FOLDER_SANS_TEMPLATE = "Photo sans template";
    public static final String FOLDER_AVEC_TEMPLATE = "Photo avec template";

    @Value("${google.credentials.path:}")
    private String credentialsPath;

    @Value("${google.credentials.json:}")
    private String credentialsJson;

    @Value("${google.drive.folder-id:}")
    private String folderId;

    // OAuth2 user credentials (preferred — uploads owned by the user, real 15GB quota)
    @Value("${google.oauth.client-id:}")
    private String oauthClientId;

    @Value("${google.oauth.client-secret:}")
    private String oauthClientSecret;

    @Value("${google.oauth.refresh-token:}")
    private String oauthRefreshToken;

    private Drive drive;
    private final Map<String, String> subfolderIds = new HashMap<>();

    // Constructor for testing (inject mock Drive directly)
    GoogleDriveService(Drive drive, String folderId) {
        this.drive = drive;
        this.folderId = folderId;
        // Pre-populate subfolder cache so tests don't trigger Drive list/create calls
        subfolderIds.put(FOLDER_GALERIE, folderId);
        subfolderIds.put(FOLDER_SANS_TEMPLATE, folderId);
        subfolderIds.put(FOLDER_AVEC_TEMPLATE, folderId);
    }

    // Default no-arg constructor for Spring
    public GoogleDriveService() {}

    @PostConstruct
    void init() {
        if (drive != null) return; // already set by test constructor
        try {
            GoogleCredentials credentials = loadCredentials();
            // Service accounts need an explicit scope; OAuth user creds carry their own.
            if (credentials instanceof ServiceAccountCredentials) {
                credentials = credentials.createScoped(
                    Collections.singletonList(DriveScopes.DRIVE));
            }
            drive = new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials)
            ).setApplicationName("ceremonie-mmi").build();
            log.info("Google Drive service initialized ({})",
                credentials instanceof UserCredentials ? "OAuth user" : "service account");
        } catch (Throwable e) {
            log.error("Failed to initialize Google Drive service: {}", e.getMessage());
        }
    }

    private GoogleCredentials loadCredentials() throws Exception {
        // Preferred: OAuth2 user credentials — files owned by the user, real quota
        if (!oauthRefreshToken.isBlank() && !oauthClientId.isBlank() && !oauthClientSecret.isBlank()) {
            return UserCredentials.newBuilder()
                .setClientId(oauthClientId)
                .setClientSecret(oauthClientSecret)
                .setRefreshToken(oauthRefreshToken)
                .build();
        }
        // Fallback: service account (note: cannot upload files to a personal Drive)
        if (credentialsJson != null && !credentialsJson.isBlank()) {
            return GoogleCredentials.fromStream(
                new ByteArrayInputStream(credentialsJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        if (credentialsPath == null || credentialsPath.isBlank()) {
            throw new IllegalStateException(
                "No Google credentials configured. Set google.oauth.* or google.credentials.*");
        }
        return GoogleCredentials.fromStream(new FileInputStream(credentialsPath));
    }

    /**
     * Find or create a subfolder inside the root Drive folder. Result is cached.
     * Returns the subfolder ID, or the root folderId on failure.
     */
    private String resolveSubfolder(String name) {
        if (subfolderIds.containsKey(name)) return subfolderIds.get(name);
        try {
            String query = String.format(
                "name='%s' and '%s' in parents and mimeType='application/vnd.google-apps.folder' and trashed=false",
                name, folderId);
            FileList result = drive.files().list().setQ(query).setFields("files(id)").execute();
            String id;
            if (!result.getFiles().isEmpty()) {
                id = result.getFiles().get(0).getId();
                log.info("Found Drive subfolder '{}': {}", name, id);
            } else {
                File folderMeta = new File();
                folderMeta.setName(name);
                folderMeta.setMimeType("application/vnd.google-apps.folder");
                folderMeta.setParents(List.of(folderId));
                id = drive.files().create(folderMeta).setFields("id").execute().getId();
                log.info("Created Drive subfolder '{}': {}", name, id);
            }
            subfolderIds.put(name, id);
            return id;
        } catch (Throwable e) {
            log.error("Could not resolve subfolder '{}', using root folder: {}", name, e.getMessage());
            return folderId;
        }
    }

    /**
     * Upload a photo to a named subfolder (FOLDER_GALERIE, FOLDER_SANS_TEMPLATE, FOLDER_AVEC_TEMPLATE).
     * Returns the Drive file ID, or null on failure.
     */
    public String uploadPhoto(byte[] data, String filename, String subfolderName) {
        if (drive == null) {
            log.warn("Drive not initialized, skipping upload of {}", filename);
            return null;
        }
        try {
            String targetFolderId = resolveSubfolder(subfolderName);
            File metadata = new File();
            metadata.setName(filename);
            metadata.setParents(List.of(targetFolderId));

            InputStreamContent content = new InputStreamContent(
                "image/jpeg", new ByteArrayInputStream(data));

            File created = drive.files().create(metadata, content).setFields("id").execute();
            log.info("Uploaded {} to '{}' ({})", filename, subfolderName, created.getId());
            return created.getId();
        } catch (Throwable e) {
            log.error("Drive upload failed for {}: {}", filename, e.getMessage());
            return null;
        }
    }

    /**
     * Upload a photo to a named subfolder and make it publicly viewable.
     * Returns the public display URL, or null on failure.
     */
    public String uploadPhotoPublic(byte[] data, String filename, String subfolderName) {
        String fileId = uploadPhoto(data, filename, subfolderName);
        return makeFilePublic(fileId);
    }

    /**
     * Make an existing Drive file publicly readable.
     * Returns the public display URL (https://drive.google.com/uc?export=view&id=...), or null on failure.
     */
    public String makeFilePublic(String fileId) {
        if (drive == null || fileId == null) return null;
        try {
            Permission permission = new Permission();
            permission.setType("anyone");
            permission.setRole("reader");
            drive.permissions().create(fileId, permission).execute();
            // lh3.googleusercontent.com serves the image directly (works in <img>);
            // the old uc?export=view endpoint now returns 403 for hotlinking.
            String url = "https://lh3.googleusercontent.com/d/" + fileId;
            log.info("File {} made public: {}", fileId, url);
            return url;
        } catch (Throwable e) {
            log.error("Failed to make file public {}: {}", fileId, e.getMessage());
            return null;
        }
    }
}
