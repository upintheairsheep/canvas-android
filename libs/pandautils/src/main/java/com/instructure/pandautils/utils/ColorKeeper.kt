/*
 * Copyright (C) 2017 - present Instructure, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */
package com.instructure.pandautils.utils

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.instructure.canvasapi2.StatusCallback
import com.instructure.canvasapi2.managers.UserManager
import com.instructure.canvasapi2.models.CanvasColor
import com.instructure.canvasapi2.models.CanvasContext
import com.instructure.canvasapi2.models.Course
import com.instructure.canvasapi2.models.Group
import com.instructure.canvasapi2.utils.*
import com.instructure.canvasapi2.utils.weave.resumeSafely
import com.instructure.pandautils.R
import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.Call
import retrofit2.Response
import java.util.*

private const val PREFERENCE_FILE_NAME = "color_keeper_prefs"

@Suppress("unused")
object ColorKeeper : PrefManager(PREFERENCE_FILE_NAME) {

    /** The default color **/
    @JvmStatic var defaultColor: Int = 0

    /** The currently cached colors **/
    var cachedColors: Map<String, Int> by NonNullGsonPref(HashMap())

    /** Whether or not colors have been synced from the API before **/
    var hasPreviouslySynced by BooleanPref()

    /** Gets the color associated with the given key if it exists, otherwise gets the [defaultColor] **/
    @JvmStatic fun getColorOrDefault(keyName: String)
            = cachedColors[keyName] ?: defaultColor

    /** Gets the color associated with the given [CanvasContext] if it exists, otherwise generates a new color **/
    @JvmStatic @JvmOverloads
    fun getOrGenerateColor(canvasContext: CanvasContext?, @ColorInt defaultColor: Int = ThemePrefs.primaryColor): Int {
        return when (canvasContext) {
            is Course -> cachedColors.getOrElse(canvasContext.contextId) { generateColor(canvasContext) }
            is Group -> {
                cachedColors.getOrElse(canvasContext.contextId) {
                    val courseContextId = CanvasContext.makeContextId(CanvasContext.Type.COURSE, canvasContext.courseId)
                    cachedColors.getOrElse(courseContextId) { ContextCompat.getColor(ContextKeeper.appContext, R.color.backgroundDark) }
                }
            }
            else -> defaultColor
        }
    }

    fun colorFromCourseId(courseId: Long) = getOrGenerateColor(CanvasContext.emptyCourseContext(courseId))

    /** Gets the color associated with the given contextId if it exists, otherwise generates a new color **/
    @JvmStatic fun getOrGenerateColor(contextId: String)
            = cachedColors.getOrElse(contextId) { generateColor(Course()) }

    /** Adds all colors in the given [CanvasColor] object to the color cache **/
    @JvmStatic fun addToCache(canvasColor: CanvasColor?) {
        canvasColor?.colors?.let { colors -> cachedColors += colors.mapValues { parseColor(it.value) } }
    }

    /** Associates a new color with a contextId **/
    @JvmStatic fun addToCache(contextId: String, color: Int) {
        cachedColors += contextId to color
    }

    /** Associates a new color with a contextId **/
    @JvmStatic fun addToCache(contextId: String, colorCode: String) {
        val color = parseColor(colorCode)
        cachedColors += contextId to color
    }

    /**
     * Generates a colored drawable
     * @param context An Android Context
     * @param resource The resource ID of the drawable to be colored
     * @param color The color that will be used to tint the drawable
     * @return The colored drawable
     */
    @JvmStatic fun getColoredDrawable(context: Context, @DrawableRes resource: Int, @ColorInt color: Int): Drawable
            = ContextCompat
            .getDrawable(context, resource)!!
            .mutate()
            .apply { setColorFilter(color, PorterDuff.Mode.SRC_ATOP) }

    /**
     * Generates a colored drawable
     * @param context An Android Context
     * @param resource The resource ID of the drawable to be colored
     * @param canvasContext A CanvasContext whose associated color will be used to tint the drawable
     * @return The colored drawable
     */
    @JvmStatic fun getColoredDrawable(context: Context, @DrawableRes resource: Int, canvasContext: CanvasContext?)
            = getColoredDrawable(context, resource, getOrGenerateColor(canvasContext))

