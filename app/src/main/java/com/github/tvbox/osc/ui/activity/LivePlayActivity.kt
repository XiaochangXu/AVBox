@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.github.tvbox.osc.ui.activity

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import com.github.tvbox.osc.ui.theme.enableTransparentEdgeToEdge
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.bean.Epginfo
import com.github.tvbox.osc.bean.LiveChannelGroup
import com.github.tvbox.osc.bean.LiveChannelItem
import com.github.tvbox.osc.bean.LiveSettingGroup
import com.github.tvbox.osc.player.KernelDecision
import com.github.tvbox.osc.player.KernelReusePolicy
import com.github.tvbox.osc.player.LivePlayerManager
import com.github.tvbox.osc.player.MyVideoView
import com.github.tvbox.osc.player.PlaybackService
import com.github.tvbox.osc.player.controller.ComposeLiveController
import com.github.tvbox.osc.player.engine.MediaSources
import com.github.tvbox.osc.player.state.PlayState
import com.github.tvbox.osc.ui.components.LocalSheetDismiss
import com.github.tvbox.osc.ui.components.SheetHostScaffold
import com.github.tvbox.osc.ui.theme.AVBoxTheme
import com.github.tvbox.osc.ui.theme.AppThemeState
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.LOG
import com.github.tvbox.osc.util.KV
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow

class LivePlayActivity : BaseActivity() {

    companion object {
        private const val TAG = "LivePlayActivity"
        private const val SYSBAR_APPEARANCE_REASSERT_DELAY_MS = 400L
        private const val CONNECT_TIMEOUT_SWITCH_DELAY = 3500L
        private val FORMAT_DATE1 = SimpleDateFormat("MM-dd", Locale.getDefault())
    }

    private val vm: LivePlayViewModel by viewModels()

    private val uiState: StateFlow<LivePlayUiState> get() = vm.state

