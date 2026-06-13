package com.example.yugicardfinder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    private companion object {
        const val CAMERA_PERMISSION_REQUEST_CODE = 100
        const val CAMERA_CAPTURE_REQUEST_CODE = 101
    }

    private lateinit var searchInput: EditText
    private lateinit var searchButton: Button
    private lateinit var cameraButton: Button
    private lateinit var cameraStatusText: TextView
    private lateinit var capturedCardImageView: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var messageText: TextView
    private lateinit var resultContainer: View
    private lateinit var cardImageView: ImageView
    private lateinit var cardNameText: TextView
    private lateinit var cardTypeText: TextView
    private lateinit var cardStatsText: TextView
    private lateinit var cardExtraText: TextView
    private lateinit var cardDescriptionText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        searchButton.setOnClickListener { searchCard() }
        cameraButton.setOnClickListener { requestCameraAndOpen() }
        updateCameraStatus()
    }

    private fun bindViews() {
        searchInput = findViewById(R.id.searchInput)
        searchButton = findViewById(R.id.searchButton)
        cameraButton = findViewById(R.id.cameraButton)
        cameraStatusText = findViewById(R.id.cameraStatusText)
        capturedCardImageView = findViewById(R.id.capturedCardImageView)
        progressBar = findViewById(R.id.progressBar)
        messageText = findViewById(R.id.messageText)
        resultContainer = findViewById(R.id.resultContainer)
        cardImageView = findViewById(R.id.cardImageView)
        cardNameText = findViewById(R.id.cardNameText)
        cardTypeText = findViewById(R.id.cardTypeText)
        cardStatsText = findViewById(R.id.cardStatsText)
        cardExtraText = findViewById(R.id.cardExtraText)
        cardDescriptionText = findViewById(R.id.cardDescriptionText)
    }

    private fun searchCard() {
        val query = searchInput.text.toString().trim()

        if (query.isEmpty()) {
            searchInput.error = getString(R.string.empty_search_error)
            showMessage(getString(R.string.empty_search_error), isError = true)
            return
        }

        hideKeyboard()
        setLoading(true)

        Thread {
            try {
                val card = fetchFirstCard(query)
                runOnUiThread {
                    setLoading(false)
                    if (card == null) {
                        showMessage(getString(R.string.card_not_found), isError = true)
                    } else {
                        showCard(card)
                    }
                }
            } catch (exception: Exception) {
                runOnUiThread {
                    setLoading(false)
                    showMessage(getString(R.string.request_error), isError = true)
                }
            }
        }.start()
    }

    private fun requestCameraAndOpen() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            updateCameraStatus()

            if (granted) {
                openCamera()
            } else {
                showMessage(getString(R.string.camera_permission_denied), isError = true)
            }
        }
    }

    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        if (cameraIntent.resolveActivity(packageManager) == null) {
            showMessage(getString(R.string.camera_unavailable), isError = true)
            return
        }

        startActivityForResult(cameraIntent, CAMERA_CAPTURE_REQUEST_CODE)
    }

    @Deprecated("Mantido pela simplicidade do projeto sem dependências extras.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == CAMERA_CAPTURE_REQUEST_CODE && resultCode == RESULT_OK) {
            val photo = data?.extras?.get("data") as? Bitmap
            if (photo != null) {
                capturedCardImageView.setImageBitmap(photo)
                capturedCardImageView.visibility = View.VISIBLE
                cameraStatusText.text = getString(R.string.camera_photo_ready)
            }
        }
    }

    private fun fetchFirstCard(query: String): Card? {
        val encodedName = Uri.encode(query)
        val url = URL("https://db.ygoprodeck.com/api/v7/cardinfo.php?fname=$encodedName")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
        }

        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    reader.readText()
                }
                parseFirstCard(response)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseFirstCard(response: String): Card? {
        val cards = JSONObject(response).optJSONArray("data") ?: JSONArray()
        if (cards.length() == 0) return null

        val cardJson = cards.getJSONObject(0)
        return Card(
            name = cardJson.optString("name", getString(R.string.unknown_value)),
            type = cardJson.optString("type", getString(R.string.unknown_value)),
            description = cardJson.optString("desc", getString(R.string.unknown_value)),
            attack = cardJson.optIntOrNull("atk"),
            defense = cardJson.optIntOrNull("def"),
            level = cardJson.optIntOrNull("level"),
            race = cardJson.optString("race", getString(R.string.unknown_value)),
            attribute = cardJson.optString("attribute", getString(R.string.not_applicable)),
            imageUrl = cardJson.optJSONArray("card_images")
                ?.optJSONObject(0)
                ?.optString("image_url")
                .orEmpty()
        )
    }

    private fun showCard(card: Card) {
        messageText.visibility = View.GONE
        resultContainer.visibility = View.VISIBLE
        cardImageView.visibility = View.GONE

        cardNameText.text = card.name
        cardTypeText.text = getString(R.string.card_type_format, card.type)
        cardStatsText.text = getString(
            R.string.card_stats_format,
            card.attack?.toString() ?: getString(R.string.not_applicable),
            card.defense?.toString() ?: getString(R.string.not_applicable)
        )
        cardExtraText.text = getString(
            R.string.card_extra_format,
            card.level?.toString() ?: getString(R.string.not_applicable),
            card.race,
            card.attribute
        )
        cardDescriptionText.text = card.description
        loadCardImage(card.imageUrl)
    }

    private fun loadCardImage(imageUrl: String) {
        if (imageUrl.isBlank()) return

        Thread {
            try {
                val url = URL(imageUrl)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val bitmap = try {
                    BitmapFactory.decodeStream(connection.inputStream)
                } finally {
                    connection.disconnect()
                }

                runOnUiThread {
                    cardImageView.setImageBitmap(bitmap)
                    cardImageView.visibility = if (bitmap == null) View.GONE else View.VISIBLE
                }
            } catch (exception: Exception) {
                runOnUiThread {
                    cardImageView.visibility = View.GONE
                }
            }
        }.start()
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        searchButton.isEnabled = !isLoading
        searchButton.text = getString(if (isLoading) R.string.searching_button else R.string.search_button)
        if (isLoading) {
            messageText.visibility = View.GONE
            resultContainer.visibility = View.GONE
        }
    }

    private fun showMessage(message: String, isError: Boolean) {
        resultContainer.visibility = View.GONE
        messageText.visibility = View.VISIBLE
        messageText.text = message
        messageText.setTextColor(if (isError) Color.rgb(151, 38, 38) else Color.rgb(63, 69, 81))
    }

    private fun hideKeyboard() {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    private fun updateCameraStatus() {
        val statusText = if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            R.string.camera_permission_granted
        } else {
            R.string.camera_permission_pending
        }
        cameraStatusText.text = getString(statusText)
    }
}

private data class Card(
    val name: String,
    val type: String,
    val description: String,
    val attack: Int?,
    val defense: Int?,
    val level: Int?,
    val race: String,
    val attribute: String,
    val imageUrl: String
)

private fun JSONObject.optIntOrNull(name: String): Int? {
    return if (has(name) && !isNull(name)) optInt(name) else null
}
