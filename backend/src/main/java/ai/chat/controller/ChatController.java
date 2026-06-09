package ai.chat.controller;

import ai.chat.config.ToolConfig;
import ai.chat.dto.ChatDto;
import ai.chat.dto.ChunkDto;
import ai.chat.repository.ChatRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatClient chatClient;
    private final ChatRepository chatRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatController(ChatClient.Builder builder, ChatMemory chatMemory, ChatRepository chatRepository, ToolConfig toolConfig) {
        this.chatRepository = chatRepository;

        this.chatClient = builder
                // ВАЖНО: Добавляем жесткий системный промпт, чтобы Qwen выдавала вызов инструмента строго по стандарту Spring AI
                .defaultSystem("""
                    Ты продвинутый ИИ-помощник. Тебе доступны инструменты (Tools).
                    Когда пользователю нужны данные, которые ты можешь получить из инструментов, ты ДОЛЖЕН сгенерировать вызов функции.
                    Выводи ответ строго на русском языке.
                    """)
                .defaultTools(toolConfig)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        // Включаем автоматический цикл выполнения инструментов прямо в потоке SSE
                        ToolCallingAdvisor.builder().build()
                )
                .build();
    }

    @GetMapping("/rooms")
    public List<ChatDto> getChats() {
        return chatRepository.findAll();
    }

    @PostMapping("/rooms")
    public ChatDto createChat(@RequestParam String title) {
        return chatRepository.createChat(title);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestParam String chatId, @RequestParam String message) {
        try {
            System.out.println("✉️ [CHAT REQUEST]: Новый запрос в чат [" + chatId + "]: " + message);

            // Сигнал начала анализа для фронтенда
            String startSignal = objectMapper.writeValueAsString(ChunkDto.tool("ИИ анализирует запрос и проверяет инструменты..."));

            Flux<String> tokenStream = this.chatClient.prompt()
                    .user(message)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .chatResponse()
                    .doOnNext(response -> {
                        // Логируем шаги агента, если они содержат системные вызовы инструментов
                        if (response.getResult() != null && response.getResult().getOutput().getMetadata().get("tool-calls") != null) {
                            System.out.println("🤖 [AGENT METADATA]: Модель инициировала вызов функции: "
                                    + response.getResult().getOutput().getMetadata().get("tool-calls"));
                        }
                    })
                    .handle((chatResponse, sink) -> {
                        // Отлавливаем чанки с метаданными инструментов, чтобы послать плашку на фронт
                        boolean isToolCall = chatResponse.getResults().stream()
                                .anyMatch(res -> res.getOutput() != null
                                        && res.getOutput().getMetadata() != null
                                        && res.getOutput().getMetadata().get("tool-calls") != null);

                        if (isToolCall) {
                            try {
                                String json = objectMapper.writeValueAsString(ChunkDto.tool("Выполняю Java-метод проверки акций..."));
                                sink.next(json);
                            } catch (Exception ignored) {}
                            return;
                        }

                        // Выводим основной текст ответа
                        if (chatResponse.getResult() != null && chatResponse.getResult().getOutput().getText() != null) {
                            String text = chatResponse.getResult().getOutput().getText();
                            try {
                                String json = objectMapper.writeValueAsString(ChunkDto.text(text));
                                sink.next(json);
                            } catch (Exception ignored) {}
                        }
                    });

            return Flux.just(startSignal).concatWith(tokenStream);

        } catch (Exception e) {
            System.err.println("❌ Ошибка в контроллере потока: " + e.getMessage());
            return Flux.just("{\"type\":\"text\",\"content\":\"Внутренняя ошибка сервера.\"}");
        }
    }

}
