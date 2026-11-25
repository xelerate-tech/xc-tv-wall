package tech.xelerate.tvwall.ui

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import tech.xelerate.tvwall.BuildConfig
import tech.xelerate.tvwall.R
import tech.xelerate.tvwall.core.DriveSource
import tech.xelerate.tvwall.core.PlaylistQueue
import tech.xelerate.tvwall.core.SlideSpeed
import tech.xelerate.tvwall.core.Slide
import tech.xelerate.tvwall.core.SyncEngine
import tech.xelerate.tvwall.core.SyncResult
import tech.xelerate.tvwall.core.UpdateChecker
import tech.xelerate.tvwall.drive.ServiceAccountDriveSource
import tech.xelerate.tvwall.settings.Settings
import tech.xelerate.tvwall.update.ApkInstaller
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Full-screen, controller-less loop of the synced Drive folder. */
class PlayerActivity : ComponentActivity() {
    private lateinit var imageFront: ImageView
    private lateinit var imageBack: ImageView
    private lateinit var videoView: PlayerView
    private lateinit var message: TextView
    private lateinit var menu: View
    private lateinit var menuValue: TextView

    private val settings by lazy { Settings(applicationContext) }
    private val hideMenu = Runnable { menu.visibility = View.GONE }

    private val queue = PlaylistQueue(
        pageCount = SlideRenderer::pageCount,
        defaultDurationMs = { settings.slideSeconds * 1000L },
        // Old files are deleted only once the player has moved on to the new playlist. Done on a
        // single background thread, in order, so a long-running sync never stalls playback.
        onActivated = { manifest -> lifecycleScope.launch(cleanupDispatcher) { engine?.releaseUnused(inUse = manifest) } },
    )
    @OptIn(ExperimentalCoroutinesApi::class)
    private val cleanupDispatcher = Dispatchers.IO.limitedParallelism(1)
    private var source: DriveSource? = null
    private var engine: SyncEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        imageFront = findViewById(R.id.imageFront)
        imageBack = findViewById(R.id.imageBack)
        videoView = findViewById(R.id.video)
        message = findViewById(R.id.message)
        menu = findViewById(R.id.menu)
        menuValue = findViewById(R.id.menuValue)

        hideSystemBars()
        // The remote's Back button must never stop the slideshow; Home still exits.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        engine = createEngine()
        if (engine == null) {
            showMessage(getString(R.string.not_configured))
            return
        }

