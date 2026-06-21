package com.example.data

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    
    // OkHttp Client configured with 60-second timeouts as mandated by Gemini guidelines
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Safely triggers Gemini 2.5 Flash model through Firebase Cloud Function.
     * Falls back to a direct REST API call if Cloud Function URL is not configured or fails.
     */
    suspend fun getGeminiResponse(
        context: Context,
        userPhone: String,
        userMessage: String,
        previousMessages: List<Message>,
        language: String
    ): String {
        val sharedPrefs = context.getSharedPreferences("BartaChatPrefs", Context.MODE_PRIVATE)
        val cloudFunctionsUrl = sharedPrefs.getString("firebase_functions_base_url", "")?.trim() ?: ""

        if (cloudFunctionsUrl.isNotEmpty()) {
            try {
                Log.d(TAG, "Attempting connection to Cloud Function at: $cloudFunctionsUrl")
                return callCloudFunction(userPhone, userMessage, previousMessages, language, cloudFunctionsUrl)
            } catch (e: Exception) {
                Log.e(TAG, "Cloud Function call failed: ${e.message}. Falling back to direct API.", e)
            }
        } else {
            Log.d(TAG, "No Cloud Function URL set. Falling back to secure direct API.")
        }

        // Direct secure REST API call fallback
        return callDirectGeminiApi(userMessage, previousMessages, language)
    }

    /**
     * HTTP call using OkHttp directly to the deployed Firebase Cloud Function URL
     */
    private fun callCloudFunction(
        userPhone: String,
        userMessage: String,
        previousMessages: List<Message>,
        language: String,
        baseUrl: String
    ): String {
        // Construct standard payload
        val jsonPayload = JSONObject()
        jsonPayload.put("message", userMessage)
        jsonPayload.put("language", language)

        val historyArray = JSONArray()
        // Take the last 15 messages for high context quality and low tokens consumption
        val recentHistory = previousMessages.sortedBy { it.timestamp }.takeLast(15)
        for (msg in recentHistory) {
            val histObj = JSONObject()
            histObj.put("id", msg.id)
            histObj.put("text", msg.text)
            histObj.put("senderId", msg.senderId)
            histObj.put("timestamp", msg.timestamp)
            historyArray.put(histObj)
        }
        jsonPayload.put("previousMessages", historyArray)

        val endpointUrl = if (baseUrl.endsWith("/")) "${baseUrl}getGeminiResponse" else "$baseUrl/getGeminiResponse"
        val body = jsonPayload.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(endpointUrl)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-user-phone", userPhone)
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(bodyStr).optString("error", "Error response from server")
                } catch (e: Exception) {
                    "HTTP Error ${response.code}"
                }
                throw Exception(errorMsg)
            }

            val jsonObj = JSONObject(bodyStr)
            return jsonObj.getString("response")
        }
    }

    /**
     * Direct REST call of gemini-2.5-flash as mandated for secure-fallback configuration
     */
    private fun callDirectGeminiApi(
        userMessage: String,
        previousMessages: List<Message>,
        language: String
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return if (language == "bn") {
                "দুঃখিত, কোনো এআই এপিআই কি (API Key) বা ক্লাউড ফাংশন ইউআরএল কনফিগার করা নেই। দয়া করে সেটিংস থেকে সেট করুন।"
            } else {
                "Sorry, no Gemini API Key or Cloud Function URL has been configured. Please configure them in Settings."
            }
        }

        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

        val contentsArray = JSONArray()
        
        // Feed conversation history inside Google contents schema
        val recentHistory = previousMessages.sortedBy { it.timestamp }.takeLast(15)
        for (msg in recentHistory) {
            val contentObj = JSONObject()
            // Set sender roles correctly: "user" (sender) or "model" (chatbot)
            contentObj.put("role", if (msg.senderId == "01300000000") "model" else "user")
            
            val partsArray = JSONArray()
            val partText = JSONObject().put("text", msg.text)
            partsArray.put(partText)
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
        }

        // Add active message
        val currentContent = JSONObject()
        currentContent.put("role", "user")
        val currentParts = JSONArray().put(JSONObject().put("text", userMessage))
        currentContent.put("parts", currentParts)
        contentsArray.put(currentContent)

        // System instructions context setup
        val isBn = language == "bn"
        val instructionsText = if (isBn) {
            "আপনি হলেন 'বার্তা' চ্যাট অ্যাপ্লিকেশনের একজন অত্যন্ত বুদ্ধিমান এবং অমায়িক এআই সহকারী (Barta AI Companion)। ব্যবহারকারীর সাথে অত্যন্ত মার্জিত ও সুন্দর বাংলা ভাষায় কথা বলবেন। আপনার উত্তরগুলো হবে সংক্ষিপ্ত, যুক্তিগ্রাহ্য এবং তথ্যবহুল। প্রয়োজনের ক্ষেত্রে আপনি ইংরেজি মিশ্রিত বাংলা (Banglish) বা সম্পূর্ণ ইংরেজি ব্যবহার করতে পারেন। যদি কোনো প্রশ্ন আপনার সীমাবদ্ধতার বাইরে থাকে, তবে মার্জিতভাবে ক্ষমা চেয়ে নেবেন।"
        } else {
            "You are the 'Barta AI Companion', an intelligent and polite AI assistant inside the 'Barta (Chat)' app. Communicate naturally, concisely, and gracefully with the user. Answer helpful questions, engage in conversational chat, and offer precise developer or app details if asked. Always maintain a warm and friendly tone."
        }
        val systemInstructionObj = JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instructionsText)))

        // Enclosing root JSON payload
        val rootPayload = JSONObject()
        rootPayload.put("contents", contentsArray)
        rootPayload.put("systemInstruction", systemInstructionObj)
        rootPayload.put("generationConfig", JSONObject().put("temperature", 0.7))

        val body = rootPayload.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(endpoint)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.e(TAG, "Direct API failed code ${response.code} body: $responseBody")
                throw Exception("Direct Gemini API error: code ${response.code}")
            }

            val rootJson = JSONObject(responseBody)
            val candidates = rootJson.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            return parts.getJSONObject(0).getString("text")
        }
    }
}
