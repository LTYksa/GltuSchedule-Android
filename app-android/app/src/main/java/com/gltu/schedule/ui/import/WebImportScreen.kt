// GLTU 课表 App —— 内嵌浏览器导入课表
//
// 流程：
//  1. 说明页：选择教务系统入口 →「进入教务系统」
//  2. 内嵌浏览器打开教务系统，用户自己手动登录（验证码/加密/统一认证自然解决）
//  3. 用户手动进入「个人课表查询 / 课表查询」页
//  4. 点「确定导入」→ 确认对话框 → 抓当前页面 HTML 解析
//  5. 成功后设定「当前第几周」
//
// UI 约定：返回键由外层置顶返回栏提供，本页不再放"返回"按钮。
package com.gltu.schedule.ui.import

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Message
import android.util.Base64
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.gltu.schedule.GltuScheduleApp
import com.gltu.schedule.data.SemesterStore
import com.gltu.schedule.import.WebTimetableParser
import com.gltu.schedule.notification.ClassReminderScheduler
import com.gltu.schedule.widget.ScheduleWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 教务系统入口（可切换）。 */
private data class Entry(val url: String, val note: String)

private val ENTRY_LIST = listOf(
    Entry("https://v.gltu.edu.cn", "学校 WebVPN（校外可用，推荐）"),
    Entry("http://jwcweb1.gltu.cn", "校内直连 · 入口一"),
    Entry("http://jwcweb2.gltu.cn", "校内直连 · 入口二"),
    Entry("http://jwcweb3.gltu.cn", "校内直连 · 入口三"),
    Entry("http://jwcweb4.gltu.cn", "校内直连 · 入口四"),
    Entry("http://jwcweb5.gltu.cn", "校内直连 · 入口五"),
    Entry("http://jwcweb6.gltu.cn", "校内直连 · 入口六"),
)

