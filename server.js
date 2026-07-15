const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 3000;
const APK_DIR = path.join(__dirname, 'APK_DOWNLOAD');

const server = http.createServer((req, res) => {
    const url = req.url;

    // Handle APK file downloads
    if (url === '/app-debug.apk' || url === '/barta-chat-all-features.apk' || url === '/barta-chat-v1.0-26MB.apk') {
        const fileName = url.substring(1);
        const filePath = path.join(APK_DIR, fileName);

        if (fs.existsSync(filePath)) {
            const stat = fs.statSync(filePath);
            res.writeHead(200, {
                'Content-Type': 'application/vnd.android.package-archive',
                'Content-Length': stat.size,
                'Content-Disposition': `attachment; filename="${fileName}"`,
                'Cache-Control': 'no-cache, no-store, must-revalidate',
                'Pragma': 'no-cache',
                'Expires': '0'
            });

            const readStream = fs.createReadStream(filePath);
            readStream.pipe(res);
            console.log(`[DOWNLOAD] Served ${fileName} (${(stat.size / (1024 * 1024)).toFixed(2)} MB)`);
            return;
        } else {
            res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
            res.end(`Error: ${fileName} could not be found. Please wait for the build to finish.`);
            return;
        }
    }

    // Serve main download page
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    
    // Get real sizes of the files
    let appDebugSize = '45.0 MB';
    let bartaAllFeaturesSize = '45.0 MB';
    let bartaV10Size = '45.0 MB';

    try {
        const size1 = fs.statSync(path.join(APK_DIR, 'app-debug.apk')).size;
        appDebugSize = (size1 / (1024 * 1024)).toFixed(2) + ' MB';
    } catch (e) {}
    try {
        const size2 = fs.statSync(path.join(APK_DIR, 'barta-chat-all-features.apk')).size;
        bartaAllFeaturesSize = (size2 / (1024 * 1024)).toFixed(2) + ' MB';
    } catch (e) {}
    try {
        const size3 = fs.statSync(path.join(APK_DIR, 'barta-chat-v1.0-26MB.apk')).size;
        bartaV10Size = (size3 / (1024 * 1024)).toFixed(2) + ' MB';
    } catch (e) {}

    const html = `
<!DOCTYPE html>
<html lang="bn">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>বার্তা (Barta) - APK ডাউনলোড সেন্টার</title>
    <style>
        :root {
            --primary: #14b8a6;
            --primary-dark: #0f766e;
            --bg-dark: #0f172a;
            --card-bg: rgba(30, 41, 59, 0.7);
            --text-light: #f8fafc;
            --text-muted: #94a3b8;
        }
        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
        }
        body {
            background-color: var(--bg-dark);
            color: var(--text-light);
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            padding: 20px;
            overflow-x: hidden;
            background-image: radial-gradient(circle at top right, rgba(20, 184, 166, 0.15), transparent),
                              radial-gradient(circle at bottom left, rgba(99, 102, 241, 0.15), transparent);
        }
        .container {
            width: 100%;
            max-width: 600px;
            background: var(--card-bg);
            backdrop-filter: blur(12px);
            border: 1px solid rgba(255, 255, 255, 0.1);
            border-radius: 24px;
            padding: 40px 30px;
            box-shadow: 0 20px 40px rgba(0, 0, 0, 0.5);
            text-align: center;
            transform: translateY(0);
            transition: all 0.3s ease;
        }
        .logo-container {
            margin-bottom: 25px;
            display: inline-block;
            position: relative;
        }
        .logo {
            width: 96px;
            height: 96px;
            border-radius: 24px;
            background: linear-gradient(135deg, var(--primary), #6366f1);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 48px;
            color: white;
            font-weight: bold;
            box-shadow: 0 10px 25px rgba(20, 184, 166, 0.4);
            margin: 0 auto;
        }
        h1 {
            font-size: 2.2rem;
            font-weight: 800;
            margin-bottom: 10px;
            background: linear-gradient(to right, #ffffff, #2dd4bf);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .subtitle {
            font-size: 1.1rem;
            color: var(--text-muted);
            margin-bottom: 35px;
            line-height: 1.6;
        }
        .download-box {
            background: rgba(15, 23, 42, 0.5);
            border: 1px solid rgba(255, 255, 255, 0.05);
            border-radius: 16px;
            padding: 20px;
            margin-bottom: 20px;
            text-align: left;
            transition: all 0.2s ease;
        }
        .download-box:hover {
            border-color: rgba(20, 184, 166, 0.3);
            background: rgba(15, 23, 42, 0.7);
        }
        .file-info {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 12px;
        }
        .file-title {
            font-weight: 700;
            font-size: 1.1rem;
            color: #ffffff;
        }
        .file-size {
            background: rgba(20, 184, 166, 0.2);
            color: #2dd4bf;
            padding: 4px 10px;
            border-radius: 100px;
            font-size: 0.85rem;
            font-weight: 600;
        }
        .btn-download {
            display: flex;
            align-items: center;
            justify-content: center;
            width: 100%;
            padding: 14px 20px;
            background: linear-gradient(135deg, var(--primary), var(--primary-dark));
            color: white;
            border: none;
            border-radius: 12px;
            font-size: 1.05rem;
            font-weight: 700;
            text-decoration: none;
            cursor: pointer;
            transition: all 0.2s ease;
            box-shadow: 0 4px 12px rgba(20, 184, 166, 0.2);
        }
        .btn-download:hover {
            transform: translateY(-2px);
            box-shadow: 0 6px 20px rgba(20, 184, 166, 0.4);
            filter: brightness(1.1);
        }
        .btn-download svg {
            margin-right: 10px;
            width: 20px;
            height: 20px;
            fill: currentColor;
        }
        .install-guide {
            margin-top: 35px;
            text-align: left;
            border-top: 1px solid rgba(255, 255, 255, 0.1);
            padding-top: 25px;
        }
        .guide-title {
            font-size: 1.2rem;
            font-weight: 700;
            color: #ffffff;
            margin-bottom: 15px;
            display: flex;
            align-items: center;
        }
        .guide-title::before {
            content: '⚙️';
            margin-right: 8px;
        }
        ol {
            padding-left: 20px;
            color: var(--text-muted);
            font-size: 0.95rem;
            line-height: 1.8;
        }
        ol li {
            margin-bottom: 8px;
        }
        ol li strong {
            color: #ffffff;
        }
        .footer {
            margin-top: 40px;
            font-size: 0.85rem;
            color: var(--text-muted);
        }
        .badge {
            display: inline-block;
            background: rgba(99, 102, 241, 0.2);
            color: #818cf8;
            padding: 2px 8px;
            border-radius: 4px;
            font-size: 0.75rem;
            font-weight: 600;
            margin-top: 5px;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="logo-container">
            <div class="logo">ব</div>
        </div>
        <h1>বার্তা (Barta)</h1>
        <div class="subtitle">বাংলাদেশী মোবাইল ওটিপি সাইন-ইন, রিয়েল-টাইম চ্যাটিং এবং এআই সহকারীর সাথে প্রিমিয়াম চ্যাট অ্যাপ্লিকেশন।</div>

        <!-- Download Section 1 -->
        <div class="download-box">
            <div class="file-info">
                <div>
                    <span class="file-title">barta-chat-all-features.apk</span>
                    <br><span class="badge">সম্পূর্ণ প্রিমিয়াম সংস্করণ (প্রিমিয়াম ওয়ালপেপার সহ)</span>
                </div>
                <span class="file-size">${bartaAllFeaturesSize}</span>
            </div>
            <a href="/barta-chat-all-features.apk" class="btn-download">
                <svg viewBox="0 0 24 24"><path d="M5 20h14v-2H5v2zM19 9h-4V3H9v6H5l7 7 7-7z"/></svg>
                ডাউনলোড করুন (Full APK)
            </a>
        </div>

        <!-- Download Section 2 -->
        <div class="download-box">
            <div class="file-info">
                <div>
                    <span class="file-title">barta-chat-v1.0-26MB.apk</span>
                    <br><span class="badge">রিয়েল-টাইম ফাস্ট সার্ভার লিংক</span>
                </div>
                <span class="file-size">${bartaV10Size}</span>
            </div>
            <a href="/barta-chat-v1.0-26MB.apk" class="btn-download">
                <svg viewBox="0 0 24 24"><path d="M5 20h14v-2H5v2zM19 9h-4V3H9v6H5l7 7 7-7z"/></svg>
                ডাউনলোড করুন (Fast Route)
            </a>
        </div>

        <!-- Download Section 3 -->
        <div class="download-box">
            <div class="file-info">
                <div>
                    <span class="file-title">app-debug.apk</span>
                    <br><span class="badge">স্ট্যান্ডার্ড ডেভেলপার টেস্ট বিল্ড</span>
                </div>
                <span class="file-size">${appDebugSize}</span>
            </div>
            <a href="/app-debug.apk" class="btn-download">
                <svg viewBox="0 0 24 24"><path d="M5 20h14v-2H5v2zM19 9h-4V3H9v6H5l7 7 7-7z"/></svg>
                ডাউনলোড করুন (Standard build)
            </a>
        </div>

        <!-- Installation Instructions -->
        <div class="install-guide">
            <div class="guide-title">ফোন ইনস্টলেশন গাইড</div>
            <ol>
                <li>উপরে দেওয়া যেকোনো একটি ডাউনলোড বাটনে ক্লিক করে APK ফাইলটি ডাউনলোড করুন।</li>
                <li>ডাউনলোড শেষ হলে ফাইলটি খুলুন।</li>
                <li>যদি আপনার ফোনে নিরাপত্তা সতর্কতা আসে, তবে ফোনের <strong>Settings (সেটিংস)</strong>-এ যান এবং <strong>"Allow from this source"</strong> অথবা <strong>"Install unknown apps"</strong> সক্রিয় (Enable) করুন।</li>
                <li>এখন <strong>Install (ইনস্টল)</strong> বাটনে ট্যাপ করে ইনস্টলেশন সম্পন্ন করুন এবং প্রিমিয়াম চ্যাটিং উপভোগ করুন!</li>
            </ol>
        </div>

        <div class="footer">
            &copy; 2026 বার্তা (Barta) চ্যাট টিম &bull; সুরক্ষিত ও নিরাপদ ডাউনলোড সেন্টার
        </div>
    </div>
</body>
</html>
    `;
    res.end(html);
});

server.listen(PORT, '0.0.0.0', () => {
    console.log(`[SERVER] APK Download Server running on port ${PORT}`);
});
