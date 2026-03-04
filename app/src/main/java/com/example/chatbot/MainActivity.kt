package com.example.chatbot

import android.R.attr.enabled
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.chatbot.groq.*
import com.example.chatbot.ui.theme.ChatBotTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChatBotTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                val listState = rememberLazyListState()
                val viewModel: ChatViewModel = viewModel()

                Scaffold(snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState)
                }, topBar = {
                    TopAppBar(
                        title = { Text("ChatBot de usuario", fontSize = 20.sp) },
                        colors = topAppBarColors(
                            containerColor = Color(0xFF4CAF50), titleContentColor = Color.White
                        ),
                        actions = {
                            IconButton(
                                onClick = {
                                    // que borre la lista de mensajes
                                    // si se pudiera hacer un viewmodel.messages.clear
                                    // Error: Cannot access 'messages': it is private in 'com.example.chatbot.groq.ChatViewModel'.
                                    viewModel.resetMessages()
                                }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Borrar la conversación",
                                    tint = Color.White
                                )
                            }
                        })
                }) { innerPadding ->

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        viewModel.resetMessages()
                        PantallaChat(
                            viewModel = viewModel, listState = listState
                        )
                    }
                }
            }
        }
    }
}

// Clase para la pantalla del chat
@Composable
fun PantallaChat(
    viewModel: ChatViewModel = viewModel(), listState: LazyListState
) {

    var text by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var showSnackbar by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F2))
    ) {

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp), state = listState
        ) {
            // Lista de mensajes
            items(viewModel.messages) { message ->
                MensajeChat(message)
            }

            if (viewModel.isThinking) {
                item {
                    TypingIndicator()
                }
            }
        }

        // Scroll automático al enviar un mensaje
        LaunchedEffect(viewModel.messages.size) {
            listState.animateScrollToItem(viewModel.messages.size)
        }


        // Se muestra el snackbar al querer enviar un mensaje cuando el bot está todavía hablando
        if (showSnackbar) {
            Snackbar(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.End)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(color = Color.Transparent), action = {}) {
                Text("Espera a que el chatbot termine de responder")
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Box(modifier = Modifier.fillMaxWidth()) {

            // Botón de acción flotante arriba del row
            FloatingActionButton(
                onClick = {
                    scope.launch { listState.animateScrollToItem(0) }
                },
                containerColor = Color(0xFF4CAF50),
                modifier = Modifier
                    .align(Alignment.TopEnd)  // arriba a la derecha del Row
                    .zIndex(1f)               // siempre por encima del Row
                    .offset(y = if (showSnackbar) (-122).dp else (-64).dp)     // ajusta altura sobre el Row
                    .background(Color.Transparent)
            ) {
                Icon(
                    Icons.Default.ArrowUpward,
                    contentDescription = "Subir al inicio",
                    tint = Color.White
                )
            }

            // Row con TextField + Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.Center
            ) {

                // Campo de texto para introducir mensajes
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Escribe un mensaje...", color = Color.Gray) },
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White, RoundedCornerShape(36.dp))
                        .background(Color.Transparent),
                    textStyle = TextStyle(fontSize = 16.sp, color = Color.Black),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        disabledContainerColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Botón de enviar
                Button(
                    onClick = {
                        if (viewModel.isTyping) {
                            scope.launch {
                                showSnackbar = true
                                delay(1500) // duración del mensaje
                                showSnackbar = false
                            }
                        } else {
                            if (text.isNotBlank()) {
                                viewModel.sendMessage(text)
                                text = ""
                            }
                        }
                    }, colors = ButtonDefaults.buttonColors(
                        containerColor = if (viewModel.isTyping) Color.Gray else Color(0xFF4CAF50) // Color de fondo de usuario
                    ), enabled = text.isNotBlank()

                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enviar", color = Color.White)
                }
            }
        }
    }
}

// Clase para los mensajes del chat
@Composable
fun MensajeChat(message: Message) {

    // Boolean que comprueba si el autor del mensaje es el usuario o no
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        // distinta orientación para cada parte
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .padding(4.dp)
                .background(
                    //Color(0xFF1976D2) 0xFF4CAF50 0xFFA5D6A7
                    // distinto color de mensaje para cada parte
                    if (isUser) Color(0xFFA5D6A7) else Color.White, RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
                .widthIn(max = 280.dp)
        ) {
            Text(
                text = message.content, color = if (isUser) Color.White else Color.Black
            )
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .padding(4.dp)
                .background(
                    Color.White, RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            Text(
                text = "Pensando...", color = Color.Gray
            )
        }
    }
}