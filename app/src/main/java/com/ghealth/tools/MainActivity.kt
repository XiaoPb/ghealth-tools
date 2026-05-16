package com.ghealth.tools

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.ghealth.tools.core.ui.theme.GHealthTheme
import com.ghealth.tools.navigation.GHealthNavHost
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val bluetoothManager by lazy {
        getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager.adapter

    private val bluetoothPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

    private val storagePermissions: Array<String>
        get() = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_IMAGES
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                emptyArray()
            }
            else -> {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
        }

    private val allPermissions: Array<String>
        get() = bluetoothPermissions + storagePermissions

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            val deniedPermissions = permissions.filter { !it.value }.keys
            Toast.makeText(
                this,
                "蓝牙权限被拒绝: ${deniedPermissions.joinToString()}",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Timber.d("Bluetooth permissions granted, now requesting storage permissions")
            requestStoragePermissions()
        }
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            val deniedPermissions = permissions.filter { !it.value }.keys
            Toast.makeText(
                this,
                "存储权限被拒绝: ${deniedPermissions.joinToString()}",
                Toast.LENGTH_LONG
            ).show()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            requestManageExternalStorage()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (bluetoothAdapter?.isEnabled == true) {
            checkAndRequestPermissions()
        } else {
            Toast.makeText(this, "蓝牙未启用", Toast.LENGTH_SHORT).show()
        }
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                Timber.d("MANAGE_EXTERNAL_STORAGE permission granted")
            } else {
                Toast.makeText(this, "存储管理权限被拒绝，日志和CSV保存可能受限", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkBluetoothAndPermissions()

        setContent {
            GHealthTheme {
                GHealthNavHost()
            }
        }
    }

    private fun checkBluetoothAndPermissions() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "设备不支持蓝牙", Toast.LENGTH_LONG).show()
            return
        }

        if (bluetoothAdapter?.isEnabled != true) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        } else {
            checkAndRequestPermissions()
        }
    }

    private fun checkAndRequestPermissions() {
        val missingBluetoothPermissions = bluetoothPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingBluetoothPermissions.isNotEmpty()) {
            Timber.d("Requesting bluetooth permissions: ${missingBluetoothPermissions.joinToString()}")
            bluetoothPermissionLauncher.launch(missingBluetoothPermissions.toTypedArray())
        } else {
            Timber.d("Bluetooth permissions already granted, requesting storage permissions")
            requestStoragePermissions()
        }
    }

    private fun requestStoragePermissions() {
        val missingStoragePermissions = storagePermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingStoragePermissions.isNotEmpty()) {
            Timber.d("Requesting storage permissions: ${missingStoragePermissions.joinToString()}")
            storagePermissionLauncher.launch(missingStoragePermissions.toTypedArray())
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            requestManageExternalStorage()
        } else {
            Timber.d("All permissions granted")
        }
    }

    private fun requestManageExternalStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                manageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStorageLauncher.launch(intent)
            }
        }
    }

    fun hasAllPermissions(): Boolean {
        val hasBluetooth = bluetoothPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            storagePermissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
        }

        return hasBluetooth && hasStorage
    }
}
