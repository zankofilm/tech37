# راهنمای رفع خطای ساخت APK در GitHub Actions

## چه چیزی از تصویر مشخص است؟
پیغام `Process completed with exit code 1` فقط می‌گوید یکی از دستورات ناموفق بوده است. خود این پیام علت خطا نیست. در تصویر بخش `Build installable debug APK` شکست خورده، اما اولین خط خطای کامپایل (مانند `e: ...kt:line:column`) در تصویر دیده نمی‌شود.

## تغییر این بسته
Workflow اکنون کامپایل Kotlin را از تست و بسته‌بندی APK جدا کرده است و متن کامل خروجی Gradle را در فایل‌های `gradle-compile.log` و `gradle-build.log` نگه می‌دارد. هنگام شکست، خلاصه خطاها در صفحه Summary نمایش داده می‌شود و گزارش کامل از بخش Artifacts با نام `Madreseyar-Gradle-Logs` قابل دانلود است.

## اجرای مجدد
1. پوشه `.github/workflows/build-apk.yml` و فایل‌های برنامه را روی شاخه `main` جایگزین و commit کنید.
2. در GitHub به Actions > Build Madreseyar Teacher APK > Run workflow بروید.
3. اگر خطا رخ داد، صفحه Run summary را باز کنید و نخستین خط `e:` یا `error:` را بررسی کنید؛ یا از بخش Artifacts فایل `Madreseyar-Gradle-Logs` را دانلود کنید.
4. اگر ساخت موفق بود، فایل `Madreseyar-Teacher-APK` قابل نصب است.

## وضعیت تأیید
منطق مستقل سؤال‌های مخزن و ورود گروهی سؤال‌ها با Kotlin/JVM کامپایل شد. در محیط فعلی ابزار Gradle و Android SDK وجود ندارد و دسترسی دانلود آن‌ها فعال نیست؛ به همین دلیل صحت کامپایل کل APK در این محیط قابل تأیید نیست. این اصلاح به‌تنهایی تضمین نمی‌کند ایراد منبع Kotlin از بین رفته باشد؛ فقط تشخیص دقیق محل خطا را آسان می‌کند.
