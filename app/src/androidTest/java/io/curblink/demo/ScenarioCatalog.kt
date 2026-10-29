package io.curblink.demo

import android.content.res.AssetManager
import org.json.JSONObject

object ScenarioCatalog {
    fun load(assets: AssetManager): List<JourneyScenario> = listOf("pickup", "parking", "short_stop", "meal", "river", "access").map { name ->
        val json = JSONObject(assets.open("scenarios/$name.json").bufferedReader().use { it.readText() })
        require(json.getInt("version") == 1)
        val stages = json.getJSONArray("stages")
        JourneyScenario(json.getString("id"), json.getString("label"), (0 until stages.length()).map { i ->
            val stage = stages.getJSONObject(i)
            val points = stage.optJSONArray("points")
            JourneyStage(stage.getString("id"), stage.getString("label"),
                if (points == null) emptyList() else (0 until points.length()).map { j ->
                    val point = points.getJSONArray(j); WorldPoint(point.getDouble(0), point.getDouble(1))
                }, stage.getDouble("speed"), stage.getDouble("wait"))
        })
    }
}
