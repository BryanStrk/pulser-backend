package com.bryanstrk.pulser.checkin;

import com.bryanstrk.pulser.checkin.dto.CheckInFeedDto;
import com.bryanstrk.pulser.checkin.event.CheckInRegistradoEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CheckInFeedListenerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void onCheckInRegistrado_emiteAlDestinoDelEventoConElFeed() {
        CheckInFeedListener listener = new CheckInFeedListener(messagingTemplate);
        CheckInFeedDto feed = new CheckInFeedDto(
                ResultadoCheckIn.VALIDO, UUID.randomUUID(), "Concierto", "General",
                "Puerta A", LocalDateTime.now());
        CheckInRegistradoEvent event = new CheckInRegistradoEvent(10L, feed);

        listener.onCheckInRegistrado(event);

        verify(messagingTemplate).convertAndSend("/topic/eventos/10/checkins", feed);
    }
}
