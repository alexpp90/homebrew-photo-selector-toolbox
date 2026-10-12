package com.photoselectortoolbox.ui.inspect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.Grid4x4
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.photoselectortoolbox.data.model.ImageItem
import com.photoselectortoolbox.domain.analysis.PhotoCoachEngine
import com.photoselectortoolbox.domain.analysis.PhotoCoachEngineImpl
import com.photoselectortoolbox.domain.format.ExifFormatter
import com.photoselectortoolbox.domain.model.CoachingTip
import com.photoselectortoolbox.domain.model.CompositionGuideType
import com.photoselectortoolbox.domain.model.CompositionResult
import com.photoselectortoolbox.domain.model.PhotoCoachReport
import com.photoselectortoolbox.domain.model.SpiralOrientation
import com.photoselectortoolbox.domain.model.TipSeverity
import com.photoselectortoolbox.ui.components.CompositionOverlay
import com.photoselectortoolbox.ui.components.EmptyStateCard
import com.photoselectortoolbox.ui.components.StarRatingBar
import com.photoselectortoolbox.ui.navigation.Screen
import com.photoselectortoolbox.ui.theme.Indigo500
import com.photoselectortoolbox.ui.theme.Indigo600
import com.photoselectortoolbox.ui.theme.PanelSurface
import com.photoselectortoolbox.ui.theme.ScoreBad
import com.photoselectortoolbox.ui.theme.ScoreGood
import com.photoselectortoolbox.ui.theme.ScoreWarn
import com.photoselectortoolbox.ui.theme.Zinc200
import com.photoselectortoolbox.ui.theme.Zinc400
import com.photoselectortoolbox.ui.theme.Zinc500
import com.photoselectortoolbox.ui.theme.Zinc700
import com.photoselectortoolbox.ui.theme.Zinc800
import com.photoselectortoolbox.ui.theme.Zinc900
import com.photoselectortoolbox.ui.theme.Zinc950
import com.photoselectortoolbox.viewmodel.SelectorViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cycles the 4-way focal eye orientation for the Fibonacci Golden Spiral in order:
 * TOP_LEFT -> TOP_RIGHT -> BOTTOM_RIGHT -> BOTTOM_LEFT -> TOP_LEFT.
 */
fun cycleSpiralOrientation(current: SpiralOrientation): SpiralOrientation = when (current) {
    SpiralOrientation.TOP_LEFT -> SpiralOrientation.TOP_RIGHT
    SpiralOrientation.TOP_RIGHT -> SpiralOrientation.BOTTOM_RIGHT
    SpiralOrientation.BOTTOM_RIGHT -> SpiralOrientation.BOTTOM_LEFT
    SpiralOrientation.BOTTOM_LEFT -> SpiralOrientation.TOP_LEFT
}

/**
 * Pure hardware keyboard hotkey handler for the Inspect & Learn workstation.
 *
 * Mappings:
 * - `L`, `I`, `Escape`: Return to Culling view (`onNavigateToSelector`).
 * - `Left Arrow`: Navigate to previous image.
 * - `Right Arrow`: Navigate to next image.
 * - `0`–`5` (and NumPad equivalents): Set rating (0 = clear/unrated, 1..5 = stars).
 */
internal fun handleInspectKey(
    key: Key,
    onNavigateToSelector: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSetRating: (Int) -> Unit,
): Boolean {
    return when (key) {
        Key.L, Key.I, Key.Escape -> {
            onNavigateToSelector()
            true
        }
        Key.DirectionLeft -> {
            onPrevious()
            true
        }
        Key.DirectionRight -> {
            onNext()
            true
        }
        Key.Zero, Key.NumPad0 -> {
            onSetRating(0)
            true
        }
        Key.One, Key.NumPad1 -> {
            onSetRating(1)
            true
        }
        Key.Two, Key.NumPad2 -> {
            onSetRating(2)
            true
        }
        Key.Three, Key.NumPad3 -> {
            onSetRating(3)
            true
        }
        Key.Four, Key.NumPad4 -> {
            onSetRating(4)
            true
        }
        Key.Five, Key.NumPad5 -> {
            onSetRating(5)
            true
        }
        else -> false
    }
}

