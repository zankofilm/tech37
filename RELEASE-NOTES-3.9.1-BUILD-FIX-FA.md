# یادداشت انتشار نسخه 3.9.1 — اصلاح کامپایل

## علت خطای نسخه 3.9.0
گزارش GitHub Actions خطای زیر را نشان داد:

`MainActivity.kt:1565:44 Unresolved reference: InfoOutline`

آیکون `InfoOutline` در importهای فعلی پروژه در دسترس نبود. این آیکون در ردیف نمایش جزئیات نشست‌های امنیتی استفاده شده بود.

## اصلاح
- جایگزینی `Icons.Default.InfoOutline` با `Icons.Default.Info` که در مجموعه آیکون‌های Material Icons Extended موجود است.
- افزایش `versionCode` از 25 به 26.
- تغییر `versionName` به `3.9.1-build-fix`.

## نتیجه بررسی گزارش قبلی
- Checkout سورس: موفق
- اعتبارسنجی HTTPS آدرس هاست: موفق
- آماده‌سازی Java 17، Android SDK و Gradle 8.9: موفق
- ممیزی ایستای ۲۶ مسیر منو: موفق
- ممیزی ایستای handlerهای خالی `onClick={}`: موفق
- کامپایل Kotlin: ناموفق فقط به علت `InfoOutline` شناخته‌نشده

## محدودیت تأیید
ممیزی مسیرها ایستا است و عملکرد API یا تعامل واقعی روی دستگاه را ثابت نمی‌کند. نسخه 3.9.1 تا زمان اجرای مجدد GitHub Actions و تست روی گوشی، APK تأییدشده محسوب نمی‌شود.
