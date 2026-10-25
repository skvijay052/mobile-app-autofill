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
        val w = webView
        if (w == null) {
            Toast.makeText(this, "TTD page is not open. Tap Open TTD Dashboard first.", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "Inspecting TTD form…", Toast.LENGTH_SHORT).show()
        val script = """
        (async function() {
          const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
          const norm = s => String(s || "").toLowerCase().replace(/[^a-z0-9]/g, "");
          const roots = () => {
            const result = [document];
            document.querySelectorAll("iframe").forEach(frame => {
              try { if (frame.contentDocument) result.push(frame.contentDocument); } catch (_) {}
            });
            return result;
          };
          const visible = el => {
            try { return !!(el.getClientRects().length) && getComputedStyle(el).visibility !== "hidden"; }
            catch (_) { return true; }
          };
          const elements = () => roots().flatMap(root =>
            Array.from(root.querySelectorAll("input,textarea,select,[role=combobox],[aria-haspopup=listbox],[contenteditable=true]"))
          );
          const attrs = el => [
            el.name, el.id, el.getAttribute("autocomplete"), el.getAttribute("aria-label"),
            el.getAttribute("placeholder"), el.getAttribute("data-testid"),
            el.getAttribute("formcontrolname"), el.getAttribute("ng-reflect-name"),
            el.getAttribute("title"), el.getAttribute("name")
          ].map(norm);
          const aliases = {
            pilgrimEmail:["pilgrimemail","email","emailaddress"],
            pilgrimCity:["pilgrimcity","city"],
            pilgrimState:["pilgrimstate","state","statename"],
            pilgrimCountry:["pilgrimcountry","country","countryname"],
            pilgrimPincode:["pilgrimpincode","pincode","postalcode","zipcode"],
            name:["pilgrimname","fullname","name"],
            age:["age","pilgrimage"],
            gender:["gender","sex"],
            idType:["idtype","photoidproof","idproof","photoidtype","identityproof"],
            idNumber:["idnumber","photoidnumber","idproofnumber","identitynumber"]
          };
          const find = key => {
            const els = elements();
            const wanted = aliases[key] || [norm(key)];
            const exact = els.filter(el => [el.name,el.id,el.getAttribute("formcontrolname"),el.getAttribute("ng-reflect-name")]
              .some(v => wanted.includes(norm(v))));
            if (exact.length) return exact;
            return els.filter(el => attrs(el).some(a => wanted.some(w => a === w || (w.length > 3 && a.includes(w)))));
          };
          const setValue = (el, raw) => {
            if (!el || raw == null || String(raw).trim() === "" || el.disabled || el.readOnly) return false;
            const value = String(raw).trim();
            if (el.tagName === "SELECT") {
              const options = Array.from(el.options);
              const option = options.find(o => norm(o.textContent) === norm(value) || norm(o.value) === norm(value)) ||
                options.find(o => norm(o.textContent).includes(norm(value)) || norm(value).includes(norm(o.textContent)));
              if (!option) return false;
              el.value = option.value;
            } else if (el.isContentEditable) {
              el.textContent = value;
            } else {
              const proto = el.tagName === "TEXTAREA" ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
              const setter = Object.getOwnPropertyDescriptor(proto, "value")?.set;
              if (setter) setter.call(el, value); else el.value = value;
            }
            el.dispatchEvent(new Event("input", {bubbles:true}));
            el.dispatchEvent(new Event("change", {bubbles:true}));
            el.dispatchEvent(new Event("blur", {bubbles:true}));
            return String(el.value ?? el.textContent ?? "").trim() === value;
          };
          const getOptions = () => roots().flatMap(root => Array.from(root.querySelectorAll(
            "li,[role=option],option,[role=menuitem],.dropdown-item,.mat-option,.ant-select-item-option,[data-value]"
          )).filter(visible));
          const choose = async (el, raw) => {
            if (!el || raw == null || String(raw).trim() === "" || el.disabled) return false;
            const value = String(raw).trim();
            if (el.tagName === "SELECT") return setValue(el, value);
            el.scrollIntoView({block:"center", inline:"nearest"});
            el.focus?.();
            el.click();
            el.dispatchEvent(new MouseEvent("mousedown", {bubbles:true}));
            for (let attempt = 0; attempt < 8; attempt++) {
              await sleep(120);
              const opts = getOptions();
              const wanted = norm(value);
              const option = opts.find(x => norm(x.innerText || x.textContent) === wanted) ||
                opts.find(x => norm(x.innerText || x.textContent).includes(wanted) || wanted.includes(norm(x.innerText || x.textContent)));
              if (option) {
                option.click();
                option.dispatchEvent(new MouseEvent("mouseup", {bubbles:true}));
                option.dispatchEvent(new Event("input", {bubbles:true}));
                option.dispatchEvent(new Event("change", {bubbles:true}));
                await sleep(250);
                const shown = norm(el.value || el.getAttribute("value") || el.textContent);
                return shown === wanted || shown.includes(wanted) || wanted.includes(shown);
              }
            }
            return false;
          };
          const waitEnabled = async key => {
            for (let i=0; i<20; i++) {
              const target = find(key)[0];
              if (target && !target.disabled && !target.readOnly) return target;
              await sleep(150);
            }
            return find(key)[0] || null;
          };
          try {
            if (!window.AndroidAutofill) throw new Error("Android bridge unavailable");
            const data = JSON.parse(AndroidAutofill.getData() || "{}");
            const general = data.general || {};
            const pilgrims = Array.isArray(data.pilgrims) ? data.pilgrims : [];
            const report = [];
            let filled = 0;
            for (const key of ["pilgrimEmail","pilgrimCity","pilgrimState","pilgrimCountry","pilgrimPincode"]) {
              const el = find(key)[0];
              const ok = setValue(el, general[key]);
              if (ok) filled++;
              if (general[key]) report.push(key + ": " + (ok ? "filled" : (el ? "not editable / value mismatch" : "not found")));
            }
            const count = Math.min(4, pilgrims.length);
            for (let i=0; i<count; i++) {
              const p = pilgrims[i] || {};
              for (const key of ["name","age"]) {
                const el = find(key)[i];
                const ok = setValue(el, p[key]);
                if (ok) filled++;
                if (p[key]) report.push("Pilgrim " + (i+1) + " " + key + ": " + (ok ? "filled" : (el ? "failed" : "not found")));
              }
              const genderEl = find("gender")[i];
              if (p.gender) {
                const ok = await choose(genderEl, p.gender);
                if (ok) filled++;
                report.push("Pilgrim " + (i+1) + " gender: " + (ok ? "selected" : (genderEl ? "option not selected" : "control not found")));
              }
              const idEl = find("idType")[i];
              if (p.idType) {
                const ok = await choose(idEl, p.idType);
                if (ok) filled++;
                report.push("Pilgrim " + (i+1) + " photo ID type: " + (ok ? "selected" : (idEl ? "option not selected" : "control not found")));
                await sleep(350);
              }
              if (p.idNumber) {
                const el = await waitEnabled("idNumber");
                const targets = find("idNumber");
                const target = targets[i] || el;
                const ok = setValue(target, p.idNumber);
                if (ok) filled++;
                report.push("Pilgrim " + (i+1) + " ID number: " + (ok ? "filled" : (target ? (target.disabled ? "still disabled after selecting ID type" : "failed") : "field not found")));
              }
            }
            const detected = elements().slice(0,60).map(el => ({
              tag:el.tagName, id:el.id || "", name:el.name || "",
              placeholder:el.getAttribute("placeholder") || "",
              aria:el.getAttribute("aria-label") || "",
              type:el.type || "", disabled:!!el.disabled, readOnly:!!el.readOnly
            }));
            const result = {
              ok:filled > 0, filled, url:location.href, report,
              inputCount:elements().length, detected,
              message: filled > 0
                ? "Filled or selected " + filled + " field(s). Review details carefully. " + report.filter(x => /not found|failed|not selected|disabled|mismatch/i.test(x)).join("; ")
                : "No fields were filled. URL: " + location.href + ". Detected controls: " +
                  (detected.map(x => [x.name,x.id,x.placeholder,x.aria,x.tag].find(Boolean)).join(", ") || "none") +
                  ". Open the actual Pilgrim Details form and try again."
            };
            return JSON.stringify(result);
          } catch (e) {
            return JSON.stringify({ok:false,error:String(e && e.stack || e),message:"Fill Form error: " + String(e && e.message || e)});
          }
        })();
        """.trimIndent()
        w.evaluateJavascript(script) { raw ->
            runOnUiThread {
                val decoded = try {
                    val value = org.json.JSONTokener(raw ?: "null").nextValue()
                    if (value is String) value else value.toString()
                } catch (_: Exception) { raw ?: "" }
                val display = try {
                    val json = JSONObject(decoded)
                    val message = json.optString("message", json.optString("error", decoded))
                    val details = json.optJSONArray("report")
                    if (details != null && details.length() > 0) {
                        message + "\n\n" + (0 until details.length()).joinToString("\n") { details.optString(it) }
                    } else message
                } catch (_: Exception) { decoded }
                Toast.makeText(this, display.ifBlank { "No response from the page. Try reloading TTD." }, Toast.LENGTH_LONG).show()
            }
        }
    }
    override fun onBackPressed() {
        val w = webView
        if (w != null && w.canGoBack()) w.goBack() else { webView = null; showHome() }
    }
}