/**
 * "Inspect & Learn" Workstation screen.
 *
 * Features:
 * 1. Single large photo preview overlaid with interactive vector composition guides (Rule of Thirds,
 *    Golden Ratio, Fibonacci Spiral with 4-way flip, Baroque/Sinister diagonals, Horizon crosshair).
 * 2. Photo Coach Card evaluating camera shake risk, reciprocal rule safe shutter comparison,
 *    sensor noise, dynamic range clipping, and EV compensation.
 * 3. EXIF technical readout and interactive 1–5 Star Rating Bar.
 * 4. Candidate thumbnail filmstrip with rating badges for fast navigation.
 * 5. Complete hardware keyboard shortcut support (`L`/`I`/`Escape`, `0`–`5`, `Left`/`Right`).
 */
@Composable
fun InspectScreen(
    windowSizeClass: WindowSizeClass,
    currentRoute: String? = null,
    onNavigate: (Screen) -> Unit = {},
    viewModel: SelectorViewModel = hiltViewModel(),
    photoCoachEngine: PhotoCoachEngine = remember { PhotoCoachEngineImpl() },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentImage = uiState.currentImage

    InspectScreenContent(
        currentImage = currentImage,
        images = uiState.filteredImages,
        currentIndex = uiState.filteredPosition.let { if (it > 0) it - 1 else uiState.currentIndex },
        windowSizeClass = windowSizeClass,
        onNavigateToSelector = { onNavigate(Screen.PhotoSelector) },
        onSelectImage = { filteredIdx ->
            val target = uiState.filteredImages.getOrNull(filteredIdx)
            if (target != null) {
                val globalIdx = uiState.images.indexOfFirst { it.uri == target.uri }
                if (globalIdx >= 0) {
                    viewModel.navigateToImage(globalIdx)
                }
            }
        },
        onPrevious = { viewModel.navigatePrevious() },
        onNext = { viewModel.navigateNext() },
        onSetRating = { item, rating -> viewModel.setRating(item, rating) },
        photoCoachEngine = photoCoachEngine,
    )
}

