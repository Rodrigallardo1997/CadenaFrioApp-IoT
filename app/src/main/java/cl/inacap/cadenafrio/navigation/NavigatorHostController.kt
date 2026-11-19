package cl.inacap.cadenafrio.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cl.inacap.cadenafrio.data.FirebaseRepository
import cl.inacap.cadenafrio.screens.BluetoothScreen
import cl.inacap.cadenafrio.screens.DashboardScreen
import cl.inacap.cadenafrio.screens.EmisorSimuladorScreen
import cl.inacap.cadenafrio.screens.HistorialScreen
import cl.inacap.cadenafrio.screens.LoginScreen

object Rutas {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val HISTORIAL = "historial"
    const val EMISOR = "emisor"
    const val BLUETOOTH = "bluetooth"
}

@Composable
fun NavigatorHostController(repo: FirebaseRepository) {
    val navController = rememberNavController()
    val rutaInicial = if (repo.estaAutenticado()) Rutas.DASHBOARD else Rutas.LOGIN

    NavHost(navController = navController, startDestination = rutaInicial) {
        composable(Rutas.LOGIN) {
            LoginScreen(
                repo = repo,
                onLoginExitoso = {
                    navController.navigate(Rutas.DASHBOARD) {
                        popUpTo(Rutas.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Rutas.DASHBOARD) {
            DashboardScreen(
                repo = repo,
                onIrHistorial = { navController.navigate(Rutas.HISTORIAL) },
                onIrEmisor = { navController.navigate(Rutas.EMISOR) },
                onIrBluetooth = { navController.navigate(Rutas.BLUETOOTH) },
                onCerrarSesion = {
                    repo.cerrarSesion()
                    navController.navigate(Rutas.LOGIN) {
                        popUpTo(Rutas.DASHBOARD) { inclusive = true }
                    }
                }
            )
        }
        composable(Rutas.BLUETOOTH) {
            BluetoothScreen(
                repo = repo,
                onVolver = { navController.popBackStack() }
            )
        }
        composable(Rutas.HISTORIAL) {
            HistorialScreen(
                repo = repo,
                onVolver = { navController.popBackStack() }
            )
        }
        composable(Rutas.EMISOR) {
            EmisorSimuladorScreen(
                repo = repo,
                onVolver = { navController.popBackStack() }
            )
        }
    }
}
