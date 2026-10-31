package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class CategoryColorOption(
    val hex: String,
    val name: String
)

val CategoryColorPalette = listOf(
    CategoryColorOption("#2563EB", "Blue"),
    CategoryColorOption("#7C3AED", "Purple"),
    CategoryColorOption("#059669", "Emerald"),
    CategoryColorOption("#0284C7", "Sky"),
    CategoryColorOption("#D97706", "Amber"),
    CategoryColorOption("#E11D48", "Rose"),
    CategoryColorOption("#DC2626", "Red"),
    CategoryColorOption("#0D9488", "Teal"),
    CategoryColorOption("#9333EA", "Violet"),
    CategoryColorOption("#64748B", "Slate")
)

val CategoryIconOptions = listOf(
    "Folder" to Icons.Default.Folder,
    "Work" to Icons.Default.Work,
    "Home" to Icons.Default.Home,
    "Receipt" to Icons.Default.Receipt,
    "School" to Icons.Default.School,
    "MenuBook" to Icons.Default.MenuBook,
    "Lock" to Icons.Default.Lock,
    "Help" to Icons.Default.Help,
    "Star" to Icons.Default.Star,
    "Tag" to Icons.Default.Tag,
    "AttachMoney" to Icons.Default.AttachMoney,
    "Description" to Icons.Default.Description
)

fun getCategoryIcon(iconName: String): ImageVector {
    return CategoryIconOptions.find { it.first.equals(iconName, ignoreCase = true) }?.second
        ?: Icons.Default.Folder
}

fun parseCategoryColor(colorHex: String, defaultColor: Color = Color(0xFF2563EB)): Color {
    return try {
        val cleanHex = colorHex.trim().removePrefix("#")
        val colorInt = when (cleanHex.length) {
            6 -> "FF$cleanHex".toLong(16).toInt()
            8 -> cleanHex.toLong(16).toInt()
            else -> return defaultColor
        }
        Color(colorInt)
    } catch (_: Exception) {
        defaultColor
    }
}
