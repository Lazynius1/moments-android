package com.moments.android.debugscreenshots

import com.moments.android.models.AppUser
import com.moments.android.models.Echo
import com.moments.android.models.EchoMomentRef
import com.moments.android.models.EchoParticipant
import com.moments.android.models.EchoParticipantStatus
import com.moments.android.models.EchoStatus
import com.moments.android.models.HiddenLayerImageFrameStyle
import com.moments.android.models.HiddenLayerPresentationStyle
import com.moments.android.models.HiddenLayerTextStyle
import com.moments.android.models.MediaItem
import com.moments.android.models.Moment
import com.moments.android.models.MomentHiddenLayer
import com.moments.android.models.OnlineStatus
import com.moments.android.models.Point
import com.moments.android.models.StickerData
import com.moments.android.models.Story
import com.moments.android.services.content.FeedMoment
import com.moments.android.views.creator.audienceselector.CustomAudienceList
import com.moments.android.views.feed.core.sections.FeedStoryUserState
import com.moments.android.views.feed.maps.MapDiscoverPayload
import com.moments.android.views.feed.maps.MapStoryPreview
import com.moments.android.views.feed.maps.toFeedMomentForMap
import com.moments.android.views.messaging.core.Conversation
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageStatus
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.views.nova.novacore.NovaChatMessage
import com.moments.android.views.shared.AILabelSource
import java.util.Date

/** Datos de demostración del modo capturas (mismo contenido que en iOS). */
object ScreenshotFixtures {
    const val BARCELONA_LAT = 41.3874
    const val BARCELONA_LON = 2.1686

    private fun ago(minutes: Double): Date = Date(System.currentTimeMillis() - (minutes * 60_000).toLong())

    // MARK: Usuarios

    private fun makeUser(
        id: String,
        username: String,
        avatar: String,
        verified: Boolean,
        bestFriends: List<String> = emptyList(),
        followers: Int = 0,
        following: Int = 0,
        moments: Int = 0,
    ): AppUser = AppUser(
        id = id,
        username = username,
        email = "",
        profileImagePath = ScreenshotImages.url(avatar),
        bestFriends = bestFriends,
        followersCount = followers,
        followingCount = following,
        momentsCount = moments,
        isVerified = verified,
        onlineStatus = OnlineStatus.ONLINE,
        isOnline = true,
        lastSeen = Date(),
    )

    val lucia = makeUser("ss_lucia", "lucia.mar", "ss_avatar_lucia", true, followers = 2840, following = 412, moments = 186)
    val theo = makeUser("ss_theo", "theo.wav", "ss_avatar_theo", false, followers = 932, following = 380, moments = 74)
    val aiko = makeUser("ss_aiko", "aiko.film", "ss_avatar_aiko", true, followers = 5120, following = 290, moments = 241)
    val mateo = makeUser("ss_mateo", "mateo.rv", "ss_avatar_mateo", false, followers = 640, following = 512, moments = 58)
    val nora = makeUser("ss_nora", "nora.eats", "ss_avatar_nora", false, followers = 1210, following = 455, moments = 133)
    val sam = makeUser("ss_sam", "sam.trails", "ss_avatar_sam", false, followers = 488, following = 301, moments = 92)
    val ines = makeUser("ss_ines", "ines.draws", "ss_avatar_ines", false, followers = 1764, following = 388, moments = 117)
    val kai = makeUser("ss_kai", "kai.waves", "ss_avatar_kai", true, followers = 3390, following = 270, moments = 204)

    val friends: List<AppUser> = listOf(lucia, theo, aiko, mateo, nora, sam, ines, kai)

    val me: AppUser = makeUser(
        id = ScreenshotMode.ME_ID,
        username = "alex",
        avatar = "ss_me",
        verified = false,
        bestFriends = listOf(lucia.id, theo.id, aiko.id),
        followers = 1280,
        following = 344,
        moments = 96,
    )

