# راهنمای اعمال پچ نسخه 4.1.3

این پچ برای سورس نسخه 4.1.2 ساخته شده است.

## روش امن
1. از مخزن GitHub فعلی یک نسخه پشتیبان یا branch جدا بسازید.
2. فایل ZIP پچ را استخراج کنید.
3. پوشه‌ها را از ریشه پچ در ریشه مخزن کپی و فایل‌های هم‌نام را جایگزین کنید.
4. Commit و Push انجام دهید.
5. در GitHub Actions، workflow با نام `Build Madreseyar Teacher APK` را اجرا کنید.
6. بعد از نصب APK، در آزمون‌ساز «نوشتن سؤال» را باز کنید؛ باید گزینه «تصویری» کنار قالب‌های پاسخ دیده شود. با انتخاب آن، افزودن تصویر الزامی می‌شود.
7. در مخزن سؤال نیز فیلتر «تصویری» باید سؤال‌های تصویری را نشان دهد.

## فایل‌های داخل پچ
- `app/src/main/java/ir/madreseyar/teacher/ExamStudioScreen.kt`
- `app/src/main/java/ir/madreseyar/teacher/QuestionBankReview.kt`
- `app/src/test/java/ir/madreseyar/teacher/UpgradeRegressionTest.kt`
- `app/build.gradle.kts`
- `README.md`
- `RELEASE-NOTES-4.1.3-RESTORE-IMAGE-QUESTION-OPTION-FA.md`

این پچ قرارداد API و داده‌های موجود روی هاست را تغییر نمی‌دهد.
