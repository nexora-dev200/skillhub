package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.social.event.SkillStarredEvent;
import com.iflytek.skillhub.domain.social.event.SkillUnstarredEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillStarService {
    private final SkillStarRepository starRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SkillStarService(SkillStarRepository starRepository,
                            ApplicationEventPublisher eventPublisher) {
        this.starRepository = starRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void star(Long skillId, String userId) {
        if (starRepository.findBySkillIdAndUserId(skillId, userId).isPresent()) {
            return; // idempotent
        }
        starRepository.save(new SkillStar(skillId, userId));
        eventPublisher.publishEvent(new SkillStarredEvent(skillId, userId));
    }

    @Transactional
    public void unstar(Long skillId, String userId) {
        starRepository.findBySkillIdAndUserId(skillId, userId).ifPresent(star -> {
            starRepository.delete(star);
            eventPublisher.publishEvent(new SkillUnstarredEvent(skillId, userId));
        });
    }

    public boolean isStarred(Long skillId, String userId) {
        return starRepository.findBySkillIdAndUserId(skillId, userId).isPresent();
    }
}
