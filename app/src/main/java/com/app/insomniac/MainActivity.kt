package com.app.insomniac

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.app.insomniac.ui.theme.InsomniacTheme
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

// =============================================================================
// MAIN ACTIVITY
// =============================================================================

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("InsomniacData", Context.MODE_PRIVATE)
        val userAles = prefs.getBoolean("LIMBA_USER_ALES", false)
        val cod = if (userAles) {
            prefs.getString("LIMBA", detecteazaLimbaImplicita()) ?: detecteazaLimbaImplicita()
        } else {
            val detectat = detecteazaLimbaImplicita()
            prefs.edit().putString("LIMBA", detectat).apply()
            detectat
        }
        super.attachBaseContext(newBase.aplicaLimba(cod))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { InsomniacTheme { InsomniacApp() } }
    }
}

// =============================================================================
// APP ROOT
// =============================================================================

@Composable
fun InsomniacApp() {
    val context = LocalContext.current
    val prefs   = remember { context.getSharedPreferences("InsomniacData", Context.MODE_PRIVATE) }

    var tab      by remember { mutableIntStateOf(0) }
    var doarme   by remember { mutableStateOf(prefs.getBoolean("DOARME", false)) }
    var oraStart by remember { mutableLongStateOf(prefs.getLong("ORA_INCEPUT", 0L)) }
    var username by remember { mutableStateOf(prefs.getString("USERNAME", "") ?: "") }
    var poza     by remember { mutableStateOf(incarcaPoza(context)) }
    var isDark   by remember { mutableStateOf(prefs.getBoolean("IS_DARK", false)) }
    var accent   by remember { mutableIntStateOf(prefs.getInt("ACCENT_INDEX", 0)) }
    var darkInainte by remember { mutableStateOf(prefs.getBoolean("IS_DARK_BEFORE_SLEEP", false)) }
    var limbaCod by remember { mutableStateOf(prefs.getString("LIMBA", "ro") ?: "ro") }

    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration) {
        val userAles = prefs.getBoolean("LIMBA_USER_ALES", false)
        if (!userAles) {
            val limbaSistem = configuration.locales[0].language
            val suportata = LIMBI_DISPONIBILE.find { it.cod == limbaSistem }?.cod
            if (suportata != null && suportata != limbaCod) {
                limbaCod = suportata
                prefs.edit().putString("LIMBA", suportata).apply()
            }
        }
    }

    val ctxLocalizat = remember(limbaCod) { context.aplicaLimba(limbaCod) }
    val theme by remember(isDark, accent) { derivedStateOf { buildTheme(isDark, accent) } }

    val istoricSomn = remember {
        mutableStateListOf<SesiuneSomn>().also { lista ->
            val raw = prefs.getString("ISTORIC", "") ?: ""
            if (raw.isNotEmpty()) lista.addAll(raw.split(";").mapNotNull { ln ->
                val p = ln.split("|")
                if (p.size >= 3) SesiuneSomn(p[0], p[1].toLong(), p[2].toLong(),
                    p.getOrElse(3) { "" }, p.getOrElse(4) { "" }, p.getOrElse(5) { "0" }.toLong())
                else null
            })
        }
    }

    val bgColor by animateColorAsState(theme.background, tween(1000), label = "bg")
    val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current

    CompositionLocalProvider(
        LocalContext provides ctxLocalizat,
        LocalActivityResultRegistryOwner provides activityResultRegistryOwner!!
    ) {
        Scaffold(
            containerColor = bgColor,
            bottomBar = {
                NavigationBar(containerColor = theme.navBar, tonalElevation = 0.dp) {
                    data class NI(val res: Int, val sel: androidx.compose.ui.graphics.vector.ImageVector, val off: androidx.compose.ui.graphics.vector.ImageVector)
                    val items = listOf(
                        NI(R.string.nav_home,     Icons.Filled.Home,      Icons.Outlined.Home),
                        NI(R.string.nav_history,  Icons.Filled.DateRange, Icons.Outlined.DateRange),
                        NI(R.string.nav_settings, Icons.Filled.Settings,  Icons.Outlined.Settings)
                    )
                    items.forEachIndexed { i, item ->
                        val sel = tab == i
                        val sc by animateFloatAsState(if (sel) 1.18f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "sc$i")
                        val tc by animateColorAsState(if (sel) theme.navSelectedContent else theme.navUnselected, tween(250), label = "tc$i")
                        NavigationBarItem(selected = sel, onClick = { tab = i },
                            icon = {
                                if (sel) Box(Modifier.background(theme.navSelected, RoundedCornerShape(16.dp)).padding(horizontal = 20.dp, vertical = 6.dp)) {
                                    Icon(item.sel, stringResource(item.res), tint = tc, modifier = Modifier.scale(sc))
                                } else Icon(item.off, stringResource(item.res), tint = tc, modifier = Modifier.scale(sc))
                            },
                            label = { Text(stringResource(item.res),
                                color = if (sel) theme.navSelectedContent else theme.navUnselected,
                                fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = if (sel) 13.sp else 12.sp) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
                        )
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (tab) {
                    0 -> EcranPrincipal(doarme, theme, oraStart, username, poza) {
                        if (!doarme) {
                            darkInainte = isDark
                            prefs.edit().putBoolean("IS_DARK_BEFORE_SLEEP", isDark).apply()
                            isDark = true; prefs.edit().putBoolean("IS_DARK", true).apply()
                            oraStart = System.currentTimeMillis(); doarme = true
                            prefs.edit().putBoolean("DOARME", true).putLong("ORA_INCEPUT", oraStart).apply()
                        } else {
                            isDark = darkInainte
                            prefs.edit().putBoolean("IS_DARK", darkInainte).apply()
                            val ms = System.currentTimeMillis() - oraStart
                            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                            istoricSomn.add(SesiuneSomn(
                                SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date()),
                                (ms / 3600000) % 24, (ms / 60000) % 60,
                                fmt.format(Date(oraStart)), fmt.format(Date()), System.currentTimeMillis()
                            ))
                            doarme = false
                            salveazaIstoric(prefs, istoricSomn)
                            prefs.edit().putBoolean("DOARME", false).putLong("ORA_INCEPUT", 0L).apply()
                        }
                    }
                    1 -> EcranIstoric(istoricSomn, theme) { s -> istoricSomn.remove(s); salveazaIstoric(prefs, istoricSomn) }
                    2 -> EcranSetari(
                        theme, poza, { tab = 3 }, isDark, doarme,
                        { nou -> isDark = nou; prefs.edit().putBoolean("IS_DARK", nou).apply() },
                        accent, { idx -> accent = idx; prefs.edit().putInt("ACCENT_INDEX", idx).apply() },
                        limbaCod
                    ) { cod ->
                        limbaCod = cod
                        val eSystemDefault = cod == detecteazaLimbaImplicita()
                        prefs.edit()
                            .putString("LIMBA", cod)
                            .putBoolean("LIMBA_USER_ALES", !eSystemDefault)
                            .apply()
                    }
                    3 -> EcranEditareProfil(theme, username, poza,
                        { newUser, newPoza, uri ->
                            username = newUser; prefs.edit().putString("USERNAME", newUser).apply()
                            if (uri != null) { salveazaPoza(context, uri); poza = newPoza }
                            tab = 2
                        }) { tab = 2 }
                }
            }
        }
    }
}

// =============================================================================
// ECRAN PRINCIPAL
// =============================================================================

@Composable
fun EcranPrincipal(
    doarme: Boolean, theme: AppTheme, oraStart: Long,
    username: String, poza: ImageBitmap?, onToggle: () -> Unit
) {
    var now by remember { mutableStateOf(Calendar.getInstance().time) }
    LaunchedEffect(Unit) { while (true) { now = Calendar.getInstance().time; delay(1000) } }

    val textColor  by animateColorAsState(theme.textMain, label = "tc")
    val efectAlpha by animateFloatAsState(if (doarme) 1f else 0f, tween(1200), label = "ea")

    Box(Modifier.fillMaxSize()) {
        if (efectAlpha > 0f) {
            Box(Modifier.fillMaxSize().graphicsLayer(alpha = efectAlpha)) {
                EfectStele(Modifier.fillMaxSize())
                NoriLenti(Modifier.fillMaxSize(), theme.primary)
                ParticuleSomnoroase(Modifier.fillMaxSize(), theme.primary)
            }
        }
        Column(Modifier.fillMaxSize().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.app_name), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = textColor)
            Text(stringResource(R.string.app_subtitle), fontSize = 14.sp,
                color = if (doarme) theme.primaryLight.copy(alpha = 0.8f) else theme.textSecondary)
            Spacer(Modifier.height(40.dp))

            val pulsScale by rememberInfiniteTransition(label = "puls").animateFloat(1f,
                if (doarme) 1.06f else 1f,
                infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse), "ps")
            Box(Modifier.size(90.dp).scale(pulsScale).clip(CircleShape).background(theme.surface), contentAlignment = Alignment.Center) {
                if (poza != null) Image(poza, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Default.Person, null, Modifier.size(60.dp), tint = theme.primary)
            }
            if (username.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(username, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    color = if (doarme) theme.primaryLight else theme.primary, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(if (username.isNotBlank()) 32.dp else 50.dp))

            Card(colors = CardDefaults.cardColors(containerColor = theme.surface),
                elevation = CardDefaults.cardElevation(if (theme.isDark) 0.dp else 8.dp),
                shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, theme.divider),
                modifier = Modifier.size(320.dp, 220.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (doarme) {
                        var scurs by remember { mutableLongStateOf(System.currentTimeMillis() - oraStart) }
                        LaunchedEffect(Unit) { while (true) { scurs = System.currentTimeMillis() - oraStart; delay(1000) } }
                        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(stringResource(R.string.sleeping), fontSize = 18.sp, color = theme.primaryLight, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(12.dp))
                                Text(String.format(Locale.getDefault(), "%02d:%02d:%02d",
                                    (scurs / 3600000) % 24, (scurs / 60000) % 60, (scurs / 1000) % 60),
                                    fontSize = 40.sp, fontWeight = FontWeight.Bold, color = theme.textMain)
                            }
                            LunaPulsanda(theme.primary, Modifier.size(90.dp))
                        }
                    } else {
                        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                            Text(stringResource(R.string.current_time), color = theme.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                                Box(Modifier.size(90.dp, 80.dp).background(theme.hourBoxBg, RoundedCornerShape(12.dp)).border(2.dp, theme.primary, RoundedCornerShape(12.dp)), Alignment.Center) {
                                    Text(SimpleDateFormat("HH", Locale.getDefault()).format(now), fontSize = 48.sp, color = theme.primary, fontWeight = FontWeight.Bold)
                                }
                                Text(" : ", fontSize = 48.sp, color = textColor, modifier = Modifier.padding(horizontal = 8.dp))
                                Box(Modifier.size(90.dp, 80.dp).background(theme.minuteBoxBg, RoundedCornerShape(12.dp)).border(1.dp, theme.divider, RoundedCornerShape(12.dp)), Alignment.Center) {
                                    Text(SimpleDateFormat("mm", Locale.getDefault()).format(now), fontSize = 48.sp, color = textColor, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(50.dp))

            val inter = remember { MutableInteractionSource() }
            val pressed by inter.collectIsPressedAsState()
            val sc by animateFloatAsState(if (pressed) 0.93f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh), label = "s")
            val btnBg  by animateColorAsState(if (doarme) (if (theme.isDark) Color(0xFF3A1010) else Color(0xFFFFB4AB)) else theme.primaryLighter, tween(600), label = "bb")
            val btnFg  by animateColorAsState(if (doarme) (if (theme.isDark) Color(0xFFEF9A9A) else Color(0xFF690005)) else theme.primary, tween(600), label = "bf")

            ElevatedButton(onClick = onToggle, interactionSource = inter,
                colors = ButtonDefaults.elevatedButtonColors(containerColor = btnBg),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.elevatedButtonElevation(if (theme.isDark) 0.dp else 4.dp),
                border = if (theme.isDark) BorderStroke(1.dp, theme.divider) else null,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                modifier = Modifier.height(60.dp).scale(sc)) {
                Icon(if (doarme) Icons.Default.LightMode else Icons.Default.NightsStay, null, tint = btnFg, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (doarme) R.string.btn_wake else R.string.btn_sleep),
                    color = btnFg, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
            }
        }
    }
}

