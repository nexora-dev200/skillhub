package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.social.SkillRatingRepository;
import com.iflytek.skillhub.domain.social.event.SkillRatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillRatingEventListenerTest {
    @Mock JdbcTemplate jdbcTemplate;
    @Mock SkillRatingRepository ratingRepository;
    @InjectMocks SkillRatingEventListener listener;

    @Test
    void onRated_updates_rating_avg_and_count() {
        when(ratingRepository.averageScoreBySkillId(1L)).thenReturn(4.2);
        when(ratingRepository.countBySkillId(1L)).thenReturn(10);
        listener.onRated(new SkillRatedEvent(1L, "10", (short) 5));
        verify(jdbcTemplate).update(
            "UPDATE skill SET rating_avg = ?, rating_count = ? WHERE id = ?",
            4.2, 10, 1L);
    }
}
