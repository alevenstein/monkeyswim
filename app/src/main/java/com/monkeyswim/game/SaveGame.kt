package com.monkeyswim.game

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists a single in-progress game to SharedPreferences. Saved on activity
 * pause, restored on activity create. A snapshot in `GAME_OVER` is kept so the
 * player can re-open the app and still be offered the watch-ad / restart
 * choice instead of being silently restarted.
 */
object SaveGame {

    private const val PREFS_NAME = "MonkeySwimSave"
    private const val KEY_PAYLOAD = "payload"
    // Bumped when the snapshot schema changes incompatibly. Older payloads are
    // ignored on load so we never feed a stale schema into Maze/GameState.
    private const val SCHEMA_VERSION = 1

    data class Snapshot(
        val level: Int,
        val score: Int,
        val lives: Int,
        val phase: GameState.Phase,
        val difficultyMultiplier: Float,
        val mazeLayout: List<String>,
        val fruitMap: Map<Pair<Int, Int>, FruitRenderer.FruitType>,
        val challengeMode: Boolean = false,
    )

    /**
     * AWAITING_START is the pre-splash state; saving it would skip the splash
     * forever on next launch even though the player never started a game.
     */
    fun shouldPersist(snapshot: Snapshot): Boolean =
        snapshot.phase != GameState.Phase.AWAITING_START

    fun save(context: Context, snapshot: Snapshot) {
        val json = JSONObject().apply {
            put("v", SCHEMA_VERSION)
            put("level", snapshot.level)
            put("score", snapshot.score)
            put("lives", snapshot.lives)
            put("phase", snapshot.phase.name)
            put("difficulty", snapshot.difficultyMultiplier.toDouble())
            put("layout", JSONArray(snapshot.mazeLayout))
            val fruits = JSONArray()
            snapshot.fruitMap.forEach { (pos, fruit) ->
                fruits.put(JSONObject().apply {
                    put("c", pos.first)
                    put("r", pos.second)
                    put("f", fruit.name)
                })
            }
            put("fruits", fruits)
            put("challenge", snapshot.challengeMode)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PAYLOAD, json.toString())
            .apply()
    }

    fun load(context: Context): Snapshot? {
        val payload = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PAYLOAD, null) ?: return null
        return try {
            val json = JSONObject(payload)
            if (json.optInt("v", 0) != SCHEMA_VERSION) return null
            val layoutArr = json.getJSONArray("layout")
            val layout = (0 until layoutArr.length()).map { layoutArr.getString(it) }
            val fruitsArr = json.getJSONArray("fruits")
            val fruitMap = mutableMapOf<Pair<Int, Int>, FruitRenderer.FruitType>()
            for (i in 0 until fruitsArr.length()) {
                val obj = fruitsArr.getJSONObject(i)
                fruitMap[obj.getInt("c") to obj.getInt("r")] =
                    FruitRenderer.FruitType.valueOf(obj.getString("f"))
            }
            Snapshot(
                level = json.getInt("level"),
                score = json.getInt("score"),
                lives = json.getInt("lives"),
                phase = GameState.Phase.valueOf(json.getString("phase")),
                difficultyMultiplier = json.getDouble("difficulty").toFloat(),
                mazeLayout = layout,
                fruitMap = fruitMap,
                // Pre-challenge-mode saves omit this key; default to false so
                // an in-progress first run-through restores normally.
                challengeMode = json.optBoolean("challenge", false),
            )
        } catch (_: Exception) {
            // Malformed payload (corrupted prefs, schema drift slipping past the
            // version check, etc.) — drop it rather than crash on launch.
            null
        }
    }
}
