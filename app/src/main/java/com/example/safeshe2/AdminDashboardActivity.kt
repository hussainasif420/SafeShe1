package com.example.safeshe2

import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.SearchView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

class AdminDashboardActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchView: SearchView
    private lateinit var btnRefresh: ImageButton
    private lateinit var userAdapter: UserAdapter
    private val userList = mutableListOf<User>()
    private val db = FirebaseFirestore.getInstance()
    private var userListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        recyclerView = findViewById(R.id.recyclerViewUsers)
        searchView = findViewById(R.id.searchView)
        btnRefresh = findViewById(R.id.btnRefresh)

        setupRecyclerView()
        setupSearchView()
        setupRefreshButton()
        loadUsers()
    }

    private fun setupRecyclerView() {
        userAdapter = UserAdapter(userList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = userAdapter
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                userAdapter.filter.filter(newText)
                return false
            }
        })
    }

    private fun setupRefreshButton() {
        btnRefresh.setOnClickListener {
            loadUsers()
            Toast.makeText(this, "Refreshing user data...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadUsers() {
        userListener?.remove() // Remove previous listener if exists
        
        userListener = db.collection("users")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(this, "Error loading users: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    userList.clear()
                    for (document in snapshot.documents) {
                        try {
                            val user = User(
                                userId = document.id,
                                email = document.getString("email") ?: "",
                                firstName = document.getString("firstName") ?: "",
                                lastName = document.getString("lastName") ?: "",
                                cnic = document.getString("cnic") ?: "",
                                dob = document.getString("dob") ?: "",
                                createdAt = convertToDate(document.get("createdAt")),
                                lastLogin = convertToDate(document.get("lastLogin")),
                                emergencyContacts = document.get("emergencyContacts") as? List<String> ?: emptyList(),
                                locationEnabled = document.getBoolean("locationEnabled") ?: false,
                                notificationEnabled = document.getBoolean("notificationEnabled") ?: false
                            )
                            userList.add(user)
                        } catch (e: Exception) {
                            Log.e("AdminDashboard", "Error parsing user document: ${e.message}")
                        }
                    }
                    userAdapter.updateList(userList)
                }
            }
    }

    private fun convertToDate(value: Any?): Date? {
        return when (value) {
            is Timestamp -> value.toDate()
            is Long -> Date(value)
            else -> null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        userListener?.remove()
    }
}

data class User(
    var userId: String = "",
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val cnic: String = "",
    val dob: String = "",
    val createdAt: Date? = null,
    val lastLogin: Date? = null,
    val emergencyContacts: List<String> = emptyList(),
    val locationEnabled: Boolean = false,
    val notificationEnabled: Boolean = false
) 