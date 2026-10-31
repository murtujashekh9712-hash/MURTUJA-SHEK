package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PdfDocumentEntity
import com.example.ui.components.PdfGridCard
import com.example.ui.components.PdfListCard
import com.example.ui.components.getCategoryIcon
import com.example.ui.components.parseCategoryColor
import com.example.ui.dialogs.CreateCategoryDialog
import com.example.ui.dialogs.CreatePdfDialog
import com.example.ui.dialogs.DeleteCategoryConfirmationDialog
import com.example.ui.dialogs.DeleteConfirmationDialog
import com.example.ui.dialogs.EditCategoryDialog
import com.example.ui.dialogs.EditDocumentDialog
import com.example.ui.dialogs.ImageToPdfDialog
import com.example.ui.dialogs.ManageCategoriesSheet
import com.example.ui.viewmodel.PdfViewModel
import com.example.ui.viewmodel.SortOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: PdfViewModel,
    onOpenPdf: (PdfDocumentEntity) -> Unit,
    onImportPdfClick: () -> Unit
) {
    val context = LocalContext.current
    val pdfs by viewModel.filteredPdfs.collectAsStateWithLifecycle()
    val allPdfsList by viewModel.allPdfs.collectAsStateWithLifecycle(emptyList())
    val stats by viewModel.storageStats.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val isGridView by viewModel.isGridView.collectAsStateWithLifecycle()

    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val categoryCounts by viewModel.categoryCounts.collectAsStateWithLifecycle()

    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val editingDocument by viewModel.editingDocument.collectAsStateWithLifecycle()
    val deletingDocument by viewModel.deletingDocument.collectAsStateWithLifecycle()
    val showCreateDialog by viewModel.showCreateDialog.collectAsStateWithLifecycle()
    val showScanDialog by viewModel.showScanDialog.collectAsStateWithLifecycle()

    val showManageCategoriesSheet by viewModel.showManageCategoriesSheet.collectAsStateWithLifecycle()
    val showCreateCategoryDialog by viewModel.showCreateCategoryDialog.collectAsStateWithLifecycle()
    val editingCategory by viewModel.editingCategory.collectAsStateWithLifecycle()
    val deletingCategory by viewModel.deletingCategory.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var isFabMenuExpanded by remember { mutableStateOf(false) }

    val categoryNamesList = remember(categories) {
        categories.map { it.name }
    }

    val countMap = remember(categoryCounts) {
        categoryCounts.associate { it.category.lowercase() to it.count }
    }

    val favoritesCount = remember(allPdfsList) {
        allPdfsList.count { it.isFavorite }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp)
            ) {
                // Secondary action: Scan images to PDF
                AnimatedVisibility(
                    visible = isFabMenuExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 4.dp
                        ) {
                            Text(
                                "Images to PDF",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FloatingActionButton(
                            onClick = {
                                isFabMenuExpanded = false
                                viewModel.setShowScanDialog(true)
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "Scan Images to PDF")
                        }
                    }
                }

                // Secondary action: Create PDF Document
                AnimatedVisibility(
                    visible = isFabMenuExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 4.dp
                        ) {
                            Text(
                                "Create Document",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FloatingActionButton(
                            onClick = {
                                isFabMenuExpanded = false
                                viewModel.setShowCreateDialog(true)
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("create_doc_fab_action")
                        ) {
                            Icon(Icons.Default.NoteAdd, contentDescription = "Create Document")
                        }
                    }
                }

                // Secondary action: Import PDF from files
                AnimatedVisibility(
                    visible = isFabMenuExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 4.dp
                        ) {
                            Text(
                                "Import PDF File",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FloatingActionButton(
                            onClick = {
                                isFabMenuExpanded = false
                                onImportPdfClick()
                            },
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("import_pdf_fab_action")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = "Import PDF")
                        }
                    }
                }

                // Main FAB toggle
                FloatingActionButton(
                    onClick = { isFabMenuExpanded = !isFabMenuExpanded },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("main_add_fab")
                ) {
                    Icon(
                        imageVector = if (isFabMenuExpanded) Icons.Default.Clear else Icons.Default.Add,
                        contentDescription = "Add or Import PDF"
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // App Header
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = "PDF Stored",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Categorized Document Vault",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Category manager button
                            IconButton(
                                onClick = { viewModel.setShowManageCategoriesSheet(true) },
                                modifier = Modifier.testTag("manage_categories_top_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Category,
                                    contentDescription = "Manage Categories",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // View mode toggle
                            IconButton(
                                onClick = { viewModel.toggleViewMode() },
                                modifier = Modifier.testTag("toggle_view_mode_button")
                            ) {
                                Icon(
                                    imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                    contentDescription = "Toggle Grid or List view",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Sort menu
                            Box {
                                IconButton(
                                    onClick = { sortMenuExpanded = true },
                                    modifier = Modifier.testTag("sort_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = "Sort options",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                DropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false }
                                ) {
                                    SortOption.values().forEach { option ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    option.label,
                                                    fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (sortOption == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            },
                                            onClick = {
                                                viewModel.setSortOption(option)
                                                sortMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search by title, category, or notes...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_text_field"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                }
            }

            // Storage Overview Stat Bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Vault Storage",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${stats.totalCount} Documents • ${stats.formattedTotalSize}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${categories.size} Categories",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${stats.totalPages} Pgs",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Category Chips Row (Dynamic from Room)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "All" chip
                val isAllSelected = selectedCategory.equals("All", ignoreCase = true)
                FilterChip(
                    selected = isAllSelected,
                    onClick = { viewModel.setCategory("All") },
                    label = {
                        Text(
                            text = "All (${stats.totalCount})",
                            fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("filter_chip_All")
                )

                // "Favorites" chip
                val isFavSelected = selectedCategory.equals("Favorites", ignoreCase = true)
                FilterChip(
                    selected = isFavSelected,
                    onClick = { viewModel.setCategory("Favorites") },
                    label = {
                        Text(
                            text = "⭐ Favorites ($favoritesCount)",
                            fontWeight = if (isFavSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFF59E0B),
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("filter_chip_Favorites")
                )

                // Dynamic Room Categories
                categories.forEach { cat ->
                    val isCatSelected = selectedCategory.equals(cat.name, ignoreCase = true)
                    val count = countMap[cat.name.lowercase()] ?: 0
                    val catColor = parseCategoryColor(cat.colorHex)
                    val icon = getCategoryIcon(cat.iconName)

                    FilterChip(
                        selected = isCatSelected,
                        onClick = { viewModel.setCategory(cat.name) },
                        leadingIcon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isCatSelected) Color.White else catColor,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "${cat.name} ($count)",
                                fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = catColor,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("filter_chip_${cat.name}")
                    )
                }

                // Add / Manage Categories Shortcut Chip
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .clickable { viewModel.setShowManageCategoriesSheet(true) }
                        .testTag("open_manage_categories_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Manage",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Documents Content (Grid or List)
            if (pdfs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching documents" else "No PDFs in \"$selectedCategory\"",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (searchQuery.isNotEmpty()) "Try searching with different keywords" else "Import an existing PDF from your device or create a new document in this category.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row {
                            TextButton(onClick = onImportPdfClick) {
                                Icon(Icons.Default.UploadFile, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import PDF")
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            TextButton(onClick = { viewModel.setShowCreateDialog(true) }) {
                                Icon(Icons.Default.NoteAdd, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Document")
                            }
                        }
                    }
                }
            } else {
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("pdf_grid_view")
                    ) {
                        items(pdfs, key = { it.id }) { pdf ->
                            PdfGridCard(
                                pdf = pdf,
                                categories = categories,
                                onClick = { onOpenPdf(pdf) },
                                onToggleFavorite = { viewModel.toggleFavorite(pdf) },
                                onShare = {
                                    viewModel.getShareIntent(pdf)?.let { shareIntent ->
                                        context.startActivity(shareIntent)
                                    }
                                },
                                onEdit = { viewModel.setEditingDocument(pdf) },
                                onDelete = { viewModel.confirmDelete(pdf) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("pdf_list_view")
                    ) {
                        items(pdfs, key = { it.id }) { pdf ->
                            PdfListCard(
                                pdf = pdf,
                                categories = categories,
                                onClick = { onOpenPdf(pdf) },
                                onToggleFavorite = { viewModel.toggleFavorite(pdf) },
                                onShare = {
                                    viewModel.getShareIntent(pdf)?.let { shareIntent ->
                                        context.startActivity(shareIntent)
                                    }
                                },
                                onEdit = { viewModel.setEditingDocument(pdf) },
                                onDelete = { viewModel.confirmDelete(pdf) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Create PDF Dialog
    if (showCreateDialog) {
        CreatePdfDialog(
            availableCategories = categoryNamesList,
            onDismiss = { viewModel.setShowCreateDialog(false) },
            onAddNewCategory = { viewModel.setShowCreateCategoryDialog(true) },
            onCreate = { title, author, category, content, notes ->
                viewModel.createDocument(title, author, category, content, notes)
            }
        )
    }

    // Image to PDF Scanner Dialog
    if (showScanDialog) {
        ImageToPdfDialog(
            availableCategories = categoryNamesList,
            onDismiss = { viewModel.setShowScanDialog(false) },
            onAddNewCategory = { viewModel.setShowCreateCategoryDialog(true) },
            onConvert = { title, category, uris ->
                viewModel.scanImagesToPdf(title, category, uris)
            }
        )
    }

    // Edit Document Dialog
    editingDocument?.let { doc ->
        EditDocumentDialog(
            pdf = doc,
            availableCategories = categoryNamesList,
            onDismiss = { viewModel.setEditingDocument(null) },
            onAddNewCategory = { viewModel.setShowCreateCategoryDialog(true) },
            onSave = { title, category, notes ->
                viewModel.updateDocumentMetadata(doc.id, title, category, notes)
            }
        )
    }

    // Delete Document Confirmation Dialog
    deletingDocument?.let { doc ->
        DeleteConfirmationDialog(
            pdf = doc,
            onDismiss = { viewModel.cancelDelete() },
            onConfirm = { viewModel.executeDelete() }
        )
    }

    // Manage Categories Bottom Sheet
    if (showManageCategoriesSheet) {
        ManageCategoriesSheet(
            categories = categories,
            categoryCounts = categoryCounts,
            onDismiss = { viewModel.setShowManageCategoriesSheet(false) },
            onAddNewCategory = { viewModel.setShowCreateCategoryDialog(true) },
            onEditCategory = { cat -> viewModel.setEditingCategory(cat) },
            onDeleteCategory = { cat -> viewModel.setDeletingCategory(cat) }
        )
    }

    // Create Category Dialog
    if (showCreateCategoryDialog) {
        CreateCategoryDialog(
            onDismiss = { viewModel.setShowCreateCategoryDialog(false) },
            onCreate = { name, colorHex, iconName ->
                viewModel.createCategory(name, colorHex, iconName)
            }
        )
    }

    // Edit Category Dialog
    editingCategory?.let { cat ->
        EditCategoryDialog(
            category = cat,
            onDismiss = { viewModel.setEditingCategory(null) },
            onSave = { name, colorHex, iconName ->
                viewModel.updateCategory(cat.copy(name = name, colorHex = colorHex, iconName = iconName), cat.name)
            }
        )
    }

    // Delete Category Confirmation Dialog
    deletingCategory?.let { cat ->
        DeleteCategoryConfirmationDialog(
            category = cat,
            onDismiss = { viewModel.setDeletingCategory(null) },
            onConfirm = { viewModel.deleteCategory(cat) }
        )
    }
}