// =============================================================================
// ECRAN ISTORIC
// =============================================================================

@Composable
fun EcranIstoric(istoric: List<SesiuneSomn>, theme: AppTheme, onSterge: (SesiuneSomn) -> Unit) {
    val valid  by remember(istoric) { derivedStateOf { istoric.filter { it.ore > 0 || it.minute > 0 } } }
    val totMin by remember(valid)   { derivedStateOf { valid.sumOf { it.ore * 60 + it.minute } } }
    val medOre by remember(totMin, valid) { derivedStateOf { if (valid.isNotEmpty()) totMin / valid.size / 60 else 0L } }
    val medMin by remember(totMin, valid) { derivedStateOf { if (valid.isNotEmpty()) (totMin / valid.size) % 60 else 0L } }
    val nrZile by remember(valid) { derivedStateOf { valid.size } }

    data class StatusInfo(val textRes: Int, val color: Color, val emoji: String, val detRes: Int)
    val status by remember(medOre, nrZile) {
        derivedStateOf {
            when {
                nrZile == 0   -> StatusInfo(R.string.no_data,          theme.textSecondary, "",   R.string.start_recording)
                medOre >= 9   -> StatusInfo(R.string.status_champion,  Color(0xFF66BB6A),   "🏆", R.string.status_champion_detail)
                medOre >= 7   -> StatusInfo(R.string.status_healthy,   Color(0xFF66BB6A),   "💚", R.string.status_healthy_detail)
                medOre >= 6   -> StatusInfo(R.string.status_tired,     Color(0xFFFFD54F),   "🌤️",R.string.status_tired_detail)
                medOre >= 5   -> StatusInfo(R.string.status_fatigued,  Color(0xFFFFB74D),   "😴", R.string.status_fatigued_detail)
                medOre >= 4   -> StatusInfo(R.string.status_exhausted, Color(0xFFFF8A65),   "⚠️", R.string.status_exhausted_detail)
                else          -> StatusInfo(R.string.status_deprived,  Color(0xFFEF9A9A),   "🚨", R.string.status_deprived_detail)
            }
        }
    }

    val startSapt by remember { derivedStateOf { getSaptamanaStartMs(System.currentTimeMillis()) } }
    val endSapt   by remember(startSapt) { derivedStateOf { startSapt + 7L * 86400000 - 1 } }
    val saptAc    by remember(valid, startSapt, endSapt) { derivedStateOf { valid.filter { getTimestamp(it) in startSapt..endSapt } } }
    val totSapt   by remember(saptAc)   { derivedStateOf { saptAc.sumOf { it.ore * 60 + it.minute } } }
    val medSaptOre by remember(totSapt, saptAc) { derivedStateOf { if (saptAc.isNotEmpty()) totSapt / saptAc.size / 60 else 0L } }
    val medSaptMin by remember(totSapt, saptAc) { derivedStateOf { if (saptAc.isNotEmpty()) (totSapt / saptAc.size) % 60 else 0L } }
    val grupuri   by remember(valid) { derivedStateOf {
        valid.reversed().groupBy { getSaptamanaStartMs(getTimestamp(it)) }.entries.sortedByDescending { it.key }
    } }

    val context = LocalContext.current
    @Composable
    fun numeZi(s: SesiuneSomn): String {
        val ts = getTimestamp(s); if (ts <= 0L) return s.data
        val cal = Calendar.getInstance(); cal.timeInMillis = ts
        val res = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY    -> R.string.day_monday;    Calendar.TUESDAY   -> R.string.day_tuesday
            Calendar.WEDNESDAY -> R.string.day_wednesday; Calendar.THURSDAY  -> R.string.day_thursday
            Calendar.FRIDAY    -> R.string.day_friday;    Calendar.SATURDAY  -> R.string.day_saturday
            Calendar.SUNDAY    -> R.string.day_sunday;    else -> return s.data
        }
        return "${stringResource(res)} - ${SimpleDateFormat("dd MMMM", Locale.getDefault()).format(cal.time)}"
    }

    var expanded by remember { mutableIntStateOf(-1) }
    var confirmDel by remember { mutableStateOf<SesiuneSomn?>(null) }

    confirmDel?.let { s ->
        AlertDialog(onDismissRequest = { confirmDel = null }, containerColor = theme.dialogBg,
            title = { Text(stringResource(R.string.delete_confirm_title), color = theme.textMain) },
            text  = { Text(stringResource(R.string.delete_confirm_text, numeZi(s)), color = theme.textSecondary) },
            confirmButton = { TextButton({ onSterge(s); expanded = -1; confirmDel = null }) {
                Text(stringResource(R.string.btn_delete), color = theme.cardRedText, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton({ confirmDel = null }) {
                Text(stringResource(R.string.btn_cancel), color = theme.textSecondary) } }
        )
    }

    LazyColumn(Modifier.fillMaxSize().padding(20.dp)) {
        item {
            Text(stringResource(R.string.history_title), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = theme.textMain)
            Spacer(Modifier.height(12.dp))
        }
        // Card Status General
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surfaceCard),
                border = BorderStroke(1.dp, theme.primary.copy(alpha = if (theme.isDark) 0.4f else 0.6f))) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.status_general), color = theme.primaryDarker, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    if (nrZile == 0) {
                        Text(stringResource(R.string.no_data), color = theme.textMain, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(stringResource(R.string.start_recording), color = theme.textSecondary, fontSize = 13.sp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${status.emoji} ", fontSize = 22.sp)
                            Text(stringResource(status.textRes), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = status.color)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(status.detRes), color = theme.textSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = theme.primaryDarker.copy(0.5f))
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.global_average, medOre, medMin, nrZile,
                            stringResource(if (nrZile == 1) R.string.day_singular else R.string.day_plural)),
                            color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        // Card Săptămâna Curentă
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surfaceCard2),
                border = BorderStroke(1.dp, theme.primary.copy(alpha = if (theme.isDark) 0.4f else 0.6f))) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.weekly_average), color = theme.primaryDarker, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.weekly_subtitle), color = theme.textSecondary.copy(0.8f), fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    if (saptAc.isEmpty()) {
                        Text(stringResource(R.string.no_records_this_week), color = theme.textMain, fontSize = 14.sp)
                    } else {
                        Text(stringResource(R.string.weekly_per_night, medSaptOre, medSaptMin), color = theme.primaryDarker, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.weekly_nights_count, saptAc.size,
                            stringResource(if (saptAc.size == 1) R.string.night_singular else R.string.night_plural)),
                            color = theme.textSecondary, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = theme.primaryDarker.copy(0.5f))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.tip_ideal), color = theme.textSecondary, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        // Grupuri Săptămâni
        grupuri.forEach { (saptMs, sesiuni) ->
            if (sesiuni.isNotEmpty()) {
                val totM  = sesiuni.sumOf { it.ore * 60 + it.minute }
                val calS  = Calendar.getInstance().also { it.timeInMillis = if (saptMs > 0L) saptMs else System.currentTimeMillis() }
                val calE  = Calendar.getInstance().also { it.timeInMillis = calS.timeInMillis; it.add(Calendar.DAY_OF_MONTH, 6) }
                val fmt   = SimpleDateFormat("dd MMM", Locale.getDefault())
                item {
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f), color = theme.weekDividerColor)
                        Text("  ${fmt.format(calS.time)} – ${fmt.format(calE.time)} = ${totM / 60}h ${totM % 60}m  ",
                            color = theme.weekDividerColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        HorizontalDivider(Modifier.weight(1f), color = theme.weekDividerColor)
                    }
                    Spacer(Modifier.height(6.dp))
                }
                sesiuni.forEach { ses ->
                    val gIdx = valid.reversed().indexOf(ses)
                    item(key = "${saptMs}_$gIdx") {
                        val ok  = ses.ore >= 7
                        val exp = expanded == gIdx
                        Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            .clickable(remember { MutableInteractionSource() }, null) { expanded = if (exp) -1 else gIdx },
                            colors = CardDefaults.cardColors(if (ok) theme.cardGreen else theme.cardRed),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (ok) theme.cardGreenText.copy(if (theme.isDark) 0.4f else 0.5f)
                            else theme.cardRedText.copy(if (theme.isDark) 0.4f else 0.5f))) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(numeZi(ses), fontWeight = FontWeight.Bold, color = theme.textMain, fontSize = 14.sp)
                                        Spacer(Modifier.height(2.dp))
                                        Text(stringResource(R.string.hours_slept, ses.ore, ses.minute), color = theme.textSecondary, fontSize = 13.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(if (ok) R.string.label_excellent else R.string.label_insufficient),
                                            color = if (ok) theme.cardGreenText else theme.cardRedText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Spacer(Modifier.width(4.dp))
                                        Icon(if (exp) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            null, tint = theme.textSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                                if (exp) {
                                    Spacer(Modifier.height(10.dp))
                                    HorizontalDivider(color = theme.divider)
                                    Spacer(Modifier.height(10.dp))
                                    if (ses.oraInceput.isNotEmpty()) {
                                        Row(verticalAlignment = Alignment.Top) {
                                            Text("🛌", fontSize = 15.sp)
                                            Spacer(Modifier.width(8.dp))
                                            Column {
                                                Text(stringResource(R.string.fell_asleep_at, ses.oraInceput), color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                                Spacer(Modifier.height(3.dp))
                                                Text(stringResource(R.string.woke_up_at, ses.oraSfarsit), color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    } else {
                                        Text(stringResource(R.string.no_times_saved), color = theme.textSecondary, fontSize = 12.sp)
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton({ confirmDel = ses }, colors = ButtonDefaults.textButtonColors(contentColor = theme.cardRedText)) {
                                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(stringResource(R.string.delete_session), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// ECRAN SETĂRI
// =============================================================================

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun EcranSetari(
    theme: AppTheme, poza: ImageBitmap?, onProfil: () -> Unit,
    isDark: Boolean, doarme: Boolean, onDark: (Boolean) -> Unit,
    accentIdx: Int, onAccent: (Int) -> Unit,
    limbaCod: String, onLimba: (String) -> Unit
) {
    var showAccent   by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    val limbaObj     = LIMBI_DISPONIBILE.find { it.cod == limbaCod }
    val limbaAfisata = limbaObj?.let { "${it.flag} ${it.numeNativ}" } ?: limbaCod
    val ctxLocalizat = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }

    // Modal Culori
    if (showAccent) {
        Dialog(onDismissRequest = { showAccent = false }) {
            CompositionLocalProvider(LocalContext provides ctxLocalizat) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(theme.dialogBg).padding(24.dp)) {
                    Column {
                        Text(ctxLocalizat.getString(R.string.accent_modal_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.textMain)
                        Spacer(Modifier.height(4.dp))
                        Text(ctxLocalizat.getString(R.string.accent_modal_subtitle), fontSize = 13.sp, color = theme.textSecondary)
                        Spacer(Modifier.height(20.dp))
                        CULORI_ACCENT.chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                row.forEach { c ->
                                    val idx = CULORI_ACCENT.indexOf(c)
                                    val sel = idx == accentIdx
                                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) { onAccent(idx); showAccent = false }) {
                                        Box(Modifier.size(if (sel) 54.dp else 46.dp).clip(CircleShape).background(c.base)
                                            .border(if (sel) 3.dp else 1.dp, if (sel) theme.textMain else Color(0xFF888888), CircleShape),
                                            contentAlignment = Alignment.Center) {
                                            if (sel) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(24.dp))
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(ctxLocalizat.getString(c.numeRes), fontSize = 10.sp,
                                            color = if (sel) theme.primary else theme.textSecondary,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                            textAlign = TextAlign.Center)
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton({ showAccent = false }) { Text(ctxLocalizat.getString(R.string.btn_close), color = theme.textSecondary) }
                        }
                    }
                }
            }
        }
    }

    // Modal Limbă
    if (showLanguage) {
        Dialog(onDismissRequest = { showLanguage = false }) {
            CompositionLocalProvider(LocalContext provides ctxLocalizat) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(theme.dialogBg).padding(24.dp)) {
                    Column {
                        Text(ctxLocalizat.getString(R.string.language_modal_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.textMain)
                        Spacer(Modifier.height(4.dp))
                        Text(ctxLocalizat.getString(R.string.language_modal_subtitle), fontSize = 13.sp, color = theme.textSecondary)
                        Spacer(Modifier.height(20.dp))
                        LIMBI_DISPONIBILE.forEach { l ->
                            val sel   = l.cod == limbaCod
                            val inter = remember { MutableInteractionSource() }
                            val pres  by inter.collectIsPressedAsState()
                            val sc    by animateFloatAsState(if (pres) 0.96f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "ls")
                            Box(Modifier.fillMaxWidth().scale(sc)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (sel) theme.primary.copy(alpha = if (theme.isDark) 0.25f else 0.12f) else Color.Transparent)
                                .border(if (sel) 1.5.dp else 1.dp, if (sel) theme.primary else theme.divider, RoundedCornerShape(12.dp))
                                .clickable(inter, null) { onLimba(l.cod); showLanguage = false }
                                .padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(l.flag, fontSize = 24.sp)
                                        Spacer(Modifier.width(12.dp))
                                        Text(l.numeNativ, fontSize = 15.sp,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                            color = if (sel) theme.primary else theme.textMain)
                                    }
                                    if (sel) Icon(Icons.Default.Check, null, tint = theme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton({ showLanguage = false }) { Text(ctxLocalizat.getString(R.string.btn_close), color = theme.textSecondary) }
                        }
                    }
                }
            }
        }
    }

    // Modal Despre
    if (showAbout) {
        Dialog(onDismissRequest = { showAbout = false }) {
            CompositionLocalProvider(LocalContext provides ctxLocalizat) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(theme.dialogBg).padding(24.dp)) {
                    Column {
                        Text(ctxLocalizat.getString(R.string.about_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.textMain)
                        Spacer(Modifier.height(18.dp))
                        Text(ctxLocalizat.getString(R.string.about_text), fontSize = 14.sp, color = theme.textSecondary, lineHeight = 22.sp)
                        Spacer(Modifier.height(30.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton({ showAbout = false }) {
                                Text(ctxLocalizat.getString(R.string.understand), color = theme.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }


    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.settings_title), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = theme.textMain)
        Spacer(Modifier.height(20.dp))

        // Profil
        SettingsRow(theme, onProfil,
            {
                Box(Modifier.size(38.dp).clip(CircleShape).border(1.5.dp, theme.primary.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center) {
                    if (poza != null) Image(poza, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Default.Person, null, tint = theme.primary, modifier = Modifier.size(22.dp))
                }
            },
            stringResource(R.string.settings_profile), stringResource(R.string.settings_profile_desc))

        HorizontalDivider(color = theme.settingsDivider)

        // Dark Mode
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Box(Modifier.size(38.dp).clip(CircleShape).border(1.5.dp, theme.primary.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center) {
                    Icon(if (isDark) Icons.Default.NightsStay else Icons.Default.WbSunny, null,
                        tint = theme.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(stringResource(R.string.settings_dark_mode), color = theme.textMain, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(stringResource(when { doarme -> R.string.settings_dark_auto; isDark -> R.string.settings_dark_active; else -> R.string.settings_light_active }),
                        color = if (doarme) theme.textSecondary.copy(0.5f) else theme.textSecondary, fontSize = 12.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val li = remember { MutableInteractionSource() }; val lp by li.collectIsPressedAsState()
                val ls by animateFloatAsState(if (lp && !doarme) 0.92f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "ls")
                Box(Modifier.scale(ls).clip(RoundedCornerShape(10.dp))
                    .background(when { doarme -> theme.divider.copy(0.35f); !isDark -> theme.primary; else -> theme.divider })
                    .clickable(li, null) { if (!doarme) onDark(false) }
                    .padding(horizontal = 12.dp, vertical = 6.dp), Alignment.Center) {
                    Text(stringResource(R.string.settings_btn_light), fontSize = 12.sp,
                        color = when { doarme -> theme.textSecondary.copy(0.3f); !isDark -> Color.White; else -> theme.textSecondary },
                        fontWeight = if (!isDark && !doarme) FontWeight.Bold else FontWeight.Normal)
                }
                val di = remember { MutableInteractionSource() }; val dp2 by di.collectIsPressedAsState()
                val ds by animateFloatAsState(if (dp2 && !doarme) 0.92f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "ds")
                Box(Modifier.scale(ds).clip(RoundedCornerShape(10.dp))
                    .background(when { doarme -> theme.divider.copy(0.35f); isDark -> theme.primary; else -> theme.divider })
                    .clickable(di, null) { if (!doarme) onDark(true) }
                    .padding(horizontal = 12.dp, vertical = 6.dp), Alignment.Center) {
                    Text(stringResource(R.string.settings_btn_dark), fontSize = 12.sp,
                        color = when { doarme -> theme.textSecondary.copy(0.3f); isDark -> Color.White; else -> theme.textSecondary },
                        fontWeight = if (isDark && !doarme) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        HorizontalDivider(color = theme.settingsDivider)

        // Culori
        SettingsRow(theme, { showAccent = true },
            {
                Box(Modifier.size(38.dp).clip(CircleShape).border(1.5.dp, theme.primary.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Brush, null, tint = theme.primary, modifier = Modifier.size(22.dp))
                }
            },
            stringResource(R.string.settings_accent),
            stringResource(R.string.settings_accent_desc, stringResource(CULORI_ACCENT[accentIdx].numeRes)))

        HorizontalDivider(color = theme.settingsDivider)

        // Limbă
        SettingsRow(theme, { showLanguage = true },
            {
                Box(Modifier.size(38.dp).clip(CircleShape).border(1.5.dp, theme.primary.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Language, null, tint = theme.primary, modifier = Modifier.size(22.dp))
                }
            },
            stringResource(R.string.settings_language),
            stringResource(R.string.settings_language_desc, limbaAfisata))


        HorizontalDivider(color = theme.settingsDivider)

       // Despre
        SettingsRow(theme, { showAbout = true },
            {
                Box(Modifier.size(38.dp).clip(CircleShape).border(1.5.dp, theme.primary.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Info, null, tint = theme.primary, modifier = Modifier.size(22.dp))
                }
            },
            stringResource(R.string.settings_about),
            stringResource(R.string.settings_about_desc))

        Spacer(Modifier.weight(1f))
        val versiune = remember {
            try { ctxLocalizat.packageManager.getPackageInfo(ctxLocalizat.packageName, 0).versionName }
            catch (e: Exception) { "1.0" }
        }
        Text("Insomniac v$versiune",
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            textAlign = TextAlign.Center, fontSize = 12.sp,
            color = theme.textSecondary.copy(alpha = 0.5f))
    }
}

@Composable
fun SettingsRow(theme: AppTheme, onClick: () -> Unit, icon: @Composable () -> Unit, titlu: String, descriere: String) {
    val inter = remember { MutableInteractionSource() }
    val pres  by inter.collectIsPressedAsState()
    val sc    by animateFloatAsState(if (pres) 0.97f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "sr")
    Row(Modifier.fillMaxWidth().scale(sc).clickable(inter, null) { onClick() }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(14.dp))
            Column {
                Text(titlu, color = theme.textMain, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(descriere, color = theme.textSecondary, fontSize = 12.sp)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = theme.textSecondary, modifier = Modifier.size(20.dp))
    }
}

// =============================================================================
// ECRAN EDITARE PROFIL
// =============================================================================

@Composable
fun EcranEditareProfil(
    theme: AppTheme, usernameInit: String, pozaInit: ImageBitmap?,
    onSave: (String, ImageBitmap?, Uri?) -> Unit, onBack: () -> Unit
) {
    val context = LocalContext.current
    var userText by remember { mutableStateOf(usernameInit) }
    var pozaPrev by remember { mutableStateOf(pozaInit) }
    var uriSel   by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { uriSel = uri; runCatching { pozaPrev = BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))?.asImageBitmap() } }
    }
    var vis by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vis = true }
    val oy by animateFloatAsState(if (vis) 0f else 60f, tween(350, easing = EaseOutCubic), label = "oy")
    val al by animateFloatAsState(if (vis) 1f else 0f, tween(300), label = "al")

    Column(Modifier.fillMaxSize().padding(20.dp).offset(y = oy.dp).graphicsLayer(alpha = al)) {
        Text(stringResource(R.string.profile_title), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = theme.textMain)
        Spacer(Modifier.height(32.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val ai = remember { MutableInteractionSource() }; val ap by ai.collectIsPressedAsState()
            val asc by animateFloatAsState(if (ap) 0.93f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "as")
            Box(Modifier.size(110.dp).scale(asc).clip(CircleShape).background(theme.surface)
                .border(2.dp, theme.primary, CircleShape).clickable(ai, null) { launcher.launch("image/*") },
                contentAlignment = Alignment.Center) {
                if (pozaPrev != null) Image(pozaPrev!!, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Default.Person, null, Modifier.size(70.dp), tint = theme.primary)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.profile_photo_hint), fontSize = 12.sp, color = theme.textSecondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.profile_username_label), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = theme.primary)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(userText, { if (it.length <= 50) userText = it },
            placeholder = { Text(stringResource(R.string.profile_username_placeholder), color = theme.textSecondary) },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = theme.inputBorderFocused, unfocusedBorderColor = theme.inputBorder,
                focusedTextColor = theme.textMain, unfocusedTextColor = theme.textMain, cursorColor = theme.primary,
                focusedContainerColor   = if (theme.isDark) theme.surface else Color.Transparent,
                unfocusedContainerColor = if (theme.isDark) theme.surface else Color.Transparent),
            supportingText = {
                Text("${userText.length}/50", Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                    color = if (userText.length >= 50) Color(0xFFCF6679) else theme.textSecondary, fontSize = 11.sp)
            })
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            val ai = remember { MutableInteractionSource() }; val ap by ai.collectIsPressedAsState()
            val asc by animateFloatAsState(if (ap) 0.94f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "an")
            TextButton(onBack, interactionSource = ai, modifier = Modifier.scale(asc)) {
                Text(stringResource(R.string.btn_cancel), color = theme.textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Spacer(Modifier.width(4.dp))
            val si = remember { MutableInteractionSource() }; val sp by si.collectIsPressedAsState()
            val ssc by animateFloatAsState(if (sp) 0.94f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "sv")
            TextButton({ onSave(userText.trim(), pozaPrev, uriSel) }, interactionSource = si, modifier = Modifier.scale(ssc)) {
                Text(stringResource(R.string.btn_save), color = theme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

// =============================================================================
// DARK MODE — TEMĂ
// =============================================================================

data class AppTheme(
    val isDark: Boolean,
    val background: Color, val surface: Color, val surface2: Color,
    val surfaceCard: Color, val surfaceCard2: Color,
    val cardGreenDark: Color, val cardRedDark: Color,
    val weekDividerColor: Color,
    val primary: Color, val primaryLight: Color, val primaryLighter: Color, val primaryDarker: Color,
    val onPrimary: Color,
    val textMain: Color, val textSecondary: Color, val textOnDark: Color,
    val navBar: Color, val navSelected: Color, val navSelectedContent: Color, val navUnselected: Color,
    val cardGreen: Color, val cardGreenText: Color, val cardRed: Color, val cardRedText: Color,
    val divider: Color, val settingsDivider: Color,
    val hourBoxBg: Color, val minuteBoxBg: Color,
    val inputBorder: Color, val inputBorderFocused: Color, val dialogBg: Color
)

fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
    val max = maxOf(r, g, b); val min = minOf(r, g, b); val delta = max - min
    val l = (max + min) / 2f
    val s = if (delta == 0f) 0f else delta / (1f - abs(2f * l - 1f))
    val h = when {
        delta == 0f -> 0f
        max == r    -> 60f * (((g - b) / delta) % 6f)
        max == g    -> 60f * (((b - r) / delta) + 2f)
        else        -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return floatArrayOf(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
}

fun hslToColor(h: Float, s: Float, l: Float): Color {
    val hh = ((h % 360f) + 360f) % 360f
    val ss = s.coerceIn(0f, 1f); val ll = l.coerceIn(0f, 1f)
    val c = (1f - abs(2f * ll - 1f)) * ss
    val x = c * (1f - abs((hh / 60f) % 2f - 1f))
    val m = ll - c / 2f
    val (r1, g1, b1) = when ((hh / 60f).toInt()) {
        0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
    }
    return Color((r1 + m).coerceIn(0f, 1f), (g1 + m).coerceIn(0f, 1f), (b1 + m).coerceIn(0f, 1f))
}

fun accentVar(base: Color, sat: Float, lum: Float): Color {
    val hsl = rgbToHsl(base.red, base.green, base.blue)
    return hslToColor(hsl[0], sat, lum)
}

fun buildTheme(isDark: Boolean, accentIndex: Int): AppTheme {
    val base = CULORI_ACCENT[accentIndex.coerceIn(0, CULORI_ACCENT.size - 1)].base
    val baseSat = rgbToHsl(base.red, base.green, base.blue)[1]
    val primary        = if (isDark) accentVar(base, baseSat.coerceAtLeast(0.55f), 0.72f) else base
    val primaryLight   = if (isDark) accentVar(base, 0.60f, 0.80f) else accentVar(base, 0.55f, 0.65f)
    val primaryLighter = if (isDark) accentVar(base, 0.22f, 0.18f) else accentVar(base, 0.35f, 0.90f)
    val primaryDarker  = if (isDark) accentVar(base, 0.65f, 0.72f) else accentVar(base, 0.80f, 0.28f)
    return AppTheme(
        isDark = isDark,
        background    = if (isDark) accentVar(base, 0.20f, 0.07f) else accentVar(base, 0.25f, 0.97f),
        surface       = if (isDark) accentVar(base, 0.18f, 0.11f) else accentVar(base, 0.20f, 0.96f),
        surface2      = if (isDark) accentVar(base, 0.20f, 0.10f) else accentVar(base, 0.22f, 0.94f),
        surfaceCard   = if (isDark) accentVar(base, 0.28f, 0.09f) else accentVar(base, 0.30f, 0.93f),
        surfaceCard2  = if (isDark) accentVar(base, 0.22f, 0.10f) else accentVar(base, 0.25f, 0.94f),
        cardGreenDark = if (isDark) Color(0xFF0D2010) else Color(0xFFB8DFB9),
        cardRedDark   = if (isDark) Color(0xFF280C0C) else Color(0xFFFFCDD2),
        weekDividerColor = if (isDark) accentVar(base, 0.55f, 0.65f) else accentVar(base, 0.70f, 0.32f),
        primary = primary, primaryLight = primaryLight, primaryLighter = primaryLighter, primaryDarker = primaryDarker,
        onPrimary     = Color.White,
        textMain      = if (isDark) Color(0xFFECE6FF) else Color(0xFF1A1A2E),
        textSecondary = if (isDark) Color(0xFFB0A8C8) else Color(0xFF6B5E80),
        textOnDark    = if (isDark) Color(0xFFECE6FF) else Color(0xFF1A1A2E),
        navBar             = if (isDark) accentVar(base, 0.15f, 0.08f) else accentVar(base, 0.18f, 0.88f),
        navSelected        = if (isDark) accentVar(base, 0.30f, 0.18f) else accentVar(base, 0.45f, 0.85f),
        navSelectedContent = primary,
        navUnselected      = if (isDark) Color(0xFF7A6E90) else Color(0xFF6B5E80),
        cardGreen     = if (isDark) Color(0xFF0D2010) else Color(0xFFB8DFB9),
        cardGreenText = if (isDark) Color(0xFF81C784) else Color(0xFF1B5E20),
        cardRed       = if (isDark) Color(0xFF280C0C) else Color(0xFFFFCDD2),
        cardRedText   = if (isDark) Color(0xFFEF9A9A) else Color(0xFF7F0000),
        divider         = if (isDark) accentVar(base, 0.22f, 0.20f) else accentVar(base, 0.28f, 0.82f),
        settingsDivider = if (isDark) Color(0xFF3A3A3A) else Color(0xFF9E9E9E),
        hourBoxBg   = if (isDark) accentVar(base, 0.25f, 0.16f) else accentVar(base, 0.40f, 0.92f),
        minuteBoxBg = if (isDark) accentVar(base, 0.18f, 0.20f) else accentVar(base, 0.10f, 0.97f),
        inputBorder        = if (isDark) accentVar(base, 0.25f, 0.25f) else accentVar(base, 0.35f, 0.75f),
        inputBorderFocused = primary,
        dialogBg = if (isDark) accentVar(base, 0.20f, 0.10f) else Color.White
    )
}

// =============================================================================
// CULORI ACCENT
// =============================================================================

data class CuloareAccent(val numeRes: Int, val base: Color)

val CULORI_ACCENT = listOf(
    CuloareAccent(R.string.accent_violet,  Color(0xFF6650A4)),
    CuloareAccent(R.string.accent_blue,    Color(0xFF1565C0)),
    CuloareAccent(R.string.accent_green,   Color(0xFF2E7D32)),
    CuloareAccent(R.string.accent_pink,    Color(0xFFC2185B)),
    CuloareAccent(R.string.accent_orange,  Color(0xFFE65100)),
    CuloareAccent(R.string.accent_red,     Color(0xFFC62828)),
    CuloareAccent(R.string.accent_cyan,    Color(0xFF00838F)),
    CuloareAccent(R.string.accent_yellow,  Color(0xFFF9A825))
)

// =============================================================================
// LIMBĂ
// =============================================================================

data class AppLanguage(val cod: String, val numeNativ: String, val flag: String)

val LIMBI_DISPONIBILE = listOf(
    AppLanguage("ro", "Română",   "🇷🇴"),
    AppLanguage("en", "English",  "🇬🇧"),
    AppLanguage("fr", "Français", "🇫🇷")
)

fun detecteazaLimbaImplicita(): String {
    val limbaSistem = Locale.getDefault().language
    return if (LIMBI_DISPONIBILE.any { it.cod == limbaSistem }) limbaSistem else "ro"
}

fun Context.aplicaLimba(cod: String): Context {
    val locale = Locale(cod)
    Locale.setDefault(locale)
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    return createConfigurationContext(config)
}

// =============================================================================
// DATE & HELPERS
// =============================================================================

data class SesiuneSomn(
    val data: String, val ore: Long, val minute: Long,
    val oraInceput: String = "", val oraSfarsit: String = "",
    val dataTimestamp: Long = 0L
)

fun estimeazaTimestamp(dataStr: String): Long {
    if (dataStr.isBlank()) return 0L
    val an = Calendar.getInstance().get(Calendar.YEAR)
    for (locale in listOf(Locale.getDefault(), Locale("ro", "RO"), Locale.ENGLISH)) {
        runCatching {
            val sdf = SimpleDateFormat("dd MMM yyyy", locale).also { it.isLenient = false }
            val cal = Calendar.getInstance().also { c ->
                c.time = sdf.parse("$dataStr $an") ?: return@runCatching
                c.set(Calendar.HOUR_OF_DAY, 12); c.set(Calendar.MINUTE, 0)
                c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }
    }
    return 0L
}

fun getTimestamp(s: SesiuneSomn): Long =
    if (s.dataTimestamp > 0L) s.dataTimestamp else estimeazaTimestamp(s.data)

fun salveazaIstoric(prefs: android.content.SharedPreferences, lista: List<SesiuneSomn>) {
    prefs.edit().putString("ISTORIC",
        lista.joinToString(";") { "${it.data}|${it.ore}|${it.minute}|${it.oraInceput}|${it.oraSfarsit}|${it.dataTimestamp}" }
    ).apply()
}

fun getSaptamanaStartMs(ts: Long): Long {
    if (ts <= 0L) return 0L
    val cal = Calendar.getInstance()
    cal.timeInMillis = ts
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    cal.add(Calendar.DAY_OF_MONTH, -(cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY))
    return cal.timeInMillis
}

fun salveazaPoza(context: Context, uri: Uri): Boolean = runCatching {
    val inp = context.contentResolver.openInputStream(uri) ?: return false
    FileOutputStream(File(context.filesDir, "profile_pic.jpg")).use { inp.copyTo(it) }
    true
}.getOrDefault(false)

fun incarcaPoza(context: Context): ImageBitmap? = runCatching {
    val f = File(context.filesDir, "profile_pic.jpg")
    if (!f.exists()) return null
    BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
}.getOrNull()

// =============================================================================
// EFECTE VIZUALE SOMN
// =============================================================================

data class Stea(val x: Float, val y: Float, val raza: Float, val faza: Float, val viteza: Float)

@Composable
fun EfectStele(modifier: Modifier = Modifier) {
    val stele = remember {
        List(80) { Stea(Random().nextFloat(), Random().nextFloat(),
            Random().nextFloat() * 2.5f + 0.5f,
            Random().nextFloat() * (2 * PI).toFloat(),
            Random().nextFloat() * 0.8f + 0.3f) }
    }
    val infinit = rememberInfiniteTransition(label = "stele")
    val timp by infinit.animateFloat(0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "t")
    Canvas(modifier = modifier) {
        stele.forEach { s ->
            val a = (sin((timp * s.viteza + s.faza).toDouble()).toFloat() * 0.5f + 0.5f).coerceIn(0.1f, 1f)
            drawCircle(Color.White.copy(alpha = a * 0.9f), s.raza * (0.7f + a * 0.3f), Offset(s.x * size.width, s.y * size.height))
        }
    }
}

@Composable
fun LunaPulsanda(culoare: Color, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "luna")
    val scala by inf.animateFloat(1f, 1.08f, infiniteRepeatable(tween(3000, easing = EaseInOutSine), RepeatMode.Reverse), "scala")
    val rot   by inf.animateFloat(-8f, 8f,   infiniteRepeatable(tween(5000, easing = EaseInOutSine), RepeatMode.Reverse), "rot")
    val glow  by inf.animateFloat(0.15f, 0.35f, infiniteRepeatable(tween(2500, easing = EaseInOutSine), RepeatMode.Reverse), "glow")
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.size(120.dp).scale(scala * 1.2f).clip(CircleShape).background(culoare.copy(alpha = glow * 0.4f)))
        Box(Modifier.size(100.dp).scale(scala * 1.1f).clip(CircleShape).background(culoare.copy(alpha = glow * 0.6f)))
        Box(Modifier.size(80.dp).scale(scala).graphicsLayer { rotationZ = rot }
            .clip(CircleShape).background(culoare.copy(alpha = 0.25f))
            .border(1.5.dp, culoare.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center) { Text("🌙", fontSize = 38.sp) }
    }
}

data class ParticulaZ(val xRel: Float, val yStart: Float, val marime: Float, val delay: Int, val durata: Int)

@Composable
fun ParticuleSomnoroase(modifier: Modifier = Modifier, culoare: Color) {
    val particule = remember {
        List(6) { i -> ParticulaZ(
            0.2f + Random().nextFloat() * 0.6f, 0.3f + Random().nextFloat() * 0.4f,
            14f + Random().nextFloat() * 12f, i * 800, 3500 + Random().nextInt(1500)) }
    }
    particule.forEach { p ->
        val inf = rememberInfiniteTransition(label = "z${p.delay}")
        val prog by inf.animateFloat(0f, 1f,
            infiniteRepeatable(tween(p.durata, delayMillis = p.delay, easing = LinearEasing)), "p${p.delay}")
        val alpha = (when {
            prog < 0.1f -> prog / 0.1f
            prog > 0.7f -> 1f - (prog - 0.7f) / 0.3f
            else -> 1f
        } * 0.7f)
        Box(modifier = modifier.fillMaxSize()) {
            Text("z", fontSize = p.marime.sp, color = culoare.copy(alpha = alpha), fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.TopStart)
                    .offset(x = (p.xRel * 300).dp, y = ((p.yStart * 400) + (-120f * prog)).dp)
                    .scale(0.6f + prog * 0.6f).graphicsLayer { rotationZ = -15f + prog * 20f })
        }
    }
}

@Composable
fun NoriLenti(modifier: Modifier = Modifier, culoare: Color) {
    val inf = rememberInfiniteTransition(label = "nori")
    val o1 by inf.animateFloat(-200f, 400f,  infiniteRepeatable(tween(18000, easing = LinearEasing)), "n1")
    val o2 by inf.animateFloat(200f,  -400f, infiniteRepeatable(tween(22000, easing = LinearEasing)), "n2")
    Box(modifier = modifier.fillMaxSize()) {
        Box(Modifier.offset(x = o1.dp, y = 60.dp).size(200.dp, 60.dp).clip(RoundedCornerShape(50.dp)).background(culoare.copy(alpha = 0.06f)))
        Box(Modifier.offset(x = o2.dp, y = 120.dp).size(160.dp, 45.dp).clip(RoundedCornerShape(50.dp)).background(culoare.copy(alpha = 0.04f)))
    }
}