package com.moments.android.extensions

import androidx.annotation.StringRes
import com.moments.android.R
import com.moments.android.models.InterestOption

object InterestEmojiHelper {

    data class SupportedInterest(
        val firestoreRaw: String,
        @StringRes val labelRes: Int,
        val emoji: String,
    )

    fun emoji(interest: String): String = emojiFor(interest)

    fun emojiFor(interest: String): String {
        InterestOption.fromRaw(interest)?.emoji?.let { return it }
        return when (interest.lowercase()) {
            "fotografía" -> "📸"
            "fotografia" -> "📸"
            "viajes" -> "✈️"
            "viajar" -> "✈️"
            "música" -> "🎵"
            "musica" -> "🎵"
            "cine" -> "🎬"
            "arte" -> "🎨"
            "deportes" -> "⚽"
            "libros" -> "📚"
            "lectura" -> "📚"
            "cocina" -> "👨‍🍳"
            "tecnología" -> "💻"
            "tecnologia" -> "💻"
            "moda" -> "👗"
            "gaming" -> "🎮"
            "videojuegos" -> "🎮"
            "fitness" -> "💪"
            "naturaleza" -> "🌿"
            "animales" -> "🐾"
            "comida" -> "🍽️"
            "ciencia" -> "🔬"
            "historia" -> "📜"
            "política" -> "🏛️"
            "politica" -> "🏛️"
            "negocios" -> "💼"
            "salud" -> "❤️‍🩹"
            "estilo" -> "✨"
            "baile" -> "💃"
            "escritura" -> "✍️"
            "diy" -> "🔧"
            "bricolaje" -> "🔧"
            "coches" -> "🚗"
            "teatro" -> "🎭"
            "meditación" -> "🕯️"
            "meditacion" -> "🕯️"
            "emprendimiento" -> "🚀"
            "yoga" -> "🧘"
            "café" -> "☕"
            "cafe" -> "☕"
            "astronomía" -> "⭐"
            "astronomia" -> "⭐"
            "podcasts" -> "🎧"
            "mascotas" -> "🐶"
            "diseño" -> "🖌️"
            "diseno" -> "🖌️"
            "programación" -> "👩‍💻"
            "programacion" -> "👩‍💻"
            "k-pop" -> "🎤"
            "kpop" -> "🎤"
            "anime" -> "🎌"
            "senderismo" -> "🥾"
            "ciclismo" -> "🚴"
            "correr" -> "🏃"
            "running" -> "🏃"
            "escalada" -> "🧗"
            "surf" -> "🏄"
            "fútbol" -> "⚽"
            "futbol" -> "⚽"
            "baloncesto" -> "🏀"
            "natación" -> "🏊"
            "natacion" -> "🏊"
            "skate" -> "🛹"
            "vinilos" -> "💿"
            "conciertos" -> "🎶"
            "hip-hop" -> "🎤"
            "hip hop" -> "🎤"
            "música electrónica" -> "🎛️"
            "musica electronica" -> "🎛️"
            "repostería" -> "🧁"
            "reposteria" -> "🧁"
            "vino" -> "🍷"
            "cerveza artesanal" -> "🍺"
            "cerveza" -> "🍺"
            "belleza" -> "💄"
            "sneakers" -> "👟"
            "tatuajes" -> "🖋️"
            "plantas" -> "🪴"
            "jardinería" -> "🌱"
            "jardineria" -> "🌱"
            "idiomas" -> "🌍"
            "voluntariado" -> "🤝"
            "sostenibilidad" -> "♻️"
            "cosplay" -> "🦸"
            "crímenes reales" -> "🕵️"
            "crimenes reales" -> "🕵️"
            "coleccionismo" -> "🃏"
            "manualidades" -> "✂️"
            "streaming" -> "📺"
            "ia" -> "🤖"
            "inteligencia artificial" -> "🤖"
            "finanzas personales" -> "💰"
            "finanzas" -> "💰"
            "filosofía" -> "💭"
            "filosofia" -> "💭"
            "ajedrez" -> "♟️"
            "juegos de mesa" -> "🎲"
            else -> "✨"
        }
    }

