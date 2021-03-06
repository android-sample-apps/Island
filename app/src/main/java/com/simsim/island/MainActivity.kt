package com.simsim.island

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.divyanshu.draw.activity.DrawingActivity
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.integration.android.IntentIntegrator
import com.simsim.island.databinding.MainActivityBinding
import com.simsim.island.model.Emoji
import com.simsim.island.ui.main.MainViewModel
import com.simsim.island.util.LOG_TAG
import com.simsim.island.util.extractCookie
import com.simsim.island.util.sections
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
fun Context.dp2PxScale() = this.resources.displayMetrics.density

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: MainActivityBinding
    private val viewModel: MainViewModel by viewModels()
    private var backPressTime = LocalDateTime.now()
    internal val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach { entry ->
                val permissionName = entry.key
                val isGranted = entry.value
                if (!isGranted) {
                    Log.e(LOG_TAG, "permission[$permissionName] is denied")
                }
            }

        }
    internal val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                Log.e(LOG_TAG, "camera take photo:success")
                viewModel.cameraTakePictureSuccess.value = true
            }

        }
    internal val pickPicture =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            viewModel.pictureUri.value = uri
        }

    class AddNewDraw : ActivityResultContract<Unit, ByteArray?>() {
        override fun createIntent(context: Context, input: Unit?): Intent {
            return Intent(context, DrawingActivity::class.java)
        }

        override fun parseResult(resultCode: Int, intent: Intent?): ByteArray? {
            if (resultCode != Activity.RESULT_OK) {
                return null
            }
            return intent?.getByteArrayExtra("bitmap")
        }
    }

    internal val newDraw = registerForActivityResult(AddNewDraw()) { byteArray ->
        byteArray?.let {
            val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
            viewModel.drawPicture.value = bitmap
            viewModel.pictureUri.value = saveImageToCache(bitmap = bitmap)
        }
    }

    internal fun shareText(text: String) {
        Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"

        }.also {
            Intent.createChooser(it, null)
            startActivity(it)
        }
    }

    inner class ScanQRCode : ActivityResultContract<Unit, String?>() {
        override fun createIntent(context: Context, input: Unit?): Intent {
            return IntentIntegrator(this@MainActivity).apply {
                setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                setPrompt("???????????????")
                setBeepEnabled(false)
                setOrientationLocked(false)
            }.createScanIntent()
        }

        override fun parseResult(resultCode: Int, intent: Intent?): String? {
            return IntentIntegrator.parseActivityResult(resultCode, intent)?.contents
        }

    }

    internal val scanQRCode = registerForActivityResult(ScanQRCode()) { QRResult ->
        QRResult?.let {
            it.extractCookie()?.let { formattedQRResult ->
                viewModel.QRcodeResult.value = formattedQRResult
                Log.e(LOG_TAG, "QR code formatted:$formattedQRResult")
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        getWindowHeightAndActionBarHeight()
        lifecycleScope.launch {
            if (!viewModel.isAnyEmoji()) {
                val emoji =
                    "|??????, (??????????`), (;????`), (???????????), (=????????)=, | ???????), |-` ), |??` ), |???` ), |???` ), (????????), (???????????????????), (???o???)???, (|||????????), ( ?????????), ( ?????`), (*?????`), (*?????????), (*?????????), (?????? 3???), ( ?????`), ( ???_??????), ( ??_???`), (*????`), (?????????), (?????????), (?????????), (?????????), (*?????????*), ( ?????????), ( `????), (`???? ), (`????? ), ??`?????),  ?????????)??, ??? ??????)???, (???????????), (|||????????), ( ????????), ??( ????????), ( ;????????), ( ;????`), (????? ) ??? ???, ( ????????), (((???????????))), ( ` ?????), ( ????`), ( -??-), (>??<), ??????( ?????`???), ( T??T), (?????????), (???3???), (?????????), (??? . ???), (?????????), (?????????), (?????????), (?????????), ???(??????????), (*????`*), (????????), ( ???????), (????????), (??????????`), (`??????????), ( `_?????), ( `?????), ( ??_???`), ( ????`), ( ????????), (o????????o), (???^??^), (???????????????), /( ???????????? )\\, ???(????`???), (????????????)???, (??????????)??, (???????????)??, |????? ), ????????????, ???(?????`???), ????????? )???, ?????????))??`), ?????????))????), ?????????))???`), (?????((?????????\n"
                emoji.split(",").mapIndexed { i, s ->
                    Emoji(emojiIndex = i, emoji = s)
                }.also { emojiList ->
                    viewModel.insertAllEmojis(emojiList)
                }
            }
            if (!viewModel.isAnySectionInDB()) {
                viewModel.insertAllSection(sections)
            }

        }
        savedInstanceState?.let {
            val poThreadId = it.getLong("poThreadId")
            if (poThreadId != 0L) {
                viewModel.savedInstanceState.value = poThreadId
            }
        }

        lifecycleScope.launchWhenCreated {
            Log.e(LOG_TAG, "network:${checkNetwork()}")
            viewModel.doWhenDestroy()
        }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        viewModel.currentPoThread?.let {
            outState.putLong("poThreadId", it.threadId)
        }
        super.onSaveInstanceState(outState)
    }


    private fun getWindowHeightAndActionBarHeight() {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        viewModel.windowHeight = displayMetrics.heightPixels
        val tv = TypedValue()
        theme.resolveAttribute(R.attr.actionBarSize, tv, true)
        viewModel.actionBarHeight = TypedValue.complexToDimensionPixelSize(
            tv.data,
            resources.displayMetrics
        )
    }


    private fun checkNetwork(): Boolean {
        val connMgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo: NetworkInfo? = connMgr.activeNetworkInfo
        return networkInfo?.isConnected == true
    }

    @Throws(IOException::class)
    internal fun createImageFile(): Uri {
        requestPermission.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
        val timeStamp: String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val displayName = "Camera_${timeStamp}_.jpg"
        val photoUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")

                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)

            }
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)!!
        } else {
            val storageDir: File =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            val photoFile = File.createTempFile(
                "Camera_${timeStamp}_", /* prefix */
                ".jpg", /* suffix */
                storageDir /* directory */
            ).apply {
                // Save a file: path for use with ACTION_VIEW intents
                viewModel.picturePath.value = absolutePath
                Log.e(LOG_TAG, "photo absolute path: $absolutePath")
            }
            FileProvider.getUriForFile(this, "com.simsim.fileProvider", photoFile)
        }