    val allUsers: List<AppUser> get() = listOf(me) + friends

    fun user(id: String): AppUser? = allUsers.firstOrNull { it.id == id }

    /** Usuario de demo; el uid real de la sesión (o vacío) se presenta como el propio. */
    fun resolveUser(id: String): AppUser? {
        user(id)?.let { return it }
        val auth = ScreenshotMode.authUid
        return if (id.isEmpty() || (auth != null && id == auth)) me else null
    }

    fun avatarURL(userId: String): String? = resolveUser(userId)?.profileImagePath

    /** Ids con los que las vistas identifican al usuario propio (demo, vacío y uid real). */
    private val ownIds: List<String>
        get() = listOfNotNull(me.id, "", ScreenshotMode.authUid).distinct()

    // MARK: Momentos

    private fun reactions(count: Int): Map<String, List<String>> {
        val ids = allUsers.map { it.id } + (0 until maxOf(0, count - allUsers.size)).map { "ss_fan_$it" }
        return mapOf(
            "love" to ids.take(count),
            "fire" to ids.take(maxOf(1, count / 3)),
            "vibe" to ids.take(maxOf(1, count / 5)),
        )
    }

    private fun coordinate(lat: Double, lon: Double) = Moment.LocationCoordinate(lat, lon)

    private fun makeMoment(
        id: String,
        author: AppUser,
        asset: String,
        caption: String,
        minutesAgo: Double,
        location: String?,
        coordinate: Moment.LocationCoordinate?,
        likes: Int,
        comments: Int,
        aspectRatio: String = "4:5",
        aiGenerated: Boolean = false,
        hiddenLayerCount: Int = 0,
    ): Moment {
        val url = ScreenshotImages.url(asset)
        return Moment(
            id = id,
            authorId = author.id,
            username = author.username,
            content = ScreenshotCopy.text("captions.$caption"),
            imagePath = url,
            timestamp = ago(minutesAgo),
            reactions = reactions(likes),
            commentCount = comments,
            profileImagePath = author.profileImagePath,
            location = location,
            locationCoordinate = coordinate,
            audience = "everyone",
            mediaItems = listOf(
                MediaItem(id = "${id}_media", type = MediaItem.MediaType.IMAGE, url = url, aspectRatio = aspectRatio),
            ),
            aspectRatio = aspectRatio,
            hasHiddenLayers = hiddenLayerCount > 0,
            hiddenLayerCount = hiddenLayerCount,
            disableComments = false,
            hideLikeCounts = false,
            allowSharing = true,
            aiGenerated = aiGenerated,
            aiLabelSource = if (aiGenerated) AILabelSource.CREATOR else null,
        )
    }

