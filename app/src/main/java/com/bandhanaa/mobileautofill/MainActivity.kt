package com.bandhanaa.mobileautofill

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var prefs: android.content.SharedPreferences
    private var webView: WebView? = null
    private val generalFields = linkedMapOf<String, EditText>()
    private data class PilgrimInputs(val name: EditText, val age: EditText, val gender: EditText, val idType: EditText, val idNumber: EditText)
    private val pilgrimInputs = mutableListOf<PilgrimInputs>()
    private val navy = 0xFF102A40.toInt()
    private val blue = 0xFF216A91.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("autofill_data", Context.MODE_PRIVATE)
        showHome()
    }

    private fun base(title: String): LinearLayout {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFFF5F7FA.toInt()) }
        val header = TextView(this).apply {
            text = title; textSize = 21f; setTextColor(-1); setBackgroundColor(navy)
            setPadding(20, 18, 16, 18); gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(header, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        return root
    }

    private fun button(label: String, action: () -> Unit): Button =
        Button(this).apply { text = label; isAllCaps = false; setTextColor(-1); setBackgroundColor(blue); setOnClickListener { action() } }

    private fun showHome() {
        webView = null
        base("Mobile Autofill")
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 24, 20, 20) }
        body.addView(TextView(this).apply {
            text = "Save your details once, then use them on the official TTD website. Review all details before submitting any booking."
            textSize = 16f; setTextColor(0xFF263746.toInt()); setPadding(0, 8, 0, 22)
        })
        body.addView(button("Save / Edit Details") { showEditor() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        body.addView(button("Enable Android Autofill Service") {
            try {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE,
                    android.net.Uri.parse("package:" + packageName)))
            } catch (_: Exception) {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS))
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        body.addView(button("Open TTD Dashboard") { showTtd() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        body.addView(TextView(this).apply {
            text = "Privacy: details are stored locally on this device. This app does not submit bookings automatically."
            textSize = 13f; setTextColor(0xFF5E6B75.toInt()); setPadding(0, 16, 0, 0)
        })
        root.addView(body)
    }

    private fun field(label: String, key: String, value: String = "", numeric: Boolean = false): EditText {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 5, 0, 5) }
        wrap.addView(TextView(this).apply { text = label; textSize = 13f; setTextColor(0xFF536574.toInt()) })
        val edit = EditText(this).apply {
            setSingleLine(true); textSize = 16f; setText(value)
            if (numeric) inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = label
        }
        wrap.addView(edit, LinearLayout.LayoutParams(-1, -2))
        editorContainer!!.addView(wrap)
        generalFields[key] = edit
        return edit
    }

    private var editorContainer: LinearLayout? = null

    private fun showEditor() {
        generalFields.clear(); pilgrimInputs.clear()
        base("Saved Form Details")
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 8, 18, 24) }
        editorContainer = content
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val saved = readData()
        val general = saved.optJSONObject("general") ?: JSONObject()
        content.addView(section("General Details"))
        field("Email Address", "pilgrimEmail", general.optString("pilgrimEmail"))
        field("City", "pilgrimCity", general.optString("pilgrimCity"))
        field("State", "pilgrimState", general.optString("pilgrimState"))
        field("Country", "pilgrimCountry", general.optString("pilgrimCountry"))
        field("Pincode", "pilgrimPincode", general.optString("pilgrimPincode"), true)
        content.addView(section("Pilgrim Details (up to 4)"))
        val pilgrims = saved.optJSONArray("pilgrims") ?: JSONArray()
        for (i in 0 until 4) {
            val p = pilgrims.optJSONObject(i) ?: JSONObject()
            content.addView(TextView(this).apply {
                text = "Pilgrim ${i + 1}"; textSize = 16f; setTextColor(navy); setPadding(0, 12, 0, 2)
            })
            val name = addPilgrimField("Name", p.optString("name"), false)
            val age = addPilgrimField("Age", p.optString("age"), true)
            val gender = addPilgrimField("Gender (Male / Female / Transgender)", p.optString("gender"), false)
            val idType = addPilgrimField("Photo ID Proof (as shown on TTD)", p.optString("idType"), false)
            val idNumber = addPilgrimField("Photo ID Number", p.optString("idNumber"), false)
            pilgrimInputs.add(PilgrimInputs(name, age, gender, idType, idNumber))
        }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(8, 6, 8, 8)
            addView(button("Save Details") { saveData(); Toast.makeText(this@MainActivity, "Details saved on this device", Toast.LENGTH_SHORT).show(); showHome() }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(button("Open TTD") { saveData(); showTtd() }, LinearLayout.LayoutParams(0, -2, 1f))
        })
    }

    private fun section(text: String) = TextView(this).apply {
        this.text = text; textSize = 18f; setTextColor(navy); setPadding(0, 16, 0, 4)
    }

    private fun addPilgrimField(label: String, value: String, numeric: Boolean): EditText {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 2, 0, 2) }
        wrap.addView(TextView(this).apply { text = label; textSize = 12f; setTextColor(0xFF536574.toInt()) })
        val e = EditText(this).apply { setSingleLine(true); textSize = 15f; setText(value); hint = label }
        if (numeric) e.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        wrap.addView(e)
        editorContainer!!.addView(wrap)
        return e
    }

    private fun readData(): JSONObject = try { JSONObject(prefs.getString("data", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }

    private fun saveData() {
        val general = JSONObject()
        generalFields.forEach { (key, edit) -> general.put(key, edit.text.toString().trim()) }
        val arr = JSONArray()
        pilgrimInputs.forEach { p ->
            arr.put(JSONObject().put("name", p.name.text.toString().trim())
                .put("age", p.age.text.toString().trim()).put("gender", p.gender.text.toString().trim())
                .put("idType", p.idType.text.toString().trim()).put("idNumber", p.idNumber.text.toString().trim()))
        }
        prefs.edit().putString("data", JSONObject().put("general", general).put("pilgrims", arr).toString()).apply()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showTtd() {
        base("TTD Booking • Autofill")
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bar.addView(button("Saved Details") { showEditor() }, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(button("Fill Form") { runAutofill() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(bar)
        val w = WebView(this)
        webView = w
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.javaScriptCanOpenWindowsAutomatically = false
        w.webChromeClient = WebChromeClient()
        w.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript("document.documentElement.style.webkitTapHighlightColor='transparent';", null)
            }
        }
        w.addJavascriptInterface(TtdBridge(), "AndroidAutofill")
        root.addView(w, LinearLayout.LayoutParams(-1, 0, 1f))
        w.loadUrl("https://ttdevasthanams.ap.gov.in/home/dashboard")
    }

    inner class TtdBridge {
        @JavascriptInterface fun getData(): String = readData().toString()
        @JavascriptInterface fun notifyResult(message: String) {
            runOnUiThread { Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show() }
        }
    }

    private fun runAutofill() {
        val w = webView ?: return
        val script = """
        (async function(){
          const data = JSON.parse(AndroidAutofill.getData() || '{}');
          const g = data.general || {};
          const sleep = ms => new Promise(r => setTimeout(r, ms));
          const setValue = (el, value) => {
            if (!el || value == null || String(value).trim() === '') return false;
            const proto = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
            const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
            if (setter) setter.call(el, String(value)); else el.value = String(value);
            el.dispatchEvent(new Event('input', {bubbles:true}));
            el.dispatchEvent(new Event('change', {bubbles:true}));
            el.dispatchEvent(new Event('blur', {bubbles:true}));
            return true;
          };
          const first = name => document.querySelector('[name="'+name+'"]');
          let filled = 0;
          for (const k of ['pilgrimEmail','pilgrimCity','pilgrimState','pilgrimCountry','pilgrimPincode']) if(setValue(first(k),g[k])) filled++;
          const pilgrims = data.pilgrims || [];
          const sections = Array.from(document.querySelectorAll('.pilDetails_mainContainer__HPFSL'));
          async function chooseCustom(input, wanted) {
            if (!input || !wanted) return false;
            for(let tries=0; tries<20 && (input.disabled || !input.isConnected); tries++) await sleep(250);
            if(input.disabled) return false;
            input.click();
            input.dispatchEvent(new MouseEvent('mousedown',{bubbles:true}));
            await sleep(350);
            const candidates = Array.from(document.querySelectorAll('li'));
            const option = candidates.find(li => li.textContent.trim().toLowerCase() === String(wanted).trim().toLowerCase());
            if(!option) return false;
            option.click();
            option.dispatchEvent(new MouseEvent('mouseup',{bubbles:true}));
            await sleep(350);
            return input.value.trim().toLowerCase() === String(wanted).trim().toLowerCase();
          }
          for(let i=0; i<Math.min(sections.length,pilgrims.length); i++){
            const sec=sections[i], p=pilgrims[i]||{};
            if(setValue(sec.querySelector('[name="name"]'),p.name)) filled++;
            if(setValue(sec.querySelector('[name="age"]'),p.age)) filled++;
            const gender=sec.querySelector('[name="gender"]');
            if(await chooseCustom(gender,p.gender)) filled++;
            const idType=sec.querySelector('[name="idType"]');
            if(idType && idType.disabled){
              for(let n=0;n<20 && idType.disabled;n++) await sleep(250);
            }
            if(await chooseCustom(idType,p.idType)) filled++;
            const idNumber=sec.querySelector('[name="idNumber"]');
            for(let n=0;n<20 && idNumber && idNumber.disabled;n++) await sleep(250);
            if(setValue(idNumber,p.idNumber)) filled++;
          }
          AndroidAutofill.notifyResult(filled ? ('Autofill attempted: '+filled+' field(s). Please review every field and dropdown.') : 'No fields were filled. Open the actual TTD pilgrim details form first.');
        })();
        """.trimIndent()
        w.evaluateJavascript(script, null)
    }

    override fun onBackPressed() {
        val w = webView
        if (w != null && w.canGoBack()) w.goBack() else { webView = null; showHome() }
    }
}
