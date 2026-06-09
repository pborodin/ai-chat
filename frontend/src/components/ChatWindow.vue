<template>
  <div class="app-layout">
    <!-- Боковая панель со списком чатов -->
    <div class="sidebar">
      <button @click="createNewChat" class="btn-new">➕ Новый чат</button>
      <div class="chat-list">
        <div
            v-for="room in rooms"
            :key="room.id"
            :class="['chat-item', { active: currentChatId === room.id }]"
            @click="selectChat(room.id)"
        >
          {{ room.title }}
        </div>
      </div>
    </div>

    <!-- Основное окно чата -->
    <div class="chat-area">
      <div v-if="!currentChatId" class="welcome">Выберите или создайте чат для начала общения</div>

      <template v-else>
        <div class="messages" ref="messagesContainer">
          <div v-for="(msg, idx) in messages[currentChatId] || []" :key="idx" :class="['message-row', msg.role]">
            <div class="message-bubble">
              <span v-if="msg.isTool" class="tool-badge">⚙️ Tool Call</span>
              <p>{{ msg.content }}</p>
            </div>
          </div>
        </div>

        <div class="input-panel">
          <input
              v-model="inputMessage"
              @keyup.enter="sendPayload"
              placeholder="Спроси меня о чем-то или запроси акции (например: какая цена у акций AAPL?)"
              :disabled="isStreaming"
          />
          <button @click="sendPayload" :disabled="isStreaming || !inputMessage.trim()">Отправить</button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue';

const BACKEND_URL = 'http://localhost:8080/api/chat';

const rooms = ref([]);
const currentChatId = ref(null);
const inputMessage = ref('');
const isStreaming = ref(false);
const messages = ref({}); // Структура: { [chatId]: [{role, content, isTool}] }
const messagesContainer = ref(null);

const fetchRooms = async () => {
  const res = await fetch(`${BACKEND_URL}/rooms`);
  rooms.value = await res.json();
  if (rooms.value.length > 0 && !currentChatId.value) {
    selectChat(rooms.value[0].id);
  }
};

const createNewChat = async () => {
  const title = prompt('Введите название чата:', `Чат от ${new Date().toLocaleTimeString()}`);
  if (!title) return;

  const res = await fetch(`${BACKEND_URL}/rooms?title=${encodeURIComponent(title)}`, { method: 'POST' });
  const newRoom = await res.json();
  rooms.value.unshift(newRoom);
  selectChat(newRoom.id);
};

const selectChat = (id) => {
  currentChatId.value = id;
  if (!messages.value[id]) {
    messages.value[id] = [];
  }
  scrollToBottom();
};

const scrollToBottom = async () => {
  await nextTick();
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
  }
};

const sendPayload = () => {
  if (!inputMessage.value.trim() || isStreaming.value) return;

  const chatId = currentChatId.value;
  const userText = inputMessage.value;
  inputMessage.value = '';

  // 1. Добавляем сообщение пользователя на экран
  messages.value[chatId].push({ role: 'user', content: userText, isTool: false });
  scrollToBottom();

  isStreaming.value = true;

  // Переменные для отслеживания текущего индекса текстового ответа
  let textMessageIndex = null;

  // 2. Открываем SSE соединение с бэкендом
  const url = `${BACKEND_URL}/stream?chatId=${encodeURIComponent(chatId)}&message=${encodeURIComponent(userText)}`;
  const eventSource = new EventSource(url);

  eventSource.onmessage = (event) => {
    if (!event.data) return;
    try {
      const data = JSON.parse(event.data);

      if (data.type === 'tool') {
        // ИИ вызвал инструмент: создаем ОТДЕЛЬНОЕ сообщение-плашку
        messages.value[chatId].push({
          role: 'assistant',
          content: data.content,
          isTool: true
        });
        // Сбрасываем текстовый индекс, так как после инструмента может пойти новый текст
        textMessageIndex = null;
        scrollToBottom();

      } else if (data.type === 'text') {
        // Прилетел обычный текстовый токен финального ответа
        if (textMessageIndex === null) {
          // Если это первый токен текста после Tool-плашки, создаем для него ОТДЕЛЬНОЕ сообщение
          messages.value[chatId].push({
            role: 'assistant',
            content: data.content,
            isTool: false
          });
          // Запоминаем его индекс, чтобы последующие токены дописывать именно сюда
          textMessageIndex = messages.value[chatId].length - 1;
        } else {
          // Дописываем токен в уже существующее текстовое сообщение
          messages.value[chatId][textMessageIndex].content += data.content;
        }
        scrollToBottom();
      }
    } catch (e) {
      console.error("Ошибка парсинга SSE пакета", e);
    }
  };

  eventSource.onerror = () => {
    eventSource.close();
    isStreaming.value = false;
  };
};


onMounted(() => {
  fetchRooms();
});
</script>

<style scoped>
.app-layout { display: flex; height: 100vh; background: #181825; }
.sidebar { width: 260px; background: #11111b; display: flex; flex-direction: column; border-right: 1px solid #313244; }
.btn-new { margin: 15px; padding: 10px; background: #89b4fa; color: #11111b; border: none; border-radius: 6px; font-weight: bold; cursor: pointer; }
.chat-list { flex: 1; overflow-y: auto; }
.chat-item { padding: 12px 15px; cursor: pointer; color: #a6adc8; border-bottom: 1px solid #1e1e2e; transition: 0.2s; text-overflow: ellipsis; overflow: hidden; white-space: nowrap;}
.chat-item:hover, .chat-item.active { background: #313244; color: #cdd6f4; }

.chat-area { flex: 1; display: flex; flex-direction: column; background: #1e1e2e; }
.welcome { margin: auto; color: #6c7086; font-size: 1.2rem; }
.messages { flex: 1; overflow-y: auto; padding: 20px; display: flex; flex-direction: column; gap: 15px; }
.message-row { display: flex; width: 100%; }
.message-row.user { justify-content: flex-end; }
.message-row.assistant { justify-content: flex-start; }
.message-bubble { max-width: 70%; padding: 12px 16px; border-radius: 12px; line-height: 1.5; }
.user .message-bubble { background: #89b4fa; color: #11111b; border-bottom-right-radius: 2px; }
.assistant .message-bubble { background: #313244; color: #cdd6f4; border-bottom-left-radius: 2px; }

.tool-badge { display: inline-block; background: #fab387; color: #11111b; font-size: 0.75rem; padding: 2px 6px; border-radius: 4px; font-weight: bold; margin-bottom: 5px; }
.input-panel { padding: 20px; background: #11111b; display: flex; gap: 10px; }
.input-panel input { flex: 1; padding: 12px; background: #313244; border: 1px solid #45475a; border-radius: 6px; color: #cdd6f4; outline: none; }
.input-panel button { padding: 0 20px; background: #a6e3a1; color: #11111b; border: none; border-radius: 6px; font-weight: bold; cursor: pointer; }
.input-panel button:disabled { background: #585b70; cursor: not-allowed; }
</style>
