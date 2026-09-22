# Security

- هیچ کلید یا URL خصوصی را داخل Repository Commit نکنید.
- `VPN_API_BASE_URL` و `VPN_APP_API_KEY` از GitHub Actions Secrets وارد Build می‌شوند.
- `Cron Token` و `Admin Gate` هرگز وارد اپ اندروید نمی‌شوند.
- لینک‌های Subscription فقط در Backend ذخیره می‌شوند.
- App API Key در APK نهایی قابل استخراج است؛ برای سرویس عمومی بزرگ، Device Registration و Token کوتاه‌عمر توصیه می‌شود.
