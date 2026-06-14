package com.example.tv_caller_app.ui.util

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.tv_caller_app.R

object AvatarHelper {

    private val presetAvatarMap = mapOf(
        "blue" to R.drawable.avatar_blue,
        "green" to R.drawable.avatar_green,
        "red" to R.drawable.avatar_red,
        "purple" to R.drawable.avatar_purple,
        "orange" to R.drawable.avatar_orange,
        "teal" to R.drawable.avatar_teal
    )

    fun loadAvatar(imageView: ImageView, avatarUrl: String?, fallbackDrawable: Int = R.drawable.avatar_circle) {
        when {
            avatarUrl.isNullOrBlank() -> {
                imageView.setImageResource(0)
                imageView.setBackgroundResource(fallbackDrawable)
            }
            avatarUrl.startsWith("preset:") -> {
                val avatarName = avatarUrl.removePrefix("preset:")
                val drawableRes = presetAvatarMap[avatarName]
                if (drawableRes != null) {
                    imageView.setBackgroundResource(0)
                    imageView.setImageResource(drawableRes)
                } else {
                    imageView.setImageResource(0)
                    imageView.setBackgroundResource(fallbackDrawable)
                }
            }
            else -> {
                imageView.setBackgroundResource(0)
                Glide.with(imageView.context)
                    .load(avatarUrl)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .circleCrop()
                    .placeholder(fallbackDrawable)
                    .into(imageView)
            }
        }
    }
}
