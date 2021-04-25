package com.simsim.island.ui.main

import android.content.DialogInterface
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.core.view.isVisible
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.simsim.island.MainActivity
import com.simsim.island.R
import com.simsim.island.dataStore
import com.simsim.island.databinding.SettingsDialogFragmentBinding
import com.simsim.island.dp2PxScale
import com.simsim.island.util.LOG_TAG
import com.simsim.island.util.extractCookie
import com.simsim.island.util.getPath
import dagger.hilt.android.AndroidEntryPoint
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.resolution
import id.zelory.compressor.constraint.size
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileInputStream
import kotlin.properties.Delegates

@AndroidEntryPoint
class SettingsFragment(
    val activity: MainActivity,
    val viewModel: MainViewModel,
    val binding: SettingsDialogFragmentBinding
) : PreferenceFragmentCompat() {
    //    private val viewModel:MainViewModel=ViewModelProvider(activity).get(MainViewModel::class.java)
    private val customDataStore: CustomDataStore = CustomDataStore()
    private val dataStore: DataStore<Preferences> = activity.dataStore
    private var dp2PxScale: Float = activity.dp2PxScale()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.apply {
            Log.e(LOG_TAG, "set CustomDataStore to preferenceDataStore")
            preferenceDataStore = customDataStore
        }
        setPreferencesFromResource(R.xml.settings, rootKey)
        val fabSizeSeekBar = findPreference<SeekBarPreference>("fab_seek_bar_key")
            ?.apply {
                setOnPreferenceChangeListener { _, newValue ->
                    val fabSize = newValue as Int
                    binding.fabAdd.customSize = (fabSize * dp2PxScale).toInt()
                    true
                }
            } ?: throw Exception("can not find fabSizeSeekBar")

        val fabSizeDefault = findPreference<Preference>("fab_default_size_key")
            ?.apply {
                setOnPreferenceClickListener {
                    binding.fabAdd.customSize = FloatingActionButton.NO_CUSTOM_SIZE
                    binding.fabAdd.size = FloatingActionButton.SIZE_AUTO
                    true
                }
            } ?: throw Exception("can not find setFabDefaultSize")

        val fabSwitch = findPreference<SwitchPreference>("enable_fab_key")?.apply {
            var isFabEnable: Boolean by Delegates.observable(this.isChecked) { _, _, newValue ->
                binding.settingToolbar.menu.findItem(R.id.setting_menu_add).isVisible = !newValue
                fabSizeSeekBar.isVisible = newValue
                fabSizeDefault.isVisible = newValue
                binding.fabAdd.isVisible = newValue
            }
            isFabEnable = isChecked
            setOnPreferenceChangeListener { _, newValue ->
                isFabEnable = newValue as Boolean
//                binding.settingToolbar.menu.findItem(R.id.setting_menu_add).isVisible=!isFabEnable
//                fabSizeSeekBar.isVisible=isFabEnable
//                fabSizeDefault.isVisible=isFabEnable
//                binding.fabAdd.isVisible=isFabEnable
                true
            }
        } ?: throw Exception("can not find fabSwitch")

        val cookieInUse = findPreference<Preference>("cookie_in_use_key")
            ?: throw Exception("can not find cookieInUse")
        val cookieQR = findPreference<Preference>("cookie_from_QR_code_key")?.apply {
            setOnPreferenceClickListener {
                MaterialAlertDialogBuilder(activity).setItems(
                    arrayOf(
                        "相机扫码",
                        "图片扫码"
                    )
                ) { dialog: DialogInterface, position: Int ->
                    dialog.dismiss()
                    when (position) {
                        0 -> {
                            activity.scanQRCode.launch(Unit)
                        }
                        1 -> {
                            QRCodeFromImage()
                        }
                    }
                }.show()
                viewModel.QRcodeResult.observe(viewLifecycleOwner) { result ->
                    result?.let {
                        preferenceDataStore!!.putString("cookie_from_QR_code_key", result)
                        cookieInUse.summary = result
                        viewModel.QRcodeResult.value = null
                    }
                }
                true
            }
        } ?: throw Exception("can not find cookieQR")

        val cookieWebView = findPreference<Preference>("cookie_from_web_view_key")?.apply {
            setOnPreferenceClickListener {
                true
            }
        } ?: throw Exception("can not find cookieWebView")

    }

    private fun QRCodeFromImage() {
        activity.pickPicture.launch("image/*")
        viewModel.pictureUri.observe(viewLifecycleOwner) { photoUri ->
            photoUri?.let {
            CoroutineScope(Dispatchers.IO).launch {
                    getPath(activity, photoUri)?.let { imagePath ->
                        var compressedImageFile = File(imagePath)
                        if (compressedImageFile.readBytes().size > 1024 * 100) {
                            compressedImageFile =
                                Compressor.compress(activity, compressedImageFile) {
                                    resolution(100,100)
                                    size(1024 * 100)
                                }
                        }
                        val compressedBitmap =
                            FileInputStream(compressedImageFile).use { fileInputStream ->
                                BitmapFactory.decodeStream(fileInputStream)
                            }
                        val width=compressedBitmap.width
                        val height=compressedBitmap.height
                        val intArray = IntArray(width * height)
                        compressedBitmap.getPixels(
                            intArray,
                            0,
                            width,
                            0,
                            0,
                            width,
                            height
                        )
                        val luminanceSource = RGBLuminanceSource(
                            width,
                            height,
                            intArray
                        )
                        val binaryBitmap = BinaryBitmap(HybridBinarizer(luminanceSource))
                        val result=try {
                            MultiFormatReader().decode(binaryBitmap).text
                        } catch (e: Exception) {
                            Log.e(
                                LOG_TAG,
                                "QRCode from image exception:${e.stackTraceToString()}"
                            )
                            null
                        }
                        result?.let {
                            CoroutineScope(Dispatchers.Main).launch {
                                viewModel.QRcodeResult.value =result.extractCookie()
                                viewModel.pictureUri.value = null
                            }
                        }

                    }
                }

            }
        }
    }

    inner class CustomDataStore : androidx.preference.PreferenceDataStore() {
        init {
            Log.e(LOG_TAG, "initialize CustomDataStore class")
        }

        override fun putString(key: String, value: String?) {
            value?.let {
                val stringKey = stringPreferencesKey(key)
                lifecycleScope.launch {
                    dataStore.edit { settings ->
                        settings[stringKey] = value
                        Log.e(LOG_TAG, "save string to dataStore with key:$key:$value")
                    }
                }
            }
        }

        override fun getString(key: String, defValue: String?): String? {
            return runBlocking {
                val stringKey = stringPreferencesKey(key)
                val dataStoreResult = withContext(Dispatchers.IO) {
                    dataStore.data.map { settings ->
                        settings[stringKey]
                    }.first()
                }
                Log.e(LOG_TAG, "get string from dataStore with key:$key:$dataStoreResult")
                dataStoreResult
            }
        }

        override fun putInt(key: String, value: Int) {
            val intKey = intPreferencesKey(key)
            lifecycleScope.launch {
                dataStore.edit { settings ->
                    settings[intKey] = value
                    Log.e(LOG_TAG, "save string to dataStore with key:$key:$value")
                }
            }
        }

        override fun getInt(key: String, defValue: Int): Int {
            return runBlocking {
                val intKey = intPreferencesKey(key)
                val dataStoreResult = withContext(Dispatchers.IO) {
                    dataStore.data.map { settings ->
                        settings[intKey] ?: defValue
                    }.first()
                }
                Log.e(LOG_TAG, "get int from dataStore with key:$key:$dataStoreResult")
                dataStoreResult
            }
        }

        override fun putBoolean(key: String, value: Boolean) {
            val booleanKey = booleanPreferencesKey(key)
            lifecycleScope.launch {
                dataStore.edit { settings ->
                    settings[booleanKey] = value
                    Log.e(LOG_TAG, "save boolean to dataStore with key:$key:$value")
                }
            }
        }

        override fun getBoolean(key: String, defValue: Boolean): Boolean {
            return runBlocking {
                val booleanKey = booleanPreferencesKey(key)
                val dataStoreResult = withContext(Dispatchers.IO) {
                    dataStore.data.map { settings ->
                        settings[booleanKey] ?: defValue
                    }.first()
                }
                Log.e(LOG_TAG, "get boolean from dataStore with key:$key:$dataStoreResult")
                dataStoreResult
            }
        }

        override fun putStringSet(key: String, values: MutableSet<String>?) {
            val stringSetKey = stringSetPreferencesKey(key)
            lifecycleScope.launch {
                dataStore.edit { settings ->
                    settings[stringSetKey] = values?: mutableSetOf()
                    Log.e(LOG_TAG, "save string set to dataStore with key:$key:${values?:"set is empty"}")
                }
            }
        }

        override fun getStringSet(
            key: String,
            defValues: MutableSet<String>?
        ): MutableSet<String> {
            return runBlocking {
                val stringSetKey = stringSetPreferencesKey(key)
                val dataStoreResult = withContext(Dispatchers.IO) {
                    dataStore.data.map { settings ->
                        settings[stringSetKey]?.toMutableSet()?:defValues?: mutableSetOf()
                    }.first()
                }
                Log.e(LOG_TAG, "get string set from dataStore with key:$key:$dataStoreResult")
                dataStoreResult
            }
        }


    }
}

//        lifecycleScope.launch {
//            dataStore.data.collectLatest {settings->
//                settings[booleanPreferencesKey("enable_fab_key")]?.let { isFabEnable->
//                    binding.settingToolbar.menu.findItem(R.id.setting_menu_add).isVisible=!isFabEnable
//                    fabSizeSeekBar.isVisible=isFabEnable
//                    fabSizeDropDown.isVisible=isFabEnable
//                    binding.fabAdd.isVisible=isFabEnable
//
//                }
//                settings[intPreferencesKey("fab_seek_bar_key")]?.let {fabSize->
//                    binding.fabAdd.customSize=(fabSize*dp2PxScale).toInt()
//                }
//                settings[stringPreferencesKey("fab_drop_down_key")]?.let { fabSize->
//
//                }
//
//            }
//        }