    val supportedInterests: List<SupportedInterest> = listOf(
        SupportedInterest("Fotografía", R.string.interest_photography, "📸"),
        SupportedInterest("Viajes", R.string.interest_travel, "✈️"),
        SupportedInterest("Música", R.string.interest_music, "🎵"),
        SupportedInterest("Cine", R.string.interest_movies, "🎬"),
        SupportedInterest("Arte", R.string.interest_art, "🎨"),
        SupportedInterest("Deportes", R.string.interest_sports, "⚽"),
        SupportedInterest("Libros", R.string.interest_books, "📚"),
        SupportedInterest("Cocina", R.string.interest_cooking, "👨‍🍳"),
        SupportedInterest("Tecnología", R.string.interest_technology, "💻"),
        SupportedInterest("Moda", R.string.interest_fashion, "👗"),
        SupportedInterest("Gaming", R.string.interest_gaming, "🎮"),
        SupportedInterest("Fitness", R.string.interest_fitness, "💪"),
        SupportedInterest("Naturaleza", R.string.interest_nature, "🌿"),
        SupportedInterest("Animales", R.string.interest_animals, "🐾"),
        SupportedInterest("Comida", R.string.interest_food, "🍽️"),
        SupportedInterest("Ciencia", R.string.interest_science, "🔬"),
        SupportedInterest("Historia", R.string.interest_history, "📜"),
        SupportedInterest("Política", R.string.interest_politics, "🏛️"),
        SupportedInterest("Negocios", R.string.interest_business, "💼"),
        SupportedInterest("Salud", R.string.interest_health, "❤️‍🩹"),
        SupportedInterest("Estilo", R.string.interest_style, "✨"),
        SupportedInterest("Baile", R.string.interest_dance, "💃"),
        SupportedInterest("Escritura", R.string.interest_writing, "✍️"),
        SupportedInterest("DIY", R.string.interest_diy, "🔧"),
        SupportedInterest("Coches", R.string.interest_cars, "🚗"),
        SupportedInterest("Teatro", R.string.interest_theater, "🎭"),
        SupportedInterest("Meditación", R.string.interest_meditation, "🕯️"),
        SupportedInterest("Emprendimiento", R.string.interest_entrepreneurship, "🚀"),
        SupportedInterest("Yoga", R.string.interest_yoga, "🧘"),
        SupportedInterest("Café", R.string.interest_coffee, "☕"),
        SupportedInterest("Astronomía", R.string.interest_astronomy, "⭐"),
        SupportedInterest("Podcasts", R.string.interest_podcasts, "🎧"),
        SupportedInterest("Mascotas", R.string.interest_pets, "🐶"),
        SupportedInterest("Diseño", R.string.interest_design, "🖌️"),
        SupportedInterest("Programación", R.string.interest_programming, "👩‍💻"),
        SupportedInterest("K-pop", R.string.interest_kpop, "🎤"),
        SupportedInterest("Anime", R.string.interest_anime, "🎌"),
        SupportedInterest("Senderismo", R.string.interest_hiking, "🥾"),
        SupportedInterest("Ciclismo", R.string.interest_cycling, "🚴"),
        SupportedInterest("Correr", R.string.interest_running, "🏃"),
        SupportedInterest("Escalada", R.string.interest_climbing, "🧗"),
        SupportedInterest("Surf", R.string.interest_surfing, "🏄"),
        SupportedInterest("Fútbol", R.string.interest_football, "⚽"),
        SupportedInterest("Baloncesto", R.string.interest_basketball, "🏀"),
        SupportedInterest("Natación", R.string.interest_swimming, "🏊"),
        SupportedInterest("Skate", R.string.interest_skateboarding, "🛹"),
        SupportedInterest("Vinilos", R.string.interest_vinyl, "💿"),
        SupportedInterest("Conciertos", R.string.interest_concerts, "🎶"),
        SupportedInterest("Hip-hop", R.string.interest_hiphop, "🎤"),
        SupportedInterest("Música electrónica", R.string.interest_electronic_music, "🎛️"),
        SupportedInterest("Repostería", R.string.interest_baking, "🧁"),
        SupportedInterest("Vino", R.string.interest_wine, "🍷"),
        SupportedInterest("Cerveza artesanal", R.string.interest_craft_beer, "🍺"),
        SupportedInterest("Belleza", R.string.interest_beauty, "💄"),
        SupportedInterest("Sneakers", R.string.interest_sneakers, "👟"),
        SupportedInterest("Tatuajes", R.string.interest_tattoos, "🖋️"),
        SupportedInterest("Plantas", R.string.interest_plants, "🪴"),
        SupportedInterest("Jardinería", R.string.interest_gardening, "🌱"),
        SupportedInterest("Idiomas", R.string.interest_languages, "🌍"),
        SupportedInterest("Voluntariado", R.string.interest_volunteering, "🤝"),
        SupportedInterest("Sostenibilidad", R.string.interest_sustainability, "♻️"),
        SupportedInterest("Cosplay", R.string.interest_cosplay, "🦸"),
        SupportedInterest("Crímenes reales", R.string.interest_true_crime, "🕵️"),
        SupportedInterest("Coleccionismo", R.string.interest_collecting, "🃏"),
        SupportedInterest("Manualidades", R.string.interest_crafts, "✂️"),
        SupportedInterest("Streaming", R.string.interest_streaming, "📺"),
        SupportedInterest("IA", R.string.interest_ai, "🤖"),
        SupportedInterest("Finanzas personales", R.string.interest_personal_finance, "💰"),
        SupportedInterest("Filosofía", R.string.interest_philosophy, "💭"),
        SupportedInterest("Ajedrez", R.string.interest_chess, "♟️"),
        SupportedInterest("Juegos de mesa", R.string.interest_board_games, "🎲"),
    )

    fun randomInterest(): SupportedInterest =
        supportedInterests.randomOrNull()
            ?: SupportedInterest("Interés", R.string.interest_writing, "✨")
}
