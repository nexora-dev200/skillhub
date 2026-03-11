package com.iflytek.skillhub.domain.skill;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "skill")
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    @Column(nullable = false, length = 100)
    private String slug;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "source_skill_id")
    private Long sourceSkillId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SkillVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SkillStatus status;

    @Column(name = "latest_version_id")
    private Long latestVersionId;

    @Column(name = "download_count", nullable = false)
    private Long downloadCount = 0L;

    @Column(name = "star_count", nullable = false)
    private Integer starCount = 0;

    @Column(name = "rating_avg", precision = 3, scale = 2, nullable = false)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    private Integer ratingCount = 0;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected Skill() {
    }

    public Skill(Long namespaceId, String slug, Long ownerId, SkillVisibility visibility) {
        this.namespaceId = namespaceId;
        this.slug = slug;
        this.ownerId = ownerId;
        this.visibility = visibility;
        this.status = SkillStatus.ACTIVE;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters
    public Long getId() {
        return id;
    }

    public Long getNamespaceId() {
        return namespaceId;
    }

    public String getSlug() {
        return slug;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSummary() {
        return summary;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public Long getSourceSkillId() {
        return sourceSkillId;
    }

    public SkillVisibility getVisibility() {
        return visibility;
    }

    public SkillStatus getStatus() {
        return status;
    }

    public Long getLatestVersionId() {
        return latestVersionId;
    }

    public Long getDownloadCount() {
        return downloadCount;
    }

    public Integer getStarCount() {
        return starCount;
    }

    public BigDecimal getRatingAvg() {
        return ratingAvg;
    }

    public Integer getRatingCount() {
        return ratingCount;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // Setters
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public void setSourceSkillId(Long sourceSkillId) {
        this.sourceSkillId = sourceSkillId;
    }

    public void setVisibility(SkillVisibility visibility) {
        this.visibility = visibility;
    }

    public void setStatus(SkillStatus status) {
        this.status = status;
    }

    public void setLatestVersionId(Long latestVersionId) {
        this.latestVersionId = latestVersionId;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }
}