    private var pageState: PageState
        get() = vm.state.value.frame.pageState
        set(value) {
            vm.updateFrame { it.copy(pageState = value) }
        }
    private var playState: PlayState
        get() = vm.state.value.player.playState
        set(value) {
            vm.updatePlayer { it.copy(playState = value) }
        }
    private var snapshotVisible: Boolean
        get() = vm.state.value.player.snapshotVisible
        set(value) {
            vm.updatePlayer { it.copy(snapshotVisible = value) }
        }
    private var snapshotBitmap: Bitmap?
        get() = vm.state.value.player.snapshotBitmap
        set(value) {
            vm.updatePlayer { it.copy(snapshotBitmap = value) }
        }
    private var fullScreen: Boolean
        get() = vm.state.value.frame.fullScreen
        set(value) {
            vm.updateFrame { it.copy(fullScreen = value) }
        }
    private var rotating: Boolean
        get() = vm.state.value.frame.rotating
        set(value) {
            vm.updateFrame { it.copy(rotating = value) }
        }
    private var overlayVisible: Boolean
        get() = vm.state.value.overlay.visible
        set(value) {
            vm.updateOverlay { it.copy(visible = value) }
        }
    private var isBackState: Boolean
        get() = vm.state.value.timeshift.isBackState
        set(value) {
            vm.updateTimeshift { it.copy(isBackState = value) }
        }
    private var epgSheetVisible: Boolean
        get() = vm.state.value.epg.sheetVisible
        set(value) {
            vm.updateEpg { it.copy(sheetVisible = value) }
        }
    private var settingsSheetVisible: Boolean
        get() = vm.state.value.settings.sheetVisible
        set(value) {
            vm.updateSettings { it.copy(sheetVisible = value) }
        }
    private var passwordDialogTarget: Pair<Int, Int>?
        get() = vm.state.value.passwordDialogTarget
        set(value) {
            vm.updatePasswordDialogTarget(value)
        }
    private var resolutionText: String
        get() = vm.state.value.player.resolutionText
        set(value) {
            vm.updatePlayer { it.copy(resolutionText = value) }
        }
    private var resolutionVisible: Boolean
        get() = vm.state.value.player.resolutionVisible
        set(value) {
            vm.updatePlayer { it.copy(resolutionVisible = value) }
        }
    private var showTimeOn: Boolean
        get() = vm.state.value.overlay.showTimeOn
        set(value) {
            vm.updateOverlay { it.copy(showTimeOn = value) }
        }
    private var showNetSpeedOn: Boolean
        get() = vm.state.value.overlay.showNetSpeedOn
        set(value) {
            vm.updateOverlay { it.copy(showNetSpeedOn = value) }
        }
    private var timeText: String
        get() = vm.state.value.overlay.timeText
        set(value) {
            vm.updateOverlay { it.copy(timeText = value) }
        }
    private var netSpeedText: String
        get() = vm.state.value.overlay.netSpeedText
        set(value) {
            vm.updateOverlay { it.copy(netSpeedText = value) }
        }
    private var gestureHintText: String?
        get() = vm.state.value.player.gestureHintText
        set(value) {
            vm.updatePlayer { it.copy(gestureHintText = value) }
        }
    private var tsPosition: Int
        get() = vm.state.value.timeshift.position
        set(value) {
            vm.updateTimeshift { it.copy(position = value) }
        }
    private var tsDuration: Int
        get() = vm.state.value.timeshift.duration
        set(value) {
            vm.updateTimeshift { it.copy(duration = value) }
        }
    private var channelInfoUi: ChannelInfoUi
        get() = vm.state.value.channelInfo
        set(value) {
            vm.updateChannelInfo(value)
        }
    private var mVideoView: MyVideoView? = null
    private var liveController: ComposeLiveController? = null
    private val mHandler = Handler(Looper.getMainLooper())
    private val liveChannelGroupList = ArrayList<LiveChannelGroup>()
    private var currentChannelGroupIndex: Int
        get() = vm.state.value.channelList.playingGroupIndex
        set(value) {
            vm.updateChannelList { it.copy(playingGroupIndex = value) }
        }
    private var currentLiveChannelIndex: Int
        get() = vm.state.value.channelList.playingChannelIndex
        set(value) {
            vm.updateChannelList { it.copy(playingChannelIndex = value) }
        }
    private var currentLiveLookBackIndex: Int
        get() = vm.state.value.epg.lookBackIndex
        set(value) {
            vm.updateEpg { it.copy(lookBackIndex = value) }
        }
    private var currentLiveChangeSourceTimes = 0
    private var currentLiveChannelItem: LiveChannelItem?
        get() = vm.state.value.channelList.playingChannel
        set(value) {
            vm.updateChannelList { it.copy(playingChannel = value) }
        }
    private val livePlayerManager = LivePlayerManager()
    private var epgdata: List<Epginfo>
        get() = vm.state.value.epg.epgList
        set(value) {
            vm.updateEpg { it.copy(epgList = value) }
        }
    private var epgChannelName: String
        get() = vm.state.value.epg.channelName
        set(value) {
            vm.updateEpg { it.copy(channelName = value) }
        }
    private var epgCanCatchup: Boolean
        get() = vm.state.value.epg.canCatchup
        set(value) {
            vm.updateEpg { it.copy(canCatchup = value) }
        }
    private var logoUrl: String? = null
    private var isSHIYI: Boolean
        get() = vm.state.value.timeshift.isShiyi
        set(value) {
            vm.updateTimeshift { it.copy(isShiyi = value) }
        }
    private var selectedChannelGroupIndex: Int
        get() = vm.state.value.channelList.tappedGroupIndex
        set(value) {
            vm.updateChannelList { it.copy(tappedGroupIndex = value) }
        }
    private var exitingLivePlay = false
    private var liveSettingGroupList: List<LiveSettingGroup> = ArrayList()
    private var nowday = Date()

    private val epgController = LiveEpgController(object : LiveEpgController.Host {
        override fun currentChannel(): LiveChannelItem? = currentLiveChannelItem

        override fun currentChannelHasLogo(): Boolean = !logoUrl.isNullOrEmpty()

        override fun onEpgListChanged(list: ArrayList<Epginfo>) {
            epgdata = list
        }

        override fun onEpgSettled() {
            overlay.updateChannelInfoUi()
        }
    })