    val beachSunset by lazy { makeMoment("ss_m_beach_sunset", lucia, "ss_moment_beach_sunset", "beach_sunset", 18.0, "Barceloneta", coordinate(41.3784, 2.1925), 214, 4) }
    val picnic by lazy { makeMoment("ss_m_picnic", lucia, "ss_moment_picnic", "picnic", 52.0, "Montjuïc", coordinate(41.3636, 2.1580), 168, 4, hiddenLayerCount = 3) }
    val mountainLake by lazy { makeMoment("ss_m_mountain_lake", aiko, "ss_moment_mountain_lake", "mountain_lake", 95.0, null, null, 342, 21, aiGenerated = true) }
    val rooftopDinner by lazy { makeMoment("ss_m_rooftop_dinner", theo, "ss_moment_rooftop_dinner", "rooftop_dinner", 140.0, "Eixample", coordinate(41.3917, 2.1649), 97, 8) }
    val latte by lazy { makeMoment("ss_m_latte", nora, "ss_moment_latte", "latte", 210.0, "Gràcia", coordinate(41.4036, 2.1564), 76, 5) }
    val concert by lazy { makeMoment("ss_m_concert", kai, "ss_moment_concert", "concert", 300.0, "Parc del Fòrum", coordinate(41.4105, 2.2266), 451, 33) }
    val nightMarket by lazy { makeMoment("ss_m_night_market", mateo, "ss_moment_night_market", "night_market", 380.0, "El Born", coordinate(41.3851, 2.1834), 88, 6) }
    val dogPark by lazy { makeMoment("ss_m_dog_park", sam, "ss_moment_dog_park", "dog_park", 460.0, "Parc de la Ciutadella", coordinate(41.3881, 2.1873), 129, 9) }
    val roadTrip by lazy { makeMoment("ss_m_road_trip", ines, "ss_moment_road_trip", "road_trip", 600.0, "Costa Brava", coordinate(41.8930, 3.1650), 157, 12) }
    val ski by lazy { makeMoment("ss_m_ski", mateo, "ss_moment_ski", "ski", 1_300.0, "La Molina", coordinate(42.3360, 1.9400), 203, 14) }
    val birthday by lazy { makeMoment("ss_m_birthday", nora, "ss_moment_birthday", "birthday", 900.0, "Poblenou", coordinate(41.4030, 2.2010), 264, 41) }
    val lisbonTram by lazy { makeMoment("ss_m_lisbon_tram", aiko, "ss_moment_lisbon_tram", "lisbon_tram", 2_100.0, "Lisboa", coordinate(38.7139, -9.1394), 188, 10) }

    /** Feed: en `aiLabel` el momento con etiqueta IA va primero. */
    val feedMoments: List<Moment>
        get() {
            val base = listOf(beachSunset, picnic, mountainLake, rooftopDinner, latte, concert, nightMarket, dogPark, roadTrip)
            if (ScreenshotMode.screen != ScreenshotMode.Screen.AI_LABEL) return base
            return listOf(mountainLake) + base.filter { it.id != mountainLake.id }
        }

    /** Mismo feed en el modelo que pinta `FeedView`. */
    val feedMomentsForFeed: List<FeedMoment>
        get() = feedMoments.map { it.toFeedMomentForMap() }

    val hiddenLayersMoment: Moment get() = picnic

    // MARK: Capas ocultas

    fun hiddenLayers(momentId: String): List<MomentHiddenLayer> {
        if (momentId != picnic.id) return emptyList()
        val created = ago(52.0)
        return listOf(
            MomentHiddenLayer(
                id = "ss_hl_text",
                type = MomentHiddenLayer.LayerType.TEXT,
                anchorX = 0.30,
                anchorY = 0.28,
                width = 0.46,
                height = 0.16,
                shape = MomentHiddenLayer.LayerShape.ROUNDED_RECT,
                zIndex = 0,
                text = ScreenshotCopy.text("hiddenLayers.note"),
                textStyle = HiddenLayerTextStyle.HANDWRITTEN,
                presentationStyle = HiddenLayerPresentationStyle.PAPER_NOTE,
                hintStyle = MomentHiddenLayer.HintStyle.AURORA,
                createdAt = created,
            ),
            MomentHiddenLayer(
                id = "ss_hl_image",
                type = MomentHiddenLayer.LayerType.IMAGE,
                anchorX = 0.70,
                anchorY = 0.56,
                width = 0.38,
                height = 0.30,
                shape = MomentHiddenLayer.LayerShape.ROUNDED_RECT,
                zIndex = 1,
                mediaURL = ScreenshotImages.url("ss_hidden_polaroid"),
                thumbnailURL = ScreenshotImages.url("ss_hidden_polaroid"),
                caption = ScreenshotCopy.text("hiddenLayers.imageCaption"),
                imageFrameStyle = HiddenLayerImageFrameStyle.CLASSIC,
                presentationStyle = HiddenLayerPresentationStyle.GLASS_CARD,
                hintStyle = MomentHiddenLayer.HintStyle.ACTUAL,
                createdAt = created,
            ),
            MomentHiddenLayer(
                id = "ss_hl_audio",
                type = MomentHiddenLayer.LayerType.AUDIO,
                anchorX = 0.32,
                anchorY = 0.80,
                width = 0.20,
                height = 0.20,
                shape = MomentHiddenLayer.LayerShape.CIRCLE,
                zIndex = 2,
                mediaURL = "https://${ScreenshotImages.HOST}/ss_hidden_voice.m4a",
                duration = 12.0,
                caption = ScreenshotCopy.text("hiddenLayers.audioCaption"),
                presentationStyle = HiddenLayerPresentationStyle.CAPTION_PILL,
                hintStyle = MomentHiddenLayer.HintStyle.EMBER,
                createdAt = created,
            ),
        )
    }

