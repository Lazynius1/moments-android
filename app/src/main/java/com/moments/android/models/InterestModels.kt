package com.moments.android.models

import android.content.res.Resources
import androidx.annotation.StringRes
import com.moments.android.R

/**
 * Catálogo unificado — port de `InterestCatalog` (iOS).
 * Firestore persiste [raw] en español; [labelRes] localiza la UI.
 */
enum class InterestOption(
    val raw: String,
    @StringRes val labelRes: Int,
    val emoji: String,
) {
    PHOTOGRAPHY("Fotografía", R.string.interest_photography, "📸"),
    TRAVEL("Viajes", R.string.interest_travel, "✈️"),
    MUSIC("Música", R.string.interest_music, "🎵"),
    MOVIES("Cine", R.string.interest_movies, "🎬"),
    ART("Arte", R.string.interest_art, "🎨"),
    SPORTS("Deportes", R.string.interest_sports, "⚽"),
    BOOKS("Libros", R.string.interest_books, "📚"),
    COOKING("Cocina", R.string.interest_cooking, "👨‍🍳"),
    TECHNOLOGY("Tecnología", R.string.interest_technology, "💻"),
    FASHION("Moda", R.string.interest_fashion, "👗"),
    GAMING("Gaming", R.string.interest_gaming, "🎮"),
    FITNESS("Fitness", R.string.interest_fitness, "💪"),
    NATURE("Naturaleza", R.string.interest_nature, "🌿"),
    ANIMALS("Animales", R.string.interest_animals, "🐾"),
    FOOD("Comida", R.string.interest_food, "🍽️"),
    SCIENCE("Ciencia", R.string.interest_science, "🔬"),
    HISTORY("Historia", R.string.interest_history, "📜"),
    POLITICS("Política", R.string.interest_politics, "🏛️"),
    BUSINESS("Negocios", R.string.interest_business, "💼"),
    HEALTH("Salud", R.string.interest_health, "❤️‍🩹"),
    STYLE("Estilo", R.string.interest_style, "✨"),
    DANCE("Baile", R.string.interest_dance, "💃"),
    WRITING("Escritura", R.string.interest_writing, "✍️"),
    DIY("DIY", R.string.interest_diy, "🔧"),
    CARS("Coches", R.string.interest_cars, "🚗"),
    THEATER("Teatro", R.string.interest_theater, "🎭"),
    MEDITATION("Meditación", R.string.interest_meditation, "🕯️"),
    ENTREPRENEURSHIP("Emprendimiento", R.string.interest_entrepreneurship, "🚀"),
    YOGA("Yoga", R.string.interest_yoga, "🧘"),
    COFFEE("Café", R.string.interest_coffee, "☕"),
    ASTRONOMY("Astronomía", R.string.interest_astronomy, "⭐"),
    PODCASTS("Podcasts", R.string.interest_podcasts, "🎧"),
    PETS("Mascotas", R.string.interest_pets, "🐶"),
    DESIGN("Diseño", R.string.interest_design, "🖌️"),
    PROGRAMMING("Programación", R.string.interest_programming, "👩‍💻"),
    KPOP("K-pop", R.string.interest_kpop, "🎤"),
    ANIME("Anime", R.string.interest_anime, "🎌"),
    HIKING("Senderismo", R.string.interest_hiking, "🥾"),
    CYCLING("Ciclismo", R.string.interest_cycling, "🚴"),
    RUNNING("Correr", R.string.interest_running, "🏃"),
    CLIMBING("Escalada", R.string.interest_climbing, "🧗"),
    SURFING("Surf", R.string.interest_surfing, "🏄"),
    FOOTBALL("Fútbol", R.string.interest_football, "⚽"),
    BASKETBALL("Baloncesto", R.string.interest_basketball, "🏀"),
    SWIMMING("Natación", R.string.interest_swimming, "🏊"),
    SKATEBOARDING("Skate", R.string.interest_skateboarding, "🛹"),
    VINYL("Vinilos", R.string.interest_vinyl, "💿"),
    CONCERTS("Conciertos", R.string.interest_concerts, "🎶"),
    HIPHOP("Hip-hop", R.string.interest_hiphop, "🎤"),
    ELECTRONIC_MUSIC("Música electrónica", R.string.interest_electronic_music, "🎛️"),
    BAKING("Repostería", R.string.interest_baking, "🧁"),
    WINE("Vino", R.string.interest_wine, "🍷"),
    CRAFT_BEER("Cerveza artesanal", R.string.interest_craft_beer, "🍺"),
    BEAUTY("Belleza", R.string.interest_beauty, "💄"),
    SNEAKERS("Sneakers", R.string.interest_sneakers, "👟"),
    TATTOOS("Tatuajes", R.string.interest_tattoos, "🖋️"),
    PLANTS("Plantas", R.string.interest_plants, "🪴"),
    GARDENING("Jardinería", R.string.interest_gardening, "🌱"),
    LANGUAGES("Idiomas", R.string.interest_languages, "🌍"),
    VOLUNTEERING("Voluntariado", R.string.interest_volunteering, "🤝"),
    SUSTAINABILITY("Sostenibilidad", R.string.interest_sustainability, "♻️"),
    COSPLAY("Cosplay", R.string.interest_cosplay, "🦸"),
    TRUE_CRIME("Crímenes reales", R.string.interest_true_crime, "🕵️"),
    COLLECTING("Coleccionismo", R.string.interest_collecting, "🃏"),
    CRAFTS("Manualidades", R.string.interest_crafts, "✂️"),
    STREAMING("Streaming", R.string.interest_streaming, "📺"),
    AI("IA", R.string.interest_ai, "🤖"),
    PERSONAL_FINANCE("Finanzas personales", R.string.interest_personal_finance, "💰"),
    PHILOSOPHY("Filosofía", R.string.interest_philosophy, "💭"),
    CHESS("Ajedrez", R.string.interest_chess, "♟️"),
    BOARD_GAMES("Juegos de mesa", R.string.interest_board_games, "🎲"),
    UNKNOWN("", 0, "✨");

    companion object {
        fun fromRaw(key: String): InterestOption? {
            val trimmed = key.trim()
            entries.firstOrNull { it.raw == trimmed }?.let { return it }
            return entries.firstOrNull {
                it != UNKNOWN && (
                    it.raw.equals(trimmed, ignoreCase = true) ||
                        InterestCatalogAliases.resolve(trimmed) == it.raw
                )
            }
        }

        fun localize(key: String, resources: Resources): String {
            val option = fromRaw(key) ?: return key
            if (option.labelRes == 0) return ""
            return resources.getString(option.labelRes)
        }

        val allKnown: List<InterestOption> = entries.filter { it != UNKNOWN }
        val firestoreKeys: List<String> = allKnown.map { it.raw }
    }
}

