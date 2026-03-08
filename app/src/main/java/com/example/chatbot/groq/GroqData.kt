package com.example.chatbot.groq

// Clases de datos para el chat
// Para la UI (se queda igual)
data class Message(val role: String, var content: String, val timestamp: Long)

// Solo para enviar a la API
data class ApiMessage(val role: String, val content: String)

// Request usa ApiMessage, no Message
data class ChatRequest(
    val model: String, val messages: List<ApiMessage>
)

// Response también usa ApiMessage (el timestamp no viene de la API)
data class Choice(val message: ApiMessage)
data class ChatResponse(val choices: List<Choice>)