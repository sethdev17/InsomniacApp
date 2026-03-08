package com.app.insomniac

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aplicatiamea.ui.theme.AplicatiaMeaTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

data class SesiuneSomn(
    val data: String,
    val ore: Long,
    val minute: Long,
    val oraInceput: String = "",
    val oraSfarsit: String = "",
    val dataTimestamp: Long = 0L
)

// ✅ FIX: ora setată la 12:00 ca să evite probleme de DST/timezone
fun estimeazaTimestamp(dataStr: String): Long {
    if (dataStr.isBlank()) return 0L
    val anCurent = Calendar.getInstance().get(Calendar.YEAR)
    val locales = listOf(Locale.getDefault(), Locale("ro", "RO"), Locale.ENGLISH)
    for (locale in locales) {
        runCatching {
            val sdf = SimpleDateFormat("dd MMM yyyy", locale)
            sdf.isLenient = false
            val parsed = sdf.parse("$dataStr $anCurent") ?: return@runCatching
            val cal = Calendar.getInstance()
            cal.time = parsed
            cal.set(Calendar.HOUR_OF_DAY, 12)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
    }
    return 0L
}

fun getTimestamp(sesiune: SesiuneSomn): Long {
    return if (sesiune.dataTimestamp > 0L) sesiune.dataTimestamp
    else estimeazaTimestamp(sesiune.data)
}

fun salveazaIstoric(stocare: android.content.SharedPreferences, lista: List<SesiuneSomn>) {
    val text = lista.joinToString(";") {
        "${it.data}|${it.ore}|${it.minute}|${it.oraInceput}|${it.oraSfarsit}|${it.dataTimestamp}"
    }
    stocare.edit().putString("ISTORIC", text).apply()
}

// ✅ FIX: normalizare corectă la Duminica săptămânii
fun getSaptamanaStartMs(ts: Long): Long {
    if (ts <= 0L) return 0L
    val cal = Calendar.getInstance()
    cal.timeInMillis = ts
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    // Duminică=1, Luni=2, ..., Sâmbătă=7 → dăm înapoi până la Duminică
    val dow = cal.get(Calendar.DAY_OF_WEEK)
    cal.add(Calendar.DAY_OF_MONTH, -(dow - Calendar.SUNDAY))
    return cal.timeInMillis
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AplicatiaMeaTheme {
                InsomniacApp()
            }
        }
    }
}

