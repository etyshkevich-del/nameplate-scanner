package com.example.nameplateexcel

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
    }

    private fun buildUi(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(24), dp(28), dp(24), dp(28))

        addView(TextView(this@LauncherActivity).apply {
            text = "Локальный AI"
            textSize = 30f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF0D47A1.toInt())
        })
        addView(TextView(this@LauncherActivity).apply {
            text = "Выберите, что хотите сделать"
            textSize = 17f
            setPadding(0, dp(8), 0, dp(24))
        })

        addView(scenarioButton(
            title = "Фото шильдика → Excel",
            subtitle = "Распознать характеристики двигателя и заполнить ПХ.xlsx",
            scenario = MainActivity.SCENARIO_NAMEPLATE,
        ))
        addView(scenarioButton(
            title = "Вопросы по PDF/DOCX",
            subtitle = "Отдельные диалоги, несколько файлов и локальные ответы",
            scenario = MainActivity.SCENARIO_RAG,
        ), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(14) })

        addView(TextView(this@LauncherActivity).apply {
            text = "Оба сценария работают на встроенной Gemma-4 E2B без отправки данных в облако."
            textSize = 13f
            setTextColor(0xFF546E7A.toInt())
            setPadding(0, dp(24), 0, 0)
        })
    }

    private fun scenarioButton(title: String, subtitle: String, scenario: String): Button =
        Button(this).apply {
            text = "$title\n$subtitle"
            textSize = 16f
            isAllCaps = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            minHeight = dp(88)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener {
                if (scenario == MainActivity.SCENARIO_RAG) {
                    startActivity(Intent(this@LauncherActivity, RagWorkspaceActivity::class.java))
                } else {
                    startActivity(Intent(this@LauncherActivity, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_SCENARIO, scenario)
                    })
                }
            }
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
