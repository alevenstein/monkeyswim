package com.monkeyswim.game

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), GameState.Listener {

    private lateinit var gameView: GameView
    private lateinit var scoreLabel: TextView
    private lateinit var levelLabel: TextView
    private lateinit var livesLabel: TextView
    private lateinit var hud: View
    private lateinit var gameOverOverlay: FrameLayout
    private lateinit var restartButton: Button
    private lateinit var watchAdButton: Button
    private lateinit var allLevelsCompleteOverlay: FrameLayout
    private lateinit var continueChallengeButton: Button
    private lateinit var restartFromCompleteButton: Button
    private lateinit var mechanicIntroOverlay: FrameLayout
    private lateinit var mechanicIntroTitle: TextView
    private lateinit var mechanicIntroBody: TextView
    private lateinit var mechanicIntroDismiss: Button
    private lateinit var helpButton: Button
    private lateinit var soundButton: Button
    private lateinit var baitButton: Button
    private lateinit var helpOverlay: FrameLayout
    private lateinit var helpCloseButton: Button
    private lateinit var splashOverlay: FrameLayout
    private lateinit var splashDifficultyGroup: RadioGroup
    private lateinit var splashStartButton: Button
    private lateinit var splashPrivacyLink: TextView
    private lateinit var debugBar: LinearLayout
    private lateinit var debugLevelSpinner: Spinner
    private lateinit var debugFruitButton: Button

    /** Suppresses the spinner's onItemSelected callback when we're updating the
     *  selection programmatically (e.g. on level-up via natural progression). */
    private var suppressDebugLevelChange = false

    /** Which mechanic intro is currently being shown — set when the overlay
     *  is displayed, cleared when dismissed. Used by the Got-It handler to
     *  know which "seen" flag to flip. */
    private var displayedMechanicIntro: GameState.MechanicIntro? = null

    /** True if showing the help overlay paused the game (so closing should resume). */
    private var helpPausedGame = false

    private lateinit var adMob: AdMobController
    private val soundEngine = SoundEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyImmersiveFlags()

        gameView = findViewById(R.id.gameView)
        scoreLabel = findViewById(R.id.scoreLabel)
        levelLabel = findViewById(R.id.levelLabel)
        livesLabel = findViewById(R.id.livesLabel)
        hud = findViewById(R.id.hud)
        gameOverOverlay = findViewById(R.id.gameOverOverlay)
        restartButton = findViewById(R.id.restartButton)
        watchAdButton = findViewById(R.id.watchAdButton)
        allLevelsCompleteOverlay = findViewById(R.id.allLevelsCompleteOverlay)
        continueChallengeButton = findViewById(R.id.continueChallengeButton)
        restartFromCompleteButton = findViewById(R.id.restartFromCompleteButton)
        mechanicIntroOverlay = findViewById(R.id.mechanicIntroOverlay)
        mechanicIntroTitle = findViewById(R.id.mechanicIntroTitle)
        mechanicIntroBody = findViewById(R.id.mechanicIntroBody)
        mechanicIntroDismiss = findViewById(R.id.mechanicIntroDismiss)
        helpButton = findViewById(R.id.helpButton)
        soundButton = findViewById(R.id.soundButton)
        baitButton = findViewById(R.id.baitButton)
        helpOverlay = findViewById(R.id.helpOverlay)
        helpCloseButton = findViewById(R.id.helpCloseButton)
        splashOverlay = findViewById(R.id.splashOverlay)
        splashDifficultyGroup = findViewById(R.id.splashDifficultyGroup)
        splashStartButton = findViewById(R.id.splashStartButton)
        splashPrivacyLink = findViewById(R.id.splashPrivacyLink)
        debugBar = findViewById(R.id.debugBar)
        debugLevelSpinner = findViewById(R.id.debugLevelSpinner)
        debugFruitButton = findViewById(R.id.debugFruitButton)
        setupDebugLevelSelector()

        splashPrivacyLink.paintFlags = splashPrivacyLink.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        splashPrivacyLink.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
        }
        splashStartButton.setOnClickListener {
            val multiplier = when (splashDifficultyGroup.checkedRadioButtonId) {
                R.id.difficultyEasy -> 0.75f
                R.id.difficultyHard -> 1.25f
                else -> 1.0f
            }
            val state = gameView.gameState()
            state.difficultyMultiplier = multiplier
            splashOverlay.visibility = View.GONE
            state.startGame()
        }

        // Push playable area below the HUD so the maze never sits under it.
        hud.post { gameView.hudHeightPx = hud.height.toFloat() }
        hud.viewTreeObserver.addOnGlobalLayoutListener {
            gameView.hudHeightPx = hud.height.toFloat()
        }

        gameView.listener = this

        restartButton.setOnClickListener {
            hideGameOver()
            gameView.gameState().reset()
        }
        continueChallengeButton.setOnClickListener {
            allLevelsCompleteOverlay.visibility = View.GONE
            gameView.gameState().acceptChallenge()
        }
        restartFromCompleteButton.setOnClickListener {
            allLevelsCompleteOverlay.visibility = View.GONE
            gameView.gameState().reset()
        }
        mechanicIntroDismiss.setOnClickListener {
            // Mark the currently-displayed mechanic as seen, hide the overlay,
            // and let the game advance to READY for this level.
            displayedMechanicIntro?.let { markMechanicIntroSeen(it) }
            displayedMechanicIntro = null
            mechanicIntroOverlay.visibility = View.GONE
            gameView.gameState().acknowledgeMechanicIntro()
        }
        soundButton.setOnClickListener {
            soundEngine.enabled = !soundEngine.enabled
            updateSoundButton()
        }
        baitButton.setOnClickListener {
            gameView.gameState().placeBait()
        }
        helpButton.setOnClickListener {
            // Pause the game on help-open if it was actively playing — closing
            // help will only resume in that case, so a manually-paused game
            // stays paused after the player closes help.
            val state = gameView.gameState()
            helpPausedGame = if (state.phase == GameState.Phase.PLAYING) {
                state.setPaused(true)
                true
            } else {
                false
            }
            helpOverlay.visibility = View.VISIBLE
        }
        helpCloseButton.setOnClickListener {
            helpOverlay.visibility = View.GONE
            if (helpPausedGame) {
                gameView.gameState().setPaused(false)
                helpPausedGame = false
            }
        }
        watchAdButton.setOnClickListener {
            watchAdButton.isEnabled = false
            adMob.showRewarded(
                onReward = {
                    gameView.gameState().grantBonusLives(3)
                    runOnUiThread {
                        hideGameOver()
                        watchAdButton.isEnabled = true
                    }
                },
                onUnavailable = {
                    runOnUiThread {
                        Toast.makeText(this, R.string.ad_failed, Toast.LENGTH_SHORT).show()
                        watchAdButton.isEnabled = true
                    }
                }
            )
        }

        adMob = AdMobController(this).also { it.init() }

        // Pre-synthesise SFX buffers on the main thread before any gameplay
        // starts. Build is fast enough not to delay onCreate noticeably.
        soundEngine.init(this)
        gameView.gameState().soundEngine = soundEngine
        updateSoundButton()

        // Restore the previously persisted game (if any). A saved game skips the
        // splash entirely; a saved GAME_OVER restores into the same overlay so
        // the player can still choose watch-ad or restart.
        SaveGame.load(this)?.let { snapshot ->
            splashOverlay.visibility = View.GONE
            gameView.gameState().restore(snapshot)
        }
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveFlags()
    }

    override fun onPause() {
        super.onPause()
        val snapshot = gameView.gameState().snapshot()
        if (SaveGame.shouldPersist(snapshot)) {
            SaveGame.save(this, snapshot)
        }
    }

    override fun onDestroy() {
        soundEngine.release()
        super.onDestroy()
    }

    /**
     * Sync the sound button's text + tint to the current SoundEngine state.
     * Same visual contract as Brick Basher's Canvas-drawn button: 🔊 in white
     * when enabled, 🔇 in muted-gray (#546E7A) when disabled.
     */
    private fun updateSoundButton() {
        if (soundEngine.enabled) {
            soundButton.text = getString(R.string.sound_on)
            soundButton.setTextColor(android.graphics.Color.WHITE)
        } else {
            soundButton.text = getString(R.string.sound_off)
            soundButton.setTextColor(android.graphics.Color.parseColor("#546E7A"))
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveFlags()
    }

    @Suppress("DEPRECATION")
    private fun applyImmersiveFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyImmersiveFlags()
    }

    // ---------- GameState.Listener ----------

    override fun onScoreChanged(score: Int) {
        runOnUiThread { scoreLabel.text = getString(R.string.score_label, score) }
    }

    override fun onLivesChanged(lives: Int) {
        runOnUiThread { livesLabel.text = getString(R.string.lives_label, lives.coerceAtLeast(0)) }
    }

    override fun onLevelChanged(level: Int) {
        runOnUiThread {
            levelLabel.text = getString(R.string.level_label, level)
            // Keep the debug-only level spinner in sync when the game advances
            // naturally so it always reflects the active level.
            if (BuildConfig.DEBUG && ::debugLevelSpinner.isInitialized) {
                val targetIdx = (level - 1).coerceIn(0, Levels.LEVEL_COUNT - 1)
                if (debugLevelSpinner.selectedItemPosition != targetIdx) {
                    suppressDebugLevelChange = true
                    debugLevelSpinner.setSelection(targetIdx)
                }
            }
        }
    }

    private fun setupDebugLevelSelector() {
        if (!BuildConfig.DEBUG) {
            debugBar.visibility = View.GONE
            return
        }
        debugBar.visibility = View.VISIBLE
        val levels = (1..Levels.LEVEL_COUNT).map { it.toString() }
        debugLevelSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            levels,
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        debugLevelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (suppressDebugLevelChange) {
                    suppressDebugLevelChange = false
                    return
                }
                gameView.gameState().jumpToLevel(position + 1)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        debugFruitButton.setOnClickListener {
            gameView.gameState().debugActivatePowerPellet()
        }
    }

    override fun onGameOver() {
        runOnUiThread { showGameOver() }
    }

    override fun onAllLevelsComplete() {
        runOnUiThread { allLevelsCompleteOverlay.visibility = View.VISIBLE }
    }

    override fun onBaitChargesChanged(charges: Int) {
        runOnUiThread {
            if (charges > 0) {
                baitButton.text = getString(R.string.bait_button, charges)
                baitButton.visibility = View.VISIBLE
            } else {
                baitButton.visibility = View.GONE
            }
        }
    }

    override fun onMechanicIntro(mechanic: GameState.MechanicIntro) {
        runOnUiThread {
            if (hasSeenMechanicIntro(mechanic)) {
                // Already shown in a previous session — skip the overlay and
                // resume play immediately so the player isn't re-interrupted.
                gameView.gameState().acknowledgeMechanicIntro()
                return@runOnUiThread
            }
            displayedMechanicIntro = mechanic
            val (titleRes, bodyRes) = when (mechanic) {
                GameState.MechanicIntro.CURRENTS ->
                    R.string.intro_currents_title to R.string.intro_currents_body
                GameState.MechanicIntro.TIDE ->
                    R.string.intro_tide_title to R.string.intro_tide_body
                GameState.MechanicIntro.LILY_PADS ->
                    R.string.intro_lilypads_title to R.string.intro_lilypads_body
            }
            mechanicIntroTitle.setText(titleRes)
            mechanicIntroBody.setText(bodyRes)
            mechanicIntroOverlay.visibility = View.VISIBLE
        }
    }

    private fun mechanicSeenKey(mechanic: GameState.MechanicIntro): String =
        "seen_intro_" + mechanic.name.lowercase()

    private fun hasSeenMechanicIntro(mechanic: GameState.MechanicIntro): Boolean =
        getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)
            .getBoolean(mechanicSeenKey(mechanic), false)

    private fun markMechanicIntroSeen(mechanic: GameState.MechanicIntro) {
        getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(mechanicSeenKey(mechanic), true).apply()
    }

    private fun showGameOver() {
        gameOverOverlay.visibility = View.VISIBLE
        watchAdButton.isEnabled = true
    }

    private fun hideGameOver() {
        gameOverOverlay.visibility = View.GONE
    }

    companion object {
        private const val PRIVACY_POLICY_URL = "https://lionstone.dev/privacy/"
        // Same SharedPreferences file SoundEngine uses for its mute state, so
        // the player's tutorial-seen flags and audio toggle share one bag.
        private const val SETTINGS_FILE = "monkeyswim_settings"
    }
}
