package com.remotetouchpad.app

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.remotetouchpad.app.databinding.ActivityMainBinding
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var connection: RemoteConnection
    private var lastKeyboardText = ""
    private var keyboardVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Modern Android (edge-to-edge by default) draws behind the system
        // navigation bar, so the bottom controls need explicit padding or
        // they end up flush against (or under) the phone's virtual nav bar.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        connection = RemoteConnection { connected -> runOnUiThread { updateStatus(connected) } }

        setupInsetPadding()
        setupTrackpad()
        setupKeyboardToggle()
        setupKeyboardInput()
        setupMediaControls()
        setupZoomButtons()
        setupReverseScrollToggle()
        binding.deviceButton.setOnClickListener { startScan() }

        tryAutoReconnect()
    }

    private fun setupInsetPadding() {
        // Capture the base values from XML once; the insets listener can
        // fire more than once (rotation, re-layout), and reading back the
        // view's own current padding as the "base" each time would keep
        // stacking the inset on top of itself.
        val baseTopPadding = binding.topBar.paddingTop
        val baseTopPaddingH = binding.topBar.paddingLeft
        val baseBottomMargin = (binding.bottomBar.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topBar.setPadding(
                baseTopPaddingH + bars.left,
                baseTopPadding + bars.top,
                baseTopPaddingH + bars.right,
                binding.topBar.paddingBottom
            )
            val params = binding.bottomBar.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = baseBottomMargin + bars.bottom
            params.leftMargin = bars.left
            params.rightMargin = bars.right
            binding.bottomBar.layoutParams = params
            insets
        }
    }

    private fun prefs() = getSharedPreferences("remote_control", Context.MODE_PRIVATE)

    private fun tryAutoReconnect() {
        val ip = prefs().getString("laptop_ip", null)
        val name = prefs().getString("laptop_name", null)
        if (ip != null) {
            binding.statusText.text = "Connecting to ${name ?: ip}…"
            connection.connect(ip)
        } else {
            binding.statusText.text = "Not connected"
        }
    }

    private fun setupTrackpad() {
        binding.trackpad.onMove = { dx, dy -> connection.sendMove(dx, dy) }
        binding.trackpad.onLeftClick = { connection.sendClick("left") }
        binding.trackpad.onRightClick = { connection.sendClick("right") }
        binding.trackpad.onScroll = { dy ->
            val reversed = prefs().getBoolean("reverse_scroll", false)
            connection.sendScroll(if (reversed) -dy else dy)
        }
    }

    private fun setupZoomButtons() {
        binding.zoomInButton.setOnClickListener { connection.sendZoomClicks(3) }
        binding.zoomOutButton.setOnClickListener { connection.sendZoomClicks(-3) }
    }

    private fun setupReverseScrollToggle() {
        updateReverseScrollIcon()
        binding.reverseScrollToggle.setOnClickListener {
            val newValue = !prefs().getBoolean("reverse_scroll", false)
            prefs().edit().putBoolean("reverse_scroll", newValue).apply()
            updateReverseScrollIcon()
        }
    }

    private fun updateReverseScrollIcon() {
        val enabled = prefs().getBoolean("reverse_scroll", false)
        val color = if (enabled) R.color.accent else R.color.textPrimary
        binding.reverseScrollToggle.imageTintList =
            android.content.res.ColorStateList.valueOf(resources.getColor(color, theme))
    }

    private var mediaVisible = false

    private fun setupMediaControls() {
        binding.mediaToggle.setOnClickListener {
            if (keyboardVisible) closeKeyboard()
            mediaVisible = !mediaVisible
            binding.mediaBar.visibility = if (mediaVisible) View.VISIBLE else View.GONE
        }
        binding.prevButton.setOnClickListener { connection.sendKeyCode("PREV_TRACK") }
        binding.nextButton.setOnClickListener { connection.sendKeyCode("NEXT_TRACK") }
        binding.playPauseButton.setOnClickListener { connection.sendKeyCode("PLAY_PAUSE") }
        setRepeatableAction(binding.volumeUpButton) { connection.sendKeyCode("VOLUME_UP") }
        setRepeatableAction(binding.volumeDownButton) { connection.sendKeyCode("VOLUME_DOWN") }
    }

    /** Fires once on tap, then keeps firing while held down (like a real volume key). */
    private fun setRepeatableAction(button: ImageButton, action: () -> Unit) {
        val repeatHandler = Handler(Looper.getMainLooper())
        var repeating = false
        lateinit var repeatRunnable: Runnable
        repeatRunnable = Runnable {
            action()
            repeatHandler.postDelayed(repeatRunnable, 120L)
        }

        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    repeating = true
                    action()
                    repeatHandler.postDelayed(repeatRunnable, 400L) // initial delay before repeat kicks in
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (repeating) {
                        repeating = false
                        repeatHandler.removeCallbacks(repeatRunnable)
                    }
                }
            }
            true
        }
    }

    private fun setupKeyboardToggle() {
        binding.keyboardToggle.setOnClickListener {
            if (mediaVisible) {
                mediaVisible = false
                binding.mediaBar.visibility = View.GONE
            }
            if (keyboardVisible) {
                closeKeyboard()
            } else {
                keyboardVisible = true
                binding.keyboardBar.visibility = View.VISIBLE
                binding.keyboardInput.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(binding.keyboardInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun closeKeyboard() {
        keyboardVisible = false
        binding.keyboardBar.visibility = View.GONE
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.keyboardInput.windowToken, 0)
        binding.keyboardInput.setText("")
        lastKeyboardText = ""
    }

    private fun setupKeyboardInput() {
        binding.keyboardInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val newText = s?.toString() ?: ""
                if (newText.length > lastKeyboardText.length && newText.startsWith(lastKeyboardText)) {
                    connection.sendText(newText.substring(lastKeyboardText.length))
                } else if (newText.length < lastKeyboardText.length) {
                    connection.sendKeyCode("BACKSPACE", repeat = lastKeyboardText.length - newText.length)
                } else if (newText != lastKeyboardText) {
                    connection.sendText(newText)
                }
                lastKeyboardText = newText
            }
        })

        binding.keyboardInput.onEmptyBackspace = { connection.sendKeyCode("BACKSPACE") }

        binding.keyboardInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                connection.sendKeyCode("ENTER")
                binding.keyboardInput.setText("")
                lastKeyboardText = ""
                true
            } else {
                false
            }
        }

        binding.enterButton.setOnClickListener { connection.sendKeyCode("ENTER") }
        binding.backspaceButton.setOnClickListener {
            val text = binding.keyboardInput.text
            if (!text.isNullOrEmpty()) {
                text.delete(text.length - 1, text.length)
            } else {
                connection.sendKeyCode("BACKSPACE")
            }
        }
    }

    private fun startScan() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_scanning, null)
        val progress = dialogView.findViewById<ProgressBar>(R.id.scanProgress)
        val status = dialogView.findViewById<TextView>(R.id.scanStatus)
        progress.visibility = View.VISIBLE
        status.text = "Looking for your laptop on this WiFi…"

        val dialog = AlertDialog.Builder(this)
            .setTitle("Find laptop")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Enter IP manually") { _, _ -> showManualEntry() }
            .create()
        dialog.show()

        thread {
            val devices = try {
                DeviceDiscovery.scan()
            } catch (e: Exception) {
                emptyList()
            }
            runOnUiThread {
                if (!dialog.isShowing) return@runOnUiThread
                if (devices.isEmpty()) {
                    status.text = "No laptop found. Make sure RemoteControlServer is running on the same WiFi."
                    progress.visibility = View.GONE
                } else {
                    dialog.dismiss()
                    showDeviceList(devices)
                }
            }
        }
    }

    private fun showDeviceList(devices: List<FoundDevice>) {
        val labels = devices.map { "${it.name}  (${it.ip})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select your laptop")
            .setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)) { _, which ->
                connectTo(devices[which].ip, devices[which].name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showManualEntry() {
        val input = android.widget.EditText(this)
        input.hint = "e.g. 192.168.1.23"
        AlertDialog.Builder(this)
            .setTitle("Enter laptop IP")
            .setView(input)
            .setPositiveButton("Connect") { _, _ ->
                val ip = input.text.toString().trim()
                if (ip.isNotEmpty()) connectTo(ip, ip)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun connectTo(ip: String, name: String) {
        prefs().edit().putString("laptop_ip", ip).putString("laptop_name", name).apply()
        binding.statusText.text = "Connecting to $name…"
        connection.connect(ip)
    }

    private fun updateStatus(connected: Boolean) {
        val name = prefs().getString("laptop_name", null)
        binding.statusText.text = when {
            connected -> name ?: "Connected"
            name != null -> "Reconnecting to $name…"
            else -> "Not connected"
        }
        val color = if (connected) R.color.success else R.color.danger
        binding.statusDot.background.setTint(resources.getColor(color, theme))
    }

    override fun onDestroy() {
        super.onDestroy()
        connection.disconnect()
    }
}
