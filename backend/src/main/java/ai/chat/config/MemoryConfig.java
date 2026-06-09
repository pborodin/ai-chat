package ai.chat.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class MemoryConfig {

    @Bean
    public ChatMemory chatMemory(JdbcTemplate jdbcTemplate) {
        // 1. Создаем репозиторий для сохранения сообщений в SQLite через JDBC
        JdbcChatMemoryRepository repository = JdbcChatMemoryRepository.builder()
                .jdbcTemplate(jdbcTemplate)
                .build();

        // 2. Оборачиваем его в MessageWindowChatMemory
        // Рекомендуется использовать именно его, чтобы контролировать лимиты токенов/сообщений
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository) // Передаем репозиторий SQLite
                .maxMessages(100)                 // Опционально: глубина памяти (количество сообщений)
                .build();
    }
}
