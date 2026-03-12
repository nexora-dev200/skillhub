package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.social.event.SkillStarredEvent;
import com.iflytek.skillhub.domain.social.event.SkillUnstarredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillStarServiceTest {
    @Mock SkillStarRepository starRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks SkillStarService service;

    @Test
    void star_skill_creates_record_and_publishes_event() {
        when(starRepository.findBySkillIdAndUserId(1L, "10")).thenReturn(Optional.empty());
        when(starRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.star(1L, "10");

        verify(starRepository).save(any(SkillStar.class));
        verify(eventPublisher).publishEvent(any(SkillStarredEvent.class));
    }

    @Test
    void star_skill_already_starred_is_idempotent() {
        when(starRepository.findBySkillIdAndUserId(1L, "10"))
            .thenReturn(Optional.of(new SkillStar(1L, "10")));

        service.star(1L, "10");

        verify(starRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void unstar_skill_deletes_record_and_publishes_event() {
        SkillStar existing = new SkillStar(1L, "10");
        when(starRepository.findBySkillIdAndUserId(1L, "10")).thenReturn(Optional.of(existing));

        service.unstar(1L, "10");

        verify(starRepository).delete(existing);
        verify(eventPublisher).publishEvent(any(SkillUnstarredEvent.class));
    }

    @Test
    void unstar_skill_not_starred_is_noop() {
        when(starRepository.findBySkillIdAndUserId(1L, "10")).thenReturn(Optional.empty());

        service.unstar(1L, "10");

        verify(starRepository, never()).delete(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void isStarred_returns_true_when_exists() {
        when(starRepository.findBySkillIdAndUserId(1L, "10"))
            .thenReturn(Optional.of(new SkillStar(1L, "10")));
        assertThat(service.isStarred(1L, "10")).isTrue();
    }
}