//        val photoUri= FileProvider.getUriForFile(this,"com.simsim.fileProvider",photoFile)
        Log.e(LOG_TAG, "take picture,and it's uri:$photoUri")
        return photoUri
    }

    internal fun saveImage(
        type: String = "jpg",
        savePath: String = Environment.DIRECTORY_PICTURES
    ): OutputStream {
        requestPermission.launch(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
        val timeStamp: String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val displayName = "Camera_${timeStamp}_.jpg"
        val photoStream =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/$type")

                    put(MediaStore.MediaColumns.RELATIVE_PATH, savePath)

                }
                val uri =
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)!!
                resolver.openOutputStream(uri)!!
            } else {
                val storageDir: File = Environment.getExternalStoragePublicDirectory(savePath)
                val photoFile = File.createTempFile(
                    "Camera_${timeStamp}_", /* prefix */
                    ".$type", /* suffix */
                    storageDir /* directory */
                ).apply {
                    // Save a file: path for use with ACTION_VIEW intents
                    Log.e(LOG_TAG, "photo absolute path: $absolutePath")
                }
                FileOutputStream(photoFile)
            }
        return photoStream
//        val photoUri= FileProvider.getUriForFile(this,"com.simsim.fileProvider",photoFile)
    }

    internal fun saveImageToCache(type: String = "jpg", bitmap: Bitmap): Uri {
        val timeStamp: String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val displayName = "Camera_${timeStamp}_.jpg"
        val storageDir: File = File(cacheDir, "IslandImageCache")
        val photoFile = File.createTempFile(
            "Camera_${timeStamp}_", /* prefix */
            ".$type", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            Log.e(LOG_TAG, "photo absolute path: $absolutePath")
        }
        val uri = FileProvider.getUriForFile(this, "com.simsim.fileProvider", photoFile)
        FileOutputStream(photoFile).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
        }
        return uri
    }


    override fun onBackPressed() {
        val now = LocalDateTime.now()
        if (Duration.between(backPressTime, now).seconds < 0.5) {
            finish()
        } else {
            Snackbar.make(binding.mainActivityCoordinatorLayout, "??????????????????", 500).show()
            backPressTime = now
        }
    }

    companion object {
        private const val REQUEST_IMAGE_CAMERA = 1
    }


}