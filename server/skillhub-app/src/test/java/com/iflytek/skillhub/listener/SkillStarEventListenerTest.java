package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.social.SkillStarRepository;
import com.iflytek.skillhub.domain.social.event.SkillStarredEvent;
import com.iflytek.skillhub.domain.social.event.SkillUnstarredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillStarEventListenerTest {
    @Mock JdbcTemplate jdbcTemplate;
    @Mock SkillStarRepository starRepository;
    @InjectMocks SkillStarEventListener listener;

    @Test
    void onStarred_updates_star_count() {
        when(starRepository.countBySkillId(1L)).thenReturn(42L);
        listener.onStarred(new SkillStarredEvent(1L, "10"));
        verify(jdbcTemplate).update("UPDATE skill SET star_count = ? WHERE id = ?", 42, 1L);
    }

    @Test
    void onUnstarred_updates_star_count() {
        when(starRepository.countBySkillId(1L)).thenReturn(41L);
        listener.onUnstarred(new SkillUnstarredEvent(1L, "10"));
        verify(jdbcTemplate).update("UPDATE skill SET star_count = ? WHERE id = ?", 41, 1L);
    }
}