@Composable
fun InsomniacApp() {
    val context = LocalContext.current
    val stocare = remember { context.getSharedPreferences("InsomniacData", Context.MODE_PRIVATE) }

    var tabCurent by remember { mutableStateOf(0) }
    var doarme by remember { mutableStateOf(stocare.getBoolean("DOARME", false)) }
    var oraDeInceput by remember { mutableLongStateOf(stocare.getLong("ORA_INCEPUT", 0L)) }

    val istoricSomn = remember {
        val lista = mutableStateListOf<SesiuneSomn>()
        val textSalvat = stocare.getString("ISTORIC", "") ?: ""
        if (textSalvat.isNotEmpty()) {
            val sesiuni = textSalvat.split(";").mapNotNull { linie ->
                val parti = linie.split("|")
                if (parti.size >= 3) SesiuneSomn(
                    parti[0],
                    parti[1].toLong(),
                    parti[2].toLong(),
                    if (parti.size > 3) parti[3] else "",
                    if (parti.size > 4) parti[4] else "",
                    if (parti.size > 5) parti[5].toLong() else 0L
                ) else null
            }
            lista.addAll(sesiuni)
        }
        lista
    }

    val backgroundColor by animateColorAsState(
        if (doarme) Color(0xFF120E1F) else Color(0xFFF3E8FF), tween(1000), label = "bg"
    )
    val textColor by animateColorAsState(
        if (doarme) Color.White else Color(0xFF1F1F1F), label = "text"
    )
    val selectedBubbleColor by animateColorAsState(
        if (doarme) Color(0xFF4F378B) else Color(0xFFD8B4FF), label = "bubble"
    )
    val selectedContentColor by animateColorAsState(
        if (doarme) Color.White else Color(0xFF381E72), label = "selContent"
    )
    val unselectedContentColor by animateColorAsState(
        if (doarme) Color.Gray else Color(0xFF7A6B8F), label = "unselContent"
    )

    Scaffold(
        containerColor = backgroundColor,
        bottomBar = {
            NavigationBar(
                containerColor = if (doarme) Color(0xFF1A152B) else Color.Transparent,
                tonalElevation = 0.dp
            ) {
                val meniuri = listOf(
                    "Principal" to Icons.Default.Home,
                    "Istoric" to Icons.Default.DateRange,
                    "Setări" to Icons.Default.Settings
                )
                meniuri.forEachIndexed { index, (nume, icon) ->
                    val isSelected = tabCurent == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { tabCurent = index },
                        icon = {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .background(selectedBubbleColor, RoundedCornerShape(16.dp))
                                        .clickable { }
                                        .padding(horizontal = 20.dp, vertical = 6.dp)
                                ) {
                                    Icon(icon, contentDescription = nume, tint = selectedContentColor)
                                }
                            } else {
                                Icon(icon, contentDescription = nume, tint = unselectedContentColor)
                            }
                        },
                        label = {
                            Text(
                                nume,
                                color = if (isSelected) selectedContentColor else unselectedContentColor,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = if (isSelected) 13.sp else 12.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (tabCurent) {
                0 -> EcranPrincipal(doarme, textColor, oraDeInceput) {
                    if (!doarme) {
                        oraDeInceput = System.currentTimeMillis()
                        doarme = true
                        stocare.edit()
                            .putBoolean("DOARME", true)
                            .putLong("ORA_INCEPUT", oraDeInceput)
                            .apply()
                    } else {
                        val timpDormitMs = System.currentTimeMillis() - oraDeInceput
                        val ore = (timpDormitMs / (1000 * 60 * 60)) % 24
                        val minute = (timpDormitMs / (1000 * 60)) % 60
                        val formatOraApp = SimpleDateFormat("HH:mm", Locale.getDefault())
                        val oraStart = formatOraApp.format(Date(oraDeInceput))
                        val oraStop = formatOraApp.format(Date())
                        val acum = System.currentTimeMillis()

                        istoricSomn.add(
                            SesiuneSomn(
                                SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date()),
                                ore, minute, oraStart, oraStop, acum
                            )
                        )
                        doarme = false
                        salveazaIstoric(stocare, istoricSomn)
                        stocare.edit()
                            .putBoolean("DOARME", false)
                            .putLong("ORA_INCEPUT", 0L)
                            .apply()
                    }
                }
                1 -> EcranIstoric(
                    istoric = istoricSomn,
                    textColor = textColor,
                    onSterge = { sesiune ->
                        istoricSomn.remove(sesiune)
                        salveazaIstoric(stocare, istoricSomn)
                    }
                )
                2 -> Text(
                    "În curând...",
                    color = textColor,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
fun EcranPrincipal(
    doarme: Boolean,
    textColor: Color,
    oraDeInceput: Long,
    onSchimbaStareSomn: () -> Unit
) {
    var timpCurent by remember { mutableStateOf(Calendar.getInstance().time) }
    LaunchedEffect(Unit) {
        while (true) {
            timpCurent = Calendar.getInstance().time
            delay(1000)
        }
    }
    val formatOra = SimpleDateFormat("HH", Locale.getDefault()).format(timpCurent)
    val formatMinute = SimpleDateFormat("mm", Locale.getDefault()).format(timpCurent)

    Column(
        modifier = Modifier.fillMaxSize().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Insomniac", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = textColor)
        Text(
            "Contorizează-ți somnul",
            fontSize = 14.sp,
            color = if (doarme) Color.LightGray else Color.DarkGray
        )
        Spacer(modifier = Modifier.height(40.dp))
        Box(
            modifier = Modifier.size(90.dp).clip(CircleShape).background(Color(0xFFD8B4FF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(60.dp), tint = Color.Black)
        }
        Spacer(modifier = Modifier.height(50.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = if (doarme) Color(0xFF26203B) else Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.size(width = 320.dp, height = 220.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (doarme) {
                    var timpScurs by remember { mutableLongStateOf(System.currentTimeMillis() - oraDeInceput) }
                    LaunchedEffect(doarme) {
                        while (doarme) {
                            timpScurs = System.currentTimeMillis() - oraDeInceput
                            delay(1000)
                        }
                    }
                    val secunde = (timpScurs / 1000) % 60
                    val minute = (timpScurs / (1000 * 60)) % 60
                    val ore = (timpScurs / (1000 * 60 * 60)) % 24
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Somn ușor 🌙", fontSize = 22.sp, color = Color(0xFFD8B4FF), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            String.format("%02d:%02d:%02d", ore, minute, secunde),
                            fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Ora curentă", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp, 80.dp)
                                    .background(Color(0xFFF3E8FF), RoundedCornerShape(12.dp))
                                    .border(2.dp, Color(0xFF4F378B), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(formatOra, fontSize = 48.sp, color = Color(0xFF4F378B), fontWeight = FontWeight.Bold)
                            }
                            Text(" : ", fontSize = 48.sp, color = Color.Black, modifier = Modifier.padding(horizontal = 8.dp))
                            Box(
                                modifier = Modifier
                                    .size(90.dp, 80.dp)
                                    .background(Color(0xFFF2F2F2), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(formatMinute, fontSize = 48.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(50.dp))
        ElevatedButton(
            onClick = onSchimbaStareSomn,
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = if (doarme) Color(0xFFFFB4AB) else Color(0xFFEADDFF)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.size(width = 200.dp, height = 60.dp)
        ) {
            if (!doarme) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF4F378B))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                if (doarme) "M-am trezit" else "Mă pun la somn",
                color = if (doarme) Color(0xFF690005) else Color(0xFF4F378B),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}

@Composable
fun EcranIstoric(
    istoric: List<SesiuneSomn>,
    textColor: Color,
    onSterge: (SesiuneSomn) -> Unit
) {
    val istoricValid = istoric.filter { it.ore > 0 || it.minute > 0 }

    val totalMinuteGlobal = istoricValid.sumOf { it.ore * 60 + it.minute }
    val medieGlobalaOre = if (istoricValid.isNotEmpty()) totalMinuteGlobal / istoricValid.size / 60 else 0
    val medieGlobalaMinute = if (istoricValid.isNotEmpty()) (totalMinuteGlobal / istoricValid.size) % 60 else 0
    val nrZile = istoricValid.size

    data class StatusInfo(val text: String, val color: Color, val emoji: String, val detaliu: String)

    val status = remember(medieGlobalaOre, nrZile) {
        when {
            nrZile == 0 -> StatusInfo("Nu ai date încă.", Color.White, "", "Începe să înregistrezi somnul.")
            medieGlobalaOre >= 9 -> StatusInfo("Dormitor Campion", Color(0xFF90EE90), "🏆", "Dormi mai mult decât recomandat. Poți reduce ușor durata.")
            medieGlobalaOre >= 7 -> StatusInfo("Odihnit și Sănătos", Color(0xFF90EE90), "💚", "Felicitări! Ești în intervalul optim de somn.")
            medieGlobalaOre >= 6 -> StatusInfo("Ușor Obosit", Color(0xFFFFE082), "🌤️", "Ești aproape de ideal. Încearcă să adaugi 30–60 min.")
            medieGlobalaOre >= 5 -> StatusInfo("Obosit", Color(0xFFFFB74D), "😴", "Deficitul de somn acumulat îți afectează concentrarea.")
            medieGlobalaOre >= 4 -> StatusInfo("Epuizat", Color(0xFFFF8A65), "⚠️", "Somn insuficient cronic. Încearcă să dormi mai mult.")
            else -> StatusInfo("Privat de Somn", Color(0xFFFFB4AB), "🚨", "Risc ridicat! Sub 4h afectează grav sănătatea.")
        }
    }

    val startSaptCurenta = getSaptamanaStartMs(System.currentTimeMillis())
    val endSaptCurenta = startSaptCurenta + (7L * 24 * 60 * 60 * 1000) - 1

    val sesiuniSaptAceasta = istoricValid.filter { sesiune ->
        val ts = getTimestamp(sesiune)
        ts in startSaptCurenta..endSaptCurenta
    }
    val totalMinSaptAceasta = sesiuniSaptAceasta.sumOf { it.ore * 60 + it.minute }
    val medieSaptOre = if (sesiuniSaptAceasta.isNotEmpty()) totalMinSaptAceasta / sesiuniSaptAceasta.size / 60 else 0
    val medieSaptMin = if (sesiuniSaptAceasta.isNotEmpty()) (totalMinSaptAceasta / sesiuniSaptAceasta.size) % 60 else 0

    fun numeZiSiData(sesiune: SesiuneSomn): String {
        val ts = getTimestamp(sesiune)
        if (ts <= 0L) return sesiune.data
        val cal = Calendar.getInstance()
        cal.timeInMillis = ts
        val numeZi = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Luni"
            Calendar.TUESDAY -> "Marți"
            Calendar.WEDNESDAY -> "Miercuri"
            Calendar.THURSDAY -> "Joi"
            Calendar.FRIDAY -> "Vineri"
            Calendar.SATURDAY -> "Sâmbătă"
            Calendar.SUNDAY -> "Duminică"
            else -> sesiune.data
        }
        val formatComplet = SimpleDateFormat("dd MMMM", Locale("ro", "RO"))
        return "$numeZi - ${formatComplet.format(cal.time)}"
    }

    val grupuriSaptamani = istoricValid
        .reversed()
        .groupBy { getSaptamanaStartMs(getTimestamp(it)) }
        .entries
        .sortedByDescending { it.key }

    var expandedIndex by remember { mutableStateOf(-1) }
    var stergeConfirmare by remember { mutableStateOf<SesiuneSomn?>(null) }

    stergeConfirmare?.let { sesiuneDeSters ->
        AlertDialog(
            onDismissRequest = { stergeConfirmare = null },
            title = { Text("Șterge sesiunea?") },
            text = {
                Text("Sigur vrei să ștergi sesiunea din ${numeZiSiData(sesiuneDeSters)}? Acțiunea nu poate fi anulată.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onSterge(sesiuneDeSters)
                    expandedIndex = -1
                    stergeConfirmare = null
                }) {
                    Text("Șterge", color = Color(0xFFBA1A1A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { stergeConfirmare = null }) {
                    Text("Anulează")
                }
            }
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp)) {

        item {
            Text("Istoricul tău", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = textColor)
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF26203B)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Status General", color = Color.LightGray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    if (nrZile == 0) {
                        Text("Nu ai date încă.", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Începe să înregistrezi somnul.", color = Color.Gray, fontSize = 13.sp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${status.emoji} ", fontSize = 22.sp)
                            Text(status.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = status.color)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(status.detaliu, color = Color.LightGray, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFF3D3356), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Medie globală: ${medieGlobalaOre}h ${medieGlobalaMinute}m/noapte din $nrZile ${if (nrZile == 1) "zi" else "zile"}",
                            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1A30)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Media Săptămânii Curente", color = Color.LightGray, fontSize = 13.sp)
                    Text("(Duminică – Sâmbătă)", color = Color(0xFF6B5F80), fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    if (sesiuniSaptAceasta.isEmpty()) {
                        Text("Nicio înregistrare săptămâna aceasta.", color = Color.White, fontSize = 14.sp)
                    } else {
                        Text(
                            "📅 ${medieSaptOre}h ${medieSaptMin}m / noapte",
                            color = Color(0xFFD8B4FF), fontSize = 18.sp, fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Media din ${sesiuniSaptAceasta.size} ${if (sesiuniSaptAceasta.size == 1) "noapte înregistrată" else "nopți înregistrate"} până acum",
                            color = Color.Gray, fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color(0xFF3D3356), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "💡 Ideal: 7–9 ore pe noapte pentru adulți",
                        color = Color(0xFFB0A0CC), fontSize = 13.sp, fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        grupuriSaptamani.forEach { (saptamanaMs, sesiuni) ->
            if (sesiuni.isNotEmpty()) {
                val totalMinSapt = sesiuni.sumOf { it.ore * 60 + it.minute }
                val totalOreSapt = totalMinSapt / 60
                val totalMinRestSapt = totalMinSapt % 60

                val calStart = Calendar.getInstance()
                calStart.timeInMillis = if (saptamanaMs > 0L) saptamanaMs else System.currentTimeMillis()
                val startPerioadaStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(calStart.time)
                val calEnd = Calendar.getInstance()
                calEnd.timeInMillis = if (saptamanaMs > 0L) saptamanaMs else System.currentTimeMillis()
                calEnd.add(Calendar.DAY_OF_MONTH, 6)
                val endPerioadaStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(calEnd.time)

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF4F378B), thickness = 1.dp)
                        Text(
                            "  $startPerioadaStr – $endPerioadaStr = ${totalOreSapt}h ${totalMinRestSapt}m  ",
                            color = Color(0xFFB39DDB), fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF4F378B), thickness = 1.dp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                sesiuni.forEachIndexed { _, sesiune ->
                    val globalIndex = istoricValid.reversed().indexOf(sesiune)
                    item(key = "${saptamanaMs}_${globalIndex}") {
                        val eSanatos = sesiune.ore >= 7
                        val isExpanded = expandedIndex == globalIndex

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clickable { expandedIndex = if (isExpanded) -1 else globalIndex },
                            colors = CardDefaults.cardColors(
                                containerColor = if (eSanatos) Color(0xFFD7E8CD) else Color(0xFFFFDAD6)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            numeZiSiData(sesiune),
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            "Ore dormite: ${sesiune.ore}h ${sesiune.minute}m",
                                            color = Color.DarkGray,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            if (eSanatos) "Excelent" else "Insuficient",
                                            color = if (eSanatos) Color(0xFF386A20) else Color(0xFFBA1A1A),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            if (isExpanded) Icons.Default.KeyboardArrowUp
                                            else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = Color.DarkGray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                if (isExpanded) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = Color.Black.copy(alpha = 0.15f), thickness = 1.dp)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    if (sesiune.oraInceput.isNotEmpty()) {
                                        Row(verticalAlignment = Alignment.Top) {
                                            Text("🛌", fontSize = 15.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    "Am adormit la: ${sesiune.oraInceput}",
                                                    color = Color.DarkGray,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Text(
                                                    "M-am trezit la: ${sesiune.oraSfarsit}",
                                                    color = Color.DarkGray,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            "Orele nu au fost salvate pentru această sesiune.",
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = { stergeConfirmare = sesiune },
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = Color(0xFFBA1A1A)
                                            )
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Șterge",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Șterge sesiunea", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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