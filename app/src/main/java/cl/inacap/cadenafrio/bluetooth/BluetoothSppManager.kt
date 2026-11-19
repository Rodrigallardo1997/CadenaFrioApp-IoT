package cl.inacap.cadenafrio.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import cl.inacap.cadenafrio.model.EstadoCadenaFrio
import cl.inacap.cadenafrio.model.Telemetria
import cl.inacap.cadenafrio.security.SeguridadIso27400
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID

// Manejo de conexion Bluetooth clasica (HC-05 / ESP32)
class BluetoothSppManager(context: Context) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var lecturaJob: Job? = null

    companion object {
        // UUID estandar para modulos seriales Bluetooth SPP
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    fun bluetoothDisponible(): Boolean = adapter != null && adapter.isEnabled

    @SuppressLint("MissingPermission")
    fun obtenerDispositivosEmparejados(): List<BluetoothDevice> {
        return adapter?.bondedDevices?.toList() ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    fun conectar(
        device: BluetoothDevice,
        scope: CoroutineScope,
        onEstadoConexion: (Boolean, String) -> Unit,
        onTelemetria: (Telemetria) -> Unit
    ) {
        desconectar()
        lecturaJob = scope.launch(Dispatchers.IO) {
            try {
                adapter?.cancelDiscovery()
                val btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                btSocket.connect()
                socket = btSocket
                outputStream = btSocket.outputStream

                withContext(Dispatchers.Main) {
                    onEstadoConexion(true, "Conectado a ${device.name ?: device.address}")
                }

                val reader = BufferedReader(InputStreamReader(btSocket.inputStream))
                while (btSocket.isConnected) {
                    val linea = reader.readLine() ?: break
                    val telemetria = parsearTramaSerial(linea, device.name ?: "Bluetooth-Local")
                    if (telemetria != null) {
                        withContext(Dispatchers.Main) {
                            onTelemetria(telemetria)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onEstadoConexion(false, "Desconectado: ${e.localizedMessage ?: "Error BT"}")
                }
            }
        }
    }

    fun enviarComando(comando: String, scope: CoroutineScope, onResultado: (Boolean) -> Unit) {
        if (!SeguridadIso27400.esComandoValido(comando)) {
            onResultado(false)
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                outputStream?.write("$comando\n".toByteArray(Charsets.UTF_8))
                outputStream?.flush()
                withContext(Dispatchers.Main) { onResultado(true) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResultado(false) }
            }
        }
    }

    fun desconectar() {
        lecturaJob?.cancel()
        lecturaJob = null
        try {
            outputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            // Ignorar error al cerrar socket
        } finally {
            outputStream = null
            socket = null
        }
    }

    // Lee el formato "T:5.2,P:0" que manda el Arduino por Serial
    private fun parsearTramaSerial(trama: String, origen: String): Telemetria? {
        val limpia = trama.trim()
        val idxT = limpia.indexOf("T:")
        val idxP = limpia.indexOf(",P:")
        if (idxT != 0 || idxP < 3) return null

        val temp = limpia.substring(2, idxP).toDoubleOrNull() ?: return null
        val puertaStr = limpia.substring(idxP + 3)
        if (puertaStr != "0" && puertaStr != "1") return null
        if (!SeguridadIso27400.esTemperaturaPlausible(temp)) return null

        val puertaAbierta = puertaStr == "1"
        val estado = EstadoCadenaFrio.evaluar(temp, puertaAbierta)
        val ahora = System.currentTimeMillis()
        return Telemetria(
            temperatura = temp,
            puertaAbierta = puertaAbierta,
            estado = estado.etiqueta,
            origen = "BT: $origen",
            timestamp = ahora,
            firmaIntegridad = SeguridadIso27400.generarFirmaIntegridad(temp, puertaAbierta, ahora)
        )
    }
}
