package com.example.nameplateexcel

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RagWorkspaceActivity : Activity() {
    private lateinit var store: RagStore
    private lateinit var chatList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = RagStore(this)
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        refreshChats()
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(28))
        }
        scroll.addView(root)
        root.addView(TextView(this).apply {
            text = "Диалоги по документам"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF0D47A1.toInt())
        })
        root.addView(TextView(this).apply {
            text = "У каждого диалога свой набор PDF/DOCX и своя история сообщений."
            textSize = 15f
            setPadding(0, dp(8), 0, dp(16))
        })
        root.addView(Button(this).apply {
            text = "+ Новый диалог"
            textSize = 16f
            isAllCaps = false
            setOnClickListener {
                val chat = store.createChat()
                startActivity(Intent(this@RagWorkspaceActivity, RagDocumentsActivity::class.java)
                    .putExtra(EXTRA_CHAT_ID, chat.id))
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(TextView(this).apply {
            text = "Ваши диалоги"
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(22), 0, dp(8))
        })
        chatList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(chatList)
        return scroll
    }

    private fun refreshChats() {
        chatList.removeAllViews()
        val chats = store.listChats()
        if (chats.isEmpty()) {
            chatList.addView(TextView(this).apply {
                text = "Пока нет диалогов. Создайте первый и подключите документы."
                textSize = 15f
                setTextColor(0xFF607D8B.toInt())
                setPadding(dp(8), dp(14), dp(8), dp(14))
            })
            return
        }
        chats.forEach { chat ->
            val documents = store.documentsForChat(chat.id)
            val last = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(chat.updatedAt))
            chatList.addView(Button(this).apply {
                text = "${chat.title}\n${documents.size} файл(ов) · ${chat.messages.size} сообщ. · $last"
                textSize = 15f
                isAllCaps = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                minHeight = dp(78)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                setOnClickListener {
                    val target = if (documents.isEmpty()) RagDocumentsActivity::class.java else RagChatActivity::class.java
                    startActivity(Intent(this@RagWorkspaceActivity, target).putExtra(EXTRA_CHAT_ID, chat.id))
                }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(9) })
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_CHAT_ID = "rag_chat_id"
    }
}
