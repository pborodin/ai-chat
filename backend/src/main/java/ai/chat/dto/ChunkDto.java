package ai.chat.dto;

// Объект для передачи на фронтенд: может содержать либо текст, либо имя вызываемого инструмента
public record ChunkDto(String type, String content) {
    public static ChunkDto text(String val) { return new ChunkDto("text", val); }
    public static ChunkDto tool(String val) { return new ChunkDto("tool", val); }
}
