package com.example.digitaldelaydd3

import androidx.compose.ui.graphics.Color

data class AppTheme(
    val id: Int,
    val name: String,
    val category: String,
    val bg: Color,
    val pedalBody: Color,
    val knobColor: Color,
    val accent: Color,
    val textPrimary: Color,
    val isGumbad: Boolean = false
)

data class DelayPreset(
    val name: String,
    val eLevel: Float,
    val fBack: Float,
    val dTimeMs: Int,
    val modeIdx: Int
)

object AppRepository {
    val languages = listOf("Roman Urdu", "Hindi", "English", "Urdu", "Bangla")

    val presets = listOf(
        DelayPreset("Naat Mehfil (480ms)", 0.60f, 0.35f, 480, 3),
        DelayPreset("Gumbad Hall (550ms)", 0.68f, 0.42f, 550, 3),
        DelayPreset("Hamad Soft (420ms)", 0.50f, 0.28f, 420, 3),
        DelayPreset("Slapback (140ms)", 0.55f, 0.20f, 140, 2),
        DelayPreset("Deep Space (750ms)", 0.72f, 0.52f, 750, 3)
    )

    val themes: List<AppTheme> = buildList {
        add(AppTheme(0, "DD-8 Classic White", "Classic", Color(0xFF121417), Color(0xFFEAECEF), Color(0xFF212529), Color(0xFF1565C0), Color(0xFF111111), false))
        add(AppTheme(1, "Gumbad-e-Khizra Green", "Gumbad", Color(0xFF071A12), Color(0xFF0D3B26), Color(0xFFD4AF37), Color(0xFF2E7D32), Color(0xFFF1F8E9), true))
        add(AppTheme(2, "Masjid Nabawi Gold", "Gumbad", Color(0xFF1B1508), Color(0xFF2C220E), Color(0xFFFFD54F), Color(0xFFFFB300), Color(0xFFFFF8E1), true))
        add(AppTheme(3, "Taj Mahal Marble Dome", "Dome", Color(0xFF1A1D24), Color(0xFFD7CCC8), Color(0xFF4E342E), Color(0xFF8D6E63), Color(0xFF212121), true))
        add(AppTheme(4, "Blue Mosque Istanbul", "Gumbad", Color(0xFF081426), Color(0xFF102A4C), Color(0xFF64B5F6), Color(0xFF1E88E5), Color(0xFFE3F2FD), true))
        add(AppTheme(5, "Al-Aqsa Golden Dome", "Dome", Color(0xFF1A160B), Color(0xFF332912), Color(0xFFFFCA28), Color(0xFFF57F17), Color(0xFFFFFDE7), true))
        add(AppTheme(6, "BOSS Midnight Black", "Classic", Color(0xFF0A0A0C), Color(0xFF1C1E22), Color(0xFF90CAF9), Color(0xFFE53935), Color(0xFFECEFF1), false))
        add(AppTheme(7, "Studio Silver Pro", "Classic", Color(0xFF181A1D), Color(0xFFB0BEC5), Color(0xFF263238), Color(0xFF00897B), Color(0xFF102027), false))

        val baseColors = listOf(
            "Emerald" to Color(0xFF1B5E20), "Royal Blue" to Color(0xFF0D47A1), "Crimson" to Color(0xFFB71C1C),
            "Purple" to Color(0xFF4A148C), "Teal" to Color(0xFF004D40), "Amber" to Color(0xFFE65100),
            "Indigo" to Color(0xFF1A237E), "Burgundy" to Color(0xFF880E4F), "Olive" to Color(0xFF33691E),
            "Cyan" to Color(0xFF006064)
        )
        val styles = listOf("Gumbad Edition", "Dome Special", "Pedal Pro", "Studio Matte", "Vintage")
        var idx = 8
        for (st in styles) {
            for ((cName, col) in baseColors) {
                val isDome = st.contains("Gumbad") || st.contains("Dome")
                add(
                    AppTheme(
                        id = idx++,
                        name = "$cName $st",
                        category = if (isDome) "Gumbad / Dome" else "Studio Skins",
                        bg = Color(0xFF0F1115),
                        pedalBody = col,
                        knobColor = Color(0xFF181818),
                        accent = Color(0xFFFFD54F),
                        textPrimary = Color.White,
                        isGumbad = isDome
                    )
                )
            }
        }
    }

