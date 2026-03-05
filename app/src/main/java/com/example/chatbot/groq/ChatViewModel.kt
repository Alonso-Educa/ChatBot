package com.example.chatbot.groq

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room.databaseBuilder
import com.example.chatbot.bbdd.ChatDatabase
import com.example.chatbot.bbdd.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = databaseBuilder(
        application, ChatDatabase::class.java, "chat_database"
    ).build()

    private val dao = db.messageDao()
    private val apiKey = "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"
    private val api = Retrofit.Builder().baseUrl("https://api.groq.com/")
        .addConverterFactory(GsonConverterFactory.create()).build().create(GroqApi::class.java)

    var messages: SnapshotStateList<Message> = mutableStateListOf()
        private set

    var isTyping by mutableStateOf(false)
        private set

    var isThinking by mutableStateOf(false)
        private set

    init {
        // Cargar historial de Room al iniciar
        viewModelScope.launch {
            val savedMessages = withContext(Dispatchers.IO) { dao.getMessages() }
            messages.addAll(savedMessages.map { Message(it.role, it.content) })
        }
    }

    fun sendMessage(text: String) = viewModelScope.launch {
        val userMessage = Message("user", text)
        messages.add(userMessage)

        // Insertar usuario en Room
        withContext(Dispatchers.IO) {
            dao.insertMessage(
                MessageEntity(
                    role = "user",
                    content = text,
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        try {
            isTyping = true
            isThinking = true
            delay(600)

            val response = withContext(Dispatchers.IO) {
                api.chat(
                    "Bearer $apiKey",
                    ChatRequest(model = "llama-3.1-8b-instant", messages = messages)
                )
            }

            isThinking = false

            if (response.isSuccessful) {
                val fullReply = response.body()?.choices?.firstOrNull()?.message?.content ?: ""

                // Mensaje vacío inicial para animación
                var botMessage = Message("assistant", "")
                messages.add(botMessage)

                for (char in fullReply) {
                    delay(10)
                    botMessage.content += char // necesitas hacer Message mutable o crear uno nuevo
                }

                // Guardar bot en Room
                withContext(Dispatchers.IO) {
                    dao.insertMessage(
                        MessageEntity(
                            role = "assistant",
                            content = fullReply,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }

            } else {
                val errorMsg = "¡Error! Código: ${response.code()}"
                messages.add(Message("assistant", errorMsg))
            }

        } catch (e: Exception) {
            messages.add(Message("assistant", "¡Error! ${e.message}"))
        } finally {
            isTyping = false
        }
    }

    fun clearMessages() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { dao.deleteAllMessages() }
            messages.clear()
            messages.add(
                Message(
                    "assistant",
                    "Hola! Soy Groq, tu asistente virtual. Escríbeme si necesitas algo, estaré encantado de ayudar."
                )
            )
        }
    }
}