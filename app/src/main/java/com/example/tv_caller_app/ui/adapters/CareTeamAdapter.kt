package com.example.tv_caller_app.ui.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.tv_caller_app.R
import com.example.tv_caller_app.model.CareTeamMember

/** The patient's care team, one row per person. */
class CareTeamAdapter(
    private val members: List<CareTeamMember>
) : RecyclerView.Adapter<CareTeamAdapter.MemberViewHolder>() {

    override fun getItemCount(): Int = members.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder =
        MemberViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_care_team, parent, false)
        )

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        holder.bind(members[position])
    }

    class MemberViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val initials: TextView = view.findViewById(R.id.txt_member_initials)
        private val name: TextView = view.findViewById(R.id.txt_member_name)
        private val role: TextView = view.findViewById(R.id.txt_member_role)
        private val phone: TextView = view.findViewById(R.id.txt_member_phone)

        fun bind(member: CareTeamMember) {
            val context = itemView.context

            initials.text = initialsOf(member.fullName)
            name.text = member.fullName

            role.setText(
                if (member.relationshipType == "Relative") {
                    R.string.care_team_role_relative
                } else {
                    R.string.care_team_role_personnel
                }
            )

            phone.text = member.phoneNumber.orEmpty()
            phone.visibility = if (member.phoneNumber.isNullOrBlank()) View.GONE else View.VISIBLE

            // ic_call is authored white for the call buttons and notifications, so
            // on this white card it has to be tinted or it disappears. Set through
            // the compat helper because drawableTint is API 23 and this app supports 21.
            TextViewCompat.setCompoundDrawableTintList(
                phone,
                ColorStateList.valueOf(ContextCompat.getColor(context, R.color.vk_gray_700))
            )
        }

        /**
         * First letters of the first and last name — "Nora Nilsen" becomes "NN".
         * Neither system stores a photo for personnel, so this stands in for one.
         */
        private fun initialsOf(fullName: String): String {
            val parts = fullName.trim().split(" ").filter { it.isNotBlank() }

            return when {
                parts.isEmpty() -> "?"
                parts.size == 1 -> parts[0].take(1).uppercase()
                else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
            }
        }
    }
}
