package com.example.chatbot.groq

// Clases de datos para el chat
data class Message(val role: String, val content: String)
data class ChatRequest(
    val model: String, val messages: List<Message>
)

data class Choice(val message: Message)
data class ChatResponse(val choices: List<Choice>)