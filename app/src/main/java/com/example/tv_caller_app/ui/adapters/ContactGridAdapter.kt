package com.example.tv_caller_app.ui.adapters

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.tv_caller_app.R
import com.example.tv_caller_app.model.Contact
import com.example.tv_caller_app.ui.util.AvatarHelper
import androidx.core.graphics.toColorInt

class ContactGridAdapter(
    private var contacts: List<Contact>,
    private val cardType: CardType = CardType.QUICK_DIAL,
    private val onContactClick: (Contact) -> Unit
) : RecyclerView.Adapter<ContactGridAdapter.ContactViewHolder>() {

    enum class CardType {
        QUICK_DIAL,
        FAVORITE
    }

    fun updateContacts(newContacts: List<Contact>) {
        val diffResult = DiffUtil.calculateDiff(ContactDiffCallback(contacts, newContacts))
        contacts = newContacts
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val density = parent.context.resources.displayMetrics.density

        val cardLayout = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                val m = (4 * density).toInt()
                setMargins(m, m, m, m)
            }

            val bgRes = if (cardType == CardType.QUICK_DIAL) {
                R.drawable.contact_card_quickdial
            } else {
                R.drawable.contact_card_favorite
            }
            setBackgroundResource(bgRes)
            gravity = Gravity.CENTER_HORIZONTAL
            val padH = (10 * density).toInt()
            val padV = (20 * density).toInt()
            setPadding(padH, padV, padH, padV)

            isFocusable = true
            isFocusableInTouchMode = true
        }

        val avatarSize = (50 * density).toInt()
        val avatarContainer = FrameLayout(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (4 * density).toInt()
            }
        }

        val avatarBg = android.view.View(parent.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundResource(R.drawable.avatar_circle)
            tag = "avatar_bg"
        }

        val avatarImage = ImageView(parent.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            tag = "avatar_image"
        }

        val avatarInitial = TextView(parent.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            tag = "avatar_initial"
        }

        avatarContainer.addView(avatarBg)
        avatarContainer.addView(avatarImage)
        avatarContainer.addView(avatarInitial)
        cardLayout.addView(avatarContainer)

        val nameText = TextView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            textSize = 13f
            setTextColor("#212121".toColorInt())
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            tag = "contact_name"
        }
        cardLayout.addView(nameText)

        val subtitleText = TextView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            textSize = 11f
            setTextColor("#616161".toColorInt())
            gravity = Gravity.CENTER
            maxLines = 1
            tag = "contact_subtitle"
        }
        cardLayout.addView(subtitleText)

        return ContactViewHolder(cardLayout)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = contacts[position]
        holder.bind(contact, onContactClick)
    }

    override fun getItemCount(): Int = contacts.size

    class ContactViewHolder(private val layout: LinearLayout) : RecyclerView.ViewHolder(layout) {
        fun bind(contact: Contact, onClick: (Contact) -> Unit) {
            val nameView = layout.findViewWithTag<TextView>("contact_name")
            val subtitleView = layout.findViewWithTag<TextView>("contact_subtitle")
            val avatarInitial = layout.findViewWithTag<TextView>("avatar_initial")
            val avatarImage = layout.findViewWithTag<ImageView>("avatar_image")
            val avatarBg = layout.findViewWithTag<android.view.View>("avatar_bg")

            nameView?.text = contact.username
            subtitleView?.text = contact.nickname ?: ""

            if (!contact.avatarUrl.isNullOrBlank()) {
                avatarInitial?.visibility = android.view.View.GONE
                avatarBg?.visibility = android.view.View.GONE
                avatarImage?.visibility = android.view.View.VISIBLE
                avatarImage?.let { AvatarHelper.loadAvatar(it, contact.avatarUrl) }
            } else {
                avatarImage?.visibility = android.view.View.GONE
                avatarBg?.visibility = android.view.View.VISIBLE
                avatarInitial?.visibility = android.view.View.VISIBLE
                avatarInitial?.text = contact.username.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            }

            layout.setOnClickListener { onClick(contact) }
        }
    }

    private class ContactDiffCallback(
        private val oldList: List<Contact>,
        private val newList: List<Contact>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size
        override fun areItemsTheSame(oldPos: Int, newPos: Int) = oldList[oldPos].id == newList[newPos].id
        override fun areContentsTheSame(oldPos: Int, newPos: Int) = oldList[oldPos] == newList[newPos]
    }
}