    // MARK: Comentarios (se siembran en la caché local de Firestore)

    data class SeedComment(
        val id: String,
        val author: AppUser,
        val text: String,
        val timestamp: Date,
    )

    val commentThreads: List<Pair<Moment, List<SeedComment>>>
        get() {
            val texts = ScreenshotCopy.list("comments")
            val authors = listOf(theo, aiko, kai, nora)
            fun thread(moment: Moment): List<SeedComment> = texts.mapIndexed { index, text ->
                SeedComment(
                    id = "ss_c_${moment.id.orEmpty()}_$index",
                    author = authors[index % authors.size],
                    text = text,
                    timestamp = ago((40 - index * 8).toDouble()),
                )
            }
            return listOf(beachSunset, picnic, mountainLake).map { it to thread(it) }
        }

    // MARK: Anillo de historias

    private fun ringUser(
        user: AppUser,
        hasStory: Boolean,
        hasUnseenStory: Boolean,
        viewed: List<Boolean>,
        audiences: List<String>,
    ) = FeedStoryUserState(
        userId = user.id,
        username = user.username,
        profileImageUrl = user.profileImagePath,
        hasStory = hasStory,
        hasUnseenStory = hasUnseenStory,
        storyCount = viewed.size,
        storyViewedStatus = viewed,
        storyAudiences = audiences,
    )

    val storyRing: List<FeedStoryUserState>
        get() = listOf(
            ringUser(me, false, false, emptyList(), emptyList()),
            ringUser(lucia, true, true, listOf(false, false, false), listOf("everyone", "bestFriends", "everyone")),
            ringUser(aiko, true, true, listOf(false, false), listOf("bestFriends", "bestFriends")),
            ringUser(theo, true, true, listOf(false), listOf("everyone")),
            ringUser(kai, true, true, listOf(true, false, false, false), listOf("everyone", "everyone", "everyone", "everyone")),
            ringUser(nora, true, false, listOf(true), listOf("everyone")),
            ringUser(mateo, true, false, listOf(true, true), listOf("everyone", "mutuals")),
            ringUser(sam, true, false, listOf(true), listOf("everyone")),
            ringUser(ines, true, false, listOf(true), listOf("everyone")),
        )

    // MARK: Historias (visor)

    /** Duración larga para que la barra de progreso no avance durante la captura. */
    const val STORY_DURATION = 600.0

    val storyIds: List<String> = listOf("ss_story_a", "ss_story_b", "ss_story_c")

    private fun makeStory(id: String, asset: String, minutesAgo: Double, audience: String, stickers: List<StickerData>) = Story(
        id = id,
        authorId = lucia.id,
        duration = STORY_DURATION,
        expirationDate = Date(System.currentTimeMillis() + 20L * 3_600_000L),
        mediaItem = MediaItem(
            id = "${id}_media",
            type = MediaItem.MediaType.IMAGE,
            url = ScreenshotImages.url(asset),
            aspectRatio = "9:16",
        ),
        profileImagePath = lucia.profileImagePath,
        timestamp = ago(minutesAgo),
        username = lucia.username,
        audience = audience,
        stickers = stickers,
        aspectRatio = "9:16",
    )

