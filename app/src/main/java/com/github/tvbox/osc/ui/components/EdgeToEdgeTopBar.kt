package com.github.tvbox.osc.ui.components

import com.github.tvbox.osc.ui.theme.GLASS_BACKDROP_BAND_MARGIN_DP

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.tvbox.osc.ui.theme.LiquidGlassState
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBarScaffold(
    titleContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    collapseEnabled: Boolean = true,
    topBarStartInset: Dp = 0.dp,
    topScrimEnabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.BoxScope.(topPadding: androidx.compose.ui.unit.Dp, bottomPadding: androidx.compose.ui.unit.Dp) -> Unit,
) {
    val scrollBehavior = if (collapseEnabled) {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    } else {
        TopAppBarDefaults.pinnedScrollBehavior()
    }
    val glassConfig = LiquidGlassState.config
    val glassEnabled = glassConfig.controlsEnabled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val glassColor by rememberUpdatedState(containerColor)
    val glassOnDraw: ContentDrawScope.() -> Unit = remember {
        { drawRect(glassColor); drawContent() }
    }
    val glassBackdrop = rememberLayerBackdrop(onDraw = glassOnDraw)
    val pauseGlassRecording = LocalTopBarGlassPauseRecording.current
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = containerColor,
        topBar = {
            CompositionLocalProvider(
                LocalTopBarGlassBackdrop provides glassBackdrop.takeIf { glassEnabled }
            ) {
                TopAppBar(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                        .padding(start = topBarStartInset),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                    title = titleContent,
                    navigationIcon = {
                        if (navigationIcon != null) {
                            Box(modifier = Modifier.padding(start = 12.dp)) { navigationIcon() }
                        }
                    },
                    actions = {
                        Row(modifier = Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            actions()
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { padding ->
        val topPadding = padding.calculateTopPadding()
        val density = LocalDensity.current
        val glassBandHeight = topPadding + GLASS_BACKDROP_BAND_MARGIN_DP.dp
        val glassBounds: (Size) -> Rect? = remember(density, glassBandHeight, containerColor) {
            { size -> Rect(0f, 0f, size.width, with(density) { glassBandHeight.toPx() }) }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (glassEnabled) {
                            Modifier.layerBackdrop(glassBackdrop, glassBounds, pauseGlassRecording)
                        } else {
                            Modifier
                        }
                    ),
            ) {
                content(
                    topPadding,
                    padding.calculateBottomPadding(),
                )
            }
            if (topScrimEnabled) {
                TopScrim(height = topPadding)
            }
        }
    }
}

@Composable
fun TopScrim(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    height: Dp = with(androidx.compose.ui.platform.LocalDensity.current) {
        (WindowInsets.statusBarsIgnoringVisibility.getTop(this) * 1.2f).toDp()
    },
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    0f to color.copy(alpha = 0.95f),
                    0.5f to color.copy(alpha = 0.7f),
                    1f to Color.Transparent,
                )
            ),
    )
}

@Composable
fun TopBarActionBox(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    fallbackColor: Color = MaterialTheme.colorScheme.surfaceBright,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .glassTopBarSurface(CircleShape, fallbackColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}