@Composable
internal fun InspectScreenContent(
    currentImage: ImageItem?,
    images: List<ImageItem>,
    currentIndex: Int,
    windowSizeClass: WindowSizeClass,
    onNavigateToSelector: () -> Unit,
    onSelectImage: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSetRating: (ImageItem, Int) -> Unit,
    modifier: Modifier = Modifier,
    photoCoachEngine: PhotoCoachEngine = remember { PhotoCoachEngineImpl() },
    compositionResult: CompositionResult? = null,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Composition overlay toggle states
    var activeGuides by remember {
        mutableStateOf<Set<CompositionGuideType>>(setOf(CompositionGuideType.RuleOfThirds))
    }
    var spiralOrientation by remember { mutableStateOf(SpiralOrientation.TOP_LEFT) }
    var showLeadingLines by remember { mutableStateOf(false) }
    var showMassDistribution by remember { mutableStateOf(false) }

    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Zinc950)
            .testTag("inspect_screen")
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                handleInspectKey(
                    key = event.key,
                    onNavigateToSelector = onNavigateToSelector,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onSetRating = { rating -> currentImage?.let { onSetRating(it, rating) } },
                )
            },
    ) {
        if (images.isEmpty() || currentImage == null) {
            EmptyStateCard(
                icon = Icons.Default.CenterFocusStrong,
                title = "No Photos to Inspect",
                description = "Choose a shoot folder in Culling to evaluate composition, exposure technique, and coaching guidance.",
                actionLabel = "Return to Culling",
                onAction = onNavigateToSelector,
                modifier = Modifier
                    .align(Alignment.Center)
                    .testTag("inspect_empty_state"),
            )
            return@Box
        }

        val coachReport = remember(currentImage) {
            photoCoachEngine.evaluate(currentImage.exifData, currentImage.scanResult)
        }

        if (isCompact) {
            // Compact layout (<600dp): Vertical stacked layout
            Column(modifier = Modifier.fillMaxSize()) {
                // Top controls bar
                InspectControlBar(
                    activeGuides = activeGuides,
                    spiralOrientation = spiralOrientation,
                    showLeadingLines = showLeadingLines,
                    showMassDistribution = showMassDistribution,
                    onToggleThirds = {
                        activeGuides = if (activeGuides.any { it is CompositionGuideType.RuleOfThirds }) {
                            activeGuides.filterNot { it is CompositionGuideType.RuleOfThirds }.toSet()
                        } else {
                            activeGuides + CompositionGuideType.RuleOfThirds
                        }
                    },
                    onToggleGoldenRatio = {
                        activeGuides = if (activeGuides.any { it is CompositionGuideType.GoldenRatio }) {
                            activeGuides.filterNot { it is CompositionGuideType.GoldenRatio }.toSet()
                        } else {
                            activeGuides + CompositionGuideType.GoldenRatio
                        }
                    },
                    onToggleSpiral = {
                        activeGuides = if (activeGuides.any { it is CompositionGuideType.FibonacciSpiral }) {
                            activeGuides.filterNot { it is CompositionGuideType.FibonacciSpiral }.toSet()
                        } else {
                            activeGuides + CompositionGuideType.FibonacciSpiral(spiralOrientation)
                        }
                    },
                    onFlipSpiral = {
                        val newOrientation = cycleSpiralOrientation(spiralOrientation)
                        spiralOrientation = newOrientation
                        if (activeGuides.any { it is CompositionGuideType.FibonacciSpiral }) {
                            activeGuides = activeGuides.filterNot { it is CompositionGuideType.FibonacciSpiral }.toSet() +
                                CompositionGuideType.FibonacciSpiral(newOrientation)
                        }
                    },
                    onToggleDiagonals = {
                        activeGuides = if (activeGuides.any { it is CompositionGuideType.BaroqueSinister }) {
                            activeGuides.filterNot { it is CompositionGuideType.BaroqueSinister }.toSet()
                        } else {
                            activeGuides + CompositionGuideType.BaroqueSinister
                        }
                    },
                    onToggleHorizon = {
                        activeGuides = if (activeGuides.any { it is CompositionGuideType.CrosshairHorizon }) {
                            activeGuides.filterNot { it is CompositionGuideType.CrosshairHorizon }.toSet()
                        } else {
                            activeGuides + CompositionGuideType.CrosshairHorizon
                        }
                    },
                    onToggleLeadingLines = { showLeadingLines = !showLeadingLines },
                    onToggleMassDistribution = { showMassDistribution = !showMassDistribution },
                    onNavigateToSelector = onNavigateToSelector,
                )

                // Central Image View with Overlays
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = currentImage.uri,
                        contentDescription = currentImage.fileName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    CompositionOverlay(
                        modifier = Modifier.fillMaxSize(),
                        imageAspectRatio = currentImage.aspectRatio ?: (3f / 2f),
                        activeGuides = activeGuides,
                        compositionResult = compositionResult,
                        showLeadingLines = showLeadingLines,
                        showMassDistribution = showMassDistribution,
                    )
                }

                // Compact side panels in scrollable container
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .background(PanelSurface)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    InspectRatingCard(
                        rating = currentImage.rating,
                        onRatingChanged = { rating -> onSetRating(currentImage, rating) },
                    )
                    PhotoCoachCard(
                        currentImage = currentImage,
                        coachReport = coachReport,
                    )
                    ExifTechnicalReadoutCard(currentImage = currentImage)
                }

                // Bottom Filmstrip
                InspectFilmstrip(
                    images = images,
                    currentIndex = currentIndex,
                    onSelectImage = onSelectImage,
                )
            }
        } else {
            // Expanded / Medium Layout (Tablet >=600dp & DeX): Row layout
            Row(modifier = Modifier.fillMaxSize()) {
                // Main Workspace: Control bar, Large Image with Overlay, Bottom Filmstrip
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    InspectControlBar(
                        activeGuides = activeGuides,
                        spiralOrientation = spiralOrientation,
                        showLeadingLines = showLeadingLines,
                        showMassDistribution = showMassDistribution,
                        onToggleThirds = {
                            activeGuides = if (activeGuides.any { it is CompositionGuideType.RuleOfThirds }) {
                                activeGuides.filterNot { it is CompositionGuideType.RuleOfThirds }.toSet()
                            } else {
                                activeGuides + CompositionGuideType.RuleOfThirds
                            }
                        },
                        onToggleGoldenRatio = {
                            activeGuides = if (activeGuides.any { it is CompositionGuideType.GoldenRatio }) {
                                activeGuides.filterNot { it is CompositionGuideType.GoldenRatio }.toSet()
                            } else {
                                activeGuides + CompositionGuideType.GoldenRatio
                            }
                        },
                        onToggleSpiral = {
                            activeGuides = if (activeGuides.any { it is CompositionGuideType.FibonacciSpiral }) {
                                activeGuides.filterNot { it is CompositionGuideType.FibonacciSpiral }.toSet()
                            } else {
                                activeGuides + CompositionGuideType.FibonacciSpiral(spiralOrientation)
                            }
                        },
                        onFlipSpiral = {
                            val newOrientation = cycleSpiralOrientation(spiralOrientation)
                            spiralOrientation = newOrientation
                            if (activeGuides.any { it is CompositionGuideType.FibonacciSpiral }) {
                                activeGuides = activeGuides.filterNot { it is CompositionGuideType.FibonacciSpiral }.toSet() +
                                    CompositionGuideType.FibonacciSpiral(newOrientation)
                            }
                        },
                        onToggleDiagonals = {
                            activeGuides = if (activeGuides.any { it is CompositionGuideType.BaroqueSinister }) {
                                activeGuides.filterNot { it is CompositionGuideType.BaroqueSinister }.toSet()
                            } else {
                                activeGuides + CompositionGuideType.BaroqueSinister
                            }
                        },
                        onToggleHorizon = {
                            activeGuides = if (activeGuides.any { it is CompositionGuideType.CrosshairHorizon }) {
                                activeGuides.filterNot { it is CompositionGuideType.CrosshairHorizon }.toSet()
                            } else {
                                activeGuides + CompositionGuideType.CrosshairHorizon
                            }
                        },
                        onToggleLeadingLines = { showLeadingLines = !showLeadingLines },
                        onToggleMassDistribution = { showMassDistribution = !showMassDistribution },
                        onNavigateToSelector = onNavigateToSelector,
                    )

                    // Large central image workspace
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = currentImage.uri,
                            contentDescription = currentImage.fileName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                        CompositionOverlay(
                            modifier = Modifier.fillMaxSize(),
                            imageAspectRatio = currentImage.aspectRatio ?: (3f / 2f),
                            activeGuides = activeGuides,
                            compositionResult = compositionResult,
                            showLeadingLines = showLeadingLines,
                            showMassDistribution = showMassDistribution,
                        )
                    }

                    // Bottom Filmstrip
                    InspectFilmstrip(
                        images = images,
                        currentIndex = currentIndex,
                        onSelectImage = onSelectImage,
                    )
                }

                // Right Flank Side Panel (Photo Coach, EXIF Readout, Rating)
                Column(
                    modifier = Modifier
                        .width(360.dp)
                        .fillMaxHeight()
                        .background(PanelSurface)
                        .border(1.dp, Zinc800, RoundedCornerShape(0.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    InspectRatingCard(
                        rating = currentImage.rating,
                        onRatingChanged = { rating -> onSetRating(currentImage, rating) },
                    )

                    PhotoCoachCard(
                        currentImage = currentImage,
                        coachReport = coachReport,
                    )

                    ExifTechnicalReadoutCard(currentImage = currentImage)
                }
            }
        }
    }
}

