package com.moments.android.views.explore

import android.app.Application
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.moments.android.models.Moment
import com.moments.android.models.SavePayload
import com.moments.android.models.encode
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h800dp-mdpi")
class ExploreViewportTest {
    @get:Rule val compose = createComposeRule()
    @Test fun actualGridRemainsBoundedAndClickableAfterLargeJumpAndAppend() {
        lateinit var scroll: ScrollState
        var tapped = -1
        val entries = mutableStateOf((0..999).map { Moment(id = "$it", authorId = "author", audience = "everyone") })
        compose.setContent {
            scroll = rememberScrollState()
            Box(Modifier.size(360.dp, 640.dp)) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    Spacer(Modifier.height(900.dp))
                    ExploreMomentsBentoGrid(entries.value, onMomentTap = { _, index, _ -> tapped = index })
                }
            }
        }
        compose.waitForIdle()
        fun cells() = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue("bounded initial composition", cells().size < 100)
        compose.runOnIdle { runBlocking { scroll.scrollTo(25000) } }
        compose.waitForIdle()
        val deep = cells()
        assertTrue("bounded deep composition", deep.size in 1..100)
        val visible = deep.first { it.boundsInRoot.center.y in 50f..590f && it.boundsInRoot.width > 0 }
        compose.onRoot().performTouchInput { click(visible.boundsInRoot.center) }
        compose.runOnIdle { assertTrue("tap addresses deep item: $tapped", tapped > 200) }
        val position = scroll.value
        compose.runOnIdle { entries.value = (0..1199).map { Moment(id = "$it", authorId = "author", audience = "everyone") } }
        compose.waitForIdle()
        assertEquals(position, scroll.value)
        assertTrue("bounded appended composition", cells().size in 1..100)
        compose.runOnIdle { runBlocking { scroll.scrollTo(900) } }
        compose.waitForIdle()
        val top = cells().first { it.boundsInRoot.center.y in 10f..590f && it.boundsInRoot.width > 0 }
        compose.onRoot().performTouchInput { click(top.boundsInRoot.center) }
        compose.runOnIdle { assertTrue("return restores first items: $tapped", tapped < 20) }
    }

    @Test fun viewportRangesIncludeTallTilesAndHandleRotation() {
        val portrait = exploreVisibleRowRange(-50000f, 1000000f, 0f, 900f, 101f, 10000, 8)
        val landscape = exploreVisibleRowRange(-50000f, 1000000f, 0f, 500f, 101f, 10000, 8)
        assertTrue(portrait.first > 400)
        assertTrue(portrait.last - portrait.first < 30)
        assertEquals(portrait.first, landscape.first)
        assertTrue(landscape.last <= portrait.last)
        val partial = exploreVisibleRowRange(-150f, 10000f, 0f, 800f, 101f, 100, 0)
        assertTrue(1 < partial.last + 1 && 1 + 2 > partial.first)
    }

    @Test fun savedPayloadPreservesAuthorAndRemainsCompatibleWithoutOne() {
        val current = JSONObject(String(SavePayload("viewer", "post", "author").encode()))
        assertEquals("author", current.getString("authorId"))
        val old = JSONObject(String(SavePayload("viewer", "post").encode()))
        assertFalse(old.has("authorId"))
        assertEquals("post", old.getString("momentId"))
    }
}
