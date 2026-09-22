# آپلود روی GitHub

1. یک Repository بسازید.
2. **محتویات داخل این پوشه** را در ریشه Repository آپلود کنید؛ پوشه `.github` فراموش نشود.
3. در Settings → Secrets and variables → Actions دو Secret بسازید: `VPN_API_BASE_URL` و `VPN_APP_API_KEY`.
4. وارد Actions شوید و Workflow با نام `Build Android APK` را اجرا کنید.
5. Artifact را دانلود و ZIP را باز کنید تا APK را بگیرید.
