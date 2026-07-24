package com.moments.android.models

import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

/** Payloads de subida en background (paridad con Views/Creator en iOS). */

data class CachedUploadMediaItem(
    val type: String,
    val localFileName: String,
    val thumbnailFileName: String? = null,
    val aspectRatio: String? = null,
    val videoDuration: Double? = null,
    val videoFileSize: Long? = null,
    val videoResolution: String? = null,
    val tags: List<PhotoTag>? = null,
)

data class CachedStickerInteractionData(
    val username: String? = null,
    val userId: String? = null,
    val hashtag: String? = null,
    val location: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val styleVariant: Int? = null,
    val pollData: List<String>? = null,
    val questionText: String? = null,
    val weatherSymbol: String? = null,
    val linkURL: String? = null,
    val linkTitle: String? = null,
    val countdownTitle: String? = null,
    val countdownTargetAtMs: Double? = null,
    val sliderEmoji: String? = null,
    val sliderPrompt: String? = null,
    val caption: String? = null,
    val profileImagePath: String? = null,
    val momentId: String? = null,
    val mediaCount: Int? = null,
    val quizQuestion: String? = null,
    val quizOptions: List<String>? = null,
    val quizCorrectIndex: Int? = null,
    val revealType: String? = null,
    val revealPattern: String? = null,
    val revealPrimaryColor: String? = null,
    val revealSecondaryColor: String? = null,
    val revealEffectColor: String? = null,
    val frameStyle: String? = null,
    val contentScale: Double? = null,
    val contentOffsetX: Double? = null,
    val contentOffsetY: Double? = null,
    val audioURL: String? = null,
    val audioDuration: Double? = null,
)

data class CachedSticker(
    val id: String,
    val localImageName: String? = null,
    val position: Point,
    val scale: Double,
    val rotationRadians: Double,
    val gifURL: String? = null,
    val videoURL: String? = null,
    val isAnimated: Boolean = false,
    val type: String,
    val interactionData: CachedStickerInteractionData? = null,
)

data class CachedHiddenLayerDraft(
    val id: String,
    val type: String,
    val anchorX: Double,
    val anchorY: Double,
    val width: Double,
    val height: Double,
    val shape: String,
    val zIndex: Int,
    val text: String,
    val caption: String,
    val imageOffsetX: Double,
    val imageOffsetY: Double,
    val imageScale: Double,
    val imageFrameStyle: String,
    val localImageFileName: String?,
    val localAudioFileName: String?,
    val duration: Double?,
    val textStyle: String,
    val presentationStyle: String,
    val unlockMode: String,
    val unlockAt: Date?,
    val authorTimezoneIdentifier: String?,
)

data class MomentUploadPayload(
    val plannedMomentId: String?,
    val content: String,
    val mediaPaths: List<CachedUploadMediaItem>,
    val taggedUsers: List<String>? = null,
    val mentionedUsers: List<String>? = null,
    val location: String? = null,
    val locationCoordinate: Moment.LocationCoordinate? = null,
    val audienceSetting: String,
    val customViewers: List<String>? = null,
    val customListId: String? = null,
    val aspectRatio: String,
    val disableComments: Boolean = false,
    val hideLikeCounts: Boolean = false,
    val allowSharing: Boolean = true,
    val scheduledDate: Date? = null,
    val hiddenLayers: List<CachedHiddenLayerDraft>? = null,
)

