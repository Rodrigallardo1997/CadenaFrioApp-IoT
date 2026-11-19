package cl.inacap.cadenafrio.security

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import cl.inacap.cadenafrio.model.EstadoCadenaFrio
import cl.inacap.cadenafrio.model.RolUsuario
import cl.inacap.cadenafrio.model.Telemetria
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// Funciones de seguridad y validaciones de la app (ISO 27400)
object SeguridadIso27400 {

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private const val HMAC_ALGORITMO = "HmacSHA256"
    private const val PBKDF2_ALGORITMO = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERACIONES = 10000
    private const val PBKDF2_KEY_BITS = 256
    private const val CLAVE_COMPARTIDA_NODO = "INACAP_TI3042_CADENA_FRIO_KEY_2026"

    // Si se equivoca 3 veces seguidas bloqueamos por 30 segundos
    const val MAX_INTENTOS_LOGIN = 3
    private const val TIEMPO_BLOQUEO_MS = 30000L
    private var intentosFallidos = 0
    private var bloqueadoHastaMs = 0L

    // Pausa de 2 segundos entre comandos para no saturar el Arduino
    private const val INTERVALO_MIN_COMANDO_MS = 2000L
    private var ultimoComandoEnviadoMs = 0L

    private const val VENTANA_ANTI_REPLAY_MS = 15 * 60 * 1000L

    // Convierte la contrasena en hash usando PBKDF2 y el salt del usuario
    fun generarHashPassword(password: String, salt: String): String {
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt.toByteArray(Charsets.UTF_8),
            PBKDF2_ITERACIONES,
            PBKDF2_KEY_BITS
        )
        val skf = SecretKeyFactory.getInstance(PBKDF2_ALGORITMO)
        val bytes = skf.generateSecret(spec).encoded
        spec.clearPassword()

        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    // Compara el hash calculado con el hash guardado
    fun verificarPasswordHash(passwordIngresada: String, salt: String, hashEsperado: String): Boolean {
        val hashCalculado = generarHashPassword(passwordIngresada, salt)
        return MessageDigest.isEqual(
            hashCalculado.toByteArray(Charsets.UTF_8),
            hashEsperado.lowercase().toByteArray(Charsets.UTF_8)
        )
    }

    fun verificarRateLimitComando(): String? {
        val ahora = System.currentTimeMillis()
        val transcurrido = ahora - ultimoComandoEnviadoMs
        if (transcurrido in 0 until INTERVALO_MIN_COMANDO_MS) {
            return "Espera 2 segundos antes de enviar otro comando."
        }
        ultimoComandoEnviadoMs = ahora
        return null
    }

    fun segundosRestantesBloqueo(): Long {
        val ahora = System.currentTimeMillis()
        return if (ahora < bloqueadoHastaMs) {
            val seg = (bloqueadoHastaMs - ahora + 999L) / 1000L
            if (seg < 1L) 1L else seg
        } else {
            0L
        }
    }

    fun verificarBloqueoFuerzaBruta(): String? {
        val seg = segundosRestantesBloqueo()
        if (seg > 0L) {
            return "Acceso bloqueado por intentos fallidos. Espera ${seg}s."
        }
        return null
    }

    fun registrarFalloCredenciales(motivoBase: String): String {
        intentosFallidos++
        return if (intentosFallidos >= MAX_INTENTOS_LOGIN) {
            bloqueadoHastaMs = System.currentTimeMillis() + TIEMPO_BLOQUEO_MS
            intentosFallidos = 0
            "Se alcanzaron $MAX_INTENTOS_LOGIN intentos fallidos. Acceso bloqueado por 30 segundos."
        } else {
            val restantes = MAX_INTENTOS_LOGIN - intentosFallidos
            "$motivoBase (Intento $intentosFallidos/$MAX_INTENTOS_LOGIN - Quedan $restantes intento(s))."
        }
    }

    fun reiniciarIntentosLogin() {
        intentosFallidos = 0
        bloqueadoHastaMs = 0L
    }

    fun validarFormatoCredenciales(email: String, password: String): String? {
        val bloqueo = verificarBloqueoFuerzaBruta()
        if (bloqueo != null) return bloqueo

        val correoLimpio = email.trim()
        if (!EMAIL_REGEX.matches(correoLimpio)) {
            return "Ingresa un correo valido (ej. usuario@inacapmail.cl)."
        }
        if (password.isEmpty()) {
            return "Debes ingresar tu contrasena."
        }
        return null
    }

    fun validarNuevaPassword(nuevaPassword: String, confirmarPassword: String): String? {
        if (nuevaPassword.length < 6) {
            return "La nueva contrasena debe tener al menos 6 caracteres."
        }
        if (nuevaPassword != confirmarPassword) {
            return "Las contrasenas nuevas no coinciden."
        }
        return null
    }

    // Genera una firma HMAC-SHA256 para comprobar que el dato no fue modificado
    fun generarFirmaIntegridad(temp: Double, puertaAbierta: Boolean, timestamp: Long): String {
        val carga = "$temp|$puertaAbierta|$timestamp"
        val mac = Mac.getInstance(HMAC_ALGORITMO)
        val secretKey = SecretKeySpec(CLAVE_COMPARTIDA_NODO.toByteArray(Charsets.UTF_8), HMAC_ALGORITMO)
        mac.init(secretKey)
        val bytes = mac.doFinal(carga.toByteArray(Charsets.UTF_8))

        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString().substring(0, 16)
    }

    fun verificarFirmaIntegridad(telemetria: Telemetria): Boolean {
        if (telemetria.firmaIntegridad.isBlank()) return false
        val esperada = generarFirmaIntegridad(
            temp = telemetria.temperatura,
            puertaAbierta = telemetria.puertaAbierta,
            timestamp = telemetria.timestamp
        )
        return telemetria.firmaIntegridad.equals(esperada, ignoreCase = true)
    }

    fun esTramaReciente(timestamp: Long): Boolean {
        val diferencia = kotlin.math.abs(System.currentTimeMillis() - timestamp)
        return diferencia <= VENTANA_ANTI_REPLAY_MS
    }

    // El sensor TMP36 mide fisicamente entre -40 y 125 grados
    fun esTemperaturaPlausible(temp: Double): Boolean {
        return !temp.isNaN() &&
            !temp.isInfinite() &&
            temp >= EstadoCadenaFrio.TEMP_MIN_FISICA &&
            temp <= EstadoCadenaFrio.TEMP_MAX_FISICA
    }

    fun esComandoValido(comando: String): Boolean {
        return comando == "CMD:SILENCIO" || comando == "CMD:AUTO" || comando == "CMD:TEST"
    }

    // Revisa si el rol del usuario tiene permiso para mandar ese comando
    fun tienePermisoComando(rol: RolUsuario, comando: String): Boolean {
        if (!esComandoValido(comando)) return false
        return when (comando) {
            "CMD:SILENCIO" -> rol.puedeSilenciarAlarma
            "CMD:AUTO", "CMD:TEST" -> rol.puedeEjecutarTestYAuto
            else -> false
        }
    }

    fun tienePermisoPublicarTelemetria(rol: RolUsuario): Boolean {
        return rol.puedeUsarEmisor || rol.puedeUsarBluetooth
    }

    fun hayConexionRed(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
}