    /**
     * Returns a color string in hex format with no alpha
     * @param color a valid color in the form of an Integer
     * @return A hex string form of the color with no alpha
     */
    @JvmStatic fun getColorStringFromInt(color: Int, prefixWithHashTag: Boolean): String {
        var colorStr = Integer.toHexString(color)
        colorStr = colorStr.substring(colorStr.length - 6)
        return if (prefixWithHashTag && !colorStr.startsWith("#")) {
            "#$colorStr"
        } else {
            colorStr
        }
    }

    /**
     * Attempts to parse a color from a hex string.
     * @param hexColor A hex color string. May optionally be prefixed with '#'
     * @return The parsed color, or [defaultColor] if the string could not be parsed
     */
    private fun parseColor(hexColor: String): Int = try {
        Color.parseColor("#${hexColor.trimMargin("#")}")
    } catch (e: IllegalArgumentException) {
        defaultColor
    }

    /**
     * Generates a generic color based on the canvas context id, this will produce consistent colors for a given course
     * @param canvasContext a valid canvas context
     * @return the generated colors
     */
    private fun generateColor(canvasContext: CanvasContext): Int {
        if (canvasContext.type == CanvasContext.Type.USER || canvasContext.name.isNullOrBlank()) {
            return defaultColor
        }

        val colorRes = when (Math.abs((canvasContext.name?.hashCode() ?: "Null Name".hashCode()) % 13)) {
            0 -> R.color.colorCottonCandy
            1 -> R.color.colorBarbie
            2 -> R.color.colorBarneyPurple
            3 -> R.color.colorEggplant
            4 -> R.color.colorUltramarine
            5 -> R.color.colorOcean11
            6 -> R.color.colorCyan
            7 -> R.color.colorAquaMarine
            8 -> R.color.colorEmeraldGreen
            9 -> R.color.colorFreshCutLawn
            10 -> R.color.colorChartreuse
            11 -> R.color.colorSunFlower
            12 -> R.color.colorTangerine
            13 -> R.color.colorBloodOrange
            else -> R.color.colorSriracha
        }

        val color = ContextCompat.getColor(ContextKeeper.appContext, colorRes)
        addToCache(canvasContext.contextId, color)
        return color
    }

    override fun onClearPrefs() {}
}

@Suppress("unused", "DEPRECATION")
object ColorApiHelper {

    const val K5_DEFAULT_COLOR = "#394B58"

    /**
     * Returns a color via a callback, if no color is in the cache it will pull from canvas via the api
     * If nothing is found in the cache or api a color is generated
     * @param canvasContext canvasContext
     * @param gotColor
     */
    @JvmStatic fun getColor(canvasContext: CanvasContext, gotColor: (color: Int) -> Unit) {
        if (canvasContext.contextId in ColorKeeper.cachedColors) {
            gotColor(ColorKeeper.cachedColors[canvasContext.contextId]!!)
        } else {
            performSync { gotColor(ColorKeeper.getOrGenerateColor(canvasContext)) }
        }
    }

    /**
     * Sets a new color to the api and caches the result
     * @param canvasContext canvasContext
     * @param newColor the new color to set
     */
    fun setNewColor(canvasContext: CanvasContext, newColor: Int, onColorSet: (color: Int, success: Boolean) -> Unit) {
        UserManager.setColors(object : StatusCallback<CanvasColor>() {
            override fun onResponse(response: Response<CanvasColor>, linkHeaders: LinkHeaders, type: ApiType) {
                if (type.isAPI) {
                    ColorKeeper.addToCache(canvasContext.contextId, newColor)
                    onColorSet(newColor, true)
                }
            }

            override fun onFail(call: Call<CanvasColor>?, error: Throwable, response: Response<*>?) = onColorSet(newColor, false)
        }, canvasContext.contextId, newColor)
    }

    /**
     * Attempts to pull and cache colors from the API.
     */
    private fun performSync(onSynced: (success: Boolean) -> Unit) {
        UserManager.getColors(object : StatusCallback<CanvasColor>() {
            override fun onResponse(response: Response<CanvasColor>, linkHeaders: LinkHeaders, type: ApiType) {
                if (type == ApiType.API) {
                    ColorKeeper.addToCache(response.body())
                    onSynced(true)
                }
            }

            override fun onFail(call: Call<CanvasColor>?, error: Throwable, response: Response<*>?) = onSynced(false)
        }, true)
    }

    suspend fun awaitSync(): Boolean = suspendCancellableCoroutine { cr -> performSync { cr.resumeSafely(it) } }
}
