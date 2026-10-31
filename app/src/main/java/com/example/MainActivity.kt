package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.PdfViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PdfStoredApp()
                }
            }
        }
    }
}

@Composable
fun PdfStoredApp(
    viewModel: PdfViewModel = viewModel()
) {
    val activePdfId by viewModel.activePdfId.collectAsStateWithLifecycle()

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importPdf(it)
        }
    }

    Crossfade(
        targetState = activePdfId,
        label = "screen_transition"
    ) { currentId ->
        if (currentId != null) {
            PdfViewerScreen(
                viewModel = viewModel,
                onBack = { viewModel.closeDocument() }
            )
        } else {
            LibraryScreen(
                viewModel = viewModel,
                onOpenPdf = { pdf -> viewModel.openDocument(pdf) },
                onImportPdfClick = {
                    openDocumentLauncher.launch(arrayOf("application/pdf"))
                }
            )
        }
    }
}
