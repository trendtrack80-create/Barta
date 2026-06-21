/**
 * Firebase Cloud Function for Secure Google Gemini AI Integration in বার্তা (Chat)
 * Place this in your firebase functions project directory and deploy using:
 * firebase deploy --only functions
 */

const functions = require("firebase-functions");
const admin = require("firebase-admin");
const { GoogleGenAI } = require("@google/genai"); // Official Google GenAI SDK

admin.initializeApp();

// Configured securely via environment variables or Firestore document fallback
// Deploy API key via CLI: firebase functions:secrets:set GEMINI_API_KEY=your_key_here
const getApiKey = () => {
  // Try finding in environment first
  return process.env.GEMINI_API_KEY || "";
};

/**
 * HTTP Cloud Function that safely wraps Gemini 2.5 Flash API calls
 */
exports.getGeminiResponse = functions.https.onRequest(async (req, res) => {
  // Handle CORS
  res.set("Access-Control-Allow-Origin", "*");
  res.set("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
  res.set("Access-Control-Allow-Headers", "Content-Type, Authorization");

  if (req.method === "OPTIONS") {
    return res.status(204).send("");
  }

  if (req.method !== "POST") {
    return res.status(405).send({ error: "Only POST method is allowed." });
  }

  const { message, previousMessages, language } = req.body;

  if (!message) {
    return res.status(400).send({ error: "Required 'message' parameter is missing." });
  }

  try {
    const apiKey = getApiKey();
    if (!apiKey) {
      throw new Error("Gemini API Key is not configured on the server environment.");
    }

    // Initialize Generative AI client (uses gemini-2.5-flash by default)
    const ai = new GoogleGenAI({ apiKey: apiKey });

    // Build standard multi-turn conversation contents
    const contents = [];
    
    // Add history if present
    if (previousMessages && Array.isArray(previousMessages)) {
      previousMessages.forEach((msg) => {
        const role = msg.senderId === "01300000000" ? "model" : "user";
        contents.push({
          role: role,
          parts: [{ text: msg.text }]
        });
      });
    }

    // Add current user message
    contents.push({
      role: "user",
      parts: [{ text: message }]
    });

    const isBengali = language === "bn";
    const systemInstruction = isBengali
      ? "আপনি হলেন 'বার্তা' চ্যাট অ্যাপ্লিকেশনের একজন অত্যন্ত বুদ্ধিমান এবং অমায়িক এআই সহকারী (Barta AI Companion)। ব্যবহারকারীর সাথে অত্যন্ত মার্জিত ও সুন্দর বাংলা ভাষায় কথা বলবেন। আপনার উত্তরগুলো হবে সংক্ষিপ্ত, যুক্তিগ্রাহ্য এবং তথ্যবহুল। প্রয়োজনের ক্ষেত্রে আপনি ইংরেজি মিশ্রিত বাংলা (Banglish) বা সম্পূর্ণ ইংরেজি ব্যবহার করতে পারেন। যদি কোনো প্রশ্ন আপনার সীমাবদ্ধতার বাইরে থাকে, তবে মার্জিতভাবে ক্ষমা চেয়ে নেবেন।"
      : "You are the 'Barta AI Companion', an intelligent and polite AI assistant inside the 'Barta (Chat)' app. Communicate naturally, concisely, and gracefully with the user. Answer helpful questions, engage in conversational chat, and offer precise developer or app details if asked. Always maintain a warm and friendly tone.";

    // Invoke Gemini 2.5 Flash
    // We use gemini-2.5-flash as requested by user
    const responseStream = await ai.models.generateContent({
      model: "gemini-2.5-flash",
      contents: contents,
      config: {
        systemInstruction: systemInstruction,
        temperature: 0.7,
        maxOutputTokens: 1024,
      }
    });

    const replyText = responseStream.text || "";

    // Save conversations in Firestore automatically for cloud backup
    const userId = req.headers["x-user-phone"] || "anonymous";
    const timestamp = Date.now();
    
    // Save backup of user request
    const userMsgRef = admin.firestore().collection("users").document(userId).collection("ai_conversations").doc();
    await userMsgRef.set({
      id: userMsgRef.id,
      text: message,
      senderId: "user",
      timestamp: timestamp
    });

    // Save backup of AI response
    const botMsgRef = admin.firestore().collection("users").document(userId).collection("ai_conversations").doc();
    await botMsgRef.set({
      id: botMsgRef.id,
      text: replyText,
      senderId: "01300000000",
      timestamp: timestamp + 1
    });

    return res.status(200).send({
      response: replyText,
      model: "gemini-2.5-flash",
      timestamp: timestamp
    });

  } catch (error) {
    console.error("Gemini invocation error:", error);
    return res.status(500).send({
      error: "আই সহকারী সাড়া দিতে ব্যর্থ হয়েছে। অনুগ্রহ করে আবার চেষ্টা করুন।",
      details: error.message
    });
  }
});
