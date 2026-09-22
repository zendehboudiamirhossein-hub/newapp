# وی پی ان من — Android v1.0.2

طراحی و توسعه: امید

این نسخه عمومی هیچ آدرس پنل، App API Key، لینک Subscription، Cron Token یا Admin Gate واقعی داخل سورس ندارد.

## امکانات
- رابط فارسی و RTL با Vazirmatn
- دریافت سرورها از VPN Panel PHP
- Latency و انتخاب سرور
- تبلیغ قبل از اتصال
- Android VpnService + Xray
- VLESS / VMess / Trojan / Shadowsocks
- خروجی arm64 سبک‌تر
- Build APK با GitHub Actions

## تنظیمات لازم برای GitHub Actions
در Repository به Settings → Secrets and variables → Actions بروید و این دو Repository Secret را بسازید:

1. VPN_API_BASE_URL
   نمونه: https://vpn.example.com یا https://example.com/vpn
   بدون / انتهایی.

2. VPN_APP_API_KEY
   همان App API Key که پنل PHP پس از نصب ایجاد می‌کند.

بعد Actions → Build Android APK → Run workflow را اجرا کنید.
Artifact نهایی: VPN-Man-Android-v1.0.2-arm64

راهنمای کامل در فایل «آموزش-نصب-صفر-تا-صد.txt» ریشه بسته است.
