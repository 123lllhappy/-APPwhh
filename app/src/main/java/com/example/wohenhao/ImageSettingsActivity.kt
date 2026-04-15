package com.example.wohenhao

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.wohenhao.data.db.AppDatabase
import com.example.wohenhao.data.db.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ImageSettingsActivity : AppCompatActivity() {

    private lateinit var binding: com.example.wohenhao.databinding.ActivityImageSettingsBinding
    private var images = mutableListOf<ImageInfo>()
    private var selectedImages = mutableListOf<ImageInfo>()
    private var isLibraryAMode = true
    private var isDeleteMode = false
    private var currentMode = "random"

    companion object {
        private const val REQUEST_CODE_PICK_IMAGES = 1001
        private const val REQUEST_CODE_PERMISSION = 1002
        private const val MAX_IMAGES = 9
    }

    data class ImageInfo(val id: String, val path: String, var selected: Boolean = false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = com.example.wohenhao.databinding.ActivityImageSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isLibraryAMode = intent.getStringExtra("library") != "B"

        binding.btnBack.setOnClickListener { finish() }
        binding.tvDelete.setOnClickListener { toggleDeleteMode() }
        binding.btnConfirm.setOnClickListener { saveSelectedImages() }

        binding.tvRandomMode.setOnClickListener { setMode("random") }
        binding.tvSequenceMode.setOnClickListener { setMode("sequence") }

        binding.tvTitle.text = if (isLibraryAMode) "图片库A" else "图片库B"

        loadImages()
        loadMode()
    }

    private fun loadImages() {
        GlobalScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getInstance(applicationContext).settingsDao()
            val key = if (isLibraryAMode) AppSettings.KEY_CHECKIN_IMAGES_A else AppSettings.KEY_CHECKIN_IMAGES_B
            val imagesStr = dao.getString(key, "")

            images.clear()
            if (imagesStr.isNotEmpty()) {
                imagesStr.split(",").filter { it.isNotEmpty() }.forEach { path ->
                    images.add(ImageInfo(path, path))
                }
            }

            if (images.isEmpty()) {
                if (isLibraryAMode) {
                    images.add(ImageInfo("@drawable/default_front", "@drawable/default_front"))
                } else {
                    images.add(ImageInfo("@drawable/default_back", "@drawable/default_back"))
                }
            }

            withContext(Dispatchers.Main) {
                updateGrid()
                updateDeleteButtonVisibility()
            }
        }
    }

    private fun loadMode() {
        GlobalScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getInstance(applicationContext).settingsDao()
            val key = if (isLibraryAMode) AppSettings.KEY_IMAGE_MODE_A else AppSettings.KEY_IMAGE_MODE_B
            currentMode = dao.getString(key, "random")

            withContext(Dispatchers.Main) {
                updateModeButtons()
            }
        }
    }

    private fun setMode(mode: String) {
        currentMode = mode

        GlobalScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getInstance(applicationContext).settingsDao()
            val key = if (isLibraryAMode) AppSettings.KEY_IMAGE_MODE_A else AppSettings.KEY_IMAGE_MODE_B
            dao.putString(key, mode)
        }

        updateModeButtons()
    }

    private fun updateModeButtons() {
        if (currentMode == "random") {
            binding.tvRandomMode.setBackgroundResource(R.drawable.bg_button_blue)
            binding.tvRandomMode.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            binding.tvSequenceMode.setBackgroundResource(R.color.bg_gray)
            binding.tvSequenceMode.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        } else {
            binding.tvSequenceMode.setBackgroundResource(R.drawable.bg_button_blue)
            binding.tvSequenceMode.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            binding.tvRandomMode.setBackgroundResource(R.color.bg_gray)
            binding.tvRandomMode.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun updateGrid() {
        val displayList = mutableListOf<ImageInfo>()
        displayList.addAll(images)

        while (displayList.size < MAX_IMAGES) {
            displayList.add(ImageInfo("", "", false))
        }

        binding.gridImages.adapter = ImageGridAdapter(this, displayList)
    }

    private fun updateDeleteButtonVisibility() {
        binding.tvDelete.visibility = if (images.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun toggleDeleteMode() {
        isDeleteMode = !isDeleteMode

        if (isDeleteMode) {
            binding.tvDelete.text = "取消"
            binding.btnConfirm.text = "删除"
            binding.btnConfirm.isEnabled = false
            selectedImages.clear()

            images.forEach { it.selected = false }
        } else {
            binding.tvDelete.text = "删除"
            binding.btnConfirm.text = "确定"
            binding.btnConfirm.isEnabled = false
            selectedImages.clear()

            images.forEach { it.selected = false }
        }

        updateGrid()
    }

    private fun saveSelectedImages() {
        if (isDeleteMode) {
            deleteSelectedImages()
        } else {
            finish()
        }
    }

    private fun deleteSelectedImages() {
        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定删除选中的图片吗？")
            .setPositiveButton("确定") { dialog, _ ->
                GlobalScope.launch(Dispatchers.IO) {
                    images.removeAll(selectedImages)
                    selectedImages.forEach {
                        if (!it.path.startsWith("@drawable/")) {
                            val file = File(it.path)
                            if (file.exists()) file.delete()
                        }
                    }

                    val dao = AppDatabase.getInstance(applicationContext).settingsDao()
                    val key = if (isLibraryAMode) AppSettings.KEY_CHECKIN_IMAGES_A else AppSettings.KEY_CHECKIN_IMAGES_B
                    dao.putString(key, images.joinToString(",") { it.id })

                    withContext(Dispatchers.Main) {
                        toggleDeleteMode()
                        updateGrid()
                        updateDeleteButtonVisibility()
                        Toast.makeText(this@ImageSettingsActivity, "删除成功", Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun pickImagesFromGallery() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                REQUEST_CODE_PERMISSION
            )
            return
        }

        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(Intent.createChooser(intent, "选择图片"), REQUEST_CODE_PICK_IMAGES)
    }

    private fun saveImageToPrivateDir(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val fileName = "image_${System.currentTimeMillis()}.jpg"
                val outputFile = File(filesDir, fileName)
                inputStream.copyTo(outputFile.outputStream())
                inputStream.close()
                outputFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("ImageSettings", "Failed to save image", e)
            null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return

        if (requestCode == REQUEST_CODE_PICK_IMAGES) {
            val remaining = MAX_IMAGES - images.size

            if (data?.clipData != null) {
                val clipData = data.clipData
                for (i in 0 until minOf(clipData!!.itemCount, remaining)) {
                    val uri = clipData.getItemAt(i).uri
                    val path = saveImageToPrivateDir(uri)
                    if (path != null) {
                        images.add(ImageInfo(path, path))
                    }
                }
            } else if (data?.data != null) {
                val uri = data.data
                val path = saveImageToPrivateDir(uri!!)
                if (path != null && images.size < MAX_IMAGES) {
                    images.add(ImageInfo(path, path))
                }
            }

            GlobalScope.launch(Dispatchers.IO) {
                val dao = AppDatabase.getInstance(applicationContext).settingsDao()
                val key = if (isLibraryAMode) AppSettings.KEY_CHECKIN_IMAGES_A else AppSettings.KEY_CHECKIN_IMAGES_B
                dao.putString(key, images.joinToString(",") { it.id })
            }

            updateGrid()
            updateDeleteButtonVisibility()
            Toast.makeText(this, "添加成功", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickImagesFromGallery()
            } else {
                Toast.makeText(this, "需要授予读取相册权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    inner class ImageGridAdapter(
        private val context: AppCompatActivity,
        private val items: List<ImageInfo>
    ) : ArrayAdapter<ImageInfo>(context, 0, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = items[position]
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_image_grid_new, parent, false)

            val layoutParams = view.layoutParams
            if (layoutParams != null) {
                layoutParams.height = parent.width / 3
            }
            view.layoutParams = layoutParams

            val ivImage = view.findViewById<ImageView>(R.id.iv_image)
            val ivAdd = view.findViewById<ImageView>(R.id.iv_add)
            val cbSelect = view.findViewById<CheckBox>(R.id.cb_select)

            if (item.id.isEmpty()) {
                ivImage.visibility = View.GONE
                ivAdd.visibility = View.VISIBLE
                cbSelect.visibility = View.GONE

                view.setOnClickListener {
                    if (!isDeleteMode) {
                        (context as ImageSettingsActivity).pickImagesFromGallery()
                    }
                }
            } else {
                ivAdd.visibility = View.GONE
                ivImage.visibility = View.VISIBLE

                if (item.path.startsWith("@drawable/")) {
                    val resName = item.path.removePrefix("@drawable/")
                    val resId = resources.getIdentifier(resName, "drawable", packageName)
                    if (resId != 0) {
                        ivImage.setImageResource(resId)
                    }
                } else if (File(item.path).exists()) {
                    ivImage.setImageURI(Uri.fromFile(File(item.path)))
                }
                ivImage.scaleType = ImageView.ScaleType.CENTER_CROP

                if (isDeleteMode) {
                    cbSelect.visibility = View.VISIBLE
                    cbSelect.isChecked = item.selected

                    cbSelect.setOnCheckedChangeListener { _, isChecked ->
                        item.selected = isChecked
                        if (isChecked) {
                            if (!selectedImages.contains(item)) {
                                selectedImages.add(item)
                            }
                        } else {
                            selectedImages.remove(item)
                        }
                        binding.btnConfirm.isEnabled = selectedImages.isNotEmpty()
                        binding.tvSelectedCount.text = "已选择 ${selectedImages.size} 项"
                    }

                    view.setOnClickListener {
                        cbSelect.isChecked = !cbSelect.isChecked
                    }
                } else {
                    cbSelect.visibility = View.GONE
                    view.setOnClickListener { }
                }
            }

            return view
        }
    }
}