/** Aliases legacy → clave Firestore canónica */
private object InterestCatalogAliases {
    private val map: Map<String, String> = InterestOption.allKnown.flatMap { opt ->
        listOf(opt.raw to opt.raw)
    }.toMap() + mapOf(
        "fotografia" to "Fotografía",
        "Viajar" to "Viajes",
        "viajar" to "Viajes",
        "musica" to "Música",
        "cine" to "Cine",
        "arte" to "Arte",
        "deportes" to "Deportes",
        "libros" to "Libros",
        "Lectura" to "Libros",
        "lectura" to "Libros",
        "cocina" to "Cocina",
        "tecnologia" to "Tecnología",
        "moda" to "Moda",
        "gaming" to "Gaming",
        "Videojuegos" to "Gaming",
        "fitness" to "Fitness",
        "naturaleza" to "Naturaleza",
        "animales" to "Animales",
        "comida" to "Comida",
        "ciencia" to "Ciencia",
        "historia" to "Historia",
        "politica" to "Política",
        "negocios" to "Negocios",
        "salud" to "Salud",
        "estilo" to "Estilo",
        "baile" to "Baile",
        "escritura" to "Escritura",
        "diy" to "DIY",
        "Bricolaje" to "DIY",
        "coches" to "Coches",
        "teatro" to "Teatro",
        "meditacion" to "Meditación",
        "emprendimiento" to "Emprendimiento",
        "yoga" to "Yoga",
        "cafe" to "Café",
        "café" to "Café",
        "astronomia" to "Astronomía",
        "podcasts" to "Podcasts",
        "mascotas" to "Mascotas",
        "diseno" to "Diseño",
        "diseño" to "Diseño",
        "programacion" to "Programación",
        "programación" to "Programación",
        "Kpop" to "K-pop",
        "kpop" to "K-pop",
        "k-pop" to "K-pop",
        "anime" to "Anime",
        "senderismo" to "Senderismo",
        "ciclismo" to "Ciclismo",
        "correr" to "Correr",
        "Running" to "Correr",
        "escalada" to "Escalada",
        "surf" to "Surf",
        "futbol" to "Fútbol",
        "fútbol" to "Fútbol",
        "baloncesto" to "Baloncesto",
        "natacion" to "Natación",
        "natación" to "Natación",
        "skate" to "Skate",
        "vinilos" to "Vinilos",
        "conciertos" to "Conciertos",
        "hip-hop" to "Hip-hop",
        "hip hop" to "Hip-hop",
        "musica electronica" to "Música electrónica",
        "reposteria" to "Repostería",
        "repostería" to "Repostería",
        "vino" to "Vino",
        "cerveza" to "Cerveza artesanal",
        "belleza" to "Belleza",
        "sneakers" to "Sneakers",
        "tatuajes" to "Tatuajes",
        "plantas" to "Plantas",
        "jardineria" to "Jardinería",
        "jardinería" to "Jardinería",
        "idiomas" to "Idiomas",
        "voluntariado" to "Voluntariado",
        "sostenibilidad" to "Sostenibilidad",
        "cosplay" to "Cosplay",
        "crimenes reales" to "Crímenes reales",
        "coleccionismo" to "Coleccionismo",
        "manualidades" to "Manualidades",
        "streaming" to "Streaming",
        "ia" to "IA",
        "Inteligencia artificial" to "IA",
        "finanzas" to "Finanzas personales",
        "filosofia" to "Filosofía",
        "filosofía" to "Filosofía",
        "ajedrez" to "Ajedrez",
        "juegos de mesa" to "Juegos de mesa",
    )

    fun resolve(raw: String): String? = map[raw] ?: map.entries.firstOrNull {
        it.key.equals(raw, ignoreCase = true)
    }?.value
}
