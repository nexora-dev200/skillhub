package com.iflytek.skillhub.bootstrap;

import com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor;
import com.iflytek.skillhub.domain.skill.validation.SkillPackagePolicy;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class BuiltinSkillPackageExtractor {

    private final SkillPackageArchiveExtractor archiveExtractor;

    public BuiltinSkillPackageExtractor(SkillPackageArchiveExtractor archiveExtractor) {
        this.archiveExtractor = archiveExtractor;
    }

    public SkillPackageArchiveExtractor.ExtractionResult extract(byte[] zipBytes) throws IOException {
        assertRootSkillMd(zipBytes);
        SkillPackageArchiveExtractor.ExtractionResult result =
                archiveExtractor.extractWithWarnings(new ByteArrayMultipartFile(zipBytes));
        boolean hasRootSkillMd = result.entries().stream()
                .anyMatch(entry -> SkillPackagePolicy.SKILL_MD_PATH.equals(entry.path()));
        if (!hasRootSkillMd) {
            throw new IllegalArgumentException("Built-in skill package must contain root " + SkillPackagePolicy.SKILL_MD_PATH);
        }
        return result;
    }

    private void assertRootSkillMd(byte[] zipBytes) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory() && SkillPackagePolicy.SKILL_MD_PATH.equals(entry.getName())) {
                    return;
                }
                zipInputStream.closeEntry();
            }
        }
        throw new IllegalArgumentException("Built-in skill package must contain root " + SkillPackagePolicy.SKILL_MD_PATH);
    }

    private record ByteArrayMultipartFile(byte[] bytes) implements MultipartFile {

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return "builtin-skill.zip";
        }

        @Override
        public String getContentType() {
            return "application/zip";
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            throw new UnsupportedOperationException("Built-in skill zip adapter is read-only");
        }
    }
}
