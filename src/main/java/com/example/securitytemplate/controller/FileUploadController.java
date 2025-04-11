package com.example.securitytemplate.controller;

import org.checkerframework.checker.units.qual.A;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.UrlResource;

import com.example.securitytemplate.util.JwtUtil;
import com.example.securitytemplate.model.AppUser;
import com.example.securitytemplate.repository.UserRepository;

import java.io.*;
import java.nio.file.*;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import java.util.List;
import java.util.stream.Stream;


@RestController
@RequestMapping("/api")
public class FileUploadController {

    // Change this to your desired base directory
    private final String baseDirectory = Path.of("C:", "Users", "rohan", "OneDrive", "Desktop", "cloudstoer").toString();
    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;
    


     @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename,
                                                 @RequestHeader("Authorization") String token) {
        // Extract username from the Bearer token
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        String username = jwtUtil.extractUsername(token);

        // Build the target directory and file path
        Path userDir = Paths.get(baseDirectory, username);
        Path filePath = userDir.resolve(filename);

        try {
            if (!Files.exists(filePath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                     .body(null);
            }

            // Create a resource for the file
            Resource resource = new UrlResource(filePath.toUri());

            // Return the file with a content-disposition header to trigger download
            return ResponseEntity.ok()
                                 .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filePath.getFileName().toString() + "\"")
                                 .body(resource);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(null);
        }
    }

    /**
     * List all file names for the user.
     */
    @GetMapping("/files")
    public ResponseEntity<List<String>> listFiles(@RequestHeader("Authorization") String token) {
        // Extract username from the Bearer token
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        String username = jwtUtil.extractUsername(token);

        Path userDir = Paths.get(baseDirectory, username);

        try {
            if (!Files.exists(userDir)) {
                return ResponseEntity.ok().body(List.of()); // Return empty list if directory does not exist
            }

            // Read the files from the user's directory and collect file names
            try (Stream<Path> stream = Files.list(userDir)) {
                List<String> fileNames = stream.filter(Files::isRegularFile)
                                               .map(Path::getFileName)
                                               .map(Path::toString)
                                               .collect(Collectors.toList());
                return ResponseEntity.ok().body(fileNames);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(null);
        }
    }
    @PostMapping("/upload-single")
public ResponseEntity<String> uploadSingleFile(@RequestParam("file") MultipartFile file,
                                               @RequestHeader("Authorization") String token) {

    String username = null;
    if (token.startsWith("Bearer ")) {
        token = token.substring(7);
        username = jwtUtil.extractUsername(token);
    }

    Path targetDir = Paths.get(baseDirectory, username);

    try {
        // Create directory if it does not exist
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        // Define the target file path
        Path targetFile = targetDir.resolve(file.getOriginalFilename());

        // If the file already exists, delete it
        if (Files.exists(targetFile)) {
            Files.delete(targetFile);
        }

        // Save the uploaded file
        Files.write(targetFile, file.getBytes());

        return ResponseEntity.ok("File uploaded successfully and replaced if existed.");
    } catch (Exception e) {
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                             .body("Error occurred: " + e.getMessage());
    }
}

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file,
                                             @RequestHeader("Authorization") String token) {

        // Sanitize the token   
        String username = null;
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
            
             username = jwtUtil.extractUsername(token);
        }
         Path targetDir = Paths.get(baseDirectory, username);

        try {
            // Create directory if it does not exist
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            // Save the uploaded file temporarily
            Path tempFile = Files.createTempFile("upload-", ".zip");
            Files.write(tempFile, file.getBytes());

            // Extract the ZIP file into the target directory
            unzip(tempFile.toString(), targetDir.toString());

            // Delete the temporary file after extraction
            Files.delete(tempFile);

            return ResponseEntity.ok("File uploaded and extracted successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("Error occurred: " + e.getMessage());
        }
    }

    /**
     * Unzips the file at zipFilePath to the destination directory (destDir).
     */
    private void unzip(String zipFilePath, String destDir) throws IOException {
        File destDirectory = new File(destDir);
        byte[] buffer = new byte[1024];
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = newFile(destDirectory, zipEntry);
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory: " + newFile);
                    }
                } else {
                    // Create all non-existent directories
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory: " + parent);
                    }
                    // Write file content
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
                zipEntry = zis.getNextEntry();
            }
        }
    }

    /**
     * Ensures that the file to be extracted does not cause a Zip Slip vulnerability.
     */
    private File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target directory: " + zipEntry.getName());
        }
        return destFile;
    }
}
