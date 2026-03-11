package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.service.SkillPublishService;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.dto.PublishResponse;
import com.iflytek.skillhub.ratelimit.RateLimit;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillPublishController {

    private final SkillPublishService skillPublishService;

    public SkillPublishController(SkillPublishService skillPublishService) {
        this.skillPublishService = skillPublishService;
    }

    @PostMapping("/{namespace}/publish")
    @RateLimit(category = "publish", authenticated = 10, anonymous = 0)
    public ResponseEntity<PublishResponse> publish(
            @PathVariable String namespace,
            @RequestParam("file") MultipartFile file,
            @RequestParam("visibility") String visibility,
            @RequestAttribute("userId") Long userId) throws IOException {

        SkillVisibility skillVisibility = SkillVisibility.valueOf(visibility.toUpperCase());

        List<PackageEntry> entries = extractZipEntries(file);

        SkillVersion version = skillPublishService.publishFromEntries(
                namespace,
                entries,
                userId,
                skillVisibility
        );

        PublishResponse response = new PublishResponse(
                version.getSkillId(),
                namespace,
                null, // slug will be extracted from metadata
                version.getVersion(),
                version.getStatus().name(),
                version.getFileCount(),
                version.getTotalSize()
        );

        return ResponseEntity.ok(response);
    }

    private List<PackageEntry> extractZipEntries(MultipartFile file) throws IOException {
        List<PackageEntry> entries = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (!zipEntry.isDirectory()) {
                    byte[] content = zis.readAllBytes();
                    entries.add(new PackageEntry(
                            zipEntry.getName(),
                            content,
                            content.length,
                            determineContentType(zipEntry.getName())
                    ));
                }
                zis.closeEntry();
            }
        }

        return entries;
    }

    private String determineContentType(String filename) {
        if (filename.endsWith(".py")) return "text/x-python";
        if (filename.endsWith(".json")) return "application/json";
        if (filename.endsWith(".yaml") || filename.endsWith(".yml")) return "application/x-yaml";
        if (filename.endsWith(".txt")) return "text/plain";
        if (filename.endsWith(".md")) return "text/markdown";
        return "application/octet-stream";
    }
}
