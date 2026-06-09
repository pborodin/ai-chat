package ai.chat.config;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class ToolConfig {

    // Принимаем аргумент напрямую как String, а не через Record.
    // Это исключает 99% ошибок десериализации в Spring AI 2.0.0-RC1
    @Tool(description = "Получить текущую стоимость акций компании по её биржевому тикеру (например: AAPL, GOOG, MSFT)")
    public String getStockPrice(String ticker) {
        System.out.println("⚙️ [TOOL EXECUTION]: Метод getStockPrice вызван с аргументом: " + ticker);

        if (ticker == null || ticker.isBlank()) {
            return "Ошибка: тикер не указан.";
        }

        // Очищаем от возможных кавычек, если модель передала их внутри строки
        String cleanTicker = ticker.toUpperCase().replaceAll("[^A-Z]", "");

        double price = switch (cleanTicker) {
            case "AAPL" -> 175.50;
            case "GOOG" -> 150.25;
            case "MSFT" -> 420.10;
            default -> 100.00;
        };

        return "Текущая цена акций " + cleanTicker + " составляет " + price + " USD.";
    }
}
