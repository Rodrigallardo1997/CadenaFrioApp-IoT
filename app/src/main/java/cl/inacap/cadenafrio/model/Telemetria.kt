package cl.inacap.cadenafrio.model

// Roles de usuario para controlar que acciones puede hacer cada perfil
enum class RolUsuario(
    val etiqueta: String,
    val descripcionPermisos: String,
    val emailDemo: String,
    val puedeSilenciarAlarma: Boolean,
    val puedeEjecutarTestYAuto: Boolean,
    val puedeUsarBluetooth: Boolean,
    val puedeUsarEmisor: Boolean
) {
    SUPERVISOR(
        etiqueta = "SUPERVISOR",
        descripcionPermisos = "Acceso completo a monitor, historial, comandos, bluetooth y emisor",
        emailDemo = "supervisor@inacapmail.cl",
        puedeSilenciarAlarma = true,
        puedeEjecutarTestYAuto = true,
        puedeUsarBluetooth = true,
        puedeUsarEmisor = true
    ),
    OPERADOR(
        etiqueta = "OPERADOR",
        descripcionPermisos = "Modo lectura: solo puede ver el monitor y el historial",
        emailDemo = "operador@inacapmail.cl",
        puedeSilenciarAlarma = false,
        puedeEjecutarTestYAuto = false,
        puedeUsarBluetooth = false,
        puedeUsarEmisor = false
    ),
    TECNICO_IOT(
        etiqueta = "TECNICO_IOT",
        descripcionPermisos = "Soporte tecnico: monitor, bluetooth, emisor y pruebas (sin silenciar alarma)",
        emailDemo = "tecnico@inacapmail.cl",
        puedeSilenciarAlarma = false,
        puedeEjecutarTestYAuto = true,
        puedeUsarBluetooth = true,
        puedeUsarEmisor = true
    );

    companion object {
        fun inferirPorEmail(email: String): RolUsuario {
            val correo = email.trim().lowercase()
            return if (correo.startsWith("supervisor") || correo.startsWith("admin")) {
                SUPERVISOR
            } else if (correo.startsWith("tecnico") || correo.startsWith("iot")) {
                TECNICO_IOT
            } else {
                OPERADOR
            }
        }
    }
}

// Datos actuales que llegan del sensor o desde Firebase
data class Telemetria(
    val temperatura: Double = 5.0,
    val puertaAbierta: Boolean = false,
    val estado: String = "NORMAL",
    val comandoActivo: String = "AUTO",
    val origen: String = "Nodo-WiFi",
    val timestamp: Long = System.currentTimeMillis(),
    val firmaIntegridad: String = "",
    val ultimoOperadorComando: String = "Sistema"
)

// Modelo para guardar cada lectura en la lista del historial
data class EventoHistorial(
    val id: String = "",
    val temperatura: Double = 0.0,
    val puertaAbierta: Boolean = false,
    val estado: String = "NORMAL",
    val origen: String = "",
    val timestamp: Long = 0L
)

// Rango de cadena de frio: entre 2.0 y 8.0 grados
sealed class EstadoCadenaFrio(val etiqueta: String, val esCritico: Boolean) {
    data object Normal : EstadoCadenaFrio("Estado: NORMAL", false)
    data object PuertaAbierta : EstadoCadenaFrio("Puerta: ABIERTA", false)
    data object Congelamiento : EstadoCadenaFrio("ALERTA: FRIO (< 2.0 °C)", true)
    data object Sobretemperatura : EstadoCadenaFrio("!ALERTA CRITICA! (>= 8.0 °C)", true)
    data object FallaSensor : EstadoCadenaFrio("FALLA SENSOR", true)

    companion object {
        const val TEMP_MIN_SEGURA = 2.0
        const val TEMP_MAX_SEGURA = 8.0
        const val TEMP_MIN_FISICA = -40.0
        const val TEMP_MAX_FISICA = 125.0

        fun evaluar(temp: Double, puertaAbierta: Boolean): EstadoCadenaFrio {
            return when {
                temp < TEMP_MIN_FISICA || temp > TEMP_MAX_FISICA -> FallaSensor
                temp >= TEMP_MAX_SEGURA -> Sobretemperatura
                temp < TEMP_MIN_SEGURA -> Congelamiento
                puertaAbierta -> PuertaAbierta
                else -> Normal
            }
        }
    }
}
