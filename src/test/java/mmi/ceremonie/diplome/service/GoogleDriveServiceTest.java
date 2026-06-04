package mmi.ceremonie.diplome.service;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleDriveServiceTest {

    @Mock
    private Drive mockDrive;

    @Mock
    private Drive.Files mockFiles;

    @Mock
    private Drive.Files.Create mockCreate;

    private GoogleDriveService service;

    @BeforeEach
    void setUp() {
        service = new GoogleDriveService(mockDrive, "test-folder-id");
    }

    @Test
    void uploadPhoto_returnsFileId_whenUploadSucceeds() throws IOException {
        File returnedFile = new File();
        returnedFile.setId("drive-file-id-123");

        when(mockDrive.files()).thenReturn(mockFiles);
        when(mockFiles.create(any(File.class), any())).thenReturn(mockCreate);
        when(mockCreate.setFields(anyString())).thenReturn(mockCreate);
        when(mockCreate.execute()).thenReturn(returnedFile);

        String fileId = service.uploadPhoto(
            "Jean Dupont".getBytes(),
            "jean-dupont_brut_123.jpg"
        );

        assertThat(fileId).isEqualTo("drive-file-id-123");
    }

    @Test
    void uploadPhoto_returnsNull_whenDriveThrows() throws IOException {
        when(mockDrive.files()).thenReturn(mockFiles);
        when(mockFiles.create(any(File.class), any())).thenReturn(mockCreate);
        when(mockCreate.setFields(anyString())).thenReturn(mockCreate);
        when(mockCreate.execute()).thenThrow(new IOException("Drive unavailable"));

        String fileId = service.uploadPhoto("data".getBytes(), "test.jpg");

        assertThat(fileId).isNull();
    }

    @Test
    void uploadPhoto_returnsNull_whenDriveNotInitialized() {
        // Arrange: service created with no Drive instance (simulates failed @PostConstruct)
        GoogleDriveService uninitialised = new GoogleDriveService();
        // Act: drive field is null
        String fileId = uninitialised.uploadPhoto("data".getBytes(), "test.jpg");
        // Assert
        assertThat(fileId).isNull();
    }
}