// ── Control Bar ───────────────────────────────────────────────────────────────

@Composable
private fun InspectControlBar(
    activeGuides: Set<CompositionGuideType>,
    spiralOrientation: SpiralOrientation,
    showLeadingLines: Boolean,
    showMassDistribution: Boolean,
    onToggleThirds: () -> Unit,
    onToggleGoldenRatio: () -> Unit,
    onToggleSpiral: () -> Unit,
    onFlipSpiral: () -> Unit,
    onToggleDiagonals: () -> Unit,
    onToggleHorizon: () -> Unit,
    onToggleLeadingLines: () -> Unit,
    onToggleMassDistribution: () -> Unit,
    onNavigateToSelector: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isThirdsActive = activeGuides.any { it is CompositionGuideType.RuleOfThirds }
    val isGoldenActive = activeGuides.any { it is CompositionGuideType.GoldenRatio }
    val isSpiralActive = activeGuides.any { it is CompositionGuideType.FibonacciSpiral }
    val isDiagonalsActive = activeGuides.any { it is CompositionGuideType.BaroqueSinister }
    val isHorizonActive = activeGuides.any { it is CompositionGuideType.CrosshairHorizon }

    val spiralOrientationLabel = when (spiralOrientation) {
        SpiralOrientation.TOP_LEFT -> "TL"
        SpiralOrientation.TOP_RIGHT -> "TR"
        SpiralOrientation.BOTTOM_RIGHT -> "BR"
        SpiralOrientation.BOTTOM_LEFT -> "BL"
    }

    Surface(
        color = Zinc900,
        contentColor = Zinc200,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Zinc800, RoundedCornerShape(0.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Return to Culling button
            IconButton(
                onClick = onNavigateToSelector,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("back_to_cull_button")
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Return to Culling (L / I / Escape)",
                    tint = Zinc400,
                )
            }

            Text(
                text = "Guides:",
                style = MaterialTheme.typography.labelSmall,
                color = Zinc500,
                modifier = Modifier.padding(end = 2.dp),
            )

            // Rule of Thirds
            GuideToggleButton(
                label = "Thirds",
                icon = Icons.Default.Grid3x3,
                active = isThirdsActive,
                onClick = onToggleThirds,
                testTag = "toggle_thirds",
                aliasTag = "nav_thirds",
            )

            // Golden Ratio Phi Grid
            GuideToggleButton(
                label = "Phi",
                icon = Icons.Default.Grid4x4,
                active = isGoldenActive,
                onClick = onToggleGoldenRatio,
                testTag = "toggle_golden_ratio",
            )

            // Fibonacci Spiral
            GuideToggleButton(
                label = "Spiral",
                icon = Icons.AutoMirrored.Filled.RotateRight,
                active = isSpiralActive,
                onClick = onToggleSpiral,
                testTag = "toggle_spiral",
            )

            // Spiral 4-way flip button
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isSpiralActive) Zinc800 else Zinc900,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSpiralActive) Indigo600 else Zinc700),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onFlipSpiral)
                    .testTag("toggle_spiral_flip")
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "Flip ($spiralOrientationLabel)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSpiralActive) Color.White else Zinc400,
                    fontWeight = FontWeight.Medium,
                )
            }

            // Diagonals (Baroque / Sinister)
            GuideToggleButton(
                label = "Diagonals",
                icon = Icons.Default.Timeline,
                active = isDiagonalsActive,
                onClick = onToggleDiagonals,
                testTag = "toggle_diagonals",
            )

            // Crosshair & Horizon
            GuideToggleButton(
                label = "Horizon",
                icon = Icons.Default.Tune,
                active = isHorizonActive,
                onClick = onToggleHorizon,
                testTag = "toggle_horizon",
            )

            Spacer(modifier = Modifier.weight(1f))

            // Leading Lines toggle
            GuideToggleButton(
                label = "Lines",
                icon = Icons.Default.LinearScale,
                active = showLeadingLines,
                onClick = onToggleLeadingLines,
                testTag = "toggle_leading_lines",
            )

            // Mass Centroid toggle
            GuideToggleButton(
                label = "Mass",
                icon = Icons.Default.Adjust,
                active = showMassDistribution,
                onClick = onToggleMassDistribution,
                testTag = "toggle_mass_centroid",
            )
        }
    }
}