    val stories: List<Story> by lazy {
        listOf(
            // A: rasca y gana a pantalla completa.
            makeStory(
                id = storyIds[0],
                asset = "ss_story_festival",
                minutesAgo = 190.0,
                audience = "everyone",
                stickers = listOf(
                    StickerData(
                        stickerId = "ss_reveal",
                        type = "reveal",
                        content = "",
                        position = Point(0.5, 0.5),
                        scale = 1.0,
                        rotation = 0.0,
                        zIndex = 1,
                        revealType = "gradient",
                        revealPattern = "dots",
                        revealPrimaryColor = "5B2C6F",
                        revealSecondaryColor = "C86DD7",
                        revealEffectColor = "FFFFFF",
                    ),
                ),
            ),
            // B: polaroid + encuesta.
            makeStory(
                id = storyIds[1],
                asset = "ss_story_sunrise",
                minutesAgo = 120.0,
                audience = "bestFriends",
                stickers = listOf(
                    StickerData(
                        stickerId = "ss_frame",
                        type = "frame",
                        content = ScreenshotImages.base64JPEG(ScreenshotMode.appContext, "ss_moment_lisbon_tram"),
                        position = Point(0.5, 0.36),
                        scale = 1.2,
                        rotation = -0.07,
                        zIndex = 1,
                        caption = ScreenshotCopy.text("story.frameCaption"),
                        frameStyle = "classic",
                    ),
                    StickerData(
                        stickerId = "ss_poll",
                        type = "poll",
                        content = "",
                        position = Point(0.5, 0.76),
                        scale = 0.95,
                        rotation = 0.0,
                        zIndex = 2,
                        styleVariant = 0,
                        questionText = ScreenshotCopy.text("story.pollQuestion"),
                        pollOptions = listOf(
                            ScreenshotCopy.text("story.pollOptionA"),
                            ScreenshotCopy.text("story.pollOptionB"),
                        ),
                    ),
                ),
            ),
            // C: caja de preguntas.
            makeStory(
                id = storyIds[2],
                asset = "ss_story_cooking",
                minutesAgo = 40.0,
                audience = "everyone",
                stickers = listOf(
                    StickerData(
                        stickerId = "ss_question",
                        type = "question",
                        content = "",
                        position = Point(0.5, 0.72),
                        scale = 1.0,
                        rotation = 0.0,
                        zIndex = 1,
                        styleVariant = 1,
                        questionText = ScreenshotCopy.text("story.question"),
                    ),
                ),
            ),
        )
    }

    // MARK: Mensajes

    const val CHAT_CONVERSATION_ID = "ss_conv_lucia"

    private fun makeConversation(
        friend: AppUser,
        lastMessage: String,
        minutesAgo: Double,
        unread: Boolean,
        lastFromMe: Boolean,
    ): Conversation {
        val own = ownIds
        // Las vistas de la bandeja leen el uid real de Auth: cuenta también como propio.
        val readStatus = own.associateWith { !unread } + (friend.id to true)
        return Conversation(
            id = if (friend.id == lucia.id) CHAT_CONVERSATION_ID else "ss_conv_${friend.id}",
            participants = listOf(friend.id, me.id),
            lastMessage = lastMessage,
            timestamp = ago(minutesAgo),
            readStatus = readStatus,
            otherParticipantId = friend.id,
            otherParticipantUsername = friend.username,
            otherParticipantProfileImagePath = friend.profileImagePath,
        ).apply {
            lastMessageSenderId = if (lastFromMe) ScreenshotMode.authUid ?: me.id else friend.id
            lastMessageType = MessageType.TEXT
        }
    }

