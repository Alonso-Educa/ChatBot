package com.example.chatbot.groq

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.collections.plus

class ChatViewModel : ViewModel() {

    private val apiKey = "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"
    private val api = Retrofit.Builder()
        .baseUrl("https://api.groq.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GroqApi::class.java)

    var messages by mutableStateOf(listOf<Message>())
        private set

    var isTyping by mutableStateOf(false)
        private set

    var isThinking by mutableStateOf(false)
        private set

    fun sendMessage(text: String) {

        val userMessage = Message("user", text)
        messages = messages + userMessage

        viewModelScope.launch {
            try {
                isTyping = true   // empieza a escribir
                isThinking = true  // se simula que piensa en la respuesta
                delay(600)

                val response = api.chat(
                    "Bearer $apiKey",
                    ChatRequest(
                        model = "llama-3.1-8b-instant",
                        messages = messages
                    )
                )
                isThinking = false

                if (response.isSuccessful) {

                    val fullReply = response.body()
                        ?.choices
                        ?.firstOrNull()
                        ?.message
                        ?.content ?: return@launch

                    // Añadimos mensaje vacío del bot
                    var botMessage = Message("assistant", "")
                    messages = messages + botMessage

                    // Se simula escritura por carácter
                    for (char in fullReply) {
                        delay(10) // velocidad de escritura

                        botMessage = botMessage.copy(
                            content = botMessage.content + char
                        )

                        // Se va reemplazando el último mensaje
                        messages = messages.dropLast(1) + botMessage
                    }
                } else {
                    // API devolvió error, se muestra en chat
                    messages = messages + Message(
                        "bot",
                        "¡Error! No se pudo obtener respuesta del chatbot. Código: ${response.code()}"
                    )
                }

            } catch (e: Exception) {
                // Error de red o excepción → mostrar en chat
                messages = messages + Message(
                    "bot",
                    "¡Error! No se pudo conectar al chatbot: ${e.message}"
                )
            }
            finally {
                isTyping = false
            }
        }
    }

    fun resetMessages() {
        messages = listOf(
            Message("assistant", "Hola! Soy Groq, tu asistente virtual. Escríbeme si necesitas algo, estaré encantado de ayudar.")
        )
    }
}