package com.reeman.agv.activities

import android.content.Intent
import android.text.InputFilter
import android.text.Spanned
import android.text.TextUtils
import android.view.View
import android.widget.Button
import com.google.android.material.textfield.TextInputEditText
import com.reeman.agv.R
import com.reeman.agv.base.BaseActivity
import com.reeman.agv.base.BaseApplication
import com.reeman.agv.utils.DebounceClickListener
import com.reeman.agv.utils.ToastUtils
import com.reeman.agv.widgets.EasyDialog
import com.reeman.commons.constants.Constants
import com.reeman.commons.utils.SpManager
import com.reeman.commons.utils.StringUtils
import timber.log.Timber

class AliasSettingActivity : BaseActivity(),DebounceClickListener {
    private lateinit var etAlias: TextInputEditText

    private lateinit var btnSkip: Button

    private var isGuide = false

    override fun getLayoutRes(): Int {
        return R.layout.activity_alias_setting
    }

    override fun initCustomView() {
        isGuide = SpManager.getInstance().getBoolean(Constants.KEY_IS_ALIAS_GUIDE, false)
        etAlias = `$`(R.id.et_alias)
        etAlias.setOnFocusChangeListener(::hideKeyBoard)
        etAlias.setFilters(arrayOf(InputFilter { source: CharSequence, start: Int, end: Int, dest: Spanned?, dstart: Int, dend: Int ->
            for (i in start until end) {
                val c = source[i]
                if (!Character.isLetterOrDigit(c) && c != '_' && c != '-' && !StringUtils.isChinese(c)) {
                    etAlias.error = getString(R.string.text_alias_input_type)
                    return@InputFilter ""
                }
            }
            null
        }))
        if (isGuide) {
            etAlias.setText(robotInfo.robotAlias)
        }
        btnSkip = `$`(R.id.btn_skip)
        btnSkip.visibility = if (isGuide) View.GONE else View.VISIBLE
        btnSkip.setDebounceClickListener (::onDebounceClick)
        `$`<View>(R.id.btn_save).setDebounceClickListener (::onDebounceClick)
        `$`<View>(R.id.btn_exit).setDebounceClickListener (::onDebounceClick)
    }


    private fun onDebounceClick(view: View) {
        when (view.id) {
            R.id.btn_save -> {
                val s = etAlias.text.toString()
                if (TextUtils.isEmpty(s)) {
                    EasyDialog.getInstance(this).confirm(getString(R.string.text_check_not_input_alias_will_use_hostname)) { dialog, id ->
                        dialog.dismiss()
                        if (id == R.id.btn_confirm) {
                            saveAlias(robotInfo.ROSHostname)
                        }
                    }
                    return
                }
                if (s.length < 5) {
                    etAlias.error = getString(R.string.text_alias_input_type)
                    return
                }
                saveAlias(s)
            }

            R.id.btn_skip -> EasyDialog.getInstance(this).confirm(getString(R.string.text_skip_will_use_hostname)) { dialog, id ->
                dialog.dismiss()
                if (id == R.id.btn_confirm) {
                    SpManager.getInstance().edit().putBoolean(Constants.KEY_IS_ALIAS_GUIDE, true).apply()
                    startActivity(Intent(this,MainActivity::class.java))
                    finish()
                    BaseApplication.mApp.startCallingService()
                }
            }

            R.id.btn_exit -> {
                if (!isGuide) {
                    BaseApplication.mApp.exit()
                    return
                }
                finish()
            }
        }
    }

    private fun saveAlias(alias: String) {
        Timber.w("设置别名为 : %s", alias)
        SpManager.getInstance().edit().putString(Constants.KEY_ROBOT_ALIAS, alias).apply()
        robotInfo.robotAlias = alias
        ToastUtils.showShortToast(getString(R.string.text_set_alias, alias))
        if (!isGuide) {
            SpManager.getInstance().edit().putBoolean(Constants.KEY_IS_ALIAS_GUIDE, true).apply()
            startActivity(Intent(this,MainActivity::class.java))
            finish()
            BaseApplication.mApp.startCallingService()
        }
    }
}
