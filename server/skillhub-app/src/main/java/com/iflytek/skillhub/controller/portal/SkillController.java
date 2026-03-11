package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.SkillFileRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.service.SkillDownloadService;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.dto.SkillDetailResponse;
import com.iflytek.skillhub.dto.SkillFileResponse;
import com.iflytek.skillhub.dto.SkillVersionResponse;
import com.iflytek.skillhub.ratelimit.RateLimit;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    private final SkillQueryService skillQueryService;
    private final SkillDownloadService skillDownloadService;
    private final SkillFileRepository skillFileRepository;

    public SkillController(
            SkillQueryService skillQueryService,
            SkillDownloadService skillDownloadService,
            SkillFileRepository skillFileRepository) {
        this.skillQueryService = skillQueryService;
        this.skillDownloadService = skillDownloadService;
        this.skillFileRepository = skillFileRepository;
    }

    @GetMapping("/{namespace}/{slug}")
    public ResponseEntity<SkillDetailResponse> getSkillDetail(
            @PathVariable String namespace,
            @PathVariable String slug,
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        SkillQueryService.SkillDetailDTO detail = skillQueryService.getSkillDetail(
                namespace, slug, userId, userNsRoles != null ? userNsRoles : Map.of());

        SkillDetailResponse response = new SkillDetailResponse(
                detail.id(),
                detail.slug(),
                detail.displayName(),
                detail.summary(),
                detail.visibility(),
                detail.status(),
                detail.downloadCount(),
                detail.starCount(),
                detail.latestVersion(),
                namespace
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{namespace}/{slug}/versions")
    public ResponseEntity<Page<SkillVersionResponse>> listVersions(
            @PathVariable String namespace,
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<SkillVersion> versions = skillQueryService.listVersions(
                namespace, slug, PageRequest.of(page, size));

        Page<SkillVersionResponse> response = versions.map(v -> new SkillVersionResponse(
                v.getId(),
                v.getVersion(),
                v.getStatus().name(),
                v.getChangelog(),
                v.getFileCount(),
                v.getTotalSize(),
                v.getPublishedAt()
        ));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{namespace}/{slug}/versions/{version}/files")
    public ResponseEntity<List<SkillFileResponse>> listFiles(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String version) {

        List<SkillFile> files = skillQueryService.listFiles(namespace, slug, version);

        List<SkillFileResponse> response = files.stream()
                .map(f -> new SkillFileResponse(
                        f.getId(),
                        f.getFilePath(),
                        f.getFileSize(),
                        f.getContentType(),
                        f.getSha256()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{namespace}/{slug}/versions/{version}/file")
    public ResponseEntity<InputStreamResource> getFileContent(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String version,
            @RequestParam("path") String path) {

        InputStream content = skillQueryService.getFileContent(namespace, slug, version, path);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(content));
    }

    @GetMapping("/{namespace}/{slug}/download")
    @RateLimit(category = "download", authenticated = 120, anonymous = 30)
    public ResponseEntity<InputStreamResource> downloadLatest(
            @PathVariable String namespace,
            @PathVariable String slug,
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        SkillDownloadService.DownloadResult result = skillDownloadService.downloadLatest(
                namespace, slug, userId, userNsRoles != null ? userNsRoles : Map.of());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.filename() + "\"")
                .contentType(MediaType.parseMediaType(result.contentType()))
                .contentLength(result.contentLength())
                .body(new InputStreamResource(result.content()));
    }

    @GetMapping("/{namespace}/{slug}/versions/{version}/download")
    @RateLimit(category = "download", authenticated = 120, anonymous = 30)
    public ResponseEntity<InputStreamResource> downloadVersion(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String version,
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        SkillDownloadService.DownloadResult result = skillDownloadService.downloadVersion(
                namespace, slug, version, userId, userNsRoles != null ? userNsRoles : Map.of());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.filename() + "\"")
                .contentType(MediaType.parseMediaType(result.contentType()))
                .contentLength(result.contentLength())
                .body(new InputStreamResource(result.content()));
    }
}