data class StoryUploadPayload(
    val plannedStoryId: String?,
    val userId: String,
    val mediaItem: CachedUploadMediaItem,
    val storyText: String? = null,
    val textPosition: Point? = null,
    val selectedTextStyle: String? = null,
    val textOverlayMetadata: StoryTextOverlayMetadata? = null,
    val textOverlays: List<StoryTextOverlayMetadata>? = null,
    val audienceSetting: String,
    val customViewers: List<String>? = null,
    val customListId: String? = null,
    val selectedListName: String? = null,
    val createdAt: Date = Date(),
    val storyVideoMode: String? = null,
    val chainId: String? = null,
    val chainPosition: Int? = null,
    val chainTitle: String? = null,
    val allowOthersToContinue: Boolean? = null,
    val continuationAudience: String? = null,
    val continuationCustomViewers: List<String>? = null,
    val continuationCustomListId: String? = null,
    val continuationCustomListName: String? = null,
    val expirationHours: Int? = null,
    val drawingFileName: String? = null,
    val stickers: List<CachedSticker>? = null,
)

object UploadPayloadDecoder {

    fun decodeMomentPayload(data: ByteArray): MomentUploadPayload? = runCatching {
        val json = JSONObject(String(data))
        val mediaArr = json.optJSONArray("mediaPaths") ?: JSONArray()
        val mediaPaths = (0 until mediaArr.length()).mapNotNull { i ->
            val item = mediaArr.optJSONObject(i) ?: return@mapNotNull null
            CachedUploadMediaItem(
                type = item.getString("type"),
                localFileName = item.getString("localFileName"),
                thumbnailFileName = item.optString("thumbnailFileName").takeIf { item.has("thumbnailFileName") && !item.isNull("thumbnailFileName") },
                aspectRatio = item.optString("aspectRatio").takeIf { item.has("aspectRatio") && !item.isNull("aspectRatio") },
                videoDuration = item.optDouble("videoDuration").takeIf { item.has("videoDuration") && !item.isNull("videoDuration") },
                videoFileSize = item.optLong("videoFileSize").takeIf { item.has("videoFileSize") },
                videoResolution = item.optString("videoResolution").takeIf { item.has("videoResolution") && !item.isNull("videoResolution") },
                tags = item.optJSONArray("tags")?.let { arr ->
                    (0 until arr.length()).mapNotNull { ti ->
                        val t = arr.optJSONObject(ti) ?: return@mapNotNull null
                        PhotoTag(
                            id = t.optString("id", java.util.UUID.randomUUID().toString()),
                            userId = t.getString("userId"),
                            username = t.getString("username"),
                            x = t.getDouble("x"),
                            y = t.getDouble("y"),
                        )
                    }.takeIf { it.isNotEmpty() }
                },
            )
        }
        val hiddenLayers = json.optJSONArray("hiddenLayers")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                decodeHiddenLayerDraft(arr.optJSONObject(i) ?: return@mapNotNull null)
            }.takeIf { it.isNotEmpty() }
        }
        MomentUploadPayload(
            plannedMomentId = json.optString("plannedMomentId").takeIf { json.has("plannedMomentId") && !json.isNull("plannedMomentId") },
            content = json.getString("content"),
            mediaPaths = mediaPaths,
            taggedUsers = json.optStringList("taggedUsers"),
            mentionedUsers = json.optStringList("mentionedUsers"),
            location = json.optString("location").takeIf { json.has("location") && !json.isNull("location") },
            locationCoordinate = json.optJSONObject("locationCoordinate")?.let { coord ->
                Moment.LocationCoordinate(
                    latitude = coord.getDouble("latitude"),
                    longitude = coord.getDouble("longitude"),
                )
            },
            audienceSetting = json.getString("audienceSetting"),
            customViewers = json.optStringList("customViewers"),
            customListId = json.optString("customListId").takeIf { json.has("customListId") && !json.isNull("customListId") },
            aspectRatio = json.optString("aspectRatio", "1:1"),
            disableComments = json.optBoolean("disableComments"),
            hideLikeCounts = json.optBoolean("hideLikeCounts"),
            allowSharing = json.optBoolean("allowSharing", true),
            scheduledDate = json.optLong("scheduledDate").takeIf { json.has("scheduledDate") }?.let { Date(it) },
            hiddenLayers = hiddenLayers,
        )
    }.getOrNull()

    fun decodeStoryPayload(data: ByteArray): StoryUploadPayload? = runCatching {
        val json = JSONObject(String(data))
        val mediaObj = json.getJSONObject("mediaItem")
        val mediaItem = CachedUploadMediaItem(
            type = mediaObj.getString("type"),
            localFileName = mediaObj.getString("localFileName"),
            thumbnailFileName = mediaObj.optString("thumbnailFileName").takeIf { mediaObj.has("thumbnailFileName") && !mediaObj.isNull("thumbnailFileName") },
            aspectRatio = mediaObj.optString("aspectRatio").takeIf { mediaObj.has("aspectRatio") && !mediaObj.isNull("aspectRatio") },
            videoDuration = mediaObj.optDouble("videoDuration").takeIf { mediaObj.has("videoDuration") && !mediaObj.isNull("videoDuration") },
            videoFileSize = mediaObj.optLong("videoFileSize").takeIf { mediaObj.has("videoFileSize") },
            videoResolution = mediaObj.optString("videoResolution").takeIf { mediaObj.has("videoResolution") && !mediaObj.isNull("videoResolution") },
        )
        val textPosition = json.optJSONObject("textPosition")?.let { pos ->
            Point(pos.getDouble("x"), pos.getDouble("y"))
        }
        val textOverlayMetadata = json.optJSONObject("textOverlayMetadata")?.let(::decodeStoryTextOverlay)
        val textOverlays = json.optJSONArray("textOverlays")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                decodeStoryTextOverlay(arr.optJSONObject(i) ?: return@mapNotNull null)
            }.takeIf { it.isNotEmpty() }
        }
        val stickers = json.optJSONArray("stickers")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                decodeCachedSticker(arr.optJSONObject(i) ?: return@mapNotNull null)
            }.takeIf { it.isNotEmpty() }
        }
        StoryUploadPayload(
            plannedStoryId = json.optString("plannedStoryId").takeIf { json.has("plannedStoryId") && !json.isNull("plannedStoryId") },
            userId = json.getString("userId"),
            mediaItem = mediaItem,
            storyText = json.optString("storyText").takeIf { json.has("storyText") && !json.isNull("storyText") },
            textPosition = textPosition,
            selectedTextStyle = json.optString("selectedTextStyle").takeIf { json.has("selectedTextStyle") && !json.isNull("selectedTextStyle") },
            textOverlayMetadata = textOverlayMetadata,
            textOverlays = textOverlays,
            audienceSetting = json.getString("audienceSetting"),
            customViewers = json.optStringList("customViewers"),
            customListId = json.optString("customListId").takeIf { json.has("customListId") && !json.isNull("customListId") },
            selectedListName = json.optString("selectedListName").takeIf { json.has("selectedListName") && !json.isNull("selectedListName") },
            createdAt = Date(json.optLong("createdAt", System.currentTimeMillis())),
            storyVideoMode = json.optString("storyVideoMode").takeIf { json.has("storyVideoMode") && !json.isNull("storyVideoMode") },
            chainId = json.optString("chainId").takeIf { json.has("chainId") && !json.isNull("chainId") },
            chainPosition = json.optInt("chainPosition").takeIf { json.has("chainPosition") },
            chainTitle = json.optString("chainTitle").takeIf { json.has("chainTitle") && !json.isNull("chainTitle") },
            allowOthersToContinue = json.optBoolean("allowOthersToContinue").takeIf { json.has("allowOthersToContinue") },
            continuationAudience = json.optString("continuationAudience").takeIf { json.has("continuationAudience") && !json.isNull("continuationAudience") },
            continuationCustomViewers = json.optStringList("continuationCustomViewers"),
            continuationCustomListId = json.optString("continuationCustomListId").takeIf { json.has("continuationCustomListId") && !json.isNull("continuationCustomListId") },
            continuationCustomListName = json.optString("continuationCustomListName").takeIf { json.has("continuationCustomListName") && !json.isNull("continuationCustomListName") },
            expirationHours = json.optInt("expirationHours").takeIf { json.has("expirationHours") },
            drawingFileName = json.optString("drawingFileName").takeIf { json.has("drawingFileName") && !json.isNull("drawingFileName") },
            stickers = stickers,
        )
    }.getOrNull()

    fun encodeMomentPayload(payload: MomentUploadPayload): ByteArray {
        val json = JSONObject()
        payload.plannedMomentId?.let { json.put("plannedMomentId", it) }
        json.put("content", payload.content)
        json.put(
            "mediaPaths",
            JSONArray().apply {
                payload.mediaPaths.forEach { item ->
                    put(
                        JSONObject().apply {
                            put("type", item.type)
                            put("localFileName", item.localFileName)
                            item.thumbnailFileName?.let { put("thumbnailFileName", it) }
                            item.aspectRatio?.let { put("aspectRatio", it) }
                            item.videoDuration?.let { put("videoDuration", it) }
                            item.videoFileSize?.let { put("videoFileSize", it) }
                            item.videoResolution?.let { put("videoResolution", it) }
                            item.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
                                put(
                                    "tags",
                                    JSONArray().apply {
                                        tags.forEach { t ->
                                            put(
                                                JSONObject().apply {
                                                    put("id", t.id)
                                                    put("userId", t.userId)
                                                    put("username", t.username)
                                                    put("x", t.x)
                                                    put("y", t.y)
                                                },
                                            )
                                        }
                                    },
                                )
                            }
                        },
                    )
                }
            },
        )
        payload.taggedUsers?.let { json.put("taggedUsers", JSONArray(it)) }
        payload.mentionedUsers?.let { json.put("mentionedUsers", JSONArray(it)) }
        payload.location?.let { json.put("location", it) }
        payload.locationCoordinate?.let { coord ->
            json.put(
                "locationCoordinate",
                JSONObject().apply {
                    put("latitude", coord.latitude)
                    put("longitude", coord.longitude)
                },
            )
        }
        json.put("audienceSetting", payload.audienceSetting)
        payload.customViewers?.let { json.put("customViewers", JSONArray(it)) }
        payload.customListId?.let { json.put("customListId", it) }
        json.put("aspectRatio", payload.aspectRatio)
        json.put("disableComments", payload.disableComments)
        json.put("hideLikeCounts", payload.hideLikeCounts)
        json.put("allowSharing", payload.allowSharing)
        payload.scheduledDate?.let { json.put("scheduledDate", it.time) }
        payload.hiddenLayers?.takeIf { it.isNotEmpty() }?.let { layers ->
            json.put(
                "hiddenLayers",
                JSONArray().apply {
                    layers.forEach { d ->
                        put(
                            JSONObject().apply {
                                put("id", d.id)
                                put("type", d.type)
                                put("anchorX", d.anchorX)
                                put("anchorY", d.anchorY)
                                put("width", d.width)
                                put("height", d.height)
                                put("shape", d.shape)
                                put("zIndex", d.zIndex)
                                put("text", d.text)
                                put("caption", d.caption)
                                put("imageOffsetX", d.imageOffsetX)
                                put("imageOffsetY", d.imageOffsetY)
                                put("imageScale", d.imageScale)
                                put("imageFrameStyle", d.imageFrameStyle)
                                d.localImageFileName?.let { put("localImageFileName", it) }
                                d.localAudioFileName?.let { put("localAudioFileName", it) }
                                d.duration?.let { put("duration", it) }
                                put("textStyle", d.textStyle)
                                put("presentationStyle", d.presentationStyle)
                                put("unlockMode", d.unlockMode)
                                d.unlockAt?.let { put("unlockAt", it.time) }
                                d.authorTimezoneIdentifier?.let { put("authorTimezoneIdentifier", it) }
                            },
                        )
                    }
                },
            )
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    fun encodeStoryPayload(payload: StoryUploadPayload): ByteArray {
        val json = JSONObject()
        payload.plannedStoryId?.let { json.put("plannedStoryId", it) }
        json.put("userId", payload.userId)
        json.put(
            "mediaItem",
            JSONObject().apply {
                put("type", payload.mediaItem.type)
                put("localFileName", payload.mediaItem.localFileName)
                payload.mediaItem.thumbnailFileName?.let { put("thumbnailFileName", it) }
                payload.mediaItem.aspectRatio?.let { put("aspectRatio", it) }
                payload.mediaItem.videoDuration?.let { put("videoDuration", it) }
                payload.mediaItem.videoFileSize?.let { put("videoFileSize", it) }
                payload.mediaItem.videoResolution?.let { put("videoResolution", it) }
            },
        )
        payload.storyText?.let { json.put("storyText", it) }
        payload.textPosition?.let { pos ->
            json.put(
                "textPosition",
                JSONObject().apply {
                    put("x", pos.x)
                    put("y", pos.y)
                },
            )
        }
        payload.selectedTextStyle?.let { json.put("selectedTextStyle", it) }
        json.put("audienceSetting", payload.audienceSetting)
        payload.customViewers?.let { json.put("customViewers", JSONArray(it)) }
        payload.customListId?.let { json.put("customListId", it) }
        payload.selectedListName?.let { json.put("selectedListName", it) }
        json.put("createdAt", payload.createdAt.time)
        payload.storyVideoMode?.let { json.put("storyVideoMode", it) }
        payload.chainId?.let { json.put("chainId", it) }
        payload.chainPosition?.let { json.put("chainPosition", it) }
        payload.chainTitle?.let { json.put("chainTitle", it) }
        payload.allowOthersToContinue?.let { json.put("allowOthersToContinue", it) }
        payload.continuationAudience?.let { json.put("continuationAudience", it) }
        payload.continuationCustomViewers?.let { json.put("continuationCustomViewers", JSONArray(it)) }
        payload.continuationCustomListId?.let { json.put("continuationCustomListId", it) }
        payload.continuationCustomListName?.let { json.put("continuationCustomListName", it) }
        payload.expirationHours?.let { json.put("expirationHours", it) }
        payload.drawingFileName?.let { json.put("drawingFileName", it) }
        fun encodeOverlay(meta: StoryTextOverlayMetadata): JSONObject = JSONObject().apply {
            put("id", meta.id)
            put("text", meta.text)
            put(
                "normalizedPosition",
                JSONObject().apply {
                    put("x", meta.normalizedPosition.x)
                    put("y", meta.normalizedPosition.y)
                },
            )
            put("layerOrder", meta.layerOrder)
            put("styleRaw", meta.styleRaw)
            put("colorHex", meta.colorHex)
            put("fontSize", meta.fontSize)
            put("alignmentRaw", meta.alignmentRaw)
            put("backgroundFillRaw", meta.backgroundFillRaw)
            put("strokeRaw", meta.strokeRaw)
            put("visualEffectRaw", meta.visualEffectRaw)
            put("motionRaw", meta.motionRaw)
            put("forcesAllCaps", meta.forcesAllCaps)
            put("isLiveOverlay", meta.isLiveOverlay)
            meta.gradientStopHexes?.let { put("gradientStopHexes", JSONArray(it)) }
            meta.gradientAngle?.let { put("gradientAngle", it) }
        }
        payload.textOverlayMetadata?.let { json.put("textOverlayMetadata", encodeOverlay(it)) }
        payload.textOverlays?.takeIf { it.isNotEmpty() }?.let { list ->
            json.put(
                "textOverlays",
                JSONArray().apply { list.forEach { put(encodeOverlay(it)) } },
            )
        }
        payload.stickers?.takeIf { it.isNotEmpty() }?.let { list ->
            json.put(
                "stickers",
                JSONArray().apply {
                    list.forEach { sticker ->
                        put(
                            JSONObject().apply {
                                put("id", sticker.id)
                                sticker.localImageName?.let { put("localImageName", it) }
                                put(
                                    "position",
                                    JSONObject().apply {
                                        put("x", sticker.position.x)
                                        put("y", sticker.position.y)
                                    },
                                )
                                put("scale", sticker.scale)
                                put("rotationRadians", sticker.rotationRadians)
                                sticker.gifURL?.let { put("gifURL", it) }
                                sticker.videoURL?.let { put("videoURL", it) }
                                put("isAnimated", sticker.isAnimated)
                                put("type", sticker.type)
                                sticker.interactionData?.let { data ->
                                    put(
                                        "interactionData",
                                        JSONObject().apply {
                                            data.username?.let { put("username", it) }
                                            data.userId?.let { put("userId", it) }
                                            data.hashtag?.let { put("hashtag", it) }
                                            data.location?.let { put("location", it) }
                                            data.latitude?.let { put("latitude", it) }
                                            data.longitude?.let { put("longitude", it) }
                                            data.styleVariant?.let { put("styleVariant", it) }
                                            data.pollData?.let { put("pollData", JSONArray(it)) }
                                            data.questionText?.let { put("questionText", it) }
                                            data.weatherSymbol?.let { put("weatherSymbol", it) }
                                            data.linkURL?.let { put("linkURL", it) }
                                            data.linkTitle?.let { put("linkTitle", it) }
                                            data.countdownTitle?.let { put("countdownTitle", it) }
                                            data.countdownTargetAtMs?.let { put("countdownTargetAtMs", it) }
                                            data.sliderEmoji?.let { put("sliderEmoji", it) }
                                            data.sliderPrompt?.let { put("sliderPrompt", it) }
                                            data.caption?.let { put("caption", it) }
                                            data.profileImagePath?.let { put("profileImagePath", it) }
                                            data.momentId?.let { put("momentId", it) }
                                            data.mediaCount?.let { put("mediaCount", it) }
                                            data.quizQuestion?.let { put("quizQuestion", it) }
                                            data.quizOptions?.let { put("quizOptions", JSONArray(it)) }
                                            data.quizCorrectIndex?.let { put("quizCorrectIndex", it) }
                                            data.revealType?.let { put("revealType", it) }
                                            data.revealPattern?.let { put("revealPattern", it) }
                                            data.revealPrimaryColor?.let { put("revealPrimaryColor", it) }
                                            data.revealSecondaryColor?.let { put("revealSecondaryColor", it) }
                                            data.revealEffectColor?.let { put("revealEffectColor", it) }
                                            data.frameStyle?.let { put("frameStyle", it) }
                                            data.contentScale?.let { put("contentScale", it) }
                                            data.contentOffsetX?.let { put("contentOffsetX", it) }
                                            data.contentOffsetY?.let { put("contentOffsetY", it) }
                                            data.audioURL?.let { put("audioURL", it) }
                                            data.audioDuration?.let { put("audioDuration", it) }
                                        },
                                    )
                                }
                            },
                        )
                    }
                },
            )
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    private fun decodeHiddenLayerDraft(obj: JSONObject): CachedHiddenLayerDraft? = runCatching {
        CachedHiddenLayerDraft(
            id = obj.getString("id"),
            type = obj.getString("type"),
            anchorX = obj.getDouble("anchorX"),
            anchorY = obj.getDouble("anchorY"),
            width = obj.getDouble("width"),
            height = obj.getDouble("height"),
            shape = obj.optString("shape", "roundedRect"),
            zIndex = obj.optInt("zIndex"),
            text = obj.optString("text", ""),
            caption = obj.optString("caption", ""),
            imageOffsetX = obj.optDouble("imageOffsetX"),
            imageOffsetY = obj.optDouble("imageOffsetY"),
            imageScale = obj.optDouble("imageScale", 1.0),
            imageFrameStyle = obj.optString("imageFrameStyle", "classic"),
            localImageFileName = obj.optString("localImageFileName").takeIf { obj.has("localImageFileName") && !obj.isNull("localImageFileName") },
            localAudioFileName = obj.optString("localAudioFileName").takeIf { obj.has("localAudioFileName") && !obj.isNull("localAudioFileName") },
            duration = obj.optDouble("duration").takeIf { obj.has("duration") && !obj.isNull("duration") },
            textStyle = obj.optString("textStyle", "classic"),
            presentationStyle = obj.optString("presentationStyle", "glassCard"),
            unlockMode = obj.optString("unlockMode", "immediate"),
            unlockAt = obj.optLong("unlockAt").takeIf { obj.has("unlockAt") && !obj.isNull("unlockAt") }?.let { Date(it) },
            authorTimezoneIdentifier = obj.optString("authorTimezoneIdentifier").takeIf { obj.has("authorTimezoneIdentifier") && !obj.isNull("authorTimezoneIdentifier") },
        )
    }.getOrNull()

    private fun decodeCachedSticker(obj: JSONObject): CachedSticker? = runCatching {
        val posObj = obj.getJSONObject("position")
        val position = Point(posObj.getDouble("x"), posObj.getDouble("y"))
        val interaction = obj.optJSONObject("interactionData")?.let { data ->
            CachedStickerInteractionData(
                username = data.optString("username").takeIf { data.has("username") && !data.isNull("username") },
                userId = data.optString("userId").takeIf { data.has("userId") && !data.isNull("userId") },
                hashtag = data.optString("hashtag").takeIf { data.has("hashtag") && !data.isNull("hashtag") },
                location = data.optString("location").takeIf { data.has("location") && !data.isNull("location") },
                latitude = data.optDouble("latitude").takeIf { data.has("latitude") && !data.isNull("latitude") },
                longitude = data.optDouble("longitude").takeIf { data.has("longitude") && !data.isNull("longitude") },
                styleVariant = data.optInt("styleVariant").takeIf { data.has("styleVariant") },
                pollData = data.optStringList("pollData"),
                questionText = data.optString("questionText").takeIf { data.has("questionText") && !data.isNull("questionText") },
                weatherSymbol = data.optString("weatherSymbol").takeIf { data.has("weatherSymbol") && !data.isNull("weatherSymbol") },
                linkURL = data.optString("linkURL").takeIf { data.has("linkURL") && !data.isNull("linkURL") },
                linkTitle = data.optString("linkTitle").takeIf { data.has("linkTitle") && !data.isNull("linkTitle") },
                countdownTitle = data.optString("countdownTitle").takeIf { data.has("countdownTitle") && !data.isNull("countdownTitle") },
                countdownTargetAtMs = data.optDouble("countdownTargetAtMs").takeIf { data.has("countdownTargetAtMs") && !data.isNull("countdownTargetAtMs") },
                sliderEmoji = data.optString("sliderEmoji").takeIf { data.has("sliderEmoji") && !data.isNull("sliderEmoji") },
                sliderPrompt = data.optString("sliderPrompt").takeIf { data.has("sliderPrompt") && !data.isNull("sliderPrompt") },
                caption = data.optString("caption").takeIf { data.has("caption") && !data.isNull("caption") },
                profileImagePath = data.optString("profileImagePath").takeIf { data.has("profileImagePath") && !data.isNull("profileImagePath") },
                momentId = data.optString("momentId").takeIf { data.has("momentId") && !data.isNull("momentId") },
                mediaCount = data.optInt("mediaCount").takeIf { data.has("mediaCount") },
                quizQuestion = data.optString("quizQuestion").takeIf { data.has("quizQuestion") && !data.isNull("quizQuestion") },
                quizOptions = data.optStringList("quizOptions"),
                quizCorrectIndex = data.optInt("quizCorrectIndex").takeIf { data.has("quizCorrectIndex") },
                revealType = data.optString("revealType").takeIf { data.has("revealType") && !data.isNull("revealType") },
                revealPattern = data.optString("revealPattern").takeIf { data.has("revealPattern") && !data.isNull("revealPattern") },
                revealPrimaryColor = data.optString("revealPrimaryColor").takeIf { data.has("revealPrimaryColor") && !data.isNull("revealPrimaryColor") },
                revealSecondaryColor = data.optString("revealSecondaryColor").takeIf { data.has("revealSecondaryColor") && !data.isNull("revealSecondaryColor") },
                revealEffectColor = data.optString("revealEffectColor").takeIf { data.has("revealEffectColor") && !data.isNull("revealEffectColor") },
                frameStyle = data.optString("frameStyle").takeIf { data.has("frameStyle") && !data.isNull("frameStyle") },
                contentScale = data.optDouble("contentScale").takeIf { data.has("contentScale") && !data.isNull("contentScale") },
                contentOffsetX = data.optDouble("contentOffsetX").takeIf { data.has("contentOffsetX") && !data.isNull("contentOffsetX") },
                contentOffsetY = data.optDouble("contentOffsetY").takeIf { data.has("contentOffsetY") && !data.isNull("contentOffsetY") },
                audioURL = data.optString("audioURL").takeIf { data.has("audioURL") && !data.isNull("audioURL") },
                audioDuration = data.optDouble("audioDuration").takeIf { data.has("audioDuration") && !data.isNull("audioDuration") },
            )
        }
        CachedSticker(
            id = obj.getString("id"),
            localImageName = obj.optString("localImageName").takeIf { obj.has("localImageName") && !obj.isNull("localImageName") },
            position = position,
            scale = obj.getDouble("scale"),
            rotationRadians = obj.getDouble("rotationRadians"),
            gifURL = obj.optString("gifURL").takeIf { obj.has("gifURL") && !obj.isNull("gifURL") },
            videoURL = obj.optString("videoURL").takeIf { obj.has("videoURL") && !obj.isNull("videoURL") },
            isAnimated = obj.optBoolean("isAnimated"),
            type = obj.getString("type"),
            interactionData = interaction,
        )
    }.getOrNull()

    private fun decodeStoryTextOverlay(obj: JSONObject): StoryTextOverlayMetadata {
        val positionObj = obj.optJSONObject("normalizedPosition")
        val position = if (positionObj != null) {
            Point(positionObj.getDouble("x"), positionObj.getDouble("y"))
        } else {
            Point(0.0, 0.0)
        }
        return StoryTextOverlayMetadata(
            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
            text = obj.optString("text", ""),
            normalizedPosition = position,
            layerOrder = obj.optInt("layerOrder"),
            styleRaw = obj.optString("styleRaw", ""),
            colorHex = obj.optString("colorHex", ""),
            fontSize = obj.optDouble("fontSize"),
            alignmentRaw = obj.optString("alignmentRaw", ""),
            backgroundFillRaw = obj.optString("backgroundFillRaw", ""),
            strokeRaw = obj.optString("strokeRaw", ""),
            visualEffectRaw = obj.optString("visualEffectRaw", ""),
            motionRaw = obj.optString("motionRaw", ""),
            forcesAllCaps = obj.optBoolean("forcesAllCaps"),
            isLiveOverlay = obj.optBoolean("isLiveOverlay", true),
            gradientStopHexes = obj.optJSONArray("gradientStopHexes")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            },
            gradientAngle = obj.optInt("gradientAngle").takeIf { obj.has("gradientAngle") },
        )
    }

    private fun JSONObject.optStringList(key: String): List<String>? {
        if (!has(key) || isNull(key)) return null
        val arr = optJSONArray(key) ?: return null
        return (0 until arr.length()).map { arr.getString(it) }
    }
}