    private val liveHost = object :
        LiveOverlayController.Host,
        LiveCatchupController.Host,
        LiveChannelSourceLoader.Host {

        override fun text(resId: Int, vararg args: Any): String = getString(resId, *args)

        override fun videoView(): MyVideoView? = mVideoView

        override fun cachedEpg(channelName: String): List<Epginfo>? = epgController.cachedEpg(channelName)

        override fun releasePlayerKernel() {
            this@LivePlayActivity.releasePlayerKernel()
        }

        override fun liveChannelHeader(): HashMap<String, String>? = this@LivePlayActivity.liveChannelHeader()

        override fun loadEpgAfterChannelStarted() {
            epgController.loadAfterChannelStarted()
        }

        override var logoUrl: String?
            get() = this@LivePlayActivity.logoUrl
            set(value) {
                this@LivePlayActivity.logoUrl = value
            }

        override fun initPlayer(view: MyVideoView) {
            livePlayerManager.init(view)
        }

        override fun initCatchup() {
            catchupController.initLiveObj()
        }

        override fun cancelEpgPending() {
            epgController.cancelPending()
        }

        override fun changeSourceTimeout(): Runnable = mConnectTimeoutChangeSourceRun

        override var changeSourceTimes: Int
            get() = currentLiveChangeSourceTimes
            set(value) {
                currentLiveChangeSourceTimes = value
            }

        override fun groups(): MutableList<LiveChannelGroup> = liveChannelGroupList

        override fun isNeedInputPassword(groupIndex: Int): Boolean = this@LivePlayActivity.isNeedInputPassword(groupIndex)

        override fun selectChannelGroup(groupIndex: Int, liveChannelIndex: Int) {
            this@LivePlayActivity.selectChannelGroup(groupIndex, liveChannelIndex)
        }

        override fun initLiveSettingGroupList() {
            this@LivePlayActivity.initLiveSettingGroupList()
        }

        override fun onChannelGroupsChanged() {
            this@LivePlayActivity.onChannelGroupsChanged()
        }

        override fun onSettingsInputsChanged() {
            this@LivePlayActivity.onSettingsInputsChanged()
        }

        override fun toast(msg: String) {
            Toast.makeText(this@LivePlayActivity, msg, Toast.LENGTH_SHORT).show()
        }
    }

    internal lateinit var overlay: LiveOverlayController

    internal lateinit var catchupController: LiveCatchupController

    internal lateinit var channelSourceLoader: LiveChannelSourceLoader

    override fun shouldRefreshAutoSize(): Boolean = true

    override fun hideSysBar() {
        if (fullScreen) super.hideSysBar()
    }

    override fun keepStatusBarHidden(): Boolean = fullScreen

