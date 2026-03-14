package de.tum.cit.aet.helios.workflow.logs.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;

@Component
public class WorkflowRunLogArchiveExtractor {

  int extractArchive(byte[] archive, Path tempDirectory) throws IOException {
    int fileCount = 0;
    try (ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(archive))) {
      ZipEntry entry;
      while ((entry = zipInput.getNextEntry()) != null) {
        Path targetPath = resolveZipEntry(tempDirectory, entry);
        if (entry.isDirectory()) {
          Files.createDirectories(targetPath);
        } else {
          Files.createDirectories(targetPath.getParent());
          Files.copy(zipInput, targetPath, StandardCopyOption.REPLACE_EXISTING);
          fileCount++;
        }
        zipInput.closeEntry();
      }
    }
    return fileCount;
  }

  private Path resolveZipEntry(Path tempDirectory, ZipEntry entry) throws IOException {
    Path resolvedPath = tempDirectory.resolve(entry.getName()).normalize();
    if (!resolvedPath.startsWith(tempDirectory)) {
      throw new IOException("Refusing to extract unsafe log entry: " + entry.getName());
    }
    return resolvedPath;
  }
}
