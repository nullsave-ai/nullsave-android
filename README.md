# MK Terminal - Android App Project

مشروع تطبيق أندرويد متكامل جاهز للبناء عبر **GitHub Actions** لتوليد ملف الـ **APK**.

## محتويات المشروع:
- واجهات تطبيق الويب React الكاملة المستخرجة من الحزمة في `app/src/main/assets/web/`
- جسر شبكي في `MainActivity.kt` يقوم بربط واجهة المستخدم مع خادم الـ API السحابي: `https://api.mkterminal.xyz`
- ملف البناء التلقائي عبر GitHub: `.github/workflows/build-apk.yml`

## خطوات رفع المشروع على GitHub وتنزيل الـ APK:
1. أنشئ مستودعاً جديداً (New Repository) على حسابك في GitHub (مثلاً: `mk-terminal-android`).
2. افتح موجه الأوامر في هذا المجلد ونفذ:
   ```bash
   git init
   git add .
   git commit -m "Initial commit for Android APK build"
   git branch -M main
   git remote add origin https://github.com/<your-username>/mk-terminal-android.git
   git push -u origin main
   ```
3. توجه إلى تبويب **Actions** في صفحة مستودع GitHub الخاص بك.
4. ستجد عملية البناء **Build Android APK** تعمل تلقائياً، وتستغرق دقيقة واحدة تقريباً.
5. بعد اكتمال البناء باللون الأخضر، اضغط على العملية وستجد ملف **MKTerminal-Debug-APK** جاهزاً للتنزيل والتثبيت على هاتفك!