@Composable
fun WebImportScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as GltuScheduleApp
    val scope = rememberCoroutineScope()

    var browsing by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf(ENTRY_LIST.first().url) }
    var customUrl by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf("") }
    var showConfirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    var importedCount by remember { mutableStateOf(0) }
    var importedStrategy by remember { mutableStateOf("") }
    var importedSemester by remember { mutableStateOf("") }
    var weekInput by remember { mutableStateOf("1") }
    var progress by remember { mutableStateOf(0f) }

    @SuppressLint("SetJavaScriptEnabled")
    val webView = remember {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
                allowFileAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            // 部分教务系统用 window.open 打开课表页，不接管就会点了没反应
            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?,
                ): Boolean {
                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                    val tmp = WebView(view?.context ?: context)
                    tmp.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            v: WebView?, request: WebResourceRequest?,
                        ): Boolean {
                            request?.url?.let { view?.loadUrl(it.toString()) }
                            tmp.destroy()
                            return true
                        }
                    }
                    transport.webView = tmp
                    resultMsg.sendToTarget()
                    return true
                }

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress / 100f
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(v: WebView?, url: String?, favicon: Bitmap?) {
                    progress = 0f
                }

                override fun onPageFinished(v: WebView?, url: String?) {
                    progress = 1f
                    url?.let { currentUrl = it }
                }

                override fun shouldOverrideUrlLoading(
                    v: WebView?, request: WebResourceRequest?,
                ): Boolean = false
            }
        }
    }

    // 离开本页时正确释放 WebView，避免重复挂载/内存泄漏
    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.stopLoading()
                webView.destroy()
            }
        }
    }

    // ==================== 说明页 ====================
    if (!browsing) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Hi，欢迎使用课表导入功能 👋",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("这样做为什么最稳", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "① App 里打开学校教务系统网页\n" +
                            "② 你像平时一样正常登录（验证码、密码加密、统一认证都由网页自己处理）\n" +
                            "③ 进入「个人课表查询 / 课表查询」页面\n" +
                            "④ 点「确定导入」，App 读取该页面并解析成课表\n\n" +
                            "不依赖任何私有接口，学校系统改版也不容易失效。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text("选择教务系统入口", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            // 入口列表（整行可选，不会横向溢出）
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ENTRY_LIST.forEachIndexed { idx, entry ->
                        val selected = selectedEntry == entry.url
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedEntry = entry.url; customUrl = "" }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (selected) Icons.Filled.CheckCircle
                                else Icons.Filled.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.size(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    entry.url.removePrefix("http://").removePrefix("https://"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                                Text(
                                    entry.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (idx != ENTRY_LIST.lastIndex) {
                            androidx.compose.material3.HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = customUrl,
                onValueChange = { customUrl = it },
                label = { Text("或手动输入教务系统网址（可留空）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    val u = customUrl.trim().ifBlank { selectedEntry }
                    val full = if (u.startsWith("http")) u else "http://$u"
                    browsing = true
                    currentUrl = full
                    message = null
                    webView.loadUrl(full)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("进入教务系统") }
        }
        return
    }

    // ==================== 内嵌浏览器 ====================
    Column(modifier = Modifier.fillMaxSize()) {
        // 网址栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = currentUrl,
                onValueChange = { currentUrl = it },
                label = { Text("网址") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = {
                val u = if (currentUrl.startsWith("http")) currentUrl else "http://$currentUrl"
                webView.loadUrl(u)
            }) { Text("打开") }
        }

        // 加载进度
        if (progress < 1f) {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 操作提示
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            Text(
                "登录后进入「个人课表查询」，把学期选成要导入的学期；页面小可双指缩放。确认后点下方「确定导入」。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp),
            )
        }

        AndroidView(
            factory = { webView },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        message?.let {
            Text(
                text = it,
                color = if (success) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        Surface(shadowElevation = 3.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { if (webView.canGoBack()) webView.goBack() }) { Text("后退") }
                OutlinedButton(onClick = { webView.reload() }) { Text("刷新") }
                Button(
                    onClick = { showConfirm = true },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("确定导入")
                    }
                }
            }
        }
    }

    // ==================== 确认导入 ====================
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("确认导入课表") },
            text = {
                Text(
                    "请确认是否登录成功，且处于课程表页面。\n" +
                        "同时，请检查课程表对应的学期是否一致。\n" +
                        "如果已经确认，点击「确定导入」即可导入课表。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    busy = true
                    message = "正在努力获取课程表…"
                    grabHtml(
                        webView = webView,
                        onResult = { html ->
                            scope.launch {
                                val outcome = withContext(Dispatchers.IO) {
                                    val parsed = WebTimetableParser.parse(html)
                                    if (parsed.courses.isNotEmpty()) {
                                        app.container.scheduleRepository.clearCourses()
                                        app.container.scheduleRepository.upsertCourses(parsed.courses)
                                    }
                                    parsed
                                }
                                busy = false
                                if (outcome.courses.isNotEmpty()) {
                                    importedCount = outcome.courses.size
                                    importedStrategy = outcome.strategy
                                    importedSemester = WebTimetableParser.guessSemester(html).orEmpty()
                                    message = "成功导入课程表！共 ${outcome.courses.size} 门课"
                                    weekInput = (
                                        WebTimetableParser.guessCurrentWeek(html)
                                            ?: SemesterStore.currentWeek(app)
                                        ).toString()
                                    success = true
                                } else {
                                    success = false
                                    message = outcome.note.ifBlank { "没有识别到课程，请确认当前停留在课表页面。" }
                                }
                            }
                        },
                        onEmpty = {
                            busy = false
                            message = "读取页面内容失败，请等页面加载完再试一次。"
                        },
                    )
                }) { Text("确定导入") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("取消") }
            },
        )
    }

    // ==================== 成功 + 设定当前周数 ====================
    if (success) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("导入成功") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("已成功导入 $importedCount 门课程。")
                    if (importedStrategy.isNotBlank()) {
                        Text(
                            "识别方式：$importedStrategy",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (importedSemester.isNotBlank()) {
                        Text(
                            "识别到学期：$importedSemester",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "请设置「当前是第几周」，用于正确显示单双周与不规则周次：",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = weekInput,
                        onValueChange = { v -> if (v.length <= 2) weekInput = v.filter { it.isDigit() } },
                        label = { Text("当前第几周") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val week = weekInput.toIntOrNull()?.coerceIn(1, 30) ?: 1
                    val thisMonday = java.time.LocalDate.now().with(
                        java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY),
                    )
                    // 用户填的"当前第几周" → 推导开学日期（存的是本周周一对应的开学周一）
                    val startDate = thisMonday.minusWeeks((week - 1).toLong())
                    SemesterStore.save(
                        app, startDate,
                        importedSemester.ifBlank { "教务系统导入" },
                    )
                    // 导入后：重排上课提醒 + 刷新小部件
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val courses = app.container.scheduleRepository.getWeekCoursesSync()
                                ClassReminderScheduler.scheduleNext(
                                    app, courses, SemesterStore.firstWeekMonday(app),
                                )
                                ScheduleWidgetProvider.refreshAll(app)
                            }
                        }
                    }
                    success = false
                    onDone()
                }) { Text("完成") }
            },
        )
    }
}

/**
 * 抓取 WebView 当前页面的 HTML（含同源 iframe 内容，很多教务系统的课表在 iframe 里）。
 * 用 base64 传输，避免 JS 字符串转义问题（中文必须这样处理）。
 */
private fun grabHtml(webView: WebView, onResult: (String) -> Unit, onEmpty: () -> Unit) {
    val js = """
        (function(){
          try{
            var parts=[];
            try{ parts.push(document.documentElement.outerHTML); }catch(e){}
            var fs=document.getElementsByTagName('iframe');
            for(var i=0;i<fs.length;i++){
              try{
                var d=fs[i].contentDocument;
                if(d && d.documentElement){ parts.push(d.documentElement.outerHTML); }
              }catch(e){}
            }
            var h=parts.join('\n<!--IFRAME-->\n');
            return btoa(unescape(encodeURIComponent(h)));
          }catch(e){ return ''; }
        })()
    """.trimIndent()

    webView.evaluateJavascript(js) { result ->
        val raw = result?.trim().orEmpty()
        if (raw.isEmpty() || raw == "null" || raw == "\"\"") {
            onEmpty()
            return@evaluateJavascript
        }
        val html = try {
            String(Base64.decode(raw.removeSurrounding("\""), Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
        if (html.isBlank()) onEmpty() else onResult(html)
    }
}
