package com.reeman.agv.fragments.setting

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.reeman.agv.BuildConfig
import com.reeman.agv.R
import com.reeman.agv.base.BaseApplication.mApp
import com.reeman.agv.base.BaseFragment
import com.reeman.agv.utils.DebounceClickListener
import com.reeman.agv.utils.PackageUtils
import com.reeman.agv.utils.ToastUtils
import com.reeman.agv.utils.UpgradeUtil
import com.reeman.agv.widgets.EasyDialog
import com.reeman.agv.widgets.ProcessDialog
import com.reeman.commons.constants.Constants
import com.reeman.commons.event.VersionInfoEvent
import com.reeman.commons.exceptions.CustomHttpException
import com.reeman.commons.model.request.ApkInfo
import com.reeman.commons.utils.AESUtil
import com.reeman.commons.utils.MMKVManager
import com.reeman.commons.utils.PrecisionUtils
import com.reeman.commons.utils.TimeUtil
import com.reeman.ros.ROSController
import kotlinx.coroutines.Job
import timber.log.Timber
import java.io.File
import java.util.Locale

class VersionSettingFragment : BaseFragment(), DebounceClickListener, (View) -> Unit {
    private lateinit var tvNavigationVersion: TextView
    private lateinit var tvPowerBoardVersion: TextView
    private lateinit var btnDownloadProcess: Button
    private lateinit var btnUpgrade: Button

    
    

    private var processDialog: ProcessDialog? = null
    private lateinit var dir: File
    override fun getLayoutRes() = R.layout.fragment_version_setting

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val tvAppVersion = findView<TextView>(R.id.tv_app_version)
        tvNavigationVersion = findView(R.id.tv_navigation_version)
        tvPowerBoardVersion = findView(R.id.tv_power_board_version)
        btnDownloadProcess = findView(R.id.btn_download_process)
        btnUpgrade = findView(R.id.btn_upgrade)

        tvAppVersion.text = PackageUtils.getVersion(requireContext())

