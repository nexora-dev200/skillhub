package com.iflytek.skillhub.search.event;

import com.iflytek.skillhub.domain.event.SkillDownloadedEvent;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class DownloadCountEventListener {

    private final SkillRepository skillRepository;

    public DownloadCountEventListener(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    @EventListener
    @Async("skillhubEventExecutor")
    public void onSkillDownloaded(SkillDownloadedEvent event) {
        skillRepository.incrementDownloadCount(event.skillId());
    }
}
