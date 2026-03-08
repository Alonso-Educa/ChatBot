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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = databaseBuilder(
        application, ChatDatabase::class.java, "chat_database"
    ).fallbackToDestructiveMigration().build()

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

    private var sendJob: Job? = null

    init {
        viewModelScope.launch {
            val savedMessages = withContext(Dispatchers.IO) { dao.getMessages() }
            if (savedMessages.isEmpty()) {
                // Primera vez: insertar bienvenida
                val welcome = MessageEntity(
                    role = "assistant",
                    content = "Hola! Soy Groq, tu asistente virtual. Escríbeme si necesitas algo, estaré encantado de ayudar.",
                    timestamp = System.currentTimeMillis()
                )
                withContext(Dispatchers.IO) { dao.insertMessage(welcome) }
                messages.add(Message(welcome.role, welcome.content, welcome.timestamp))
            } else {
                messages.addAll(savedMessages.map { Message(it.role, it.content, it.timestamp) })
            }
        }
    }

    fun sendMessage(text: String) {
        sendJob?.cancel() // ← cancela la respuesta en curso
        sendJob = viewModelScope.launch {
            val userMessage = Message("user", text, System.currentTimeMillis()) // ← timestamp único
            messages.add(userMessage)

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
                    val result = api.chat(
                        "Bearer $apiKey",
                        ChatRequest(
                            model = "llama-3.1-8b-instant",
                            messages = messages.map { ApiMessage(it.role, it.content) }
                        )
                    )
                    // Imprime el error body para ver qué dice Groq exactamente
                    if (!result.isSuccessful) {
                        android.util.Log.e(
                            "GROQ_ERROR",
                            result.errorBody()?.string() ?: "sin detalle"
                        )
                    }
                    result
                }

                isThinking = false

                if (response.isSuccessful) {
                    val fullReply = response.body()?.choices?.firstOrNull()?.message?.content ?: ""
                    val botTimestamp = System.currentTimeMillis() // ← timestamp único para el bot

                    var botMessage = Message("assistant", "", botTimestamp)
                    messages.add(botMessage)

                    for (char in fullReply) {
                        delay(5)
                        val updated = botMessage.copy(content = botMessage.content + char)
                        val index = messages.indexOf(botMessage)
                        if (index != -1) messages[index] = updated // ← actualiza en la lista
                        botMessage = updated
                    }

                    withContext(Dispatchers.IO) {
                        dao.insertMessage(
                            MessageEntity(
                                role = "assistant",
                                content = fullReply,
                                timestamp = botTimestamp
                            )
                        )
                    }

                } else {
                    messages.add(
                        Message(
                            "assistant",
                            "¡Error! Código: ${response.code()}",
                            System.currentTimeMillis()
                        )
                    )
                }

            } catch (e: Exception) {
                messages.add(
                    Message(
                        "assistant",
                        "¡Error! ${e.message}",
                        System.currentTimeMillis()
                    )
                )
            } finally {
                isTyping = false
            }
        }
    }

    fun clearMessages() {
        sendJob?.cancel() // ← cancela la respuesta en curso
        sendJob = null
        isTyping = false
        isThinking = false

        viewModelScope.launch {
            withContext(Dispatchers.IO) { dao.deleteAllMessages() }
            messages.clear()

            val welcomeMessage = MessageEntity(
                role = "assistant",
                content = "Hola! Soy Groq, tu asistente virtual. Escríbeme si necesitas algo, estaré encantado de ayudar.",
                timestamp = System.currentTimeMillis()
            )

            // Guardarlo en Room también
            withContext(Dispatchers.IO) { dao.insertMessage(welcomeMessage) }

            messages.add(Message(welcomeMessage.role, welcomeMessage.content, welcomeMessage.timestamp))
        }
    }
}