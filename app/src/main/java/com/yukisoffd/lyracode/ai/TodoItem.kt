package com.yukisoffd.lyracode.ai

data class TodoItem(
    val id: String,
    val text: String,
    val status: String,
    val note: String = "",
)
