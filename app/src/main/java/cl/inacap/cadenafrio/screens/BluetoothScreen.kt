package cl.inacap.cadenafrio.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cl.inacap.cadenafrio.bluetooth.BluetoothSppManager
import cl.inacap.cadenafrio.data.FirebaseRepository
import cl.inacap.cadenafrio.model.EstadoCadenaFrio
import cl.inacap.cadenafrio.model.Telemetria

// Pantalla para conectar por Bluetooth de forma local (HC-05 o ESP32)
@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothScreen(
    repo: FirebaseRepository,
    onVolver: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val btManager = remember { BluetoothSppManager(context) }

    val permisosRequeridos = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }
    }

    var tienePermisos by remember {
        mutableStateOf(
            permisosRequeridos.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    var dispositivos by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var conectado by remember { mutableStateOf(false) }
    var mensajeEstado by remember { mutableStateOf("Selecciona un dispositivo HC-05 / ESP32") }
    var telemetriaLocal by remember { mutableStateOf<Telemetria?>(null) }
    var reenviarAFirebase by remember { mutableStateOf(false) }

    val launcherPermisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultado ->
        tienePermisos = resultado.values.all { it }
        if (tienePermisos) {
            dispositivos = btManager.obtenerDispositivosEmparejados()
        }
    }

    LaunchedEffect(tienePermisos) {
        if (tienePermisos) {
            dispositivos = btManager.obtenerDispositivosEmparejados()
        }
    }

    DisposableEffect(Unit) {
        onDispose { btManager.desconectar() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conexion Local Bluetooth") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!tienePermisos) {
                Text("Se necesitan permisos de Bluetooth para buscar dispositivos.")
                Button(onClick = { launcherPermisos.launch(permisosRequeridos) }) {
                    Text("Dar permisos de Bluetooth")
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(mensajeEstado, fontWeight = FontWeight.SemiBold)
                        telemetriaLocal?.let { t ->
                            val eval = EstadoCadenaFrio.evaluar(t.temperatura, t.puertaAbierta)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "${eval.etiqueta} | ${String.format("%.1f °C", t.temperatura)} | Puerta: ${if (t.puertaAbierta) "Abierta" else "Cerrada"}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                if (conectado) {
                    val rol = repo.rolActual()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { btManager.enviarComando("CMD:SILENCIO", scope) {} },
                            enabled = rol.puedeSilenciarAlarma,
                            modifier = Modifier.weight(1f)
                        ) { Text("Silenciar") }
                        OutlinedButton(
                            onClick = { btManager.enviarComando("CMD:TEST", scope) {} },
                            enabled = rol.puedeEjecutarTestYAuto,
                            modifier = Modifier.weight(1f)
                        ) { Text("Test") }
                        OutlinedButton(
                            onClick = {
                                btManager.desconectar()
                                conectado = false
                                mensajeEstado = "Desconectado"
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Salir") }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Subir lecturas BT a Firebase (Gateway)")
                    Switch(checked = reenviarAFirebase, onCheckedChange = { reenviarAFirebase = it })
                }

                Text("Dispositivos vinculados:", style = MaterialTheme.typography.titleSmall)

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(dispositivos, key = { it.address }) { dev ->
                        Card(
                            onClick = {
                                btManager.conectar(
                                    device = dev,
                                    scope = scope,
                                    onEstadoConexion = { ok, msg ->
                                        conectado = ok
                                        mensajeEstado = msg
                                    },
                                    onTelemetria = { t ->
                                        telemetriaLocal = t
                                        if (reenviarAFirebase) {
                                            repo.publicarTelemetria(t.temperatura, t.puertaAbierta, t.origen) {}
                                        }
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Bluetooth, contentDescription = null)
                                Column {
                                    Text(dev.name ?: "Dispositivo sin nombre", fontWeight = FontWeight.Bold)
                                    Text(dev.address, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
