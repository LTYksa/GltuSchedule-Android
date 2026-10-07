// GLTU 课表 App —— 数字滚轮选择器（用于"上课提醒时间"等）
package com.ltyksa.gltuschedule.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 滚轮数字选择器：上下滑动选择，中间高亮为当前值。
 * 用于替代"列一堆数字按钮"的做法。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NumberWheel(
    range: IntRange,
    selected: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 42.dp,
    visibleCount: Int = 5,
    suffix: String = "",
) {
    val items = remember(range) { range.toList() }
    val listState = rememberLazyListState()
    val half = visibleCount / 2
    val padding = itemHeight * half

    // 首次进入定位到当前值
    LaunchedEffect(Unit) {
        val idx = items.indexOf(selected).coerceAtLeast(0)
        listState.scrollToItem(idx)
    }

    // 滚动停止后把中间项回调出去
    val density = LocalDensity.current
    val halfItemPx = with(density) { (itemHeight / 2).toPx() }
    LaunchedEffect(listState, items, halfItemPx) {
        snapshotFlow {
            val offset = listState.firstVisibleItemScrollOffset  // 单位：px
            val index = listState.firstVisibleItemIndex
            // 中心项：偏移超过半格（px）认为进入下一项
            val centered = index + if (offset > halfItemPx) 1 else 0
            centered.coerceIn(0, items.lastIndex)
        }
            .distinctUntilChanged()
            .collect { idx ->
                items.getOrNull(idx)?.let { if (it != selected) onSelectedChange(it) }
            }
    }

    Box(
        modifier = modifier.height(itemHeight * visibleCount),
        contentAlignment = Alignment.Center,
    ) {
        // 中间高亮条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
        )

        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items.size) { i ->
                val value = items[i]
                val distance = abs(i - items.indexOf(selected))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$value$suffix",
                        textAlign = TextAlign.Center,
                        fontWeight = if (distance == 0) FontWeight.Bold else FontWeight.Normal,
                        fontSize = if (distance == 0) 19.sp else 15.sp,
                        color = when (distance) {
                            0 -> MaterialTheme.colorScheme.primary
                            1 -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}