    override fun init() {
        enableTransparentEdgeToEdge()
        applyStatusBarAppearance()
        vm.attachSettingsSource(settingSource)
        overlay = LiveOverlayController(vm, liveHost, mHandler)
        catchupController = LiveCatchupController(vm, liveHost, overlay)
        channelSourceLoader = LiveChannelSourceLoader(vm, liveHost, overlay, mHandler)
        syncLandscapeNow()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    epgSheetVisible -> epgSheetVisible = false
                    settingsSheetVisible -> settingsSheetVisible = false
                    fullScreen -> applyFullscreen(false)
                    isBackState -> catchupController.backToLiveFromEpg()
                    else -> {
                        exitingLivePlay = true
                        finish()
                    }
                }
            }
        })
        epgController.reloadAddress()
        nowday = Date()
        epgController.setDayKey(FORMAT_DATE1.format(nowday))
        initVideoView()
        findViewById<ComposeView>(R.id.compose_view).setContent {
            AVBoxTheme(manageStatusBarIcons = false) {
                SheetHostScaffold {
                    val state by uiState.collectAsStateWithLifecycle()
                    LiveScreen(state = state, actions = remember { liveActions() })
                }
            }
        }
        channelSourceLoader.initLiveChannelList()
        initLiveSettingGroupList()
    }

    override fun onResume() {
        super.onResume()
        applyStatusBarAppearance()
        exitingLivePlay = false
        val takenOverByVod = PlaybackService.peek()?.enterLiveState() ?: false
        rebindLiveControllerIfNeeded()
        if (takenOverByVod) {
            replayCurrentChannelAfterTakeover()
        } else {
            mVideoView?.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (!exitingLivePlay) mVideoView?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        overlay.hideSwitchChannelSnapshot()
        PlaybackService.peek()?.exitLive()
        mVideoView = null
        mHandler.removeCallbacksAndMessages(null)
        epgController.cancelAll()
        channelSourceLoader.cancelAll()
    }

    private fun initVideoView() {
        val controller = ComposeLiveController(this)
        controller.setListener(liveControlListener)
        liveController = controller
        val view = PlaybackService.engine(this).also { it.enterLive() }.player()
        view.setVideoController(controller)
        view.setProgressSink(null)
        mVideoView = view
    }

    private fun rebindLiveControllerIfNeeded() {
        val view = mVideoView ?: return
        val controller = liveController ?: return
        if (view.videoController !== controller) {
            view.setVideoController(controller)
            LOG.i("echo-p4 re-bind live controller")
        }
    }

    private fun replayCurrentChannelAfterTakeover() {
        val item = currentLiveChannelItem ?: return
        val videoView = mVideoView ?: return
        currentLiveLookBackIndex = -1
        isSHIYI = false
        isBackState = false
        overlayVisible = false
        overlay.stopTimeshiftTicker()
        overlay.hideSwitchChannelSnapshot()
        videoView.setUrl(item.url, liveChannelHeader())
        videoView.start()
        overlay.showResolutionAfterChannelSwitch()
        catchupController.loadEpgAfterChannelStarted()
    }

    internal fun releasePlayerKernel() {
        val eng = PlaybackService.peek()
        if (eng != null && !eng.isReleased()) eng.releasePlayer()
        else mVideoView?.release()
    }

    private val liveControlListener = object : ComposeLiveController.LiveControlListener {
        override fun onSingleTap(): Boolean {
            if (fullScreen) {
                overlayVisible = !overlayVisible
                if (overlayVisible) overlay.scheduleOverlayHide()
            } else {
                applyFullscreen(true)
            }
            return true
        }

        override fun onLongPress() {
            if (isBackState) {
                overlayVisible = true
                overlay.scheduleOverlayHide()
            } else {
                openSettingsSheet()
            }
        }

        override fun onPlayStateChanged(playState: PlayState) {
            this@LivePlayActivity.playState = playState
            handleAutoSourceSwitch(playState)
        }

        override fun onHorizontalFling(direction: Int) {
            if (direction > 0) playNext() else playPrevious()
        }

        override fun onGesturePercent(isBrightness: Boolean, percent: Int) {
            overlay.showGestureHint(isBrightness, percent)
        }
    }

    private fun handleAutoSourceSwitch(state: PlayState) {
        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun)
        when (state) {
            PlayState.IDLE, PlayState.PAUSED -> {}
            PlayState.PREPARED, PlayState.BUFFERED, PlayState.PLAYING -> {
                overlay.onPlaybackStarted()
                currentLiveChangeSourceTimes = 0
            }
            PlayState.ERROR, PlayState.COMPLETED -> {
                overlay.hideSwitchChannelSnapshot()
                mHandler.postDelayed(mConnectTimeoutChangeSourceRun, CONNECT_TIMEOUT_SWITCH_DELAY)
            }
            PlayState.PREPARING, PlayState.BUFFERING -> {
                mHandler.postDelayed(
                    mConnectTimeoutChangeSourceRun,
                    (KV.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 1) + 1) * 5000L,
                )
            }
            else -> LOG.i("echo-Unexpected live_play state: $state")
        }
    }

    fun applyFullscreen(full: Boolean) {
        if (fullScreen == full) return
        rotating = (full != (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE))
        fullScreen = full
        requestedOrientation = if (full) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            orientationPolicyValue()
        }
        if (full) {
            overlayVisible = true
            overlay.scheduleOverlayHide()
            super.hideSysBar()
        } else {
            overlayVisible = false
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.show(WindowInsetsCompat.Type.navigationBars())
            applyHideStatusBarPref()
            applyStatusBarAppearance()
            window.decorView.postDelayed({
                if (!isFinishing && !isDestroyed) applyStatusBarAppearance()
            }, SYSBAR_APPEARANCE_REASSERT_DELAY_MS)
        }
    }

    private fun applyStatusBarAppearance() {
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = !AppThemeState.isDark(systemDark)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        rotating = false
        syncLandscapeNow()
        applyStatusBarAppearance()
    }

    fun isFullBox(): Boolean = vm.state.value.frame.isFullBox

    private fun syncLandscapeNow() {
        val landNow = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        vm.updateFrame { it.copy(landscapeNow = landNow) }
    }

    private fun playChannel(channelGroupIndex: Int, liveChannelIndex: Int, changeSource: Boolean): Boolean {
        if ((channelGroupIndex == currentChannelGroupIndex && liveChannelIndex == currentLiveChannelIndex && !changeSource)
            || (changeSource && currentLiveChannelItem?.sourceNum == 1)
        ) {
            return true
        }
        val groupChannels = channelSourceLoader.getLiveChannels(channelGroupIndex)
        if (groupChannels == null || groupChannels.isEmpty() || liveChannelIndex < 0 || liveChannelIndex >= groupChannels.size) {
            return false
        }
        val showPreviousFrame = currentLiveChannelItem != null && mVideoView?.isPlaying == true
        val previousLivePlayerType = livePlayerManager.livePlayerType
        if (!changeSource) {
            currentChannelGroupIndex = channelGroupIndex
            currentLiveChannelIndex = liveChannelIndex
            currentLiveChannelItem = channelSourceLoader.getLiveChannels(currentChannelGroupIndex)?.get(currentLiveChannelIndex)
            KV.put(HawkConfig.LIVE_CHANNEL, currentLiveChannelItem?.channelName ?: "")
            requestChannelScroll()
        }
        epgChannelName = currentLiveChannelItem?.channelName.orEmpty()
        onSettingsInputsChanged()
        currentLiveLookBackIndex = -1
        isSHIYI = false
        isBackState = false
        overlayVisible = false
        overlay.stopTimeshiftTicker()
        val item = currentLiveChannelItem ?: return false
        item.include_back = canCurrentChannelCatchup()
        epgCanCatchup = item.include_back
        overlay.updateChannelInfoUi()
        val videoView = mVideoView
        if (videoView != null) {
            val rebuildKernel = videoView.consumeKernelRebuildRequired()
            val reusePlayer = KernelReusePolicy.decide(videoView.mediaPlayer != null, rebuildKernel, false,
                    canReusePlayer(previousLivePlayerType)) == KernelDecision.REUSE
            val keepExoFrame = reusePlayer
            if (showPreviousFrame && !keepExoFrame) {
                overlay.showSwitchChannelSnapshot()
            } else {
                overlay.hideSwitchChannelSnapshot()
            }
            val liveUrl = item.url
            if (reusePlayer) {
                videoView.setUrl(liveUrl, liveChannelHeader())
                videoView.replay(true)
            } else {
                releasePlayerKernel()
                videoView.setUrl(liveUrl, liveChannelHeader())
                videoView.start()
            }
            overlay.showResolutionAfterChannelSwitch()
        }
        catchupController.loadEpgAfterChannelStarted()
        return true
    }

    private fun canReusePlayer(previousLivePlayerType: Int): Boolean {
        val videoView = mVideoView ?: return false
        return videoView.playState != PlayState.IDLE &&
                previousLivePlayerType == livePlayerManager.livePlayerType
    }

    private fun playNext() {
        if (!isCurrentLiveChannelValid()) return
        val next = getNextChannel(1)
        playChannel(next[0], next[1], false)
    }

    private fun playPrevious() {
        if (!isCurrentLiveChannelValid()) return
        val next = getNextChannel(-1)
        playChannel(next[0], next[1], false)
    }

    private fun playNextSource() {
        if (!isCurrentLiveChannelValid()) return
        currentLiveChannelItem?.nextSource()
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true)
    }

    internal val mConnectTimeoutChangeSourceRun = Runnable {
        currentLiveChangeSourceTimes++
        if (currentLiveChannelItem?.sourceNum == currentLiveChangeSourceTimes) {
            currentLiveChangeSourceTimes = 0
            val next = getNextChannel(if (KV.get(HawkConfig.LIVE_CHANNEL_REVERSE, false)) -1 else 1)
            playChannel(next[0], next[1], false)
        } else {
            playNextSource()
        }
    }

    private fun getNextChannel(direction: Int): IntArray {
        return LiveChannelNavigator.nextPosition(
            groups = liveChannelGroupList,
            currentGroupIndex = currentChannelGroupIndex,
            currentChannelIndex = currentLiveChannelIndex,
            direction = direction,
            crossGroup = KV.get(HawkConfig.LIVE_CROSS_GROUP, false),
            channelsOf = { groupIndex -> channelSourceLoader.getLiveChannels(groupIndex) },
        )
    }

    private fun isCurrentLiveChannelValid(): Boolean {
        if (currentLiveChannelItem == null) {
            Toast.makeText(App.getInstance()!!, getString(R.string.live_please_select_channel), Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    internal fun selectChannelGroup(groupIndex: Int, liveChannelIndex: Int) {
        selectedChannelGroupIndex = groupIndex
        if (isNeedInputPassword(groupIndex)) {
            showPasswordDialog(groupIndex, liveChannelIndex)
            return
        }
        if (liveChannelIndex > -1) {
            loadChannelGroupDataAndPlay(groupIndex, liveChannelIndex)
        } else {
            expandChannelGroup(groupIndex)
        }
    }

    fun toggleChannelGroup(groupIndex: Int) {
        if (isChannelGroupExpanded(groupIndex)) {
            collapseChannelGroup(groupIndex)
            return
        }
        if (isNeedInputPassword(groupIndex)) {
            showPasswordDialog(groupIndex, -1)
            return
        }
        expandChannelGroup(groupIndex)
    }

    internal fun isChannelGroupExpanded(groupIndex: Int): Boolean =
        vm.state.value.channelList.expandedGroups.contains(groupIndex)

    internal fun expandChannelGroup(groupIndex: Int) {
        if (isChannelGroupExpanded(groupIndex)) return
        vm.updateChannelList { it.copy(expandedGroups = it.expandedGroups + groupIndex) }
    }

    private fun collapseChannelGroup(groupIndex: Int) {
        vm.updateChannelList { it.copy(expandedGroups = it.expandedGroups - groupIndex) }
    }

    private fun requestChannelScroll() {
        vm.updateChannelList { it.copy(scrollRequestId = it.scrollRequestId + 1) }
    }

    fun selectChannel(groupIndex: Int, position: Int) {
        selectedChannelGroupIndex = groupIndex
        clickLiveChannel(position)
    }

    private fun clickLiveChannel(position: Int) {
        playChannel(selectedChannelGroupIndex, position, false)
    }

    private fun loadChannelGroupDataAndPlay(groupIndex: Int, liveChannelIndex: Int) {
        selectedChannelGroupIndex = groupIndex
        expandChannelGroup(groupIndex)
        requestChannelScroll()
        if (liveChannelIndex > -1) {
            clickLiveChannel(liveChannelIndex)
        }
    }

    private fun showPasswordDialog(groupIndex: Int, liveChannelIndex: Int) {
        passwordDialogTarget = groupIndex to liveChannelIndex
    }

    internal fun onPasswordConfirmed(password: String) {
        val target = passwordDialogTarget ?: return
        passwordDialogTarget = null
        val groupIndex = target.first
        if (password == liveChannelGroupList.getOrNull(groupIndex)?.groupPassword) {
            vm.updateChannelList { it.copy(confirmedPasswordGroups = it.confirmedPasswordGroups + groupIndex) }
            loadChannelGroupDataAndPlay(groupIndex, target.second)
        } else {
            Toast.makeText(App.getInstance()!!, getString(R.string.live_wrong_password), Toast.LENGTH_SHORT).show()
        }
    }

    internal fun isNeedInputPassword(groupIndex: Int): Boolean {
        val group = liveChannelGroupList.getOrNull(groupIndex) ?: return false
        return group.groupPassword.orEmpty().isNotEmpty() && !isPasswordConfirmed(groupIndex)
    }

    private fun isPasswordConfirmed(groupIndex: Int): Boolean =
        vm.state.value.channelList.confirmedPasswordGroups.contains(groupIndex)

    fun getLiveChannels(groupIndex: Int): ArrayList<LiveChannelItem>? =
        channelSourceLoader.getLiveChannels(groupIndex)

    internal fun loadLiveConfigOnEnter() = channelSourceLoader.loadLiveConfigOnEnter()

    private fun setEmptyLiveChannelList(releasePlayer: Boolean = true) =
        channelSourceLoader.setEmptyLiveChannelList(releasePlayer)

    internal fun initLiveSettingGroupList() {
        liveSettingGroupList = ApiConfig.get().liveSettingGroupList
    }

    private fun loadCurrentSourceList() {
        liveSettingGroupList.getOrNull(0)?.liveSettingItems =
            LiveSettingsRules.sourceItems(currentLiveChannelItem?.channelSourceNames)
    }

    internal fun openSettingsSheet() {
        ApiConfig.get().refreshLiveApiHistoryItems()
        loadCurrentSourceList()
        vm.onSettingsOpened()
    }

    internal fun removeLiveConfigHistory(itemIndex: Int) {
        vm.onSettingRemoved(itemIndex, settingHost)
    }

    internal fun onSettingsInputsChanged() {
        vm.onSettingsInputsChanged()
    }

    private val settingSource = object : LiveSettingsSource {
        override fun settingGroups(): List<LiveSettingGroup> = liveSettingGroupList

        override fun playerScale(): Int = livePlayerManager.livePlayerScale

        override fun playerType(): Int = livePlayerManager.livePlayerType

        override fun connectTimeoutIndex(): Int = KV.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 1)

        override fun switchChecked(position: Int): Boolean = when (position) {
            0 -> KV.get(HawkConfig.LIVE_SHOW_TIME, false)
            1 -> KV.get(HawkConfig.LIVE_SHOW_NET_SPEED, false)
            2 -> KV.get(HawkConfig.LIVE_CHANNEL_REVERSE, false)
            3 -> KV.get(HawkConfig.LIVE_CROSS_GROUP, false)
            else -> false
        }

        override fun liveGroupIndex(): Int = ApiConfig.getLiveGroupIndex()

        override fun configIndex(): Int = LiveSettingsRules.currentConfigIndex(
            ApiConfig.isLiveFollowVod(),
            ApiConfig.get().getLiveConfigUrls(),
            KV.get(HawkConfig.LIVE_API_URL, ""),
        )

        override fun isLiveApiLineMode(): Boolean = ApiConfig.get().isLiveApiLineMode()
    }

    internal fun clickSettingItem(groupIndex: Int, position: Int) {
        vm.onSettingClicked(groupIndex, position, settingHost)
    }

    private val settingHost = object : LivePlayViewModel.Host {
        override fun currentChannelItem(): LiveChannelItem? = currentLiveChannelItem

        override fun currentPlayerScale(): Int = livePlayerManager.livePlayerScale

        override fun currentPlayerType(): Int = livePlayerManager.livePlayerType

        override fun replayCurrentChannel() {
            playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true)
        }

        override fun applyPlayerScale(position: Int) {
            mVideoView?.let { livePlayerManager.changeLivePlayerScale(it, position) }
        }

        override fun applyPlayerType(position: Int) {
            val videoView = mVideoView ?: return
            releasePlayerKernel()
            livePlayerManager.changeLivePlayerType(videoView, position)
            currentLiveChannelItem?.let { videoView.setUrl(it.url, liveChannelHeader()) }
            videoView.start()
        }

        override fun releasePlayerKernel() {
            this@LivePlayActivity.releasePlayerKernel()
        }

        override fun refreshTimeOverlay() {
            overlay.showTime()
        }

        override fun refreshNetSpeedOverlay() {
            overlay.showNetSpeed()
        }

        override fun refreshChannelListAndPlay(channelName: String?, sourceIndex: Int) {
            channelSourceLoader.refreshLiveChannelListAndPlay(channelName, sourceIndex)
        }

        override fun setEmptyChannelList(releasePlayer: Boolean) {
            setEmptyLiveChannelList(releasePlayer)
        }

        override fun removeConfigHistory(itemIndex: Int) {
            if (ApiConfig.get().isLiveApiLineMode()) {
                Toast.makeText(
                    this@LivePlayActivity,
                    getString(R.string.live_repo_entry_not_deletable),
                    Toast.LENGTH_SHORT,
                ).show()
                return
            }
            val history = KV.get(HawkConfig.LIVE_API_HISTORY, ArrayList<String>())
            if (itemIndex < 0 || itemIndex >= history.size) return
            if (history[itemIndex] == KV.get(HawkConfig.LIVE_API_URL, "")) {
                Toast.makeText(
                    this@LivePlayActivity,
                    getString(R.string.live_active_config_not_deletable),
                    Toast.LENGTH_SHORT,
                ).show()
                return
            }
            history.removeAt(itemIndex)
            KV.put(HawkConfig.LIVE_API_HISTORY, history)
            ApiConfig.get().refreshLiveApiHistoryItems()
            Toast.makeText(
                this@LivePlayActivity,
                getString(R.string.toast_removed_from_history),
                Toast.LENGTH_SHORT,
            ).show()
        }

        override fun toast(msg: String) {
            Toast.makeText(this@LivePlayActivity, msg, Toast.LENGTH_SHORT).show()
        }

        override fun isFinishing(): Boolean = this@LivePlayActivity.isFinishing

        override fun postToMain(action: Runnable) {
            mHandler.post(action)
        }
    }

    private fun liveWebHeader(): HashMap<String, String>? {
        return KV.get(HawkConfig.LIVE_WEB_HEADER)
    }

    internal fun liveChannelHeader(): HashMap<String, String>? {
        val item = currentLiveChannelItem ?: return liveWebHeader()
        val header = HashMap<String, String>()
        liveWebHeader()?.let { header.putAll(it) }
        item.headers?.let { header.putAll(it) }
        if (item.channelFormat.orEmpty().isNotEmpty()) {
            header[MediaSources.HEADER_FORMAT] = item.channelFormat.orEmpty()
        }
        return if (header.isEmpty()) null else header
    }

    internal fun onEpgRowClicked(position: Int): Boolean = catchupController.onEpgRowClicked(position)

    fun onTimeshiftSeek(progress: Float) {
        catchupController.onTimeshiftSeek(progress)
    }

    fun onTimeshiftTogglePlay() {
        catchupController.onTimeshiftTogglePlay()
    }

    internal fun canCurrentChannelCatchup(): Boolean = catchupController.canCurrentChannelCatchup()

    internal fun onChannelGroupsChanged() {
        vm.onChannelGroupsChanged(liveChannelGroupList)
    }

    private fun liveActions(): LivePlayActions = LivePlayActions(
        page = LivePageActions(
            onRetryLoad = { loadLiveConfigOnEnter() },
            onPasswordConfirm = { onPasswordConfirmed(it) },
            onPasswordDismiss = { passwordDialogTarget = null },
        ),
        player = LivePlayerActions(
            videoView = { mVideoView },
            onExitFullscreen = { applyFullscreen(false) },
            onEpgSheetOpen = { epgSheetVisible = true },
            onSettingsSheetOpen = { openSettingsSheet() },
            onTimeshiftSeek = { onTimeshiftSeek(it) },
            onTimeshiftTogglePlay = { onTimeshiftTogglePlay() },
        ),
        epg = LiveEpgActions(
            onDismiss = { epgSheetVisible = false },
            onRowClicked = { onEpgRowClicked(it) },
        ),
        list = LiveChannelListActions(
            onToggleGroup = { toggleChannelGroup(it) },
            onSelectChannel = { groupIndex, position -> selectChannel(groupIndex, position) },
        ),
        settings = LiveSettingsActions(
            onDismiss = { settingsSheetVisible = false },
            onItemClick = { groupIndex, itemIndex -> clickSettingItem(groupIndex, itemIndex) },
            onItemLongClick = { removeLiveConfigHistory(it) },
        ),
    )


}
