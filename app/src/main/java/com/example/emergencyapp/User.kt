package com.example.emergencyapp

data class User(
    var name: String? = null,
    var phone: String? = null,
    var id: String? = null // Optional - used for updating/deleting
)
