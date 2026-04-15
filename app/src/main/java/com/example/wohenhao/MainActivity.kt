package com.example.wohenhao

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import com.example.wohenhao.data.db.AppDatabase
import com.example.wohenhao.data.db.AppSettings
import com.example.wohenhao.data.db.Contact
import com.example.wohenhao.data.db.SmsTemplate
import com.example.wohenhao.databinding.ActivityMainBinding
import com.example.wohenhao.service.GuardianWorker
import com.example.wohenhao.util.CheckInImageSelector
import com.example.wohenhao.util.Constants
import com.example.wohenhao.util.LocationHelper
import com.example.wohenhao.util.PermissionHelper
import com.example.wohenhao.util.SmsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 主页面 - 四个 Tab：打卡 / SOS / 联系人 / 设置
 * 我很好 2.0 版本
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var checkInImageSelector: CheckInImageSelector

    companion object {
        private const val TAG = "MainActivity"
        private const val TAB_CHECKIN = 0
        private const val TAB_SOS = 1
        private const val TAB_CONTACTS = 2
        private const val TAB_SETTINGS = 3
    }

    private var currentTab = TAB_CHECKIN
    private var contacts: List<Contact> = emptyList()
    private var checkInImageLoaded = false
    private var isCardFlipped = false  // 卡片是否已翻转（显示背面）
    
    // 图片缓存
    private var cachedLibrary = "A"
    private var cachedImages = listOf<String>()
    private var cachedMode = "random"
    private var cachedIndex = 0
    private var isCacheLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity onCreate - 我很好 2.0")

        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            // 初始化工具类
            checkInImageSelector = CheckInImageSelector(this)

            // 初始化数据库
            ensureDatabaseInitialized()

            // 设置 UI
            setupUI()

            // 加载数据
            loadData()

            // 权限状态：延迟更新（等页面渲染完成）
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinishing && !isDestroyed) {
                    updatePermissionStatus()
                }
            }, 500)

        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "页面加载异常", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "MainActivity onResume")

        try {
            // 每次回到首页刷新权限状态（用户可能刚从设置页回来）
            updatePermissionStatus()

            // 更新最后打开时间
            updateLastOpenTime()

            // 刷新数据
            loadData()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onResume", e)
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // 转发给 PermissionHelper 处理（权限申请结果不影响横幅逻辑，横幅只在 onResume 更新）
        PermissionHelper.handlePermissionResult(this, requestCode, permissions, grantResults)
    }

    private fun ensureDatabaseInitialized() {
        if (!AppDatabase.isInitialized()) {
            try {
                AppDatabase.getInstance(applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize database", e)
            }
        }
    }

    private fun setupUI() {
        try {
            // Tab 切换
            binding.tabCheckin.setOnClickListener { switchTab(TAB_CHECKIN) }
            binding.tabSos.setOnClickListener { switchTab(TAB_SOS) }
            binding.tabContacts.setOnClickListener { switchTab(TAB_CONTACTS) }
            binding.tabSettings.setOnClickListener { switchTab(TAB_SETTINGS) }

            // 打卡按钮
            binding.btnCheckin.setOnClickListener {
                performCheckIn()
            }

            // 打卡卡片点击翻转（每次点击都可以翻转）
            binding.layoutCheckinCard.setOnClickListener {
                flipCard()
            }
            
            // ImageView 也设置点击事件，确保点击图片区域也能切换
            binding.ivCheckinImage.setOnClickListener {
                flipCard()
            }

            // SOS 按钮
            binding.btnSos.setOnClickListener {
                showSOSConfirmDialog()
            }

            // 骑行按钮
            binding.btnCycling.setOnClickListener {
                startCyclingMode()
            }

            // 结束骑行按钮
            binding.btnEndCycling.setOnClickListener {
                stopCyclingMode()
            }

            // 添加联系人按钮（联系人 Tab）
            binding.btnAddContactTab.setOnClickListener {
                startActivity(Intent(this, AddContactActivity::class.java))
            }

            // 短信编辑按钮
            binding.layoutEditSms.setOnClickListener {
                startActivity(Intent(this, SmsEditActivity::class.java))
            }

            // SOS短信模板选择
            binding.layoutSosTemplate.setOnClickListener {
                showSmsTemplateDialog("SOS短信模板", AppSettings.KEY_SOS_TEMPLATE_ID, SmsTemplate.DEFAULT_SOS_ID)
            }

            // 自动求助短信模板选择
            binding.layoutAutoHelpTemplate.setOnClickListener {
                showSmsTemplateDialog("自动求助短信模板", AppSettings.KEY_AUTO_HELP_TEMPLATE_ID, SmsTemplate.DEFAULT_AUTO_HELP_ID)
            }

            // 骑行求助短信模板选择
            binding.layoutCyclingTemplate.setOnClickListener {
                showSmsTemplateDialog("骑行求助短信模板", AppSettings.KEY_CYCLING_TEMPLATE_ID, SmsTemplate.DEFAULT_CYCLING_ID)
            }

            // SOS 模板选择
            binding.layoutSosTemplate.setOnClickListener {
                showTemplatePicker(true)
            }

            // 自动求助模板选择
            binding.layoutAutoHelpTemplate.setOnClickListener {
                showTemplatePicker(false)
            }

            // 设置项点击事件
            setupSettingsListeners()

            // 默认显示打卡 Tab
            switchTab(TAB_CHECKIN)

        } catch (e: Exception) {
            Log.e(TAG, "Error in setupUI", e)
        }
    }

    private fun switchTab(tab: Int) {
        try {
            currentTab = tab

            // 重置所有 Tab 样式
            binding.tabCheckin.isSelected = false
            binding.tabSos.isSelected = false
            binding.tabContacts.isSelected = false
            binding.tabSettings.isSelected = false

            // 隐藏所有内容
            binding.contentCheckin.visibility = View.GONE
            binding.contentSos.visibility = View.GONE
            binding.contentContacts.visibility = View.GONE
            binding.contentSettings.visibility = View.GONE

            when (tab) {
                TAB_CHECKIN -> {
                    binding.tabCheckin.isSelected = true
                    binding.contentCheckin.visibility = View.VISIBLE
                    if (!checkInImageLoaded) {
                        loadTodayCheckInImage()
                        checkInImageLoaded = true
                    }
                }
                TAB_SOS -> {
                    binding.tabSos.isSelected = true
                    binding.contentSos.visibility = View.VISIBLE
                }
                TAB_CONTACTS -> {
                    binding.tabContacts.isSelected = true
                    binding.contentContacts.visibility = View.VISIBLE
                    loadContacts()
                }
                TAB_SETTINGS -> {
                    binding.tabSettings.isSelected = true
                    binding.contentSettings.visibility = View.VISIBLE
                    loadSettings()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error switching tab", e)
        }
    }

    private fun loadData() {
        loadContacts()
        loadSmsTemplateNames()
    }

    private fun loadTodayCheckInImage() {
        lifecycleScope.launch {
            try {
                // 如果缓存未加载，先加载缓存
                if (!isCacheLoaded) {
                    loadImageCache()
                }
                
                // 使用缓存加载图片
                if (cachedImages.isNotEmpty()) {
                    val imagePath = if (cachedMode == "random") {
                        cachedImages.random()
                    } else {
                        val selected = cachedImages[cachedIndex % cachedImages.size]
                        cachedIndex = (cachedIndex + 1) % cachedImages.size
                        selected
                    }
                    loadImageToView(imagePath)
                } else {
                    val checkInImage = checkInImageSelector.selectTodayImage()
                    binding.ivCheckinImage.setImageResource(checkInImage.resourceId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading check-in image", e)
            }
        }
    }
    
    private suspend fun loadImageCache() {
        val dao = AppDatabase.getInstance(applicationContext).settingsDao()
        
        cachedLibrary = withContext(Dispatchers.IO) {
            dao.getString(AppSettings.KEY_ACTIVE_IMAGE_LIBRARY, "A")
        }
        
        val (imagesKey, modeKey, indexKey) = if (cachedLibrary == "A") {
            Triple(AppSettings.KEY_CHECKIN_IMAGES_A, AppSettings.KEY_IMAGE_MODE_A, "checkin_image_index_a")
        } else {
            Triple(AppSettings.KEY_CHECKIN_IMAGES_B, AppSettings.KEY_IMAGE_MODE_B, "checkin_image_index_b")
        }
        
        val imagesStr = withContext(Dispatchers.IO) {
            dao.getString(imagesKey, "")
        }
        
        if (imagesStr.isNotEmpty()) {
            cachedImages = imagesStr.split(",").filter { it.isNotEmpty() }
        }
        
        cachedMode = withContext(Dispatchers.IO) {
            dao.getString(modeKey, "random")
        }
        
        cachedIndex = withContext(Dispatchers.IO) {
            dao.getInt(indexKey, 0)
        }
        
        isCacheLoaded = true
    }
    
    private fun loadImageToView(imagePath: String) {
        Log.d(TAG, "Loading image: $imagePath")
        
        if (imagePath.startsWith("@drawable/")) {
            val resName = imagePath.removePrefix("@drawable/")
            val resId = resources.getIdentifier(resName, "drawable", packageName)
            
            if (resId != 0) {
                binding.ivCheckinImage.setImageResource(resId)
                Log.d(TAG, "Successfully loaded drawable: $resName")
            } else {
                Log.e(TAG, "Drawable resource not found: $resName")
            }
        } else if (File(imagePath).exists()) {
            binding.ivCheckinImage.setImageURI(android.net.Uri.fromFile(File(imagePath)))
            Log.d(TAG, "Successfully loaded file: $imagePath")
        } else {
            Log.e(TAG, "Image file not found: $imagePath")
        }
    }
    
    private fun loadNextCheckInImage() {
        Log.d(TAG, "loadNextCheckInImage - isCacheLoaded: $isCacheLoaded, images count: ${cachedImages.size}")
        
        if (!isCacheLoaded || cachedImages.isEmpty()) {
            Log.d(TAG, "Cache not ready, loading today's image")
            lifecycleScope.launch { 
                loadTodayCheckInImage()
                isFlipping = false
            }
            return
        }
        
        // 直接使用缓存切换到下一张图片
        val imagePath = if (cachedMode == "random") {
            cachedImages.random()
        } else {
            val selected = cachedImages[cachedIndex % cachedImages.size]
            cachedIndex = (cachedIndex + 1) % cachedImages.size
            selected
        }
        
        loadImageToView(imagePath)
        isFlipping = false
        
        // 后台保存索引
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getInstance(applicationContext).settingsDao()
            val indexKey = if (cachedLibrary == "A") "checkin_image_index_a" else "checkin_image_index_b"
            dao.putInt(indexKey, cachedIndex)
        }
    }

    /**
     * 切换打卡卡片（点击切换到下一张图片）
     */
    private var isFlipping = false

    private fun flipCard() {
        if (isFlipping) return
        
        isFlipping = true
        loadNextCheckInImage()
    }

    private fun performCheckIn() {
        lifecycleScope.launch {
            try {
                val dao = AppDatabase.getInstance(applicationContext).settingsDao()
                
                // 检查今天是否已打卡
                val lastCheckInTime = dao.getLong(AppSettings.KEY_LAST_CHECK_IN_TIME, 0L)
                val now = System.currentTimeMillis()
                val todayStart = java.util.Calendar.getInstance().apply {
                    timeInMillis = now
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
                val checkedInToday = lastCheckInTime >= todayStart
                
                if (checkedInToday) {
                    // 今天已打卡，取消打卡
                    withContext(Dispatchers.IO) {
                        dao.putLong(AppSettings.KEY_LAST_CHECK_IN_TIME, 0L)
                    }
                    
                    binding.btnCheckin.text = "打卡"
                    binding.btnCheckin.isEnabled = true
                    
                    // 不再需要翻回正面，直接刷新图片
                    
                    Handler(Looper.getMainLooper()).postDelayed({
                        loadTodayCheckInImage()
                    }, 2000)
                    
                    Toast.makeText(this@MainActivity, "已取消打卡", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Check-in cancelled")
                } else {
                    // 今天未打卡，执行打卡
                    withContext(Dispatchers.IO) {
                        dao.putLong(AppSettings.KEY_LAST_CHECK_IN_TIME, System.currentTimeMillis())
                        dao.putLong(AppSettings.KEY_LAST_OPEN_TIME, System.currentTimeMillis())
                    }
                    
                    binding.btnCheckin.text = "已打卡（点击取消）"
                    binding.btnCheckin.isEnabled = true
                    
                    // 切换到下一张图片
                    loadNextCheckInImage()
                    
                    // 自动翻转到下一张图片
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!isCardFlipped) {
                            flipCard()
                        }
                    }, 300)
                    
                    Handler(Looper.getMainLooper()).postDelayed({
                        binding.btnCheckin.text = "已打卡"
                    }, 2000)
                    
                    Toast.makeText(this@MainActivity, "打卡成功！再次点击可取消", Toast.LENGTH_LONG).show()
                    Log.d(TAG, "Check-in successful")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error performing check-in", e)
                Toast.makeText(this@MainActivity, "打卡失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadContacts() {
        lifecycleScope.launch {
            try {
                val dao = AppDatabase.getInstance(applicationContext).contactDao()
                contacts = withContext(Dispatchers.IO) {
                    dao.getAllContactsSync()
                }
                updateContactsUI()
                // 更新权限状态显示
                updatePermissionStatus()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading contacts", e)
            }
        }
    }

    private fun updateContactsUI() {
        try {
            if (contacts.isEmpty()) {
                binding.tvContactsEmpty.visibility = View.VISIBLE
                binding.rvContactsList.visibility = View.GONE
                binding.tvContactsCount.text = "暂无紧急联系人"
            } else {
                binding.tvContactsEmpty.visibility = View.GONE
                binding.rvContactsList.visibility = View.VISIBLE
                binding.tvContactsCount.text = "${contacts.size} 位紧急联系人"
                
                val adapter = ContactAdapter(contacts) { contact ->
                    deleteContact(contact)
                }
                binding.rvContactsList.layoutManager = LinearLayoutManager(this)
                binding.rvContactsList.adapter = adapter
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating contacts UI", e)
        }
    }

    private fun deleteContact(contact: Contact) {
        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定删除联系人 ${contact.name} 吗？")
            .setPositiveButton("确定") { dialog, _ ->
                lifecycleScope.launch {
                    try {
                        val dao = AppDatabase.getInstance(applicationContext).contactDao()
                        withContext(Dispatchers.IO) {
                            dao.delete(contact)
                        }
                        loadContacts()
                        Toast.makeText(this@MainActivity, "删除成功", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting contact", e)
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    inner class ContactAdapter(
        private val contacts: List<Contact>,
        private val onDelete: (Contact) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
            val tvName: TextView = itemView.findViewById(R.id.tv_contact_name)
            val tvPhone: TextView = itemView.findViewById(R.id.tv_contact_phone)
            val tvDelete: TextView = itemView.findViewById(R.id.tv_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_contact, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val contact = contacts[position]
            holder.tvName.text = contact.name
            holder.tvPhone.text = contact.phone
            holder.tvDelete.setOnClickListener {
                onDelete(contact)
            }
        }

        override fun getItemCount(): Int {
            return contacts.size
        }
    }

    // 守护时间更新已移除（守护说明卡片移至联系人Tab，不再需要UI更新）

    /**
     * 更新权限状态行（守护说明卡片内）
     * 根据实际权限状态显示「已获得☑」或「未获得☐」
     * 点击整行可跳转对应权限设置
     */
    private fun updatePermissionStatus() {
        try {
            // 短信权限状态
            val hasSms = PermissionHelper.hasSmsPermission(this)
            if (hasSms) {
                binding.tvSmsState.text = getString(R.string.permission_granted)
                binding.tvSmsState.setTextColor(getColor(R.color.system_green))
                binding.tvSmsCheck.text = "☑"
                binding.tvSmsCheck.setTextColor(getColor(R.color.system_green))
            } else {
                binding.tvSmsState.text = getString(R.string.permission_not_granted)
                binding.tvSmsState.setTextColor(getColor(R.color.system_red))
                binding.tvSmsCheck.text = "☐"
                binding.tvSmsCheck.setTextColor(getColor(R.color.system_red))
            }
            binding.layoutPermissionSms.setOnClickListener {
                PermissionHelper.openAppSettings(this)
            }

            // 位置权限状态：区分「使用时允许」和「始终允许」
            val hasForeground = PermissionHelper.hasForegroundLocation(this)
            val hasBackground = PermissionHelper.hasBackgroundLocation(this)
            if (hasBackground) {
                // 获得了后台位置权限（始终允许）
                binding.tvLocationLabel.text = "位置权限（需始终允许）"
                binding.tvLocationState.text = "已获得（始终允许）"
                binding.tvLocationState.setTextColor(getColor(R.color.system_green))
                binding.tvLocationCheck.text = "☑"
                binding.tvLocationCheck.setTextColor(getColor(R.color.system_green))
            } else if (hasForeground) {
                // 只获得了前台位置权限，但需要的是始终允许
                binding.tvLocationLabel.text = "位置权限（需始终允许）"
                binding.tvLocationState.text = "未获得（始终允许）"
                binding.tvLocationState.setTextColor(getColor(R.color.system_red))
                binding.tvLocationCheck.text = "☐"
                binding.tvLocationCheck.setTextColor(getColor(R.color.system_red))
            } else {
                // 未获得任何位置权限
                binding.tvLocationLabel.text = "位置权限（需始终允许）"
                binding.tvLocationState.text = "未获得（始终允许）"
                binding.tvLocationState.setTextColor(getColor(R.color.system_red))
                binding.tvLocationCheck.text = "☐"
                binding.tvLocationCheck.setTextColor(getColor(R.color.system_red))
            }
            binding.layoutPermissionLocation.setOnClickListener {
                PermissionHelper.openLocationSettings(this)
            }

            // GPS 定位服务状态
            val locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
            val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
            if (isGpsEnabled) {
                binding.tvGpsState.text = "已开启"
                binding.tvGpsState.setTextColor(getColor(R.color.system_green))
                binding.tvGpsCheck.text = "☑"
                binding.tvGpsCheck.setTextColor(getColor(R.color.system_green))
            } else {
                binding.tvGpsState.text = "未开启"
                binding.tvGpsState.setTextColor(getColor(R.color.system_red))
                binding.tvGpsCheck.text = "☐"
                binding.tvGpsCheck.setTextColor(getColor(R.color.system_red))
            }
            binding.layoutGpsStatus.setOnClickListener {
                // 打开位置设置页面
                val intent = android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating permission status", e)
        }
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            try {
                val dao = AppDatabase.getInstance(applicationContext).settingsDao()

                val bootAutoStart = withContext(Dispatchers.IO) {
                    dao.getBoolean(AppSettings.KEY_BOOT_AUTO_START, true)
                }

                binding.switchBootAutoStart.isChecked = bootAutoStart

                // 触发超时（小时）
                val timeoutHours = withContext(Dispatchers.IO) {
                    dao.getInt(AppSettings.KEY_TIMEOUT_HOURS, 12)
                }
                // 根据 timeoutHours 值显示对应文本
                binding.tvTimeoutValue.text = when (timeoutHours) {
                    0 -> "15秒（测试）"
                    1 -> "1小时"
                    else -> "${timeoutHours}小时"
                }

                // 检测频率（秒）
                val checkInterval = withContext(Dispatchers.IO) {
                    dao.getInt(AppSettings.KEY_CHECK_INTERVAL_SECONDS, 3600)
                }
                binding.tvCheckIntervalValue.text = formatCheckIntervalDisplay(checkInterval)

                loadTemplateNames(dao)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading settings", e)
            }
        }
    }

    private suspend fun loadTemplateNames(settingsDao: com.example.wohenhao.data.db.SettingsDao) {
        try {
            val templateDao = AppDatabase.getInstance(applicationContext).smsTemplateDao()

            val sosId = withContext(Dispatchers.IO) {
                settingsDao.getString(AppSettings.KEY_SOS_TEMPLATE_ID, SmsTemplate.DEFAULT_SOS_ID)
            }
            val autoHelpId = withContext(Dispatchers.IO) {
                settingsDao.getString(AppSettings.KEY_AUTO_HELP_TEMPLATE_ID, SmsTemplate.DEFAULT_AUTO_HELP_ID)
            }

            val sosTemplate = withContext(Dispatchers.IO) { templateDao.getTemplateById(sosId) }
            val autoHelpTemplate = withContext(Dispatchers.IO) { templateDao.getTemplateById(autoHelpId) }

            binding.tvSosTemplateName.text = sosTemplate?.name ?: getString(R.string.settings_template_none)
            binding.tvAutoHelpTemplateName.text = autoHelpTemplate?.name ?: getString(R.string.settings_template_none)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading template names", e)
        }
    }

    private fun showTemplatePicker(isSos: Boolean) {
        lifecycleScope.launch {
            try {
                val templates = withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(applicationContext).smsTemplateDao().getAllTemplatesSync()
                }

                if (templates.isEmpty()) {
                    Toast.makeText(this@MainActivity, "请先添加短信模板", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val settingsKey = if (isSos) AppSettings.KEY_SOS_TEMPLATE_ID else AppSettings.KEY_AUTO_HELP_TEMPLATE_ID
                val currentId = withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(applicationContext).settingsDao().getString(settingsKey, "")
                }

                val names = templates.map { it.name }.toTypedArray()
                val ids = templates.map { it.id }
                val currentIndex = ids.indexOf(currentId).coerceAtLeast(0)
                val titleRes = if (isSos) R.string.settings_template_picker_sos else R.string.settings_template_picker_auto_help

                AlertDialog.Builder(this@MainActivity)
                    .setTitle(titleRes)
                    .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                        lifecycleScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    AppDatabase.getInstance(applicationContext).settingsDao()
                                        .putString(settingsKey, ids[which])
                                }
                                if (isSos) {
                                    binding.tvSosTemplateName.text = templates[which].name
                                } else {
                                    binding.tvAutoHelpTemplateName.text = templates[which].name
                                }
                                Toast.makeText(this@MainActivity, "已选择「${templates[which].name}」", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error saving template selection", e)
                            }
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } catch (e: Exception) {
                Log.e(TAG, "Error showing template picker", e)
                Toast.makeText(this@MainActivity, "加载模板失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSettingsListeners() {
        binding.switchBootAutoStart.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                try {
                    AppDatabase.getInstance(applicationContext).settingsDao()
                        .putBoolean(AppSettings.KEY_BOOT_AUTO_START, isChecked)
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving boot auto start", e)
                }
            }
        }

        binding.layoutTimeout.setOnClickListener {
            showTimeoutDialog()
        }

        binding.layoutCheckInterval.setOnClickListener {
            showCheckIntervalDialog()
        }

        binding.layoutFrontImageSettings.setOnClickListener {
            val intent = Intent(this, ImageSettingsActivity::class.java)
            intent.putExtra("library", "A")
            startActivity(intent)
        }

        binding.layoutBackImageSettings.setOnClickListener {
            val intent = Intent(this, ImageSettingsActivity::class.java)
            intent.putExtra("library", "B")
            startActivity(intent)
        }

        binding.tvLibraryA.setOnClickListener {
            setActiveLibrary("A")
        }

        binding.tvLibraryB.setOnClickListener {
            setActiveLibrary("B")
        }

        loadActiveLibrary()
    }

    private fun loadActiveLibrary() {
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(applicationContext).settingsDao()
            val library = withContext(Dispatchers.IO) {
                dao.getString(AppSettings.KEY_ACTIVE_IMAGE_LIBRARY, "A")
            }

            updateLibraryButtons(library)
        }
    }

    private fun setActiveLibrary(library: String) {
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(applicationContext).settingsDao()
            withContext(Dispatchers.IO) {
                dao.putString(AppSettings.KEY_ACTIVE_IMAGE_LIBRARY, library)
            }

            updateLibraryButtons(library)
            
            // 重置缓存状态，强制重新加载对应图库的图片
            isCacheLoaded = false
            cachedImages = emptyList()
            
            loadTodayCheckInImage()
        }
    }

    private fun updateLibraryButtons(library: String) {
        if (library == "A") {
            binding.tvLibraryA.setBackgroundResource(R.drawable.bg_button_blue)
            binding.tvLibraryA.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            binding.tvLibraryB.setBackgroundResource(R.color.bg_gray)
            binding.tvLibraryB.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        } else {
            binding.tvLibraryB.setBackgroundResource(R.drawable.bg_button_blue)
            binding.tvLibraryB.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            binding.tvLibraryA.setBackgroundResource(R.color.bg_gray)
            binding.tvLibraryA.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    /**
     * 触发超时设置（多久未打卡触发自动求助）
     */
    private fun showTimeoutDialog() {
        val options = arrayOf("15秒（测试）", "1小时", "12小时", "24小时", "48小时", "72小时")
        val values = arrayOf(0, 1, 12, 24, 48, 72) // 0=15秒测试模式, 其他为小时

        lifecycleScope.launch {
            val currentTimeout = try {
                withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(applicationContext).settingsDao()
                        .getInt(AppSettings.KEY_TIMEOUT_HOURS, 12)
                }
            } catch (e: Exception) {
                12
            }

            val selectedIndex = values.indexOf(currentTimeout).coerceAtLeast(0)

            AlertDialog.Builder(this@MainActivity)
                .setTitle("自动求助触发时间")
                .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                    val selectedValue = values[which]
                    lifecycleScope.launch(Dispatchers.IO) {
                        AppDatabase.getInstance(applicationContext).settingsDao()
                            .putInt(AppSettings.KEY_TIMEOUT_HOURS, selectedValue)
                    }
                    binding.tvTimeoutValue.text = options[which]
                    
                    // 如果选择了15秒测试模式，提示用户
                    if (selectedValue == 0) {
                        Toast.makeText(
                            this@MainActivity,
                            "已设为15秒测试模式，请谨慎使用",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "触发时间已设为「${options[which]}」",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    
                    dialog.dismiss()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    /**
     * 检测频率设置（多久检测一次是否需要求助）
     */
    private fun showCheckIntervalDialog() {
        val options = arrayOf("15秒（测试）", "1小时", "12小时")
        val values = arrayOf(15, 3600, 43200) // 秒：15秒、1小时、12小时

        lifecycleScope.launch {
            val currentInterval = try {
                withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(applicationContext).settingsDao()
                        .getInt(AppSettings.KEY_CHECK_INTERVAL_SECONDS, 3600)
                }
            } catch (e: Exception) {
                3600
            }

            val selectedIndex = values.indexOf(currentInterval).coerceAtLeast(0)

            AlertDialog.Builder(this@MainActivity)
                .setTitle("检测频率")
                .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                    val selectedSeconds = values[which]
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            AppDatabase.getInstance(applicationContext).settingsDao()
                                .putInt(AppSettings.KEY_CHECK_INTERVAL_SECONDS, selectedSeconds)
                        }
                        binding.tvCheckIntervalValue.text = formatCheckIntervalDisplay(selectedSeconds)

                        // 重新调度守护 Worker
                        rescheduleGuardianWorker(selectedSeconds)

                        Toast.makeText(
                            this@MainActivity,
                            "检测频率已设为「${options[which]}」",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    dialog.dismiss()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    /**
     * 格式化检测频率显示文本
     */
    private fun formatCheckIntervalDisplay(seconds: Int): String {
        return when (seconds) {
            15 -> "15秒"
            3600 -> "1小时"
            43200 -> "12小时"
            else -> {
                val hours = seconds / 3600
                val mins = (seconds % 3600) / 60
                if (hours > 0 && mins > 0) "${hours}小时${mins}分"
                else if (hours > 0) "${hours}小时"
                else if (mins > 0) "${mins}分钟"
                else "${seconds}秒"
            }
        }
    }

    /**
     * 重新调度守护 Worker（修改检测频率后调用）
     */
    private fun rescheduleGuardianWorker(intervalSeconds: Int) {
        try {
            val workManager = WorkManager.getInstance(applicationContext)

            // 取消所有旧的守护 Worker
            workManager.cancelAllWorkByTag(Constants.WORKER_GUARDIAN)
            workManager.cancelUniqueWork("quick_guardian_check")

            if (intervalSeconds <= 60) {
                // 短间隔（15秒等）：用 OneTimeWorkRequest 链式调度
                Log.d(TAG, "Short interval mode: ${intervalSeconds}s, using OneTimeWorkRequest")
                val oneTimeWork = OneTimeWorkRequestBuilder<GuardianWorker>()
                    .setInitialDelay(intervalSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                    .addTag(Constants.WORKER_GUARDIAN)
                    .addTag("quick_guardian_check")
                    .build()

                workManager.enqueueUniqueWork(
                    "quick_guardian_check",
                    ExistingWorkPolicy.REPLACE,
                    oneTimeWork
                )
            } else {
                // 长间隔（1小时、4小时）：用 PeriodicWorkRequest
                val periodicWork = androidx.work.PeriodicWorkRequestBuilder<GuardianWorker>(
                    intervalSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS
                )
                    .addTag(Constants.WORKER_GUARDIAN)
                    .build()

                workManager.enqueueUniquePeriodicWork(
                    Constants.WORKER_GUARDIAN,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    periodicWork
                )
            }

            Log.d(TAG, "GuardianWorker rescheduled with interval: ${intervalSeconds}s")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reschedule GuardianWorker", e)
        }
    }

    private fun updateLastOpenTime() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(applicationContext).settingsDao()
                        .putLong(AppSettings.KEY_LAST_OPEN_TIME, System.currentTimeMillis())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating last open time", e)
            }
        }
    }

    private fun showSOSConfirmDialog() {
        if (contacts.isEmpty()) {
            Toast.makeText(this, getString(R.string.sos_no_contacts), Toast.LENGTH_SHORT).show()
            return
        }

        // 使用自定义 View 来放大按钮
        val dialogView = layoutInflater.inflate(R.layout.dialog_sos_confirm, null)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        dialogView.findViewById<android.widget.TextView>(R.id.tv_dialog_title).text = getString(R.string.sos_confirm_title)
        dialogView.findViewById<android.widget.TextView>(R.id.tv_dialog_message).text = getString(R.string.sos_confirm_message, contacts.size)
        
        dialogView.findViewById<android.widget.Button>(R.id.btn_dialog_confirm).apply {
            text = getString(R.string.sos_confirm)
            setOnClickListener {
                dialog.dismiss()
                triggerSOS()
            }
        }
        
        dialogView.findViewById<android.widget.Button>(R.id.btn_dialog_cancel).apply {
            text = getString(R.string.cancel)
            setOnClickListener {
                dialog.dismiss()
            }
        }
        
        dialog.show()
    }

    private fun triggerSOS() {
        if (contacts.isEmpty()) {
            Toast.makeText(this, R.string.sos_no_contacts, Toast.LENGTH_SHORT).show()
            return
        }

        binding.tvSosStatus.text = getString(R.string.sos_sending)
        binding.tvSosStatus.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val locationHelper = LocationHelper(applicationContext)
                val locationResult = locationHelper.getCurrentLocation()

                val latitude = locationResult.getOrNull()?.latitude ?: 0.0
                val longitude = locationResult.getOrNull()?.longitude ?: 0.0

                val results = SmsHelper.sendEmergencyMessages(
                    applicationContext,
                    contacts,
                    latitude,
                    longitude,
                    isSOS = true
                )

                val successCount = results.values.count { it }

                withContext(Dispatchers.IO) {
                    val record = com.example.wohenhao.data.db.MessageRecord(
                        type = "sos",
                        latitude = latitude,
                        longitude = longitude,
                        recipients = contacts.joinToString(",") { it.phone },
                        status = if (successCount == contacts.size) "success" else "partial"
                    )
                    AppDatabase.getInstance(applicationContext).messageRecordDao().insert(record)
                }

                binding.tvSosStatus.text = getString(R.string.sos_success, successCount)

                Handler(Looper.getMainLooper()).postDelayed({
                    binding.tvSosStatus.visibility = View.GONE
                }, 5000)

            } catch (e: Exception) {
                Log.e(TAG, "Error triggering SOS", e)
                binding.tvSosStatus.text = getString(R.string.sos_failed)
            }
        }
    }

    // ==================== 骑行守护模式 ====================

    private fun startCyclingMode() {
        // 检查位置权限
        if (!PermissionHelper.hasLocationPermission(this)) {
            Toast.makeText(this, "需要位置权限才能使用骑行守护", Toast.LENGTH_LONG).show()
            return
        }

        // 检查是否有联系人
        if (contacts.isEmpty()) {
            Toast.makeText(this, "请先添加紧急联系人", Toast.LENGTH_LONG).show()
            return
        }

        // 启动骑行服务
        com.example.wohenhao.service.CyclingService.start(this)

        // 更新 UI
        binding.btnCycling.visibility = android.view.View.GONE
        binding.layoutCyclingStatus.visibility = android.view.View.VISIBLE

        Toast.makeText(this, "🚴 骑行守护已开启！", Toast.LENGTH_LONG).show()
        Log.d(TAG, "Cycling mode started")
    }

    private fun stopCyclingMode() {
        // 停止骑行服务
        com.example.wohenhao.service.CyclingService.stop(this)

        // 更新 UI
        binding.btnCycling.visibility = android.view.View.VISIBLE
        binding.layoutCyclingStatus.visibility = android.view.View.GONE

        Toast.makeText(this, "骑行守护已结束", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Cycling mode stopped")
    }

    // ==================== 短信模板选择 ====================

    private fun showSmsTemplateDialog(title: String, settingsKey: String, defaultId: String) {
        lifecycleScope.launch {
            try {
                val database = AppDatabase.getInstance(this@MainActivity)
                val templates = database.smsTemplateDao().getAllTemplatesSync()
                
                if (templates.isEmpty()) {
                    Toast.makeText(this@MainActivity, "暂无短信模板", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val currentTemplateId = database.settingsDao().getString(settingsKey, defaultId)
                val templateNames = templates.map { it.name }.toTypedArray()
                val selectedIndex = templates.indexOfFirst { it.id == currentTemplateId }.coerceAtLeast(0)
                
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(title)
                    .setSingleChoiceItems(templateNames, selectedIndex) { dialog, which ->
                        lifecycleScope.launch {
                            val selectedTemplate = templates[which]
                            database.settingsDao().putString(settingsKey, selectedTemplate.id)
                            Toast.makeText(this@MainActivity, "已选择: ${selectedTemplate.name}", Toast.LENGTH_SHORT).show()
                            
                            // 刷新显示
                            loadSmsTemplateNames()
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } catch (e: Exception) {
                Log.e(TAG, "Error showing SMS template dialog", e)
            }
        }
    }

    private fun loadSmsTemplateNames() {
        lifecycleScope.launch {
            try {
                val database = AppDatabase.getInstance(this@MainActivity)
                
                // 加载 SOS 模板名称
                val sosTemplateId = database.settingsDao().getString(
                    AppSettings.KEY_SOS_TEMPLATE_ID, 
                    SmsTemplate.DEFAULT_SOS_ID
                )
                val sosTemplate = database.smsTemplateDao().getTemplateById(sosTemplateId)
                binding.tvSosTemplateName.text = sosTemplate?.name ?: "SOS紧急求助"
                
                // 加载自动求助模板名称
                val autoHelpTemplateId = database.settingsDao().getString(
                    AppSettings.KEY_AUTO_HELP_TEMPLATE_ID, 
                    SmsTemplate.DEFAULT_AUTO_HELP_ID
                )
                val autoHelpTemplate = database.smsTemplateDao().getTemplateById(autoHelpTemplateId)
                binding.tvAutoHelpTemplateName.text = autoHelpTemplate?.name ?: "超时自动求助"
                
                // 加载骑行模板名称
                val cyclingTemplateId = database.settingsDao().getString(
                    AppSettings.KEY_CYCLING_TEMPLATE_ID, 
                    SmsTemplate.DEFAULT_CYCLING_ID
                )
                val cyclingTemplate = database.smsTemplateDao().getTemplateById(cyclingTemplateId)
                binding.tvCyclingTemplate.text = cyclingTemplate?.name ?: "骑行守护求助"
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading SMS template names", e)
            }
        }
    }
}