    val conversations: List<Conversation>
        get() {
            val lastChatLine = ScreenshotCopy.entries("chat").lastOrNull { it["text"] != null }?.get("text").orEmpty()
            return listOf(
                makeConversation(lucia, lastChatLine, 3.0, unread = false, lastFromMe = true),
                makeConversation(theo, ScreenshotCopy.text("inbox.theo"), 12.0, unread = true, lastFromMe = false),
                makeConversation(aiko, ScreenshotCopy.text("inbox.aiko"), 34.0, unread = true, lastFromMe = false),
                makeConversation(kai, ScreenshotCopy.text("inbox.kai"), 75.0, unread = false, lastFromMe = false),
                makeConversation(mateo, ScreenshotCopy.text("inbox.mateo"), 160.0, unread = false, lastFromMe = false),
                makeConversation(nora, ScreenshotCopy.text("inbox.nora"), 320.0, unread = false, lastFromMe = false),
                makeConversation(ines, ScreenshotCopy.text("inbox.ines"), 1_440.0, unread = false, lastFromMe = false),
                makeConversation(sam, ScreenshotCopy.text("inbox.sam"), 2_900.0, unread = false, lastFromMe = false),
            )
        }

    private val voiceWaveform: List<Float> = listOf(
        0.18f, 0.32f, 0.55f, 0.41f, 0.72f, 0.9f, 0.64f, 0.38f, 0.5f, 0.82f,
        0.95f, 0.7f, 0.44f, 0.3f, 0.58f, 0.76f, 0.62f, 0.4f, 0.26f, 0.48f,
        0.68f, 0.86f, 0.74f, 0.52f, 0.36f, 0.6f, 0.8f, 0.66f, 0.42f, 0.28f,
        0.46f, 0.7f, 0.88f, 0.6f, 0.34f, 0.22f, 0.4f, 0.56f, 0.3f, 0.16f,
    )

    fun messages(conversationId: String): List<EnhancedMessage> {
        if (conversationId != CHAT_CONVERSATION_ID) return emptyList()
        val lines = ScreenshotCopy.entries("chat")
        val start = maxOf(lines.size, 1) * 6.0 + 4.0
        return lines.mapIndexed { index, line ->
            val fromMe = line["from"] == "me"
            val senderId = if (fromMe) me.id else lucia.id
            val timestamp = ago(start - index * 6.0)
            val messageId = "ss_msg_$index"
            when (line["type"] ?: "text") {
                "image" -> EnhancedMessage(
                    id = messageId,
                    conversationId = conversationId,
                    senderId = senderId,
                    type = MessageType.IMAGE,
                    mediaUrl = ScreenshotImages.url("ss_chat_photo"),
                    thumbnailUrl = ScreenshotImages.url("ss_chat_photo"),
                    mediaWidth = 1080,
                    mediaHeight = 1350,
                    timestamp = timestamp,
                    status = MessageStatus.READ,
                    isRead = true,
                )
                "audio" -> EnhancedMessage(
                    id = messageId,
                    conversationId = conversationId,
                    senderId = senderId,
                    type = MessageType.AUDIO,
                    mediaUrl = "https://${ScreenshotImages.HOST}/ss_voice_note.m4a",
                    duration = 14.0,
                    audioWaveform = voiceWaveform,
                    timestamp = timestamp,
                    status = MessageStatus.READ,
                    isRead = true,
                )
                else -> EnhancedMessage(
                    id = messageId,
                    conversationId = conversationId,
                    senderId = senderId,
                    type = MessageType.TEXT,
                    content = line["text"].orEmpty(),
                    timestamp = timestamp,
                    status = MessageStatus.READ,
                    isRead = true,
                )
            }
        }
    }

    // MARK: Echo

    private val echoConcertKai by lazy { makeMoment("ss_echo_m_kai", kai, "ss_moment_concert", "concert", 70.0, "Parc del Fòrum", coordinate(41.4105, 2.2266), 120, 6) }
    private val echoFestivalLucia by lazy { makeMoment("ss_echo_m_lucia", lucia, "ss_story_festival", "festival", 62.0, "Parc del Fòrum", coordinate(41.4107, 2.2262), 98, 4, aspectRatio = "9:16") }
    private val echoNightTheo by lazy { makeMoment("ss_echo_m_theo", theo, "ss_moment_night_market", "night_market", 55.0, "Parc del Fòrum", coordinate(41.4101, 2.2270), 64, 3) }

