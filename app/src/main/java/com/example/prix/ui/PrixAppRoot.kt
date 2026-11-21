package com.example.prix.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.prix.PrixAppContainer
import com.example.prix.core.database.DocumentEntity
import com.example.prix.core.datastore.UserPreferences
import com.example.prix.core.error.PrixErrorMapper
import com.example.prix.core.error.PrixOperation
import com.example.prix.core.model.DocumentFilterTab
import com.example.prix.core.model.DocumentFormat
import com.example.prix.core.model.DocumentSortOption
import com.example.prix.core.model.StorageStats
import com.example.prix.core.storage.DocumentStorageManager
import com.example.prix.core.ui.ErrorRecoveryDialog
import com.example.prix.core.ui.GlassBottomBar
import com.example.prix.core.ui.GlassDialog
import com.example.prix.core.ui.GlassNavTab
import com.example.prix.core.ui.LiquidGlassBackground
import com.example.prix.feature.documents.DocumentsScreen
import com.example.prix.feature.home.HomeScreen
import com.example.prix.feature.legal.LegalDetailScreen
import com.example.prix.feature.pdfeditor.PdfEditorScreen
import com.example.prix.feature.pdfviewer.PdfViewerScreen
import com.example.prix.feature.scanner.ScannerScreen
import com.example.prix.feature.settings.SettingsScreen
import com.example.prix.feature.splash.AnimatedSplashOverlay
import com.example.prix.feature.splash.FirstLaunchOnboardingScreen
import com.example.prix.feature.tools.ImageToPdfScreen
import com.example.prix.feature.tools.ToolWorkspaceHostScreen
import com.example.prix.feature.tools.ToolsCenterScreen
import com.example.prix.ui.navigation.RouteDocuments
import com.example.prix.ui.navigation.RouteHome
import com.example.prix.ui.navigation.RouteImageToPdf
import com.example.prix.ui.navigation.RouteLegal
import com.example.prix.ui.navigation.RoutePdfEditor
import com.example.prix.ui.navigation.RoutePdfViewer
import com.example.prix.ui.navigation.RouteScanner
import com.example.prix.ui.navigation.RouteSettings
import com.example.prix.ui.navigation.RouteToolWorkspace
import com.example.prix.ui.navigation.RouteTools
import com.example.prix.ui.navigation.ToolIds
import com.example.ui.theme.LocalPrixEffects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PrixAppRoot(
    container: PrixAppContainer,
    preferences: UserPreferences,
    incomingPdfUri: Uri? = null,
    onConsumeIncomingPdfUri: () -> Unit = {},
    showSplashInitially: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = container.repository
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val effects = LocalPrixEffects.current

    val validDocuments by repository.validDocuments.collectAsStateWithLifecycle(initialValue = emptyList())
    val allFolders by repository.allFolders.collectAsStateWithLifecycle(initialValue = emptyList())
    val storageStats by repository.storageStats.collectAsStateWithLifecycle(initialValue = StorageStats())

    var splashVisible by remember { mutableStateOf(showSplashInitially) }

    // Documents Screen state
    var currentFolderId by remember { mutableStateOf<Long?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var activeFilterTab by remember { mutableStateOf(DocumentFilterTab.ALL) }
    var sortOption by remember { mutableStateOf(DocumentSortOption.DATE_DESC) }
    var isGridView by remember { mutableStateOf(false) }
    val selectedDocIds = remember { mutableStateListOf<Long>() }

    // Dialog states
    var renameDocTarget by remember { mutableStateOf<DocumentEntity?>(null) }
    var deleteDocTarget by remember { mutableStateOf<DocumentEntity?>(null) }
    var textDocViewerTarget by remember { mutableStateOf<DocumentEntity?>(null) }
    var showEditPdfPickerDialog by remember { mutableStateOf(false) }
    var errorDialogState by remember { mutableStateOf<Pair<String, String>?>(null) }

    val showMessage: (String) -> Unit = { msg ->
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    val safeStartActivity: (Intent, String) -> Unit = { intent, fallbackError ->
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            showMessage(fallbackError)
        } catch (_: Exception) {
            showMessage(fallbackError)
        }
    }

    val openDocument: (DocumentEntity) -> Unit = { doc ->
        when (doc.format) {
            DocumentFormat.PDF -> navController.navigate(RoutePdfViewer(doc.id))
            DocumentFormat.PNG, DocumentFormat.JPEG, DocumentFormat.WEBP ->
                navController.navigate(RouteToolWorkspace(ToolIds.IMAGE_TOOLKIT, doc.id))
            DocumentFormat.TXT -> textDocViewerTarget = doc
        }
    }

    // Handle incoming ACTION_VIEW PDF intent (cold start or warm onNewIntent)
    LaunchedEffect(incomingPdfUri) {
        if (incomingPdfUri != null) {
            val res = repository.importPdfUri(incomingPdfUri)
            onConsumeIncomingPdfUri()
            res.onSuccess { doc ->
                navController.navigate(RoutePdfViewer(doc.id))
            }.onFailure { err ->
                val mapped = PrixErrorMapper.map(PrixOperation.IMPORT_PDF, err)
                errorDialogState = mapped.title to mapped.toDisplayMessage()
            }
        }
    }

    val importPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = repository.importPdfUri(uri, currentFolderId)
                res.onSuccess { doc ->
                    showMessage("Imported ${doc.name}")
                    navController.navigate(RoutePdfViewer(doc.id))
                }.onFailure { err ->
                    val mapped = PrixErrorMapper.map(PrixOperation.IMPORT_PDF, err)
                    errorDialogState = mapped.title to mapped.toDisplayMessage()
                }
            }
        }
    }

    if (!preferences.hasSeenOnboarding && !splashVisible) {
        FirstLaunchOnboardingScreen(
            onGetStarted = {
                scope.launch { container.preferencesRepository.setHasSeenOnboarding(true) }
            }
        )
    } else {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val isTopLevelTab = currentDestination?.let { dest ->
            dest.hasRoute<RouteHome>() ||
                dest.hasRoute<RouteDocuments>() ||
                dest.hasRoute<RouteTools>() ||
                dest.hasRoute<RouteSettings>()
        } ?: true

        val selectedTabIndex = when {
            currentDestination?.hasRoute<RouteDocuments>() == true -> 1
            currentDestination?.hasRoute<RouteTools>() == true -> 2
            currentDestination?.hasRoute<RouteSettings>() == true -> 3
            else -> 0
        }

        val glassTabs = remember {
            PrixTopTabs.map { tab ->
                GlassNavTab(
                    label = tab.label,
                    selectedIcon = tab.selectedIcon,
                    unselectedIcon = tab.unselectedIcon,
                    testTag = "bottom_nav_${tab.label.lowercase()}"
                )
            }
        }

        LiquidGlassBackground {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 600.dp

                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.padding(bottom = if (isTopLevelTab && !useRail) 84.dp else 0.dp)
                        )
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (isTopLevelTab) {
                                        Modifier
                                            .statusBarsPadding()
                                            .padding(
                                                start = innerPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                                                end = innerPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
                                            )
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            if (isTopLevelTab && useRail) {
                                NavigationRail(containerColor = Color.Transparent) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    PrixTopTabs.forEach { tab ->
                                        val selected = currentDestination?.let { tab.matches(it) } ?: false
                                        NavigationRailItem(
                                            selected = selected,
                                            onClick = {
                                                navController.navigate(tab.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                            icon = {
                                                Icon(
                                                    if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = tab.label
                                                )
                                            },
                                            label = { Text(tab.label) }
                                        )
                                    }
                                }
                            }

                            val navEnter = if (effects.shouldAnimate) {
                                fadeIn(tween(220)) + slideInHorizontally(tween(240)) { it / 18 }
                            } else {
                                EnterTransition.None
                            }
                            val navExit = if (effects.shouldAnimate) {
                                fadeOut(tween(180)) + slideOutHorizontally(tween(200)) { -it / 24 }
                            } else {
                                ExitTransition.None
                            }
                            val navPopEnter = if (effects.shouldAnimate) {
                                fadeIn(tween(200)) + slideInHorizontally(tween(220)) { -it / 18 }
                            } else {
                                EnterTransition.None
                            }
                            val navPopExit = if (effects.shouldAnimate) {
                                fadeOut(tween(180)) + slideOutHorizontally(tween(200)) { it / 24 }
                            } else {
                                ExitTransition.None
                            }

                            NavHost(
                                navController = navController,
                                startDestination = RouteHome,
                                enterTransition = { navEnter },
                                exitTransition = { navExit },
                                popEnterTransition = { navPopEnter },
                                popExitTransition = { navPopExit },
                                modifier = Modifier.weight(1f)
                            ) {
                                composable<RouteHome> {
                                    HomeScreen(
                                        recentDocuments = validDocuments,
                                        storageStats = storageStats,
                                        onSearchClick = { navController.navigate(RouteDocuments(focusSearch = true)) },
                                        onSettingsClick = { navController.navigate(RouteSettings) },
                                        onScanClick = { navController.navigate(RouteScanner) },
                                        onImagesToPdfClick = { navController.navigate(RouteImageToPdf) },
                                        onEditPdfClick = { showEditPdfPickerDialog = true },
                                        onImportPdfClick = { importPdfLauncher.launch(arrayOf("application/pdf")) },
                                        onQuickToolClick = { toolId ->
                                            navController.navigate(RouteToolWorkspace(toolId = toolId))
                                        },
                                        onSeeAllDocuments = { navController.navigate(RouteDocuments()) },
                                        onOpenDocument = openDocument,
                                        onToggleStar = { doc ->
                                            scope.launch { repository.toggleStar(doc.id, !doc.isStarred) }
                                        },
                                        onRenameDocument = { renameDocTarget = it },
                                        onDuplicateDocument = { doc ->
                                            scope.launch {
                                                repository.duplicateDocument(doc.id)
                                                    .onSuccess { showMessage("Duplicated ${doc.name}") }
                                                    .onFailure { showMessage(it.localizedMessage ?: "Duplicate failed") }
                                            }
                                        },
                                        onShareDocument = { doc ->
                                            safeStartActivity(
                                                repository.storage.buildShareIntent(File(doc.filePath), doc.mimeType, doc.name),
                                                "No app available to share this file."
                                            )
                                        },
                                        onDeleteDocument = { deleteDocTarget = it }
                                    )
                                }

                                composable<RouteDocuments> { entry ->
                                    val docRoute = entry.toRoute<RouteDocuments>()
                                    DocumentsScreen(
                                        allDocuments = validDocuments,
                                        allFolders = allFolders,
                                        currentFolderId = currentFolderId,
                                        searchQuery = searchQuery,
                                        activeTab = activeFilterTab,
                                        sortOption = sortOption,
                                        isGridView = isGridView,
                                        selectedIds = selectedDocIds.toSet(),
                                        focusSearchInitially = docRoute.focusSearch,
                                        onSearchChange = { searchQuery = it },
                                        onTabChange = { activeFilterTab = it },
                                        onSortChange = { sortOption = it },
                                        onToggleGridView = { isGridView = !isGridView },
                                        onOpenFolder = { currentFolderId = it },
                                        onCreateFolder = { name ->
                                            scope.launch {
                                                repository.createFolder(name, currentFolderId)
                                                    .onSuccess { showMessage("Created folder $name") }
                                                    .onFailure { showMessage(it.localizedMessage ?: "Could not create folder") }
                                            }
                                        },
                                        onRenameFolder = { folder, newName ->
                                            scope.launch { repository.renameFolder(folder.id, newName) }
                                        },
                                        onDeleteFolder = { folder ->
                                            scope.launch {
                                                repository.deleteFolder(folder.id)
                                                if (currentFolderId == folder.id) currentFolderId = folder.parentFolderId
                                            }
                                        },
                                        onOpenDocument = openDocument,
                                        onToggleSelection = { id ->
                                            if (id in selectedDocIds) selectedDocIds.remove(id) else selectedDocIds.add(id)
                                        },
                                        onClearSelection = { selectedDocIds.clear() },
                                        onToggleStar = { doc ->
                                            scope.launch { repository.toggleStar(doc.id, !doc.isStarred) }
                                        },
                                        onRenameDocument = { renameDocTarget = it },
                                        onDuplicateDocument = { doc ->
                                            scope.launch {
                                                repository.duplicateDocument(doc.id)
                                                    .onSuccess { showMessage("Duplicated ${doc.name}") }
                                                    .onFailure { err ->
                                                        val mapped = PrixErrorMapper.map(PrixOperation.CREATE_PDF, err)
                                                        errorDialogState = mapped.title to mapped.toDisplayMessage()
                                                    }
                                            }
                                        },
                                        onShareDocument = { doc ->
                                            safeStartActivity(
                                                repository.storage.buildShareIntent(File(doc.filePath), doc.mimeType, doc.name),
                                                "No app available to share this file."
                                            )
                                        },
                                        onDeleteDocument = { deleteDocTarget = it },
                                        onBatchDelete = { ids ->
                                            scope.launch {
                                                repository.deleteDocuments(ids)
                                                    .onSuccess { count ->
                                                        selectedDocIds.clear()
                                                        showMessage("Deleted $count document(s)")
                                                    }
                                                    .onFailure { err ->
                                                        selectedDocIds.clear()
                                                        val mapped = PrixErrorMapper.map(PrixOperation.DELETE_DOCUMENT, err)
                                                        errorDialogState = mapped.title to mapped.toDisplayMessage()
                                                    }
                                            }
                                        },
                                        onBatchMoveToFolder = { ids, folderId ->
                                            scope.launch {
                                                repository.moveDocumentsToFolder(ids, folderId)
                                                selectedDocIds.clear()
                                                showMessage("Moved ${ids.size} document(s)")
                                            }
                                        },
                                        onBatchShare = { ids ->
                                            val docsToShare = validDocuments.filter { it.id in ids }
                                            if (docsToShare.isNotEmpty()) {
                                                val intent = repository.storage.buildShareMultipleIntent(
                                                    docsToShare.map { File(it.filePath) to it.mimeType },
                                                    title = "PRIX Documents (${docsToShare.size})"
                                                )
                                                safeStartActivity(intent, "No app available to share these files.")
                                            }
                                        },
                                        onBatchMergePdfs = { pdfIds ->
                                            scope.launch {
                                                repository.mergePdfs(pdfIds, "Merged_${DocumentStorageManager.timestampTag()}")
                                                    .onSuccess { merged ->
                                                        selectedDocIds.clear()
                                                        showMessage("Merged into ${merged.name}")
                                                        navController.navigate(RoutePdfViewer(merged.id))
                                                    }
                                                    .onFailure { err ->
                                                        val mapped = PrixErrorMapper.map(PrixOperation.MERGE_PDF, err)
                                                        errorDialogState = mapped.title to mapped.toDisplayMessage()
                                                    }
                                            }
                                        },
                                        onScanFirstDocument = { navController.navigate(RouteScanner) }
                                    )
                                }

                                composable<RouteTools> {
                                    ToolsCenterScreen(
                                        onOpenScanner = { navController.navigate(RouteScanner) },
                                        onOpenImagesToPdf = { navController.navigate(RouteImageToPdf) },
                                        onOpenTool = { toolId -> navController.navigate(RouteToolWorkspace(toolId = toolId)) }
                                    )
                                }

                                composable<RouteSettings> {
                                    SettingsScreen(
                                        preferences = preferences,
                                        preferencesRepo = container.preferencesRepository,
                                        folders = allFolders,
                                        storageStats = storageStats,
                                        onClearTempFiles = {
                                            scope.launch {
                                                val freed = withContext(Dispatchers.IO) {
                                                    repository.storage.clearTempFiles()
                                                }
                                                showMessage("Cleared ${DocumentStorageManager.formatFileSize(freed)} of temporary files.")
                                            }
                                        },
                                        onOpenSignatureManager = {
                                            navController.navigate(RouteToolWorkspace(ToolIds.SIGNATURE_LIBRARY))
                                        },
                                        onOpenLegal = { section -> navController.navigate(RouteLegal(section)) }
                                    )
                                }

                                composable<RouteScanner> {
                                    ScannerScreen(
                                        repository = repository,
                                        preferences = preferences,
                                        folders = allFolders,
                                        onClose = { navController.popBackStack() },
                                        onSavedPdf = { saved ->
                                            navController.popBackStack()
                                            navController.navigate(RoutePdfViewer(saved.id))
                                        }
                                    )
                                }

                                composable<RouteImageToPdf> {
                                    ImageToPdfScreen(
                                        repository = repository,
                                        preferences = preferences,
                                        onBack = { navController.popBackStack() },
                                        onOpenScanner = {
                                            navController.popBackStack()
                                            navController.navigate(RouteScanner)
                                        },
                                        onCreatedPdf = { saved ->
                                            navController.popBackStack()
                                            navController.navigate(RoutePdfViewer(saved.id))
                                        }
                                    )
                                }

                                composable<RoutePdfViewer> { entry ->
                                    val route = entry.toRoute<RoutePdfViewer>()
                                    PdfViewerScreen(
                                        documentId = route.documentId,
                                        repository = repository,
                                        onBack = { navController.popBackStack() },
                                        onOpenEditor = { id -> navController.navigate(RoutePdfEditor(id)) },
                                        onOpenTool = { toolId, id -> navController.navigate(RouteToolWorkspace(toolId, id)) },
                                        onShowMessage = showMessage
                                    )
                                }

                                composable<RoutePdfEditor> { entry ->
                                    val route = entry.toRoute<RoutePdfEditor>()
                                    PdfEditorScreen(
                                        documentId = route.documentId,
                                        repository = repository,
                                        startWithSignature = false,
                                        onBack = { navController.popBackStack() },
                                        onSavedDocument = { saved ->
                                            navController.popBackStack()
                                            navController.navigate(RoutePdfViewer(saved.id))
                                        }
                                    )
                                }

                                composable<RouteToolWorkspace> { entry ->
                                    val route = entry.toRoute<RouteToolWorkspace>()
                                    ToolWorkspaceHostScreen(
                                        toolId = route.toolId,
                                        initialDocumentId = route.initialDocumentId,
                                        repository = repository,
                                        onBack = { navController.popBackStack() },
                                        onOpenViewer = { id ->
                                            navController.popBackStack()
                                            navController.navigate(RoutePdfViewer(id))
                                        },
                                        onShowMessage = showMessage
                                    )
                                }

                                composable<RouteLegal> { entry ->
                                    val route = entry.toRoute<RouteLegal>()
                                    LegalDetailScreen(
                                        section = route.section,
                                        preferences = preferences,
                                        preferencesRepo = container.preferencesRepository,
                                        onBack = { navController.popBackStack() },
                                        onNavigateSection = { next ->
                                            navController.navigate(RouteLegal(next))
                                        }
                                    )
                                }
                            }
                        }

                        if (isTopLevelTab && !useRail) {
                            GlassBottomBar(
                                tabs = glassTabs,
                                selectedIndex = selectedTabIndex,
                                onSelect = { index ->
                                    val tab = PrixTopTabs[index]
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                }
            }
        }
    }

    // Animated Splash Overlay
    AnimatedSplashOverlay(
        visible = splashVisible,
        animationsEnabled = preferences.animationsEnabled,
        onFinished = { splashVisible = false }
    )

    // Dialogs
    renameDocTarget?.let { target ->
        var newName by remember(target.id) { mutableStateOf(target.name) }
        GlassDialog(
            onDismissRequest = { renameDocTarget = null },
            title = "Rename Document",
            content = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("File name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        repository.renameDocument(target.id, newName)
                        renameDocTarget = null
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameDocTarget = null }) { Text("Cancel") }
            }
        )
    }

    deleteDocTarget?.let { target ->
        GlassDialog(
            onDismissRequest = { deleteDocTarget = null },
            title = "Delete Document?",
            content = {
                Text("Are you sure you want to permanently remove \"${target.name}\" from this device?")
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val res = repository.deleteDocuments(listOf(target.id))
                        deleteDocTarget = null
                        res.onSuccess {
                            showMessage("Deleted ${target.name}")
                        }.onFailure { err ->
                            val mapped = PrixErrorMapper.map(PrixOperation.DELETE_DOCUMENT, err)
                            errorDialogState = mapped.title to mapped.toDisplayMessage()
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteDocTarget = null }) { Text("Cancel") }
            }
        )
    }

    textDocViewerTarget?.let { txtDoc ->
        var content by remember(txtDoc.id) { mutableStateOf("Loading…") }
        LaunchedEffect(txtDoc.id) {
            content = withContext(Dispatchers.IO) {
                try { File(txtDoc.filePath).readText() } catch (_: Exception) { "Unable to read text file." }
            }
        }
        GlassDialog(
            onDismissRequest = { textDocViewerTarget = null },
            title = txtDoc.name,
            content = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(content, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText(txtDoc.name, content))
                        showMessage("Copied text to clipboard.")
                    } else {
                        showMessage("Clipboard unavailable on this device.")
                    }
                    textDocViewerTarget = null
                }) { Text("Copy") }
            },
            dismissButton = {
                TextButton(onClick = { textDocViewerTarget = null }) { Text("Close") }
            }
        )
    }

    if (showEditPdfPickerDialog) {
        val pdfs = validDocuments.filter { it.format == DocumentFormat.PDF }
        GlassDialog(
            onDismissRequest = { showEditPdfPickerDialog = false },
            title = "Choose a PDF to Edit",
            content = {
                if (pdfs.isEmpty()) {
                    Text("No PDFs in your vault yet. Import a PDF or scan a document first.")
                } else {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pdfs.forEach { pdf ->
                            TextButton(
                                onClick = {
                                    showEditPdfPickerDialog = false
                                    navController.navigate(RoutePdfEditor(pdf.id))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(pdf.name, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (pdfs.isEmpty()) {
                    Button(onClick = {
                        showEditPdfPickerDialog = false
                        importPdfLauncher.launch(arrayOf("application/pdf"))
                    }) { Text("Import PDF") }
                } else {
                    TextButton(onClick = { showEditPdfPickerDialog = false }) { Text("Cancel") }
                }
            }
        )
    }

    errorDialogState?.let { (title, message) ->
        ErrorRecoveryDialog(
            title = title,
            message = message,
            onDismiss = { errorDialogState = null }
        )
    }
}

private data class PrixTopTab(
    val route: Any,
    val label: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val matches: (androidx.navigation.NavDestination) -> Boolean
)

private val PrixTopTabs = listOf(
    PrixTopTab(
        route = RouteHome,
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        matches = { it.hasRoute<RouteHome>() }
    ),
    PrixTopTab(
        route = RouteDocuments(),
        label = "Documents",
        selectedIcon = Icons.Filled.Folder,
        unselectedIcon = Icons.Outlined.Folder,
        matches = { it.hasRoute<RouteDocuments>() }
    ),
    PrixTopTab(
        route = RouteTools,
        label = "Tools",
        selectedIcon = Icons.Filled.Build,
        unselectedIcon = Icons.Outlined.Build,
        matches = { it.hasRoute<RouteTools>() }
    ),
    PrixTopTab(
        route = RouteSettings,
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        matches = { it.hasRoute<RouteSettings>() }
    )
)
