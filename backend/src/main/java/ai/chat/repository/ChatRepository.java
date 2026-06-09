package ai.chat.repository;

import ai.chat.dto.ChatDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public class ChatRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChatRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ChatDto> findAll() {
        return jdbcTemplate.query("SELECT id, title FROM chats ORDER BY created_at DESC",
                (rs, rowNum) -> new ChatDto(rs.getString("id"), rs.getString("title")));
    }

    public ChatDto createChat(String title) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO chats (id, title) VALUES (?, ?)", id, title);
        return new ChatDto(id, title);
    }
}
