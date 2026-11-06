# مدرسه‌یار 4.1.1 — اصلاح خطاهای کامپایل

## علت خطا
گزارش GitHub Actions نسخه 4.1.0 سه خطای unresolved reference ثبت کرده بود:
- `ExtendedTeacherScreens.kt:249` — `clip`
- `MainActivity.kt:1015` — `border`
- `MainActivity.kt:1022` — `border`

## اصلاح
- افزودن `import androidx.compose.ui.draw.clip` به `ExtendedTeacherScreens.kt`.
- افزودن `import androidx.compose.foundation.border` به `MainActivity.kt`.
- تغییر `versionName` به `4.1.1` و `versionCode` به `29`.

هیچ منطق API، داده، صفحه، رنگ، آیکون یا چیدمان بصری تغییر نکرده است؛ این نسخه فقط خطاهای کامپایل ثبت‌شده را اصلاح می‌کند.

## اعتبارسنجی
- ممیزی ایستای مسیرهای منو و کلیک‌ها اجرا می‌شود.
- Build کامل APK و تست روی دستگاه تا اجرای موفق GitHub Actions تأیید نشده است.
