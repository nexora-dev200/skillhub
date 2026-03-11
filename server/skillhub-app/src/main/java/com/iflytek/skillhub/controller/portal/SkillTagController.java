package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.domain.skill.SkillTag;
import com.iflytek.skillhub.domain.skill.service.SkillTagService;
import com.iflytek.skillhub.dto.TagRequest;
import com.iflytek.skillhub.dto.TagResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/skills/{namespace}/{slug}/tags")
public class SkillTagController {

    private final SkillTagService skillTagService;

    public SkillTagController(SkillTagService skillTagService) {
        this.skillTagService = skillTagService;
    }

    @GetMapping
    public ResponseEntity<List<TagResponse>> listTags(
            @PathVariable String namespace,
            @PathVariable String slug) {

        List<SkillTag> tags = skillTagService.listTags(namespace, slug);

        List<TagResponse> response = tags.stream()
                .map(TagResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{tagName}")
    public ResponseEntity<TagResponse> createOrMoveTag(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String tagName,
            @Valid @RequestBody TagRequest request,
            @RequestAttribute("userId") Long userId) {

        SkillTag tag = skillTagService.createOrMoveTag(
                namespace,
                slug,
                tagName,
                request.targetVersion(),
                userId
        );

        return ResponseEntity.ok(TagResponse.from(tag));
    }

    @DeleteMapping("/{tagName}")
    public ResponseEntity<Void> deleteTag(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String tagName,
            @RequestAttribute("userId") Long userId) {

        skillTagService.deleteTag(namespace, slug, tagName, userId);

        return ResponseEntity.noContent().build();
    }
}