@Composable
private fun GuideToggleButton(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String,
    aliasTag: String? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (active) Indigo600 else Zinc900,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) Indigo500 else Zinc700),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
            .then(if (aliasTag != null) Modifier.testTag(aliasTag) else Modifier)
            .pointerHoverIcon(PointerIcon.Hand),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (active) Color.White else Zinc400,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) Color.White else Zinc400,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

// ── Photo Coach Card ──────────────────────────────────────────────────────────

@Composable
private fun PhotoCoachCard(
    currentImage: ImageItem,
    coachReport: PhotoCoachReport,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Zinc900),
        border = androidx.compose.foundation.BorderStroke(1.dp, Zinc800),
        modifier = modifier
            .fillMaxWidth()
            .testTag("photo_coach_card"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = Indigo500,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Photo Coach",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }

            // Camera Shake Risk Meter
            val risk = coachReport.cameraShakeRisk
            val riskPct = (risk * 100).toInt()
            val (riskLabel, riskColor) = when {
                risk >= 0.7f -> "Critical Blur Risk ($riskPct%)" to ScoreBad
                risk >= 0.3f -> "Moderate Shake Risk ($riskPct%)" to ScoreWarn
                else -> "Low Shake Risk ($riskPct%)" to ScoreGood
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("camera_shake_badge")
                    .testTag("camera_shake_risk"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Camera Shake Risk:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Zinc400,
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = riskColor.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, riskColor.copy(alpha = 0.6f)),
                ) {
                    Text(
                        text = riskLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = riskColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            // Safe Shutter Speed Comparison
            val safeSpeed = coachReport.safeShutterSpeed
            val actualSpeed = currentImage.exifData?.shutterSpeed
            val safeSpeedFormatted = safeSpeed?.let { ExifFormatter.shutterSpeed(it) } ?: "—"
            val actualSpeedFormatted = actualSpeed?.let { ExifFormatter.shutterSpeed(it) } ?: "—"

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("shutter_speed_comparison"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Reciprocal Safe Shutter (1/f):",
                    style = MaterialTheme.typography.bodySmall,
                    color = Zinc400,
                )
                Text(
                    text = "$safeSpeedFormatted (Actual: $actualSpeedFormatted)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                )
            }

            // Recommended EV Compensation
            coachReport.recommendedEvCompensation?.let { ev ->
                val evFormatted = if (ev > 0.0) "+$ev" else "$ev"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recommended_ev"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Suggested EV Correction:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Zinc400,
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = ScoreWarn.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ScoreWarn.copy(alpha = 0.5f)),
                    ) {
                        Text(
                            text = "$evFormatted EV",
                            style = MaterialTheme.typography.labelSmall,
                            color = ScoreWarn,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            // Coaching Tips
            Text(
                text = "Technique Guidance:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Zinc400,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("coaching_tips_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (coachReport.tips.isEmpty()) {
                    Text(
                        text = "Clean capture: exposure parameters and sharpness are within optimal thresholds.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Zinc500,
                    )
                } else {
                    for (tip in coachReport.tips) {
                        CoachingTipItem(tip = tip)
                    }
                }
            }
        }
    }
}

@Composable
private fun CoachingTipItem(
    tip: CoachingTip,
    modifier: Modifier = Modifier,
) {
    val (chipColor, chipBg) = when (tip.severity) {
        TipSeverity.CRITICAL -> ScoreBad to ScoreBad.copy(alpha = 0.15f)
        TipSeverity.WARNING -> ScoreWarn to ScoreWarn.copy(alpha = 0.15f)
        TipSeverity.INFO -> Indigo500 to Indigo500.copy(alpha = 0.15f)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Zinc950,
        border = androidx.compose.foundation.BorderStroke(1.dp, Zinc800),
        modifier = modifier
            .fillMaxWidth()
            .testTag("coaching_tip_item"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = tip.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tip_title"),
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = chipBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, chipColor.copy(alpha = 0.5f)),
                    modifier = Modifier.testTag("tip_severity_chip"),
                ) {
                    Text(
                        text = tip.severity.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = chipColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Text(
                text = tip.description,
                style = MaterialTheme.typography.bodySmall,
                color = Zinc400,
                modifier = Modifier.testTag("tip_description"),
            )

            Text(
                text = "💡 ${tip.recommendation}",
                style = MaterialTheme.typography.bodySmall,
                color = Zinc200,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("tip_recommendation"),
            )
        }
    }
}

// ── EXIF Technical Readout ────────────────────────────────────────────────────

@Composable
private fun ExifTechnicalReadoutCard(
    currentImage: ImageItem,
    modifier: Modifier = Modifier,
) {
    val exif = currentImage.exifData
    val rows = ExifFormatter.detailRows(exif)

    val formattedDate = remember(currentImage.lastModified) {
        if (currentImage.lastModified > 0) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(currentImage.lastModified))
        } else {
            "Unknown"
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Zinc900),
        border = androidx.compose.foundation.BorderStroke(1.dp, Zinc800),
        modifier = modifier
            .fillMaxWidth()
            .testTag("exif_readout"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Indigo500,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "EXIF Technical Readout",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }

            for ((label, value) in rows) {
                ReadoutRow(label = label, value = value)
            }

            ReadoutRow(label = "Dimensions", value = "${currentImage.imageWidth} × ${currentImage.imageHeight}")
            ReadoutRow(label = "Timestamp", value = formattedDate)
        }
    }
}

