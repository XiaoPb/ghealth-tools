package com.ghealth.tools.core.ui.adaptive

import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val WindowWidthSizeClass.isWide: Boolean
    get() = this == WindowWidthSizeClass.Medium || this == WindowWidthSizeClass.Expanded

val WindowSizeClass.shouldUseLandscapeLayout: Boolean
    get() = widthSizeClass.isWide && heightSizeClass == WindowHeightSizeClass.Medium

val FORM_MAX_WIDTH: Dp = 480.dp

val CONTENT_MAX_WIDTH: Dp = 640.dp
