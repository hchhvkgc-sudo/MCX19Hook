package com.linyu.mcx19hook.ui

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import com.linyu.mcx19hook.config.ConfigManager
import com.linyu.mcx19hook.hook.NativeHooks
import com.linyu.mcx19hook.hook.EntityData
import timber.log.Timber

class FloatingWindowService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isVisible = false
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = Runnable { updateEsp() }

    companion object {
        private const val TAG = "MCX19Hook-Float"
        private var instance: FloatingWindowService? = null
        fun getInstance() = instance

        fun startIfEnabled(context: Context) {
            if (ConfigManager.isGuiOverlayEnabled()) {
                val intent = Intent(context, FloatingWindowService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun toggleVisibility() {
            instance?.run {
                isVisible = !isVisible
                floatingView?.visibility = if (isVisible) View.VISIBLE else View.GONE
            }
        }

        fun drawEsp(viewMatrix: FloatArray, entities: List<Any>) {
            instance?.queueEspDraw(viewMatrix, entities)
        }

        fun drawGuiOverlay(poseStack: Any, mouseX: Int, mouseY: Int) {
            instance?.queueGuiDraw(poseStack, mouseX, mouseY)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createFloatingView()
        startForeground(1001, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        removeFloatingView()
        handler.removeCallbacks(updateRunnable)
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): android.app.Notification {
        val channelId = "mcx19hook_float"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId, "MCX19Hook Floating Window",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }
        return android.app.Notification.Builder(this, channelId)
            .setContentTitle("MCX19Hook")
            .setContentText("悬浮窗运行中 - 点击展开菜单")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun createFloatingView() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window, null)
        setupFloatingView(floatingView!!)
        windowManager?.addView(floatingView, layoutParams)
    }

    private fun setupFloatingView(view: View) {
        val toggleBtn = view.findViewById<ImageButton>(R.id.btn_toggle)
        val menuLayout = view.findViewById<LinearLayout>(R.id.menu_layout)
        val closeBtn = view.findViewById<ImageButton>(R.id.btn_close)

        // 拖拽移动
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    layoutParams?.let { params ->
                        params.x = (event.rawX - event.x).toInt()
                        params.y = (event.rawY - event.y).toInt()
                        windowManager?.updateViewLayout(v, params)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams?.let { params ->
                        params.x = (event.rawX - event.x).toInt()
                        params.y = (event.rawY - event.y).toInt()
                        windowManager?.updateViewLayout(v, params)
                    }
                    true
                }
                else -> false
            }
        }

        toggleBtn.setOnClickListener {
            menuLayout.visibility = if (menuLayout.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        closeBtn.setOnClickListener {
            stopSelf()
        }

        // 功能开关按钮
        setupFeatureToggles(menuLayout)
    }

    private fun setupFeatureToggles(menu: LinearLayout) {
        val features = listOf(
            "透视 (ESP)" to ConfigManager::isEspEnabled to ConfigManager::setEspEnabled,
            "自瞄 (Aimbot)" to ConfigManager::isAimbotEnabled to ConfigManager::setAimbotEnabled,
            "飞行 (Fly)" to ConfigManager::isFlyEnabled to ConfigManager::setFlyEnabled,
            "穿墙 (Noclip)" to ConfigManager::isNoclipEnabled to ConfigManager::setNoclipEnabled,
            "无敌 (GodMode)" to ConfigManager::isGodModeEnabled to ConfigManager::setGodModeEnabled,
            "无限物品" to ConfigManager::isInfiniteItemsEnabled to ConfigManager::setInfiniteItemsEnabled,
            "加速 (Speed)" to ConfigManager::isSpeedEnabled to ConfigManager::setSpeedEnabled,
            "长臂 (Reach)" to ConfigManager::isReachEnabled to ConfigManager::setReachEnabled,
            "X-Ray" to ConfigManager::isXrayEnabled to ConfigManager::setXrayEnabled,
            "实体发光" to ConfigManager::isEntityGlowEnabled to ConfigManager::setEntityGlowEnabled,
            "瞬间挖掘" to ConfigManager::isInstantMineEnabled to ConfigManager::setInstantMineEnabled,
        )

        features.forEach { (name, getter, setter) ->
            val switchView = LayoutInflater.from(this).inflate(R.layout.item_feature_toggle, menu, false)
            val tv = switchView.findViewById<TextView>(R.id.tv_feature_name)
            val sw = switchView.findViewById<Switch>(R.id.sw_feature)
            tv.text = name
            sw.isChecked = getter()
            sw.setOnCheckedChangeListener { _, checked -> setter(checked) }
            menu.addView(switchView)
        }

        // 滑块设置
        addSeekBar(menu, "自瞄 FOV", 10, 180, ConfigManager.getAimbotFov().toInt()) { v -> ConfigManager.setAimbotFov(v.toFloat()) }
        addSeekBar(menu, "自瞄平滑", 1, 20, ConfigManager.getAimbotSmooth().toInt()) { v -> ConfigManager.setAimbotSmooth(v.toFloat()) }
        addSeekBar(menu, "速度倍率", 10, 50, (ConfigManager.getSpeedMultiplier() * 10).toInt()) { v -> ConfigManager.setSpeedMultiplier(v / 10f) }
        addSeekBar(menu, "长臂距离", 3, 20, ConfigManager.getReachDistance().toInt()) { v -> ConfigManager.setReachDistance(v.toFloat()) }
        addSeekBar(menu, "ESP 最大距离", 20, 300, ConfigManager.getEspMaxDistance()) { v -> ConfigManager.setEspMaxDistance(v) }
    }

    private fun addSeekBar(menu: LinearLayout, title: String, min: Int, max: Int, current: Int, onChange: (Int) -> Unit) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_seekbar, menu, false)
        val tv = view.findViewById<TextView>(R.id.tv_seekbar_title)
        val sb = view.findViewById<SeekBar>(R.id.seekbar)
        val tvVal = view.findViewById<TextView>(R.id.tv_seekbar_value)
        tv.text = title
        sb.max = max - min
        sb.progress = current - min
        tvVal.text = current.toString()
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + min
                tvVal.text = value.toString()
                onChange(value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        menu.addView(view)
    }

    private fun removeFloatingView() {
        floatingView?.let { windowManager?.removeView(it) }
        floatingView = null
    }

    // ==================== ESP 绘制队列 ====================
    private val espQueue = java.util.concurrent.ConcurrentLinkedQueue<EspDrawData>()
    private val guiQueue = java.util.concurrent.ConcurrentLinkedQueue<GuiDrawData>()

    private data class EspDrawData(val viewMatrix: FloatArray, val entities: List<EntityData>)
    private data class GuiDrawData(val poseStack: Any, val mouseX: Int, val mouseY: Int)

    private fun queueEspDraw(viewMatrix: FloatArray, entities: List<Any>) {
        val list = entities.filterIsInstance<EntityData>().toList()
        espQueue.add(EspDrawData(viewMatrix, list))
        if (espQueue.size > 2) espQueue.poll() // 保留最新两帧
    }

    private fun queueGuiDraw(poseStack: Any, mouseX: Int, mouseY: Int) {
        guiQueue.add(GuiDrawData(poseStack, mouseX, mouseY))
        if (guiQueue.size > 2) guiQueue.poll()
    }

    private fun updateEsp() {
        val floatingView = this.floatingView ?: return
        val canvasView = floatingView.findViewById<View>(R.id.canvas_esp) ?: return

        // 获取最新帧数据
        val espData = espQueue.poll() ?: return
        val guiData = guiQueue.poll()

        // 在 Canvas 上绘制
        val bitmap = Bitmap.createBitmap(canvasView.width, canvasView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // 绘制 ESP
        if (ConfigManager.isEspEnabled()) {
            drawEntities(canvas, espData.viewMatrix, espData.entities)
        }

        // 绘制 GUI 覆盖
        if (ConfigManager.isGuiOverlayEnabled() && guiData != null) {
            drawGuiOverlay(canvas, guiData.mouseX, guiData.mouseY)
        }

        // 显示到 View
        canvasView.post { (canvasView as ImageView).setImageBitmap(bitmap) }

        handler.postDelayed(updateRunnable, 16) // ~60fps
    }

    private fun drawEntities(canvas: Canvas, viewMatrix: FloatArray, entities: List<EntityData>) {
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 2f
            textSize = 24f
        }

        entities.forEach { ent ->
            val screen = worldToScreen(ent.x, ent.y, ent.z, viewMatrix, canvas.width, canvas.height)
            if (screen == null) return@forEach

            // 根据实体类型选择颜色
            paint.color = when {
                ent.isPlayer -> Color.RED
                ent.entityType == 100 -> Color.GREEN // 假设生物
                ent.entityType == 200 -> Color.YELLOW // 假设箱子
                else -> Color.WHITE
            }

            // 绘制 2D 方框
            val boxSize = (200f / Math.max(1f, ent.z)).toInt() // 简单透视缩放
            val left = screen.x - boxSize / 2
            val top = screen.y - boxSize / 2
            val right = screen.x + boxSize / 2
            val bottom = screen.y + boxSize / 2

            canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint)

            // 绘制名称和血量
            paint.style = Paint.Style.FILL
            canvas.drawText("${ent.name} [${ent.health}]", screen.x.toFloat(), (top - 10).toFloat(), paint)
            paint.style = Paint.Style.STROKE
        }
    }

    private fun drawGuiOverlay(canvas: Canvas, mouseX: Int, mouseY: Int) {
        // 绘制准星、状态信息等
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        val cx = canvas.width / 2
        val cy = canvas.height / 2

        // 准星
        val crossSize = 10
        canvas.drawLine((cx - crossSize).toFloat(), cy.toFloat(), (cx + crossSize).toFloat(), cy.toFloat(), paint)
        canvas.drawLine(cx.toFloat(), (cy - crossSize).toFloat(), cx.toFloat(), (cy + crossSize).toFloat(), paint)

        // 状态文本
        paint.style = Paint.Style.FILL
        paint.textSize = 32f
        var y = 100f
        fun drawStatus(text: String, color: Int) {
            paint.color = color
            canvas.drawText(text, 50f, y, paint)
            y += 40f
        }

        drawStatus("MCX19Hook Active", Color.GREEN)
        if (ConfigManager.isEspEnabled()) drawStatus("ESP: ON", Color.CYAN)
        if (ConfigManager.isAimbotEnabled()) drawStatus("AIMBOT: ON", Color.RED)
        if (ConfigManager.isFlyEnabled()) drawStatus("FLY: ON", Color.YELLOW)
        if (ConfigManager.isGodModeEnabled()) drawStatus("GODMODE: ON", Color.MAGENTA)
        if (ConfigManager.isInfiniteItemsEnabled()) drawStatus("INF ITEMS: ON", Color.GREEN)
    }

    // 世界坐标转屏幕坐标
    private fun worldToScreen(x: Float, y: Float, z: Float, matrix: FloatArray, w: Int, h: Int): Point? {
        val clipX = x * matrix[0] + y * matrix[4] + z * matrix[8] + matrix[12]
        val clipY = x * matrix[1] + y * matrix[5] + z * matrix[9] + matrix[13]
        val clipZ = x * matrix[2] + y * matrix[6] + z * matrix[10] + matrix[14]
        val clipW = x * matrix[3] + y * matrix[7] + z * matrix[11] + matrix[15]

        if (clipW < 0.1f) return null

        val ndcX = clipX / clipW
        val ndcY = clipY / clipW
        val ndcZ = clipZ / clipW

        if (ndcZ < 0 || ndcZ > 1) return null

        val sx = (ndcX + 1) * 0.5f * w
        val sy = (1 - ndcY) * 0.5f * h

        return Point(sx.toInt(), sy.toInt())
    }
}