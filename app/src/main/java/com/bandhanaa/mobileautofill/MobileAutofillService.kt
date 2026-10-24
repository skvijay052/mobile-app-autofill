package com.bandhanaa.mobileautofill

import android.app.assist.AssistStructure
import android.content.Context
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import org.json.JSONObject
import java.util.Locale

class MobileAutofillService : AutofillService() {
    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        try {
            val prefs = getSharedPreferences("autofill_data", Context.MODE_PRIVATE)
            val data = JSONObject(prefs.getString("data", "{}") ?: "{}")
            val general = data.optJSONObject("general") ?: JSONObject()
            val pilgrims = data.optJSONArray("pilgrims")
            val firstPilgrim = pilgrims?.optJSONObject(0) ?: JSONObject()

            val ids = mutableListOf<AutofillId>()
            val values = mutableListOf<String>()
            val structure = request.fillContexts.lastOrNull()?.structure
            if (structure == null) {
                callback.onSuccess(null)
                return
            }

            fun walk(node: AssistStructure.ViewNode) {
                val id = node.autofillId
                if (id != null && node.isFocused.not()) {
                    val key = identifyField(node)
                    val value = key?.let {
                        when (it) {
                            "pilgrimEmail", "pilgrimCity", "pilgrimState", "pilgrimCountry", "pilgrimPincode" ->
                                general.optString(it, "")
                            "name", "age", "gender", "idType", "idNumber" ->
                                firstPilgrim.optString(it, "")
                            else -> ""
                        }
                    }.orEmpty()
                    if (value.isNotBlank()) {
                        ids.add(id)
                        values.add(value)
                    }
                }
                for (i in 0 until node.childCount) walk(node.getChildAt(i))
            }

            for (i in 0 until structure.windowNodeCount) {
                walk(structure.getWindowNodeAt(i).rootViewNode)
            }

            if (ids.isEmpty()) {
                callback.onSuccess(null)
                return
            }

            val presentation = RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
                setTextViewText(R.id.text1, "Fill saved details")
            }
            val datasetBuilder = Dataset.Builder(presentation)
            for (i in ids.indices) {
                datasetBuilder.setValue(ids[i], AutofillValue.forText(values[i]))
            }
            val response = FillResponse.Builder()
                .addDataset(datasetBuilder.build())
                .build()
            callback.onSuccess(response)
        } catch (_: Exception) {
            callback.onSuccess(null)
        }
    }

    private fun identifyField(node: AssistStructure.ViewNode): String? {
        val clues = buildList {
            node.autofillHints?.forEach { add(it) }
            node.idEntry?.let { add(it) }
            node.hint?.let { add(it) }
            node.contentDescription?.let { add(it.toString()) }
        }.joinToString(" ").lowercase(Locale.ROOT)
            .replace("_", "").replace("-", "").replace(" ", "")

        return when {
            "pilgrimemail" in clues || "email" in clues -> "pilgrimEmail"
            "pilgrimcity" in clues || clues == "city" -> "pilgrimCity"
            "pilgrimstate" in clues || clues == "state" -> "pilgrimState"
            "pilgrimcountry" in clues || clues == "country" -> "pilgrimCountry"
            "pilgrimpincode" in clues || "pincode" in clues || "postalcode" in clues || "zipcode" in clues -> "pilgrimPincode"
            clues.contains("idnumber") || clues.contains("photoidnumber") -> "idNumber"
            clues.contains("idtype") || clues.contains("photoidproof") || clues.contains("identityproof") -> "idType"
            clues.contains("gender") || clues.contains("sex") -> "gender"
            clues.contains("age") -> "age"
            clues.contains("pilgrimname") || clues == "name" || clues.contains("fullname") -> "name"
            else -> null
        }
    }

    override fun onSaveRequest(request: android.service.autofill.SaveRequest, callback: android.service.autofill.SaveCallback) {
        callback.onSuccess()
    }
}