    private val echoMoments: List<Moment> get() = listOf(echoFestivalLucia, echoConcertKai, echoNightTheo)

    fun echoMoment(id: String): Moment? = echoMoments.firstOrNull { it.id == id }

    val echo: Echo by lazy {
        val participants = listOf(lucia, kai, theo).map {
            EchoParticipant(
                userId = it.id,
                username = it.username,
                profileImagePath = it.profileImagePath,
                status = EchoParticipantStatus.ACCEPTED,
            )
        }
        val refs = echoMoments.mapNotNull { moment ->
            moment.mediaItems?.firstOrNull()?.let { EchoMomentRef.fromMediaItem(it, moment) }
        }
        Echo(
            id = "ss_echo",
            hostId = lucia.id,
            participants = participants,
            location = coordinate(41.4105, 2.2266),
            locationName = "Parc del Fòrum",
            createdAt = ago(90.0),
            expiresAt = Date(System.currentTimeMillis() + 20L * 3_600_000L),
            status = EchoStatus.ACTIVE,
            moments = refs,
            vibeSummary = ScreenshotCopy.text("echo.vibe"),
            participantIds = participants.map { it.userId },
        )
    }

    // MARK: Mapa

    val mapPayload: MapDiscoverPayload
        get() {
            val moments = listOf(beachSunset, picnic, rooftopDinner, latte, concert, nightMarket, dogPark, birthday)
            val stories = listOf(
                MapStoryPreview(
                    id = "ss_map_story_aiko",
                    authorId = aiko.id,
                    username = aiko.username,
                    profileImagePath = aiko.profileImagePath,
                    timestamp = ago(35.0),
                    locationName = "Sagrada Família",
                    latitude = 41.4036,
                    longitude = 2.1744,
                    previewUrl = ScreenshotImages.url("ss_story_sunrise"),
                    locationFuzzed = false,
                ),
                MapStoryPreview(
                    id = "ss_map_story_kai",
                    authorId = kai.id,
                    username = kai.username,
                    profileImagePath = kai.profileImagePath,
                    timestamp = ago(80.0),
                    locationName = "Bunkers del Carmel",
                    latitude = 41.4190,
                    longitude = 2.1620,
                    previewUrl = ScreenshotImages.url("ss_story_festival"),
                    locationFuzzed = false,
                ),
            )
            return MapDiscoverPayload(
                moments = moments,
                stories = stories,
                source = "screenshots",
                momentsError = null,
                storiesError = null,
            )
        }

    // MARK: Listas de audiencia

    val customLists: List<CustomAudienceList>
        get() = listOf(
            CustomAudienceList(
                id = "ss_list_climbing",
                name = ScreenshotCopy.text("lists.climbing"),
                members = listOf(mateo.id, sam.id, kai.id, theo.id),
                color = "06D6A0",
                icon = "flag.fill",
            ),
            CustomAudienceList(
                id = "ss_list_family",
                name = ScreenshotCopy.text("lists.family"),
                members = listOf(nora.id, ines.id),
                color = "FF6B6B",
                icon = "house.fill",
            ),
            CustomAudienceList(
                id = "ss_list_studio",
                name = ScreenshotCopy.text("lists.studio"),
                members = listOf(aiko.id, lucia.id, ines.id, theo.id, mateo.id),
                color = "3A86FF",
                icon = "briefcase.fill",
            ),
        )

    // MARK: Nova

    val novaMessages: List<NovaChatMessage>
        get() = ScreenshotCopy.entries("nova").map { entry ->
            NovaChatMessage(
                text = entry["text"].orEmpty(),
                isUser = entry["from"] == "me",
                isHistorical = true,
            )
        }
}