    fun tr(lang: String): Map<String, String> = when (lang) {
        "Hindi" -> mapOf(
            "subtitle" to "नात पढ़ने वालों के लिए बेस्ट ऐप",
            "live_mic" to "लाइव माइक",
            "file_echo" to "फ़ाइल इको",
            "check" to "चेक (CHECK)",
            "feedback_warn" to "⚠️ चेतावनी: बिना इयरफ़ोन के सीटी (Feedback) बजेगी! Wired Earphone लगाएं।",
            "start_live" to "लाइव इको शुरू करें",
            "stop_live" to "इको बंद करें",
            "rec_start" to "WAV रिकॉर्ड करें",
            "rec_stop" to "रिकॉर्डिंग सेव करें",
            "select_audio" to "ऑडियो फ़ाइल चुनें (List / Picker)",
            "preview_echo" to "इको सुनें (Preview)",
            "save_echo" to "इको फ़ाइल सेव करें",
            "share_wa" to "WhatsApp पर भेजें",
            "before_lbl" to "BEFORE (सूखी / Flat आवाज़)",
            "after_lbl" to "AFTER (Digital Delay के साथ)",
            "themes" to "थीम्स (58+)",
            "guide" to "गाइड",
            "settings" to "भाषा (Language)"
        )
        "Urdu" -> mapOf(
            "subtitle" to "نعت پڑھنے والوں کے لیے بہترین ایپ",
            "live_mic" to "لائیو مائیک",
            "file_echo" to "فائل ایکو",
            "check" to "چیک (CHECK)",
            "feedback_warn" to "⚠️ انتباہ: بغیر ایئرفون کے سیٹی بجے گی! وائرڈ ایئرفون لگائیں۔",
            "start_live" to "لائیو ایکو شروع کریں",
            "stop_live" to "ایکو بند کریں",
            "rec_start" to "WAV ریکارڈ کریں",
            "rec_stop" to "ریکارڈنگ محفوظ کریں",
            "select_audio" to "آڈیو فائل منتخب کریں",
            "preview_echo" to "ایکو سنیں (Preview)",
            "save_echo" to "ایکو فائل محفوظ کریں",
            "share_wa" to "واٹس ایپ پر شیئر کریں",
            "before_lbl" to "BEFORE (بغیر ایکو)",
            "after_lbl" to "AFTER (ڈیجیٹل ڈیلے کے ساتھ)",
            "themes" to "تھیمز (58+)",
            "guide" to "گائیڈ",
            "settings" to "زبان (Language)"
        )
        "Bangla" -> mapOf(
            "subtitle" to "নাত পাঠকদের জন্য সেরা অ্যাপ",
            "live_mic" to "লাইভ মাইক",
            "file_echo" to "ফাইল ইকো",
            "check" to "চেক (CHECK)",
            "feedback_warn" to "⚠️ সতর্কতা: ইয়ারফোন ছাড়া শিস বাজবে! Wired Earphone ব্যবহার করুন।",
            "start_live" to "লাইভ ইকো চালু করুন",
            "stop_live" to "ইকো বন্ধ করুন",
            "rec_start" to "WAV রেকর্ড করুন",
            "rec_stop" to "রেকর্ডিং সেভ করুন",
            "select_audio" to "অডিও ফাইল বাছুন",
            "preview_echo" to "ইকো শুনুন (Preview)",
            "save_echo" to "ইকো ফাইল সেভ করুন",
            "share_wa" to "WhatsApp-এ শেয়ার করুন",
            "before_lbl" to "BEFORE (ইকো ছাড়া)",
            "after_lbl" to "AFTER (Digital Delay সহ)",
            "themes" to "থিম (58+)",
            "guide" to "গাইড",
            "settings" to "ভাষা (Language)"
        )
        "English" -> mapOf(
            "subtitle" to "Best App For Naat Reciters",
            "live_mic" to "LIVE MIC",
            "file_echo" to "FILE ECHO",
            "check" to "CHECK",
            "feedback_warn" to "⚠️ Warning: Without earphones, feedback whistling will occur!",
            "start_live" to "START LIVE ECHO",
            "stop_live" to "STOP LIVE ECHO",
            "rec_start" to "RECORD WAV",
            "rec_stop" to "SAVE RECORDING",
            "select_audio" to "SELECT AUDIO FILE",
            "preview_echo" to "PREVIEW ECHO",
            "save_echo" to "SAVE ECHO FILE",
            "share_wa" to "SHARE ON WHATSAPP",
            "before_lbl" to "BEFORE (Dry / Flat)",
            "after_lbl" to "AFTER (With Digital Delay)",
            "themes" to "THEMES (58+)",
            "guide" to "GUIDE",
            "settings" to "LANGUAGE"
        )
        else -> mapOf(
            "subtitle" to "Naat Padhne Walo Ke Liye Best App",
            "live_mic" to "LIVE MIC",
            "file_echo" to "FILE ECHO",
            "check" to "CHECK",
            "feedback_warn" to "⚠️ Warning: Bina earphone ke seeti bajegi! Wired Earphone / AUX lagayein.",
            "start_live" to "LIVE ECHO START KAREIN",
            "stop_live" to "ECHO BAND KAREIN",
            "rec_start" to "WAV RECORD KAREIN",
            "rec_stop" to "RECORDING SAVE KAREIN",
            "select_audio" to "AUDIO FILE CHUNEIN",
            "preview_echo" to "ECHO SUNAYEIN (PREVIEW)",
            "save_echo" to "ECHO FILE SAVE KAREIN",
            "share_wa" to "WHATSAPP PAR SHARE KAREIN",
            "before_lbl" to "BEFORE (Sookhi / Flat Awaaz)",
            "after_lbl" to "AFTER (Digital Delay Ke Saath)",
            "themes" to "THEMES (58+)",
            "guide" to "GUIDE",
            "settings" to "SETTINGS"
        )
    }
}
