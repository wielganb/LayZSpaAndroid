package pl.smartspa.showroom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val Navy = Color(0xFF061923)
private val Panel = Color(0xFF102A37)
private val Panel2 = Color(0xFF173747)
private val Blue = Color(0xFF159BFF)
private val Cyan = Color(0xFF48D7FF)
private val Green = Color(0xFF4DE38A)
private val Orange = Color(0xFFFF8B55)
private val TextMain = Color(0xFFF3F8FC)
private val TextMuted = Color(0xFF9FB8C7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SmartSpaApp() }
    }
}

@Composable
fun SmartSpaApp() {
    var tab by remember { mutableIntStateOf(0) }
    var temperature by remember { mutableFloatStateOf(37f) }
    var target by remember { mutableFloatStateOf(38f) }
    var heating by remember { mutableStateOf(true) }
    var bubbles by remember { mutableStateOf(false) }
    var filtration by remember { mutableStateOf(true) }
    var session by remember { mutableIntStateOf(30) }
    var running by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Navy,
            surface = Panel,
            primary = Blue,
            secondary = Cyan,
            onBackground = TextMain,
            onSurface = TextMain
        )
    ) {
        Surface(color = Navy, modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = Navy,
                bottomBar = {
                    NavigationBar(containerColor = Color(0xFF07151D)) {
                        listOf(
                            "Start" to Icons.Default.Home,
                            "Urządzenia" to Icons.Default.Devices,
                            "Harmonogram" to Icons.Default.CalendarMonth,
                            "Statystyki" to Icons.Default.BarChart
                        ).forEachIndexed { index, item ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Icon(item.second, null) },
                                label = { Text(item.first, fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Blue,
                                    selectedTextColor = Blue,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted,
                                    indicatorColor = Panel2
                                )
                            )
                        }
                    }
                }
            ) { padding ->
                when (tab) {
                    0 -> HomeScreen(
                        padding, temperature, target, heating, bubbles, filtration,
                        session, running,
                        onHeating = { heating = !heating },
                        onBubbles = { bubbles = !bubbles },
                        onFiltration = { filtration = !filtration },
                        onSession = { session = it },
                        onRun = { running = !running }
                    )
                    1 -> DevicesScreen(padding)
                    2 -> ScheduleScreen(padding)
                    else -> StatisticsScreen(padding)
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Menu, null, tint = TextMain, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, color = TextMuted, fontSize = 12.sp) }
        }
        Icon(Icons.Default.Settings, null, tint = TextMuted)
    }
}

@Composable
private fun HomeScreen(
    padding: PaddingValues,
    temperature: Float,
    target: Float,
    heating: Boolean,
    bubbles: Boolean,
    filtration: Boolean,
    session: Int,
    running: Boolean,
    onHeating: () -> Unit,
    onBubbles: () -> Unit,
    onFiltration: () -> Unit,
    onSession: (Int) -> Unit,
    onRun: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 18.dp)
    ) {
        item {
            Header("Smart Spa", "Twój komfort. Zawsze pod kontrolą.")
            Card(
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.padding(horizontal = 14.dp).fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Blue, Cyan))),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Water, null, tint = Color.White) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Jacuzzi Ogród", fontWeight = FontWeight.Bold)
                            Text("MIAMI2021", color = TextMuted, fontSize = 12.sp)
                        }
                        Text("● Demo", color = Green, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(18.dp))
                    Box(
                        modifier = Modifier.size(230.dp).clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    listOf(Blue, Cyan, Panel2, Blue)
                                )
                            ).padding(9.dp).clip(CircleShape)
                            .background(Navy),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${temperature.roundToInt()}°C", fontSize = 43.sp, color = Cyan, fontWeight = FontWeight.Bold)
                            Text("Aktualna temperatura", color = TextMain, fontSize = 13.sp)
                            Spacer(Modifier.height(10.dp))
                            Text("Cel: ${target.roundToInt()}°C", color = Cyan, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton("−") {}
                        SmallButton("+") {}
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        StatusTile("Grzanie", heating, Icons.Default.LocalFireDepartment, Orange, onHeating, Modifier.weight(1f))
                        StatusTile("Bąbelki", bubbles, Icons.Default.BubbleChart, Blue, onBubbles, Modifier.weight(1f))
                        StatusTile("Filtracja", filtration, Icons.Default.FilterAlt, Green, onFiltration, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Szybka sesja SPA", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(1, 5, 10, 15, 20, 30).forEach {
                            Box(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                                    .background(if (session == it) Blue else Panel2)
                                    .clickable { onSession(it) }.padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center
                            ) { Text("$it", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onRun,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = if (running) Orange else Blue)
                    ) {
                        Icon(if (running) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (running) "Zakończ sesję" else "Uruchom sesję • $session min")
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 0.dp)) {
        Text(label, fontSize = 20.sp)
    }
}

@Composable
private fun StatusTile(
    label: String, active: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color, onClick: () -> Unit, modifier: Modifier
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(if (active) color.copy(alpha = .18f) else Panel2)
            .clickable { onClick() }.padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(27.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(if (active) "Włączone" else "Wyłączone", color = if (active) color else TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun DevicesScreen(padding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
        item { Header("Moje SPA", "Wybierz urządzenie") }
        items(listOf("Jacuzzi Ogród" to "37°C", "Jacuzzi Dom" to "35°C", "Jacuzzi Rodzice" to "—°C", "Spa Domek" to "36°C")) { (name, temp) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Panel),
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                shape = RoundedCornerShape(15.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(70.dp).clip(RoundedCornerShape(12.dp)).background(Panel2), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.HotTub, null, tint = Cyan, modifier = Modifier.size(42.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.Bold)
                        Text("MIAMI2021", color = TextMuted, fontSize = 12.sp)
                        Text("● Online", color = Green, fontSize = 12.sp)
                    }
                    Text(temp, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ScheduleScreen(padding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
        item { Header("Harmonogram", "Gotowe sesje") }
        items(listOf("Poranny start" to "Codziennie • 07:00", "Wieczorne SPA" to "Pon, Wt, Śr, Czw, Nd • 20:00", "Filtracja dzienna" to "Codziennie • 10:00", "Filtracja nocna" to "Codziennie • 02:00")) { (title, desc) ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = Cyan, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.Bold)
                        Text(desc, color = TextMuted, fontSize = 12.sp)
                    }
                    Switch(checked = true, onCheckedChange = {})
                }
            }
        }
        item {
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Dodaj harmonogram")
            }
        }
    }
}

@Composable
private fun StatisticsScreen(padding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
        item { Header("Statystyki", "Ostatnie 7 dni") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Temperatura wody", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    Text("36.2°C", fontSize = 35.sp, color = Cyan, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().height(100.dp)) {
                        listOf(42, 60, 48, 75, 55, 90, 68).forEach { h ->
                            Box(Modifier.weight(1f).fillMaxHeight(h / 100f).clip(RoundedCornerShape(5.dp)).background(Blue))
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Metric("Czas grzania", "18h 25min")
                        Metric("Zużycie", "12.4 kWh")
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Panel2).padding(12.dp)) {
        Text(label, color = TextMuted, fontSize = 11.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}
