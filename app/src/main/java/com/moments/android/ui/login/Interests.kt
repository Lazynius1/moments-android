package com.moments.android.ui.login

import androidx.annotation.StringRes
import com.moments.android.R

/**
 * Intereses. El valor `raw` (español) es el que se guarda en Firestore — igual que iOS,
 * que usa el español como key/ID en la base de datos — y `labelRes` es su texto localizado.
 */
data class InterestOption(val raw: String, @StringRes val labelRes: Int)

val AllInterests: List<InterestOption> = listOf(
    InterestOption("Fotografía", R.string.interest_photography),
    InterestOption("Viajes", R.string.interest_travel),
    InterestOption("Música", R.string.interest_music),
    InterestOption("Cine", R.string.interest_movies),
    InterestOption("Arte", R.string.interest_art),
    InterestOption("Deportes", R.string.interest_sports),
    InterestOption("Libros", R.string.interest_books),
    InterestOption("Cocina", R.string.interest_cooking),
    InterestOption("Tecnología", R.string.interest_technology),
    InterestOption("Moda", R.string.interest_fashion),
    InterestOption("Gaming", R.string.interest_gaming),
    InterestOption("Fitness", R.string.interest_fitness),
    InterestOption("Naturaleza", R.string.interest_nature),
    InterestOption("Animales", R.string.interest_animals),
    InterestOption("Comida", R.string.interest_food),
    InterestOption("Ciencia", R.string.interest_science),
    InterestOption("Historia", R.string.interest_history),
    InterestOption("Política", R.string.interest_politics),
    InterestOption("Negocios", R.string.interest_business),
    InterestOption("Salud", R.string.interest_health),
    InterestOption("Estilo", R.string.interest_style),
    InterestOption("Baile", R.string.interest_dance),
    InterestOption("Escritura", R.string.interest_writing),
    InterestOption("DIY", R.string.interest_diy),
    InterestOption("Coches", R.string.interest_cars),
)

const val INTERESTS_MIN = 3
const val INTERESTS_MAX = 5