        tvNavigationVersion.setDebounceClickListener(this)
        tvPowerBoardVersion.setDebounceClickListener(this)
        btnUpgrade.setDebounceClickListener(this)
        btnDownloadProcess.setDebounceClickListener(this)
        findView<Button>(R.id.btn_check_update).setDebounceClickListener(this)
    }

    override fun onResume() {
        super.onResume()
        ROSController.heartBeat()
        dir = File(
            if (BuildConfig.IS3128) {
                Environment.getExternalStorageDirectory()
            } else {
                requireContext().filesDir
            }, "down"
        )
        val upgradeInfo = UpgradeUtil.getUpgradeInfo(requireContext(),dir)
        if (upgradeInfo != null) {
            btnUpgrade.visibility = View.VISIBLE
            btnUpgrade.tag = upgradeInfo
        }
    }

    override fun onPause() {
        super.onPause()
        if (UpgradeUtil.isDownloading()) {
            UpgradeUtil.releaseDownloadCallback()
        }
    }

    override fun onVersionEvent(event: VersionInfoEvent) {
        tvNavigationVersion.text = event.softVer
        tvPowerBoardVersion.text = event.hardwareVer
    }

    override fun invoke(v: View) {
        when (v.id) {
            R.id.tv_navigation_version -> {
                tvNavigationVersion.text = ""
                ROSController.heartBeat()
            }

            R.id.tv_power_board_version -> {
                tvPowerBoardVersion.text = ""
                ROSController.heartBeat()
            }

            R.id.btn_check_update -> {
            }

            R.id.btn_upgrade -> {

            }

            R.id.btn_download_process -> showProcessDialog(UpgradeUtil.lastProcess)
        }
    }

    private fun onGetApkInfoFailure(throwable: Throwable) {
        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
        val content = if (throwable is CustomHttpException) {
            when (throwable.code) {
                -1, -2 -> getString(
                    R.string.text_get_apk_info_failed_with_message_,
                    throwable.message
                )

                else -> getString(
                    R.string.text_get_apk_info_failed_unknown_exception,
                    throwable.message
                )
            }
        } else {
            getString(R.string.text_get_apk_info_failed_unknown_exception, throwable.message)
        }
        EasyDialog.getInstance(requireContext()).warnError(content)
    }

    private fun onGetApkInfoSuccess(apkInfo: ApkInfo) {
        if (EasyDialog.isShow()) EasyDialog.getInstance().dismiss()
        val currentPackageName = requireContext().packageName
        val packageInfo = requireContext().packageManager.getPackageInfo(
            currentPackageName,
            0
        )
        val versionCode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            }
        val versionName = packageInfo.versionName
        Timber.w(" versionCode: $versionCode, versionName: $versionName, apkInfo: $apkInfo")
        if (versionCode > apkInfo.version.toInt() || versionName == apkInfo.versionShort) {
            ToastUtils.showShortToast(getString(R.string.text_check_version_already_last))
            return
        }
        showUpgradeInfoDialog(apkInfo, getString(R.string.text_upgrade), confirmListener = {
            UpgradeUtil.startFileDownload(
                url = apkInfo.installUrl,
                outputFile = dir,
                onProgressUpdate = this::onProgressUpdate,
                onSuccess = { filePath ->
                    onSuccess(apkInfo, filePath)
                },
                onFailure = this::onFailure
            )
            showProcessDialog(0)
        })
    }

    private fun showProcessDialog(process: Int) {
        UpgradeUtil.isBackgroundDownload = false
        btnDownloadProcess.visibility = View.GONE
        processDialog = ProcessDialog(requireContext(),
            process,
            cancelListener = {
                EasyDialog.getInstance(requireContext())
                    .confirm(getString(R.string.text_confirm_to_cancel_download)) { dialog, mId ->
                        dialog.dismiss()
                        if (mId == R.id.btn_confirm) {
                            UpgradeUtil.cancelDownload()
                        } else {
                            UpgradeUtil.isBackgroundDownload = true
                            btnDownloadProcess.visibility = View.VISIBLE
                        }
                    }
            }, backgroundDownloadListener = {
                UpgradeUtil.isBackgroundDownload = true
                btnDownloadProcess.visibility = View.VISIBLE
                btnDownloadProcess.text = getString(
                    R.string.text_download_process,
                    String.format(Locale.CHINA,"%s%%", UpgradeUtil.lastProcess.toString())
                )
            })
        processDialog?.show()
    }

    private fun showUpgradeInfoDialog(
        apkInfo: ApkInfo,
        positiveText: String,
        confirmListener: () -> Unit
    ) {
        EasyDialog.getInstance(requireContext()).confirm(
            getString(R.string.text_apk_info),
            positiveText,
            getString(R.string.text_cancel),
            "${
                getString(R.string.text_name_, apkInfo.name)
            }\n${
                getString(R.string.text_version_code_, apkInfo.version)
            }\n${
                getString(R.string.text_version_name_, apkInfo.versionShort)
            }\n${
                getString(
                    R.string.text_file_size_,
                    "${PrecisionUtils.setScale(apkInfo.binary.fsize.toFloat() / 1024 / 1024)}MB"
                )
            }\n${
                getString(R.string.text_update_at_, TimeUtil.formatTime(apkInfo.updatedAt * 1000))
            }\n${
                getString(R.string.text_update_log_, apkInfo.changelog)
            }"
        ) { dialog, mId ->
            dialog.dismiss()
            if (mId == R.id.btn_confirm) {
                confirmListener.invoke()
            }
        }
    }

    private fun onProgressUpdate(process: Int) {
        if (UpgradeUtil.isBackgroundDownload) {
            btnDownloadProcess.text =
                getString(R.string.text_download_process, String.format(Locale.CHINA,"%s%%", process.toString()))
        } else {
            processDialog?.updateProgress(process)
        }
    }

    private fun onSuccess(apkInfo: ApkInfo, path: String) {
        processDialog?.dismiss()
        if (UpgradeUtil.isBackgroundDownload) {
            btnDownloadProcess.visibility = View.GONE
            apkInfo.localPath = path
            btnUpgrade.visibility = View.VISIBLE
            btnUpgrade.tag = apkInfo
            return
        }
        upgrade(path)
    }

    private fun onFailure(throwable: Throwable) {
        processDialog?.dismiss()
        val content = if (throwable is CustomHttpException) {
            when (throwable.code) {
                -1 -> getString(R.string.text_download_failed_file_not_exist)
                -2 -> getString(R.string.text_download_failed_with_message_, throwable.message)
                -3 -> getString(R.string.text_read_file_name_failed)
                else -> getString(R.string.text_download_failed)
            }
        } else {
            getString(R.string.text_download_failed)
        }
        if (UpgradeUtil.isBackgroundDownload) {
            btnDownloadProcess.visibility = View.GONE
            ToastUtils.showShortToast(content)
            return
        } else {
            EasyDialog.getInstance(requireContext())
                .warnError(content)
        }
    }

    private fun upgrade(path: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canInstallPackages = requireContext().packageManager.canRequestPackageInstalls()
            if (!canInstallPackages) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${requireContext().packageName}"))
                requireContext().startActivity(intent)
                return
            }
        }
        try {
            UpgradeUtil.installApk(requireContext(), File(path))
            if (BuildConfig.IS3128) {
                mApp.exit()
            }
        } catch (e: Exception) {
            Timber.w(e, "升级失败")
            ToastUtils.showShortToast(getString(R.string.text_upgrade_failed, e.message))
        }
    }
}