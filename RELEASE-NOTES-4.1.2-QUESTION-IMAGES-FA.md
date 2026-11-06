# نسخه 4.1.2 — اصلاح نمایش تصاویر مخزن سؤال

## علت ایراد
در نسخه 4.1.1، `QuestionImagePreview` فقط `data:image/...;base64` را decode می‌کرد. اما تصاویر سؤال‌های تأییدشده در هاست به‌صورت فایل رسانه‌ای ذخیره شده و API برای آن‌ها مسیرهایی مانند `/student/media/question-images/<file>.png` برمی‌گرداند. در نصب cPanel زیر `/app/`، درخواست باید به `/app/student/media/question-images/<file>.png` برسد.

## اصلاحات
- افزودن `QuestionImagePath.kt` برای تبدیل امن مسیرهای قدیمی رسانه به مسیر نصب‌شده زیر BasePath برنامه.
- پشتیبانی از URL کامل فقط در HTTPS و همان دامنه؛ URL خارجی، مسیر ناامن و traversal رد می‌شود.
- بارگذاری تصویر با `HttpURLConnection`، توکن Bearer فعلی در صورت نیاز، timeout، محدودیت ۳ مگابایت و بررسی Content-Type.
- پشتیبانی هم‌زمان از Base64 و URL/pathهای تصویری.
- خواندن کلیدهای متداول تصویر از پاسخ API: `imageDataUrl`, `imageUrl`, `image_url`, `imagePath`, `image_path`, `questionImage`, `mediaUrl`, `image`, `picture`, `photo` و objectهای دارای `url/path/src`.
- اعمال منبع تصویر یکسان در مخزن سؤال، صفحه جزئیات آزمون و تبدیل سؤال بانک به پیش‌نویس آزمون.
- حفظ ظاهر نسخه 4.1؛ تغییر بصری در صفحات انجام نشده است.

## کنترل کیفیت
- `bash tools/local-smoke/run.sh` — موفق؛ شامل مسیر رسانه زیر `/app/`، جلوگیری از دوباره‌افزایی BasePath، رد دامنه خارجی و رد مسیر traversal.
- `python3 tools/ui_route_audit.py` — موفق؛ ۲۶ مسیر منو، پنج مقصد نوار پایین و بررسی handlerها.
- تست‌های JUnit جدید برای مسیر تصویر و فیلدهای جایگزین API اضافه شدند.
- کامپایل کامل Android و تست روی گوشی در این محیط اجرا نشده؛ باید از طریق GitHub Actions تأیید شود.

## نسخه
- Version Name: `4.1.2`
- Version Code: `30`
- API endpoints: بدون تغییر
- طراحی: بدون تغییر؛ فقط مسیر و renderer تصاویر اصلاح شد.
