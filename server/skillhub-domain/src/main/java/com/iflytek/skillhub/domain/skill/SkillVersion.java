package com.iflytek.skillhub.domain.skill;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "skill_version")
public class SkillVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(nullable = false, length = 50)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SkillVersionStatus status;

    @Column(columnDefinition = "TEXT")
    private String changelog;

    @Column(name = "parsed_metadata_json", columnDefinition = "TEXT")
    private String parsedMetadataJson;

    @Column(name = "manifest_json", columnDefinition = "TEXT")
    private String manifestJson;

    @Column(name = "file_count", nullable = false)
    private Integer fileCount = 0;

    @Column(name = "total_size", nullable = false)
    private Long totalSize = 0L;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SkillVersion() {
    }

    public SkillVersion(Long skillId, String version, Long createdBy) {
        this.skillId = skillId;
        this.version = version;
        this.createdBy = createdBy;
        this.status = SkillVersionStatus.DRAFT;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters
    public Long getId() {
        return id;
    }

    public Long getSkillId() {
        return skillId;
    }

    public String getVersion() {
        return version;
    }

    public SkillVersionStatus getStatus() {
        return status;
    }

    public String getChangelog() {
        return changelog;
    }

    public String getParsedMetadataJson() {
        return parsedMetadataJson;
    }

    public String getManifestJson() {
        return manifestJson;
    }

    public Integer getFileCount() {
        return fileCount;
    }

    public Long getTotalSize() {
        return totalSize;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // Setters
    public void setStatus(SkillVersionStatus status) {
        this.status = status;
    }

    public void setChangelog(String changelog) {
        this.changelog = changelog;
    }

    public void setParsedMetadataJson(String parsedMetadataJson) {
        this.parsedMetadataJson = parsedMetadataJson;
    }

    public void setManifestJson(String manifestJson) {
        this.manifestJson = manifestJson;
    }

    public void setFileCount(Integer fileCount) {
        this.fileCount = fileCount;
    }

    public void setTotalSize(Long totalSize) {
        this.totalSize = totalSize;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }
}