        lifecycleScope.launch {
            withContext(Dispatchers.IO) { queue.setManifest(engine!!.currentManifest()) }
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val player = ExoPlayer.Builder(this@PlayerActivity).build()
                videoView.player = player
                try {
                    launch { syncLoop() }
                    launch { updateLoop() }
                    playLoop(player)
                } finally {
                    videoView.player = null
                    player.release()
                }
            }
        }
    }

    /** OK or Menu opens the slide-speed menu; while it is open, Left/Right change, OK/Back close. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val open = menu.visibility == View.VISIBLE
        val handled = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_MENU -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    if (open) closeMenu() else openMenu()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (open && event.action == KeyEvent.ACTION_DOWN) {
                    val current = settings.slideSeconds
                    settings.slideSeconds = if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) SlideSpeed.faster(current)
                    else SlideSpeed.slower(current)
                    openMenu()
                }
                open
            }
            KeyEvent.KEYCODE_BACK -> {
                if (open && event.action == KeyEvent.ACTION_UP) closeMenu()
                open
            }
            else -> false
        }
        return handled || super.dispatchKeyEvent(event)
    }

    /** Shows (or refreshes) the menu and restarts its auto-close timer. */
    private fun openMenu() {
        val seconds = settings.slideSeconds
        menuValue.text = getString(R.string.menu_seconds, seconds)
        findViewById<View>(R.id.menuFaster).alpha = if (seconds == SlideSpeed.CHOICES_SECONDS.first()) 0.25f else 1f
        findViewById<View>(R.id.menuSlower).alpha = if (seconds == SlideSpeed.CHOICES_SECONDS.last()) 0.25f else 1f
        menu.visibility = View.VISIBLE
        menu.bringToFront()
        menu.removeCallbacks(hideMenu)
        menu.postDelayed(hideMenu, MENU_TIMEOUT_MS)
    }

    private fun closeMenu() {
        menu.removeCallbacks(hideMenu)
        hideMenu.run()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun createEngine(): SyncEngine? {
        if (BuildConfig.DRIVE_FOLDER_ID.isBlank()) return null
        val drive = try {
            assets.open(SERVICE_ACCOUNT_ASSET).use { ServiceAccountDriveSource(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Service account key missing or invalid", e)
            return null
        }
        source = drive
        return SyncEngine(drive, BuildConfig.DRIVE_FOLDER_ID, File(filesDir, "tvwall"))
    }

    private suspend fun CoroutineScope.syncLoop() {
        while (isActive) {
            val result = withContext(Dispatchers.IO) { engine!!.sync() }
            when (result) {
                is SyncResult.Updated -> {
                    Log.i(TAG, "Synced ${result.manifest.items.size} items")
                    result.skipped.forEach { Log.w(TAG, "Could not fetch $it") }
                }
                is SyncResult.Failed -> Log.w(TAG, "Sync failed; keeping current playlist", result.error)
                SyncResult.Unchanged -> Unit
            }
            // Always hand over what is cached, not just a fresh update: a sync that finished while
            // the app was in the background lost its result, and the next one reports Unchanged.
            // The queue ignores a manifest it is already playing.
            if (result !is SyncResult.Failed) queue.setManifest(withContext(Dispatchers.IO) { engine!!.currentManifest() })
            delay(SYNC_INTERVAL_MS)
        }
    }

    private suspend fun CoroutineScope.updateLoop() {
        val drive = source ?: return
        val checker = UpdateChecker(drive, BuildConfig.DRIVE_FOLDER_ID, BuildConfig.VERSION_CODE)
        while (isActive) {
            delay(UPDATE_INTERVAL_MS)
            try {
                val update = withContext(Dispatchers.IO) { checker.findUpdate() } ?: continue
                val apk = withContext(Dispatchers.IO) {
                    File(filesDir, "update").apply { mkdirs() }.resolve("update.apk").also { drive.download(update.id, it) }
                }
                Log.i(TAG, "Offering update ${update.name}")
                ApkInstaller.install(this@PlayerActivity, apk)
                checker.markOffered(update)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed", e)
            }
        }
    }

    private suspend fun CoroutineScope.playLoop(player: ExoPlayer) {
        var consecutiveFailures = 0
        while (isActive) {
            val slide = withContext(Dispatchers.IO) { queue.next() }
            if (slide == null) {
                showMessage(getString(R.string.waiting))
                delay(EMPTY_RETRY_MS)
                continue
            }
            try {
                Log.d(TAG, "Showing $slide")
                show(slide, player)
                consecutiveFailures = 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Includes OutOfMemoryError from an oversized image: skip it rather than crash the wall.
                Log.w(TAG, "Skipping $slide", e)
                if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    showMessage(getString(R.string.waiting))
                    delay(ALL_FAILED_RETRY_MS)
                    consecutiveFailures = 0
                }
            }
        }
    }

    private suspend fun show(slide: Slide, player: ExoPlayer) {
        when (slide) {
            is Slide.Image -> showBitmap(withContext(Dispatchers.IO) { SlideRenderer.decodeImage(slide.path) }, slide.durationMs)
            is Slide.PdfPage -> showBitmap(
                withContext(Dispatchers.IO) { SlideRenderer.renderPdfPage(slide.path, slide.pageIndex) },
                slide.durationMs,
            )
            is Slide.Video -> playVideo(slide.path, player)
        }
    }

    private suspend fun showBitmap(bitmap: Bitmap, durationMs: Long) {
        message.visibility = View.GONE
        // Fade the new image in on the back view, then swap roles.
        imageBack.setImageBitmap(bitmap)
        imageBack.alpha = 0f
        imageBack.bringToFront()
        menu.bringToFront()
        imageBack.animate().alpha(1f).setDuration(CROSSFADE_MS).start()
        delay(CROSSFADE_MS)
        videoView.visibility = View.GONE
        imageFront.setImageDrawable(null)
        val previousFront = imageFront
        imageFront = imageBack
        imageBack = previousFront
        delay((durationMs - CROSSFADE_MS).coerceAtLeast(0))
    }

    private suspend fun playVideo(path: String, player: ExoPlayer) {
        message.visibility = View.GONE
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
        player.prepare()
        videoView.visibility = View.VISIBLE
        videoView.bringToFront()
        menu.bringToFront()
        try {
            player.play()
            // A file that never becomes ready or never ends must not freeze the wall.
            PlaybackLimits.withPlaybackTimeout(VIDEO_START_TIMEOUT_MS, "Video start") { awaitState(player, Player.STATE_READY) }
            val durationMs = player.duration.takeIf { it != C.TIME_UNSET }
            PlaybackLimits.withPlaybackTimeout(PlaybackLimits.videoTimeoutMs(durationMs), "Video") {
                awaitState(player, Player.STATE_ENDED)
            }
        } finally {
            player.stop()
            player.clearMediaItems()
        }
    }

    /** Suspends until the player reaches [state] or later; throws on a playback error. */
    private suspend fun awaitState(player: ExoPlayer, state: Int) {
        if (player.playbackState >= state) return
        var listener: Player.Listener? = null
        try {
            suspendCancellableCoroutine { cont ->
                listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState >= state && cont.isActive) cont.resume(Unit)
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        if (cont.isActive) cont.resumeWithException(error)
                    }
                }.also(player::addListener)
                if (player.playbackState >= state && cont.isActive) cont.resume(Unit)
            }
        } finally {
            listener?.let(player::removeListener)
        }
    }

    private fun showMessage(text: String) {
        imageFront.setImageDrawable(null)
        imageBack.setImageDrawable(null)
        videoView.visibility = View.GONE
        message.text = text
        message.visibility = View.VISIBLE
        message.bringToFront()
        menu.bringToFront()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    companion object {
        private const val TAG = "XcTvWall"
        const val SERVICE_ACCOUNT_ASSET = "service-account.json"
        private const val SYNC_INTERVAL_MS = 5 * 60_000L
        private const val UPDATE_INTERVAL_MS = 60 * 60_000L
        private const val VIDEO_START_TIMEOUT_MS = 15_000L
        private const val EMPTY_RETRY_MS = 5_000L
        private const val ALL_FAILED_RETRY_MS = 30_000L
        private const val MAX_CONSECUTIVE_FAILURES = 20
        private const val CROSSFADE_MS = 500L
        private const val MENU_TIMEOUT_MS = 15_000L
    }
}
