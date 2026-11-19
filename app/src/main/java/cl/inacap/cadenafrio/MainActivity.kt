package cl.inacap.cadenafrio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import cl.inacap.cadenafrio.data.FirebaseRepository
import cl.inacap.cadenafrio.navigation.NavigatorHostController

// Paleta azul clinico para el tema de la app (cadena de frio)
private val PaletaCadenaFrio = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FA),
    onPrimaryContainer = Color(0xFF0D3B75),
    secondary = Color(0xFF2B6CB0),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3ECF8),
    onSecondaryContainer = Color(0xFF1A365D),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF1A202C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A202C),
    surfaceVariant = Color(0xFFEDF2F7),
    onSurfaceVariant = Color(0xFF4A5568),
    outline = Color(0xFFCBD5E0),
    error = Color(0xFFA8123B),
    onError = Color.White
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = FirebaseRepository()
        setContent {
            MaterialTheme(colorScheme = PaletaCadenaFrio) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    NavigatorHostController(repo = repo)
                }
            }
        }
    }
}
