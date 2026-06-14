package com.example.tv_caller_app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.example.tv_caller_app.R
import com.example.tv_caller_app.model.Contact
import com.example.tv_caller_app.model.Profile

sealed class ContactListItem {
    data class SectionHeader(val letter: Char) : ContactListItem()
    data class TextHeader(@StringRes val titleRes: Int) : ContactListItem()
    data class ContactEntry(val contact: Contact) : ContactListItem()
    data class SearchResultEntry(val profile: Profile) : ContactListItem()
}

class ContactListAdapter(
    private val items: List<ContactListItem>,
    private val onContactClick: (Contact) -> Unit,
    private val onSearchResultClick: ((Profile) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_CONTACT = 1
        const val VIEW_TYPE_SEARCH_RESULT = 2
        const val VIEW_TYPE_TEXT_HEADER = 3

        fun buildGroupedList(contacts: List<Contact>): List<ContactListItem> {
            val sorted = contacts.sortedBy { it.username.lowercase() }
            val items = mutableListOf<ContactListItem>()
            var lastLetter: Char? = null

            for (contact in sorted) {
                val firstChar = contact.username.firstOrNull()?.uppercaseChar() ?: '?'
                if (firstChar != lastLetter) {
                    items.add(ContactListItem.SectionHeader(firstChar))
                    lastLetter = firstChar
                }
                items.add(ContactListItem.ContactEntry(contact))
            }
            return items
        }

        fun buildCombinedList(
            contacts: List<Contact>,
            searchResults: List<Profile>,
            hasQuery: Boolean
        ): List<ContactListItem> {
            val items = mutableListOf<ContactListItem>()

            if (contacts.isNotEmpty()) {
                if (hasQuery && searchResults.isNotEmpty()) {
                    items.add(ContactListItem.TextHeader(R.string.section_your_contacts))
                }
                val sorted = contacts.sortedBy { it.username.lowercase() }
                var lastLetter: Char? = null
                for (contact in sorted) {
                    val firstChar = contact.username.firstOrNull()?.uppercaseChar() ?: '?'
                    if (firstChar != lastLetter) {
                        items.add(ContactListItem.SectionHeader(firstChar))
                        lastLetter = firstChar
                    }
                    items.add(ContactListItem.ContactEntry(contact))
                }
            }

            if (searchResults.isNotEmpty()) {
                items.add(ContactListItem.TextHeader(R.string.section_other_users))
                for (profile in searchResults) {
                    items.add(ContactListItem.SearchResultEntry(profile))
                }
            }

            return items
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ContactListItem.SectionHeader -> VIEW_TYPE_HEADER
            is ContactListItem.TextHeader -> VIEW_TYPE_TEXT_HEADER
            is ContactListItem.ContactEntry -> VIEW_TYPE_CONTACT
            is ContactListItem.SearchResultEntry -> VIEW_TYPE_SEARCH_RESULT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_contact_section_header, parent, false)
                HeaderViewHolder(view)
            }
            VIEW_TYPE_TEXT_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_contact_section_header, parent, false)
                TextHeaderViewHolder(view)
            }
            VIEW_TYPE_SEARCH_RESULT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_search_result, parent, false)
                SearchResultViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_contact_list, parent, false)
                ContactViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ContactListItem.SectionHeader -> {
                (holder as HeaderViewHolder).bind(item.letter)
            }
            is ContactListItem.TextHeader -> {
                (holder as TextHeaderViewHolder).bind(item.titleRes)
            }
            is ContactListItem.ContactEntry -> {
                (holder as ContactViewHolder).bind(item.contact, onContactClick)
            }
            is ContactListItem.SearchResultEntry -> {
                (holder as SearchResultViewHolder).bind(item.profile, onSearchResultClick)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val sectionLetter: TextView = itemView.findViewById(R.id.section_letter)

        fun bind(letter: Char) {
            sectionLetter.text = letter.toString()
        }
    }

    class TextHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val sectionLetter: TextView = itemView.findViewById(R.id.section_letter)

        fun bind(@StringRes titleRes: Int) {
            sectionLetter.text = itemView.context.getString(titleRes)
        }
    }

    class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val contactName: TextView = itemView.findViewById(R.id.contact_name)

        fun bind(contact: Contact, onClick: (Contact) -> Unit) {
            contactName.text = if (!contact.nickname.isNullOrBlank()) {
                itemView.context.getString(R.string.contact_name_with_nickname, contact.username, contact.nickname)
            } else {
                contact.username
            }
            itemView.setOnClickListener { onClick(contact) }
        }
    }

    class SearchResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val username: TextView = itemView.findViewById(R.id.search_result_username)
        private val contactId: TextView = itemView.findViewById(R.id.search_result_contact_id)

        fun bind(profile: Profile, onClick: ((Profile) -> Unit)?) {
            username.text = profile.username ?: itemView.context.getString(R.string.unknown_contact)
            contactId.text = itemView.context.getString(R.string.contact_id_format, profile.contactId)
            itemView.setOnClickListener { onClick?.invoke(profile) }
        }
    }
}
