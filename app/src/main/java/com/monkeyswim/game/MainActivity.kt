package com.monkeyswim.game

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.FrameLayout
import android.widget.RadioGroup
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
    private lateinit var helpButton: Button
    private lateinit var helpOverlay: FrameLayout
    private lateinit var helpCloseButton: Button
    private lateinit var splashOverlay: FrameLayout
    private lateinit var splashDifficultyGroup: RadioGroup
    private lateinit var splashStartButton: Button
    private lateinit var splashPrivacyLink: TextView

    /** True if showing the help overlay paused the game (so closing should resume). */
    private var helpPausedGame = false

    private lateinit var adMob: AdMobController

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
        helpButton = findViewById(R.id.helpButton)
        helpOverlay = findViewById(R.id.helpOverlay)
        helpCloseButton = findViewById(R.id.helpCloseButton)
        splashOverlay = findViewById(R.id.splashOverlay)
        splashDifficultyGroup = findViewById(R.id.splashDifficultyGroup)
        splashStartButton = findViewById(R.id.splashStartButton)
        splashPrivacyLink = findViewById(R.id.splashPrivacyLink)

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
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveFlags()
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
        runOnUiThread { levelLabel.text = getString(R.string.level_label, level) }
    }

    override fun onGameOver() {
        runOnUiThread { showGameOver() }
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
    }
}
