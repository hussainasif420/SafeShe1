package com.example.safeshe2

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class UserAdapter(private var userList: List<User>) : RecyclerView.Adapter<UserAdapter.UserViewHolder>(), Filterable {
    private var filteredList: List<User> = userList
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.textViewName)
        val emailTextView: TextView = itemView.findViewById(R.id.textViewEmail)
        val cnicTextView: TextView = itemView.findViewById(R.id.textViewCnic)
        val dobTextView: TextView = itemView.findViewById(R.id.textViewDob)
        val firstNameTextView: TextView = itemView.findViewById(R.id.textViewUserId)
        val createdAtTextView: TextView = itemView.findViewById(R.id.textViewCreatedAt)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = filteredList[position]
        holder.nameTextView.text = "Name: ${user.firstName} ${user.lastName}"
        holder.emailTextView.text = "Email: ${user.email}"
        holder.cnicTextView.text = "CNIC: ${user.cnic}"
        holder.firstNameTextView.text = "First Name: ${user.firstName}"
        holder.dobTextView.text = "Date of Birth: ${user.dob}"
        val createdAt = user.createdAt?.let { dateFormat.format(it) } ?: "Not available"
        holder.createdAtTextView.text = "Created: $createdAt"
    }

    override fun getItemCount() = filteredList.size

    fun updateList(newList: List<User>) {
        userList = newList
        filteredList = newList
        notifyDataSetChanged()
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filteredResults = if (constraint.isNullOrEmpty()) {
                    userList
                } else {
                    userList.filter {
                        it.firstName.contains(constraint, ignoreCase = true) ||
                        it.lastName.contains(constraint, ignoreCase = true) ||
                        it.email.contains(constraint, ignoreCase = true) ||
                        it.cnic.contains(constraint, ignoreCase = true)
                    }
                }
                return FilterResults().apply { values = filteredResults }
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredList = results?.values as? List<User> ?: emptyList()
                notifyDataSetChanged()
            }
        }
    }
} 