@Composable
private fun ReadoutRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Zinc400,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ── Rating Card ───────────────────────────────────────────────────────────────

@Composable
private fun InspectRatingCard(
    rating: Int,
    onRatingChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Zinc900),
        border = androidx.compose.foundation.BorderStroke(1.dp, Zinc800),
        modifier = modifier
            .fillMaxWidth()
            .testTag("star_rating_card"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Rating & Evaluation",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "Hotkeys 0–5",
                    style = MaterialTheme.typography.labelSmall,
                    color = Zinc500,
                )
            }

            StarRatingBar(
                rating = rating,
                onRatingChanged = onRatingChanged,
                starSize = 28.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            )
        }
    }
}

// ── Bottom Filmstrip ──────────────────────────────────────────────────────────

@Composable
private fun InspectFilmstrip(
    images: List<ImageItem>,
    currentIndex: Int,
    onSelectImage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (currentIndex in images.indices) {
            listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
        }
    }

    Surface(
        color = Zinc950,
        contentColor = Zinc200,
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .border(1.dp, Zinc800, RoundedCornerShape(0.dp))
            .testTag("inspect_filmstrip"),
    ) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(images, key = { _, item -> item.uri }) { index, item ->
                val isSelected = index == currentIndex

                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(68.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Zinc900)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Indigo500 else Zinc800,
                            shape = RoundedCornerShape(6.dp),
                        )
                        .clickable { onSelectImage(index) }
                        .testTag("filmstrip_thumb_$index")
                        .pointerHoverIcon(PointerIcon.Hand),
                ) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.fileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Rating badge at bottom
                    if (item.rating != 0) {
                        Surface(
                            shape = RoundedCornerShape(topEnd = 4.dp),
                            color = Color.Black.copy(alpha = 0.75f),
                            modifier = Modifier.align(Alignment.BottomStart),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                if (item.rating > 0) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(10.dp),
                                    )
                                    Text(
                                        text = "${item.rating}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                    )
                                } else {
                                    Text(
                                        text = "✕",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = ScoreBad,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
